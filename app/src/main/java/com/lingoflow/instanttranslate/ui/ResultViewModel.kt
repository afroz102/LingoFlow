package com.lingoflow.instanttranslate.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lingoflow.instanttranslate.coordinator.TranslateCoordinator
import com.lingoflow.instanttranslate.coordinator.TranslationOutcome
import com.lingoflow.instanttranslate.provider.stub.StubTranslationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Retained across rotation/config change so the translation isn't redone on every rotation —
 * there is no persistence beyond the ViewModel's own lifetime (no saved instance state, no
 * disk), matching docs/TECHNICAL_PLAN.md §3's "avoid saving source/result" rule for the presenter.
 *
 * The [StubTranslationProvider] wiring here is Stage 0A only. It is swapped for the Gemini
 * adapter's provider at Stage 1 — nothing else in this class changes, since both satisfy the
 * same [com.lingoflow.instanttranslate.provider.TranslationProvider] contract.
 */
class ResultViewModel(
    originalText: String,
    isReadOnly: Boolean,
) : ViewModel() {

    private val coordinator = TranslateCoordinator(StubTranslationProvider())

    private val _uiState = MutableStateFlow<ResultUiState>(ResultUiState.Loading)
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = when (val outcome = coordinator.translate(originalText)) {
                is TranslationOutcome.Translated -> ResultUiState.Success(
                    original = outcome.original,
                    translated = outcome.translated,
                    direction = outcome.direction,
                    isReadOnly = isReadOnly,
                )
                is TranslationOutcome.Failed -> ResultUiState.Error
            }
        }
    }

    class Factory(
        private val originalText: String,
        private val isReadOnly: Boolean,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ResultViewModel(originalText, isReadOnly) as T
    }
}
