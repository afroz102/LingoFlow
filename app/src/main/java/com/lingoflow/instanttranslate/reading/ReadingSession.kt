package com.lingoflow.instanttranslate.reading

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat

object ReadingSession {
    /** Called only from a visible activity after an explicit user action. */
    fun start(context: Context, selectedText: String? = null): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        return try {
            ContextCompat.startForegroundService(context, Intent(context, ReadingOverlayService::class.java)
                .setAction(if (selectedText == null) ReadingOverlayService.ACTION_START else ReadingOverlayService.ACTION_TRANSLATE)
                .putExtra(ReadingOverlayService.EXTRA_TEXT, selectedText))
            true
        } catch (_: RuntimeException) { false }
    }

    fun stop(context: Context) { context.stopService(Intent(context, ReadingOverlayService::class.java)) }
}
