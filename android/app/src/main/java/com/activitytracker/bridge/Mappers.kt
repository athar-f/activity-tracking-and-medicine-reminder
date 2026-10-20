package com.activitytracker.bridge

import com.activitytracker.data.ActivityType
import com.activitytracker.data.LocationPoint
import com.activitytracker.data.Medicine
import com.activitytracker.data.SensorSample
import com.activitytracker.data.TrackingSession
import com.activitytracker.data.User
import com.activitytracker.reminders.ReminderScheduler
import com.activitytracker.tracking.TrackingSnapshot
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap

/** Conversions from native models to the JS shapes declared in src/native/types.ts. */
internal object Mappers {

  fun user(user: User): WritableMap =
      Arguments.createMap().apply {
        putDouble("id", user.id.toDouble())
        putString("username", user.username)
        putString("displayName", user.displayName)
      }

  fun durations(map: Map<ActivityType, Long>): WritableMap =
      Arguments.createMap().apply {
        ActivityType.entries.forEach { putDouble(it.key, (map[it] ?: 0L).toDouble()) }
      }

  fun snapshot(s: TrackingSnapshot, now: Long = System.currentTimeMillis()): WritableMap =
      Arguments.createMap().apply {
        putBoolean("isTracking", s.isTracking)
        putNullableDouble("sessionId", s.sessionId?.toDouble())
        putNullableDouble("startTime", s.startTime?.toDouble())
        putNullableDouble("elapsedMs", s.startTime?.let { (now - it).toDouble() })
        putString("activity", s.activity.key)
        putDouble("activityDurationMs", s.activityStartedAt?.let { (now - it).toDouble() } ?: 0.0)
        putMap("durations", durations(s.durations))
        putDouble("distanceM", s.distanceM)
        putNullableDouble("lightLux", s.lightLux)
        putNullableDouble("pressureHpa", s.pressureHpa)
        putDouble("intensity", s.intensity)
        putDouble("cadenceHz", s.cadenceHz)
        if (s.lastLocation != null) putMap("lastLocation", location(s.lastLocation))
        else putNull("lastLocation")
      }

  fun session(s: TrackingSession): WritableMap =
      Arguments.createMap().apply {
        putDouble("id", s.id.toDouble())
        putDouble("startTime", s.startTime.toDouble())
        putNullableDouble("endTime", s.endTime?.toDouble())
        putDouble("distanceM", s.distanceM)
        putMap(
            "durations",
            durations(
                mapOf(
                    ActivityType.STATIONARY to s.stationaryMs,
                    ActivityType.WALKING to s.walkingMs,
                    ActivityType.RUNNING to s.runningMs,
                )))
        putNullableDouble("avgLight", s.avgLight)
        putNullableDouble("avgPressure", s.avgPressure)
        putString("status", s.status)
      }

  fun sessions(list: List<TrackingSession>): WritableArray =
      Arguments.createArray().apply { list.forEach { pushMap(session(it)) } }

  fun sample(s: SensorSample): WritableMap =
      Arguments.createMap().apply {
        putDouble("ts", s.ts.toDouble())
        putString("activity", s.activity.key)
        putDouble("intensity", s.intensity)
        putNullableDouble("light", s.light)
        putNullableDouble("pressure", s.pressure)
      }

  fun location(p: LocationPoint): WritableMap =
      Arguments.createMap().apply {
        putDouble("ts", p.ts.toDouble())
        putDouble("latitude", p.lat)
        putDouble("longitude", p.lng)
        putDouble("accuracy", p.accuracy.toDouble())
        putDouble("speed", p.speed.toDouble())
      }

  fun medicine(m: Medicine, now: Long = System.currentTimeMillis()): WritableMap =
      Arguments.createMap().apply {
        putDouble("id", m.id.toDouble())
        putString("name", m.name)
        putString("dosage", m.dosage)
        putInt("hour", m.hour)
        putInt("minute", m.minute)
        putString("notes", m.notes)
        putBoolean("enabled", m.enabled)
        putNullableDouble(
            "nextReminderAt",
            if (m.enabled) ReminderScheduler.nextTriggerTime(m.hour, m.minute, now).toDouble() else null)
      }

  private fun WritableMap.putNullableDouble(key: String, value: Double?) {
    if (value == null) putNull(key) else putDouble(key, value)
  }
}
