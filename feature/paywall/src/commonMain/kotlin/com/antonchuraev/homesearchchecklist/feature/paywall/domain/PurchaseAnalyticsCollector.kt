package com.antonchuraev.homesearchchecklist.feature.paywall.domain

/**
 * Platform-specific helpers for purchase analytics context.
 *
 * getDeviceCountry(): ISO 3166-1 alpha-2 from device locale (e.g. "US", "ET", "KZ").
 *   Null on platforms where locale is unavailable.
 *
 * getPlayStoreVersion(): Google Play Store app versionName — Android only.
 *   Null on iOS and web (no Play Store).
 */
expect fun getDeviceCountry(): String?
expect fun getPlayStoreVersion(): String?
