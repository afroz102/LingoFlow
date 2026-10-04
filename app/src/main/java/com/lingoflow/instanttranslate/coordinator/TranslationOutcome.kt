package com.lingoflow.instanttranslate.coordinator

import com.lingoflow.instanttranslate.direction.Direction
import com.lingoflow.instanttranslate.provider.FailureReason

/**
 * Typed UI-facing outcome of a translate-selection request (docs/TECHNICAL_PLAN.md
 * coordinator). [DisclosureRequired] and [Offline] are pre-flight gates
 * the coordinator checks before ever calling the provider; [Failed] covers everything the
 * provider itself can fail with.
 */
sealed interface TranslationOutcome {
    data class Translated(
        val original: String,
        val translated: String,
        val direction: Direction,
    ) : TranslationOutcome

    /** The user has not (yet) acknowledged that selected text is sent to Google's Gemini API. */
    data object DisclosureRequired : TranslationOutcome

    /** No validated internet connectivity — caught before spending a request. */
    data object Offline : TranslationOutcome

    data class Failed(val reason: FailureReason) : TranslationOutcome
}
