package com.activitytracker.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.activitytracker.data.LocationPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GPS tracking with noise filtering. A fix is accepted only if it is accurate enough, moved
 * further than its own uncertainty, and implies a plausible on-foot speed. Distance is the
 * sum of haversine distances between accepted fixes.
 */
class LocationTracker(context: Context, private val looper: Looper) : LocationListener {

  fun interface Listener {
    fun onAccepted(point: LocationPoint, segmentMeters: Double)
  }

  private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
  private var listener: Listener? = null
  private var lastAccepted: LocationPoint? = null

  /** Whether the user is currently stationary; suppresses GPS drift accumulation. */
  @Volatile var isStationary: Boolean = true

  @Volatile var lastSpeed: Float? = null
    private set

  @Volatile var lastFix: LocationPoint? = null
    private set

  @SuppressLint("MissingPermission") // Checked by TrackingService before starting.
  fun start(resumeFrom: LocationPoint?, listener: Listener) {
    this.listener = listener
    lastAccepted = resumeFrom
    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
      locationManager.requestLocationUpdates(
          LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MS, 0f, this, looper)
    }
  }

  fun stop() {
    locationManager.removeUpdates(this)
    listener = null
    lastAccepted = null
  }

  override fun onLocationChanged(location: Location) {
    val point =
        LocationPoint(
            ts = location.time,
            lat = location.latitude,
            lng = location.longitude,
            accuracy = location.accuracy,
            speed = if (location.hasSpeed()) location.speed else 0f,
        )
    lastFix = point
    lastSpeed = if (location.hasSpeed()) location.speed else null
    if (location.accuracy > MAX_ACCURACY_M) return

    val previous = lastAccepted
    if (previous == null) {
      accept(point, 0.0)
      return
    }

    val meters = haversineMeters(previous.lat, previous.lng, point.lat, point.lng)
    val seconds = max(1L, point.ts - previous.ts) / 1000.0
    val minMove = max(MIN_SEGMENT_M, location.accuracy * 0.5)
    when {
      meters < minMove -> return
      meters / seconds > MAX_SPEED_MPS -> return // GPS jump
      isStationary && meters < STATIONARY_DRIFT_M -> return
      else -> accept(point, meters)
    }
  }

  private fun accept(point: LocationPoint, meters: Double) {
    lastAccepted = point
    listener?.onAccepted(point, meters)
  }

  @Deprecated("Required for API < 29")
  override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

  override fun onProviderEnabled(provider: String) = Unit

  override fun onProviderDisabled(provider: String) = Unit

  companion object {
    private const val UPDATE_INTERVAL_MS = 2_000L
    private const val MAX_ACCURACY_M = 25f
    private const val MIN_SEGMENT_M = 3.0
    private const val STATIONARY_DRIFT_M = 10.0
    private const val MAX_SPEED_MPS = 12.0 // ~43 km/h, faster is treated as a jump

    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
      val r = 6_371_000.0
      val dLat = Math.toRadians(lat2 - lat1)
      val dLng = Math.toRadians(lng2 - lng1)
      val a =
          sin(dLat / 2) * sin(dLat / 2) +
              cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
      return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
  }
}
