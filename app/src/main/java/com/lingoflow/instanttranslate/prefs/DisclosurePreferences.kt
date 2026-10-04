package com.lingoflow.instanttranslate.prefs

import android.content.Context
import androidx.core.content.edit
import com.lingoflow.instanttranslate.cloud.DisclosureGate

/**
 * Stores only the acknowledged disclosure version. Bump [CURRENT_DISCLOSURE_VERSION] when
 * processing or terms change so an older acknowledgement cannot silently approve a new notice.
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
