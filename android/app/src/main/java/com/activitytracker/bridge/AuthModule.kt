package com.activitytracker.bridge

import android.content.Intent
import com.activitytracker.auth.LoginActivity
import com.activitytracker.auth.SessionManager
import com.activitytracker.tracking.TrackingService
import com.activitytracker.tracking.TrackingState
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/** Exposes the natively authenticated user to RN and hands logout back to [LoginActivity]. */
class AuthModule(context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {

  private val sessionManager = SessionManager(context)

  override fun getName() = NAME

  @ReactMethod
  fun getCurrentUser(promise: Promise) {
    val user = sessionManager.currentUser
    promise.resolve(user?.let(Mappers::user))
  }

  @ReactMethod
  fun logout(promise: Promise) {
    val ctx = reactApplicationContext
    if (TrackingState.snapshot.isTracking) TrackingService.stop(ctx)
    sessionManager.clear()
    ctx.startActivity(
        Intent(ctx, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    promise.resolve(null)
  }

  companion object {
    const val NAME = "AuthModule"
  }
}
