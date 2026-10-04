package com.lingoflow.instanttranslate.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Checks for validated internet access, not just an active network interface — a Wi-Fi
 * association with no real route out (e.g. a captive portal) is still "offline" here, so the app
 * reports [com.lingoflow.instanttranslate.coordinator.TranslationOutcome.Offline] immediately
 * instead of burning the full request timeout against Gemini and reporting a confusing
 * provider-error state for what is really a connectivity problem.
 */
class AndroidConnectivityChecker(context: Context) : ConnectivityChecker {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
