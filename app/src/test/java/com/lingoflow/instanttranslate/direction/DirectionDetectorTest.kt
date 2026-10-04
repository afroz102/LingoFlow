package com.lingoflow.instanttranslate.direction

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectionDetectorTest {

    @Test
    fun `plain English text detects as English to Hindi`() {
        assertEquals(Direction.ENGLISH_TO_HINDI, DirectionDetector.detect("hello there"))
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
    fun `Romanized Hindi with no Devanagari codepoints detects as English to Hindi`() {
        // Hinglish ambiguity classification is Stage 2 scope (docs/TECHNICAL_PLAN.md) —
        // Stage 0A's cheap script signal cannot and must not claim certainty here.
        assertEquals(Direction.ENGLISH_TO_HINDI, DirectionDetector.detect("aap kaise ho"))
    }
}
