package com.activitytracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.activitytracker.R
import com.activitytracker.data.MedicineRepository
import com.activitytracker.notifications.Notifications
import java.util.Locale

/** Posts the offline medicine notification and chains the next day's alarm. */
class ReminderReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    Notifications.ensureChannels(context)
    when (intent.action) {
      ACTION_TEST ->
          notify(
              context,
              TEST_NOTIFICATION_ID,
              context.getString(R.string.reminder_test_title),
              context.getString(R.string.reminder_test_body))
      ACTION_REMIND -> handleReminder(context, intent.getLongExtra(EXTRA_MEDICINE_ID, -1))
    }
  }

  private fun handleReminder(context: Context, medicineId: Long) {
    val pending = goAsync()
    Thread {
          try {
            val medicine = MedicineRepository(context).get(medicineId)
            if (medicine == null || !medicine.enabled) return@Thread
            val time = String.format(Locale.US, "%02d:%02d", medicine.hour, medicine.minute)
            val dosage = medicine.dosage.ifBlank { medicine.name }
            notify(
                context,
                NOTIFICATION_ID_BASE + medicine.id.toInt(),
                context.getString(R.string.reminder_title, medicine.name),
                context.getString(R.string.reminder_body, dosage, time) +
                    if (medicine.notes.isNotBlank()) "\n${medicine.notes}" else "")
            ReminderScheduler(context).schedule(medicine)
          } finally {
            pending.finish()
          }
        }
        .start()
  }

  private fun notify(context: Context, id: Int, title: String, body: String) {
    val notification =
        NotificationCompat.Builder(context, Notifications.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(Notifications.openAppIntent(context, id))
            .build()
    try {
      NotificationManagerCompat.from(context).notify(id, notification)
    } catch (e: SecurityException) {
      Log.w(TAG, "POST_NOTIFICATIONS not granted", e)
    }
  }

  companion object {
    private const val TAG = "ReminderReceiver"
    const val ACTION_REMIND = "com.activitytracker.reminders.REMIND"
    const val ACTION_TEST = "com.activitytracker.reminders.TEST"
    const val EXTRA_MEDICINE_ID = "medicine_id"
    private const val NOTIFICATION_ID_BASE = 2000
    private const val TEST_NOTIFICATION_ID = 1999
  }
}
