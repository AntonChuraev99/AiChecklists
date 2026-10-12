package com.antonchuraev.homesearchchecklist.navigation

/**
 * Whether the shared chat dock must ignore its blur-collapse — the value every host passes as
 * `GistiExpandableDockContent(keepExpanded = …)`.
 *
 * The dock reads a blur of its input as "the user dismissed the keyboard" and, when the input is
 * blank, collapses to Peek. That reading is wrong whenever the app itself took the focus away by
 * disabling the field, which it does in three situations: a turn is in flight, a hold-to-record is
 * running, and the recording is being transcribed. Each of them has something on screen that the
 * collapse would hide (the typing indicator, the recording timer, the transcription spinner), so
 * each of them holds the dock open.
 *
 * In the v2 shell a settle at Peek is not a smaller dock but a dismissal — `V2ChatDockOverlay`
 * unmounts it — so a missing term here does not shrink the panel, it removes it.
 *
 * The dock samples this at the moment focus is lost and nowhere else, so it is a hold and not a
 * latch: once the terms go false the next blur collapses the dock exactly as before, and a manual
 * drag, BACK or a scrim tap collapse it at any time regardless of the value.
 *
 * @param itemCreateActive the dock is in item-create mode (pinned open for rapid multi-add).
 * @param hasLastAnswer the answer frame has content: a pending choice, an in-flight turn or a reply.
 * @param isRecording a hold-to-record is running — the recording overlay is on screen.
 * @param isTranscribing the recording is being transcribed — the input row shows the spinner. This
 *   can last tens of seconds on a cold function, and the user has nothing else telling them the
 *   press was heard.
 */
internal fun chatDockKeepExpanded(
    itemCreateActive: Boolean,
    hasLastAnswer: Boolean,
    isRecording: Boolean,
    isTranscribing: Boolean,
): Boolean = itemCreateActive || hasLastAnswer || isRecording || isTranscribing
