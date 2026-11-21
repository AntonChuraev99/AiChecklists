package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** iOS posts no analysis notification yet (no-op notifier), so there is nothing to ask: `true`. */
@Composable
actual fun rememberAnalyzeNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val current = rememberUpdatedState(onResult)
    return remember { { current.value(true) } }
}
