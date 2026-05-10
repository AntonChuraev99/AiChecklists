package com.antonchuraev.homesearchchecklist.feature.paywall.domain

import android.content.pm.PackageManager
import org.koin.core.context.GlobalContext
import android.app.Application

actual fun getDeviceCountry(): String? = runCatching {
    java.util.Locale.getDefault().country.takeIf { it.isNotBlank() }
}.getOrNull()

actual fun getPlayStoreVersion(): String? = runCatching {
    val context = GlobalContext.get().get<Application>()
    @Suppress("DEPRECATION")
    context.packageManager
        .getPackageInfo("com.android.vending", 0)
        .versionName
}.getOrNull()
