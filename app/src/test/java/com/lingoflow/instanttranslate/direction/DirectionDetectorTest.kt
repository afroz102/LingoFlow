package com.lingoflow.instanttranslate.direction

import org.junit.Assert.*
import org.junit.Test

class DirectionDetectorTest {
    @Test fun `English and informal Hinglish use one model call for detection`() {
        for (text in listOf("hello there", "aap kaise ho", "main meeting mein late aaunga")) {
            assertEquals(Direction.AUTO, DirectionDetector.detect(text))
            assertTrue(DirectionDetector.isSupported(text))
        }
    }
    @Test fun `all scripts are accepted but blank and oversized input are rejected locally`() {
        for (text in listOf(" ", "x".repeat(4001))) {
            assertFalse(DirectionDetector.isSupported(text))
        }
        assertTrue(DirectionDetector.isSupported("x".repeat(4000)))
        for (text in listOf("नमस्ते", "কাল আসব", "안녕하세요", "Привет", "こんにちは", "kal milte hain 🎮")) assertTrue(DirectionDetector.isSupported(text))
    }
    @Test fun `automatic writing cannot resolve into reading mode`() {
        assertTrue(Direction.AUTO.acceptsResolved(Direction.ENGLISH_TO_HINGLISH))
        assertTrue(Direction.AUTO.acceptsResolved(Direction.HINGLISH_TO_ENGLISH))
        assertFalse(Direction.AUTO.acceptsResolved(Direction.READ_TO_ENGLISH))
    }
}
