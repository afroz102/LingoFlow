package com.lingoflow.instanttranslate.textaction

import android.content.Intent

/**
 * Validated, sanitized Process Text input. [text] is an immutable flattened copy — any spans
 * or styling the host attached are dropped here and never relayed onward (FR-03).
 */
data class ValidatedInput(val text: String, val isReadOnly: Boolean)

/** Re-check MIME/action for explicit callers too; manifest filters cover only implicit dispatch. */
object ProcessTextInput {

    // Shared with the HTTP provider/backend input boundary; final product limits still need
    // benchmark validation. Reject oversized selections before any cloud request.
    private const val MAX_LENGTH = 4000

    fun validate(intent: Intent): ValidatedInput? {
        if (intent.action != Intent.ACTION_PROCESS_TEXT || intent.type != "text/plain") return null

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
