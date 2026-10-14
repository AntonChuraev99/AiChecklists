---
title: "Voice input: web permission prompt, visible transcription, honest Analyze dialogs"
summary: "Web hold-to-record lost presses to the permission prompt and leaked a recording; transcription looked idle. One shared start machine, a release that clears the UI, a text indicator."
date: 2026-10-14
type: bug-fix
modules: [core/filepicker, feature/aichat, feature/analyze, composeApp]
keywords: [audio, permission, wasmjs, android, hold-to-record, AsyncStartRecorderMachine, getUserMedia, VoicePressTerminalGate, transcribe_audio, AudioRecorderErrorCodes]
project: Checklists
---

# Voice input: web permission prompt, visible transcription, honest Analyze dialogs

**Суть:** the web recorder start is async, so the press is tracked by `AsyncStartRecorderMachine`
(`core/filepicker/api` commonMain). A release before the recorder runs clears the recording UI at
once; the terminal outcome still arrives once. Transcription shows "Recognizing speech…".

## Проблема / Контекст

Android was fixed 2026-10-12. Left on the web: a release during the browser prompt became a
cancel, then the grant recorded with no finger down; a denial came as free text. The timer kept
ticking during the prompt. A cold `transcribe_audio` looked idle. Analyze showed raw player errors
and the "press again" hint under an "Error" title.

## Решение

- `AsyncStartRecorderMachine` (`IDLE / STARTING / RECORDING`): `start()` is a no-op while starting
  or recording; stop/cancel while starting abandon the press silently; abandoned + prompt + grant →
  `PERMISSION_GRANTED_PRESS_AGAIN`; no release (Analyze tap) → auto-start. Prompt detection: the
  Permissions API, else a wait threshold (500 ms, источник: `AsyncStartRecorderMachineTest`, 499/500).
  `NotAllowedError` + `denied` → `PERMISSION_DENIED_PERMANENTLY`.
- `AudioRecorder.wasmJs.kt` drives it; `init.js.template` reports permission state and aborts a
  pending start.
- `endVoicePress` sends `ChatScreenIntent.OnVoicePressReleasedBeforeStart` (clears `isRecording`)
  when the recorder never started; wired in `ChatRoute.kt` and both docks in `App.kt`.
- `ChatInputRow` shows `chat_input_transcribing_placeholder`; Analyze logs raw errors and shows
  `analyze_voice_playback_failed`; the hint uses `AnalyzeVoiceHintDialog`.

## Почему именно так

The machine is pure commonMain: testable, reusable by the unshipped iOS actual (same race). The
expect signature is unchanged — Analyze needs the auto-start. The UI clears on release, the outcome
stays owned by `VoicePressTerminalGate`. Min-instances for `transcribe_audio` is a standing cost,
so the indicator makes the cold wait readable instead.

## Связанные файлы

- `core/filepicker/api/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/core/filepicker/api/recorder/AsyncStartRecorderMachine.kt`
- `composeApp/src/wasmJsMain/resources/init.js.template`
- `feature/aichat/impl/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/feature/aichat/impl/presentation/VoicePressTerminalGate.kt`
- `docs/solutions/chat-voice-hold-to-record-fix-2026-10-12.md` — the Android half.
