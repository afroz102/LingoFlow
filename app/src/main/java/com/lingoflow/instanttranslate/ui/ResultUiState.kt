package com.lingoflow.instanttranslate.ui

import com.lingoflow.instanttranslate.direction.Direction

/**
 * Result-presenter states (docs/TECHNICAL_PLAN.md §3). Stage 0A only reaches Loading/Success/Error —
 * the disclosure-required and per-failure-type cloud states are Stage 1/2 scope
 * (docs/IMPLEMENTATION_PLAN.md §4, §5.2), added once there is a real network-backed provider to fail.
 */
sealed interface ResultUiState {
    data object Loading : ResultUiState

    data class Success(
        val original: String,
        val translated: String,
        val direction: Direction,
        val isReadOnly: Boolean,
    ) : ResultUiState

    data object Error : ResultUiState
}
