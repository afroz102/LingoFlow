package com.lingoflow.instanttranslate.provider.backend

import com.lingoflow.instanttranslate.BuildConfig
import com.lingoflow.instanttranslate.direction.TranslationLanguages
import com.lingoflow.instanttranslate.direction.TranslationLanguagePair
import com.lingoflow.instanttranslate.direction.DirectionDetector
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

    override suspend fun translate(text: String, direction: Direction): TranslationResult = request(text, direction, null)

    override suspend fun translate(text: String, languages: TranslationLanguagePair): TranslationResult =
        if (languages.isValid()) request(text, Direction.MULTILINGUAL, languages)
        else TranslationResult.Failure(FailureReason.UNSUPPORTED_INPUT)

    private suspend fun request(text: String, direction: Direction, languages: TranslationLanguagePair?): TranslationResult {
        if (direction == Direction.MULTILINGUAL && languages == null) return TranslationResult.Failure(FailureReason.UNSUPPORTED_INPUT)
        if (languages == null && direction != Direction.READ_TO_ENGLISH &&
            text.contains(Regex("[\\u0900-\\u097f\\ua8e0-\\ua8ff\\x{11B00}-\\x{11B09}]")))
            return TranslationResult.Failure(FailureReason.UNSUPPORTED_INPUT)
        if (!configured) return TranslationResult.Failure(FailureReason.PROVIDER_ERROR)
        if (!DirectionDetector.isSupported(text)) return TranslationResult.Failure(FailureReason.UNSUPPORTED_INPUT)
        return try {
            withTimeout(30_000) {
                TranslationTimeline.mark(TimingMark.T_CLIENT_READY)
                val payload = JSONObject().put("text", text).put("direction", direction.name)
                languages?.let { payload.put("sourceLanguage", it.source).put("targetLanguage", it.target) }
                val body = payload.toString()
                TranslationTimeline.mark(TimingMark.T_REQUEST_SENT)
                // Only explicit user Retry resends selected text; a timeout may have spent quota.
                parseResponse(http.translate(body), direction, languages)
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

    private fun parseResponse(response: HttpResponse, requestedDirection: Direction, languages: TranslationLanguagePair?): TranslationResult {
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
        val json = try { JSONObject(response.body) } catch (_: JSONException) { null }
            ?: return TranslationResult.Failure(FailureReason.INVALID_RESPONSE)
        val translated = json.opt("translation") as? String
        if (languages != null && (json.optString("sourceLanguage") != languages.source ||
            json.optString("targetLanguage") != languages.target)) return TranslationResult.Failure(FailureReason.INVALID_RESPONSE)
        if (translated.isNullOrBlank() || translated.length > 16000 ||
            (languages == null && (translated.any { it in '\u0900'..'\u097f' || it in '\ua8e0'..'\ua8ff' } ||
            translated.contains(Regex("[\\x{11B00}-\\x{11B09}]"))))) return TranslationResult.Failure(FailureReason.INVALID_RESPONSE)
        if (languages != null && TranslationLanguages.find(languages.target)?.romanized == true &&
            translated.contains(Regex("[^\\p{IsLatin}\\p{IsCommon}\\p{IsInherited}]"))) return TranslationResult.Failure(FailureReason.INVALID_RESPONSE)
        // Older servers may omit direction for explicit requests; AUTO/MULTILINGUAL require explicit response direction.
        val direction = if (json.has("direction")) {
            val name = json.opt("direction") as? String
            Direction.entries.firstOrNull { it.name == name }
        } else requestedDirection.takeUnless { it == Direction.AUTO || it == Direction.MULTILINGUAL }
        if (direction == null || !requestedDirection.acceptsResolved(direction)) {
            return TranslationResult.Failure(FailureReason.INVALID_RESPONSE)
        }
        TranslationTimeline.mark(TimingMark.T_RESPONSE_END)
        return TranslationResult.Success(translated, direction)
    }
}
