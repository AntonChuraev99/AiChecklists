package com.antonchuraev.homesearchchecklist.di

import com.antonchuraev.aichecklists.BuildConfig
import com.antonchuraev.homesearchchecklist.Analytics
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.csat.ObservableAnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppContextHolder
import com.antonchuraev.homesearchchecklist.core.datastore.api.UserAppDatastoreProvider
import com.antonchuraev.homesearchchecklist.feature.analyze.data.config.GeminiConfig
import com.antonchuraev.homesearchchecklist.feature.user.data.device.DeviceIdProvider
import com.antonchuraev.homesearchchecklist.widget.di.widgetModule
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    includes(widgetModule)

    single { AppContextHolder.context }
    single { GeminiConfig(apiKey = BuildConfig.GEMINI_API_KEY) }
    single { DeviceIdProvider(UserAppDatastoreProvider.instance) }
    single<AnalyticsTracker> { ObservableAnalyticsTracker(Analytics) }
}
