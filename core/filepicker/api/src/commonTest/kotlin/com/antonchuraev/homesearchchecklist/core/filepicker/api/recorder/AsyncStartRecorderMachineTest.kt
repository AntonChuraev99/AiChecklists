package com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder

import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.AsyncStartRecorderMachine.CancelAction
import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.AsyncStartRecorderMachine.Phase
import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.AsyncStartRecorderMachine.SettleAction
import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.AsyncStartRecorderMachine.StartOutcome
import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.AsyncStartRecorderMachine.StopAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AsyncStartRecorderMachineTest {

    private fun starting() = AsyncStartRecorderMachine().also { assertTrue(it.onStart()) }

    private fun recording() = starting().also {
        assertEquals(SettleAction.KeepRecording, it.onStartSettled(StartOutcome.Started(promptShown = false)))
    }

    // ── start ────────────────────────────────────────────────────────────────

    @Test
    fun start_fromIdle_launchesPlatformStart() {
        val machine = AsyncStartRecorderMachine()
        assertTrue(machine.onStart())
        assertEquals(Phase.STARTING, machine.phase)
    }

    @Test
    fun start_whileStarting_isNoOp() {
        val machine = starting()
        assertFalse(machine.onStart())
        assertEquals(Phase.STARTING, machine.phase)
    }

    @Test
    fun start_whileRecording_isNoOp() {
        val machine = recording()
        assertFalse(machine.onStart())
        assertEquals(Phase.RECORDING, machine.phase)
    }

    @Test
    fun start_whileAbandonedPromptIsUp_isNoOpAndKeepsAbandon() {
        val machine = starting()
        machine.onStop()
        assertFalse(machine.onStart())
        assertEquals(
            SettleAction.Release(AudioRecorderErrorCodes.PERMISSION_GRANTED_PRESS_AGAIN),
            machine.onStartSettled(StartOutcome.Started(promptShown = true)),
        )
    }

    // ── stop / cancel during the pending start: no callback now ─────────────

    @Test
    fun stop_whileStarting_defersWithoutCallback() {
        val machine = starting()
        assertEquals(StopAction.DeferUntilStartSettles, machine.onStop())
        assertEquals(Phase.STARTING, machine.phase)
    }

    @Test
    fun cancel_whileStarting_defersWithoutCallback() {
        val machine = starting()
        assertEquals(CancelAction.DeferUntilStartSettles, machine.onCancel())
        assertEquals(Phase.STARTING, machine.phase)
    }

    // ── settle after an abandoned press ─────────────────────────────────────

    @Test
    fun stopDuringPrompt_thenGrant_reportsPressAgainAndRecordsNothing() {
        val machine = starting()
        machine.onStop()
        assertEquals(
            SettleAction.Release(AudioRecorderErrorCodes.PERMISSION_GRANTED_PRESS_AGAIN),
            machine.onStartSettled(StartOutcome.Started(promptShown = true)),
        )
        assertEquals(Phase.IDLE, machine.phase)
    }

    @Test
    fun cancelDuringPrompt_thenGrant_reportsPressAgain() {
        val machine = starting()
        machine.onCancel()
        assertEquals(
            SettleAction.Release(AudioRecorderErrorCodes.PERMISSION_GRANTED_PRESS_AGAIN),
            machine.onStartSettled(StartOutcome.Started(promptShown = true)),
        )
        assertEquals(Phase.IDLE, machine.phase)
    }

    @Test
    fun stopBeforeStartWithoutPrompt_reportsTooShort() {
        val machine = starting()
        machine.onStop()
        assertEquals(
            SettleAction.Release(AudioRecorderErrorCodes.RECORDING_TOO_SHORT),
            machine.onStartSettled(StartOutcome.Started(promptShown = false)),
        )
        assertEquals(Phase.IDLE, machine.phase)
    }

    @Test
    fun cancelBeforeStartWithoutPrompt_reportsNullResult() {
        val machine = starting()
        machine.onCancel()
        assertEquals(
            SettleAction.Release(errorCode = null),
            machine.onStartSettled(StartOutcome.Started(promptShown = false)),
        )
        assertEquals(Phase.IDLE, machine.phase)
    }

    @Test
    fun cancelThenStop_whileStarting_keepsCancelMeaning() {
        val machine = starting()
        machine.onCancel()
        machine.onStop()
        assertEquals(
            SettleAction.Release(errorCode = null),
            machine.onStartSettled(StartOutcome.Started(promptShown = false)),
        )
    }

    @Test
    fun abandonedPress_thenDenied_reportsDeniedCode() {
        val machine = starting()
        machine.onStop()
        assertEquals(
            SettleAction.Fail(AudioRecorderErrorCodes.PERMISSION_DENIED_PERMANENTLY),
            machine.onStartSettled(StartOutcome.Denied(permanently = true)),
        )
        assertEquals(Phase.IDLE, machine.phase)
    }

    // ── settle while the press is still held (tap-to-record auto-start) ─────

    @Test
    fun grantWithoutRelease_autoStartsRecording() {
        val machine = starting()
        assertEquals(SettleAction.KeepRecording, machine.onStartSettled(StartOutcome.Started(promptShown = true)))
        assertEquals(Phase.RECORDING, machine.phase)
        assertEquals(StopAction.StopRecording, machine.onStop())
        assertEquals(Phase.IDLE, machine.phase)
    }

    @Test
    fun deniedWithoutRelease_reportsRetryableDenied() {
        val machine = starting()
        assertEquals(
            SettleAction.Fail(AudioRecorderErrorCodes.PERMISSION_DENIED),
            machine.onStartSettled(StartOutcome.Denied(permanently = false)),
        )
        assertEquals(Phase.IDLE, machine.phase)
    }

    @Test
    fun failedStart_reportsRawMessage() {
        val machine = starting()
        assertEquals(
            SettleAction.Fail("NotFoundError: Requested device not found"),
            machine.onStartSettled(StartOutcome.Failed("NotFoundError: Requested device not found")),
        )
        assertEquals(Phase.IDLE, machine.phase)
    }

    // ── idle / recording / dispose ──────────────────────────────────────────

    @Test
    fun stopAndCancel_whenIdle_reportNothingRecorded() {
        val machine = AsyncStartRecorderMachine()
        assertEquals(StopAction.ReportNothingRecorded, machine.onStop())
        assertEquals(CancelAction.ReportNothingRecorded, machine.onCancel())
    }

    @Test
    fun cancel_whileRecording_discards() {
        val machine = recording()
        assertEquals(CancelAction.DiscardRecording, machine.onCancel())
        assertEquals(Phase.IDLE, machine.phase)
    }

    @Test
    fun settle_afterDispose_isIgnored() {
        val machine = starting()
        machine.onDispose()
        assertEquals(SettleAction.Ignore, machine.onStartSettled(StartOutcome.Started(promptShown = false)))
        assertEquals(Phase.IDLE, machine.phase)
    }

    @Test
    fun nextPress_afterAbandonedPress_startsClean() {
        val machine = starting()
        machine.onStop()
        machine.onStartSettled(StartOutcome.Started(promptShown = true))
        assertTrue(machine.onStart())
        assertEquals(SettleAction.KeepRecording, machine.onStartSettled(StartOutcome.Started(promptShown = false)))
    }

    // ── promptWasShown ───────────────────────────────────────────────────────

    @Test
    fun promptWasShown_trustsPermissionsApi() {
        assertTrue(AsyncStartRecorderMachine.promptWasShown("prompt", elapsedMs = 0))
        assertFalse(AsyncStartRecorderMachine.promptWasShown("granted", elapsedMs = 10_000))
        assertFalse(AsyncStartRecorderMachine.promptWasShown("denied", elapsedMs = 10_000))
    }

    @Test
    fun promptWasShown_withoutPermissionsApi_infersFrom500msWait() {
        assertFalse(AsyncStartRecorderMachine.promptWasShown("unknown", elapsedMs = 499))
        assertTrue(AsyncStartRecorderMachine.promptWasShown("unknown", elapsedMs = 500))
        assertEquals(500, AsyncStartRecorderMachine.PROMPT_INFERENCE_MS)
    }

    // ── failureOutcome ───────────────────────────────────────────────────────

    @Test
    fun failureOutcome_notAllowedAndDenied_isPermanent() {
        assertEquals(
            StartOutcome.Denied(permanently = true),
            AsyncStartRecorderMachine.failureOutcome("NotAllowedError", "Permission denied", "denied"),
        )
    }

    @Test
    fun failureOutcome_notAllowedOtherwise_isRetryable() {
        assertEquals(
            StartOutcome.Denied(permanently = false),
            AsyncStartRecorderMachine.failureOutcome("NotAllowedError", "Permission dismissed", "prompt"),
        )
        assertEquals(
            StartOutcome.Denied(permanently = false),
            AsyncStartRecorderMachine.failureOutcome("NotAllowedError", "Permission denied", "unknown"),
        )
    }

    @Test
    fun failureOutcome_otherError_isFailureWithTypeAndMessage() {
        assertEquals(
            StartOutcome.Failed("NotFoundError: Requested device not found"),
            AsyncStartRecorderMachine.failureOutcome("NotFoundError", "Requested device not found", "granted"),
        )
        assertEquals(
            StartOutcome.Failed("start failed with no error"),
            AsyncStartRecorderMachine.failureOutcome("", "", "unknown"),
        )
    }
}
