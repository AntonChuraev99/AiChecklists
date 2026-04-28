package com.antonchuraev.homesearchchecklist.feature.paywall.data

object PaywallConfig {
    // RevenueCat API keys
    const val ANDROID_API_KEY = "goog_tpOemOyrsIGCbUGnmCWVeRhRZUT"
    const val IOS_API_KEY = "" // iOS release planned after Android revenue covers Apple Store publication cost

    // Preferred offering identifier — resolved by name from RevenueCat dashboard.
    // Falls back to offerings.current if this name is missing on the server, so
    // changing the dashboard offering won't break old builds shipping a different
    // OFFERING_ID value. Bump this when introducing a new offering version.
    const val OFFERING_ID = "monthAndYear"

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
