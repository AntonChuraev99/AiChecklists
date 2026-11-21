package com.antonchuraev.homesearchchecklist.feature.analyze.data.job

import com.antonchuraev.homesearchchecklist.core.common.api.AiModelExperimentTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.analyze.data.util.FileReader
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobLauncher
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobProcessor
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobScheduler
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AppForegroundMonitor
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.CoroutineScope

/**
 * A self-contained job graph (memory store and payloads, coroutine launcher on [scope]). Production
 * gets the app-scoped graph from Koin; this exists so a ViewModel built without it — the JVM unit
 * tests — still runs the SAME scheduler / processor code instead of a second, test-only path.
 *
 * [launch] = false gives a launcher that never runs anything: a job stays Queued, which is what
 * "waiting for a connection" looks like to the screen.
 */
fun inProcessAnalysisJobScheduler(
    scope: CoroutineScope,
    analyzeRepository: AnalyzeRepository,
    checklistRepository: ChecklistRepository,
    analyticsTracker: AnalyticsTracker,
    foregroundMonitor: AppForegroundMonitor,
    defaultChecklistName: suspend () -> String,
    aiModelExperimentTracker: AiModelExperimentTracker? = null,
    logger: AppLogger = SilentAppLogger,
    launch: Boolean = true,
): AnalysisJobScheduler {
    val store = InMemoryAnalysisJobStore()
    val payloads = InMemoryAnalysisPayloadStore()
    val processor: AnalysisJobProcessor = AnalysisJobProcessorImpl(
        store = store,
        payloadStore = payloads,
        downscaler = PassThroughImageDownscaler(),
        analyzeRepository = analyzeRepository,
        findChecklist = checklistRepository::getChecklistById,
        analyticsTracker = analyticsTracker,
        foregroundMonitor = foregroundMonitor,
        notifier = NoOpAnalysisReadyNotifier(),
        logger = logger,
        defaultChecklistName = defaultChecklistName,
        aiModelExperimentTracker = aiModelExperimentTracker,
    )
    val launcher: AnalysisJobLauncher = if (launch) {
        CoroutineAnalysisJobLauncher(processor = { processor }, logger = logger, scope = scope)
    } else {
        object : AnalysisJobLauncher {
            override fun launch(jobId: String) = Unit
            override fun cancel(jobId: String) = Unit
        }
    }
    return AnalysisJobManager(
        store = store,
        payloadStore = payloads,
        launcher = launcher,
        analyticsTracker = analyticsTracker,
        logger = logger,
        scope = scope,
        readFileBytes = FileReader::readBytes,
        readFileText = FileReader::readText,
    )
}

/** Logger of the in-process graph when the caller has none (unit tests). */
internal object SilentAppLogger : AppLogger {
    override fun debug(tag: String, message: String) = Unit
    override fun info(tag: String, message: String) = Unit
    override fun warning(tag: String, message: String) = Unit
    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}
