package com.antonchuraev.homesearchchecklist.feature.paywall.data

object PaywallConfig {
    // RevenueCat API keys
    const val ANDROID_API_KEY = "goog_tpOemOyrsIGCbUGnmCWVeRhRZUT"
    const val IOS_API_KEY = "" // TODO: Add iOS key when App Store is configured

    // Offering identifier (configure in RevenueCat dashboard)
    const val DEFAULT_OFFERING_ID = "default"

    // Entitlement identifier (configure in RevenueCat dashboard)
    const val PREMIUM_ENTITLEMENT_ID = "AiChecklists Pro"
}
