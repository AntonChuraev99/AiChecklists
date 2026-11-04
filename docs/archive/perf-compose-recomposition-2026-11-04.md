# Compose recomposition + stability — lag fixes

**Статус:** Done
**Дата старта:** 2026-11-04
**Start SHA:** c7e81dd888c61023a793461e5d6616e44d3ba200
**Project:** Checklists
**Тип:** refactor
**Затронутые модули:** feature/home, composeApp, feature/aichat/impl, all Compose modules (build config)

## Цель (продуктовая)

The owner sees lag in the app (observed on a debug build on Pixel 9). Remove the recomposition hot spots the audit found and make domain models stable for the Compose compiler, so scrolling, typing and keyboard transitions stop re-running whole screens.

## Технический план

Audit (2026-11-04, read-only): Pixel 9 `dumpsys gfxinfo` on the debuggable build — GPU p99 4 ms, UI-thread p95/p99 22/48 ms → CPU-side (composition/layout), not draw (источник: `adb shell dumpsys gfxinfo com.antonchuraev.aichecklists`).

1. Stability config file + compose compiler reports behind `-PcomposeReports` (all Compose modules) — @android-platform-expert. Reports before/after.
2. Compose fixes — @compose-expert:
   - `ChecklistDetailScreen.kt:1003` infinite wiggle transition runs always, value passed as Float to every row → gate on edit mode, pass `() -> Float`, read in `graphicsLayer`. Same at `MainScreenContent.kt:192`.
   - `ChecklistDetailScreen.kt:1312-1315` IME inset read in composition + `remember { derivedStateOf { imeBottom > navBottom + 8.dp } }` freezes on first value (bug). Also `MainScreen.kt:438`, `V2ChatDockOverlay.kt:397`, `ChatScreen.kt:479` (`imeBottom` as `LaunchedEffect` key).
   - `App.kt:636` whole `ChatScreenState` read at App root → every chat keystroke recomposes the app; `entryProvider` (`:1919`) not remembered.
   - `ChecklistDetailScreen.kt:1026` scrim alpha read in composition → `drawBehind`.
3. Measure before/after: same debug build type, `gfxinfo reset` + owner runs the scenario.

4. (added 2026-11-04 after the Perfetto iteration, owner chose «both in this branch») Per-frame `measureAndLayout` in V2 Ask Gisti dock / item sheet animations — @compose-expert; Baseline + Startup Profile module `:baselineprofile` — @android-platform-expert (isolated worktree, patch applied here, generation on the emulator).

Out of scope (deferred): moving `pendingItemInput` / Inbox draft out of `UiState` (contract change, @feature-expert); R8 keep narrowing (`docs/active/r8-keep-rules-too-broad-2026-07-23.md` (не проверено — untracked file in the main checkout, not in git)).

Trace window labels used below (`item_sheet`, `ask_gisti`, `chip_inbox`, `capture_dock`) and trace names (`perf_warm`, `perf_layoutfix`, `perf_round2`, `perf_final`) are session scratchpad artefacts, not repository identifiers (не проверено — not in the repository by design).

## Лог итераций

### Итерация 0 — 2026-11-04 — main: baseline measurement → captured
Debug APK of unmodified master (c7e81dd8), Pixel 9, fresh process, owner ran the 5-step scenario (open checklist 10+ items, idle 5 s, scroll, add-item keyboard + type, Ask Gisti type). Total frames 2648, janky 111 (4.19%), legacy janky 550 (20.77%), UI p50/p90/p95/p99 = 7/14/20/44 ms, Slow UI thread 111, GPU p99 4 ms (источник: `adb shell dumpsys gfxinfo com.antonchuraev.aichecklists`).

### Итерация 1 — 2026-11-04 — android-platform-expert: root `subprojects {}` stability config + `-PcomposeReports` → done
`compose_compiler_config.conf` lists read-only collections (`List<*>`, `Set<*>`, `Collection<*>`, `Map<*,*>` — not `kotlin.collections.*`, which would also mark `MutableList` stable) + checklist / aichat.api / user domain-model packages. Two build traps closed: (1) the compose compiler names report files after the KMP module name `AIChecklists.feature:home`, and `:` is an invalid Windows file name → `IrGenerationExtensionException: Invalid file path`; reports-only `moduleName` `:`→`_`. (2) KGP passes the stability path as an untracked compiler option → editing the conf left compile tasks UP-TO-DATE; tracked via `inputs.file`. unknownStableArguments feature:home 206→159, aichat:impl 33→28 (unstable params 8→0), composeApp 91→82 (источник: compose_metrics `*-module.json` before/after, agent report). `ChecklistFillItem`, `Checklist`, `ChatScreenState`, `InboxTask` → stable.

