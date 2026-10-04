package com.lingoflow.instanttranslate.ui

import com.lingoflow.instanttranslate.direction.Direction
import com.lingoflow.instanttranslate.provider.FailureReason

/**
 * Result-presenter states (docs/TECHNICAL_PLAN.md §3, §5 runtime state model). [DisclosureRequired]
 * and each [ErrorKind] under [Error] are new at Stage 1 (docs/IMPLEMENTATION_PLAN.md §4 item 6) —
 * Stage 0A's stub could only ever reach Loading/Success, since it had no network, quota, or
 * consent step to fail against.
 */
sealed interface ResultUiState {
    data object Loading : ResultUiState

    /** Shown once, before the first request that would send content to Gemini; see [Error] for what can fail after acknowledgement. */
    data object DisclosureRequired : ResultUiState

    data class Success(
        val original: String,
        val translated: String,
        val direction: Direction,
        val isReadOnly: Boolean,
    ) : ResultUiState

    data class Error(val kind: ErrorKind) : ResultUiState
}

/**
 * User-facing error category (docs/TECHNICAL_PLAN.md §5: "Only non-content timing labels and
 * error categories may leave this state machine"). Each value maps to its own string resource in
 * [ResultActivity] — never to the underlying exception message, which could in principle vary
 * per-request in ways that aren't meant for end users.
 */
enum class ErrorKind {
    OFFLINE,
    RATE_LIMITED,
    TIMEOUT,
    PROVIDER_ERROR,
    INVALID_RESPONSE,
    UNSUPPORTED_INPUT,
}

internal fun FailureReason.toErrorKind(): ErrorKind = when (this) {
    FailureReason.UNSUPPORTED_INPUT -> ErrorKind.UNSUPPORTED_INPUT
    FailureReason.RATE_LIMITED -> ErrorKind.RATE_LIMITED
    FailureReason.TIMEOUT -> ErrorKind.TIMEOUT
    FailureReason.PROVIDER_ERROR -> ErrorKind.PROVIDER_ERROR
    FailureReason.INVALID_RESPONSE -> ErrorKind.INVALID_RESPONSE
}
