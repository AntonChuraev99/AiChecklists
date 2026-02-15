package com.antonchuraev.homesearchchecklist

import android.app.Application
import com.antonchuraev.homesearchchecklist.core.common.api.AppContextHolder
import com.antonchuraev.homesearchchecklist.di.appModule
import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import com.antonchuraev.homesearchchecklist.feature.paywall.data.RevenueCatInitializer
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

open class GistiApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize AppContextHolder first (required for DI)
        AppContextHolder.init(this)

        // Initialize Koin if not already started (for widget support)
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidLogger()
                androidContext(this@GistiApplication)
                modules(appModule)
            }
        }

        initRevenueCat()
    }

    /**
     * Initialize RevenueCat for subscription management.
     * Open so that test Application subclass can skip it to avoid
     * creating fake anonymous users in RevenueCat dashboard.
     */
    protected open fun initRevenueCat() {
        RevenueCatInitializer.initialize(
            apiKey = PaywallConfig.ANDROID_API_KEY,
            isDebug = AppBuildConfig.isDebug
        )
    }
}
