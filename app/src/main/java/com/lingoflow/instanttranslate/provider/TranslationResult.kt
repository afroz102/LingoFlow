package com.lingoflow.instanttranslate.provider

/** Content-free provider result. Backend/Gemini errors never expose upstream bodies to UI. */
sealed interface TranslationResult {
    data class Success(val translatedText: String) : TranslationResult
    data class Failure(val reason: FailureReason) : TranslationResult
}

enum class FailureReason {
    /** Reserved for a provider that can distinguish this from a generic failure; used by the backend input boundary. */
    UNSUPPORTED_INPUT,

    /** Quota/rate limit from our backend or Gemini. Retry only on explicit user action. */
    RATE_LIMITED,

    /** The request did not complete within its bounded deadline. */
    TIMEOUT,

    /** Any other provider-side failure: server error, safety block, misconfiguration, or an unrecognized exception type. */
    PROVIDER_ERROR,

    /** The provider returned a response that was empty, malformed, or didn't match the expected schema. */
    INVALID_RESPONSE,
}
