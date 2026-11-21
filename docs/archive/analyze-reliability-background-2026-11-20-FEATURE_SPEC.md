# FEATURE_SPEC — Analyze: background analysis + reliability

**Статус:** Done
**Дата:** 2026-11-20
**Mode:** SPEC (feature-expert). Task doc: `docs/archive/analyze-reliability-background-2026-11-20.md`.

## 0. Model in one paragraph

The analysis stops belonging to `AnalyzeViewModel`. The Analyze tap builds an `AnalysisJobRequest` and
calls `AnalysisJobScheduler.enqueue` (app-scoped Koin single). The scheduler persists an `AnalysisJob`
(id = server `request_id`, input captured into app storage, target, analytics dims) and hands the id
to a platform `AnalysisJobLauncher` (Android: unique expedited WorkManager worker; web/iOS: app-scope
coroutine). The worker/coroutine calls the common `AnalysisJobProcessor.process(jobId)`, which does
the network call, writes the checklist (or persists the fill result), emits `ai_analyze_completed` /
`ai_analyze_failed`, and sets a terminal status. Delivery of the outcome is a separate, single-flight
step: exactly one of {the Analyze screen that watches the job, the app-level snackbar host, a
notification tap} wins `claimDelivery(jobId)` and navigates. Nobody else navigates — ever.

Contract files (all compile, `:feature:analyze` Android + wasmJs; existing `AnalyzeViewModelTest` green):

| File | Content |
|---|---|
| `feature/analyze/.../domain/job/AnalysisJob.kt` | `AnalysisJob`, `AnalysisJobInput`, `StoredFileKind`, `AnalysisJobTarget`, `AnalysisJobAnalytics`, `AnalysisJobStatus` (+`isTerminal`), `AnalysisJobOutcome`, `AnalysisJobFailure`, `DeliveryClaimant` |
| `.../domain/job/AnalysisJobScheduler.kt` | `AnalysisJobScheduler` (jobs, observe, enqueue, cancel, claimDelivery, acknowledge, resumePending), `AnalysisJobRequest`, `AnalysisJobRequestInput` |
| `.../domain/job/AnalysisJobPorts.kt` | `AnalysisJobStore`, `AnalysisPayloadStore`, `AnalysisJobLauncher`, `AnalysisJobProcessor` + `ProcessResult`, `AnalysisReadyNotifier`, `AppForegroundMonitor` |
| `.../domain/job/ImageDownscaler.kt` | `ImageDownscaler`, `AnalyzeImageLimits` (2048 px, JPEG 85) |
| `.../domain/job/AnalysisReadySignal.kt` | `AnalysisReadySignal` (pending, onDismissed, onOpen), `ReadyAnalysis`, `ReadyDestination` |
| `.../domain/repository/AiActionCostProvider.kt` | `AiActionCostProvider` (`actionCost: StateFlow<Int?>`, `refresh()`) |
| `.../presentation/AnalyzeJobUiContract.kt` | `AnalyzeJobPhase` (Editing / Waiting / Delivering / Failed), `AnalyzeJobIntent` (OnScreenEntered(token), OnScreenLeft, OnContinueInBackgroundClick, OnCancelWaitingClick, OnRetryClick, OnBackToEditingClick, OnNotificationPermissionResult) |
| `.../presentation/AnalyzeScreenContract.kt` (edited) | new `AnalyzeScreenState` fields: `jobPhase`, `otherJobsInProgress`, `creditsReservedByRunningJobs`, `inputError`, `askNotificationPermission` |
| `.../presentation/AnalyzeViewModel.kt` (edited) | one placeholder branch `is AnalyzeJobIntent -> Unit` so the sealed `when` compiles; IMPLEMENT replaces it |

Contract changes deliberately left to IMPLEMENT (they break exhaustive `when`s or existing fakes):
`aiActionCost: Int = 30` → `Int? = null` · `AiInputType.PDF_BASE64` (+ wire `"pdf_base64"`) ·
new `AiFailureReason` entries · new `AiAnalyzeError` variants · `requestId` parameter on
`FirebaseAiService.generateChecklist` / `analyzeAndFillChecklist` / `AnalyzeRepository.analyzeData`
(optional, default null = generate, so `FirebaseAiAnalyzerAdapter` (chat) and the onboarding typed goal
keep compiling) · `FirebaseAiService.getCreditsInfo(userId)`.

