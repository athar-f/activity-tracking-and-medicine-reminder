package com.activitytracker.auth

import android.content.Context
import com.activitytracker.data.User

/** Persists the logged-in user so the native service and the RN bridge agree on who is active. */
class SessionManager(context: Context) {

  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  val isLoggedIn: Boolean
    get() = prefs.contains(KEY_USER_ID)

  val currentUser: User?
    get() {
      if (!isLoggedIn) return null
      return User(
          id = prefs.getLong(KEY_USER_ID, -1),
          username = prefs.getString(KEY_USERNAME, "") ?: "",
          displayName = prefs.getString(KEY_DISPLAY_NAME, "") ?: "",
      )
    }

  fun save(user: User) {
    prefs
        .edit()
        .putLong(KEY_USER_ID, user.id)
        .putString(KEY_USERNAME, user.username)
        .putString(KEY_DISPLAY_NAME, user.displayName)
        .apply()
  }

  fun clear() {
    prefs.edit().clear().apply()
  }

  private companion object {
    const val PREFS = "auth_session"
    const val KEY_USER_ID = "user_id"
    const val KEY_USERNAME = "username"
    const val KEY_DISPLAY_NAME = "display_name"
  }
}
