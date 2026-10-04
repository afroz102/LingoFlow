package com.lingoflow.instanttranslate.direction

import org.junit.Assert.*
import org.junit.Test

class TranslationLanguagesTest {
    @Test fun `defaults and swapped automatic pair are valid`() {
        val defaults = TranslationLanguagePair()
        assertEquals("auto", defaults.source); assertEquals("en", defaults.target)
        assertTrue(defaults.isValid())
        assertEquals(TranslationLanguagePair("en", "hi-Latn"), defaults.swapped())
        assertEquals(TranslationLanguagePair("fr", "ru"), TranslationLanguagePair("ru", "fr").swapped())
        assertFalse(TranslationLanguagePair("auto", "auto").isValid())
        assertFalse(TranslationLanguagePair("bad", "en").isValid())
    }
    @Test fun `Indian Roman variants and requested global languages exist`() {
        assertEquals(67, TranslationLanguages.available.size)
        assertEquals(22, TranslationLanguages.available.count { it.romanized })
        for (id in listOf("ru", "fr", "es", "de", "ko", "id", "zh", "ja", "hi", "hi-Latn", "ta-Latn")) {
            assertNotNull(TranslationLanguages.find(id))
        }
    }
}
