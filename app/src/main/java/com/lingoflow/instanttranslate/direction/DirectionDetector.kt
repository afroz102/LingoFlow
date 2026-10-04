package com.lingoflow.instanttranslate.direction

/**
 * Cheap script signal: any Devanagari routes to English; other text routes to Hindi.
 * This does not resolve Latin Hinglish or mixed/short-text ambiguity. User-correctable
 * direction and output-script controls remain requirements in docs/PRODUCT_REQUIREMENTS.md.
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
