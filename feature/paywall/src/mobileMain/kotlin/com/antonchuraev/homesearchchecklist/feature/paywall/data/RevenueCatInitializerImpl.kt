package com.antonchuraev.homesearchchecklist.feature.paywall.data

import com.revenuecat.purchases.kmp.LogLevel
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration

/**
 * RevenueCat SDK initializer. Available on mobile platforms only (Android + iOS).
 * Not available on wasmJs — use no-op patterns there.
 */
object RevenueCatInitializer {

    private var isInitialized = false

    fun initialize(apiKey: String, appUserId: String? = null, isDebug: Boolean = false) {
        if (isInitialized) return

        if (isDebug) {
            Purchases.logLevel = LogLevel.DEBUG
        }

        val configuration = PurchasesConfiguration(apiKey) {
            appUserId?.let { this.appUserId = it }
            // Enable support for Google Play's pending prepaid plans
            pendingTransactionsForPrepaidPlansEnabled = true
        }
        Purchases.configure(configuration)

        isInitialized = true
    }

    fun isConfigured(): Boolean = Purchases.isConfigured
}
