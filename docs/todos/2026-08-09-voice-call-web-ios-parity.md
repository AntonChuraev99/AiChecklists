# Voice Call — Web and iOS Platform Parity

**Status:** Open

**Severity:** P2 — violates CLAUDE.md architecture parity rule

**Trigger:** Before voice call becomes a marketed feature or counted in platform parity matrices.

---

## Problem

`core/voice/api` expect/actual stubs return `UnsupportedOperationException` on wasmJs and iOS. The voice-call button is hidden on web (no render in the UI), and iOS has no implementation path at all.

CLAUDE.md mandates platform parity: «Web is a full parallel platform (not a lite companion): Compose wasmJs renderer... same features and data on Android and Web.»

Voice call is Android-only by accident, not by design. This breaks the parity promise.

## Solution Candidates

1. **Web (wasmJs):** 
   - Gemini Live supports native `MediaRecorder` → WebSocket on browser (no proxy needed for ephemeral-token)
   - Implement `PcmAudioCapture` via `MediaDevices.getUserMedia()` + `WebAudio` `AnalyserNode`
   - Implement `TextToSpeech` via Web Speech API or `AudioContext` + waveform data
   - TurnBased: existing `transcribe_audio` CF already accepts CORS calls; reuse it

2. **iOS:**
   - Native `AVCaptureSession` for audio recording (parallel to Android `AudioRecord`)
   - `AVSpeechSynthesizer` for TTS (parallel to `TextToSpeech`)
   - Same TurnBased + Live dual-engine structure
   - Requires swift-kotlin bridge via [expect/actual](../../docs/solutions/kotlin-multiplatform-expect-actual-pattern.md)

## Verification

- [ ] Web: test voice-call round-trip on :9090 in Chrome/Firefox/Safari
- [ ] iOS: record screen of full call flow on iPhone simulator
- [ ] Analytics: `voice_call_initiated` event count on all three platforms

## Deferred Until

- User request for web/iOS voice calls (feature parity not auto-triggered without demand)
- OR roadmap milestone explicitly marking voice as cross-platform
- Consensus on which engine (TurnBased / Live) to expose per platform
