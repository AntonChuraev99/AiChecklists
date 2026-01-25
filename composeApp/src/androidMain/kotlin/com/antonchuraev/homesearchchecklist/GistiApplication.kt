package com.antonchuraev.homesearchchecklist

import android.app.Application
import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import com.antonchuraev.homesearchchecklist.feature.paywall.data.RevenueCatInitializer

class GistiApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize RevenueCat early (before any UI)
        RevenueCatInitializer.initialize(
            apiKey = PaywallConfig.ANDROID_API_KEY,
            isDebug = AppBuildConfig.isDebug
        )
    }
}
