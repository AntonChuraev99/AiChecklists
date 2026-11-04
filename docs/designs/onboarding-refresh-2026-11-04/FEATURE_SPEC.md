# FEATURE_SPEC — Onboarding refresh (Showcase → Try it)

> Проектная спека: часть имён (кейсы тестов, параметры Content, этапы демо) и тайминги — план до реализации (не проверено — источник истины код `feature/onboarding/.../welcome/` и `WelcomeOnboardingViewModelTest.kt`).

- **Owner of this file:** `@feature-expert` (behaviour). Layout: `DESIGN_SPEC.md` next to it (source of truth for screens, strings, motion).
- **Contract in code:** `feature/onboarding/src/commonMain/.../presentation/welcome/OnboardingContract.kt` (UiState / Intent / SideEffect / enums) · `core/navigation/api/.../ShellLaunch.kt` (chat / call hand-off to the app shell).
- **Status of the contract:** SPEC. The new types live beside the old `WelcomeOnboarding*` ones so the module and the old tests keep compiling; the IMPLEMENT iteration switches the ViewModel over and deletes the old types (§9).
- **Owner decisions applied (2026-11-04):** one sample (the note) · sample list has no dates or reminders · web sign-in action only if a one-tap entry exists (it does not, as a callable unit — §5.3) · when AI fails, nothing is added for the call · `GistiPresenceOrb` moves to `core/designsystem` (`@compose-expert`).

---

## 1. State model

```
OnboardingUiState
├─ step: Showcase | TryIt                      ← SavedStateHandle
├─ pendingFocus: Write | Attach | Talk | null  ← one-shot, consumed by the UI
├─ inputText: String                           ← SavedStateHandle
├─ showEmptyInputHint: Boolean
├─ isCallAvailable: Boolean                    ← constructor input (§5.4)
├─ phase: Idle | Creating(seed) | SampleRunning(Reading|Ready) | AiGenerating(prompt, isSlow) | Leaving(seed)
└─ aiError: Unavailable | SignInRequired | null
derived: isBusy = phase != Idle
```

Invariants (the IMPLEMENT tests pin each one):

- **I1.** `phase != Idle` ⇒ every intent except `OnInputChanged`, `OnModeFocusConsumed`, `OnSampleAnimationFinished` is a no-op, **and is logged at `info`** (a swallowed tap while a loader is up is visible feedback already; the log keeps it diagnosable).
- **I2.** `Leaving` is terminal: the only way out is a failed `completeOnboardingUseCase()` → `Idle` + snackbar.
- **I3.** `aiError != null` ⇒ `step == TryIt && phase == Idle`. Any `OnInputChanged` clears it.
- **I4.** `pendingFocus != null` ⇒ `step == TryIt`. `OnBack` clears it.
- **I5.** `onboarding_completed` is sent **at most once per ViewModel lifetime, including process death** (latch `KEY_COMPLETED_TRACKED` in `SavedStateHandle`).
- **I6.** A local checklist is inserted **at most once per seed per ViewModel lifetime, including process death** (latch `KEY_CREATED_CHECKLIST_ID` + `KEY_CREATED_SEED`). A retry after a later failure reuses the stored id.
- **I7.** `completeOnboardingUseCase()` runs **before** any navigation call, on every exit.
- **I8.** Inbox tips are written at most once (existing `inboxTipsWritten`, now also persisted: `KEY_INBOX_TIPS_WRITTEN`).

---

## 2. Matrix — Showcase (step 1)

| State ↓ / Event → | Enter (first) | Tile tap (W/A/T) | CTA "Try it now" | Skip | Back (system) | Process death → restore | Repeat tap |
|---|---|---|---|---|---|---|---|
| **Idle, call on** | `onboarding_started` (once, latch exists) + `onboarding_step_viewed{1}` | → TryIt, `pendingFocus = mode`; `onboarding_step_completed{1, showcase}` + `onboarding_mode_selected{mode}` | → TryIt, `pendingFocus = null`; `step_completed{1}` + `mode_selected{default}` | §4 Skip | Not handled by the screen (`PlatformBackHandler` disabled) → OS: Android root exits the app as today; web: browser back | Restored to Showcase, text kept | Second tap arrives on TryIt (the step changed synchronously) → that tap is a no-op, because tiles are not on TryIt. No double event: the transition is guarded by `step == Showcase`. |
| **Idle, call off** | as above; Talk caption = chat-only (`isCallAvailable = false`) | as above (`Talk` still valid: it rings block D, which then shows Chat only) | as above | as above | as above | as above | as above |
| **Busy** | impossible: Showcase has no busy action except Skip, and Skip → `Leaving` → navigates away | no-op (I1) | no-op (I1) | no-op (I1) | swallowed | `phase` is not saved → restored as Idle | no-op |
| **loading / empty / error / offline / partial** | not applicable: Showcase reads no data. Offline changes nothing here. | | | | | | |

---

## 3. Matrix — Try it (step 2)

Rows are states, columns are events. "→ §4.x" = finish branch.

