package com.antonchuraev.homesearchchecklist.csat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow

/**
 * Stub — iOS is not released yet, so no SKStoreReviewController call is wired up.
 *
 * The request completes as [ReviewLaunchOutcome.Unsupported]: the CSAT flow must still close and
 * thank the user, but no review was ever launched and analytics must not count one.
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
