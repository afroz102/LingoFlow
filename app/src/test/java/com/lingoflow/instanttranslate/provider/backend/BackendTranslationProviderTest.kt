package com.lingoflow.instanttranslate.provider.backend

import com.lingoflow.instanttranslate.direction.Direction
import com.lingoflow.instanttranslate.provider.FailureReason
import com.lingoflow.instanttranslate.provider.TranslationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException

class BackendTranslationProviderTest {
    private val direction = Direction.ENGLISH_TO_HINDI

    @Test fun `one translation sends only selected text and direction`() = runTest {
        var calls = 0
        val provider = BackendTranslationProvider(BackendHttp { body ->
            calls++
            val json = JSONObject(body)
            assertEquals(setOf("text", "direction"), json.keys().asSequence().toSet())
            assertEquals("hello", json.getString("text"))
            assertEquals(direction.name, json.getString("direction"))
            HttpResponse(200, """{"translation":"नमस्ते"}""")
        })
        assertEquals(TranslationResult.Success("नमस्ते"), provider.translate("hello", direction))
        assertEquals(1, calls)
    }

    @Test fun `rate limit server timeout and malformed result map to typed failures`() = runTest {
        for ((response, reason) in listOf(
            HttpResponse(429, "{}") to FailureReason.RATE_LIMITED,
            HttpResponse(504, "{}") to FailureReason.TIMEOUT,
            HttpResponse(522, "{}") to FailureReason.TIMEOUT,
            HttpResponse(502, """{"error":"INVALID_RESPONSE"}""") to FailureReason.INVALID_RESPONSE,
            HttpResponse(503, "{}") to FailureReason.PROVIDER_ERROR,
            HttpResponse(415, "{}") to FailureReason.UNSUPPORTED_INPUT,
        )) {
            val provider = BackendTranslationProvider(BackendHttp { response })
            assertEquals(TranslationResult.Failure(reason), provider.translate("hello", direction))
        }
    }

    @Test fun `invalid successful bodies are never displayed as a translation`() = runTest {
        for (raw in listOf("not json", "{}", """{"translation":""}""", """{"translation":123}""",
            JSONObject().put("translation", "x".repeat(16001)).toString())) {
            val provider = BackendTranslationProvider(BackendHttp { HttpResponse(200, raw) })
            assertEquals(TranslationResult.Failure(FailureReason.INVALID_RESPONSE), provider.translate("hello", direction))
        }
    }

    @Test fun `invalid input and missing backend never make a request`() = runTest {
        val http = BackendHttp { error("must not call") }
        for (text in listOf(" ", "x".repeat(4001))) {
            assertEquals(TranslationResult.Failure(FailureReason.UNSUPPORTED_INPUT),
                BackendTranslationProvider(http).translate(text, direction))
        }
        assertEquals(TranslationResult.Failure(FailureReason.PROVIDER_ERROR),
            BackendTranslationProvider(http, configured = false).translate("hello", direction))
    }

    @Test fun `socket timeout does not automatically resend selected text`() = runTest {
        var calls = 0
        val provider = BackendTranslationProvider(BackendHttp { calls++; throw SocketTimeoutException() })
        assertEquals(TranslationResult.Failure(FailureReason.TIMEOUT), provider.translate("hello", direction))
        assertEquals(1, calls)
    }

    @Test fun `lifecycle cancellation propagates`() = runTest {
        val provider = BackendTranslationProvider(BackendHttp { throw CancellationException("cancelled") })
        try { provider.translate("hello", direction); fail("cancellation must propagate") }
        catch (_: CancellationException) { }
    }
}
