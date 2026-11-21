package com.antonchuraev.homesearchchecklist.feature.analyze.domain.job

import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import kotlinx.serialization.Serializable

/**
 * One AI analysis the user started on the Analyze screen, persisted so it outlives the screen, the
 * ViewModel, the app being backgrounded and (on Android) the process.
 *
 * The analysis is owned by the app-level [AnalysisJobScheduler], never by `AnalyzeViewModel`: the
 * ViewModel only enqueues a job and observes it. That is what lets the user press Back, switch
 * screens or leave the app without cancelling or losing the result.
 *
 * [id] IS the server `request_id`. It is generated once at enqueue and reused verbatim by every
 * attempt (WorkManager retry, process-death re-run, web reload resume), so the server's
 * `request_id` credit dedup never charges the user twice for one job.
 */
@Serializable
data class AnalysisJob(
    val id: String,
    val createdAtMs: Long,
    val input: AnalysisJobInput,
    val target: AnalysisJobTarget,
    /** Dimensions of `ai_analyze_*` captured at enqueue, so a background finish emits the same params. */
    val analytics: AnalysisJobAnalytics,
    val status: AnalysisJobStatus,
    /** Network attempts made so far (bounded by the platform launcher's retry policy). */
    val attempt: Int = 0,
    /** Who showed the outcome to the user; null while nobody has. Set once, by [AnalysisJobScheduler.claimDelivery]. */
    val deliveredBy: DeliveryClaimant? = null,
    /** True once the outcome needs no further in-app or notification signal; the job is then prunable. */
    val acknowledged: Boolean = false,
)

/** What the job sends to the server. File material is copied into app storage at enqueue time. */
@Serializable
sealed interface AnalysisJobInput {

    /** Raw text or the content of a `.txt` file read at enqueue time. */
    @Serializable
    data class Text(val text: String) : AnalysisJobInput

    /** Already validated and normalized `http(s)` URL. */
    @Serializable
    data class Url(val url: String) : AnalysisJobInput

    /**
     * Photo / PDF / voice recording. The bytes live in [AnalysisPayloadStore] under the job id —
     * never the picker's path, which may point into a cache the OS clears while the job waits.
     * The photo is downscaled by the job, not at enqueue, so the Analyze tap stays instant.
     */
    @Serializable
    data class StoredFile(
        val kind: StoredFileKind,
        val fileName: String,
        val mimeType: String,
    ) : AnalysisJobInput
}

@Serializable
enum class StoredFileKind { PHOTO, PDF, AUDIO }

/** Where the result goes. Mirrors the two existing Analyze outcomes. */
@Serializable
sealed interface AnalysisJobTarget {

    /**
     * `generate_checklist` → a new checklist is written to Room by the job itself.
     * [fromActivationHero] = the screen was opened with `autoAnalyze` (activation hero), which decides
     * `onAiChecklistCreated` vs `onUserChecklistCreated` at delivery time.
     */
    @Serializable
    data class NewChecklist(val fromActivationHero: Boolean) : AnalysisJobTarget

    /**
     * `analyze_and_fill_checklist` → nothing is written by the job; the result waits in
     * [AnalysisJobOutcome.FillReady] for the user to review it in the editable preview.
     */
    @Serializable
    data class Fill(
        val checklistId: Long,
        val checklistName: String,
        val fillDefault: Boolean,
        val isFillMode: Boolean,
    ) : AnalysisJobTarget
}

@Serializable
data class AnalysisJobAnalytics(
    /** `InputDataType.name.lowercase()` — same value `ai_analyze_started` carried. */
    val inputType: String,
    /** `AiEntrySource.wire` or `"none"`. */
    val source: String,
)

@Serializable
sealed interface AnalysisJobStatus {

    /** Enqueued, not sent yet. On Android this also covers "waiting for a network connection". */
    @Serializable
    data class Queued(val waitingForNetwork: Boolean) : AnalysisJobStatus

    /** A request is in flight (or the payload is being prepared). */
    @Serializable
    data object Running : AnalysisJobStatus

    @Serializable
    data class Succeeded(val outcome: AnalysisJobOutcome) : AnalysisJobStatus

    @Serializable
    data class Failed(val failure: AnalysisJobFailure) : AnalysisJobStatus

    /** The user cancelled while [Queued]. A sent request is never cancellable (credits are reserved). */
    @Serializable
    data object Cancelled : AnalysisJobStatus
}

val AnalysisJobStatus.isTerminal: Boolean
    get() = this is AnalysisJobStatus.Succeeded ||
        this is AnalysisJobStatus.Failed ||
        this is AnalysisJobStatus.Cancelled

@Serializable
sealed interface AnalysisJobOutcome {

    /** The checklist is already in Room; delivery only has to open it. */
    @Serializable
    data class CreatedChecklist(
        val checklistId: Long,
        val checklistName: String,
        val itemCount: Int,
        val hasFolders: Boolean,
    ) : AnalysisJobOutcome

    /**
     * Fill result persisted for review. [fillItems] is carried explicitly because
     * `AnalyzeResult.fillItems` is `@Transient` and would be lost across process death.
     */
    @Serializable
    data class FillReady(
        val suggestedItems: List<ChecklistItem>,
        val fillItems: List<ChecklistFillItem>,
        val summary: String?,
    ) : AnalysisJobOutcome
}

@Serializable
data class AnalysisJobFailure(
    val reason: AiFailureReason,
    /** Server's own message when it is user-presentable (credits / limit); never a transport marker. */
    val serverMessage: String? = null,
    /**
     * True when re-running the SAME input can succeed (network, timeout, 5xx, user-not-ready).
     * False for failures about the input itself (too large, nothing found, unreadable) — a retry
     * with the same input would charge-and-fail or loop; the UI offers "change input" instead.
     */
    val retryableWithSameInput: Boolean,
)

@Serializable
enum class DeliveryClaimant {
    /** The Analyze screen that watched the job navigated to the result. */
    SCREEN,

    /** The app-level host (snackbar with "Open") while the app was in the foreground. */
    IN_APP_HOST,

    /** The user tapped the system notification. */
    NOTIFICATION,
}
