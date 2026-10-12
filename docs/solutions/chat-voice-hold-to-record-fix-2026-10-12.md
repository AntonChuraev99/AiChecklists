---
title: "Chat voice input: hold-to-record that reaches a visible result"
summary: "One press started the recorder twice, the dock vanished while recording, a permission grant left an orphan recording. Fix: one start() owner, a permission state machine, a dock hold."
date: 2026-10-12
type: bug-fix
modules: [core/filepicker, feature/aichat, feature/analyze, composeApp]
keywords: [audio, permission, hold-to-record, MediaRecorder, rememberAudioRecorderLauncher, AudioRecorderErrorCodes, VoicePressTerminalGate, VoiceCancelReason, chatDockKeepExpanded, keepExpanded, RECORD_AUDIO, analytics, android]
project: Checklists
---

# Chat voice input: hold-to-record that reaches a visible result

**Суть:** hold-to-record needs one caller of the recorder's `start()`, a recorder that knows the
permission dialog ended the press, and a dock told that the app, not the user, took the focus.
See `AudioRecorder.android.kt`, `VoicePressTerminalGate.kt`, `ChatDockKeepExpanded.kt`.

## Проблема / Контекст

Recording and transcription worked, yet the user saw nothing. The gesture and a ViewModel side
effect both called `start()`, and the second call destroyed the running recording. Disabling the
input blurred it, which collapsed the dock — a dismissal in the v2 shell. A permission grant
started a recording with no finger down. Every failure reported as one cancel.

## Решение

- **One owner of `start()`** — the gesture. The side effect is deleted; guarded by
  `voiceRecordingStarted_setsRecordingState_andEmitsNoSideEffect`.
- **Recorder state machine** — `PermissionRequest { NONE, AWAITING_START, ABANDONED }`. `start()`
  is a no-op while recording or while the dialog is up. `stop()` / `cancel()` during the dialog
  mark the request `ABANDONED` without a callback; the grant then reports
  `PERMISSION_GRANTED_PRESS_AGAIN` and records nothing. A throwing `stop()` reports
  `RECORDING_TOO_SHORT` and deletes the file.
- **Cancel reason** — `voiceCancelReasonFor()` maps a code to `VoiceCancelReason`, sent as the
  `reason` param. `PERMISSION_JUST_GRANTED` is not a cancel: no event, a hold-to-record hint.
- **One terminal callback per press** — `VoicePressTerminalGate` beside the launcher in both
  hosts absorbs the `onResult(null)` a finger release produces after a failed start.
- **Dock hold** — `chatDockKeepExpanded()` adds recording and transcribing to the terms that
  suppress the blur-collapse. A hold, not a latch: sampled only when focus is lost.

## Почему именно так

- The expect signature is unchanged: Analyze records on a tap and needs the auto-start after a
  grant. A press cancelled under the dialog separates the two callers without a parameter.
- The duplicate guard sits in the gesture host: an existing test requires the ViewModel to accept
  a stop with no preceding start.
- The dock is held, not re-expanded on the transcript: a cold `transcribe_audio` wait must not
  be an empty screen.
- Not verified on a device; the iOS actual is uncompiled; the recorder has no unit tests.

## Связанные файлы

- `core/filepicker/api/src/androidMain/kotlin/com/antonchuraev/homesearchchecklist/core/filepicker/api/recorder/AudioRecorder.android.kt`
- `core/filepicker/api/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/core/filepicker/api/recorder/AudioRecorderErrorCodes.kt`
- `feature/aichat/impl/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/aichat/impl/presentation/VoicePressTerminalGate.kt`
- `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/navigation/ChatDockKeepExpanded.kt`
- `docs/backlog/2026-10-12-chat-voice-input-broken-flow.md` — diagnosis and what is left.
