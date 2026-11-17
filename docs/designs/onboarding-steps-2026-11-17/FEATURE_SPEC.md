# FEATURE_SPEC v2 — Onboarding step 2, one screen per choice

> Written with the IMPLEMENT pass of 2026-11-17. Source of truth after this file: `feature/onboarding/.../welcome/` (`OnboardingContract.kt`, `WelcomeOnboardingViewModel.kt`) and `WelcomeOnboardingViewModelTest.kt`. Layout: `DESIGN_SPEC.md` next to this file (variant A + "Owner decisions (2026-11-17)").

- **Supersedes** §1–3, §7 and §12 of `docs/designs/onboarding-refresh-2026-11-04/FEATURE_SPEC.md`. **Kept from it unchanged:** finish branches §4.1–4.9 (`finish(seed, target)`, sample timeout, typed → AI, chip, plain list, own file, call, skip, Inbox tips), invariants I1, I2, I5–I8, the web sign-in box §5.3, call availability §5.4.
- **Owner decisions applied:** A (three screens) · Showcase CTA removed, text button "Show me an example" runs the sample · voice = dictation into the field on Write and Talk · Talk send / starter / Chat with text → the chat with the message **already sent** · `onboarding_sample_note_*` kept.

---

## 1. State model

```
OnboardingUiState
├─ step: Showcase | Write | Attach | Talk           ← SavedStateHandle (enum name)
├─ inputText: String                                ← SavedStateHandle; ONE value shared by Write and Talk
├─ showEmptyInputHint: Boolean                      ← Write: Send on a blank field
├─ showVoiceTooShortHint: Boolean                   ← Write / Talk: a press under ~1 s
├─ isCallAvailable: Boolean                         ← constructor input (11-04 §5.4)
├─ isVoiceAvailable: Boolean                        ← !isWeb || user.isGoogleLinked (followed live on web); false = no mic
├─ phase: Idle | Creating(seed) | SampleRunning(Reading|Ready) | AiGenerating(prompt, isSlow)
│         | Recording(screen) | Transcribing(screen) | Leaving(seed)
└─ aiError: Unavailable | SignInRequired | null     ← Write only
derived: isBusy = phase != Idle · isRecording · isTranscribing
```

`OnboardingStep`: `index` 1/2/2/2, `count` = 2 (a constant, not `entries.size`), `wire` showcase/write/attach/talk, `hasComposer` = Write | Talk. `OnboardingMode` (a tile) = Write | Attach | Talk, each with its `step`. `Default` is gone.

Invariants (new or changed; I1, I2, I5–I8 as in 11-04):

- **I1'.** `phase != Idle` ⇒ every intent except `OnInputChanged`, `OnSampleAnimationFinished`, `OnVoicePressReleasedBeforeStart`, `OnVoiceRecordingStopped` is a no-op logged at `info`. Recording and Transcribing are busy phases: back, Skip, Send, tiles, chips are swallowed until the press / transcription ends.
- **I3'.** `aiError != null` ⇒ `step == Write && phase == Idle`. `OnInputChanged`, a successful transcript and `OnBack` clear it.
- **I4 removed** (`pendingFocus`): each screen is its own focus target; Write's autofocus after the step transition is UI-local.
- **I9.** The mic never starts without a user press: only the Route calls `recorder.start()`, from the press gesture. `OnVoiceRecordingStarted` is accepted only when Idle on a composer screen.
- **I10.** One terminal recorder callback per press reaches the ViewModel (`VoicePressTerminalGate`, shared with the chat; moved to `core:filepicker:api`).
- **I11.** Dictation never sends anything: the transcript lands in `inputText`; Send / Chat / Call stay the user's tap.

---

## 2. Matrix — Showcase (step 1)

