package com.lingoflow.instanttranslate.provider.stub

import com.lingoflow.instanttranslate.direction.Direction
import com.lingoflow.instanttranslate.provider.TranslationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StubTranslationProviderTest {

    private val provider = StubTranslationProvider()

    @Test
    fun `known word maps to its fixed translation`() = runTest {
        val result = provider.translate("hello", Direction.ENGLISH_TO_HINDI)
        assertEquals(TranslationResult.Success("नमस्ते"), result)
    }

    @Test
    fun `known word lookup is case and whitespace insensitive`() = runTest {
        val result = provider.translate("  Hello  ", Direction.ENGLISH_TO_HINDI)
        assertEquals(TranslationResult.Success("नमस्ते"), result)
    }

    @Test
    fun `unknown text falls back to a deterministic, obviously-not-real transform`() = runTest {
        val result = provider.translate("some unmapped phrase", Direction.ENGLISH_TO_HINDI) as TranslationResult.Success
        assertTrue(result.translatedText.startsWith("[stub EN→HI] "))
        assertEquals("some unmapped phrase".reversed(), result.translatedText.removePrefix("[stub EN→HI] "))
    }

    @Test
    fun `same input always produces the same output`() = runTest {
        val first = provider.translate("repeatable input", Direction.HINDI_TO_ENGLISH)
        val second = provider.translate("repeatable input", Direction.HINDI_TO_ENGLISH)
        assertEquals(first, second)
    }
}
