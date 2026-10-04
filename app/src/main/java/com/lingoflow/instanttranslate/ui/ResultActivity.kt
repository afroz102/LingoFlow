package com.lingoflow.instanttranslate.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lingoflow.instanttranslate.R
import com.lingoflow.instanttranslate.databinding.ActivityResultBinding
import com.lingoflow.instanttranslate.direction.DirectionDetector
import com.lingoflow.instanttranslate.prefs.DisclosurePreferences
import com.lingoflow.instanttranslate.reading.ReadingSession
import com.lingoflow.instanttranslate.reading.ReadingNotifications
import com.lingoflow.instanttranslate.reading.ClipboardAccess
import com.lingoflow.instanttranslate.keyboard.KeyboardReadingInbox
import com.lingoflow.instanttranslate.timing.TimingMark
import com.lingoflow.instanttranslate.timing.TimingOutcome
import com.lingoflow.instanttranslate.timing.TranslationTimeline
import kotlinx.coroutines.launch

/** Editable hosts receive one replacement result. Reading can hand off to the selected keyboard. */
class ResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResultBinding
    private val originalText by lazy { intent.getStringExtra(EXTRA_TEXT).orEmpty() }
    private val isReadOnly by lazy { intent.getBooleanExtra(EXTRA_READ_ONLY, true) }
    private val viewModel: ResultViewModel by viewModels {
        ResultViewModel.Factory(application, originalText, isReadOnly)
    }
    private var returned = false
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { prepareReading() }
    private val overlaySettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        prepareReading()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setResult(RESULT_CANCELED)
        binding.buttonCancel.setOnClickListener { finish() }
        if (isReadOnly) {
            prepareReading()
        } else {
            binding.buttonDisclosureContinue.setOnClickListener { viewModel.acknowledgeDisclosureAndRetry() }
            binding.buttonRetry.setOnClickListener { viewModel.retry() }
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.uiState.collect(::renderWriting) }
            }
        }
    }

    private fun prepareReading() {
        binding.progress.isVisible = false
        binding.disclosure.isVisible = false
        binding.errorGroup.isVisible = false
        if (!DirectionDetector.isSupported(originalText)) {
            readingError(R.string.error_generic, retry = false)
        } else if (!DisclosurePreferences(this).isAcknowledged()) {
            binding.disclosure.isVisible = true
            binding.buttonDisclosureContinue.setOnClickListener {
                DisclosurePreferences(this).acknowledge(); prepareReading()
            }
        } else if (ClipboardAccess.hasKeyboardAccess(this) && !ReadingSession.isRunning) {
            // A read-only message isn't an IME editor. The host's chat composer must open the keyboard.
            KeyboardReadingInbox.offer(originalText)
            Toast.makeText(this, R.string.keyboard_open_to_read, Toast.LENGTH_LONG).show()
            finish()
        } else if (!Settings.canDrawOverlays(this)) {
            readingError(R.string.reading_permission, retry = true)
            binding.buttonRetry.setText(R.string.setup_overlay)
            binding.buttonRetry.setOnClickListener {
                try { overlaySettings.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
                catch (_: RuntimeException) { readingError(R.string.overlay_unavailable, retry = false) }
            }
        } else if (ReadingNotifications.shouldRequest(this)) {
            ReadingNotifications.markRequested(this)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (ReadingSession.start(this, originalText)) {
            // Read-only selections never return EXTRA_PROCESS_TEXT, including an English result.
            finish()
        } else readingError(R.string.overlay_unavailable, retry = false)
    }

    private fun readingError(messageRes: Int, retry: Boolean) {
        binding.errorGroup.isVisible = true
        binding.textError.setText(messageRes)
        binding.buttonRetry.isVisible = retry
    }

    private fun renderWriting(state: ResultUiState) {
        binding.progress.isVisible = state is ResultUiState.Loading
        binding.disclosure.isVisible = state is ResultUiState.DisclosureRequired
        binding.errorGroup.isVisible = state is ResultUiState.Error
        binding.buttonRetry.isVisible = state is ResultUiState.Error && state.kind != ErrorKind.UNSUPPORTED_INPUT
        if (state is ResultUiState.Error) binding.textError.setText(when (state.kind) {
            ErrorKind.OFFLINE -> R.string.error_offline
            ErrorKind.RATE_LIMITED -> R.string.error_rate_limited
            ErrorKind.TIMEOUT -> R.string.error_timeout
            ErrorKind.PROVIDER_ERROR -> R.string.error_provider
            ErrorKind.INVALID_RESPONSE -> R.string.error_invalid_response
            ErrorKind.UNSUPPORTED_INPUT -> R.string.error_generic
        })
        if (state is ResultUiState.Success && !state.isReadOnly && !returned) {
            returned = true
            TranslationTimeline.mark(TimingMark.T_RENDER)
            TranslationTimeline.complete(TimingOutcome.SUCCESS)
            setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, state.translated))
            finish()
        }
    }

    companion object {
        private const val EXTRA_TEXT = "com.lingoflow.instanttranslate.extra.TEXT"
        private const val EXTRA_READ_ONLY = "com.lingoflow.instanttranslate.extra.READ_ONLY"
        fun createIntent(context: Context, text: String, isReadOnly: Boolean): Intent =
            Intent(context, ResultActivity::class.java).putExtra(EXTRA_TEXT, text).putExtra(EXTRA_READ_ONLY, isReadOnly)
    }
}
