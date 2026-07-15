package com.antonchuraev.homesearchchecklist.core.common.impl.di

import com.antonchuraev.homesearchchecklist.core.common.api.AiModelExperimentTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppDispatchersProvider
import com.antonchuraev.homesearchchecklist.core.common.impl.AiModelExperimentTrackerImpl
import com.antonchuraev.homesearchchecklist.core.common.impl.createLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val commonCoreModule = module {
    single<AppLogger> { createLogger() }
    single<AppDispatchersProvider> { AppDispatchersProvider.DEFAULT }
    // App-wide scope: sync, credits convergence, and any write that must outlive the ViewModel that
    // started it. SupervisorJob is load-bearing, not decoration — CoroutineScope(context) supplies a
    // plain Job() when the context has none, so ONE failing child would cancel this singleton's Job
    // and every later launch{} on it would silently no-op for the rest of the process. Consumers are
    // independent of each other, so a failure must stay local to the child that threw.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    // Depends on AnalyticsTracker (platform module) + AiExperimentPrefsRepository (datastoreModule);
    // both are resolved lazily from the aggregated appModule at first use.
    single<AiModelExperimentTracker> {
        AiModelExperimentTrackerImpl(analytics = get(), prefs = get(), logger = get())
    }
}
