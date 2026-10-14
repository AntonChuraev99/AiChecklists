package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.NotificationDialogOutcome
import com.antonchuraev.homesearchchecklist.core.common.api.NotificationPermissionLedger
import com.antonchuraev.homesearchchecklist.core.common.api.classifyNotificationDialogResult
import com.antonchuraev.homesearchchecklist.core.common.api.isPermanent
import com.antonchuraev.homesearchchecklist.core.common.api.isPermanentDenialStillValid
import org.koin.compose.koinInject

@Composable
actual fun rememberNotificationPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onResult(granted)
    }

    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onResult(true)
        }
    }
}

/** Mutable per-launch bookkeeping: rationale before the ask and whether the host paused since. */
private class AskProbe {
    var rationaleBefore = false
    var pausedSinceLaunch = false
    var inFlight = false
}

@Composable
actual fun rememberNotificationPermissionController(
    onResult: (NotificationPermissionResult) -> Unit,
): NotificationPermissionController {
    val context = LocalContext.current
    val logger: AppLogger = koinInject()
    val currentOnResult = rememberUpdatedState(onResult)
    val probe = remember { AskProbe() }

    // A system permission dialog is a separate activity: the host PAUSES while it is up. No pause
    // between launch and answer = the OS answered without any UI (permanent deny).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && probe.inFlight) probe.pausedSinceLaunch = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        probe.inFlight = false
        val outcome = classifyNotificationDialogResult(
            granted = granted,
            dialogShown = probe.pausedSinceLaunch,
            rationaleBefore = probe.rationaleBefore,
            rationaleAfter = rationale(context),
        )
        NotificationPermissionLedger.record(context, outcome)
        // Blocked with no dialog: the tap must still DO something visible — open the app's
        // notification settings right away, the only place this can be fixed.
        val openedSettings = outcome == NotificationDialogOutcome.BlockedNoDialog
        if (openedSettings) openAppNotificationSettings(context, logger)
        currentOnResult.value(
            NotificationPermissionResult(
                granted = granted,
                permanentlyDenied = outcome.isPermanent,
                openedSettings = openedSettings,
            ),
        )
    }
    return remember(context, launcher) {
        val next: () -> NotificationPermissionAction = { decide(context) }
        NotificationPermissionController(
            nextAction = next,
            request = {
                val action = next()
                when (action) {
                    NotificationPermissionAction.AlreadyEnabled ->
                        currentOnResult.value(NotificationPermissionResult(granted = true, permanentlyDenied = false))
                    NotificationPermissionAction.SystemDialog -> {
                        probe.rationaleBefore = rationale(context)
                        probe.pausedSinceLaunch = false
                        probe.inFlight = true
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    NotificationPermissionAction.Settings -> openAppNotificationSettings(context, logger)
                }
                action
            },
        )
    }
}

private fun rationale(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        (context.findActivity()?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
        } ?: false)

private fun decide(context: Context): NotificationPermissionAction {
    val runtimeApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val granted = runtimeApi && ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    return resolveNotificationPermissionAction(
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        runtimePermissionApi = runtimeApi,
        permissionGranted = granted,
        permanentlyDeniedValid = isPermanentDenialStillValid(
            storedFlag = NotificationPermissionLedger.isPermanentlyDenied(context),
            permissionGranted = granted,
            rationaleNow = rationale(context),
        ),
    )
}

private fun openAppNotificationSettings(context: Context, logger: AppLogger) {
    NotificationPermissionLedger.openAppNotificationSettings(context)?.let { e ->
        logger.error(TAG, "open app notification settings failed: ${e.message}", e)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val TAG = "NotificationPermission"
