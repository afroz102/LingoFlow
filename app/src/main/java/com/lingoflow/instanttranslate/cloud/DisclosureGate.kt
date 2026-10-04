package com.lingoflow.instanttranslate.cloud

/**
 * Gates content transmission on the user's disclosure acknowledgement. The SharedPreferences
 * implementation stores only a notice version; the coordinator can use a fake in unit tests.
 */
interface DisclosureGate {
    fun isAcknowledged(): Boolean
    fun acknowledge()
}
