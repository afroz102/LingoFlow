package com.lingoflow.instanttranslate.direction

/**
 * Stage 0A direction signal: the "cheap script signal for clear Devanagari" described in
 * docs/TECHNICAL_PLAN.md §3's direction policy boundary. It only distinguishes text that
 * contains Devanagari codepoints from text that doesn't.
 *
 * This is deliberately not the full policy. Ambiguity classification for short, mixed, and
 * Romanized (Hinglish) text, plus a user-correctable override, is Stage 2 scope
 * (docs/IMPLEMENTATION_PLAN.md §5.1) — introducing it now would let this component silently
 * misrepresent a low-confidence guess as certainty, which docs/TECHNICAL_PLAN.md §3 forbids.
 */
object DirectionDetector {

    private val DEVANAGARI_RANGE = 'ऀ'..'ॿ'

    fun detect(text: String): Direction =
        if (text.any { it in DEVANAGARI_RANGE }) {
            Direction.HINDI_TO_ENGLISH
        } else {
            Direction.ENGLISH_TO_HINDI
        }
}
