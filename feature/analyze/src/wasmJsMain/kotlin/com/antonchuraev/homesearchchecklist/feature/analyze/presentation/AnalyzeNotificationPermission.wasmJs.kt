package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** No OS notification permission gates web delivery (it is in-app on return): answer `true` at once. */
@Composable
actual fun rememberAnalyzeNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val current = rememberUpdatedState(onResult)
    return remember { { current.value(true) } }
}
