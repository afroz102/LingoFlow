package com.lingoflow.instanttranslate.prefs

import android.content.Context
import androidx.core.content.edit
import com.lingoflow.instanttranslate.cloud.DisclosureGate

/**
 * Local, non-content preference (docs/TECHNICAL_PLAN.md §3 "Local preferences": "cloud-processing
 * disclosure acknowledgement version"). Plain SharedPreferences, per docs/TECHNICAL_PLAN.md §3's
 * "use the simplest platform storage ... do not add a repository abstraction or database solely
 * for a few settings."
 *
 * Stored as a version number, not a boolean, so a future change to the disclosure wording or the
 * underlying terms (docs/CLOUD_AND_HINGLISH_DECISION.md "Privacy conflict") can bump
 * [CURRENT_DISCLOSURE_VERSION] and re-prompt users who already acknowledged an older version,
 * instead of silently carrying forward consent to different terms.
 */
class DisclosurePreferences(context: Context) : DisclosureGate {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isAcknowledged(): Boolean =
        prefs.getInt(KEY_ACKNOWLEDGED_VERSION, 0) >= CURRENT_DISCLOSURE_VERSION

    override fun acknowledge() {
        prefs.edit { putInt(KEY_ACKNOWLEDGED_VERSION, CURRENT_DISCLOSURE_VERSION) }
    }

    private companion object {
        const val PREFS_NAME = "instanttranslate_prefs"
        const val KEY_ACKNOWLEDGED_VERSION = "cloud_disclosure_ack_version"
        const val CURRENT_DISCLOSURE_VERSION = 3
    }
}
