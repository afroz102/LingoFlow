package com.lingoflow.instanttranslate.coordinator

import com.lingoflow.instanttranslate.cloud.ConnectivityChecker
import com.lingoflow.instanttranslate.cloud.DisclosureGate
import com.lingoflow.instanttranslate.direction.DirectionDetector
import com.lingoflow.instanttranslate.direction.Direction
import com.lingoflow.instanttranslate.provider.TranslationProvider
import com.lingoflow.instanttranslate.provider.TranslationResult
import com.lingoflow.instanttranslate.timing.TimingMark
import com.lingoflow.instanttranslate.timing.TranslationTimeline

/**
 * Translate-selection coordinator (docs/TECHNICAL_PLAN.md). Takes
 * already-validated, already-flattened text from the text-action adapter and, in order: checks
 * disclosure acknowledgement, checks connectivity, resolves direction, invokes the provider, and
 * returns a typed outcome. Both gate checks run before the provider is touched — no request is
 * ever sent without acknowledged disclosure or without connectivity, and neither gate spends a
 * network call to find that out.
 */
class TranslateCoordinator(
    private val provider: TranslationProvider,
    private val disclosureGate: DisclosureGate,
    private val connectivityChecker: ConnectivityChecker,
) {

    suspend fun translate(text: String, requestedDirection: Direction? = null): TranslationOutcome {
        if (!disclosureGate.isAcknowledged()) return TranslationOutcome.DisclosureRequired
        if (!connectivityChecker.isConnected()) return TranslationOutcome.Offline

        val direction = requestedDirection ?: DirectionDetector.detect(text)
        TranslationTimeline.mark(TimingMark.T_DIRECTION)
        return when (val result = provider.translate(text, direction)) {
            is TranslationResult.Success -> TranslationOutcome.Translated(
                original = text,
                translated = result.translatedText,
                direction = result.direction,
            )
            is TranslationResult.Failure -> TranslationOutcome.Failed(result.reason)
        }
    }
}
