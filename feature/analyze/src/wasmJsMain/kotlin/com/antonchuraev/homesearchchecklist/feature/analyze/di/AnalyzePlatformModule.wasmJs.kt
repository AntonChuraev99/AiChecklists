package com.antonchuraev.homesearchchecklist.feature.analyze.di

import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.BeforeUnloadAwareLauncher
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.BeforeUnloadJobGuard
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.CoroutineAnalysisJobLauncher
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.InMemoryAnalysisPayloadStore
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.NoOpAnalysisReadyNotifier
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.VisibilityForegroundMonitor
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.WasmImageDownscaler
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobLauncher
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobProcessor
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobScheduler
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisPayloadStore
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisReadyNotifier
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AppForegroundMonitor
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ImageDownscaler
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Web half of the background-analysis graph:
 * - launcher: app-scope coroutine (no OS scheduler on the web), wrapped so the browser's
 *   `beforeunload` prompt is armed while any job is in flight;
 * - payloads: in memory — a reload fails a file job as INTERRUPTED (owner decision 6, v1);
 * - downscaler: `createImageBitmap` (EXIF-aware) + canvas JPEG;
 * - foreground: `document.visibilityState`;
 * - notifier: none — the outcome is delivered in-app when the user returns to the tab.
 *
 * The scheduler and processor are resolved lazily (inside `launch`), never at construction: the
 * scheduler itself depends on this launcher.
 */
actual val analyzePlatformModule: Module = module {
    single<AnalysisJobLauncher> {
        val scope = this
        BeforeUnloadAwareLauncher(
            delegate = CoroutineAnalysisJobLauncher(processor = { scope.get<AnalysisJobProcessor>() }, logger = get()),
            guard = BeforeUnloadJobGuard(jobs = { scope.get<AnalysisJobScheduler>().jobs }, logger = get()),
        )
    }
    single<AnalysisPayloadStore> { InMemoryAnalysisPayloadStore() }
    single<ImageDownscaler> { WasmImageDownscaler(logger = get()) }
    single<AppForegroundMonitor> { VisibilityForegroundMonitor() }
    single<AnalysisReadyNotifier> { NoOpAnalysisReadyNotifier() }
}
