package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.compose.runtime.Composable

/**
 * Web stub for notification permission requester.
 * Web push notifications require a separate browser permission flow (not implemented here).
 * Returns a no-op launcher that reports permission as granted to avoid blocking UI.
 */
@Composable
actual fun rememberNotificationPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit = { onResult(true) }
