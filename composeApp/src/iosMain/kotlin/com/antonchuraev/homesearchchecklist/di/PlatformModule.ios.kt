package com.antonchuraev.homesearchchecklist.di

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.csat.ObservableAnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.datastore.api.UserAppDatastoreProvider
import com.antonchuraev.homesearchchecklist.feature.analyze.data.config.GeminiConfig
import com.antonchuraev.homesearchchecklist.feature.user.data.device.DeviceIdProvider
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSBundle

private object StubAnalyticsTracker : AnalyticsTracker {
    override fun setUserId(userId: String) = Unit
    override fun screenView(name: String) = Unit
    override fun event(name: String, params: Map<String, Any>) = Unit
}

actual fun platformModule(): Module = module {
    single {
        val apiKey = NSBundle.mainBundle.objectForInfoDictionaryKey("GEMINI_API_KEY") as? String ?: ""
        GeminiConfig(apiKey = apiKey)
    }
    single { DeviceIdProvider(UserAppDatastoreProvider.instance) }
    single<AnalyticsTracker> { ObservableAnalyticsTracker(StubAnalyticsTracker) }
}
