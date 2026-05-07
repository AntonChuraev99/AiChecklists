package com.antonchuraev.homesearchchecklist.feature.onboarding

import androidx.compose.runtime.Composable

/**
 * Web stub for notification permission requester.
 * Returns a no-op launcher that reports permission as granted.
 */
@Composable
actual fun rememberNotificationPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit = { onResult(true) }
