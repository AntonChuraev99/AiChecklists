package com.antonchuraev.homesearchchecklist.feature.onboarding

import androidx.compose.runtime.Composable

/**
 * Composable that remembers a launcher for requesting notification permission.
 *
 * @param onResult Callback invoked with true if permission was granted, false otherwise.
 * @return A lambda that, when called, triggers the system permission request.
 */
@Composable
expect fun rememberNotificationPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit
