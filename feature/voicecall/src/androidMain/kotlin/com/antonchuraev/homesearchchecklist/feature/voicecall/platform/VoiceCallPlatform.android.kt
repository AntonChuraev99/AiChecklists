package com.antonchuraev.homesearchchecklist.feature.voicecall.platform

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.VoiceCallAction
import org.koin.compose.koinInject

private const val TAG = "VoiceCallPlatform"

/** System TTS settings; not a public `Settings` constant, but the action every AOSP-based Settings app exports. */
private const val ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"

@Composable
actual fun rememberVoiceCallPlatform(
    onMicPermissionResult: (VoiceCallAction.MicPermissionResult) -> Unit,
): VoiceCallPlatform {
    val context = LocalContext.current
    val logger: AppLogger = koinInject()
    val currentOnResult by rememberUpdatedState(onMicPermissionResult)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Read AFTER the answer: false after a denial = "don't ask again" / blocked by policy, the dialog will not
        // show again and only the app settings can unblock it. No activity = cannot tell: the safe side is the
        // settings path, which always works.
        val activity = context.findActivity()
        if (activity == null && !granted) {
            logger.warning(TAG, "no Activity for the rationale probe: treating the denial as blocked")
        }
        val canAskAgain = granted ||
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true
        currentOnResult(VoiceCallAction.MicPermissionResult(granted = granted, canAskAgain = canAskAgain))
    }

    return remember(context, launcher, logger) {
        AndroidVoiceCallPlatform(context, logger, launch = { launcher.launch(it) })
    }
}

private class AndroidVoiceCallPlatform(
    private val context: Context,
    private val logger: AppLogger,
    private val launch: (String) -> Unit,
) : VoiceCallPlatform {

    override val isSupported: Boolean get() = true

    override fun hasMicPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun requestMicPermission() {
        // A blocked permission answers at once with granted=false and no dialog: the callback still fires.
        launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun openAppSettings(): Boolean = start(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    ) || start(Intent(Settings.ACTION_SETTINGS))

    override fun openTtsSettings(): Boolean = start(Intent(ACTION_TTS_SETTINGS)) || start(Intent(Settings.ACTION_SETTINGS))

    override fun isChangingConfigurations(): Boolean = context.findActivity()?.isChangingConfigurations == true

    /** Starts [intent]; `false` when no activity handles it (a stripped-down Settings app, a work profile policy). */
    private fun start(intent: Intent): Boolean {
        if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }
            .onFailure { error ->
                if (error is ActivityNotFoundException || error is SecurityException) {
                    // A fallback, not a defect: the caller tries the next intent, and a final `false` is reported
                    // to the user by the host. An error here would flood Crashlytics on every OEM without TTS_SETTINGS.
                    logger.warning(TAG, "cannot open ${intent.action}: ${error.message}")
                } else {
                    throw error
                }
            }
            .isSuccess
    }
}

/** The Activity behind a Compose `LocalContext`, which may be wrapped (themes, locale wrappers). */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
