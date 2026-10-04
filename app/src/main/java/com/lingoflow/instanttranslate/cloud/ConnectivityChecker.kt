package com.lingoflow.instanttranslate.cloud

/**
 * Pre-flight connectivity check (docs/TECHNICAL_PLAN.md §3: the coordinator "verif[ies]
 * disclosure, connectivity, and provider availability" before spending a request). A narrow
 * interface — implemented with ConnectivityManager in [AndroidConnectivityChecker] — so the
 * coordinator can be unit-tested without an Android Context.
 */
interface ConnectivityChecker {
    fun isConnected(): Boolean
}
