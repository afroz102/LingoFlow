package com.lingoflow.instanttranslate.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.graphics.Color
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.widget.ImageView
import android.view.Gravity
import android.view.View
import com.google.android.material.button.MaterialButton
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
        val ink = Color.parseColor("#E5EEEE")
        val muted = Color.parseColor("#A2B4BC")
        val backgroundColor = Color.parseColor("#18252B")
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        fun text(res: Int, size: Float = 16f): TextView = TextView(this).apply {
            setText(res); textSize = size; setTextColor(if (size >= 20) ink else muted)
            setLineSpacing(dp(3).toFloat(), 1f); setPadding(0, dp(8), 0, dp(12))
            if (size >= 20) setTypeface(typeface, Typeface.BOLD)
            content.addView(this)
        }
        fun button(res: Int, action: () -> Unit) { content.addView(MaterialButton(this).apply {
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#344F5B"))
            setTextColor(ink)
            setText(res); isAllCaps = false; cornerRadius = dp(16); insetTop = dp(4); insetBottom = dp(4)
            textSize = 15f; setOnClickListener { action() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60))) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(12)) }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_lingoboard_logo); contentDescription = getString(R.string.keyboard_logo)
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        header.addView(TextView(this).apply {
            text = getString(R.string.setup_version, com.lingoflow.instanttranslate.BuildConfig.VERSION_NAME)
            textSize = 11f; letterSpacing = 0.06f; setTextColor(muted); setPadding(dp(16), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(header)
        text(R.string.setup_intro)
        status = text(R.string.clipboard_manual)
        text(R.string.setup_keyboard_explanation)
        button(R.string.setup_enable_keyboard) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        button(R.string.setup_choose_keyboard) { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
        // Suggestions learn typed words on this device only; users must be able to wipe them.
        button(R.string.setup_clear_learned) {
            com.lingoflow.instanttranslate.keyboard.SuggestionEngine.clearLearned(this)
            Toast.makeText(this, R.string.setup_learned_cleared, Toast.LENGTH_SHORT).show()
        }
        text(R.string.setup_writing)
        val optionalStart = content.childCount
        button(R.string.setup_optional_floating) {
            val first = content.getChildAt(optionalStart + 1)
            val visibility = if (first.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            for (index in optionalStart + 1 until content.childCount) content.getChildAt(index).visibility = visibility
        }
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
        for (index in optionalStart + 1 until content.childCount) content.getChildAt(index).visibility = View.GONE
        setContentView(ScrollView(this).apply { setBackgroundColor(backgroundColor); addView(content) })
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
