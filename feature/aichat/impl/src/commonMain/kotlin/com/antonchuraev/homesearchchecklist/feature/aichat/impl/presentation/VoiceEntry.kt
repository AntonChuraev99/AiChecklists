package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

/**
 * What a "open the chat already recording" entry (the v2 Ask pill's mic) does, given the chat's
 * current state.
 *
 * The entry is TAP-to-start: no finger stays on a mic, so the recording is ended the way the input
 * row already ends a running recording — a tap on its mic (`ChatInputRow`: "tap while already
 * recording → finish & save"). The dock always opens; only the recorder start is conditional.
 */
enum class VoiceEntryDecision {
    /** Open the dock and start the recorder inside the same tap (keeps the browser's user gesture). */
    StartRecording,

    /**
     * Open the dock without starting: the dock itself shows why — a recording or a transcription
     * that is still running, or a pending question whose chips replace the input row (a recording
     * started there would have no mic on screen to stop it).
     */
    OpenOnly,
}

fun voiceEntryDecision(
    isRecording: Boolean,
    isTranscribing: Boolean,
    hasBlockingQuestion: Boolean,
): VoiceEntryDecision = when {
    // A second start would re-open the press gate and double VOICE_STARTED for one recording.
    isRecording -> VoiceEntryDecision.OpenOnly
    // The input row disables the mic while transcribing: a new start() deletes the file in upload.
    isTranscribing -> VoiceEntryDecision.OpenOnly
    hasBlockingQuestion -> VoiceEntryDecision.OpenOnly
    else -> VoiceEntryDecision.StartRecording
}

/** [voiceEntryDecision] over the live chat state. A post-action offer keeps the input, so it does not block. */
fun ChatScreenState.voiceEntryDecision(): VoiceEntryDecision = voiceEntryDecision(
    isRecording = isRecording,
    isTranscribing = isTranscribing,
    hasBlockingQuestion = pendingChoice != null && !pendingChoice.isPostAction,
)

/**
 * Whether a dock-hosted recording must be cancelled because its host surface went away.
 *
 * A tap-started recording has no finger on screen whose release would end it, so every way the dock
 * disappears (scrim tap, swipe, BACK, a route or tab change, opening the capture dock) must end it
 * explicitly — otherwise the microphone keeps running behind a closed dock with nothing to stop it.
 * Cancel, not stop: the user walked away from the dock; transcribing (and billing) audio they never
 * confirmed would be a surprise. The cancel reaches the ViewModel as a normal terminal callback, so
 * the user sees "Recording cancelled".
 *
 * [recordingInViewModel] is the ViewModel's `isRecording` — true from the start tap, including while
 * a permission prompt is still up, which is exactly when a cancel must abandon the pending start.
 */
fun shouldCancelVoiceWhenDockGone(dockOpen: Boolean, recordingInViewModel: Boolean): Boolean =
    !dockOpen && recordingInViewModel

/**
 * Whether going to the background must cancel the dock's recording.
 *
 * Only a recorder that is ACTUALLY capturing ([recorderCapturing]) is cancelled. A start still
 * waiting on the permission prompt is left alone: if a platform ever reported the prompt as a stop,
 * cancelling would abandon the start and the grant would end in "press again" instead of recording.
 */
fun shouldCancelVoiceOnBackground(
    dockOpen: Boolean,
    recordingInViewModel: Boolean,
    recorderCapturing: Boolean,
): Boolean = dockOpen && recordingInViewModel && recorderCapturing
