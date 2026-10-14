package com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder

/**
 * Press lifecycle of a recorder whose `start()` is asynchronous and may sit behind a permission
 * prompt — the browser's `getUserMedia`. Pure state, no platform calls: the wasmJs launcher asks
 * it what to do and performs the side effects itself.
 *
 * Same contract as the Android recorder (`AudioRecorder.android.kt`):
 * - `start()` is a no-op while a start is pending or a recording runs;
 * - `stop()` / `cancel()` while the start is pending abandon it without a callback — the press
 *   gets exactly one terminal callback, when the start settles;
 * - a pending start that was NOT abandoned turns into a recording as soon as it settles
 *   (tap-to-record callers wait behind the prompt and expect the auto-start);
 * - an abandoned start that went through a permission prompt reports
 *   [AudioRecorderErrorCodes.PERMISSION_GRANTED_PRESS_AGAIN] and records nothing;
 * - an abandoned start with no prompt was a press too short to capture anything.
 *
 * Main-thread only, like the launcher that owns it.
 */
internal class AsyncStartRecorderMachine {

    enum class Phase { IDLE, STARTING, RECORDING }

    private enum class Abandon { NONE, BY_STOP, BY_CANCEL }

    var phase: Phase = Phase.IDLE
        private set

    private var abandon = Abandon.NONE

    /** True → the caller launches the platform start; false → deliberate no-op, no callback. */
    fun onStart(): Boolean {
        if (phase != Phase.IDLE) return false
        phase = Phase.STARTING
        abandon = Abandon.NONE
        return true
    }

    fun onStop(): StopAction = when (phase) {
        Phase.IDLE -> StopAction.ReportNothingRecorded
        Phase.STARTING -> {
            // A cancel that came first keeps its meaning
            if (abandon == Abandon.NONE) abandon = Abandon.BY_STOP
            StopAction.DeferUntilStartSettles
        }
        Phase.RECORDING -> {
            phase = Phase.IDLE
            StopAction.StopRecording
        }
    }

    fun onCancel(): CancelAction = when (phase) {
        Phase.IDLE -> CancelAction.ReportNothingRecorded
        Phase.STARTING -> {
            abandon = Abandon.BY_CANCEL
            CancelAction.DeferUntilStartSettles
        }
        Phase.RECORDING -> {
            phase = Phase.IDLE
            CancelAction.DiscardRecording
        }
    }

    /** The platform start finished. Only meaningful in [Phase.STARTING]; ignored otherwise. */
    fun onStartSettled(outcome: StartOutcome): SettleAction {
        if (phase != Phase.STARTING) return SettleAction.Ignore
        val abandonedBy = abandon
        abandon = Abandon.NONE
        return when (outcome) {
            is StartOutcome.Started -> when {
                abandonedBy == Abandon.NONE -> {
                    phase = Phase.RECORDING
                    SettleAction.KeepRecording
                }
                outcome.promptShown -> {
                    phase = Phase.IDLE
                    SettleAction.Release(errorCode = AudioRecorderErrorCodes.PERMISSION_GRANTED_PRESS_AGAIN)
                }
                abandonedBy == Abandon.BY_STOP -> {
                    phase = Phase.IDLE
                    SettleAction.Release(errorCode = AudioRecorderErrorCodes.RECORDING_TOO_SHORT)
                }
                else -> {
                    phase = Phase.IDLE
                    SettleAction.Release(errorCode = null)
                }
            }
            is StartOutcome.Denied -> {
                phase = Phase.IDLE
                SettleAction.Fail(
                    if (outcome.permanently) {
                        AudioRecorderErrorCodes.PERMISSION_DENIED_PERMANENTLY
                    } else {
                        AudioRecorderErrorCodes.PERMISSION_DENIED
                    }
                )
            }
            is StartOutcome.Failed -> {
                phase = Phase.IDLE
                SettleAction.Fail(outcome.message)
            }
        }
    }

    /** The owner left composition: whatever runs is released, nothing is reported. */
    fun onDispose() {
        phase = Phase.IDLE
        abandon = Abandon.NONE
    }

    sealed interface StartOutcome {
        /** Microphone acquired and recording begun. */
        data class Started(val promptShown: Boolean) : StartOutcome

        data class Denied(val permanently: Boolean) : StartOutcome

        /** Any other failure; [message] is a log string (type + message), never shown to the user. */
        data class Failed(val message: String) : StartOutcome
    }

    enum class StopAction { ReportNothingRecorded, DeferUntilStartSettles, StopRecording }

    enum class CancelAction { ReportNothingRecorded, DeferUntilStartSettles, DiscardRecording }

    sealed interface SettleAction {
        data object Ignore : SettleAction

        data object KeepRecording : SettleAction

        /** Discard the just-started recording; report [errorCode], or `onResult(null)` when it is null. */
        data class Release(val errorCode: String?) : SettleAction

        /** Nothing was acquired; report [errorCode] through `onError`. */
        data class Fail(val errorCode: String) : SettleAction
    }

    companion object {
        /**
         * Without the Permissions API a start that took this long is taken as answered by a human:
         * an already granted microphone opens far faster than anyone can read and click a prompt.
         */
        const val PROMPT_INFERENCE_MS = 500

        /**
         * Whether the browser showed a permission prompt during the start.
         * [permissionBefore] is the `navigator.permissions` state of `microphone` read right before
         * `getUserMedia` (`granted` / `prompt` / `denied`), or `unknown` where the API is missing.
         */
        fun promptWasShown(permissionBefore: String, elapsedMs: Int): Boolean = when (permissionBefore) {
            "prompt" -> true
            "granted", "denied" -> false
            else -> elapsedMs >= PROMPT_INFERENCE_MS
        }

        /**
         * Classifies a failed browser start. `NotAllowedError` is the user's (or the browser's)
         * refusal: permanent when the permission now reads `denied` — the browser will not ask
         * again and only site settings can lift it — retryable otherwise (prompt dismissed).
         */
        fun failureOutcome(errorName: String, message: String, permissionAfter: String): StartOutcome =
            if (errorName == "NotAllowedError" || errorName == "PermissionDeniedError") {
                StartOutcome.Denied(permanently = permissionAfter == "denied")
            } else {
                StartOutcome.Failed(
                    listOf(errorName, message).filter { it.isNotEmpty() }.joinToString(": ")
                        .ifEmpty { "start failed with no error" }
                )
            }
    }
}
