package com.lingoflow.instanttranslate.reading

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Ask once at session start; denial must not block translation or cause repeated prompts. */
object ReadingNotifications {
    fun shouldRequest(context: Context): Boolean = Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
        !context.getSharedPreferences("reading_setup", Context.MODE_PRIVATE).getBoolean("notification_requested", false)

    fun markRequested(context: Context) {
        context.getSharedPreferences("reading_setup", Context.MODE_PRIVATE).edit().putBoolean("notification_requested", true).apply()
    }
}
