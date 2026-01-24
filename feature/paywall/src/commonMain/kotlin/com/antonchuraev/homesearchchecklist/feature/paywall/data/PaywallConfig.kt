package com.antonchuraev.homesearchchecklist.feature.paywall.data

object PaywallConfig {
    // RevenueCat API keys
    const val ANDROID_API_KEY = "goog_tpOemOyrsIGCbUGnmCWVeRhRZUT"
    const val IOS_API_KEY = "" // TODO: Add iOS key when App Store is configured

    // Offering identifier (configure in RevenueCat dashboard)
    const val DEFAULT_OFFERING_ID = "default"

    // Entitlement identifier (configure in RevenueCat dashboard)
    const val PREMIUM_ENTITLEMENT_ID = "AiChecklists Pro"

    // Legal pages URLs (Firebase Hosting)
    const val TERMS_OF_USE_URL = "https://gisti-app.web.app/terms"
    const val PRIVACY_POLICY_URL = "https://gisti-app.web.app/privacy-policy"

    // Default free trial days (fallback when RevenueCat doesn't return trial info)
    const val DEFAULT_FREE_TRIAL_DAYS = 3

    // Support email
    const val SUPPORT_EMAIL = "churaevanton@gmail.com"
}
