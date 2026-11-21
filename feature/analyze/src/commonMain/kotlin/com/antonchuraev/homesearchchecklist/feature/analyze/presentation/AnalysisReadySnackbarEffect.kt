package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.analyze_details
import aichecklists.core.designsystem.generated.resources.analyze_failed_snackbar
import aichecklists.core.designsystem.generated.resources.analyze_interrupted_snackbar
import aichecklists.core.designsystem.generated.resources.analyze_ready_fill_snackbar
import aichecklists.core.designsystem.generated.resources.analyze_ready_many_snackbar
import aichecklists.core.designsystem.generated.resources.analyze_ready_open
import aichecklists.core.designsystem.generated.resources.analyze_ready_review
import aichecklists.core.designsystem.generated.resources.analyze_ready_snackbar
import aichecklists.core.designsystem.generated.resources.analyze_ready_view
import aichecklists.core.designsystem.generated.resources.analyze_try_again
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobScheduler
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisReadySignal
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.DeliveryClaimant
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ReadyAnalysis
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject

/**
 * App-level delivery of background analyses (owner decision 5: snackbar "ready · Open", never an
 * automatic navigation). Hosted ONCE at the app root next to the app [SnackbarHostState], so it works
 * over every screen of both shells. Also re-launches unfinished jobs once at start
 * ([AnalysisJobScheduler.resumePending]: process death, web reload).
 *
 * One snackbar at a time from [AnalysisReadySignal.pending] (derived state — a signal missed while
 * nothing collected it is still there); two or more new checklists fold into one summary.
 */
@Composable
fun AnalysisReadySnackbarEffect(snackbarHostState: SnackbarHostState, navigator: AppNavigator) {
    val signal: AnalysisReadySignal = koinInject()
    val scheduler: AnalysisJobScheduler = koinInject()
    val fontScale by rememberUpdatedState(LocalDensity.current.fontScale)

    LaunchedEffect(scheduler) { scheduler.resumePending() }

    LaunchedEffect(signal) {
        while (true) {
            val pending = signal.pending.first { it.isNotEmpty() }
            val next = nextReadySnackbar(pending) ?: continue
            val visuals = next.toVisuals(fontScale)
            val result = snackbarHostState.showSnackbar(visuals)
            when (next) {
                is ReadySnackbar.Summary -> if (result == SnackbarResult.ActionPerformed) {
                    signal.onOpenSummary(next.jobIds)
                    navigator.navigateToMainScreen()
                } else {
                    next.jobIds.forEach { signal.onDismissed(it) }
                }
                is ReadySnackbar.Single -> if (result == SnackbarResult.ActionPerformed) {
                    // Null = someone else (the notification, a screen) already delivered it: do nothing.
                    signal.onOpen(next.item.jobId, DeliveryClaimant.IN_APP_HOST)
                        ?.let { navigator.navigateToReadyDestination(it) }
                } else {
                    signal.onDismissed(next.item.jobId)
                }
            }
        }
    }
}

/**
 * Root `SnackbarHost(snackbar = …)` renderer: puts the action on its own line for a ready-analysis
 * snackbar whose copy is long or whose text is scaled up; every other app snackbar renders as before.
 */
@Composable
fun AppRootSnackbar(data: SnackbarData) {
    Snackbar(
        snackbarData = data,
        actionOnNewLine = (data.visuals as? AnalysisReadySnackbarVisuals)?.actionOnNewLine ?: false,
    )
}

/** Visuals of a ready-analysis snackbar; [actionOnNewLine] is read by [AppRootSnackbar]. */
class AnalysisReadySnackbarVisuals(
    override val message: String,
    override val actionLabel: String,
    val actionOnNewLine: Boolean,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Long
}

private suspend fun ReadySnackbar.toVisuals(fontScale: Float): AnalysisReadySnackbarVisuals {
    val (message, action) = when (this) {
        is ReadySnackbar.Summary ->
            getPluralString(Res.plurals.analyze_ready_many_snackbar, count, count) to getString(Res.string.analyze_ready_view)
        is ReadySnackbar.Single -> when (val item = item) {
            is ReadyAnalysis.ChecklistCreated ->
                getString(Res.string.analyze_ready_snackbar, truncateForSnackbar(item.checklistName)) to
                    getString(Res.string.analyze_ready_open)
            is ReadyAnalysis.FillReady ->
                getString(Res.string.analyze_ready_fill_snackbar, truncateForSnackbar(item.checklistName)) to
                    getString(Res.string.analyze_ready_review)
            is ReadyAnalysis.Failed -> when {
                item.isInterrupted() ->
                    getString(Res.string.analyze_interrupted_snackbar) to getString(Res.string.analyze_details)
                item.offersRetry() ->
                    getString(Res.string.analyze_failed_snackbar) to getString(Res.string.analyze_try_again)
                else -> getString(Res.string.analyze_failed_snackbar) to getString(Res.string.analyze_details)
            }
        }
    }
    return AnalysisReadySnackbarVisuals(message, action, snackbarActionOnNewLine(message, fontScale))
}