## 1. States × events matrix

States of one job, seen from the screen that started it: **E** Editing (form) · **W** Waiting
(Queued or Running) · **D** Delivering · **F** Failed (watched). Plus states seen from outside:
**BG-run** (job running, no screen attached) · **BG-done** (terminal, unclaimed) · **ACK** (delivered).

Columns: what the user sees · what is persisted · analytics · credits.

### 1.1 Start

| # | State + event | User sees | Persisted | Analytics | Credits |
|---|---|---|---|---|---|
| 1 | E, no material / empty input, tap Analyze | Existing `analyze_error_no_input` dialog (unchanged) | nothing | none (as today) | none |
| 2 | E, WEB_LINK not a URL (`hello world`, no dot, spaces, non-http scheme), tap Analyze | Inline `inputError` = `analyze_error_invalid_url` under the field; button stays | nothing | none | none — nothing sent |
| 3 | E, WEB_LINK `example.com/x` (no scheme) | Accepted; sent as `https://example.com/x` | job with normalized URL | `started` | reserved by server |
| 4 | E, file material whose bytes cannot be read (cache cleared) | Dialog "Couldn't read this file. Pick it again." Phase stays E, file fields cleared | nothing | none (no job) | none. Today it sends the text `"Failed to read image: <path>"` and gets charged: removed |
| 5 | E, user id blank (new install, sign-in pending), tap Analyze | Existing `analyze_error_user_not_ready` dialog | nothing | `failed` reason `user_not_ready` (as today) | none |
| 6 | E, valid input, online, tap Analyze | Phase W(waitingForNetwork=false): loader "Analyzing your content" + line "You can leave — we'll let you know when it's ready" + "Continue in background" | job Queued→Running, payload copied (Android `filesDir`), photo downscaled in the job | `ai_analyze_started` from the VM with today's params (input_type, source) | server reserves `action_cost` under `request_id` = job id |
| 7 | E, valid input, **offline** (Android) | W(waitingForNetwork=true): "Waiting for a connection — the analysis starts automatically" + "Cancel" + "Continue in background" | job Queued; WorkManager holds it on `CONNECTED` | `started` | none until sent |
| 8 | E, valid input, offline (web) | Ktor fails fast → F: `analyze_error_network` + "Try again" | job Failed NETWORK | `started`, `failed` network | none |
| 9 | W, tap Analyze again / double tap | Impossible: the bottom bar is hidden in W (today's rule). The VM additionally ignores `OnAnalyzeClick` unless phase is E (one in-flight latch per visit) | — | — | — |
| 10 | E with `autoAnalyze` + initialText (activation hero) | Enqueue on init exactly once per visit (token latch, not per VM instance) → W | job target NewChecklist(fromActivationHero=true) | `started` | reserved | <!-- docs-leak-scan: reviewed — matrix row number, no metric -->
| 11 | E, credits < cost (known) − reserved | Button disabled + `analyze_not_enough_credits` (existing). Cost unknown (fetch failed) → button NOT gated, server decides | — | — | — |
| 12 | W, notification permission needed (`OnNotificationPermissionResult`) (Android 13+, first job, never asked) | System dialog over the loader; analysis runs regardless of the answer | "asked" flag | — | — |

### 1.2 While running

| # | State + event | User sees | Persisted | Analytics | Credits |
|---|---|---|---|---|---|
| 13 | W, **Back** / "Continue in background" | Previous screen (Inbox/Main). No loader anywhere, no later yank | job unchanged; screen detached (`OnScreenLeft`) | none | unchanged |
| 14 | W, user goes elsewhere in-app (after Back: other tabs, chat, a checklist) | Normal app; when the job finishes → app snackbar (row 22) | — | — | — |
| 15 | W, **HOME** (Android) | Worker keeps running as expedited work (FGS notification on API ≤ 30 only, from `getForegroundInfo`); network is not cut | — | — | — |
| 16 | W, tab hidden (web) | fetch keeps running in a background tab | — | — | — |
| 17 | W, **rotation** / config change | Same W (token from `rememberSaveable` unchanged → no reset) | — | — | — |
| 18 | W, connection lost mid-request (Android) | Ktor retries; then RetryLater → W(waitingForNetwork=true) on the screen if attached | status Queued(true), attempt+1, SAME id | none yet | server dedups by id — never charged twice; refund path is server-side |
| 19 | W, connection lost, last attempt (Android) / any (web) | F (if attached) or failure snackbar/notification: `analyze_error_network` + "Try again" | Failed NETWORK, retryable | `failed` reason network | server refund (existing behaviour) |
| 20 | W, **process killed** (Android) | Nothing visible. On relaunch: splash → home; the worker re-runs (or already finished) with the same id; result arrives as row 22/23 | job record (DataStore) + payload file survive | completed/failed emitted once by whichever run reaches terminal | dedup by id |
| 21 | W, **page reload / tab closed** (web) | On next load `resumePending`: text/URL jobs re-run with the same id; file jobs → failure "The analysis was interrupted. Pick the file again." | Failed INTERRUPTED (file) | `failed` reason `interrupted` | text/URL: dedup by id; file: possibly charged with no result (fork 6) |

### 1.3 Outcome

| # | State + event | User sees | Persisted | Analytics | Credits |
|---|---|---|---|---|---|
| 22 | Success NewChecklist, screen attached + foreground | D → `navigateToChecklistDetail(id, clearBackStack = true)` (today's behaviour), notification cancelled | checklist in Room (written by the processor), job deliveredBy=SCREEN, ack | `completed` (+`app_state=foreground`) + `checklist_created` source=ai from the processor; activation hook at delivery | balance from response → `userDataRepository.update` (existing) |
| 23 | Success NewChecklist, **no screen attached**, foreground | App snackbar "Checklist "<name>" is ready" · **Open** → `navigateToChecklistDetail(id)` (plain push). Timeout/swipe → ack, no navigation; the checklist is already in the list | deliveredBy=IN_APP_HOST on Open | `completed`; on Open `ai_analyze_result_opened via=in_app` | same |
| 24 | Success, app **in background** | System notification "Your checklist is ready" (Android). Web: nothing until the tab is visible → row 22/23 | unclaimed | `completed` (+`app_state=background`) | same |
| 25 | Notification tap (cold or warm start) | App opens straight on the checklist detail (fill: on the review preview) | deliveredBy=NOTIFICATION | `ai_analyze_result_opened via=notification` (NOT `reminder_notification_tapped`) | — |
| 26 | Notification tap, outcome already delivered in-app | App opens where it was; no navigation | — | none | — |
| 27 | Returns via launcher while the Analyze screen is still on top and attached, job done | D → navigates (row 22); notification cancelled | SCREEN | — | — |
| 28 | Success **Fill mode** / `fillDefault`, attached | Today's flow: `AnalyzeResultHolder.set` → `navigateToAnalyzeResultPreview()` | outcome FillReady persisted; nothing written to Room until the user confirms in preview | `completed` | balance update |
| 29 | Success Fill, not attached | Snackbar "Answers for "<checklist>" are ready" · **Review** → holder rebuilt from the persisted `FillReady` → preview. Notification when backgrounded | FillReady until ack | `completed`; opened via=… | — |
| 30 | Success with **0 items** (old server or slipped through) | Treated as `nothing_found` (row 33). An empty checklist is never created | Failed NOTHING_FOUND | `failed` nothing_found | server refunds on its own `nothing_found`; for an old server's empty success the charge already happened (logged warning) |
| 31 | 402 credits | F / failure snackbar: server message (existing). CTA "Change input" is wrong here → only "OK" | Failed CREDIT_GATE, not retryable | `failed` credit_gate | not charged |
| 32 | `error_code=input_too_large` (4xx) **or HTTP 413** (platform body limit) | "This file is too large to analyze. Try a smaller file or a photo of the page." · "Change input" | Failed INPUT_TOO_LARGE | `failed` `input_too_large` | not charged |
| 33 | `error_code=nothing_found` | "Couldn't find anything to turn into a checklist. Try a clearer photo or different text." · "Change input" | Failed NOTHING_FOUND | `failed` `nothing_found` | refunded by the server |
| 34 | 429 daily limit | Server message (existing) | Failed DAILY_LIMIT, not retryable | `failed` daily_limit | none |
| 35 | 5xx after Ktor retries | `analyze_error_analysis_failed` · "Try again" | Failed SERVER_5XX, retryable | `failed` server_5xx | server refund (existing) |
| 36 | 403 App Check | `analyze_error_analysis_failed` · "Try again" | Failed AUTH_403, retryable | `failed` auth_403 | none |
| 37 | Retry ("Try again") on a retryable failure | New job, NEW request_id, same captured input (payload kept until ack) → W | new job; old one acked | `started` again | new reservation (the old one was refunded/never charged) |
| 38 | Failure, not attached | Snackbar "Couldn't create a checklist" · "Try again" (retryable → opens Analyze on the same material with input restored) / "Details" (input problem → opens Analyze in F) | — | `failed` (already) | — |

### 1.4 Re-entry and concurrency

| # | State + event | User sees | Persisted | Analytics | Credits |
|---|---|---|---|---|---|
| 39 | **Second visit through the same door after a success** (the Activity-scoped VM survives) | Fresh form seeded from the route args. Never the old loader. Mechanism: `OnScreenEntered(token)` with a new `rememberSaveable` token resets the form and detaches from the old job | — | — | — |
| 40 | Second visit (any door) while a job runs | Fresh form + strip "1 analysis in progress" (`otherJobsInProgress`). A new analysis is allowed (fork 1); the credit gate subtracts `creditsReservedByRunningJobs` | — | — | reserved shown honestly |
| 41 | Two jobs finish while the user is elsewhere | Snackbars queue one by one (oldest first) | each claimed separately | one `ai_analyze_result_opened` per Open | — |
| 42 | 4th concurrent job (if fork 1 = cap 3) | Dialog "You already have 3 analyses running. Wait for one to finish." | nothing | none | none |
| 43 | Cancel while W(waitingForNetwork) | Back to E with the input kept | Cancelled, payload deleted | `failed` reason `cancelled` (closes the funnel) | none |
| 44 | Back while D | Impossible window: claim + navigate run in one main-thread step | — | — | — |

### 1.5 Not changed (anti-regression)

- Onboarding typed goal (`WelcomeOnboardingViewModel` ~:445) keeps calling `analyzeRepository.analyzeData` in place, foreground, with `surface=onboarding`; CSAT logic untouched. Not a job.
- AI-chat attachments (`FirebaseAiAnalyzerAdapter` → `analyzeData`) stay synchronous; they gain the PDF/downscale/error-code fixes through the repository only.
- Voice: recording UI and permission untouched; the recorded file becomes `StoredFile(AUDIO)`.
- `showResultDialog` / `applyToExistingChecklist` / `createNewChecklist` / `createFill` paths keep working as today.
- Error dialog copy for existing reasons unchanged; `UserNotReady` guard kept in the repository AND checked at enqueue.

## 2. Delivery rule (pure function, unit-tested)

`resolveDelivery(job, attachedJobId, isForeground)`:
- `job.deliveredBy != null` or not terminal → **None**
- `attachedJobId == job.id && isForeground` → **Screen** (the VM claims, then navigates)
- `isForeground` → **InAppHost** (`AnalysisReadySignal.pending` contains it)
- else → **Notification** (processor calls `AnalysisReadyNotifier.notifyFinished`)

Every navigation happens only after `claimDelivery` returned true. The VM collects the job flow in
`viewModelScope` (Activity-scoped), so the attached flag — not the collector's life — gates navigation.

Activation hooks (`onAiChecklistCreated` / `onUserChecklistCreated`) run at **delivery**, not in the
worker: `reminderOptInRequests` is a non-replaying SharedFlow and a worker emission with no UI collector
would burn the one-time opt-in sheet.

## 3. Analytics

| Event | Emitter | Params |
|---|---|---|
| `ai_analyze_started` | VM, on successful enqueue | unchanged (`input_type`, `source`) |
| `ai_analyze_completed` | processor | unchanged + additive `app_state` = `foreground` / `background` |
| `ai_analyze_failed` | processor (or VM for pre-job failures, as today) | unchanged + `app_state`; new `failure_reason` wire values: `input_too_large`, `nothing_found`, `input_unreadable`, `interrupted`, `cancelled` |
| `checklist_created` (source=ai) | processor, at Room write | unchanged |
| `ai_analyze_result_opened` (new, recommended) | ready signal / VM at claim | `via` = `screen` / `in_app` / `notification`, `outcome` = `checklist` / `fill` / `failed` |

`surface` is NOT used for the background dimension: CSAT reads `surface == onboarding` to skip scoring.

## 4. Implementation split by file zone

**@feature-expert IMPLEMENT** (feature/analyze commonMain + App.kt glue)
- `AnalysisJobManager` (scheduler impl), `DataStoreAnalysisJobStore` (one JSON string key), `AnalysisJobProcessorImpl`, `AnalysisReadySignalImpl`, `resolveDelivery`, `AiActionCostProviderImpl`.
- `FirebaseAiService`: `requestId` param, `getCreditsInfo`, `error_code` in `ErrorEnvelopeDto` and both response DTOs, `AiInputType.PDF_BASE64` → `"pdf_base64"`.
- `AnalyzeRepositoryImpl`: PDF bytes as base64, no "Failed to read …" text payloads (typed local error), empty success → NOTHING_FOUND, injectable byte reader for tests.
- `AiAnalyzeError` / `AiFailureReason`: input_too_large (incl. HTTP 413), nothing_found, input_unreadable, interrupted, cancelled; `userFacingError` mapping; strings EN/RU/HI in `core/designsystem` strings.xml (literal apostrophes).
- `normalizeAnalyzeUrl(input): String?` + VM wiring of every `AnalyzeJobIntent`, `aiActionCost` → `Int?`.
- Koin: common bindings in `analyzeFeatureModule`; App.kt: collect `AnalysisReadySignal.pending` into the existing app `SnackbarHostState`, map `ReadyDestination` onto `AppNavigator`, call `resumePending()` once at startup.

**@android-platform-expert** (androidMain, Gradle, manifest)
- `libs.versions.toml`: explicit `androidx.work:work-runtime-ktx` ≥ 2.11.2, `androidx.exifinterface`; `feature/analyze/build.gradle.kts`: `projects.core.datastore.api` (commonMain), work + exifinterface (androidMain).
- `AnalysisWorker` (CoroutineWorker, Koin for the processor, `getForegroundInfo()` mandatory, catch Throwable / rethrow CancellationException, RetryLater → `Result.retry()`, `isLastAttempt` from `runAttemptCount`), `WorkManagerAnalysisJobLauncher` (unique work KEEP per id, expedited `RUN_AS_NON_EXPEDITED_WORK_REQUEST`, CONNECTED, bounded backoff, lazy WorkManager via the existing `Configuration.Provider`; never remove `InitializationProvider`). Pattern: `PushTokenRetryWorker`.
- `FileAnalysisPayloadStore` (`filesDir/analysis_jobs/`), `AndroidImageDownscaler`, `AndroidAnalysisReadyNotifier` (channel with API-O guard, own PendingIntent extra with the job id), `ProcessLifecycleAppForegroundMonitor`, MainActivity extra handling (cold + warm) → `AnalysisReadySignal.onOpen(jobId, NOTIFICATION)`, POST_NOTIFICATIONS request for the screen (reuse the retention opt-in machinery if it fits), the waiting-for-network flag from WorkInfo.
- iOS stubs: pass-through downscaler, coroutine launcher, no-op notifier, always-foreground monitor.

**@wasmjs-expert**
- `WasmImageDownscaler` (createImageBitmap `from-image` + resize, canvas `toBlob` JPEG), in-memory payload store, app-scope coroutine launcher, `visibilitychange` foreground monitor, no-op notifier, `beforeunload` prompt while a job is Running; verify on :9090 (photo, PDF, link, leave screen, return).

**@compose-expert** (after `@design-expert` DESIGN_SPEC for the new states)
- Waiting state (copy, "Continue in background", "Cancel" only when waiting for network), Failed state with "Try again" / "Change input", in-progress strip, inline URL error, cost row with unknown cost, snackbar copy; screen sends `OnScreenEntered(rememberSaveable token)` / `OnScreenLeft` (DisposableEffect). Screenshot cycle.

## 5. Open product forks (owner)

1. **Parallel analyses.** (a) allow, up to 3 running at once **(Recommended)** — matches "leave and start another"; credits shown net of running jobs. (b) one at a time — simpler, but the second door shows "wait" for up to a minute. (c) unlimited — the server rejects the extras with 402, a burst of failure notifications.
2. **Waiting screen.** (a) explicit "Continue in background" button + one-line promise **(Recommended)** — users do not guess that Back is safe after years of "don't leave". (b) text only.
3. **Notification permission (Android 13+).** (a) ask once at the first analysis start, analysis runs regardless **(Recommended)**. (b) never ask, in-app snackbar only on return.
4. **Fill mode in background.** (a) same job model, delivered as "Review" (snackbar / notification) to the editable preview **(Recommended)** — nothing is written until the user confirms, as today. (b) keep fill foreground-only (Back loses a paid result).
5. **Finished while the user is on another screen.** (a) snackbar "ready · Open", no auto-navigation **(Recommended)**. (b) silent, only the new row in the list.
6. **Web file job interrupted by reload.** (a) accept "interrupted, pick the file again" for v1 **(Recommended)** — rare, the tab gets a `beforeunload` prompt. (b) persist bytes in OPFS and re-run with the same id (no double charge) — more web work while the web is moving to React.

Not a fork (decided by rule): cost unknown → button not gated; retry never reuses an input the server rejected as input.

## 6. Red-repro and new-behaviour tests (written in IMPLEMENT)

Red on today's code (assert, not compile):
1. `AnalyzeViewModelTest.reEntryAfterSuccessfulCreate_showsTheForm_notTheLoader` — success → `OnScreenEntered("t2")` → `jobPhase == Editing && !isAnalyzing`. Today `isAnalyzing` stays true.
2. `AnalyzeViewModelTest.backDuringAnalysis_thenSuccess_neverNavigates` — repo suspended, `OnBackClick` + `OnScreenLeft`, complete → no `navigateToChecklistDetail`. Today it navigates with `clearBackStack`.
3. `AnalyzeViewModelTest.actionCost_isTheServerValue` — provider serves 20, then a second run with 35: `aiActionCost` equals the served value, and 20 credits enable the button at cost 20. Today 30.
4. `AnalyzeRepositoryTest.pdf_isSentAsPdfBase64OfTheFileBytes` — fake service captures `inputType == PDF_BASE64` and `inputData == base64(bytes)`. Today TEXT `"PDF content from: …"`.
5. `AnalyzeViewModelTest.invalidUrl_isRejectedBeforeAnyRequest` — `hello world` → `inputError` set, repo never called, no `started`. Today the repo is called.
6. `AnalyzeRepositoryTest.unreadablePhoto_failsLocally_withoutARequest` — today sends `"Failed to read image: …"` as TEXT.
7. `AnalyzeRepositoryTest.emptySuccess_isNothingFound_notAnEmptyChecklist` (moved to the repository in IMPLEMENT) — success with 0 items → no `createChecklistFromResult`, NOTHING_FOUND message. Today an empty checklist is created.

Green with the implementation (new behaviour):
8. `AiFailureReasonTest`: `error_code=input_too_large` and HTTP 413 → INPUT_TOO_LARGE; `nothing_found` → NOTHING_FOUND.
9. `AiInputTypeWireTest`: PDF_BASE64 → `"pdf_base64"` (wire as an enum property, testable without Ktor).
10. `NormalizeAnalyzeUrlTest`: `example.com/a` → `https://example.com/a`; `https://x.y/z` kept; `hello world`, `ftp://x.y`, `abc` → null.
11. `AnalysisJobProcessorTest`: every attempt sends the SAME request id; a second `process` of a Succeeded job writes no second checklist; RetryLater keeps the id; last attempt writes Failed.
12. `AnalysisJobManagerTest.claimDelivery_hasExactlyOneWinner`.
13. `ResolveDeliveryTest`: the four branches of §2.