| State ↓ / Event → | Enter | Type | Send | Sample tap | Own-file pill | Chip | Chat | Call | Skip | Back | Process death |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **Idle, empty field** | `step_viewed{2}`; if `pendingFocus` the UI scrolls/rings/focuses and sends `OnModeFocusConsumed` | `inputText` updated, hint and `aiError` cleared | `showEmptyInputHint = true`, **no AI call** (UI focuses the field) | → §4.1 | → §4.2 | → §4.4 | → §4.5 (no prefill) | → §4.6 (only if `isCallAvailable`) | → §4.7 | → Showcase, `pendingFocus = null`, text kept | step + text restored; phase Idle |
| **Idle, text typed** | — | as above | → §4.3 | → §4.1 (typed text is NOT used by the sample; it stays in the field until navigation) | → §4.2 (text ignored) | chips are hidden while text is present (DESIGN_SPEC §2) → the event cannot arrive; if it does, it wins and the text is ignored | → §4.5 with `prefill = text.trim()` | → §4.6 (text not passed: the call has no text entry) | → §4.7 (text discarded, logged length only) | → Showcase, text kept | as above |
| **Idle + aiError** | — | clears `aiError` | → §4.3 again (retry) | → §4.1, origin `ai_error` when tapped from the box | → §4.2 | hidden (text present) | → §4.5 with prefill | → §4.6 | → §4.7 | → Showcase; `aiError` cleared (I3) | `aiError` not saved → restored without the box; text kept |
| **Creating (chip / plain)** | — | ignored (field disabled; if an event arrives it updates text, harmless) | no-op | no-op | no-op | no-op (double chip tap ⇒ one insert) | no-op | no-op | no-op | swallowed | not saved → Idle; if the insert finished, `KEY_CREATED_CHECKLIST_ID` makes the next same-seed tap resume (§6) |
| **SampleRunning(Reading)** | — | — | no-op | no-op (double tap ⇒ one insert) | no-op | no-op | no-op | no-op | **hidden + no-op** (DESIGN_SPEC: Skip hidden in the overlay) | swallowed (≤ 2 s) | Idle; resume via the id latch |
| **SampleRunning(Ready)** | — | — | no-op | no-op | no-op | no-op | no-op | no-op | no-op | swallowed | Idle; resume via the id latch |
| **AiGenerating** | — | — | no-op | no-op | no-op | no-op | no-op | no-op | no-op (loader has no chrome) | swallowed | the request dies with the process; if `createChecklistFromResult` already returned, the id latch holds it (§6) |
| **AiGenerating(isSlow)** | flips after 8 s on a timer in `viewModelScope` | same as above | | | | | | | | | |
| **Leaving** | — | — | no-op | no-op | no-op | no-op | no-op | no-op | no-op | swallowed | `KEY_COMPLETED_TRACKED` set ⇒ no second `onboarding_completed`; Splash sees onboarding completed and never shows this screen again |

Rows that do not exist for this screen, and why:

- **loading:** nothing is fetched on entry. The only async inputs (nav shell, Google-linked flag, platform) are read lazily inside an action.
- **empty:** the screen is never empty; its content is static.
- **offline:** not a state. Offline only matters to the typed-AI path, where it surfaces as `aiError = Unavailable` (reason `network`). Sample, chips, plain list, Skip work offline. Own file → Analyze owns its own offline handling. Chat → the chat owns it.
- **partial:** not applicable (no list data).
- **permission denied:** onboarding never asks for a permission. Camera/mic come from Analyze / the call after the explicit tap (§4.2, §4.6).
- **rotation / window resize:** the VM is activity-scoped (no Nav3 ViewModel entry decorator), so state survives; `pendingFocus` survives too and the UI must treat a re-composition with the same non-null value as "not yet consumed" only until it sends `OnModeFocusConsumed`.

---

## 4. Finish branches

Common tail of every branch = **`finish(seed, target)`**:

1. `phase = Leaving(seed)`.
2. `trackCompleted(seed, checklistCreated)` — skipped if `KEY_COMPLETED_TRACKED` (I5); set the key **before** emitting.
3. `completeOnboardingUseCase()` (I7). Throws → `phase = Idle`, snackbar `ERROR_KEY`, log `error`. `KEY_COMPLETED_TRACKED` stays set, so a retry does not resend the event (closes the gap noted in `docs/archive/onboarding-tips-to-inbox-2026-10-26.md`: the event used to go out twice when the completion write failed and the user tapped again). Trade-off: an event sent for a completion that then failed and was never retried. Accepted — the alternative (send after the write) loses the event when the shell tears the screen down first, as the archive doc explains.
4. Navigate (`target`), always with a cleared back stack.

### 4.1 Sample — "Try with this note" / "Try the sample"

1. `onboarding_sample_used{origin}`.
2. `phase = SampleRunning(Reading)`.
3. In parallel: the UI plays the overlay; the VM writes the checklist — **no network, no credits**:
   - name = `onboarding_sample_checklist_name`, items = `onboarding_sample_item_1..6` (resolved via `WelcomeStringResolver`), **no due dates, no reminders** (owner decision), `separateCompleted = NEW_CHECKLIST_SEPARATE_COMPLETED` (= true), set explicitly at the creation site.
   - insert is skipped if `KEY_CREATED_SEED == sample` (I6) — the stored id is reused.
   - on success: store the id, emit `checklist_created{source = onboarding_sample}`, `phase = SampleRunning(Ready)`.
   - timeout 5 s on the write (`withTimeout` around a `Deferred` launched in `viewModelScope`, not around the blocking call) → `phase = Idle`, snackbar `onboarding_welcome_create_error`, log `error`.
   - any other failure → same as timeout.
4. Navigate when **both** happened: `stage == Ready` **and** `OnSampleAnimationFinished`. Order-independent (either can come first).
5. `finish(Sample, checklistCreated = true)` → `navigateToChecklistDetail(id, clearBackStack = true)`. Back from detail → Inbox (the navigator rebuilds the stack around `setDefaultRootRoute`).

