package com.antonchuraev.homesearchchecklist.feature.analyze.di

import android.content.Context
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.CoroutineAnalysisJobLauncher
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobLauncher
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobProcessor
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisPayloadStore
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisReadyNotifier
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AppForegroundMonitor
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ImageDownscaler
import com.antonchuraev.homesearchchecklist.feature.analyze.platform.AnalysisNotificationStyle
import com.antonchuraev.homesearchchecklist.feature.analyze.platform.AnalysisNotifications
import com.antonchuraev.homesearchchecklist.feature.analyze.platform.AndroidAnalysisReadyNotifier
import com.antonchuraev.homesearchchecklist.feature.analyze.platform.AndroidImageDownscaler
import com.antonchuraev.homesearchchecklist.feature.analyze.platform.FileAnalysisPayloadStore
import com.antonchuraev.homesearchchecklist.feature.analyze.platform.ProcessLifecycleAppForegroundMonitor
import com.antonchuraev.homesearchchecklist.feature.analyze.platform.WorkManagerAnalysisJobLauncher
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android ports. `Context` comes from the app's platform module / `androidContext`; the optional
 * [AnalysisNotificationStyle] (small icon) from the app module, launcher icon otherwise.
 */
actual val analyzePlatformModule: Module = module {
    single<AnalysisJobLauncher> {
        val scope = this
        val logger: AppLogger = get()
        WorkManagerAnalysisJobLauncher(
            context = get<Context>(),
            store = get(),
            logger = logger,
            fallback = CoroutineAnalysisJobLauncher(processor = { scope.get<AnalysisJobProcessor>() }, logger = logger),
        )
    }
    single<AnalysisPayloadStore> { FileAnalysisPayloadStore(context = get<Context>(), logger = get()) }
    single<ImageDownscaler> { AndroidImageDownscaler(logger = get()) }
    single<AppForegroundMonitor> { ProcessLifecycleAppForegroundMonitor(logger = get()) }
    single<AnalysisReadyNotifier> {
        val context: Context = get()
        val logger: AppLogger = get()
        val style: AnalysisNotificationStyle? = getOrNull()
        if (style == null) logger.warning("AnalysisJob", "no AnalysisNotificationStyle bound — launcher icon used")
        AndroidAnalysisReadyNotifier(
            context = context,
            logger = logger,
            smallIconRes = AnalysisNotifications.resolveSmallIcon(context, style),
        )
    }
}
