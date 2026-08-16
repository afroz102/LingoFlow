package com.lingoflow.instanttranslate.coordinator

import com.lingoflow.instanttranslate.direction.DirectionDetector
import com.lingoflow.instanttranslate.provider.TranslationProvider
import com.lingoflow.instanttranslate.provider.TranslationResult

/**
 * Translate-selection coordinator (docs/TECHNICAL_PLAN.md §3). Takes already-validated,
 * already-flattened text from the text-action adapter, resolves direction, invokes the
 * provider, and returns a typed outcome. Stage 1 adds disclosure/connectivity gating here;
 * Stage 0A has neither, since the stub provider needs no network or user consent.
 */
class TranslateCoordinator(private val provider: TranslationProvider) {

    suspend fun translate(text: String): TranslationOutcome {
        val direction = DirectionDetector.detect(text)
        return when (val result = provider.translate(text, direction)) {
            is TranslationResult.Success -> TranslationOutcome.Translated(
                original = text,
                translated = result.translatedText,
                direction = direction,
            )
            is TranslationResult.Failure -> TranslationOutcome.Failed(result.reason)
        }
    }
}
