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

    @Test fun `automatic Hinglish translation uses one request and validates the resolved direction`() = runTest {
        var calls = 0
        val provider = BackendTranslationProvider(BackendHttp { body ->
            calls++
            assertEquals("AUTO", JSONObject(body).getString("direction"))
            HttpResponse(200, """{"translation":"How are you?","direction":"HINGLISH_TO_ENGLISH"}""")
        })
        assertEquals(TranslationResult.Success("How are you?", Direction.HINGLISH_TO_ENGLISH),
            provider.translate("aap kaise ho", Direction.AUTO))
        assertEquals(1, calls)
    }

    @Test fun `automatic response cannot omit direction or resolve to an output preference`() = runTest {
        for (resolved in listOf(null, "AUTO", "OTHER", "ENGLISH_TO_HINGLISH", 123)) {
            val body = JSONObject().put("translation", "hello")
            if (resolved != null) body.put("direction", resolved)
            val provider = BackendTranslationProvider(BackendHttp { HttpResponse(200, body.toString()) })
            assertEquals(TranslationResult.Failure(FailureReason.INVALID_RESPONSE), provider.translate("hello", Direction.AUTO))
        }
    }

    @Test fun `every explicit direction is sent unchanged and mismatched response is rejected`() = runTest {
        for (requested in Direction.entries.filter { it != Direction.AUTO }) {
            val provider = BackendTranslationProvider(BackendHttp { body ->
                assertEquals(requested.name, JSONObject(body).getString("direction"))
                HttpResponse(200, JSONObject().put("translation", "sample").put("direction", requested.name).toString())
            })
            assertEquals(TranslationResult.Success("sample", requested), provider.translate("sample", requested))
        }
        val mismatch = BackendTranslationProvider(BackendHttp {
            HttpResponse(200, """{"translation":"sample","direction":"HINGLISH_TO_ENGLISH"}""")
        })
        assertEquals(TranslationResult.Failure(FailureReason.INVALID_RESPONSE), mismatch.translate("sample", direction))
    }

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
        assertEquals(TranslationResult.Success("नमस्ते", direction), provider.translate("hello", direction))
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
