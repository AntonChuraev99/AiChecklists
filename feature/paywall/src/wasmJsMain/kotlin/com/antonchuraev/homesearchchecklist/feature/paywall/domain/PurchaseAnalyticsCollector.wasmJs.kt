package com.antonchuraev.homesearchchecklist.feature.paywall.domain

// No billing on web target — return stubs.
actual fun getDeviceCountry(): String? = null
actual fun getPlayStoreVersion(): String? = null
