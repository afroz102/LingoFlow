package com.lingoflow.instanttranslate.coordinator

import com.lingoflow.instanttranslate.direction.Direction
import com.lingoflow.instanttranslate.provider.FailureReason

/** Typed UI-facing outcome of a translate-selection request (docs/TECHNICAL_PLAN.md §3 coordinator). */
sealed interface TranslationOutcome {
    data class Translated(
        val original: String,
        val translated: String,
        val direction: Direction,
    ) : TranslationOutcome

    data class Failed(val reason: FailureReason) : TranslationOutcome
}
