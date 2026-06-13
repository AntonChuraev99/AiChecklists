package com.antonchuraev.homesearchchecklist.appupdate

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable

/** In-app updates are a Play Store feature — no-op on iOS. */
@Composable
actual fun AppUpdateLauncher(snackbarHostState: SnackbarHostState) {
    // No-op: iOS apps update through the App Store, not via an in-app API.
}
