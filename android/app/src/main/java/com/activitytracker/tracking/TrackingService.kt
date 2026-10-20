package com.activitytracker.tracking

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.activitytracker.R
import com.activitytracker.auth.SessionManager
import com.activitytracker.data.ActivityType
import com.activitytracker.data.LocationPoint
import com.activitytracker.data.SensorSample
import com.activitytracker.data.TrackingRepository
import com.activitytracker.notifications.Notifications
import java.util.Locale

/**
 * Foreground service that owns a tracking session end-to-end: motion classification,
 * GPS route + distance and light/pressure sampling, persisted straight to SQLite. It keeps
 * running when the app is minimised or the JS runtime is destroyed, and resumes the active
 * session if the system restarts it (START_STICKY).
 */
class TrackingService : Service() {

  private lateinit var thread: HandlerThread
  private lateinit var handler: Handler
  private lateinit var repository: TrackingRepository
  private lateinit var sensors: SensorHub
  private lateinit var location: LocationTracker
  private val classifier = ActivityClassifier()
  private var wakeLock: PowerManager.WakeLock? = null

  private var running = false
  private var sessionId = -1L
  private var startTime = 0L
  private var distanceM = 0.0
  private val durations = mutableMapOf<ActivityType, Long>()
  private var activity = ActivityType.STATIONARY
  private var activityStartedAt = 0L
  private var lastTickElapsed = 0L
  private var tickCount = 0L

  private val tick =
      object : Runnable {
        override fun run() {
          onTick()
          if (running) handler.postDelayed(this, TICK_MS)
        }
      }

