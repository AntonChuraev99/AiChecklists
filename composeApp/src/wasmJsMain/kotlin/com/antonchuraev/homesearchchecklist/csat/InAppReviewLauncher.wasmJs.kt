package com.antonchuraev.homesearchchecklist.csat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** In-app review is not available on the web target — no-op implementation. */
@Composable
actual fun InAppReviewLauncher(
    shouldLaunch: Boolean,
    onComplete: () -> Unit,
) {
    LaunchedEffect(shouldLaunch) {
        if (shouldLaunch) onComplete()
    }
}
