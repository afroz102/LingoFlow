package com.lingoflow.instanttranslate.direction

/** Latin input needs semantic detection; shared words and informal spellings defeat word lists. */
object DirectionDetector {
    fun detect(text: String): Direction = Direction.AUTO

    fun isSupported(text: String): Boolean =
        text.isNotBlank() && text.length <= 4000 &&
            text.none { it in '\u0900'..'\u097f' || it in '\ua8e0'..'\ua8ff' } &&
            !text.contains(Regex("[\\x{11B00}-\\x{11B09}]"))
}
