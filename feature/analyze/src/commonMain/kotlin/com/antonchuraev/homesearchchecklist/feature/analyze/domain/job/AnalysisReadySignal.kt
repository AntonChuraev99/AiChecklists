package com.antonchuraev.homesearchchecklist.feature.analyze.domain.job

import kotlinx.coroutines.flow.StateFlow

/**
 * The "your checklist is ready" signal OUTSIDE the Analyze screen. Collected by the app shell
 * (App.kt, next to the existing app-level `SnackbarHostState`), so it works over every screen of
 * both navigation shells (v1 Main and v2 Inbox) without a per-screen hook.
 *
 * Derived state, not a fire-once event: [pending] is recomputed from the persisted jobs, so a
 * signal missed while no collector was attached (process death, app relaunch, notification
 * dismissed) is still there on the next composition. Implementation: commonMain, `@feature-expert`.
 */
interface AnalysisReadySignal {

    /**
     * Terminal, not-yet-acknowledged jobs that no Analyze screen claimed, oldest first. Empty while
     * the app is not in the foreground (the notification covers that case) and while an Analyze
     * screen is attached to the job (that screen delivers it itself).
     */
    val pending: StateFlow<List<ReadyAnalysis>>

    /**
     * The snackbar for [jobId] was shown and timed out / was swiped away: acknowledge, no navigation.
     * The checklist itself stays in the user's list; nothing is lost.
     */
    suspend fun onDismissed(jobId: String)

    /**
     * "Open" on the snackbar or a notification tap ([via]). Claims delivery; on a win returns where to
     * go and runs the post-create hooks that need a live UI (activation coordinator, `CHECKLIST_OPENED`
     * analytics). Returns null when someone else already delivered it — the caller then does nothing.
     */
    suspend fun onOpen(jobId: String, via: DeliveryClaimant): ReadyDestination?

    /**
     * "View" on the folded summary snackbar ("3 checklists are ready", DESIGN_SPEC G2): claims every
     * job as [DeliveryClaimant.IN_APP_HOST], runs the post-create hooks, acknowledges them. The caller
     * opens the main list. Jobs someone else already delivered are skipped silently.
     */
    suspend fun onOpenSummary(jobIds: List<String>)
}

sealed interface ReadyAnalysis {
    val jobId: String

    /** New checklist was created: "Checklist "<name>" is ready · Open". */
    data class ChecklistCreated(override val jobId: String, val checklistName: String) : ReadyAnalysis

    /** Fill answers are ready for review: "Answers for "<checklist>" are ready · Review". */
    data class FillReady(override val jobId: String, val checklistName: String) : ReadyAnalysis

    /**
     * Background job failed: "Couldn't create a checklist · Try again" (retryable) or
     * "· Details" (input problem → opens Analyze so the user can change the input).
     */
    data class Failed(override val jobId: String, val failure: AnalysisJobFailure) : ReadyAnalysis
}

/** Navigation decided by [AnalysisReadySignal.onOpen]; App.kt maps it onto `AppNavigator` calls. */
sealed interface ReadyDestination {
    /** `navigateToChecklistDetail(id)` — a normal push, never `clearBackStack`. */
    data class ChecklistDetail(val checklistId: Long) : ReadyDestination

    /** `AnalyzeResultHolder` is already set from the persisted result; `navigateToAnalyzeResultPreview()`. */
    data object FillPreview : ReadyDestination

    /**
     * Failed job: open Analyze attached to it ([AnalysisJobHandoff] carries [jobId]) — the screen shows
     * the reason, its buttons and the captured input. [fillChecklistId] opens the fill Analyze of that
     * checklist; null = the create Analyze.
     */
    data class AnalyzeRetry(
        val jobId: String,
        val fillChecklistId: Long? = null,
        val fillDefault: Boolean = false,
    ) : ReadyDestination
}
