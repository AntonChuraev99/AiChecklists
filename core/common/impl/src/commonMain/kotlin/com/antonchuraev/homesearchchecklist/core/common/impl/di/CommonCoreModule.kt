package com.antonchuraev.homesearchchecklist.core.common.impl.di

import com.antonchuraev.homesearchchecklist.core.common.api.AiModelExperimentTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppDispatchersProvider
import com.antonchuraev.homesearchchecklist.core.common.impl.AiModelExperimentTrackerImpl
import com.antonchuraev.homesearchchecklist.core.common.impl.createLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module

val commonCoreModule = module {
    single<AppLogger> { createLogger() }
    single<AppDispatchersProvider> { AppDispatchersProvider.DEFAULT }
    single<CoroutineScope> { CoroutineScope(Dispatchers.Default) }
    // Depends on AnalyticsTracker (platform module) + AiExperimentPrefsRepository (datastoreModule);
    // both are resolved lazily from the aggregated appModule at first use.
    single<AiModelExperimentTracker> {
        AiModelExperimentTrackerImpl(analytics = get(), prefs = get(), logger = get())
    }
}
