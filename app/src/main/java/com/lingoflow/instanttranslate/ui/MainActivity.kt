package com.lingoflow.instanttranslate.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.lingoflow.instanttranslate.R
import com.lingoflow.instanttranslate.prefs.DisclosurePreferences
import com.lingoflow.instanttranslate.reading.ClipboardAccess
import com.lingoflow.instanttranslate.reading.ReadingSession
import com.lingoflow.instanttranslate.reading.ReadingNotifications
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** User-visible setup and stop controls. No session starts on boot or through a background alarm. */
class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { startReading() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        fun text(res: Int, size: Float = 16f): TextView = TextView(this).apply {
            setText(res); textSize = size; setPadding(0, 12, 0, 24); content.addView(this)
        }
        fun button(res: Int, action: () -> Unit) { content.addView(Button(this).apply {
            setText(res); isAllCaps = false; setOnClickListener { action() }
        }) }
        text(R.string.app_name, 28f)
        text(R.string.setup_intro)
        status = text(R.string.clipboard_manual)
        text(R.string.setup_keyboard_explanation)
        button(R.string.setup_enable_keyboard) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        button(R.string.setup_choose_keyboard) { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
        text(R.string.setup_writing)
        text(R.string.setup_optional_floating, 20f)
        button(R.string.setup_overlay) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
            catch (_: RuntimeException) { Toast.makeText(this, R.string.overlay_unavailable, Toast.LENGTH_LONG).show() }
        }
        button(R.string.setup_start) {
            val disclosure = DisclosurePreferences(this)
            if (!disclosure.isAcknowledged()) {
                MaterialAlertDialogBuilder(this).setTitle(R.string.app_name).setMessage(R.string.disclosure_message)
                    .setPositiveButton(R.string.action_continue) { _, _ -> disclosure.acknowledge(); startReading() }
                    .setNegativeButton(android.R.string.cancel, null).show()
            } else startReading()
        }
        button(R.string.setup_stop) { ReadingSession.stop(this); Toast.makeText(this, R.string.session_stopped, Toast.LENGTH_SHORT).show() }
        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onResume() {
        super.onResume()
        status.setText(if (ClipboardAccess.hasKeyboardAccess(this)) R.string.clipboard_automatic else R.string.clipboard_manual)
    }

    private fun startReading() {
        if (Settings.canDrawOverlays(this) && ReadingNotifications.shouldRequest(this)) {
            ReadingNotifications.markRequested(this)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (ReadingSession.start(this)) {
            Toast.makeText(this, R.string.session_started, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, R.string.overlay_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}
