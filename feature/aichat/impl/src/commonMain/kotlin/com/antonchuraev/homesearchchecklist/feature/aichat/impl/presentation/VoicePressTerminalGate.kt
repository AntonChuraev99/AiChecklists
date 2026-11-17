package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger

// VoicePressTerminalGate and endVoicePress moved to core:filepicker:api (recorder package) so the
// onboarding composer reuses the same press contract; the chat-specific mapping stays here.

/**
 * Maps a recorder `onError` string to its [VoiceCancelReason] and logs it. The recorder module has
 * no logger, so this is the only place a recorder failure reaches Crashlytics.
 */
fun logVoiceRecorderError(logger: AppLogger, errorCode: String): VoiceCancelReason {
    val reason = voiceCancelReasonFor(errorCode)
    if (reason == VoiceCancelReason.RECORDER_ERROR) {
        logger.error(VOICE_LOG_TAG, "Recorder failed: $errorCode", IllegalStateException(errorCode))
    } else {
        logger.warning(VOICE_LOG_TAG, "Recording ended without audio: $errorCode")
    }
    return reason
}

private const val VOICE_LOG_TAG = "ChatVoice"
