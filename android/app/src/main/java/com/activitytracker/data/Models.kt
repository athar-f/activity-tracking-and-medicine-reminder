package com.activitytracker.data

enum class ActivityType(val key: String) {
  STATIONARY("stationary"),
  WALKING("walking"),
  RUNNING("running");

  companion object {
    fun fromKey(key: String?): ActivityType = entries.firstOrNull { it.key == key } ?: STATIONARY
  }
}

data class User(val id: Long, val username: String, val displayName: String)

data class TrackingSession(
    val id: Long,
    val userId: Long,
    val startTime: Long,
    val endTime: Long?,
    val distanceM: Double,
    val stationaryMs: Long,
    val walkingMs: Long,
    val runningMs: Long,
    val avgLight: Double?,
    val avgPressure: Double?,
    val status: String,
) {
  val isActive: Boolean
    get() = status == STATUS_ACTIVE

  companion object {
    const val STATUS_ACTIVE = "active"
    const val STATUS_COMPLETED = "completed"
  }
}

data class SensorSample(
    val ts: Long,
    val activity: ActivityType,
    val intensity: Double,
    val light: Double?,
    val pressure: Double?,
)

data class LocationPoint(
    val ts: Long,
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val speed: Float,
)

data class Medicine(
    val id: Long,
    val userId: Long,
    val name: String,
    val dosage: String,
    val hour: Int,
    val minute: Int,
    val notes: String,
    val enabled: Boolean,
)
