package com.activitytracker.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor

class TrackingRepository(context: Context) {

  private val db = AppDatabase.get(context)

  fun createSession(userId: Long, startTime: Long): Long {
    val values =
        ContentValues().apply {
          put("user_id", userId)
          put("start_time", startTime)
          put("status", TrackingSession.STATUS_ACTIVE)
        }
    return db.writableDatabase.insertOrThrow("sessions", null, values)
  }

  fun updateSessionProgress(
      sessionId: Long,
      distanceM: Double,
      durations: Map<ActivityType, Long>,
  ) {
    val values =
        ContentValues().apply {
          put("distance_m", distanceM)
          put("stationary_ms", durations[ActivityType.STATIONARY] ?: 0L)
          put("walking_ms", durations[ActivityType.WALKING] ?: 0L)
          put("running_ms", durations[ActivityType.RUNNING] ?: 0L)
        }
    db.writableDatabase.update("sessions", values, "id = ?", arrayOf(sessionId.toString()))
  }

  /** Finalises a session and stores the environmental averages computed from its samples. */
  fun completeSession(sessionId: Long, endTime: Long) {
    val w = db.writableDatabase
    var avgLight: Double? = null
    var avgPressure: Double? = null
    w.rawQuery(
            "SELECT AVG(light), AVG(pressure) FROM samples WHERE session_id = ?",
            arrayOf(sessionId.toString()))
        .use { c ->
          if (c.moveToFirst()) {
            avgLight = c.doubleOrNull(0)
            avgPressure = c.doubleOrNull(1)
          }
        }
    val values =
        ContentValues().apply {
          put("end_time", endTime)
          put("status", TrackingSession.STATUS_COMPLETED)
          if (avgLight != null) put("avg_light", avgLight) else putNull("avg_light")
          if (avgPressure != null) put("avg_pressure", avgPressure) else putNull("avg_pressure")
        }
    w.update("sessions", values, "id = ?", arrayOf(sessionId.toString()))
  }

  fun insertSample(sessionId: Long, sample: SensorSample) {
    val values =
        ContentValues().apply {
          put("session_id", sessionId)
          put("ts", sample.ts)
          put("activity", sample.activity.key)
          put("intensity", sample.intensity)
          if (sample.light != null) put("light", sample.light) else putNull("light")
          if (sample.pressure != null) put("pressure", sample.pressure) else putNull("pressure")
        }
    db.writableDatabase.insert("samples", null, values)
  }

  fun insertLocation(sessionId: Long, point: LocationPoint) {
    val values =
        ContentValues().apply {
          put("session_id", sessionId)
          put("ts", point.ts)
          put("lat", point.lat)
          put("lng", point.lng)
          put("accuracy", point.accuracy)
          put("speed", point.speed)
        }
    db.writableDatabase.insert("locations", null, values)
  }

  fun getSessions(userId: Long): List<TrackingSession> =
      db.readableDatabase
          .rawQuery(
              "SELECT * FROM sessions WHERE user_id = ? ORDER BY start_time DESC",
              arrayOf(userId.toString()))
          .use { c -> generateSequence { if (c.moveToNext()) c.toSession() else null }.toList() }

  fun getSession(sessionId: Long): TrackingSession? =
      db.readableDatabase
          .rawQuery("SELECT * FROM sessions WHERE id = ?", arrayOf(sessionId.toString()))
          .use { c -> if (c.moveToFirst()) c.toSession() else null }

  fun getActiveSession(): TrackingSession? =
      db.readableDatabase
          .rawQuery(
              "SELECT * FROM sessions WHERE status = ? ORDER BY start_time DESC LIMIT 1",
              arrayOf(TrackingSession.STATUS_ACTIVE))
          .use { c -> if (c.moveToFirst()) c.toSession() else null }

  fun getSamples(sessionId: Long): List<SensorSample> =
      db.readableDatabase
          .rawQuery(
              "SELECT ts, activity, intensity, light, pressure FROM samples WHERE session_id = ? ORDER BY ts",
              arrayOf(sessionId.toString()))
          .use { c ->
            generateSequence {
                  if (!c.moveToNext()) null
                  else
                      SensorSample(
                          ts = c.getLong(0),
                          activity = ActivityType.fromKey(c.getString(1)),
                          intensity = c.getDouble(2),
                          light = c.doubleOrNull(3),
                          pressure = c.doubleOrNull(4),
                      )
                }
                .toList()
          }

  fun getLocations(sessionId: Long): List<LocationPoint> =
      db.readableDatabase
          .rawQuery(
              "SELECT ts, lat, lng, accuracy, speed FROM locations WHERE session_id = ? ORDER BY ts",
              arrayOf(sessionId.toString()))
          .use { c ->
            generateSequence {
                  if (!c.moveToNext()) null
                  else
                      LocationPoint(
                          ts = c.getLong(0),
                          lat = c.getDouble(1),
                          lng = c.getDouble(2),
                          accuracy = c.getFloat(3),
                          speed = c.getFloat(4),
                      )
                }
                .toList()
          }

  fun deleteSession(sessionId: Long) {
    db.writableDatabase.delete("sessions", "id = ?", arrayOf(sessionId.toString()))
  }

  private fun Cursor.toSession() =
      TrackingSession(
          id = getLong(getColumnIndexOrThrow("id")),
          userId = getLong(getColumnIndexOrThrow("user_id")),
          startTime = getLong(getColumnIndexOrThrow("start_time")),
          endTime = longOrNull(getColumnIndexOrThrow("end_time")),
          distanceM = getDouble(getColumnIndexOrThrow("distance_m")),
          stationaryMs = getLong(getColumnIndexOrThrow("stationary_ms")),
          walkingMs = getLong(getColumnIndexOrThrow("walking_ms")),
          runningMs = getLong(getColumnIndexOrThrow("running_ms")),
          avgLight = doubleOrNull(getColumnIndexOrThrow("avg_light")),
          avgPressure = doubleOrNull(getColumnIndexOrThrow("avg_pressure")),
          status = getString(getColumnIndexOrThrow("status")),
      )
}

internal fun Cursor.doubleOrNull(index: Int): Double? = if (isNull(index)) null else getDouble(index)

internal fun Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)
