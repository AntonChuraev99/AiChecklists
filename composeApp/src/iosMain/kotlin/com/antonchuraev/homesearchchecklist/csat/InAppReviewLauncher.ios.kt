package com.antonchuraev.homesearchchecklist.csat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
actual fun InAppReviewLauncher(
    shouldLaunch: Boolean,
    onComplete: () -> Unit,
) {
    LaunchedEffect(shouldLaunch) {
        if (shouldLaunch) onComplete()
    }
}
