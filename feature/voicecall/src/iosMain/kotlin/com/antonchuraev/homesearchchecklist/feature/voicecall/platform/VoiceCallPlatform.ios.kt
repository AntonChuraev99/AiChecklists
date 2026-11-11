package com.antonchuraev.homesearchchecklist.feature.voicecall.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.VoiceCallAction

/** iOS: no voice call yet. Every answer is an honest "no"; a permission request is denied at once (blocked). */
@Composable
actual fun rememberVoiceCallPlatform(
    onMicPermissionResult: (VoiceCallAction.MicPermissionResult) -> Unit,
): VoiceCallPlatform {
    val currentOnResult by rememberUpdatedState(onMicPermissionResult)
    return remember { UnsupportedVoiceCallPlatform { currentOnResult(it) } }
}
