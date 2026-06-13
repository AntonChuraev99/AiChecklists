package com.antonchuraev.homesearchchecklist.appupdate

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable

/**
 * Platform-specific side-effect composable that runs the native in-app update flow.
 *
 * - Android: checks Google Play for an update (IMMEDIATE preferred, FLEXIBLE fallback),
 *   resumes an interrupted update on every resume, and shows a "Restart" snackbar through
 *   [snackbarHostState] when a flexible update has finished downloading.
 * - iOS / Web (wasmJs): no-op — in-app updates are a Play Store feature.
 *
 * Mirrors the [com.antonchuraev.homesearchchecklist.csat.InAppReviewLauncher] pattern:
 * a UI-less composable hosted once at the App root.
 *
 * @param snackbarHostState The app-level snackbar host used to prompt the restart.
 */
@Composable
expect fun AppUpdateLauncher(snackbarHostState: SnackbarHostState)
