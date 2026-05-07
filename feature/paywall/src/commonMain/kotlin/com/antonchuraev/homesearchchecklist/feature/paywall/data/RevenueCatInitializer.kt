package com.antonchuraev.homesearchchecklist.feature.paywall.data

/**
 * Platform-specific RevenueCat API key.
 * Returns empty string on platforms without in-app purchase support (e.g. web).
 */
expect fun getPlatformApiKey(): String
