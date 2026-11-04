package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.VoiceCallPreviewFixtures as F

// One preview per FEATURE_SPEC row group; the full matrix is VoiceCallPreviewFixtures.all.

@Composable
private fun Frame(state: VoiceCallUiState, dark: Boolean = false) {
    AppTheme(darkTheme = dark) {
        VoiceCallScreen(uiState = state, onAction = {}, micLevel = { 0.6f }, voiceLevel = { 0.6f })
    }
}

@PreviewLightDark @Composable private fun VoiceCallConnectingPreview() = Frame(F.connecting)
@Preview(widthDp = 320, heightDp = 640) @Composable private fun VoiceCallListeningPreview() = Frame(F.listeningSilence)
@Preview(widthDp = 320, heightDp = 640, fontScale = 1.5f) @Composable private fun VoiceCallHearingPreview() = Frame(F.listeningHearing)
@Preview(widthDp = 320, heightDp = 640) @Composable private fun VoiceCallThinkingToolPreview() = Frame(F.thinkingTool)
@Preview(widthDp = 320, heightDp = 640, fontScale = 2f) @Composable private fun VoiceCallVerySlowPreview() = Frame(F.thinkingVerySlow)
@Preview(widthDp = 412, heightDp = 915) @Composable private fun VoiceCallSpeakingPreview() = Frame(F.speakingCaptions)
@Preview(widthDp = 320, heightDp = 640, fontScale = 1.5f) @Composable private fun VoiceCallSpeakingCardDarkPreview() = Frame(F.speakingCardCaptions, dark = true)
@Preview(widthDp = 360, heightDp = 640) @Composable private fun VoiceCallMutedPreview() = Frame(F.muted, dark = true)
@Preview(widthDp = 360, heightDp = 640, fontScale = 1.5f) @Composable private fun VoiceCallReconnectingPreview() = Frame(F.reconnecting)
@Preview(widthDp = 320, heightDp = 640) @Composable private fun VoiceCallPausedPreview() = Frame(F.pausedBackground)
@Preview(widthDp = 320, heightDp = 640, fontScale = 1.5f) @Composable private fun VoiceCallLimitWarningPreview() = Frame(F.limitWarningFree)
@Preview(widthDp = 320, heightDp = 640, fontScale = 2f) @Composable private fun VoiceCallPermissionPreview() = Frame(F.permRequest)
@Preview(widthDp = 320, heightDp = 640) @Composable private fun VoiceCallPermissionBlockedPreview() = Frame(F.permBlocked)
@Preview(widthDp = 360, heightDp = 640) @Composable private fun VoiceCallOfflinePreview() = Frame(F.errOffline, dark = true)
@Preview(widthDp = 360, heightDp = 640) @Composable private fun VoiceCallServerErrorPreview() = Frame(F.errServer)
@Preview(widthDp = 320, heightDp = 640, fontScale = 1.5f) @Composable private fun VoiceCallSummaryPreview() = Frame(F.summary)
@Preview(widthDp = 360, heightDp = 640) @Composable private fun VoiceCallSummaryNothingPreview() = Frame(F.summaryNothing)
@Preview(widthDp = 412, heightDp = 915) @Composable private fun VoiceCallLimitFreePreview() = Frame(F.limitFree, dark = true)
@Preview(widthDp = 412, heightDp = 915) @Composable private fun VoiceCallLimitDailyPreview() = Frame(F.limitDaily)
@Preview(widthDp = 800, heightDp = 360) @Composable private fun VoiceCallLandscapePreview() = Frame(F.speakingCardCaptions)
