package com.lingoflow.instanttranslate.direction

/** Detection is semantic and happens in the translation call; this guard only bounds text. */
object DirectionDetector {
    fun detect(text: String): Direction = Direction.AUTO

    fun isSupported(text: String): Boolean = text.isNotBlank() && text.length <= 4000
}
