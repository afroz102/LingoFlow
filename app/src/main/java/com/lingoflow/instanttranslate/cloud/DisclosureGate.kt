package com.lingoflow.instanttranslate.cloud

/**
 * Whether the user has acknowledged that selected text leaves the device
 * (docs/TECHNICAL_PLAN.md §3 "Cloud readiness and disclosure": "prevent the first
 * selected-content request until disclosure acknowledgement"; docs/CLOUD_AND_HINGLISH_DECISION.md
 * "Privacy conflict": "a clear disclosure and acknowledgement is required before the first
 * request"). Kept as a narrow interface — implemented with SharedPreferences in
 * prefs/DisclosurePreferences.kt — so [com.lingoflow.instanttranslate.coordinator.TranslateCoordinator]
 * can be unit-tested without an Android Context.
 */
interface DisclosureGate {
    fun isAcknowledged(): Boolean
    fun acknowledge()
}
