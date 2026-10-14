package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.compose.runtime.Composable

@Composable
actual fun rememberNotificationPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit = { onResult(true) }

/** No OS push permission on this platform: the ask always resolves as enabled. */
@Composable
actual fun rememberNotificationPermissionController(
    onResult: (NotificationPermissionResult) -> Unit,
): NotificationPermissionController = NotificationPermissionController(
    nextAction = { NotificationPermissionAction.AlreadyEnabled },
    request = {
        onResult(NotificationPermissionResult(granted = true, permanentlyDenied = false))
        NotificationPermissionAction.AlreadyEnabled
    },
)
