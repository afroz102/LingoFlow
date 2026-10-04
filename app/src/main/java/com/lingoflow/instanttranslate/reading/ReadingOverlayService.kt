package com.lingoflow.instanttranslate.reading

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.lingoflow.instanttranslate.R
import com.lingoflow.instanttranslate.cloud.AndroidConnectivityChecker
import com.lingoflow.instanttranslate.coordinator.TranslateCoordinator
import com.lingoflow.instanttranslate.coordinator.TranslationOutcome
import com.lingoflow.instanttranslate.direction.Direction
import com.lingoflow.instanttranslate.direction.DirectionDetector
import com.lingoflow.instanttranslate.prefs.DisclosurePreferences
import com.lingoflow.instanttranslate.provider.FailureReason
import com.lingoflow.instanttranslate.provider.backend.BackendTranslationProvider
import com.lingoflow.instanttranslate.timing.TranslationTimeline
import com.lingoflow.instanttranslate.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** One visible, user-started reading session. Messages/results live only in this service's memory. */
class ReadingOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val deduplicator = ClipboardDeduplicator()
    private lateinit var windows: WindowManager
    private lateinit var clipboard: ClipboardManager
    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var request: Job? = null
    private var message: String? = null
    private var result: String? = null
    private var phase = Phase.IDLE
    private var failureRes = R.string.error_generic
    private var isBubble = true
    private var capturing = false
    private var generation = 0
    private val expirePrompt = Runnable {
        if (phase == Phase.PROMPT) { clearMessage(); showBubble() }
    }
    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        // An overlay permission is not clipboard access. Only our selected IME permits auto-read.
        if (!capturing && ClipboardAccess.hasKeyboardAccess(this) && canReadNow()) {
            val text = ClipboardAccess.readText(this)
            if (text != null) {
                if (deduplicator.accept(text)) offerTranslation(text)
            } else if (phase == Phase.PROMPT) {
                // A new ineligible clip must not leave a prompt referring to the previous message.
                clearMessage(); showBubble()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ReadingSession.isRunning = true
        windows = getSystemService(WindowManager::class.java)
        clipboard = getSystemService(ClipboardManager::class.java)
        createNotification()
        clipboard.addPrimaryClipChangedListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == ACTION_TRANSLATE) {
            val text = intent.getStringExtra(EXTRA_TEXT)
            if (text != null && DirectionDetector.isSupported(text)) {
                clearMessage()
                message = text
                translateConfirmed()
            } else {
                clearMessage(); phase = Phase.ERROR; failureRes = R.string.error_generic; showCard()
            }
        } else if (root == null) showBubble()
        // Never restart clipboard monitoring after a system/process kill without another user action.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ReadingSession.isRunning = false
        clipboard.removePrimaryClipChangedListener(listener)
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        removeWindow()
        message = null; result = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (root != null) { if (isBubble) showBubble() else showCard() }
    }

    private fun canReadNow(): Boolean =
        !getSystemService(KeyguardManager::class.java).isDeviceLocked &&
            getSystemService(PowerManager::class.java).isInteractive

    private fun createNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.session_channel), NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, ReadingOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startForeground(42, NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle(getString(R.string.session_channel))
            .setContentText(getString(R.string.session_notification)).setContentIntent(open)
            .setOngoing(true).addAction(0, getString(R.string.action_stop), stop).build())
    }

    private fun offerTranslation(text: String) {
        clearMessage()
        message = text
        phase = Phase.PROMPT
        showCard()
        handler.postDelayed(expirePrompt, 60_000)
    }

    private fun clearMessage() {
        generation++
        request?.cancel(); request = null
        handler.removeCallbacks(expirePrompt)
        message = null; result = null; phase = Phase.IDLE
        capturing = false
    }

    private fun translateConfirmed() {
        val text = message ?: return
        handler.removeCallbacks(expirePrompt)
        phase = Phase.LOADING
        showCard()
        val currentGeneration = ++generation
        TranslationTimeline.startRun()
        request = scope.launch {
            val outcome = TranslateCoordinator(BackendTranslationProvider(), DisclosurePreferences(this@ReadingOverlayService),
                AndroidConnectivityChecker(this@ReadingOverlayService)).translate(text, Direction.READ_TO_ENGLISH)
            if (currentGeneration != generation) return@launch
            when (outcome) {
                is TranslationOutcome.Translated -> { result = outcome.translated; phase = Phase.RESULT }
                is TranslationOutcome.DisclosureRequired -> phase = Phase.DISCLOSURE
                is TranslationOutcome.Offline -> { phase = Phase.ERROR; failureRes = R.string.error_offline }
                is TranslationOutcome.Failed -> {
                    phase = Phase.ERROR
                    failureRes = when (outcome.reason) {
                        FailureReason.UNSUPPORTED_INPUT -> R.string.error_generic
                        FailureReason.RATE_LIMITED -> R.string.error_rate_limited
                        FailureReason.TIMEOUT -> R.string.error_timeout
                        FailureReason.PROVIDER_ERROR -> R.string.error_provider
                        FailureReason.INVALID_RESPONSE -> R.string.error_invalid_response
                    }
                }
            }
            // A minimized request remains minimized until the user opens it.
            if (isBubble) showBubble() else showCard()
        }
    }

    private fun showBubble() {
        isBubble = true
        val button = Button(themedContext()).apply {
            text = getString(R.string.reading_bubble)
            isAllCaps = false
            contentDescription = getString(R.string.reading_bubble_description)
            setOnClickListener {
                if (phase != Phase.IDLE) showCard() else captureCopiedText()
            }
            setOnLongClickListener { stopSelf(); true }
        }
        attach(button, bubble = true)
        draggable(button, click = true)
    }

    /** A user tap may focus a small overlay briefly. We never acquire focus on a clipboard event. */
    private fun captureCopiedText() {
        if (!canReadNow()) return
        clearMessage()
        capturing = true
        val captureGeneration = generation
        isBubble = false
        phase = Phase.CAPTURING
        showCard(focusable = true)
        root?.requestFocus()
        fun readAfterFocus(attempt: Int) {
            handler.postDelayed({
                if (!capturing || root == null || captureGeneration != generation) return@postDelayed
                if (root?.hasWindowFocus() != true && attempt < 6) { readAfterFocus(attempt + 1); return@postDelayed }
                val copied = if (root?.hasWindowFocus() == true) ClipboardAccess.readText(this) else null
                capturing = false
                if (copied == null) {
                    phase = Phase.ERROR; failureRes = R.string.clipboard_unavailable; showCard()
                } else {
                    message = copied
                    // Tapping the bubble explicitly requests a translation of the current clipboard.
                    translateConfirmed()
                }
            }, 80)
        }
        readAfterFocus(0)
    }

    private fun showCard(focusable: Boolean = false) {
        isBubble = false
        val context = themedContext()
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val foreground = if (dark) Color.WHITE else Color.rgb(24, 32, 40)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(12); setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(30, 35, 43) else Color.WHITE)
                cornerRadius = dp(16).toFloat(); setStroke(dp(1), Color.rgb(98, 117, 142))
            }
            elevation = dp(8).toFloat()
        }
        val title = TextView(context).apply {
            setText(R.string.reading_title); textSize = 18f; setTextColor(foreground)
            setTypeface(null, Typeface.BOLD); setPadding(dp(4), dp(8), dp(4), dp(12))
        }
        card.addView(title)
        val body = TextView(context).apply {
            textSize = 16f; setTextColor(foreground); setPadding(dp(4), dp(6), dp(4), dp(12))
            text = when (phase) {
                Phase.PROMPT -> getString(R.string.reading_prompt)
                Phase.CAPTURING -> getString(R.string.reading_capturing)
                Phase.LOADING -> getString(R.string.reading_loading)
                Phase.RESULT -> result.orEmpty()
                Phase.ERROR -> getString(failureRes)
                Phase.DISCLOSURE -> getString(R.string.disclosure_message)
                Phase.IDLE -> getString(R.string.clipboard_manual)
            }
        }
        card.addView(object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                    (resources.displayMetrics.heightPixels * 0.48f).toInt(), MeasureSpec.AT_MOST))
            }
        }.apply { addView(body) })
        val actions = LinearLayout(context)
        card.addView(actions)
        fun button(res: Int, action: () -> Unit) {
            actions.addView(Button(context).apply {
                setText(res); isAllCaps = false; textSize = 12f; minimumWidth = 0; minWidth = 0; setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        when (phase) {
            Phase.PROMPT -> button(R.string.action_translate) { translateConfirmed() }
            Phase.DISCLOSURE -> button(R.string.action_continue) {
                DisclosurePreferences(this).acknowledge(); translateConfirmed()
            }
            Phase.ERROR -> if (message != null) button(R.string.action_retry) { translateConfirmed() }
                else button(R.string.action_read_clipboard) { captureCopiedText() }
            Phase.RESULT -> button(R.string.action_copy) {
                result?.let { clipboard.setPrimaryClip(ClipData.newPlainText(ClipboardAccess.OWN_CLIP_LABEL, it)) }
                Toast.makeText(this, R.string.copied_confirmation, Toast.LENGTH_SHORT).show()
            }
            else -> Unit
        }
        button(R.string.action_minimize) { if (capturing) clearMessage(); showBubble() }
        button(R.string.action_close) { clearMessage(); showBubble() }
        button(R.string.action_stop) { stopSelf() }
        attach(card, bubble = false, focusable = focusable)
        draggable(title, click = false)
    }

    private fun themedContext() = ContextThemeWrapper(this, R.style.Theme_InstantTranslate)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun attach(view: View, bubble: Boolean, focusable: Boolean = false) {
        val oldX = params?.x ?: dp(12)
        val oldY = params?.y ?: dp(120)
        removeWindow()
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val flags = WindowManager.LayoutParams.FLAG_SECURE or
            if (focusable) WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val layout = WindowManager.LayoutParams(
            if (bubble) WindowManager.LayoutParams.WRAP_CONTENT else minOf(dp(360), resources.displayMetrics.widthPixels - dp(24)),
            WindowManager.LayoutParams.WRAP_CONTENT, type, flags, android.graphics.PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = oldX.coerceIn(0, maxOf(0, resources.displayMetrics.widthPixels - if (bubble) dp(120) else width))
            y = oldY.coerceIn(dp(24), maxOf(dp(24), resources.displayMetrics.heightPixels - dp(220)))
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        }
        view.isFocusableInTouchMode = focusable
        try {
            windows.addView(view, layout); root = view; params = layout
            view.post {
                if (root !== view) return@post
                layout.x = layout.x.coerceIn(0, maxOf(0, resources.displayMetrics.widthPixels - view.width))
                layout.y = layout.y.coerceIn(dp(24), maxOf(dp(24), resources.displayMetrics.heightPixels - view.height - dp(24)))
                try { windows.updateViewLayout(view, layout) } catch (_: RuntimeException) { stopSelf() }
            }
        }
        catch (_: RuntimeException) {
            Toast.makeText(this, R.string.overlay_unavailable, Toast.LENGTH_LONG).show(); stopSelf()
        }
    }

    private fun draggable(handle: View, click: Boolean) {
        var initialX = 0; var initialY = 0; var downX = 0f; var downY = 0f; var moved = false
        handle.setOnTouchListener { view, event ->
            val layout = params ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layout.x; initialY = layout.y; downX = event.rawX; downY = event.rawY; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(8)) moved = true
                    if (moved) {
                        layout.x = (initialX + dx.toInt()).coerceIn(0, maxOf(0, resources.displayMetrics.widthPixels - (root?.width ?: 0)))
                        layout.y = (initialY + dy.toInt()).coerceIn(dp(24), maxOf(dp(24), resources.displayMetrics.heightPixels - (root?.height ?: 0) - dp(24)))
                        try { root?.let { windows.updateViewLayout(it, layout) } }
                        catch (_: RuntimeException) { stopSelf() }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved && click) view.performClick(); true }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun removeWindow() {
        root?.let { try { windows.removeView(it) } catch (_: IllegalArgumentException) { } }
        root = null
    }

    private enum class Phase { IDLE, PROMPT, CAPTURING, LOADING, RESULT, ERROR, DISCLOSURE }
    companion object {
        const val ACTION_START = "com.lingoflow.instanttranslate.START_READING"
        const val ACTION_TRANSLATE = "com.lingoflow.instanttranslate.READ_SELECTION"
        const val ACTION_STOP = "com.lingoflow.instanttranslate.STOP_READING"
        const val EXTRA_TEXT = "reading_text"
        private const val CHANNEL = "reading_session"
    }
}