### Итерация 2 — 2026-11-04 — compose-expert: 4 recomposition fixes → done, builds green
`rememberEditModeWiggle(active): () -> Float` (no infinite transition outside edit mode; rows read it in `graphicsLayer`); `rememberImeOpenState` reads insets inside `derivedStateOf` (fixes the frozen `imeVisible`; red→green `ImeOpenStateTest`, 2 tests failed on the old logic extracted verbatim); `ChatMessageList` scroll effect via `snapshotFlow` instead of `imeBottom` as an effect key; `App.kt` keeps the chat `State` and reads narrow `derivedStateOf` fields at root; scrims draw via `drawBehind`. `entryProvider` deliberately NOT remembered — entries capture dozens of root values and would freeze. Pre-existing failure, not from this task: `CaptureDockShoulderTest` 2 of 8 fail on pristine master code too (checked by reverting both the build file and the Compose diff).

### Итерация 3 — 2026-11-04 — main: after measurement → idle frames halved, slow frames unchanged
Same scenario, same debug build type, fresh process after install. Total frames 2648→1390, Slow UI thread 111→111, janky 4.19%→8.06%, UI p50/p90/p99 7/14/44→6/20/121 ms, High input latency 3098→1297 (источник: `adb shell dumpsys gfxinfo com.antonchuraev.aichecklists`). Reading: the always-on wiggle produced ~1250 cheap idle frames (gone = fix confirmed); the absolute number of slow frames did not move, so the jank the owner feels is NOT in the fixed recomposition paths — percentages got worse only because the cheap frames no longer dilute the denominator. Hypothesis (not yet verified): slow frames = first composition of heavy screens / transitions, interpreted code in a freshly installed debuggable APK. Next evidence needed: Perfetto trace of the slow frames and/or a non-debuggable build.

### Итерация 4 — 2026-11-04 — main: Perfetto on emulator (owner allowed driving the emulator) → jank located
Emulator Pixel_9 AVD API 36, same debug APK. Note: the emulator and the owner's device run the V2 shell, where a checklist opens as an Inbox project page — most of iteration 2 targeted the v1 `ChecklistDetailScreen`, which explains the unchanged slow-frame count. Findings (источник: trace_processor over `perf_after` / `perf_warm` traces, scratchpad):
- Idle and list scroll: no frame over 40 ms (источник: trace_processor q2 over `perf_after`). The lag is on OPENING things: cold start (first frame 1618 ms, источник: q2), item sheet, project chip switch, Ask Gisti dock, tabs.
- Same action ×3 in one process: worst frame item_sheet 504→120→94 ms, ask_gisti 282→95→108, chip_inbox 245→128→79 (источник: trace_processor q4 over `perf_warm`) — the first open pays JIT/class-load cost (Baseline Profile / release AOT territory).
- But frames over 16.7 ms per open stay flat (item_sheet 15/16/15, ask_gisti 25/25/26; источник: q4 over `perf_warm`): on warm animation frames recompose is 0–6 ms while `AndroidOwner:measureAndLayout` is 8–25 ms per frame → the dock-expand and sheet animations re-measure a large tree every frame. Recomposition is no longer the bottleneck; layout during animation is.
Caveat: emulator + debuggable build inflates absolute ms; ratios and phase shares are the evidence, not the numbers.