| State ↓ / Event → | Enter | Tile (W/A/T) | "Show me an example" | Skip | Back | Process death | Repeat tap |
|---|---|---|---|---|---|---|---|
| **Idle** | `onboarding_started` once + `step_viewed{showcase,1}` | → that screen; `step_completed{showcase}` + `mode_selected{write\|attach\|talk}` + `step_viewed{<wire>,2}` | → sample overlay (§4.1 of 11-04), origin `showcase`; exit `step_index` 1 | → Inbox (tips) | OS owns it | Showcase, text kept | second tile tap arrives on the branch → `info` log, no second event |
| **SampleRunning / Leaving** | — | no-op (I1') | no-op (one insert) | no-op | swallowed | Idle; id latch resumes | no-op |
| **Recording / Transcribing** | impossible: no mic on Showcase (I9) | | | | | | |
| loading / empty / error / offline | not applicable: static content; the sample needs no network | | | | | | |

---

## 3. Matrix — Write / Attach / Talk (step 2)

"11-04 §4.x" = finish branch unchanged from the 2026-11-04 spec.

| Screen · State ↓ / Event → | Type | Send (Write) / Talk send | Mic press | Chip / starter | Sample | Source card | Call | Skip | Back | Process death |
|---|---|---|---|---|---|---|---|---|---|---|
| **Write · Idle, empty** | text saved; hints + box cleared | hint, no AI | → Recording(Write) | chip → 11-04 §4.4 | from the box only | — | — | 11-04 §4.7 | → Showcase, text kept | step + text restored |
| **Write · Idle, typed** | as above | → 11-04 §4.3 (AI list) | → Recording; transcript is APPENDED | chips hidden | — | — | — | as above | as above | as above |
| **Write · Idle + aiError** | clears box | retry §4.3 | → Recording | chips back (way out) | box "Try the sample" → origin `ai_error` | — | — | as above | → Showcase, box cleared (I3') | box not saved |
| **Attach · Idle** | — | — | no mic (I9) | — | card CTA → origin `card` | → Analyze, 11-04 §4.2 | — | as above | → Showcase | step restored |
| **Talk · Idle** | text saved | `OpenChat(text, autoSend = true)`; blank → `OpenChat(null)` (§5) | → Recording(Talk) | starter → `OpenChat(starterText, autoSend = true)` | — | — | 11-04 §4.6, only `isCallAvailable` | as above | → Showcase, text kept (carries back to Write) | step + text restored |
| **Recording(screen)** | — (field replaced by the strip) | swallowed | release → §4; drag-off → cancel | swallowed | swallowed | swallowed | swallowed | swallowed | swallowed | Idle on the same screen; the clip is lost |
| **Transcribing(screen)** | field read-only | swallowed | swallowed | swallowed | swallowed | swallowed | swallowed | swallowed | swallowed | Idle; transcript lost, typed text kept |
| **Creating / SampleRunning / AiGenerating / Leaving** | as 11-04 §3 | | | | | | | | swallowed | as 11-04 §6 |

Rows that do not exist, and why: **loading** (nothing fetched on entry) · **empty** (static screens) · **partial** (no list data) · **offline** is not a state: it surfaces as `aiError = Unavailable` on Write Send, as `chat_transcribe_error` on dictation; sample, chips, plain list, Skip and Attach doors work offline; chat / call / Analyze own their own offline handling.

---

## 4. Voice dictation (Write and Talk)

Route (gesture host) → ViewModel, the chat's press contract:

1. Finger down: `gate.onPressStarted()` → `OnVoiceRecordingStarted` → `recorder.start()` (the permission prompt lives inside `start()`; I9). VM: Idle on a composer screen → `Recording(step)`, clears both hints.
2. Finger up before the recorder started (prompt still up): `OnVoicePressReleasedBeforeStart` → Idle at once; the terminal callback still follows.
3. Terminal callback (once per press, I10) → `OnVoiceRecordingStopped(path?, mime, stop?)`. Accepted while Recording or Idle; during any other phase it is dropped with a `warning` (no transcription).

| Outcome | State | Visible answer | `onboarding_voice_recorded.outcome` |
|---|---|---|---|
| path ≠ null | `Transcribing(screen)` → `transcribe_audio` (1 credit) | composer "Transcribing…" | — |
| · Success | Idle; `inputText = appendTranscript(old, text)`, saved; box cleared | the text in the field | `transcribed` |
| · EmptyTranscript | Idle, text kept | snackbar `chat_transcribe_empty` | `empty` |
| · InsufficientCredits | Idle, text kept | snackbar `chat_insufficient_credits` | `insufficient_credits` |
| · Network / Service / NotRegistered / FileMissing / thrown | Idle, text kept | snackbar `chat_transcribe_error` | `network_error` / `service_error` / `not_registered` / `file_missing` |
| null, cancel (drag-off or plain null) | Idle | the recording strip disappears | `cancelled` |
| null, too short | Idle, `showVoiceTooShortHint` | `chat_voice_too_short` under the composer | `too_short` |
| null, permission denied (incl. permanently) | Idle | snackbar `onboarding_mic_denied`; the field stays usable | `permission_denied` |
| null, permission just granted | Idle | snackbar `chat_mic_hold_to_record` | `permission_just_granted` |
| null, recorder error | Idle | snackbar `chat_recording_failed`; Route logs `AppLogger.error` | `recorder_error` |

Transcription = `AiChatRepository.transcribeAudio(path, mime, ChatLocaleProvider.current())` behind `OnboardingVoiceTranscriber` (Koin, `OnboardingFeatureModule`). The repository deletes the clip in every branch. Platforms: Android, web (MediaRecorder via `getUserMedia`) and iOS all have a recorder actual.

**`isVoiceAvailable` (decision of 2026-11-17):** `false` on web while the user is not linked to Google — the wallet is 0 until sign-in (same source as `SignInRequired`), so every dictation would end in "not enough credits". The composers then show no mic; `OnVoiceRecordingStarted` and a stray `OnVoiceRecordingStopped` are ignored with a `warning` (no transcription, no event). On web the flag follows `UserDataRepository.getUserDataFlow()`, so the mic appears once the link lands. Android / iOS: always `true`.

---

## 5. Hand-off to the chat (`ShellLaunch.OpenChat(prefill, autoSend)`)

- `autoSend = false` (default, unchanged): prefill goes into the composer unsent.
- `autoSend = true`: App.kt seeds `OnSetScreenContext(null, chatSurfaceFor(backStack, navVariant, null))` synchronously, then `ChatScreenIntent.OnPrefillAndSend(text)` — the chat's own `handleSend()`: credits, 402 → `emitInsufficientCredits`, `ai_chat_*` analytics. Mapping: pure `chatIntentForOpenChat(launch)` in App.kt.
- Requested only after `completeOnboardingUseCase()` succeeded (11-04 §4 tail), so a failed completion leaves nothing pending.
- Talk send: `prefill = inputText.trim()`, `autoSend = prefill != null`. Starter: text resolved through `WelcomeStringResolver` (UI language); unresolvable → `AppLogger.error` + an empty chat (still lands where the user asked).

Credits: a Talk send / starter now spends credits in the chat at once (Layer 2 classify 1 + possibly Layer 3 agent 3). Dictation spends 1 per successful transcription (refunded server-side on a Gemini failure).

---

## 6. Process death

Keys and latches as 11-04 §6. Changes: `onboarding_step` now holds `Showcase|Write|Attach|Talk`; a value saved by the 11-04 build (`TryIt`) restores to Showcase with the text. `phase` is not saved: a recording or transcription dies with the process; the screen and the typed / transcribed text survive.

---

## 7. Analytics

All onboarding events carry `variant = ai_welcome`. Debug builds send nothing.

| Event | Change | Params |
|---|---|---|
| `onboarding_step_viewed` / `_step_completed` | `step` values `write`/`attach`/`talk` replace `try_it`; `step_index` 2 for every branch; `step_count` 2 | unchanged names |
| `onboarding_mode_selected` | `default` retired (no CTA) | `mode` write/attach/talk |
| `onboarding_sample_used` | new origin `showcase` | `origin` card/ai_error/showcase |
| `onboarding_chat_tapped` | meaning: Talk send; `prefilled = true` now means "sent in the chat" | `prefilled` |
| `onboarding_starter_tapped` **new** | Talk starter | `key` plan_week/pack_trip/what_can_you_do |
| `onboarding_voice_recorded` **new** | every mic press on a composer | `screen` write/talk, `outcome` (§4) |
| `onboarding_completed` | unchanged; starters use seed `chat`; "Show me an example" → seed `sample`, `step_index` 1 | `checklist_created`, `seed`, `step_index` |

---

## 8. Tests (`WelcomeOnboardingViewModelTest`, `OnboardingRulesTest`, `ShellLaunchChatIntentTest`, `ChatViewModelTest`)

Tile → own screen (each mode) · back from each branch · text carries Write → Talk · Show me an example · starter auto-send + failed completion · dictation success / append / Talk then send / every failure / every no-audio reason / too-short hint / release-before-start / busy / non-composer screen / late result · process death per branch, old `TryIt` value, mid-transcription · `voiceStopReasonFor`, `appendTranscript`, `transcriptionFailureSnackbar`, step index/count · `chatIntentForOpenChat` · `OnPrefillAndSend` out of credits takes `emitInsufficientCredits`.

## 9. Notes

- Web, signed out of Google: mic hidden (`isVoiceAvailable`, §4) — resolved 2026-11-17.
- Talk send = `OnChatClick` (no separate `OnTalkSend`) — accepted 2026-11-17.
- Talk send and starters spend Free-wallet credits immediately (owner accepted Q2).