`ActivationCoordinator.onUserChecklistCreated(id)` is called after the insert (a list the user chose to make; same hook as the weekly path) — **needs confirmation**, see §10 Q4.

### 4.2 Own file — Photo / PDF / Link / Voice pill

1. `ai_entry_tapped{destination = analyze, source = onboarding, input_type = kind.wire}` (the existing door event; this is the door).
2. `finish(OwnFile(kind), checklistCreated = false)` → `navigator.navigateToMainScreen(clearBackStack = true)`, then `navigator.navigateToAnalyzeWithInput(kind, AiEntrySource.ONBOARDING)`.
3. Analyze emits `ai_analyze_started/completed/failed{source = onboarding}` and `checklist_created{source = ai}` itself. Back from Analyze → main. Checked in IMPLEMENT: `navigateToAnalyzeWithInput` only pushes, so without the first call the stack was `[Onboarding, Analyze]` and back landed on onboarding frozen in `Leaving` (the old `OnMoreWaysToStart` had the same defect).

`AiEntrySource.ONBOARDING("onboarding")` is added **in IMPLEMENT together with this send site** (the enum's own rule).

### 4.3 Typed — Send with text

1. `prompt = inputText.trim()` (non-blank by the time we get here). Max length: none on the client; the server's `INPUT_TOO_LONG` maps to `aiError = Unavailable` — **see §10 Q5**.
2. `ai_analyze_started{input_type = raw_text, source = onboarding, surface = onboarding}`. All three `ai_analyze_*` of this path carry `surface = onboarding` (`AnalyticsEvents.Analyze.SURFACE_ONBOARDING`); the Analyze screen never sets `surface`, so an Analyze opened from the own-file pill (§4.2, `source = onboarding`) is told apart from this in-place run. CSAT skips exactly this signature.
3. `phase = AiGenerating(prompt)`; after 8 s → `isSlow = true` (timer job cancelled when the phase leaves AiGenerating).
4. `analyzeRepository.analyzeData(AnalyzeInputData.RawText(prompt))` — the **same repository** as today. A repository that THROWS (instead of returning `Result.failure`) is treated as the same failure.
   - failure → `ai_analyze_failed{input_type, source, surface, failure_reason = AiFailureReason.classify(e).wireValue, error}`; `phase = Idle`; `aiError = resolveAiError(reason, isWeb, isGoogleLinked)` (§5.3); `onboarding_ai_error_shown{kind}`; log `error`. **No snackbar** — the box is the feedback.
   - success → `ai_analyze_completed{input_type, source, surface, item_count, ai_model_*}`.
5. `createChecklistFromResult(name, result)` (name = `suggestedName` or `onboarding_welcome_default_checklist_name`). Failure → `phase = Idle`, `aiError` box with reason `unknown`, `onboarding_ai_error_shown`, log `error` — but **no `ai_analyze_failed`**: the analysis itself succeeded and `ai_analyze_completed` is already out; a `failed` after a `completed` would count one run twice in the `ai_analyze_*` funnel (deliberate). Success → store id (I6, latch keyed by the prompt: `typed:<prompt>` — a Send after editing the text is a new request, not a reopen of the old list), `checklist_created{source = onboarding_ai}`, `activationCoordinator.onAiChecklistCreated(id)` (as today).
6. `finish(Typed, true)` → checklist detail. Keep `AiGenerating` on screen until navigation (no flash, as today).

Why the AI events are emitted here and not by routing through Analyze: the onboarding deliberately skips the Analyze screen (no preview, in-place loader). The emission copies `AnalyzeViewModel.analyzeInput()`'s param set exactly (`INPUT_TYPE`, `SOURCE`, `FAILURE_REASON`, `ERROR`, `item_count`, model arm), so `ai_analyze_*` funnels need no special case. Risk: two emitters of one event drift. IMPLEMENT extracts the param-map builders into one internal helper in `feature:analyze` used by both (no behaviour change for Analyze; its tests pin the map).

### 4.4 Starter chip

1. `onboarding_chip_tapped{key}`.
2. `phase = Creating(Chip)`; insert preset (`checklistNameRes` + `itemRes`), `separateCompleted = true`, no AI. Latch I6.
3. Success → `checklist_created{source = onboarding_chip}` → `finish(Chip, true)` → detail.
4. Failure → `Idle` + snackbar `onboarding_welcome_create_error`.

### 4.5 Chat

1. `onboarding_chat_tapped{prefilled = text.isNotBlank()}`.
2. Inbox tips (§4.8) — fail-open.
3. `shellLaunchRequests.request(ShellLaunch.OpenChat(prefill = text.trim().ifBlank { null }))` **before** navigating (the shell reads a sticky value, §5.1).
4. `finish(Chat, checklistCreated = tipsWritten)` → `navigateToMainScreen(clearBackStack = true)`.

### 4.6 Call

Guard: `isCallAvailable == false` → no-op + `warning` log (the button is not rendered, so this is a defensive check, not a UX path).

1. `onboarding_call_tapped`.
2. Inbox tips (§4.8) — fail-open.
3. `request(ShellLaunch.StartVoiceCall)`.
4. `finish(Call, tipsWritten)` → main. The mic prompt comes from the call flow. AI down: the call's own error screens apply (owner decision: nothing extra here).

### 4.7 Skip (either step)

1. `onboarding_skipped{variant, step, step_index}` (existing event, gains `step_index`; `step` changes from the old enum names to `showcase` / `try_it`).
2. Inbox tips (§4.8) — **fail-open**: a failed seed logs `error` and still lets the user in. Skip must never trap the user on onboarding.
3. `finish(Skip, checklistCreated = tipsWritten)` → main.

### 4.8 Inbox tips (PR #69 kept)

Same code as today's `appendTipsToInbox`: v2 shell only (`navExperimentResolver.ensureResolved() == V2`), `DEFAULT_STARTER_ITEMS`, template + fill pair, `inboxTipsWritten` latch (now persisted, I8). v1 shell: nothing is written (the old v1 "My Checklist" from an empty CTA has no trigger any more — the empty CTA is gone).

Change vs today: tips used to be written only on the empty-CTA path and **blocked** on failure (snackbar, stay). Now they run on every exit that lands in Inbox (Skip, Chat, Call) and are **fail-open**. → §10 Q2.

### 4.9 Save as a plain list (AI-error box)

1. `onboarding_fallback_action{action = plain_list}`.
2. `phase = Creating(PlainList)`; name = `inputText.trim()` cut at 60 characters on a word boundary + `…` (pure function `plainListName(text)`, unit-tested), items = `DEFAULT_STARTER_ITEMS` text, `separateCompleted = true`. Latch I6.
3. Success → `checklist_created{source = onboarding_plain}` → `finish(PlainList, true)` → detail. Failure → `Idle` + snackbar; `aiError` stays (the user can still try the sample).

"Try the sample" in the same box → §4.1 with origin `ai_error` and also `onboarding_fallback_action{action = sample}`.

---

## 5. Cross-module contracts

### 5.1 Chat dock and voice call (they live in App.kt, not behind a route)

`core/navigation/api/.../ShellLaunch.kt` (created in this SPEC):

```kotlin
sealed interface ShellLaunch { data class OpenChat(val prefill: String?); data object StartVoiceCall }
interface ShellLaunchRequests { val pending: StateFlow<ShellLaunch?>; fun request(l); fun consume(l) }
```

- **Why sticky, not `AppNavEvent`:** `AppNavigatorImpl.events` is `replay = 0`. The request is made in the frame that navigates to Inbox, i.e. before the v2 shell (and its dock state `v2ChatDockOpen`, `App.kt:1271`) is composed. A one-shot event would be emitted into nothing or collected by the top-level collector (`App.kt:482`), which cannot reach `v2ChatDockOpen` (declared ~800 lines deeper). A sticky value consumed by the shell has no ordering problem.
- **Why a new interface, not new `AppNavigator` members:** `AppNavigator` has ~27 hand-written fakes in feature tests; new abstract members break all of them, default bodies are banned on this interface for the reason in its KDoc (`navigateToAnalyzeWithInput`).
- **IMPLEMENT (feature side):** `ShellLaunchRequestsImpl` (a `MutableStateFlow`) in `core/navigation/impl`, Koin `single` binding, injected into `WelcomeOnboardingViewModel`.
- **IMPLEMENT (App.kt glue, no new logic):** in the scope where `chatViewModel`, `voiceCallViewModel`, `v2ChatDockOpen` and `startVoiceCall` are visible:
  ```kotlin
  val launch by shellLaunchRequests.pending.collectAsState()
  LaunchedEffect(launch, showV2Shell, showShell) {
      val l = launch ?: return@LaunchedEffect
      if (!showV2Shell && !showShell) return@LaunchedEffect  // shell not mounted yet: wait
      when (l) {
          is ShellLaunch.OpenChat -> { <body of the pill's onOpenChat>; l.prefill?.let { chatViewModel.sendIntent(ChatScreenIntent.OnPrefillInput(it)) } }
          ShellLaunch.StartVoiceCall -> startVoiceCall?.invoke() ?: logger.warning(...)
      }
      shellLaunchRequests.consume(l)
  }
  ```
  - **As implemented, the gate is stricter than the sketch** (`isShellStableForLaunch`, App.kt): arm resolved and a shell mounted, and in v2 the one-shot Inbox re-root already applied with the Inbox in the stack. On first launch `navigateToMainScreen(clearBackStack = true)` leaves `[Main]` (the default root is set later), the re-root then swaps the top route, and the route-change dock closer would shut a dock opened in that frame. The closer additionally spares the route the hand-off opened the dock on (`v2DockLaunchRoute`), so the outcome does not depend on the launch order of two effects keyed in one frame. `StartVoiceCall` waits on the same gate.
  - v1 (control) shell has no dock: `OpenChat` → `navigator.navigateToAiChat()` + the same prefill intent.
  - `OnPrefillInput` exists and does not send (`ChatViewModelTest.onPrefillInput_setsInputTextAndDoesNotSend`).
  - Call surface: a new constant `VOICE_CALL_SURFACE_ONBOARDING = "onboarding"` beside `VOICE_CALL_SURFACE_PILL` (`App.kt:2970`), so `voice_call_*` events tell the onboarding entry from the pill. This means the onboarding start must call `voiceCallViewModel.onAction(VoiceCallAction.Start(surface = VOICE_CALL_SURFACE_ONBOARDING))`, not `startVoiceCall` verbatim — same entry, different `surface`. <!-- docs-leak-scan: reviewed — code line number, no figure -->
  - `ai_chat_opened` follows from the dock as usual; `nav_chat_fab_tapped{source = onboarding}` mirrors the pill's event.

### 5.2 Own file

`AppNavigator.navigateToAnalyzeWithInput(kind, AiEntrySource.ONBOARDING)` — exists (`AppNavigator.kt:108`). Only the enum value is new.

### 5.3 Web "no credits until Google sign-in"

```
resolveAiError(reason, isWeb, isGoogleLinked) =
    SignInRequired  if isWeb && !isGoogleLinked && reason in {CREDIT_GATE, USER_NOT_READY}
    Unavailable     otherwise
```

A pure function, unit-tested. Inputs: `AiFailureReason.classify` (feature:analyze, already a dependency), `isWebTarget` (an `expect val` in `feature:onboarding`, `true` only in `wasmJsMain` — a source-set fact, not a comparison against the `getPlatformName()` string) and `UserDataRepository` (feature:user, already a dependency).

⚠️ `CREDIT_GATE` is also what the server's 402 maps to, and 402 does not separate "no credits" from "no user" (project memory). On Android it always falls to `Unavailable`; on web, a signed-out user gets the sign-in text, which is correct for both meanings.

**Sign-in action: not offered in v1.** The one-tap provider exists (`GoogleAuthRepository.signInWithGoogle()`, popup on web), but the working flow is sign-in + `getIdToken()` + `linkGoogleAccount()` + analytics, written twice (App.kt `handleSignIn` :416-461 and `MainScreenViewModel.handleSignInClick` :407+), with no shared use case. Offering it here means a third copy or a new use case in `feature:user` (`@core-expert`), plus a way to tell the user the credits have arrived (they land asynchronously). By the owner's rule this counts as "no one-tap entry" → the box keeps the two AI-free actions. → §10 Q3.

### 5.4 Call availability — one source with the Ask pill

The Ask pill computes `voiceCallAvailable = voiceCapabilities.isCallSupported && remoteConfigProvider.getBoolean(VOICE_CALL_ENABLED, RemoteConfigDefaults.voiceCallEnabled(isDebugBuild))` at `App.kt:2480`, **after** `NavDisplay`. Onboarding cannot read `VoiceCapabilities` (it lives in `core:voice`, not a dependency of `feature:onboarding`, and adding one pulls the call stack into onboarding).

Contract:

- **App.kt (glue):** hoist that `val voiceCallAvailable` above `NavDisplay` (same expression, one definition), keep the pill reading it, and pass it to the route: `entry<AppNavRoute.WelcomeOnboarding> { WelcomeOnboardingScreen(isCallAvailable = voiceCallAvailable) }`.
- **Route → VM:** `koinViewModel { parametersOf(isCallAvailable) }`; constructor param `isCallAvailable: Boolean = false` → initial `OnboardingUiState.isCallAvailable`.
- RC is already activated on this path (Splash resolves the onboarding variant from RC before navigating here), and the pill does not react to later changes either, so a one-time read matches.
- Web / iOS: `UnavailableVoiceCapabilities.isCallSupported = false` → hidden there by the same expression.

---

## 6. Process death and re-entry

`KEY_*` in `SavedStateHandle` (activity-scoped VM, so Android restores it; web has no process death):

| Key | Type | Written | Effect on restore |
|---|---|---|---|
| `onboarding_started_tracked` (exists) | Boolean | init | no second `onboarding_started` |
| `onboarding_step` | String (`OnboardingStep.name`) | on every step change | step restored |
| `onboarding_input` | String | on every `OnInputChanged` | text restored |
| `onboarding_created_checklist_id` + `onboarding_created_seed` | Long + String | right after a successful insert | a later tap on the **same action** skips the insert and continues from the write's success branch (no duplicate list). The stored value is `sample` / `typed:<prompt>` / `plain_list` / `chip:<key>` — a chip carries its key (and the typed goal its prompt), so a retry with a different chip / edited text creates that list |
| `onboarding_completed_tracked` | Boolean | right before `onboarding_completed` | no second event |
| `onboarding_inbox_tips_written` | Boolean | after both template and fill writes | no duplicate tips |

Not saved, by decision: `phase` (an in-flight operation dies with the process; restoring a loader with no job behind it is a dead screen), `aiError`, `pendingFocus`, `showEmptyInputHint`.

Restore with a created id but onboarding not completed (process died between insert and completion): the user is on Try it; tapping the same action finishes with the stored list. Tapping a **different** action creates a second list — accepted (rare, and the user made a different choice).

**Re-entry in the same activity** (debug menu `navigateToWelcomeOnboarding()`, the only path after completion): the VM is activity-scoped (`koinViewModel()` with no Nav3 ViewModel entry decorator), so it is the same instance, still in `phase = Leaving` → every tap is a no-op. Today's VM has the same defect (`isGeneratingAi` is never reset after success). Debug-only; the fix (a per-entry `koinViewModel(key = …)`) is decided in IMPLEMENT → §10 Q6.

---

## 7. Analytics

All names become constants in `AnalyticsEvents.Onboarding` / `AnalyticsParams` **in IMPLEMENT, together with their emit sites** (a constant without an emitter is a lie in the taxonomy). `variant = ai_welcome` on every onboarding event.

| Event | When | Params |
|---|---|---|
| `onboarding_started` (exists) | first init | `variant` |
| `onboarding_vm_created` (exists) | every init | `variant`, `is_restored` |
| `onboarding_step_viewed` **new** | step becomes visible (VM-side, on entry and on each step change; not on restore of the same step) | `step` (`showcase`/`try_it`), `step_index` (1/2), `step_count` (2) |
| `onboarding_step_completed` (exists) | **both** steps: Showcase → TryIt (tile or CTA), TryIt → any finish | `step`, `step_index` (Int), `step_count` |
| `onboarding_mode_selected` **new** | Showcase → TryIt | `mode` (`default`/`write`/`attach`/`talk`) |
| `onboarding_sample_used` **new** | sample tap | `origin` (`card`/`ai_error`) |
| `onboarding_chip_tapped` **new** | chip | `key` |
| `onboarding_chat_tapped` **new** | Chat | `prefilled` |
| `onboarding_call_tapped` **new** | Call | — |
| `onboarding_ai_error_shown` **new** | box shown | `kind` (`unavailable`/`sign_in_required`), `failure_reason` |
| `onboarding_fallback_action` **new** | box action | `action` (`sample`/`plain_list`) |
| `onboarding_skipped` (exists) | Skip | `step`, `step_index` |
| `onboarding_completed` (exists) | every exit, once (I5) | `checklist_created`, `seed` (§ seed table), `step_index` of the exit |
| `ai_entry_tapped` (exists) | own-file pill | `destination=analyze`, `source=onboarding`, `input_type` |
| `ai_analyze_started/completed/failed` (exist) | typed path | same params as Analyze, `input_type=raw_text`, `source=onboarding` |
| `checklist_created` (exists) | sample / chip / typed / plain list | `source` = new `ChecklistSource` values |

`seed` values (`OnboardingSeed.wire`): `sample` · `own_file_photo` · `own_file_pdf` · `own_file_web_link` · `own_file_voice` · `typed` · `chip` · `plain_list` · `chat` · `call` · `skip`. **Retired:** `default`, `multimodal`, `inbox` (no path emits them any more; their series end on release). `inbox` used to mean "empty CTA → tips"; that path is gone, and tips are now reported by `checklist_created=true` on `skip`/`chat`/`call`.

New `ChecklistSource` values (IMPLEMENT, with the emit sites): `ONBOARDING_SAMPLE("onboarding_sample")`, `ONBOARDING_CHIP("onboarding_chip")`, `ONBOARDING_AI("onboarding_ai")`, `ONBOARDING_PLAIN("onboarding_plain")`. Not `AI` for the typed path: `ai` means "the Analyze flow", and its dashboards would silently gain an onboarding-only cohort.

CSAT (resolved in IMPLEMENT): `CsatManager` skips only the onboarding's OWN scored emits — `checklist_created` with an onboarding `ChecklistSource`, and `ai_analyze_completed` with `surface = onboarding` (the in-place typed goal). An Analyze screen opened from the own-file pill (`source = onboarding`, no `surface`) analyses the user's own file and keeps its CSAT weight of 5, as the old "More ways" path did. Pinned by `CsatManagerScoredTest`.

Debug builds send nothing (`isDebugBuild` guard, as today).

---

## 8. Things outside the happy path

- **Unfinished action on leaving the screen:** the only way to leave mid-action is the OS killing the app (back is swallowed while busy). Covered by §6.
- **Double tap:** every action enters a non-Idle phase synchronously in `onIntent` (before any `launch`), so the second tap hits I1. Plus the id latch for inserts and the completion latch for the event.
- **Empty input:** send → hint, no AI. Whitespace-only = empty (`trim()`).
- **Max input:** no client cap; field `maxLines = 4` is visual. Plain-list name capped at 60 characters (§4.9). Server `INPUT_TOO_LONG` → `Unavailable` box (§10 Q5).
- **IME over content:** `imePadding` on the chrome; mode Write requests focus → IME opens; the UI must keep block C visible above the IME (compose-expert, DESIGN_SPEC §13 "not verified by picture").
- **Every tap answers** (`~/.claude/rules/user-feedback.md`, global harness rule — (не проверено — вне репозитория)): empty send → hint; busy taps → disabled controls + loader already visible; failures → box or snackbar; Call while unavailable → not rendered.

---

## 9. IMPLEMENT plan (next iteration, `@feature-expert`)

1. Switch `WelcomeOnboardingViewModel` to `AppViewModel<OnboardingUiState, OnboardingIntent, OnboardingSideEffect>`; delete `WelcomeOnboardingState`, `WelcomeOnboardingStep`, `WelcomeOnboardingIntent`, `WelcomeOnboardingSideEffect` from `WelcomeOnboardingScreenContract.kt` (keep `WelcomeStarterTemplate`, `WelcomeStringResolver`).
2. Re-point the Route (`WelcomeOnboardingScreen`) at compose-expert's `OnboardingContent`; delete the old private composables that compose-expert left in place.
3. Add, each with its emit site: `AiEntrySource.ONBOARDING`, four `ChecklistSource.ONBOARDING_*`, the `AnalyticsEvents.Onboarding` constants, `AnalyticsParams.STEP_INDEX` / `STEP_COUNT` / `MODE` / `ORIGIN` / `PREFILLED` / `KIND`. Check for `every X` tests over these enums first.
4. `ShellLaunchRequestsImpl` + Koin binding; App.kt glue (§5.1, §5.4).
5. Rewrite `WelcomeOnboardingViewModelTest` (§11) — the old tests are replaced because the contract they pin (four steps, CTA with empty input, chip toggle) no longer exists; list in §11.2.
6. `strings.xml` keys from DESIGN_SPEC §10 are compose-expert's (strings first in its build order); the VM needs `onboarding_sample_checklist_name`, `onboarding_sample_item_1..6`.

---

## 10. Decisions that need the main agent / owner

- **Q1.** `seed` vocabulary retires `default`/`multimodal`/`inbox` and adds `plain_list`. OK?
- **Q2.** Inbox tips now run on Skip, Chat and Call, fail-open (today: only the empty CTA, blocking). OK? Alternative: tips on Skip only.
- **Q3.** No sign-in action in the web box (no reusable one-tap use case; §5.3). Making one = `@core-expert` task (shared `SignInWithGoogleUseCase` used by App.kt, MainScreenViewModel and onboarding).
- **Q4.** Call `ActivationCoordinator.onUserChecklistCreated` for sample / chip / plain list? It drives the reminder opt-in soft-ask, which would then appear over the very first detail screen. Recommended: **no** for the sample (it is a rehearsal, not the user's own content), **yes** for chip and plain list (today's seeded path does not call it either — so "no" everywhere keeps today's behaviour).
- **Q5.** `INPUT_TOO_LONG` / `DAILY_LIMIT` show the generic "can't reach the AI" text, which is not true for them. A third box kind costs one string. Recommended: accept for v1 (both are unreachable for a new Free user typing a goal).
- **Q6.** Activity-scoped VM re-entry (§6) — fix now or leave (debug-only)?

---

## 11. Test cases for IMPLEMENT (RED first on the key branches)

### 11.1 New

Pure functions (no VM): `plainListName` (≤60, word boundary, no trailing space, `…`; text shorter than 60 untouched) · `resolveAiError` (web+signed-out+credit_gate → SignInRequired; android+credit_gate → Unavailable; web+signed-in+credit_gate → Unavailable; network → Unavailable) · `OnboardingSeed.OwnFile(kind).wire` for all four kinds.

ViewModel (fakes from the existing test file):

1. **sample_createsExactlyOneChecklist_withLocalizedItems_noAiCall** — one `addChecklist`, 6 items, name = sample key, `separateCompleted = true`, `analyzeCallCount == 0`, no due dates.
2. **sample_doubleTap_insertsOnce** — two `OnSampleClick` before the write finishes (`blockAddUntilReleased`) → `addChecklistCallCount == 1`.
3. **sample_navigatesOnlyAfterWriteAndAnimation** — write done, no `OnSampleAnimationFinished` → no navigation; after it → detail with `clearBackStack = true`; and the reverse order.
4. **sample_writeTimeout_returnsToIdleWithSnackbar**.
5. **completeBeforeNavigation_everyBranch** — parameterised over sample / chip / typed / plain / own file / chat / call / skip: the fake records call order; `completeOnboarding` index < navigation index.
6. **completed_sentOnce_whenCompletionFailsAndUserRetries** — `throwOnNextUpdate`, tap, tap again → exactly one `onboarding_completed`.
7. **skip_sendsSeedSkip_fromBothSteps** — `seed == "skip"`, `step_index` = 1 and 2.
8. **skip_tipsWriteFails_stillCompletesAndNavigates** (fail-open) — `ensureInboxThrows`.
9. **typed_aiFailure_showsUnavailableBox_keepsText_noSnackbar** — plus `ai_analyze_failed{source=onboarding, failure_reason}`.
10. **typed_aiFailure_thenSample_createsSample** and **typed_aiFailure_thenPlainList_namesListFromText**.
11. **typed_success_emitsAnalyzeStartedCompleted_andChecklistCreatedOnboardingAi**.
12. **typed_editAfterError_clearsBox**.
13. **emptySend_showsHint_noAiCall**.
14. **call_hiddenWhenUnavailable** — `isCallAvailable = false` → state false; `OnCallClick` → no navigation, no `ShellLaunch`, no event.
15. **call_available_requestsStartVoiceCall_thenCompletes_thenMain**.
16. **chat_withText_requestsOpenChatWithTrimmedPrefill_notSent**; **chat_empty_prefillNull**.
17. **ownFile_navigatesToAnalyzeWithInput_onboardingSource_afterCompletion** — for one kind; `seed = own_file_<kind>`.
18. **tile_setsPendingFocus_consumeClears_backClears** and **back_fromTryIt_keepsText**.
19. **stepCompleted_bothSteps_withNumericIndex**.
20. **busy_ignoresAllActions** — in `AiGenerating`, every action intent is a no-op.
21. **processDeath_restoresStepAndText_andCreatedIdPreventsDuplicate** — build VM 1, insert, rebuild VM 2 with the same `SavedStateHandle`, tap the same action → no second insert, navigates to the stored id.

### 11.2 Existing tests the contract change retires (rewrite in IMPLEMENT, do not weaken)

All in `feature/onboarding/src/commonTest/.../welcome/WelcomeOnboardingViewModelTest.kt`. They pin behaviour that no longer exists, so each is replaced by the §11.1 case named after it, in the same diff as the VM switch, after the main agent has confirmed the contract change (`~/.claude/rules/testing.md` — (не проверено — вне репозитория)):

| Test | Pins | Replaced by |
|---|---|---|
| `initialState_isWelcomeStep` | 4-step enum | initial `Showcase` (18) |
| `onNext_advancesThroughSteps`, `onNext_atLastStep_isNoOp` | `OnNext` through 4 steps | tile / CTA (18, 19) |
| `onBack_fromWelcome_isNoOp`, `onBack_fromCapture_returnsToWelcome` | old steps | 18 |
| `onTemplateSelected_selectsAndClearsTypedText`, `onTemplateSelected_reTap_deselects` | chip toggle + second CTA tap | chip creates in one tap (§4.4) |
| `createFirstChecklist_chipSelected_usesChipNameAndChipItems`, `createFirstChecklist_chipWins_…` | chip via CTA | chip one-tap test (same asserts on name/items) |
| `createFirstChecklist_typedNoChip_*` (4 tests) | typed via CTA, snackbar on failure | 9–11 (box instead of snackbar) |
| `createFirstChecklist_typedWhitespaceOnly_treatedAsEmptyUsesDefaultStarter`, `createFirstChecklist_emptyInputNoChip_usesDefaultNameAndDefaultStarterItems` | empty CTA creates a list | 13 (empty send creates nothing) |
| `createFirstChecklist_navigatesToDetailWithClearedBackStack`, `createFirstChecklist_repoError_setsErrorAndDoesNotNavigate` | CTA path | 3, 4 |
| `createFirstChecklist_v2_*` (8 tests), `createFirstChecklist_v1_emptyInput_completedEventKeepsDefaultSeed`, `createFirstChecklist_control_emptyInput_neverTouchesInbox` | tips via empty CTA, blocking on failure | tips via Skip (7, 8) — the append-order, no-duplicate and v1-untouched asserts are carried over unchanged |
| `onMoreWaysToStart_*` (2 tests) | "More ways" card → Analyze hub | 17 |
| `onSkip_completesOnboardingToMainScreen` | skip without tips / seed | 7 |

---

## 12. UI_SKELETON — what `@compose-expert` builds against

All stateless; state in, lambdas out. Files under `feature/onboarding/src/commonMain/.../presentation/welcome/` (`components/` for blocks). New files only; the old `WelcomeOnboardingScreen.kt` stays compiling until IMPLEMENT re-points the Route.

```kotlin
// welcome/WelcomeOnboardingScreen.kt (OnboardingContent lives here)
@Composable fun OnboardingContent(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
)
// Picks the surface from state.phase: SampleRunning → SampleAnalyzingOverlay; AiGenerating → AiGeneratingLoader;
// else chrome + AnimatedContent(state.step). Owns PlatformBackHandler(enabled = state.step == TryIt || state.isBusy)
// { if (!state.isBusy) onIntent(OnBack) }.

// components/
@Composable fun OnboardingTopBar(step: OnboardingStep, showSkip: Boolean, onBack: () -> Unit, onSkip: () -> Unit)
@Composable fun StepProgress(current: Int, total: Int)                       // a11y "Step %d of %d"
@Composable fun ShowcaseStep(isCallAvailable: Boolean, onTileClick: (OnboardingMode) -> Unit, onCtaClick: () -> Unit)
@Composable fun ShowcaseTiles(isCallAvailable: Boolean, onTileClick: (OnboardingMode) -> Unit)
@Composable fun DemoStageWrite() / DemoStageAttach() / DemoStageTalk()     // decorative, fontScale-locked
@Composable fun TryItStep(
    state: OnboardingUiState,          // inputText, showEmptyInputHint, aiError, isCallAvailable, isBusy, pendingFocus, phase
    onIntent: (OnboardingIntent) -> Unit,
)   // sends OnModeFocusConsumed after it has scrolled / ringed / focused pendingFocus
@Composable fun SampleCard(enabled: Boolean, onTrySample: () -> Unit)
@Composable fun SampleNote(width: Dp, textScale: Float)
@Composable fun SampleNoteThumb()
@Composable fun TypeBlock(
    text: String, showEmptyHint: Boolean, aiError: OnboardingAiError?, enabled: Boolean,
    onTextChange: (String) -> Unit, onSend: () -> Unit,
    onChipClick: (WelcomeStarterTemplate) -> Unit,
    onErrorSample: () -> Unit, onErrorSavePlain: () -> Unit,
    focusRequester: FocusRequester,
)
@Composable fun AiErrorBox(kind: OnboardingAiError, onSample: () -> Unit, onSavePlain: () -> Unit)
@Composable fun TalkCard(isCallAvailable: Boolean, hasTypedText: Boolean, enabled: Boolean, onChat: () -> Unit, onCall: () -> Unit)
@Composable fun SampleAnalyzingOverlay(stage: SampleStage, onFinished: () -> Unit)   // calls onFinished once at the end of "ready"; haptic on Ready
@Composable fun AiGeneratingLoader(prompt: String, isSlow: Boolean)
```

Own-file block = existing `SourceRowSection(title, onSelect = { kind -> onIntent(OnOwnSourceClick(kind)) }, style = Page)` — its callback already carries `AnalyzeInputKind` (`SourceRow.kt:123-127`), no mapping. Chips = `GistiPromptChips` over `WelcomeStarterTemplate.entries` → `OnStarterChipClick(template)`.

Preview fixtures (`internal object OnboardingPreviewFixtures`, debug source set if the module has one, else `commonMain` beside the previews — see memory note on previews in main):

| Name | State |
|---|---|
| `showcaseDefault` | `OnboardingUiState(isCallAvailable = true)` |
| `showcaseCallOff` | `OnboardingUiState(isCallAvailable = false)` |
| `tryItDefault` | `step = TryIt, isCallAvailable = true` |
| `tryItModeWrite` / `tryItModeAttach` / `tryItModeTalk` | `pendingFocus = Write/Attach/Talk` |
| `tryItCallHidden` | `step = TryIt, isCallAvailable = false` |
| `tryItTyped` | `inputText = "Pack for a beach trip"` |
| `tryItTypedLong` | 4-line text |
| `tryItEmptyHint` | `showEmptyInputHint = true` |
| `tryItAiError` | `inputText = …, aiError = Unavailable` |
| `tryItSignInError` | `aiError = SignInRequired` |
| `tryItChipCreating` | `phase = Creating(OnboardingSeed.Chip)` |
| `sampleReading` / `sampleReady` | `phase = SampleRunning(Reading / Ready)` |
| `aiGenerating` / `aiGeneratingSlowLongPrompt` | `phase = AiGenerating(prompt, isSlow)` |

If a state the layout needs is missing from `OnboardingUiState`, compose-expert returns `NEEDS_DELEGATION @feature-expert` rather than adding a field.
