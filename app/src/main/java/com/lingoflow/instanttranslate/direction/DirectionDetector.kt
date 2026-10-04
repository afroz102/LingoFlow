package com.lingoflow.instanttranslate.direction

/**
 * Devanagari routes to English. Latin text needs the model to distinguish English from
 * Romanized Hindi; a word list would misclassify shared words, spelling variants and names.
 */
object DirectionDetector {

    private val DEVANAGARI_RANGE = 'ऀ'..'ॿ'

    fun detect(text: String): Direction =
        if (text.any { it in DEVANAGARI_RANGE }) {
            Direction.HINDI_TO_ENGLISH
        } else {
            Direction.AUTO
        }
}
