package com.antonchuraev.homesearchchecklist.feature.paywall.data

object PaywallConfig {
    // RevenueCat API keys (test key works for both platforms)
    const val ANDROID_API_KEY = "test_BpmLKytcnMzPKqqsltbfcyVgFIH"
    const val IOS_API_KEY = "test_BpmLKytcnMzPKqqsltbfcyVgFIH"

    // Offering identifier (configure in RevenueCat dashboard)
    const val DEFAULT_OFFERING_ID = "default"

    // Entitlement identifier (configure in RevenueCat dashboard)
    const val PREMIUM_ENTITLEMENT_ID = "AiChecklists Pro"
}
