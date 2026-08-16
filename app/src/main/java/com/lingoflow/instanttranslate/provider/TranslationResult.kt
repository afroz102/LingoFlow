package com.lingoflow.instanttranslate.provider

/**
 * Result of a [TranslationProvider] call. Stage 1's Gemini adapter will map connectivity,
 * quota, timeout, and invalid-response failures onto [Failure] — kept to one case now
 * because the Stage 0A stub cannot actually produce the others yet.
 */
sealed interface TranslationResult {
    data class Success(val translatedText: String) : TranslationResult
    data class Failure(val reason: FailureReason) : TranslationResult
}

enum class FailureReason {
    UNSUPPORTED_INPUT,
}
