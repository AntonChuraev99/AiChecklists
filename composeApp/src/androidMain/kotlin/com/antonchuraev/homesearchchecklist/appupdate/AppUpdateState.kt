package com.antonchuraev.homesearchchecklist.appupdate

import androidx.compose.runtime.Immutable

/**
 * UI-facing state of the Google Play in-app update flow.
 *
 * Consumed by [AppUpdateLauncher] (androidMain actual) to drive the
 * "Update downloaded — Restart" snackbar. The whole feature is Android-only,
 * so this state lives in androidMain; other targets ship a no-op launcher and
 * never observe it.
 */
@Immutable
sealed interface AppUpdateState {
    /** No update activity in progress. */
    data object Idle : AppUpdateState

    /** Querying Play for an available update. */
    data object Checking : AppUpdateState

    /** An update is available and an update flow is about to start. */
    data object UpdateAvailable : AppUpdateState

    /** Immediate flow running, or flexible download in progress. */
    data object UpdateInProgress : AppUpdateState

    /** Flexible update finished downloading — ready to install on restart. */
    data object UpdateDownloaded : AppUpdateState

    /** Play reported no update (or no allowed update type) for this device. */
    data object UpdateNotAvailable : AppUpdateState

    /** The update flow failed; [exception] carries the cause. */
    data class Error(val exception: Throwable) : AppUpdateState
}
