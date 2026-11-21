package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ReadyDestination

/**
 * The ONE mapping of a won delivery ([ReadyDestination], returned by `AnalysisReadySignal.onOpen`) onto
 * [AppNavigator]. Shared by every delivery surface outside the Analyze screen — the app snackbar host
 * (App.kt) and the Android notification tap (MainActivity) — so the two cannot drift apart.
 *
 * Call it only with a destination that `onOpen` returned (i.e. after a won claim); never navigate on
 * a null.
 */
fun AppNavigator.navigateToReadyDestination(destination: ReadyDestination) {
    when (destination) {
        // Plain push, never clearBackStack: the user is somewhere else in the app.
        is ReadyDestination.ChecklistDetail -> navigateToChecklistDetail(destination.checklistId)
        // onOpen already rebuilt AnalyzeResultHolder from the persisted FillReady outcome.
        ReadyDestination.FillPreview -> navigateToAnalyzeResultPreview()
        // Back to Analyze ATTACHED to the failed job (AnalysisJobHandoff): reason, buttons and the
        // captured input are restored by the screen; "Try again" reuses the captured payload.
        is ReadyDestination.AnalyzeRetry -> {
            val fillChecklistId = destination.fillChecklistId
            if (fillChecklistId != null) {
                navigateToAnalyzeScreen(checklistId = fillChecklistId, fillDefault = destination.fillDefault)
            } else {
                navigateToAnalyzeScreen()
            }
        }
    }
}
