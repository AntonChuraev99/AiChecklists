package com.antonchuraev.homesearchchecklist.feature.voicecall.platform

import androidx.compose.runtime.Composable
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.VoiceCallAction

/**
 * The system side of the call screen the host (Route) needs while the call is open: the microphone permission,
 * the system settings screens, and telling a real trip to the background from a configuration change.
 *
 * Effect mapping in the host:
 * - `VoiceCallEffect.RequestMicPermission` -> [requestMicPermission]; the answer arrives as
 *   [VoiceCallAction.MicPermissionResult] through the `onMicPermissionResult` callback of [rememberVoiceCallPlatform].
 * - `VoiceCallEffect.OpenAppSettings` -> [openAppSettings]; `VoiceCallEffect.OpenTtsSettings` -> [openTtsSettings].
 * - ON_START -> `VoiceCallAction.ScreenStarted(micGranted = hasMicPermission())`.
 * - ON_STOP -> `VoiceCallAction.ScreenStopped` only when `!isChangingConfigurations()` (a rotation keeps the call).
 *
 * Android only in practice: on web and iOS [isSupported] is `false` and every call answers "no" (never throws).
 */
interface VoiceCallPlatform {
    /** The platform runs the voice call; `false` on web and iOS. */
    val isSupported: Boolean

    /** RECORD_AUDIO is granted right now (a fresh probe: the user may have changed it in settings). */
    fun hasMicPermission(): Boolean

    /**
     * Shows the system RECORD_AUDIO dialog. Exactly one [VoiceCallAction.MicPermissionResult] follows through the
     * callback, also when the dialog is not shown (permission blocked: `granted = false, canAskAgain = false`; already
     * granted: `granted = true`). On web and iOS the answer is an immediate denial with `canAskAgain = false`.
     */
    fun requestMicPermission()

    /** Opens this app's system settings page (to unblock the permission); `false` when nothing could be opened. */
    fun openAppSettings(): Boolean

    /**
     * Opens the system text-to-speech settings (to install a voice), falling back to the general settings when the
     * device has no TTS settings screen; `false` when nothing could be opened.
     */
    fun openTtsSettings(): Boolean

    /**
     * The hosting activity is being destroyed for a configuration change (rotation, dark mode, locale). Read it in
     * ON_STOP: `true` = the screen comes right back, do not pause the call.
     */
    fun isChangingConfigurations(): Boolean
}

/**
 * The [VoiceCallPlatform] of the current composition. [onMicPermissionResult] receives the answer of every
 * [VoiceCallPlatform.requestMicPermission]; the latest lambda is used, so it may capture the ViewModel freely.
 */
@Composable
expect fun rememberVoiceCallPlatform(
    onMicPermissionResult: (VoiceCallAction.MicPermissionResult) -> Unit,
): VoiceCallPlatform

/**
 * [VoiceCallPlatform] of a platform without the call (web, iOS). A permission request is answered at once as a
 * blocked denial, so the ViewModel leaves the permission state instead of waiting for a dialog that never comes.
 */
internal class UnsupportedVoiceCallPlatform(
    private val onMicPermissionResult: (VoiceCallAction.MicPermissionResult) -> Unit,
) : VoiceCallPlatform {
    override val isSupported: Boolean get() = false

    override fun hasMicPermission(): Boolean = false

    override fun requestMicPermission() {
        onMicPermissionResult(VoiceCallAction.MicPermissionResult(granted = false, canAskAgain = false))
    }

    override fun openAppSettings(): Boolean = false

    override fun openTtsSettings(): Boolean = false

    override fun isChangingConfigurations(): Boolean = false
}
