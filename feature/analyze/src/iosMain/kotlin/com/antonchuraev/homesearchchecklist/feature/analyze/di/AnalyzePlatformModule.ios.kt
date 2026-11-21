package com.antonchuraev.homesearchchecklist.feature.analyze.di

import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.AlwaysForegroundMonitor
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.CoroutineAnalysisJobLauncher
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.InMemoryAnalysisPayloadStore
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.NoOpAnalysisReadyNotifier
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.PassThroughImageDownscaler
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobLauncher
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobProcessor
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisPayloadStore
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisReadyNotifier
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AppForegroundMonitor
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ImageDownscaler
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS stubs (the iOS app is built, not released): in-process coroutine launcher, in-memory payloads
 * (a killed app loses a file job → INTERRUPTED on resume), no downscale, no notification, always
 * foreground. A real BGTaskScheduler / UIImage / UNUserNotificationCenter side is future work.
 */
actual val analyzePlatformModule: Module = module {
    single<AnalysisJobLauncher> {
        val scope = this
        CoroutineAnalysisJobLauncher(processor = { scope.get<AnalysisJobProcessor>() }, logger = get())
    }
    single<AnalysisPayloadStore> { InMemoryAnalysisPayloadStore() }
    single<ImageDownscaler> { PassThroughImageDownscaler() }
    single<AppForegroundMonitor> { AlwaysForegroundMonitor() }
    single<AnalysisReadyNotifier> { NoOpAnalysisReadyNotifier() }
}
