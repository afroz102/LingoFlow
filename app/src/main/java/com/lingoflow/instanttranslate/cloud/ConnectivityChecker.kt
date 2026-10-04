package com.lingoflow.instanttranslate.cloud

/**
 * Pre-flight connectivity gate, separate from provider failures. The interface keeps the
 * coordinator testable without an Android Context; [AndroidConnectivityChecker] implements it.
 */
interface ConnectivityChecker {
    fun isConnected(): Boolean
}
