package com.antonchuraev.homesearchchecklist.feature.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.NotificationDialogOutcome
import com.antonchuraev.homesearchchecklist.core.common.api.NotificationPermissionLedger
import com.antonchuraev.homesearchchecklist.core.common.api.NotificationPermissionSource
import com.antonchuraev.homesearchchecklist.core.common.api.classifyNotificationDialogResult
import com.antonchuraev.homesearchchecklist.core.common.api.isPermanent
import com.antonchuraev.homesearchchecklist.core.common.api.notificationPermissionRequested
import com.antonchuraev.homesearchchecklist.core.common.api.notificationPermissionResult
import org.koin.compose.koinInject

private class OnboardingAskProbe {
    var rationaleBefore = false
    var pausedSinceLaunch = false
    var inFlight = false
}

/**
 * Onboarding's POST_NOTIFICATIONS ask. Same classification and the same persisted ledger as
 * feature:home's NotificationPermissionController (core:common:api), so a denial here is seen by the
 * banner's CTA; reports `notification_permission_requested/result` with source "onboarding".
 * A denial answered with no dialog at all opens app notification settings (visible response).
 */
@Composable
actual fun rememberNotificationPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val analytics: AnalyticsTracker = koinInject()
    val logger: AppLogger = koinInject()
    val currentOnResult = rememberUpdatedState(onResult)
    val probe = remember { OnboardingAskProbe() }

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
        analytics.notificationPermissionResult(
            source = NotificationPermissionSource.ONBOARDING,
            granted = granted,
            permanentlyDenied = outcome.isPermanent,
        )
        if (outcome == NotificationDialogOutcome.BlockedNoDialog) {
            NotificationPermissionLedger.openAppNotificationSettings(context)?.let { e ->
                logger.error(TAG, "open app notification settings failed: ${e.message}", e)
            }
        }
        currentOnResult.value(granted)
    }

    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            probe.rationaleBefore = rationale(context)
            probe.pausedSinceLaunch = false
            probe.inFlight = true
            analytics.notificationPermissionRequested(NotificationPermissionSource.ONBOARDING)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            currentOnResult.value(true)
        }
    }
}

private fun rationale(context: Context): Boolean =
    context.findActivity()?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
    } ?: false

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val TAG = "OnboardingNotifPermission"
