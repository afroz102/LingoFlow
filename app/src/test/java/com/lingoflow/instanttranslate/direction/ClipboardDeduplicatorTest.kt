package com.lingoflow.instanttranslate.direction

import com.lingoflow.instanttranslate.reading.ClipboardDeduplicator
import org.junit.Assert.*
import org.junit.Test

class ClipboardDeduplicatorTest {
    @Test fun `only consecutive duplicate clips are suppressed within a session`() {
        val session = ClipboardDeduplicator()
        assertTrue(session.accept("kal milte hain"))
        assertFalse(session.accept("kal milte hain"))
        assertTrue(session.accept("please wait"))
        assertTrue(session.accept("kal milte hain"))
        assertTrue(ClipboardDeduplicator().accept("kal milte hain"))
    }
}
