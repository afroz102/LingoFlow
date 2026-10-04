package com.lingoflow.instanttranslate.provider.backend

import com.lingoflow.instanttranslate.BuildConfig
import com.lingoflow.instanttranslate.direction.Direction
import com.lingoflow.instanttranslate.provider.FailureReason
import com.lingoflow.instanttranslate.provider.TranslationProvider
import com.lingoflow.instanttranslate.provider.TranslationResult
import com.lingoflow.instanttranslate.timing.TimingMark
import com.lingoflow.instanttranslate.timing.TranslationTimeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException

/** One app-to-backend request. The Gemini key and SQLite database remain on the server. */
class BackendTranslationProvider internal constructor(
    private val http: BackendHttp,
    private val configured: Boolean = true,
) : TranslationProvider {
    constructor() : this(UrlConnectionHttp(BuildConfig.BACKEND_URL), BuildConfig.BACKEND_URL.startsWith("https://"))

    override suspend fun translate(text: String, direction: Direction): TranslationResult {
        if (!configured) return TranslationResult.Failure(FailureReason.PROVIDER_ERROR)
        if (text.isBlank() || text.length > 4000) return TranslationResult.Failure(FailureReason.UNSUPPORTED_INPUT)
        return try {
            withTimeout(30_000) {
                TranslationTimeline.mark(TimingMark.T_CLIENT_READY)
                val body = JSONObject().put("text", text).put("direction", direction.name).toString()
                TranslationTimeline.mark(TimingMark.T_REQUEST_SENT)
                // Only explicit user Retry resends selected text; a timeout may have spent quota.
                parseResponse(http.translate(body))
            }
        } catch (deadline: TimeoutCancellationException) {
            TranslationResult.Failure(FailureReason.TIMEOUT)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (timeout: SocketTimeoutException) {
            TranslationResult.Failure(FailureReason.TIMEOUT)
        } catch (network: IOException) {
            TranslationResult.Failure(FailureReason.PROVIDER_ERROR)
        }
    }

    private fun parseResponse(response: HttpResponse): TranslationResult {
        if (response.status !in 200..299) {
            val reason = when (response.status) {
                400, 413, 415 -> FailureReason.UNSUPPORTED_INPUT
                429 -> FailureReason.RATE_LIMITED
                408, 504, 522, 524 -> FailureReason.TIMEOUT
                else -> {
                    val code = try { JSONObject(response.body).optString("error") } catch (_: JSONException) { "" }
                    if (code == "INVALID_RESPONSE") FailureReason.INVALID_RESPONSE else FailureReason.PROVIDER_ERROR
                }
            }
            return TranslationResult.Failure(reason)
        }
        val translated = try { JSONObject(response.body).get("translation") as? String }
            catch (_: JSONException) { null }
        if (translated.isNullOrBlank() || translated.length > 16000) return TranslationResult.Failure(FailureReason.INVALID_RESPONSE)
        TranslationTimeline.mark(TimingMark.T_RESPONSE_END)
        return TranslationResult.Success(translated)
    }
}
