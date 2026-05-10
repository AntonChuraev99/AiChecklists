package com.antonchuraev.homesearchchecklist.feature.paywall.domain

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.countryCode

actual fun getDeviceCountry(): String? = runCatching {
    NSLocale.currentLocale.countryCode?.takeIf { it.isNotBlank() }
}.getOrNull()

// No Play Store on iOS.
actual fun getPlayStoreVersion(): String? = null
