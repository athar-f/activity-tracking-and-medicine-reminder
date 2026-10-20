package com.activitytracker.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.activitytracker.data.Medicine
import com.activitytracker.data.MedicineRepository
import java.util.Calendar

/**
 * Schedules one exact, doze-safe alarm per medicine, [LEAD_TIME_MINUTES] before the dose.
 * Alarms are owned by the OS, so they fire with the app closed or killed. Each alarm
 * reschedules the next day's occurrence when it fires (see [ReminderReceiver]).
 */
class ReminderScheduler(private val context: Context) {

  private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

  fun canScheduleExact(): Boolean =
      Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

  /** Returns the trigger time (epoch ms) of the scheduled reminder. */
  fun schedule(medicine: Medicine, now: Long = System.currentTimeMillis()): Long {
    val triggerAt = nextTriggerTime(medicine.hour, medicine.minute, now)
    setAlarm(triggerAt, pendingIntent(medicine.id, ReminderReceiver.ACTION_REMIND))
    return triggerAt
  }

  fun cancel(medicineId: Long) {
    alarmManager.cancel(pendingIntent(medicineId, ReminderReceiver.ACTION_REMIND))
  }

  fun rescheduleAll() {
    MedicineRepository(context).getAllEnabled().forEach { schedule(it) }
  }

  /** Fires a demo reminder after [delayMs], used to verify notifications with the app closed. */
  fun scheduleTest(delayMs: Long): Long {
    val triggerAt = System.currentTimeMillis() + delayMs
    setAlarm(triggerAt, pendingIntent(TEST_REQUEST_ID, ReminderReceiver.ACTION_TEST))
    return triggerAt
  }

  private fun setAlarm(triggerAt: Long, operation: PendingIntent) {
    if (canScheduleExact()) {
      alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
    } else {
      // Exact alarms revoked by the user: still deliver, within a small OS-chosen window.
      alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
    }
  }

  private fun pendingIntent(medicineId: Long, action: String): PendingIntent =
      PendingIntent.getBroadcast(
          context,
          medicineId.toInt(),
          Intent(context, ReminderReceiver::class.java)
              .setAction(action)
              .putExtra(ReminderReceiver.EXTRA_MEDICINE_ID, medicineId),
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

  companion object {
    const val LEAD_TIME_MINUTES = 15
    private const val TEST_REQUEST_ID = -1L

    /** Next moment, strictly after [now], that is [LEAD_TIME_MINUTES] before hour:minute. */
    fun nextTriggerTime(hour: Int, minute: Int, now: Long): Long {
      val cal =
          Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, -LEAD_TIME_MINUTES)
          }
      while (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
      return cal.timeInMillis
    }
  }
}
