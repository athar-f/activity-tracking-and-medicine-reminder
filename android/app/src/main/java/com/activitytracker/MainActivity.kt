package com.activitytracker

import android.content.Intent
import android.os.Bundle
import com.activitytracker.auth.LoginActivity
import com.activitytracker.auth.SessionManager
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

/** Hosts the React Native UI. Only reachable once [LoginActivity] has authenticated a user. */
class MainActivity : ReactActivity() {

  override fun getMainComponentName(): String = "ActivityTracker"

  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

  override fun onCreate(savedInstanceState: Bundle?) {
    // Pass null so react-native-screens does not try to restore fragments after process death.
    super.onCreate(null)
    if (!SessionManager(this).isLoggedIn) {
      startActivity(
          Intent(this, LoginActivity::class.java)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
      finish()
    }
  }
}
