package com.lingoflow.instanttranslate.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lingoflow.instanttranslate.cloud.AndroidConnectivityChecker
import com.lingoflow.instanttranslate.coordinator.TranslateCoordinator
import com.lingoflow.instanttranslate.coordinator.TranslationOutcome
import com.lingoflow.instanttranslate.direction.TranslationLanguagePair
import com.lingoflow.instanttranslate.prefs.DisclosurePreferences
import com.lingoflow.instanttranslate.provider.backend.BackendTranslationProvider
import com.lingoflow.instanttranslate.timing.TranslationTimeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Retained across rotation/config change so the translation isn't redone on every rotation —
 * there is no persistence beyond the ViewModel's own lifetime (no saved instance state, no
 * disk), matching docs/TECHNICAL_PLAN.md's "avoid saving source/result" rule for the presenter.
 *
 * [AndroidViewModel] rather than a plain [androidx.lifecycle.ViewModel] only because the Stage 1
 * gates it wires up ([DisclosurePreferences], [AndroidConnectivityChecker]) need a
 * [android.content.Context] — the application context is held, never the Activity, so this still
 * can't leak an Activity across rotation.
 */
class ResultViewModel(
    application: Application,
    private val originalText: String,
    private val isReadOnly: Boolean,
) : AndroidViewModel(application) {

    private val disclosureGate = DisclosurePreferences(application)

    private val coordinator = TranslateCoordinator(
        provider = BackendTranslationProvider(),
        disclosureGate = disclosureGate,
        connectivityChecker = AndroidConnectivityChecker(application),
    )

    private val _uiState = MutableStateFlow<ResultUiState>(ResultUiState.Loading)
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    private val languages = if (isReadOnly) TranslationLanguagePair() else {
        val preferences = application.getSharedPreferences("translation_languages", 0)
        TranslationLanguagePair(preferences.getString("source", "auto") ?: "auto",
            preferences.getString("target", "en") ?: "en").takeIf { it.isValid() } ?: TranslationLanguagePair()
    }

    init {
        // No startRun() here: ProcessTextActivity already opened this run when it received the
        // intent, and restarting it would discard that T_RECEIVE mark.
        runTranslation()
    }

    /** Called once, from the disclosure screen's Continue action — never re-entered mid-request since that screen is only shown for a terminal, non-Loading state. */
    fun acknowledgeDisclosureAndRetry() {
        if (_uiState.value !is ResultUiState.DisclosureRequired) return
        disclosureGate.acknowledge()
        runTranslation(isNewRun = true)
    }

    /** Lets the result screen offer a plain Retry after a recoverable failure (offline, rate-limited, timeout, provider error) without re-showing disclosure, since it's already acknowledged by the time any [ResultUiState.Error] can be reached. */
    fun retry() {
        if (_uiState.value !is ResultUiState.Error) return
        runTranslation(isNewRun = true)
    }

    /**
     * @param isNewRun true when the user asked for another attempt (retry, or continuing past the
     * disclosure gate). Each attempt is its own provider request with its own latency, so it gets
     * its own timed run rather than being averaged into the first attempt
     * (docs/VALIDATION_PLAN.md §3.4).
     */
    private fun runTranslation(isNewRun: Boolean = false) {
        if (isNewRun) TranslationTimeline.startRun()
        _uiState.value = ResultUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val outcome = coordinator.translate(originalText, languages = languages)) {
                is TranslationOutcome.Translated -> ResultUiState.Success(
                    original = outcome.original,
                    translated = outcome.translated,
                    direction = outcome.direction,
                    isReadOnly = isReadOnly,
                )
                is TranslationOutcome.DisclosureRequired -> ResultUiState.DisclosureRequired
                is TranslationOutcome.Offline -> ResultUiState.Error(ErrorKind.OFFLINE)
                is TranslationOutcome.Failed -> ResultUiState.Error(outcome.reason.toErrorKind())
            }
        }
    }

    class Factory(
        private val application: Application,
        private val originalText: String,
        private val isReadOnly: Boolean,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ResultViewModel(application, originalText, isReadOnly) as T
    }
}
