package com.activitytracker.tracking

import com.activitytracker.data.ActivityType
import com.activitytracker.data.LocationPoint
import java.util.concurrent.CopyOnWriteArraySet

/** Latest live tracking values, published by [TrackingService] once per second. */
data class TrackingSnapshot(
    val isTracking: Boolean = false,
    val sessionId: Long? = null,
    val startTime: Long? = null,
    val activity: ActivityType = ActivityType.STATIONARY,
    val activityStartedAt: Long? = null,
    val durations: Map<ActivityType, Long> = emptyMap(),
    val distanceM: Double = 0.0,
    val lightLux: Double? = null,
    val pressureHpa: Double? = null,
    val intensity: Double = 0.0,
    val cadenceHz: Double = 0.0,
    val lastLocation: LocationPoint? = null,
)

/**
 * Process-wide hub between the background service and the React Native bridge. The service
 * writes; the bridge subscribes only while JS has listeners, so nothing leaks when RN is gone.
 */
object TrackingState {

  fun interface SnapshotListener {
    fun onSnapshot(snapshot: TrackingSnapshot)
  }

  fun interface LocationListener {
    fun onLocation(sessionId: Long, point: LocationPoint)
  }

  @Volatile
  var snapshot = TrackingSnapshot()
    private set

  private val snapshotListeners = CopyOnWriteArraySet<SnapshotListener>()
  private val locationListeners = CopyOnWriteArraySet<LocationListener>()

  fun publish(next: TrackingSnapshot) {
    snapshot = next
    snapshotListeners.forEach { it.onSnapshot(next) }
  }

  fun publishLocation(sessionId: Long, point: LocationPoint) {
    locationListeners.forEach { it.onLocation(sessionId, point) }
  }

  fun addSnapshotListener(listener: SnapshotListener) = snapshotListeners.add(listener)

  fun removeSnapshotListener(listener: SnapshotListener) = snapshotListeners.remove(listener)

  fun addLocationListener(listener: LocationListener) = locationListeners.add(listener)

  fun removeLocationListener(listener: LocationListener) = locationListeners.remove(listener)
}
