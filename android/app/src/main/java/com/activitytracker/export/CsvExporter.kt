package com.activitytracker.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.activitytracker.data.TrackingRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes a session (summary, sensor samples, GPS route) to CSV and exposes it via FileProvider. */
class CsvExporter(private val context: Context) {

  private val repository = TrackingRepository(context)
  private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

  fun export(sessionId: Long): File {
    val session = repository.getSession(sessionId) ?: error("Session $sessionId not found")
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "session_${sessionId}.csv")

    file.bufferedWriter().use { w ->
      w.appendLine("# Session summary")
      w.appendLine("session_id,start,end,distance_m,stationary_s,walking_s,running_s,avg_light_lux,avg_pressure_hpa")
      w.appendLine(
          listOf(
                  session.id,
                  iso.format(Date(session.startTime)),
                  session.endTime?.let { iso.format(Date(it)) }.orEmpty(),
                  "%.1f".format(Locale.US, session.distanceM),
                  session.stationaryMs / 1000,
                  session.walkingMs / 1000,
                  session.runningMs / 1000,
                  session.avgLight?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
                  session.avgPressure?.let { "%.2f".format(Locale.US, it) }.orEmpty(),
              )
              .joinToString(","))
      w.appendLine()

      w.appendLine("# Sensor samples")
      w.appendLine("timestamp,activity,intensity,light_lux,pressure_hpa")
      repository.getSamples(sessionId).forEach { s ->
        w.appendLine(
            "${iso.format(Date(s.ts))},${s.activity.key},${"%.3f".format(Locale.US, s.intensity)}," +
                "${s.light?.let { "%.1f".format(Locale.US, it) }.orEmpty()}," +
                s.pressure?.let { "%.2f".format(Locale.US, it) }.orEmpty())
      }
      w.appendLine()

      w.appendLine("# GPS route")
      w.appendLine("timestamp,latitude,longitude,accuracy_m,speed_mps")
      repository.getLocations(sessionId).forEach { p ->
        w.appendLine("${iso.format(Date(p.ts))},${p.lat},${p.lng},${p.accuracy},${p.speed}")
      }
    }
    return file
  }

  fun uriFor(file: File): Uri =
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

  fun shareIntent(file: File): Intent {
    val send =
        Intent(Intent.ACTION_SEND).apply {
          type = "text/csv"
          putExtra(Intent.EXTRA_STREAM, uriFor(file))
          putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    return Intent.createChooser(send, "Export session").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
}