  override fun onCreate() {
    super.onCreate()
    Notifications.ensureChannels(this)
    repository = TrackingRepository(this)
    thread = HandlerThread("tracking-service").apply { start() }
    handler = Handler(thread.looper)
    sensors = SensorHub(this, handler)
    location = LocationTracker(this, thread.looper)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> handler.post { stopSession() }
      // ACTION_START, or a null intent when the system restarts a sticky service.
      else -> {
        if (!promoteToForeground()) {
          stopSelf()
          return START_NOT_STICKY
        }
        handler.post { startOrResumeSession() }
      }
    }
    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    running = false
    handler.removeCallbacksAndMessages(null)
    sensors.stop()
    location.stop()
    releaseWakeLock()
    thread.quitSafely()
    super.onDestroy()
  }

  private fun promoteToForeground(): Boolean {
    if (!hasLocationPermission()) {
      Log.w(TAG, "Location permission missing; cannot start location foreground service")
      return false
    }
    val type =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        else 0
    return try {
      ServiceCompat.startForeground(
          this, Notifications.TRACKING_NOTIFICATION_ID, buildNotification(), type)
      true
    } catch (e: Exception) {
      // e.g. ForegroundServiceStartNotAllowedException when restarted from the background.
      Log.e(TAG, "Unable to start foreground service", e)
      false
    }
  }

  private fun startOrResumeSession() {
    if (running) return
    val user = SessionManager(this).currentUser
    if (user == null) {
      stopSelf()
      return
    }

    val active = repository.getActiveSession()
    var resumePoint: LocationPoint? = null
    if (active != null) {
      sessionId = active.id
      startTime = active.startTime
      distanceM = active.distanceM
      durations[ActivityType.STATIONARY] = active.stationaryMs
      durations[ActivityType.WALKING] = active.walkingMs
      durations[ActivityType.RUNNING] = active.runningMs
      resumePoint = repository.getLocations(active.id).lastOrNull()
    } else {
      startTime = System.currentTimeMillis()
      sessionId = repository.createSession(user.id, startTime)
      distanceM = 0.0
      durations.clear()
    }

    classifier.reset()
    activity = ActivityType.STATIONARY
    activityStartedAt = System.currentTimeMillis()
    lastTickElapsed = SystemClock.elapsedRealtime()
    tickCount = 0
    running = true

    acquireWakeLock()
    sensors.startEnvironment()
    sensors.startMotion(
        object : SensorHub.Listener {
          override fun onAccelerometer(timestampNs: Long, x: Float, y: Float, z: Float) =
              classifier.addAccelerometer(timestampNs, x, y, z)

          override fun onGyroscope(timestampNs: Long, x: Float, y: Float, z: Float) =
              classifier.addGyroscope(timestampNs, x, y, z)
        })
    location.start(resumePoint) { point, segment ->
      distanceM += segment
      repository.insertLocation(sessionId, point)
      TrackingState.publishLocation(sessionId, point)
    }
    handler.post(tick)
  }

  private fun onTick() {
    if (!running) return
    val nowElapsed = SystemClock.elapsedRealtime()
    val delta = nowElapsed - lastTickElapsed
    lastTickElapsed = nowElapsed

    val result = classifier.evaluate(location.lastSpeed)
    durations[activity] = (durations[activity] ?: 0L) + delta
    if (result.activity != activity) {
      activity = result.activity
      activityStartedAt = System.currentTimeMillis()
    }
    location.isStationary = activity == ActivityType.STATIONARY

    tickCount++
    if (tickCount % PERSIST_EVERY_TICKS == 0L) {
      repository.insertSample(
          sessionId,
          SensorSample(
              ts = System.currentTimeMillis(),
              activity = activity,
              intensity = result.intensity,
              light = sensors.lightLux,
              pressure = sensors.pressureHpa,
          ))
      repository.updateSessionProgress(sessionId, distanceM, durations)
      updateNotification()
    }

    TrackingState.publish(snapshot(result.intensity, result.cadenceHz))
  }

  private fun stopSession() {
    if (running) {
      running = false
      handler.removeCallbacks(tick)
      sensors.stop()
      location.stop()
      repository.updateSessionProgress(sessionId, distanceM, durations)
      repository.completeSession(sessionId, System.currentTimeMillis())
    } else {
      // Stop requested while not running in this process: close any dangling session.
      repository.getActiveSession()?.let { repository.completeSession(it.id, System.currentTimeMillis()) }
    }
    TrackingState.publish(
        TrackingSnapshot(lightLux = sensors.lightLux, pressureHpa = sensors.pressureHpa))
    releaseWakeLock()
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun snapshot(intensity: Double, cadence: Double) =
      TrackingSnapshot(
          isTracking = true,
          sessionId = sessionId,
          startTime = startTime,
          activity = activity,
          activityStartedAt = activityStartedAt,
          durations = durations.toMap(),
          distanceM = distanceM,
          lightLux = sensors.lightLux,
          pressureHpa = sensors.pressureHpa,
          intensity = intensity,
          cadenceHz = cadence,
          lastLocation = location.lastFix,
      )

  private fun buildNotification(): Notification {
    val stopIntent =
        PendingIntent.getService(
            this,
            0,
            Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val text =
        if (!running) getString(R.string.channel_tracking_desc)
        else
            String.format(
                Locale.US,
                "%s · %.2f km · %s",
                activity.key.replaceFirstChar { it.uppercase() },
                distanceM / 1000.0,
                formatElapsed(System.currentTimeMillis() - startTime))
    return NotificationCompat.Builder(this, Notifications.CHANNEL_TRACKING)
        .setSmallIcon(R.drawable.ic_stat_tracking)
        .setContentTitle(getString(R.string.tracking_notification_title))
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(Notifications.openAppIntent(this, 1))
        .addAction(0, getString(R.string.tracking_action_stop), stopIntent)
        .build()
  }

  private fun updateNotification() {
    if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
      try {
        NotificationManagerCompat.from(this)
            .notify(Notifications.TRACKING_NOTIFICATION_ID, buildNotification())
      } catch (e: SecurityException) {
        Log.w(TAG, "Notification permission revoked", e)
      }
    }
  }

  private fun hasLocationPermission() =
      ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
          PackageManager.PERMISSION_GRANTED

  /** Keeps the CPU awake so motion sensors keep delivering events with the screen off. */
  private fun acquireWakeLock() {
    if (wakeLock?.isHeld == true) return
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock =
        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ActivityTracker:tracking").apply {
          setReferenceCounted(false)
          acquire(MAX_WAKE_LOCK_MS)
        }
  }

  private fun releaseWakeLock() {
    wakeLock?.let { if (it.isHeld) it.release() }
    wakeLock = null
  }

  companion object {
    private const val TAG = "TrackingService"
    const val ACTION_START = "com.activitytracker.tracking.START"
    const val ACTION_STOP = "com.activitytracker.tracking.STOP"
    private const val TICK_MS = 1_000L
    private const val PERSIST_EVERY_TICKS = 5L
    private const val MAX_WAKE_LOCK_MS = 12 * 60 * 60 * 1000L

    fun start(context: Context) {
      ContextCompat.startForegroundService(
          context, Intent(context, TrackingService::class.java).setAction(ACTION_START))
    }

    fun stop(context: Context) {
      context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_STOP))
    }

    fun formatElapsed(ms: Long): String {
      val totalSeconds = ms / 1000
      return String.format(
          Locale.US, "%02d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
    }
  }
}