### Итерация 5 — 2026-11-04 — compose-expert: per-frame layout round 1 → ask/sheet over-budget frames cut
F1: `AppScaffold` always pads the (empty, `captureDockRenders=false`) Inbox `bottomBar` slot with `ime ∪ navigationBars`, the shell consumed only the plate height → every keyboard frame shrank the content slot and re-measured the whole Inbox tab under the modal scrim. Fix in `V2ShellCompactBar`: `consumeWindowInsets(WindowInsets.ime)` on the content box while the chat owns the keyboard (held until ime == 0 or 500 ms). F2: `BoxWithConstraints` around the Inbox LazyColumn re-subcomposed the list on every constraint change → plain Box + `Modifier.layout`-stored max height for the empty state (inbox_empty_* snapshots byte-identical). Trace `perf_layoutfix`, warm passes 2/3: Ask Gisti frames >16.7 ms 25/26 → 10/9, over-budget 644/667 → 303/232 ms; item sheet 16/15 → 6/1, 478/351 → 192/73 ms (источник: trace_processor q6 over `perf_warm` vs `perf_layoutfix`). Temp sections: `Gisti:measure:ShellContent` 4× during Ask Gisti (was per frame); remaining: `Gisti:measure:ChatDock` 60× and `Gisti:measure:InboxContent` 20× during the item sheet → round 2.

### Итерация 6 — 2026-11-04 — android-platform-expert (isolated worktree): Baseline Profile infra → patch applied
Module `:baselineprofile` (`com.android.test` + `androidx.baselineprofile` 1.5.0), consumer in `:androidApp` (`automaticGenerationDuringBuild = false`, `dexLayoutOptimization = true`), explicit `profileinstaller` 1.4.1; `nonMinifiedRelease` / `benchmarkRelease` declared by hand so `initWith(release)` does not leak the release key and prod Amplitude key into them (debug signing, debug Amplitude key, no Crashlytics mapping upload). Generator + StartupBenchmark + OpenActionsBenchmark use text/content-desc selectors only.

### Итерация 7 — 2026-11-04 — compose-expert round 2 + round-2 trace → item-panel root found
Probe trap: the first temporary layout-trace probe (deleted in round 3) built a new `Modifier.layout {}` lambda per call, so every recomposition replaced the modifier and forced a re-measure — the probe inflated the counts it was measuring; rewritten as a `Modifier.Node` with name-based equality. Fixed: `dockHeightPx` no longer read in composition (`rawDockHeightPx` + publish after 2 stable frames) → the full-chat overlay and its `LaunchedEffect(updateAnchors)` stop restarting per frame. Trace: a task-row tap in the V2 Inbox does NOT open `ItemDetailsSheet` in a separate window — a panel in the Inbox `AppScaffold` bottomBar slot grows each frame (`Gisti:size:ScaffoldBottomBar` 24 distinct heights 532→1064 px), shrinking the content slot (InboxContent 1687→1155 px) → the page + LazyColumn re-measured 26× per open while `compose:InboxScreen` ran once (источник: trace_processor q8 over `perf_round2`). Ask Gisti: content slot stable (bottomBar 0x0), only the dock's own growth remains (inherent to the keyboard-capped answer design).

### Итерация 8 — 2026-11-04 — compose-expert round 3 + main: scenario correction + final trace
Scenario mislabel found by screenshots: after a cold start the app opens on the EMPTY Inbox page, whose chip row sits at y≈459 (not 554 as on a two-line project title), so the old "item_sheet" tap hit the empty-state "Add task" button → `QuickCaptureDock` + keyboard, not `ItemDetailsSheet`. Round 3 therefore fixed the right thing for the measured flow: `holdContentHeightWhileSlotMoves` / `rememberSettlingSlotHeight` (InboxScreen.kt) measure the page at a held height while the bottomBar slot moves and snap once after 2 still frames; all temporary trace code removed. Final scenario (coordinates verified by screenshots) adds the real item sheet. Warm passes 2/3 vs `perf_warm`: capture dock frames >16.7 ms 16/15 → 5/5, ask_gisti 25/26 → 11/6; real item sheet 2/2 frames, one ~113 ms first-composition frame per open; scroll 0/1 (источник: trace_processor `q_final` over `perf_final`, `q4` over `perf_warm`).

