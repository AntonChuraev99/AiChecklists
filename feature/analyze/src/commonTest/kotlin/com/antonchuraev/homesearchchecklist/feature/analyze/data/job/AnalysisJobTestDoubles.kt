package com.antonchuraev.homesearchchecklist.feature.analyze.data.job

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJob
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobAnalytics
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobInput
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobLauncher
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobStatus
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobTarget
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisReadyNotifier
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AppForegroundMonitor
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.PreparedAnalyzeInput
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import kotlinx.coroutines.flow.MutableStateFlow

/** Scripted analyze results, one per call; records the request id and input of every call. */
internal class ScriptedAnalyzeRepository(results: List<Result<AnalyzeResult>>) : AnalyzeRepository {
    constructor(single: Result<AnalyzeResult>) : this(listOf(single))

    private val script = results.toMutableList()
    val requestIds = mutableListOf<String?>()
    val inputs = mutableListOf<PreparedAnalyzeInput>()
    var createCount = 0

    override suspend fun analyzePrepared(
        input: PreparedAnalyzeInput,
        targetChecklist: Checklist?,
        requestId: String?,
    ): Result<AnalyzeResult> {
        requestIds += requestId
        inputs += input
        crash?.let { throw it }
        return if (script.size > 1) script.removeAt(0) else script.first()
    }

    override suspend fun analyzeData(inputData: AnalyzeInputData, targetChecklist: Checklist?): Result<AnalyzeResult> =
        error("the processor must go through analyzePrepared")

    /** The Room write fails this many times before it succeeds. */
    var createFailures = 0

    /** Thrown (not returned) by the analyze call — a crash, not a typed failure. */
    var crash: Throwable? = null

    override suspend fun createChecklistFromResult(name: String, result: AnalyzeResult): Result<Checklist> {
        if (createFailures > 0) {
            createFailures--
            return Result.failure(IllegalStateException("SQLiteFullException: database or disk is full"))
        }
        createCount++
        return Result.success(Checklist(id = 100L + createCount, name = name, items = result.suggestedItems))
    }

    override suspend fun applyToChecklist(checklistId: Long, result: AnalyzeResult): Result<Checklist> = error("unused")
    override suspend fun createFillFromResult(checklistId: Long, fillName: String, result: AnalyzeResult): Result<Long> =
        error("unused")
    override suspend fun isAnalyzerAvailable(): Boolean = true
}

internal fun successResult(name: String = "Trip") =
    Result.success(AnalyzeResult(suggestedItems = listOf(ChecklistItem(text = "Passport")), suggestedName = name))

internal class RecordingTracker : AnalyticsTracker {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    override fun setUserId(userId: String) {}
    override fun setUserProperties(properties: Map<String, Any>) {}
    override fun screenView(name: String) {}
    override fun event(name: String, params: Map<String, Any>) {
        events += name to params
    }
}

internal class RecordingLauncher : AnalysisJobLauncher {
    val launched = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    override fun launch(jobId: String) {
        launched += jobId
    }
    override fun cancel(jobId: String) {
        cancelled += jobId
    }
}

internal class RecordingNotifier : AnalysisReadyNotifier {
    val notified = mutableListOf<String>()
    override fun notifyFinished(job: AnalysisJob) {
        notified += job.id
    }
    override fun cancel(jobId: String) = Unit
}

internal class FakeForeground(initial: Boolean = true) : AppForegroundMonitor {
    override val isInForeground = MutableStateFlow(initial)
}

internal fun queuedJob(
    id: String,
    input: AnalysisJobInput = AnalysisJobInput.Text("pack for a trip"),
    status: AnalysisJobStatus = AnalysisJobStatus.Queued(waitingForNetwork = false),
) = AnalysisJob(
    id = id,
    createdAtMs = 1L,
    input = input,
    target = AnalysisJobTarget.NewChecklist(fromActivationHero = false),
    analytics = AnalysisJobAnalytics(inputType = "raw_text", source = "none"),
    status = status,
)
