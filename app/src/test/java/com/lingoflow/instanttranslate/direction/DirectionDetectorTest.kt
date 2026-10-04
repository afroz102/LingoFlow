package com.lingoflow.instanttranslate.direction

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectionDetectorTest {

    @Test
    fun `Latin English asks the model to resolve the language`() {
        assertEquals(Direction.AUTO, DirectionDetector.detect("hello there"))
    }

    @Test
    fun `Devanagari text detects as Hindi to English`() {
        assertEquals(Direction.HINDI_TO_ENGLISH, DirectionDetector.detect("नमस्ते दुनिया"))
    }

    @Test
    fun `mixed script with any Devanagari codepoint detects as Hindi to English`() {
        assertEquals(Direction.HINDI_TO_ENGLISH, DirectionDetector.detect("please कल आना"))
    }

    @Test
    fun `Romanized Hindi is not incorrectly forced to English to Hindi`() {
        assertEquals(Direction.AUTO, DirectionDetector.detect("aap kaise ho"))
    }
}
