package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.AudioRecorderErrorCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoicePressTerminalGateTest {

    // ── voiceCancelReasonFor ─────────────────────────────────────────────────

    @Test
    fun knownRecorderCodes_mapToTheirReasons() {
        assertEquals(
            VoiceCancelReason.PERMISSION_DENIED,
            voiceCancelReasonFor(AudioRecorderErrorCodes.PERMISSION_DENIED),
        )
        assertEquals(
            VoiceCancelReason.PERMISSION_DENIED_PERMANENTLY,
            voiceCancelReasonFor(AudioRecorderErrorCodes.PERMISSION_DENIED_PERMANENTLY),
        )
        assertEquals(
            VoiceCancelReason.PERMISSION_JUST_GRANTED,
            voiceCancelReasonFor(AudioRecorderErrorCodes.PERMISSION_GRANTED_PRESS_AGAIN),
        )
        assertEquals(
            VoiceCancelReason.TOO_SHORT,
            voiceCancelReasonFor(AudioRecorderErrorCodes.RECORDING_TOO_SHORT),
        )
    }

    @Test
    fun unknownString_isRecorderError() {
        assertEquals(VoiceCancelReason.RECORDER_ERROR, voiceCancelReasonFor("MediaRecorder start failed: -38"))
        assertEquals(VoiceCancelReason.RECORDER_ERROR, voiceCancelReasonFor(""))
    }

    @Test
    fun analyticsValues_areDistinct() {
        val values = VoiceCancelReason.entries.map { it.analyticsValue }
        assertEquals(values.size, values.toSet().size)
    }

    // ── VoicePressTerminalGate ───────────────────────────────────────────────

    @Test
    fun firstCallbackOfAPress_isDelivered_secondIsBlocked() {
        val gate = VoicePressTerminalGate()
        gate.onPressStarted()

        assertTrue(gate.tryDeliver(), "onError of a failed start")
        assertFalse(gate.tryDeliver(), "onResult(null) from the release that follows")
        assertFalse(gate.tryDeliver())
    }

    @Test
    fun newPress_opensTheGateAgain() {
        val gate = VoicePressTerminalGate()
        gate.onPressStarted()
        assertTrue(gate.tryDeliver())

        gate.onPressStarted()

        assertTrue(gate.tryDeliver())
        assertFalse(gate.tryDeliver())
    }

    // A gate recreated with the Activity never saw the press whose callback is still pending
    // (the ViewModel kept isRecording = true). That callback must pass once to reset the state.
    @Test
    fun freshGate_deliversExactlyOneCallbackWithoutAPress() {
        val gate = VoicePressTerminalGate()

        assertTrue(gate.tryDeliver(), "callback of a press from before the recreation")
        assertFalse(gate.tryDeliver())
    }

    @Test
    fun freshGate_thenPress_deliversOnce_secondIsBlocked() {
        val gate = VoicePressTerminalGate()

        gate.onPressStarted()

        assertTrue(gate.tryDeliver())
        assertFalse(gate.tryDeliver())
    }

    // Permission flow: press → system dialog → the gesture is cancelled and the recorder sends NO
    // callback for that cancel → the dialog answer arrives later as onError. The gate was never
    // consumed, so that late callback must go through exactly once.
    @Test
    fun lateCallbackAfterCancelWithoutCallback_isDeliveredOnce() {
        val gate = VoicePressTerminalGate()
        gate.onPressStarted()
        // recorder.cancel() during the dialog: no callback, nothing touches the gate.

        assertTrue(gate.tryDeliver(), "onError(PERMISSION_GRANTED_PRESS_AGAIN) after the dialog")
        assertFalse(gate.tryDeliver())
    }
}
