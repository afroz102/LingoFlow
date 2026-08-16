package com.lingoflow.instanttranslate.provider

import com.lingoflow.instanttranslate.direction.Direction

/**
 * Translation provider boundary (docs/TECHNICAL_PLAN.md §3): `translate(text, direction) →
 * translated text | typed failure`. No Firebase/Gemini types, prompts, or provider-specific
 * response objects may leak past this interface — the Gemini adapter (Stage 1) lives entirely
 * behind provider/gemini/ and implements this same contract.
 */
interface TranslationProvider {
    suspend fun translate(text: String, direction: Direction): TranslationResult
}
