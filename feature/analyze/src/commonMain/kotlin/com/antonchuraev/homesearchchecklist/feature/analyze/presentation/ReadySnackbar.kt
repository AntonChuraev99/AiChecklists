package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ReadyAnalysis
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason

/** What the app-level host shows next for finished background analyses (DESIGN_SPEC G2). */
sealed interface ReadySnackbar {

    /** Two or more new checklists are ready: ONE "N checklists are ready · View" (→ main list). */
    data class Summary(val jobIds: List<String>) : ReadySnackbar {
        val count: Int get() = jobIds.size
    }

    /** One outcome: a checklist ("Open"), fill answers ("Review") or a failure ("Try again" / "Details"). */
    data class Single(val item: ReadyAnalysis) : ReadySnackbar
}

/**
 * Picks the next snackbar from [AnalysisReadySignal.pending][com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisReadySignal.pending]
 * (oldest first). New checklists fold into one summary once there are two or more; fill answers and
 * failures are never folded — each needs its own action.
 */
fun nextReadySnackbar(pending: List<ReadyAnalysis>): ReadySnackbar? {
    val created = pending.filterIsInstance<ReadyAnalysis.ChecklistCreated>()
    if (created.size >= 2) return ReadySnackbar.Summary(created.map { it.jobId })
    return pending.firstOrNull()?.let { ReadySnackbar.Single(it) }
}

/** The checklist name inside a snackbar: at most [max] characters, then "…". */
fun truncateForSnackbar(name: String, max: Int = SNACKBAR_NAME_MAX): String =
    if (name.length <= max) name else name.take(max).trimEnd() + "…"

/** Long copy or large text puts the action under the message instead of squeezing it. */
fun snackbarActionOnNewLine(message: String, fontScale: Float): Boolean =
    message.length > SNACKBAR_INLINE_MAX_CHARS || fontScale >= SNACKBAR_NEW_LINE_FONT_SCALE

/** Failure snackbar action: "Try again" when the same input can succeed, "Details" otherwise. */
fun ReadyAnalysis.Failed.offersRetry(): Boolean = failure.retryableWithSameInput

/** A web reload ate the file: its own copy ("Analysis interrupted by a page reload"). */
fun ReadyAnalysis.Failed.isInterrupted(): Boolean = failure.reason == AiFailureReason.INTERRUPTED

const val SNACKBAR_NAME_MAX = 24
private const val SNACKBAR_INLINE_MAX_CHARS = 40
private const val SNACKBAR_NEW_LINE_FONT_SCALE = 1.3f
