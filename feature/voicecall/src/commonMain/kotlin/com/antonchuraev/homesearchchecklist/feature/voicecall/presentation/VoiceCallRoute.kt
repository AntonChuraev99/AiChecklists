package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.voice_call_settings_unavailable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.feature.voicecall.platform.VoiceCallPlatform
import com.antonchuraev.homesearchchecklist.feature.voicecall.platform.rememberVoiceCallPlatform
import org.jetbrains.compose.resources.stringResource

/**
 * The voice call overlay as the app hosts it: the ViewModel's state on [VoiceCallScreen], the system
 * side ([VoiceCallPlatform]: permission dialog, settings screens, configuration change) and the
 * navigation effects handed to the host.
 *
 * Composed by `App.kt` LAST in its root `Box`, outside Nav3, so the call covers every route. Nothing is
 * composed while the state is [VoiceCallUiState.Closed] except the effect collector, so closing the
 * overlay together with a navigation effect never loses the effect.
 *
 * @param onOpenChecklist open a checklist; `callDurationSec` non-null = the host also shows the
 *   "Call ended · m:ss" snackbar (the call was ended from a card).
 * @param onOpenPaywall the existing paywall route with the given analytics source.
 * @param onOpenChatSheet open the v2 chat sheet the way the Ask pill does.
 */
@Composable
fun VoiceCallRoute(
    viewModel: VoiceCallViewModel,
    onOpenChecklist: (checklistId: String, callDurationSec: Int?) -> Unit,
    onOpenPaywall: (source: String) -> Unit,
    onOpenChatSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val level = viewModel.level.collectAsStateWithLifecycle()
    val platform = rememberVoiceCallPlatform(onMicPermissionResult = viewModel::onAction)
    val snackbarHostState = remember { SnackbarHostState() }
    val settingsUnavailable = stringResource(Res.string.voice_call_settings_unavailable)
    val openChecklist by rememberUpdatedState(onOpenChecklist)
    val openPaywall by rememberUpdatedState(onOpenPaywall)
    val openChatSheet by rememberUpdatedState(onOpenChatSheet)

    LaunchedEffect(viewModel, platform) {
        viewModel.effects.collect { effect ->
            when (effect) {
                VoiceCallEffect.RequestMicPermission -> platform.requestMicPermission()
                // `false` = no settings screen could be opened: say so, never a silent tap.
                VoiceCallEffect.OpenAppSettings ->
                    if (!platform.openAppSettings()) snackbarHostState.showSnackbar(settingsUnavailable)
                VoiceCallEffect.OpenTtsSettings ->
                    if (!platform.openTtsSettings()) snackbarHostState.showSnackbar(settingsUnavailable)
                is VoiceCallEffect.OpenChecklist -> openChecklist(effect.checklistId, effect.callDurationSec)
                is VoiceCallEffect.OpenPaywall -> openPaywall(effect.source)
                VoiceCallEffect.OpenChatSheet -> openChatSheet()
            }
        }
    }

    if (uiState == VoiceCallUiState.Closed) return

    // A rotation keeps the call; a real trip to the background pauses it. ON_START also re-probes the
    // permission (the user may have turned it on in the app settings).
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.onAction(VoiceCallAction.ScreenStarted(micGranted = platform.hasMicPermission()))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!platform.isChangingConfigurations()) viewModel.onAction(VoiceCallAction.ScreenStopped)
    }

    Box(modifier.fillMaxSize()) {
        VoiceCallScreen(
            uiState = uiState,
            onAction = viewModel::onAction,
            micLevel = { level.value },
            voiceLevel = { level.value },
        )
        // The app's snackbar host is under the overlay; the overlay's own messages need their own.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding(),
        )
    }
}
