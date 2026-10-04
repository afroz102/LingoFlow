package com.lingoflow.instanttranslate.provider.backend

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingoflow.instanttranslate.BuildConfig
import com.lingoflow.instanttranslate.direction.Direction
import com.lingoflow.instanttranslate.provider.TranslationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in: harmless real translations, with no Auth/session calls. */
@RunWith(AndroidJUnit4::class)
class BackendTranslationSmokeTest {
    @Test fun writingAndReadingTranslateThroughOurBackend() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Pass liveCloud=true to allow live requests", arguments.getString("liveCloud") == "true")
        val testUrl = arguments.getString("testBackendUrl") ?: BuildConfig.BACKEND_URL
        assumeTrue("Backend URL missing", testUrl.isNotBlank())
        val transport = UrlConnectionHttp(testUrl)
        var status = "not called"
        val provider = BackendTranslationProvider(BackendHttp { body ->
            try { transport.translate(body).also { status = "HTTP ${it.status}" } }
            catch (failure: Exception) { status = failure.javaClass.simpleName; throw failure }
        })
        for ((text, requested, resolved) in listOf(
            Triple("aap kaise ho?", Direction.AUTO, Direction.HINGLISH_TO_ENGLISH),
            Triple("How are you?", Direction.AUTO, Direction.ENGLISH_TO_HINGLISH),
            Triple("main kal nahi aa sakta", Direction.READ_TO_ENGLISH, Direction.READ_TO_ENGLISH),
            Triple("Please wait for me", Direction.READ_TO_ENGLISH, Direction.READ_TO_ENGLISH),
        )) {
            val result = provider.translate(text, requested)
            assertTrue("${requested.name} failed: ${category(result)} ($status)", result is TranslationResult.Success)
            result as TranslationResult.Success
            assertTrue("Wrong resolved direction", result.direction == resolved)
            assertTrue("Devanagari output is unsupported", result.translatedText.none { it in 'ऀ'..'ॿ' })
            if (text == "Please wait for me") assertTrue("English reading must remain English", result.translatedText == text)
        }
    }

    private fun category(result: TranslationResult): String =
        if (result is TranslationResult.Failure) result.reason.name else "SUCCESS"
}
