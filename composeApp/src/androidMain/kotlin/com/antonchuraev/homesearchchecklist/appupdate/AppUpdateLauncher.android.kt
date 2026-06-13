package com.antonchuraev.homesearchchecklist.appupdate

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.app_update_downloaded_message
import aichecklists.core.designsystem.generated.resources.app_update_downloaded_restart
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Android in-app update launcher.
 *
 * On first composition it runs a full update check; on every subsequent resume it re-checks
 * for an interrupted/downloaded update. When a flexible update finishes downloading it shows
 * the shared "Update downloaded — Restart" snackbar and completes the update on confirmation.
 */
@Composable
actual fun AppUpdateLauncher(snackbarHostState: SnackbarHostState) {
    // No ComponentActivity (e.g. @Preview) → nothing to attach the update flow to.
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val controller: AppUpdateController = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // StartIntentSenderForResult is the non-deprecated way to launch the update flow:
    // the result code is forwarded to the controller (RESULT_OK / CANCELED / failure).
    val updateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        controller.handleUpdateResult(result.resultCode)
    }

    // Cold start: full check (IMMEDIATE preferred, FLEXIBLE fallback).
    LaunchedEffect(activity) {
        controller.checkAndStartUpdate(updateLauncher)
    }

    // Resume check: re-surface a downloaded flexible update / resume an interrupted immediate one.
    // The first ON_RESUME coincides with the cold-start check above, so it is skipped to avoid
    // restarting the immediate flow twice.
    DisposableEffect(lifecycleOwner) {
        var skipFirstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (skipFirstResume) {
                    skipFirstResume = false
                } else {
                    scope.launch {
                        controller.checkAndResumeUpdate(updateLauncher)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Flexible update finished downloading → prompt restart via the app-level snackbar.
    val updateState by controller.updateState.collectAsStateWithLifecycle()
    val restartMessage = stringResource(Res.string.app_update_downloaded_message)
    val restartAction = stringResource(Res.string.app_update_downloaded_restart)
    LaunchedEffect(updateState) {
        if (updateState is AppUpdateState.UpdateDownloaded) {
            val result = snackbarHostState.showSnackbar(
                message = restartMessage,
                actionLabel = restartAction,
                duration = SnackbarDuration.Indefinite,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                controller.completeFlexibleUpdate()
            }
        }
    }
}
