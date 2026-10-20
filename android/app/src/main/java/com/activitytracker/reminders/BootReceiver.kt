package com.activitytracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Alarms are cleared on reboot and can drift on clock/time-zone changes, so every enabled
 * reminder is re-registered on these system events.
 */
class BootReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action !in HANDLED_ACTIONS) return
    val pending = goAsync()
    Thread {
          try {
            ReminderScheduler(context).rescheduleAll()
          } finally {
            pending.finish()
          }
        }
        .start()
  }

  private companion object {
    val HANDLED_ACTIONS =
        setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
  }
}
