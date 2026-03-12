package com.antonchuraev.homesearchchecklist.feature.onboarding

import androidx.compose.runtime.Composable

@Composable
actual fun rememberNotificationPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit = { onResult(true) }
