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

/** Explicit opt-in: two harmless real translations, with no Auth/session calls. */
@RunWith(AndroidJUnit4::class)
class BackendTranslationSmokeTest {
    @Test fun englishAndHindiTranslateThroughOurBackend() = runBlocking {
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
        val hindi = provider.translate("How are you?", Direction.ENGLISH_TO_HINDI)
        assertTrue("English to Hindi failed: ${category(hindi)} ($status)", hindi is TranslationResult.Success)
        assertTrue((hindi as TranslationResult.Success).translatedText.any { it in 'ऀ'..'ॿ' })
        val english = provider.translate("आप कैसे हैं?", Direction.HINDI_TO_ENGLISH)
        assertTrue("Hindi to English failed: ${category(english)} ($status)", english is TranslationResult.Success)
        assertTrue((english as TranslationResult.Success).translatedText.any { it in 'a'..'z' || it in 'A'..'Z' })
    }

    private fun category(result: TranslationResult): String =
        if (result is TranslationResult.Failure) result.reason.name else "SUCCESS"
}
