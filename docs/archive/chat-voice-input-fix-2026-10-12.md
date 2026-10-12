# Chat voice input: one recorder start, visible dock, honest cancel reason

**Статус:** Done
**Дата старта:** 2026-10-12
**Start SHA:** d7cffafcfb0ba2887d26e0b0eacf1e27c4cb7313
**Project:** Checklists
**Тип:** bug-fix
**Затронутые модули:** core/filepicker, feature/aichat, feature/analyze, composeApp, core/designsystem

## Цель (продуктовая)

Hold the mic in chat, release, see transcription in progress, see the text in the input — on the
home dock and in the full-screen chat. The first press after install must not leave a recording
running with no finger down. Analytics must tell a broken recording from a user cancel.

Source: `docs/backlog/2026-10-12-chat-voice-input-broken-flow.md`.

Owner decisions (2026-10-12): the cold start of `transcribe_audio` is out of this task; the home
dock stays visible for the whole recording and transcription; no device repro before the fix —
the owner verifies the debug APK on a physical phone.

## Технический план

1. Red repro for the double start at the ViewModel boundary.
2. Recorder: idempotent `start()`, no auto-start after a grant once the press is over, a distinct
   code for a too-short recording.
3. Chat: the gesture is the only caller of `start()`, cancel reason in the intent and in analytics,
   one terminal callback per press, hint after the permission grant.
4. Dock: stays mounted while recording and transcribing; screenshot cycle before/after.

Contract between layers: recorder codes are shared constants (`AudioRecorderErrorCodes`); one
press produces exactly one terminal callback.

## Лог итераций

### Итерация 1 — 2026-10-12 — test-expert: red repro `voiceRecordingStarted_setsRecordingState_andEmitsNoSideEffect` → fails on the empty-effects assert, rest of the suite green
### Итерация 2 — 2026-10-12 — android-platform-expert: recorder permission state machine + `AudioRecorderErrorCodes` → Android and wasmJs builds green, no device run, no unit test (module has no test source set)
### Итерация 3 — 2026-10-12 — feature-expert: side effect removed, `VoiceCancelReason`, `VoicePressTerminalGate`, `reason` on the cancel event, localized recorder errors on Analyze → `ChatViewModelTest` green including the former red repro
### Итерация 4 — 2026-10-12 — compose-expert: `chatDockKeepExpanded()` adds recording and transcribing to the dock hold → `ChatDockVoiceHoldScreenshotTest` green, before/after frames taken

### Итерация 5 — 2026-10-12 — diff review → feature-expert + android-platform-expert: the gate starts open, the start-failure string carries the exception type → module tests and `:androidApp:assembleDebug` green

**Почему так (итерация 5).** The gate lives in `remember` and was created closed, while the
ViewModel survives an activity recreation with `isRecording = true`. After a rotation the next
mic tap hit a closed gate, the stop never reached the ViewModel and the input stayed disabled.
None of the new unit tests caught it — they exercised one gate instance; the reviewer did.

**Почему так (итерация 2).** The backlog entry said the recording "does not start" after the
permission grant. Reading `AudioRecorder.android.kt` showed the opposite: the pending-start flag
survived `cancel()`, so the recorder started with no finger down. The fix became "do not start"
plus a hint, not a hint alone.

**Почему так (итерация 4).** The keyboard inset was a suspect and is not involved: the collapse
comes only from the blur handler in `GistiInlineChatPanel.kt`, which read `keepExpanded = false`
on an empty chat. No host `imePadding` was changed.

## Выводы

- All four client defects are fixed in code; the cold start stays in the backlog entry.
- Verified by: the module test task for `feature/aichat/impl`, the Roborazzi verify task for
  `ChatDockVoiceHoldScreenshotTest`, `:androidApp:assembleDebug` and
  `:composeApp:compileKotlinWasmJs`.
- Not verified: anything on a device (gesture, permission dialog, keyboard, Analyze screen), the
  iOS compile, and the Hindi wording of the new strings.
- After a cancel with a blank input the dock stays open without the keyboard instead of collapsing:
  in the v2 shell a collapse is a dismissal and would remove the panel from under the hint.
- Left open, not started: the transcription indicator is only a small spinner in the input row —
  weak for a wait of tens of seconds; the Analyze audio player still shows a raw error string.
- Permanent write-up: `docs/solutions/chat-voice-hold-to-record-fix-2026-10-12.md`.

## Предложения по улучшению агентов

- feature-expert reported that the Analyze screen no longer auto-starts after a permission grant.
  The recorder code it was briefed on does the opposite (`AWAITING_START` → start). A claim about
  another agent's layer should quote the code line, not be inferred from the brief.
