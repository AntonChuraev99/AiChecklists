package com.antonchuraev.homesearchchecklist.appupdate

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable

/** In-app updates are a Play Store feature — no-op on the web target. */
@Composable
actual fun AppUpdateLauncher(snackbarHostState: SnackbarHostState) {
    // No-op: the web app is always served fresh; there is no Play in-app update.
}
