package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger

/**
 * Lets exactly one recorder terminal callback through per mic press.
 *
 * The recorder can report twice for one press: a failed `start()` fires `onError`, and the finger
 * release that follows calls `stop()` / `cancel()` on the now-idle recorder, which fires
 * `onResult(null)`. Without the gate the user gets two snackbars and analytics gets two events.
 *
 * Lives in the gesture host (remembered next to the recorder launcher), NOT in the ViewModel:
 * the ViewModel must keep accepting a stop that had no preceding start.
 *
 * The gate stays open until a callback is delivered, however late it arrives — a press that
 * opened the permission dialog gets its single callback only after the dialog is answered.
 *
 * A fresh gate starts OPEN. The instance lives in `remember`, so an Activity recreation
 * (rotation, theme change) replaces it, while the ViewModel survives with `isRecording = true`.
 * The callback that follows — a stop tap on the dead recorder, or the answer of a permission
 * dialog that was on screen during the recreation — belongs to a press this instance never saw,
 * and it is the only thing that resets `isRecording`. It must pass once; starting closed left
 * the input disabled until process death.
 * Main-thread only, like the recorder callbacks themselves.
 */
class VoicePressTerminalGate {
    private var open = true

    /** Call on finger down, before the recorder's `start()`. */
    fun onPressStarted() {
        open = true
    }

    /** True for the first terminal callback of the current press, false for every later one. */
    fun tryDeliver(): Boolean {
        if (!open) return false
        open = false
        return true
    }
}

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
