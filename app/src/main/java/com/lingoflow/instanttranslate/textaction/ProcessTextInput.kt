package com.lingoflow.instanttranslate.textaction

import android.content.Intent

/**
 * Validated, sanitized Process Text input. [text] is an immutable flattened copy — any spans
 * or styling the host attached are dropped here and never relayed onward (FR-03).
 */
data class ValidatedInput(val text: String, val isReadOnly: Boolean)

/**
 * Text-action adapter input validation (docs/TECHNICAL_PLAN.md): action, MIME/extra shape, and
 * size, exactly as exercised by docs/VALIDATION_PLAN.md §2.4's "wrong action, wrong MIME type,
 * missing extra, wrong extra type, and malformed parcel" cases. MIME type itself is enforced
 * by the manifest intent-filter; this only re-checks what a caller could still spoof by
 * launching the exported activity directly with an arbitrary intent.
 */
object ProcessTextInput {

    // Shared with the HTTP provider/backend input boundary; final product limits still need
    // benchmark validation. Reject oversized selections before any cloud request.
    private const val MAX_LENGTH = 4000

    fun validate(intent: Intent): ValidatedInput? {
        if (intent.action != Intent.ACTION_PROCESS_TEXT) return null

        val raw: CharSequence = readCharSequenceExtraSafely(intent) ?: return null
        if (raw.isBlank()) return null
        if (raw.length > MAX_LENGTH) return null

        // Missing or wrong-typed read-only extra defaults to read-only (FR-09): the safer
        // assumption when we cannot prove the host field actually accepts a replacement.
        val isReadOnly = try {
            intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        } catch (malformed: RuntimeException) {
            true
        }

        return ValidatedInput(text = raw.toString(), isReadOnly = isReadOnly)
    }

    private fun readCharSequenceExtraSafely(intent: Intent): CharSequence? =
        try {
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
        } catch (malformedOrWrongType: RuntimeException) {
            null
        }
}
