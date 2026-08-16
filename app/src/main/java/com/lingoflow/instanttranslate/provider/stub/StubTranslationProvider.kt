package com.lingoflow.instanttranslate.provider.stub

import com.lingoflow.instanttranslate.direction.Direction
import com.lingoflow.instanttranslate.provider.TranslationProvider
import com.lingoflow.instanttranslate.provider.TranslationResult

/**
 * Stage 0A deterministic stand-in for the real provider (docs/VALIDATION_PLAN.md §2.1): "a
 * deterministic local test mapping or equally trivial transformation so that model
 * initialization, quality, and networking cannot hide platform behavior."
 *
 * This is intentionally not a translator. It exists only so the Process Text platform
 * integration (intent handling, read-only/editable behavior, Copy, Replace, lifecycle) can be
 * proven and benchmarked with zero ML/cloud code, per docs/IMPLEMENTATION_PLAN.md §2. It is replaced
 * outright by the Gemini adapter in provider/gemini/ at Stage 1 — callers depend only on
 * [TranslationProvider], never on this class.
 */
class StubTranslationProvider : TranslationProvider {

    private val knownWords = mapOf(
        "hello" to "नमस्ते",
        "thank you" to "धन्यवाद",
        "yes" to "हाँ",
        "no" to "नहीं",
        "नमस्ते" to "hello",
        "धन्यवाद" to "thank you",
        "हाँ" to "yes",
        "नहीं" to "no",
    )

    override suspend fun translate(text: String, direction: Direction): TranslationResult {
        val normalized = text.trim().lowercase()
        val translated = knownWords[normalized] ?: deterministicFallback(text, direction)
        return TranslationResult.Success(translated)
    }

    /** Obviously-not-real output — a reversed string tagged with the requested direction. */
    private fun deterministicFallback(text: String, direction: Direction): String {
        val tag = when (direction) {
            Direction.ENGLISH_TO_HINDI -> "EN→HI"
            Direction.HINDI_TO_ENGLISH -> "HI→EN"
        }
        return "[stub $tag] " + text.reversed()
    }
}
