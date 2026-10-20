package com.activitytracker.bridge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import com.activitytracker.auth.SessionManager
import com.activitytracker.data.TrackingRepository
import com.activitytracker.export.CsvExporter
import com.activitytracker.tracking.SensorHub
import com.activitytracker.tracking.TrackingService
import com.activitytracker.tracking.TrackingSnapshot
import com.activitytracker.tracking.TrackingState
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap

/**
 * Bridge between React Native and the tracking service. Live values are pushed to JS as
 * `TrackingUpdate` (1 Hz) and `LocationUpdate` (per accepted GPS fix) events; history and
 * exports are served from SQLite.
 */
class TrackingModule(context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {

  private val repository = TrackingRepository(context)
  private val sessionManager = SessionManager(context)

  private val snapshotListener = TrackingState.SnapshotListener { emit(EVENT_TRACKING, Mappers.snapshot(it)) }
  private val locationListener =
      TrackingState.LocationListener { sessionId, point ->
        emit(
            EVENT_LOCATION,
            Mappers.location(point).apply { putDouble("sessionId", sessionId.toDouble()) })
      }

  // Light/pressure preview for the dashboard while no session is running.
  private var previewThread: HandlerThread? = null
  private var previewHub: SensorHub? = null
  private var previewHandler: Handler? = null
  private val previewTick =
      object : Runnable {
        override fun run() {
          val hub = previewHub ?: return
          if (!TrackingState.snapshot.isTracking) {
            emit(
                EVENT_TRACKING,
                Mappers.snapshot(TrackingSnapshot(lightLux = hub.lightLux, pressureHpa = hub.pressureHpa)))
          }
          previewHandler?.postDelayed(this, 1_000L)
        }
      }

  override fun getName() = NAME

  override fun initialize() {
    super.initialize()
    TrackingState.addSnapshotListener(snapshotListener)
    TrackingState.addLocationListener(locationListener)
  }

  override fun invalidate() {
    TrackingState.removeSnapshotListener(snapshotListener)
    TrackingState.removeLocationListener(locationListener)
    stopPreview()
    super.invalidate()
  }

  @ReactMethod
  fun startTracking(promise: Promise) {
    val ctx = reactApplicationContext
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) !=
        PackageManager.PERMISSION_GRANTED) {
      promise.reject("E_PERMISSION", "Location permission is required to track a session")
      return
    }
    if (sessionManager.currentUser == null) {
      promise.reject("E_AUTH", "No user is signed in")
      return
    }
    try {
      TrackingService.start(ctx)
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("E_START", e.message, e)
    }
  }

  @ReactMethod
  fun stopTracking(promise: Promise) {
    try {
      TrackingService.stop(reactApplicationContext)
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("E_STOP", e.message, e)
    }
  }

  @ReactMethod
  fun getStatus(promise: Promise) {
    val snapshot = TrackingState.snapshot
    val hub = SensorHub(reactApplicationContext, Handler(reactApplicationContext.mainLooper))
    promise.resolve(
        Arguments.createMap().apply {
          putMap("snapshot", Mappers.snapshot(snapshot))
          putMap(
              "sensors",
              Arguments.createMap().apply { hub.availability.forEach { (k, v) -> putBoolean(k, v) } })
        })
  }

  @ReactMethod
  fun startEnvironmentPreview() {
    if (previewHub != null) return
    val thread = HandlerThread("env-preview").apply { start() }
    val handler = Handler(thread.looper)
    previewThread = thread
    previewHandler = handler
    previewHub = SensorHub(reactApplicationContext, handler).also { it.startEnvironment() }
    handler.post(previewTick)
  }

  @ReactMethod
  fun stopEnvironmentPreview() = stopPreview()

  private fun stopPreview() {
    previewHandler?.removeCallbacksAndMessages(null)
    previewHub?.stop()
    previewThread?.quitSafely()
    previewHub = null
    previewHandler = null
    previewThread = null
  }

  @ReactMethod
  fun getSessions(promise: Promise) {
    val user = sessionManager.currentUser ?: return promise.resolve(Arguments.createArray())
    runCatching { Mappers.sessions(repository.getSessions(user.id)) }
        .onSuccess(promise::resolve)
        .onFailure { promise.reject("E_DB", it.message, it) }
  }

  @ReactMethod
  fun getSessionDetail(sessionId: Double, promise: Promise) {
    runCatching {
          val id = sessionId.toLong()
          val session = repository.getSession(id) ?: error("Session not found")
          Arguments.createMap().apply {
            putMap("session", Mappers.session(session))
            putArray(
                "samples",
                Arguments.createArray().apply {
                  repository.getSamples(id).forEach { pushMap(Mappers.sample(it)) }
                })
            putArray(
                "route",
                Arguments.createArray().apply {
                  repository.getLocations(id).forEach { pushMap(Mappers.location(it)) }
                })
          }
        }
        .onSuccess(promise::resolve)
        .onFailure { promise.reject("E_DB", it.message, it) }
  }

  @ReactMethod
  fun deleteSession(sessionId: Double, promise: Promise) {
    val id = sessionId.toLong()
    if (TrackingState.snapshot.isTracking && TrackingState.snapshot.sessionId == id) {
      promise.reject("E_ACTIVE", "Stop the session before deleting it")
      return
    }
    runCatching { repository.deleteSession(id) }
        .onSuccess { promise.resolve(null) }
        .onFailure { promise.reject("E_DB", it.message, it) }
  }

  @ReactMethod
  fun exportSessionCsv(sessionId: Double, promise: Promise) {
    runCatching {
          val exporter = CsvExporter(reactApplicationContext)
          val file = exporter.export(sessionId.toLong())
          val chooser = exporter.shareIntent(file)
          (reactApplicationContext.currentActivity ?: reactApplicationContext).startActivity(chooser)
          file.absolutePath
        }
        .onSuccess(promise::resolve)
        .onFailure { promise.reject("E_EXPORT", it.message, it) }
  }

  // Required by NativeEventEmitter on the JS side.
  @ReactMethod fun addListener(eventName: String) = Unit

  @ReactMethod fun removeListeners(count: Double) = Unit

  private fun emit(event: String, payload: WritableMap) {
    val ctx = reactApplicationContext
    if (ctx.hasActiveReactInstance()) ctx.emitDeviceEvent(event, payload)
  }

  companion object {
    const val NAME = "TrackingModule"
    const val EVENT_TRACKING = "TrackingUpdate"
    const val EVENT_LOCATION = "LocationUpdate"
  }
}
