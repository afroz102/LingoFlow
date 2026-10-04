package com.lingoflow.instanttranslate.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lingoflow.instanttranslate.R
import com.lingoflow.instanttranslate.databinding.ActivityResultBinding
import com.lingoflow.instanttranslate.direction.Direction
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lingoflow.instanttranslate.timing.TimingMark
import com.lingoflow.instanttranslate.timing.TimingOutcome
import com.lingoflow.instanttranslate.timing.TranslationTimeline
import kotlinx.coroutines.launch

/**
 * Result presenter (docs/TECHNICAL_PLAN.md). Reached only from [com.lingoflow.instanttranslate.textaction.ProcessTextActivity]
 * via an explicit intent — never launched directly by a host app. Renders the translation,
 * writes to the clipboard only on explicit Copy, and returns translated text only on explicit
 * Replace (and only when the source selection was editable).
 */
class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding

    private val originalText: String by lazy { intent.getStringExtra(EXTRA_TEXT).orEmpty() }
    private val isReadOnly: Boolean by lazy { intent.getBooleanExtra(EXTRA_READ_ONLY, true) }

    private val viewModel: ResultViewModel by viewModels {
        ResultViewModel.Factory(application, originalText, isReadOnly)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // android:excludeFromRecents on this activity and ProcessTextActivity only keeps THIS
        // task out of the Overview list — it does not stop the calling host's own task snapshot
        // from compositing whatever was visually on top of it (this dialog) at the moment the
        // user leaves. Confirmed on-device: without FLAG_SECURE, selected/translated text was
        // captured in the host app's Recents thumbnail. FLAG_SECURE blocks both that and
        // screenshots of this screen; losing screenshot capability is an acceptable trade here
        // since Copy already covers the "save the translation" use case.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Default outcome unless Replace is pressed explicitly — Back, cancel, and any error
        // path all leave the host unmodified (docs/VALIDATION_PLAN.md §2.5).
        setResult(RESULT_CANCELED)

        binding.buttonCopy.setOnClickListener { copyTranslationToClipboard() }
        binding.buttonReplace.setOnClickListener { replaceAndFinish() }
        binding.buttonDisclosureContinue.setOnClickListener { viewModel.acknowledgeDisclosureAndRetry() }
        binding.buttonRetry.setOnClickListener { viewModel.retry() }
        binding.buttonDirection.setOnClickListener { showDirectionChoices() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: ResultUiState) {
        binding.progress.isVisible = state is ResultUiState.Loading
        binding.disclosure.isVisible = state is ResultUiState.DisclosureRequired
        binding.errorGroup.isVisible = state is ResultUiState.Error
        binding.content.isVisible = state is ResultUiState.Success
        binding.buttonDirection.isVisible = state is ResultUiState.Success || state is ResultUiState.Error

        if (state is ResultUiState.Success) {
            binding.textOriginal.text = state.original
            binding.textTranslated.text = state.translated
            binding.textDirection.setText(state.direction.displayNameRes)
            binding.buttonReplace.isVisible = !state.isReadOnly
        }

        if (state is ResultUiState.Error) {
            binding.textError.setText(state.kind.messageRes())
        }

        state.timingOutcome()?.let(::recordRenderTiming)
    }

    private fun showDirectionChoices() {
        val choices = Direction.entries
        val labels = choices.map { getString(it.displayNameRes) }.toTypedArray()
        val selected = viewModel.requestedDirection ?: Direction.AUTO
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_change_direction)
            .setSingleChoiceItems(labels, choices.indexOf(selected)) { dialog, index ->
                dialog.dismiss()
                viewModel.changeDirection(choices[index])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }

    /**
     * Closes out the Gate 1 timing chain (docs/VALIDATION_PLAN.md §3.4) once a terminal state is
     * actually on screen. Loading is not terminal, so it produces no mark.
     *
     * T_render is defined as "usable result visible". The closest observable approximation is the
     * first message to run after the frame carrying this state has been produced: the pre-draw
     * listener fires while that frame is being built, and posting from it lands just after. This
     * over-reports slightly against a true photometric measurement, which is why
     * docs/VALIDATION_PLAN.md §3.4 also asks for a visual/high-speed sanity check against these
     * numbers rather than trusting them alone.
     */
    private fun recordRenderTiming(outcome: TimingOutcome) {
        OneShotPreDrawListener.add(binding.root) {
            binding.root.post {
                TranslationTimeline.mark(TimingMark.T_RENDER)
                TranslationTimeline.complete(outcome)
            }
        }
    }

    private fun ResultUiState.timingOutcome(): TimingOutcome? = when (this) {
        is ResultUiState.Loading -> null
        is ResultUiState.Success -> TimingOutcome.SUCCESS
        is ResultUiState.DisclosureRequired -> TimingOutcome.DISCLOSURE_REQUIRED
        is ResultUiState.Error ->
            if (kind == ErrorKind.OFFLINE) TimingOutcome.OFFLINE else TimingOutcome.FAILED
    }

    private fun ErrorKind.messageRes(): Int = when (this) {
        ErrorKind.OFFLINE -> R.string.error_offline
        ErrorKind.RATE_LIMITED -> R.string.error_rate_limited
        ErrorKind.TIMEOUT -> R.string.error_timeout
        ErrorKind.PROVIDER_ERROR -> R.string.error_provider
        ErrorKind.INVALID_RESPONSE -> R.string.error_invalid_response
        ErrorKind.UNSUPPORTED_INPUT -> R.string.error_generic
    }

    private fun copyTranslationToClipboard() {
        val state = viewModel.uiState.value as? ResultUiState.Success ?: return
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.label_translated), state.translated))
        Toast.makeText(this, R.string.copied_confirmation, Toast.LENGTH_SHORT).show()
    }

    private fun replaceAndFinish() {
        val state = viewModel.uiState.value as? ResultUiState.Success ?: return
        if (state.isReadOnly) return // defense in depth: the button is already hidden for read-only input

        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, state.translated))
        finish()
    }

    companion object {
        private const val EXTRA_TEXT = "com.lingoflow.instanttranslate.extra.TEXT"
        private const val EXTRA_READ_ONLY = "com.lingoflow.instanttranslate.extra.READ_ONLY"

        fun createIntent(context: Context, text: String, isReadOnly: Boolean): Intent =
            Intent(context, ResultActivity::class.java)
                .putExtra(EXTRA_TEXT, text)
                .putExtra(EXTRA_READ_ONLY, isReadOnly)
    }
}
