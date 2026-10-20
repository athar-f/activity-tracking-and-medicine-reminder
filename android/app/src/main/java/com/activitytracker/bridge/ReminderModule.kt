package com.activitytracker.bridge

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.activitytracker.auth.SessionManager
import com.activitytracker.data.MedicineRepository
import com.activitytracker.reminders.ReminderScheduler
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap

/** Medicine CRUD for React Native; scheduling itself is fully native (AlarmManager). */
class ReminderModule(context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {

  private val repository = MedicineRepository(context)
  private val scheduler = ReminderScheduler(context)
  private val sessionManager = SessionManager(context)

  override fun getName() = NAME

  @ReactMethod
  fun getMedicines(promise: Promise) {
    val user = sessionManager.currentUser ?: return promise.resolve(Arguments.createArray())
    runCatching {
          Arguments.createArray().apply {
            repository.getForUser(user.id).forEach { pushMap(Mappers.medicine(it)) }
          }
        }
        .onSuccess(promise::resolve)
        .onFailure { promise.reject("E_DB", it.message, it) }
  }

  @ReactMethod
  fun addMedicine(input: ReadableMap, promise: Promise) {
    val user = sessionManager.currentUser ?: return promise.reject("E_AUTH", "No user is signed in")
    val name = input.getString("name")?.trim().orEmpty()
    val hour = if (input.hasKey("hour")) input.getInt("hour") else -1
    val minute = if (input.hasKey("minute")) input.getInt("minute") else -1
    if (name.isEmpty() || hour !in 0..23 || minute !in 0..59) {
      promise.reject("E_INVALID", "Name and a valid time are required")
      return
    }
    runCatching {
          val medicine =
              repository.insert(
                  userId = user.id,
                  name = name,
                  dosage = input.getString("dosage")?.trim().orEmpty(),
                  hour = hour,
                  minute = minute,
                  notes = input.getString("notes")?.trim().orEmpty(),
              )
          scheduler.schedule(medicine)
          Mappers.medicine(medicine)
        }
        .onSuccess(promise::resolve)
        .onFailure { promise.reject("E_DB", it.message, it) }
  }

  @ReactMethod
  fun setMedicineEnabled(id: Double, enabled: Boolean, promise: Promise) {
    runCatching {
          val medicineId = id.toLong()
          repository.setEnabled(medicineId, enabled)
          val medicine = repository.get(medicineId) ?: error("Medicine not found")
          if (enabled) scheduler.schedule(medicine) else scheduler.cancel(medicineId)
          Mappers.medicine(medicine)
        }
        .onSuccess(promise::resolve)
        .onFailure { promise.reject("E_DB", it.message, it) }
  }

  @ReactMethod
  fun deleteMedicine(id: Double, promise: Promise) {
    runCatching {
          scheduler.cancel(id.toLong())
          repository.delete(id.toLong())
        }
        .onSuccess { promise.resolve(null) }
        .onFailure { promise.reject("E_DB", it.message, it) }
  }

  @ReactMethod
  fun getReminderStatus(promise: Promise) {
    promise.resolve(
        Arguments.createMap().apply {
          putBoolean("exactAlarmsAllowed", scheduler.canScheduleExact())
          putBoolean(
              "notificationsEnabled",
              NotificationManagerCompat.from(reactApplicationContext).areNotificationsEnabled())
          putInt("leadTimeMinutes", ReminderScheduler.LEAD_TIME_MINUTES)
        })
  }

  @ReactMethod
  fun openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val ctx = reactApplicationContext
    ctx.startActivity(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }

  @ReactMethod
  fun scheduleTestReminder(delaySeconds: Double, promise: Promise) {
    runCatching { scheduler.scheduleTest((delaySeconds * 1000).toLong()).toDouble() }
        .onSuccess(promise::resolve)
        .onFailure { promise.reject("E_SCHEDULE", it.message, it) }
  }

  companion object {
    const val NAME = "ReminderModule"
  }
}
