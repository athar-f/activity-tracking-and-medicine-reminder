package com.activitytracker

import android.app.Application
import com.activitytracker.bridge.AppPackage
import com.activitytracker.notifications.Notifications
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost

class MainApplication : Application(), ReactApplication {

  override val reactHost: ReactHost by lazy {
    getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          // Kotlin modules: tracking service, reminders and auth bridge.
          add(AppPackage())
        },
    )
  }

  override fun onCreate() {
    super.onCreate()
    Notifications.ensureChannels(this)
    loadReactNative(this)
  }
}
