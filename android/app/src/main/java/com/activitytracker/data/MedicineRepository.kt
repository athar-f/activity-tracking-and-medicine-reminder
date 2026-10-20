package com.activitytracker.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor

class MedicineRepository(context: Context) {

  private val db = AppDatabase.get(context)

  fun insert(userId: Long, name: String, dosage: String, hour: Int, minute: Int, notes: String): Medicine {
    val values =
        ContentValues().apply {
          put("user_id", userId)
          put("name", name)
          put("dosage", dosage)
          put("hour", hour)
          put("minute", minute)
          put("notes", notes)
          put("enabled", 1)
          put("created_at", System.currentTimeMillis())
        }
    val id = db.writableDatabase.insertOrThrow("medicines", null, values)
    return Medicine(id, userId, name, dosage, hour, minute, notes, enabled = true)
  }

  fun setEnabled(id: Long, enabled: Boolean) {
    val values = ContentValues().apply { put("enabled", if (enabled) 1 else 0) }
    db.writableDatabase.update("medicines", values, "id = ?", arrayOf(id.toString()))
  }

  fun delete(id: Long) {
    db.writableDatabase.delete("medicines", "id = ?", arrayOf(id.toString()))
  }

  fun get(id: Long): Medicine? =
      db.readableDatabase
          .rawQuery("SELECT * FROM medicines WHERE id = ?", arrayOf(id.toString()))
          .use { c -> if (c.moveToFirst()) c.toMedicine() else null }

  fun getForUser(userId: Long): List<Medicine> =
      db.readableDatabase
          .rawQuery(
              "SELECT * FROM medicines WHERE user_id = ? ORDER BY hour, minute",
              arrayOf(userId.toString()))
          .use { c -> generateSequence { if (c.moveToNext()) c.toMedicine() else null }.toList() }

  /** All enabled medicines across users — reminders fire regardless of who is logged in. */
  fun getAllEnabled(): List<Medicine> =
      db.readableDatabase.rawQuery("SELECT * FROM medicines WHERE enabled = 1", null).use { c ->
        generateSequence { if (c.moveToNext()) c.toMedicine() else null }.toList()
      }

  private fun Cursor.toMedicine() =
      Medicine(
          id = getLong(getColumnIndexOrThrow("id")),
          userId = getLong(getColumnIndexOrThrow("user_id")),
          name = getString(getColumnIndexOrThrow("name")),
          dosage = getString(getColumnIndexOrThrow("dosage")),
          hour = getInt(getColumnIndexOrThrow("hour")),
          minute = getInt(getColumnIndexOrThrow("minute")),
          notes = getString(getColumnIndexOrThrow("notes")),
          enabled = getInt(getColumnIndexOrThrow("enabled")) == 1,
      )
}
