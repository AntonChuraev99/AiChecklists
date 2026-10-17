package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceEntryTest {

    @Test
    fun idleChat_startsRecording() {
        assertEquals(VoiceEntryDecision.StartRecording, ChatScreenState().voiceEntryDecision())
    }

    @Test
    fun alreadyRecording_opensOnly() {
        assertEquals(
            VoiceEntryDecision.OpenOnly,
            ChatScreenState(isRecording = true).voiceEntryDecision(),
        )
    }

    @Test
    fun transcribing_opensOnly() {
        assertEquals(
            VoiceEntryDecision.OpenOnly,
            ChatScreenState(isTranscribing = true).voiceEntryDecision(),
        )
    }

    @Test
    fun blockingQuestion_opensOnly_postActionOfferDoesNot() {
        assertEquals(
            VoiceEntryDecision.OpenOnly,
            voiceEntryDecision(isRecording = false, isTranscribing = false, hasBlockingQuestion = true),
        )
        assertEquals(
            VoiceEntryDecision.StartRecording,
            voiceEntryDecision(isRecording = false, isTranscribing = false, hasBlockingQuestion = false),
        )
    }

    @Test
    fun aiTurnInFlight_stillStartsRecording() {
        // The input row's mic stays live while the assistant answers; the entry matches it.
        assertEquals(
            VoiceEntryDecision.StartRecording,
            ChatScreenState(isProcessing = true).voiceEntryDecision(),
        )
    }

    @Test
    fun dockGone_cancelsOnlyARecordingInProgress() {
        assertTrue(shouldCancelVoiceWhenDockGone(dockOpen = false, recordingInViewModel = true))
        assertFalse(shouldCancelVoiceWhenDockGone(dockOpen = false, recordingInViewModel = false))
        assertFalse(shouldCancelVoiceWhenDockGone(dockOpen = true, recordingInViewModel = true))
    }

    @Test
    fun background_cancelsOnlyACapturingRecorderInTheOpenDock() {
        assertTrue(shouldCancelVoiceOnBackground(dockOpen = true, recordingInViewModel = true, recorderCapturing = true))
        // Permission prompt still up: the start must survive so a grant records.
        assertFalse(shouldCancelVoiceOnBackground(dockOpen = true, recordingInViewModel = true, recorderCapturing = false))
        assertFalse(shouldCancelVoiceOnBackground(dockOpen = false, recordingInViewModel = true, recorderCapturing = true))
        assertFalse(shouldCancelVoiceOnBackground(dockOpen = true, recordingInViewModel = false, recorderCapturing = true))
    }
}
