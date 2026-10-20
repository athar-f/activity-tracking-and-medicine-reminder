package com.activitytracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Single SQLite database shared by the native layer (tracking service, reminders, auth)
 * and exposed to React Native through the bridge modules. Native code is the single
 * source of truth so data keeps flowing while the JS runtime is not alive.
 */
class AppDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

  override fun onConfigure(db: SQLiteDatabase) {
    super.onConfigure(db)
    db.setForeignKeyConstraintsEnabled(true)
    db.enableWriteAheadLogging()
  }

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE users (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          username TEXT NOT NULL UNIQUE,
          display_name TEXT NOT NULL,
          password_hash TEXT NOT NULL,
          salt TEXT NOT NULL,
          created_at INTEGER NOT NULL
        )
        """.trimIndent())

    db.execSQL(
        """
        CREATE TABLE sessions (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          user_id INTEGER NOT NULL,
          start_time INTEGER NOT NULL,
          end_time INTEGER,
          distance_m REAL NOT NULL DEFAULT 0,
          stationary_ms INTEGER NOT NULL DEFAULT 0,
          walking_ms INTEGER NOT NULL DEFAULT 0,
          running_ms INTEGER NOT NULL DEFAULT 0,
          avg_light REAL,
          avg_pressure REAL,
          status TEXT NOT NULL DEFAULT 'active'
        )
        """.trimIndent())

    db.execSQL(
        """
        CREATE TABLE samples (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          session_id INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
          ts INTEGER NOT NULL,
          activity TEXT NOT NULL,
          intensity REAL NOT NULL,
          light REAL,
          pressure REAL
        )
        """.trimIndent())
    db.execSQL("CREATE INDEX idx_samples_session ON samples(session_id, ts)")

    db.execSQL(
        """
        CREATE TABLE locations (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          session_id INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
          ts INTEGER NOT NULL,
          lat REAL NOT NULL,
          lng REAL NOT NULL,
          accuracy REAL NOT NULL,
          speed REAL NOT NULL
        )
        """.trimIndent())
    db.execSQL("CREATE INDEX idx_locations_session ON locations(session_id, ts)")

    db.execSQL(
        """
        CREATE TABLE medicines (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          user_id INTEGER NOT NULL,
          name TEXT NOT NULL,
          dosage TEXT NOT NULL,
          hour INTEGER NOT NULL,
          minute INTEGER NOT NULL,
          notes TEXT NOT NULL DEFAULT '',
          enabled INTEGER NOT NULL DEFAULT 1,
          created_at INTEGER NOT NULL
        )
        """.trimIndent())
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // Schema v1 only; future migrations go here.
  }

  companion object {
    private const val DB_NAME = "activity_tracker.db"
    private const val DB_VERSION = 1

    @Volatile private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) { instance ?: AppDatabase(context).also { instance = it } }
  }
}
