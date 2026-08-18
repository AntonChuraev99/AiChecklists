# Voice Call — User Acceptance Testing (Device & Live Cycles)

**Status:** Open

**Severity:** P1 — no user-visible feature without UAT

**Trigger:** Feature ready for dog-fooding; user will test on real device(s).

---

## Problem

Modular tests (VAD detects silence, TTS initializes) ≠ real-world behavior. No voice call has been tested end-to-end on a physical Android device or web browser. Unknown unknowns:

- Microphone permission grant flow and denial handling
- VAD accuracy on real speech vs. background noise (traffic, café, office chatter)
- Echo: playback audio leaking into microphone during simultaneous record+playback (barge-in prep)
- TTS quality across device speakers (tiny speaker on phone vs. Bluetooth headset)
- Lifecycle: app backgrounding, screen lock, incoming call interruption → session resume/cancel state
- API rate limits: rapid-fire calls, quota exhaustion, grace degradation

## Test Plan

### Phase 1: Manual Walkthrough (30 min)
1. Launch debug-build on Pixel device (or emulator with audio mock)
2. Navigate to AI Chat screen, tap "Call Gisti"
3. Speak test phrase: «What's the weather tomorrow?»
4. Verify VAD stops recording after silence (≥2 sec)
5. Verify TTS plays response
6. Repeat for 3–5 cycles
7. Log observations: clarity, lag, noise, crashes

**Expected:** No crashes, latency <5s per round, TTS intelligible, VAD triggers on silence.

### Phase 2: Edge Cases (30 min)
1. Background the app during TTS playback → resume → continue call (should NOT crash, session recovers or restarts cleanly)
2. Lock screen during call → unlock (mic/speaker state preserved)
3. Incoming call / notification sound during TTS (expected: interruption, app dismisses call UI gracefully)
4. Network toggle (WiFi ↔ cellular) mid-call (expected: graceful error, retry or abort)

### Phase 3: Live Mode Test (if `create_live_session` deployed)
1. Unlock debug menu (Volume Up→Down→Up)
2. Toggle **Voice Call Engine → Live** (visible only in debug)
3. Repeat Phase 1 with Live WebSocket
4. Compare: latency, barge-in ability, cost per session

## Success Criteria

- [ ] No ANR (Application Not Responsive) during any call
- [ ] No FATAL crashes in Crashlytics after UAT
- [ ] VAD accuracy ≥80% (correctly stops recording on silence, doesn't stop mid-speech)
- [ ] TTS latency ≤2 sec after transcription completes
- [ ] App state survives background/resume cycle (session either continues or dismisses cleanly with message)
- [ ] Live mode (if tested): comparable latency, no token expiry errors

## Instrumentation

- App logs (Timber/Logcat) tag `VoiceCall` captures all state transitions
- Amplitude events (pre-logged in code):
  - `voice_call_initiated` (TurnBased or Live)
  - `voice_call_mic_permission_granted` / `denied`
  - `voice_call_vad_triggered` (recording stopped)
  - `voice_call_transcription_received` (CF response)
  - `voice_call_tts_started` / `tts_finished`
  - `voice_call_completed` or `voice_call_failed` + reason

## Deferred Until

- Feature branches builds are available for user (release on Play Store or via `.apk` sideload)
- User has physical device(s) with mic + speaker (emulator OK for sanity, not for real audio testing)
- Scheduled UAT window (30–60 min uninterrupted time)
