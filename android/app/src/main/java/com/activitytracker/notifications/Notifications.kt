package com.activitytracker.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.activitytracker.R
import com.activitytracker.auth.LoginActivity

object Notifications {
  const val CHANNEL_TRACKING = "tracking"
  const val CHANNEL_REMINDERS = "medicine_reminders"

  const val TRACKING_NOTIFICATION_ID = 1001

  fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(
                CHANNEL_TRACKING,
                context.getString(R.string.channel_tracking_name),
                NotificationManager.IMPORTANCE_LOW)
            .apply { description = context.getString(R.string.channel_tracking_desc) })
    manager.createNotificationChannel(
        NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.channel_reminders_name),
                NotificationManager.IMPORTANCE_HIGH)
            .apply {
              description = context.getString(R.string.channel_reminders_desc)
              enableVibration(true)
            })
  }

  /** Opens the app through the login gate, which forwards to RN when already signed in. */
  fun openAppIntent(context: Context, requestCode: Int): PendingIntent =
      PendingIntent.getActivity(
          context,
          requestCode,
          Intent(context, LoginActivity::class.java)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
