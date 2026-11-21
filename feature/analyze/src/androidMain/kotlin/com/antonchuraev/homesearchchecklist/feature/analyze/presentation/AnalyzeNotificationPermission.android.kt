package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

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
import com.antonchuraev.homesearchchecklist.core.common.api.NotificationPermissionLedger
import com.antonchuraev.homesearchchecklist.core.common.api.classifyNotificationDialogResult
import com.antonchuraev.homesearchchecklist.core.common.api.isPermanentDenialStillValid

/**
 * Android ask: the POST_NOTIFICATIONS dialog only when the OS can still show it; every other state
 * answers at once and never opens settings. Shares the permanent-deny bookkeeping with the reminder
 * and onboarding asks (`NotificationPermissionLedger` + `classifyNotificationDialogResult` in
 * core:common:api), so a denial recorded here is honoured there and vice versa.
 */
@Composable
actual fun rememberAnalyzeNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val current = rememberUpdatedState(onResult)
    val probe = remember { AskProbe() }

    // The system dialog is its own Activity: the host pauses while it is up. No pause between launch
    // and answer = the OS answered without UI (permanent deny).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && probe.inFlight) probe.pausedSinceLaunch = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        probe.inFlight = false
        val outcome = classifyNotificationDialogResult(
            granted = granted,
            dialogShown = probe.pausedSinceLaunch,
            rationaleBefore = probe.rationaleBefore,
            rationaleAfter = rationale(context),
        )
        NotificationPermissionLedger.record(context, outcome)
        current.value(granted)
    }

    return remember(context, launcher) {
        {
            when {
                NotificationManagerCompat.from(context).areNotificationsEnabled() -> current.value(true)
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> current.value(false)
                !canShowDialog(context) -> current.value(false)
                probe.inFlight -> Unit // a dialog is already up; its answer reports
                else -> {
                    probe.rationaleBefore = rationale(context)
                    probe.pausedSinceLaunch = false
                    probe.inFlight = true
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}

private class AskProbe {
    var rationaleBefore = false
    var pausedSinceLaunch = false
    var inFlight = false
}

/**
 * API 33+, notifications off: the dialog can appear unless the permission is already granted (the
 * block is then the app/channel toggle — settings only) or a permanent denial is still valid.
 */
private fun canShowDialog(context: Context): Boolean {
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) return false
    return !isPermanentDenialStillValid(
        storedFlag = NotificationPermissionLedger.isPermanentlyDenied(context),
        permissionGranted = false,
        rationaleNow = rationale(context),
    )
}

private fun rationale(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        (context.findActivity()?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
        } ?: false)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
