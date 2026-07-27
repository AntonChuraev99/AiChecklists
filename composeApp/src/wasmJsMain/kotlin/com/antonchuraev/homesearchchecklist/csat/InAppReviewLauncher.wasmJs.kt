package com.antonchuraev.homesearchchecklist.csat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow

/**
 * In-app review is not available on the web target — no-op implementation.
 *
 * The request is still completed so the CSAT flow closes and the user gets the thanks snackbar;
 * reporting anything but [ReviewLaunchOutcome.Unsupported] would fabricate review launches on a
 * platform that has no review API.
 */
@Composable
actual fun InAppReviewLauncher(
    requests: Flow<Unit>,
    onComplete: (ReviewLaunchOutcome) -> Unit,
) {
    val currentOnComplete by rememberUpdatedState(onComplete)
    LaunchedEffect(requests) {
        requests.collect { currentOnComplete(ReviewLaunchOutcome.Unsupported) }
    }
}