### Итерация 9 — 2026-11-04 — main: Baseline Profile generation on the emulator → journeys fixed in 2 rounds
First background run was reaped by Claude Code under low RAM (1.2 GB free of 15.6, источник: `Win32_OperatingSystem` FreePhysicalMemory); after the owner freed memory: pre-build without the emulator, `gradlew --stop`, emulator with `-memory 2048`, generation with `ANDROID_SERIAL=emulator-5554` (источник: adb / AGP connected-test device selection env var) so the owner's connected Pixel 9 is never driven (verified: no instrumentation process on the Pixel). Generator log showed the open-actions journey silently degraded (steps are best-effort): after a cold start the app sits on the EMPTY Inbox; the wide "Add task" button was taken for a task row → capture dock + keyboard stayed open over the bottom bar → "Ask pill not found", every tab "not found". Fixed in `GistiJourneys.kt`: explicit capture-dock step, "Add task" excluded from task rows, task sheet verified by its "Reminder" row. Second run: chip row still "not found" — V2 chips are exposed neither as scrollable nor as clickable nodes → selector switched to text nodes in the band under the "N tasks" subtitle.

### Итерация 10 — 2026-11-04 — main: root of the degraded journey = fresh install → profile complete
The connected-test run installs a clean APK and uninstalls it afterwards, so the generator always meets a FRESH user: onboarding → empty Inbox, no projects (hence "chip row not found" — there is no second chip). Generator now seeds one local task through the capture dock (Room write; no network, no credits). Two more traps found by hand on the emulator: ENTER in the capture field inserts a newline (multi-line field), and the round "+" submit button has NO contentDescription (found by geometry; also an a11y defect — TalkBack cannot name it); V2 Inbox task rows do not expose their text to UiAutomator → task row tapped by geometry under the subtitle, sheet verified by its "Reminder" row. Final profile: 7476 app rules in baseline-prof.txt, 5542 in startup-prof.txt; `ItemDetailsSheet`, `V2ChatDockOverlay`, `QuickCaptureDock`, `CalendarScreen` present (источник: `grep -c` over `androidApp/src/release/generated/baselineProfiles/`). `bundleRelease`: `BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof` 30171 B vs 19739 B library-only before (источник: `unzip -l` of the AAB; the before figure from the read-only audit of the 17.09 AAB).

## Выводы

- The jank the owner felt was NOT steady-state recomposition. Idle and scroll were clean; jank sat on opening things and had two roots: JIT on the first open (worst frame 3–5× worse than the repeat) and per-frame `measureAndLayout` while a bottom slot animated and shrank the `AppScaffold` content slot.
- Shipped: recomposition fixes (wiggle only in edit mode, IME reads inside `derivedStateOf` incl. the frozen-`imeVisible` bug, narrow chat-state reads in `App.kt`, scrim in `drawBehind`), stability config, per-frame-layout fixes in the V2 shell / Inbox capture dock / chat dock, Baseline + Startup Profile module with generated profiles (7476 app rules, источник: `grep -c` over `baseline-prof.txt`).
- Measured on the emulator, warm passes 2/3 vs before: capture dock frames >16.7 ms 16/15 → 5/5, Ask Gisti 25/26 → 11/6 (источник: Perfetto `perf_warm` vs `perf_final`). Absolute ms on an emulator + debuggable build are inflated; the owner's real-device check on a release build is still pending.
- Left as is (by design): the Ask Gisti dock itself still re-measures each keyboard frame — the answer height is capped to the space above the keyboard; the real item sheet costs one heavy first-composition frame per open.
- Not done (out of scope): moving `pendingItemInput` / Inbox draft out of `UiState`; R8 keep narrowing (`docs/active/r8-keep-rules-too-broad-2026-07-23.md` (не проверено — untracked file in the main checkout, not in git)); the capture-dock "+" button has no `contentDescription` (a11y).
- Profile regeneration: `ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:generateReleaseBaselineProfile -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile` (источник: this session's runs; the env var keeps a connected physical phone out of the run). Check the generator log with `adb logcat -s GistiJourney` — every journey step is best-effort and only logs a miss.

## Предложения по улучшению агентов

- @android-platform-expert: a Baseline Profile generator must assume a FRESH install (the connected-test run installs and uninstalls the APK) — onboarding, empty data; seed local data in the journey and verify each step, never trust a green run of best-effort steps.
- @compose-expert: a temporary layout probe built as `Modifier.layout {}` re-measures its node on every recomposition and inflates the counts it measures — build probes as `Modifier.Node` with value equality from the start.
- @compose-expert / scenario authors: verify every scripted tap by a screenshot before trusting a trace window label — the first traces labelled a capture-dock open as "item sheet" because the chip row sits at a different y on the empty Inbox.
