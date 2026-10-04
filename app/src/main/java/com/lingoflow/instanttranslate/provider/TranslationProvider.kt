package com.lingoflow.instanttranslate.provider

import com.lingoflow.instanttranslate.direction.TranslationLanguagePair
import com.lingoflow.instanttranslate.direction.Direction

/**
 * Translation provider boundary (docs/TECHNICAL_PLAN.md): `translate(text, direction) →
 * translated text | typed failure`. No cloud SDK types, prompts, or provider-specific response objects leak past
 * this interface. The active HTTP adapter lives behind provider/backend/.
 */
interface TranslationProvider {
    suspend fun translate(text: String, direction: Direction): TranslationResult
    suspend fun translate(text: String, languages: TranslationLanguagePair): TranslationResult =
        TranslationResult.Failure(FailureReason.PROVIDER_ERROR)
}
