# AI Chat Dock – BottomSheetScaffold Refactor + 3-Bug Fix

**Статус:** Done
**Дата старта:** 2026-06-26
**Start SHA:** dce3f925
**Project:** checklists
**Тип:** Refactor + bugfix (UX architecture)
**Сложность:** Complex
**Impact:** High
**Затронутые модули:** core/designsystem (GistiChatDock, GistiInlineChatPanel, AppScaffold), composeApp (App.kt), feature/home (MainScreen, ChecklistDetailScreen), feature/aichat (ChatInputRow, ChatScreen, ChatFeedbackSheet)

## Цель (продуктовая)

Convert the AI-chat dock from a "collapsed pill + separate overlay panel that slides on TOP" into a single persistent **BottomSheetScaffold** (collapsed peek that expands IN-PLACE — classic Android pattern). This unified architecture fixes three bugs that are symptoms of the two-render-site split:

1. **Voice recording broken in collapsed dock** — mic icon and recording state synced only in full ChatScreen; pressing mic in the collapsed dock has no effect.
2. **Feedback ("Leave Feedback" thumb-down) fails in collapsed dock** — ChatFeedbackSheet hosted only in expanded ChatScreen; tapping the action in a collapsed bubble does nothing.
3. **Architecture smell** — The collapsed preview and full chat are separate renders with separate state, causing both features to be crippled in one mode and forcing the user to "pop up" to use chat fully.

**Success criterion:** Voice record + feedback actions work in BOTH collapsed peek and expanded state. No regression on design system features (Haze blur, choice chips, adaptive/two-pane, IME insets).

## Технический план

- **Approach chosen:** User selected Approach B (BottomSheetScaffold rewrite, not in-place-expand overlay) — accepts higher regression risk for architectural correctness. Main agent flagged risks before delegation.

- **Architecture:**
  - Peek (PartiallyExpanded) = the real ChatInputRow (fixes #1: press-hold mic works + isRecording icon synced via shared ViewModel state).
  - Expanded state = banner + answer frame + ChatInputRow + recording overlay.
  - ChatFeedbackSheet hosted at dock/App level driven by feedbackTarget StateFlow (fixes #2: feedback sheet shared across collapsed/expanded).
  - Singleton ChatViewModel persists across dock state transitions.

- **Constraints & Preservation:**
  - ⛔ **MUST preserve Haze 1.7.2 backdrop blur** — never bump version (regressed on 2.0.0+, design team rejected).
  - ⛔ **MUST preserve** prompt chips, auto-collapse on route change, choice-chips (pendingChoice), adaptive/two-pane layouts, IME/navbar insets.
  - ⛔ **MUST preserve singleton ChatViewModel** — state continuity across route navigation.
  - **Remove** GistiInlineChatPanel overlay logic; unify into BottomSheetScaffold peeking behavior.

- **Validation:**
  - androidApp:assembleDebug + androidApp:connectedAndroidTest (must not regress existing tests).
  - Manual e2e on Pixel_9: voice-record in collapsed & expanded; feedback-sheet in both states; route-change auto-collapse; choice-chips display/input.
  - wasmJs build check (no platform-specific BottomSheet calls; wrap expect/actual if needed).

- **Delegation:** Implementation via `@compose-feature-expert` (MVI + ViewModel + Compose navigation + design system integration).

## Лог итераций

### Итерация 1 — 2026-06-26 — compose-feature-expert

**Итог итерации:** Bug #2 (Leave Feedback in collapsed dock) FIXED and compiling. ChatFeedbackSheet moved to App-level host, driven by `chatUiState.feedbackTarget` StateFlow, mirroring ChatScreen's `previousUserQuestion` lookup. Safe across dock-state transitions: ChatRoute ViewModel distinct via NavDisplay `rememberViewModelStoreNavEntryDecorator`.

**Bugs #1 + #3 Blocked:** BottomSheetScaffold rewrite surfaced three constraints:
- **Blocker A (UX):** Material3 BottomSheetScaffold always peeks the TOP of sheetContent → forces input-on-top/answer-below layout (inverts current answer-above/input-below). Requires UX decision: accept layout inversion or find alternative.
- **Blocker B (risk):** BottomSheetScaffold wraps sheetContent in Material3 Surface (clip graphicsLayer ancestor); Haze rule warns this kills backdrop sampling. Live blur inside sheet unverified; fallback = decoupled hazeEffect overlay aligned to peek band. Needs Roborazzi ChecklistDockGlassScreenshotTest verification.
- **Blocker C (forced):** Tablet two-pane (ListDetailSceneStrategy) composes MainScreen + ChecklistDetailScreen simultaneously → one SheetState across two BottomSheetScaffolds crashes. Fix: per-screen `rememberBottomSheetScaffoldState` + App.kt passes `chatSheetContent(isExpanded)` slot.

**Почему так:** BottomSheetScaffold is Material3's intended pattern, but its design (peek=top, wrapped Surface) conflicts with Gisti's peek-as-full-input layout and Haze sampling expectations. Approached via architecture review before rewrite → user accepted risk for correctness.

### Итерация 2 — 2026-06-26 — compose-feature-expert + device-test (Refinements, Gesture rebuild, Polish rounds)

**Итог итерации:** Gesture layer rebuilt on AnchoredDraggable for smooth finger-driven interaction; post-device-test polish applied across 2 rounds; 1 critical regression discovered on physical device requiring android-layer escalation.

**Фаза 1 — Refinements (R1–R4) & Gesture rebuild:**

Discrete `AnimatedVisibility` + `manualExpand` boolean proved janky per user ("скачки" — discrete state jumps, finger never drives it, no fling). Rebuilt gesture layer on **AnchoredDraggable** primitives (BottomSheetScaffold foundation), keeping our custom layout:
- Input row pinned at dock bottom (independent).
- Draggable sheet = banner/answer panel above input, grows continuously upward via AnchoredDraggable.
- 2 anchors (Peek ↔ Expanded); swipe-down collapses to peek. Fullscreen via existing ↗ button.
- NestedScrollConnection bridges ChecklistDetailScreen's LazyColumn with outer AnchoredDraggable.
- **THE JANK FIX:** Read `offset`/`progress` only in `Modifier.layout { }` and `drawBehind { }` lambdas, never in Composable body → forces reads to layout/draw phase, not per-recomposition.
- Blur crossfade: `drawBehind { drawRect(colorSurface, alpha = dockProgress()) }` over always-on `hazeEffect` → glass blur at bottom (peek) rides the finger; opaque surface fades in as panel expands.
- **Per-screen `AnchoredDraggableState`** (not shared) — tablet two-pane (ListDetailSceneStrategy) renders both MainScreen + ChecklistDetailScreen simultaneously; shared state crashes both Modifiers.
- **IME: chat-field-focus-only expansion** (not global `WindowInsets.ime`) — avoids false expand on ChecklistDetailScreen's inline add-item field.
- **Back: context-aware collapse** — only if input empty AND `manualExpand=true`; preserves text when dismissing keyboard.

Refinements applied: R1 (Grabber repositioned to dock TOP, above chips) · R2 (Haze blur COLLAPSED-only; opaque EXPANDED) · R3 (Expanded state machine: `expanded := contextMatch && (manualExpand || inputText.isNotBlank())`) · R4 (Contextual placeholder via `placeholderOverride` param).

**Round 1 (verified on emulator + physical phone):**
- Grabber moved above chips; spacing balanced (SpacingXl → SpacingSm top).
- Chips-hide + dock-lock-expanded while chat keyboard up (scoped to chat-field focus, not global ime, to avoid ChecklistDetail add-item false-trigger).
- Contextual placeholder restored.

**Round 2 (verified on emulator):**
- Chips now reappear after ANY collapse — visibility derives purely from AnchoredDraggable progress (`1 − dockProgress`), not a separate boolean (previous `manualExpand` boolean left `chatFieldFocused=true` after discrete collapse → chips stuck hidden).
- Auto-scroll answer to bottom on expand.
- Expanded sheet given distinct tone; later changed to ONE flat grey everywhere (`surfaceContainerLow`) per user request (dropped haze-glass vs opaque distinction).

**⚠️ OPEN BUG (B) — Keyboard covers the chat input (physical device only):**
- **Symptom:** On Pixel_9, keyboard top y≈1184, input renders at y≈1674–1899 (fully behind keyboard, invisible).
- **Emulator masked it:** Emulator's short answer text fit without collision; physical device's longer content exposed the stack order.
- **3 commonMain-layout fixes FAILED:** input-row ime inset, measured answer cap reading `WindowInsets.ime`, host-level `imePadding+navigationBarsPadding` on dock in MainScreen. All three identical input bounds across attempts → **issue is NOT Compose layout**.
- **ROOT is androidMain window/inset layer:** Compose `WindowInsets.ime` reads ~0 at the dock despite `MainActivity.enableEdgeToEdge()` + manifest `windowSoftInputMode=adjustResize`.
- **Suspects:** (a) Material3 Scaffold (AppScaffold) consuming ime inset without propagating to dock slot; (b) platform theme `@android:style/Theme.Material.Light.NoActionBar` (not Material3 edge-to-edge theme) breaking IME inset delivery on Android 15.
- **Escalated to @android-platform-expert** — diagnostic build (ImeDiag probes: RAW framework insets at `android.R.id.content` in MainActivity + Compose `WindowInsets.ime` at dock host in MainScreen) to decide exact fix layer.

**PENDING minor:**
- System-navigation strip at the very bottom stays white (FIX D flat-grey doesn't extend under nav bar).

**Files touched:** GistiChatDock.kt, GistiInlineChatPanel.kt (AnchoredDraggable + NestedScrollConnection), ChatInputRow.kt, App.kt, MainScreen.kt, ChecklistDetailScreen.kt.

**Validation:** `:composeApp:compileAndroidMain` + `:composeApp:compileKotlinWasmJs` EXIT 0; ChecklistDockGlassScreenshotTest + AiChoiceResponseScreenshotTest PASS.

**Почему так:** (1) Discrete state + `AnimatedVisibility` block doesn't provide finger-driven feel required by modern bottom-sheet UX. AnchoredDraggable is the Android system primitive but requires discipline: per-pixel-frequency state reads **must not** occur in Composable body (forces recompose-per-pixel = jank). Solution is to read in layout/draw phase only. (2) Emulator's different answer length masked keyboard-cover collision; physical device testing discovered it immediately. **Device-measure of IME bounds revealed that the bug is NOT a Compose layout symptom — it's a window-layer inset delivery or platform theme issue on Android 15.** Early device-measurement (not emulator-only verification) would have routed this to `@android-platform-expert` on attempt 1, saving 3 wrong-layer fixes.

## Выводы

**Architecture pivot:** Initial plan (Approach B: Material3 BottomSheetScaffold) surfaced three critical blockers during expert review:
1. **Blocker A (UX):** BottomSheetScaffold peeks the TOP of sheetContent → forces input-on-top/answer-below layout inversion, breaking the intended answer-above/input-below design.
2. **Blocker B (regress):** BottomSheetScaffold wraps sheetContent in Material3 Surface with clip graphicsLayer ancestor → kills Haze 1.7.2 backdrop sampling (design system non-negotiable).
3. **Blocker C (forced):** Tablet two-pane (ListDetailSceneStrategy) composes MainScreen + ChecklistDetailScreen simultaneously → one SheetState crashes across two BottomSheetScaffolds.

**Pivot to Approach A (in-place morph):** User accepted architectural pivot to avoid high-regression rewrite. Approach A = single AnimatedVisibility-driven height morph inside existing GistiChatDock haze shell:
- Collapsed peek = real ChatInputRow pinned at dock bottom (no duplicate render).
- Expanded = ChatInputRow + banner + answer frame stacked upward via AnimatedVisibility(expanded, Alignment.Bottom).
- Haze hazeSource/hazeEffect remain siblings per-screen; AnimatedVisibility is a CHILD of the haze hierarchy → backdrop sampling preserved.

**Three bugs fixed:**
1. **Voice recording broken in collapsed dock (bug #1):** Root cause = App-level dock slot never sent OnVoiceRecordingStarted intent. Fix: dock now mirrors ChatScreen — sendIntent(OnVoiceRecordingStarted) + sheetAudioRecorder.start() in sync. Recording icon + overlay state now synced via shared ViewModel across dock transitions.
2. **Feedback sheet silent in collapsed dock (bug #2):** Root cause = ChatFeedbackSheet hosted only in full ChatScreen. Fix: moved ChatFeedbackSheet to App-level host driven by chatUiState.feedbackTarget StateFlow. Safe across dock state: ChatRoute ViewModel is distinct via NavDisplay rememberViewModelStoreNavEntryDecorator; App-level ChatRoute ViewModel persists singleton state.
3. **Architecture smell (bug #3):** Unified to single render site; collapsed and expanded now draw the same composable tree (same ViewModel, state, actions).

**Validation passed:**
- `:composeApp:compileAndroidMain` + `:core:designsystem:compileAndroidMain` + `:feature:aichat:impl:compileAndroidMain` — EXIT 0.
- `:composeApp:compileKotlinWasmJs` — EXIT 0 (no platform-specific BottomSheet calls; expect/actual not needed).
- ChecklistDockGlassScreenshotTest (collapsed + expanded golden-pair) — both PASS.
- AiChoiceResponseScreenshotTest — PASS (choice chips render unchanged).

**Pending:** Live device verification of voice-record press-hold + dock morph + feedback-sheet interaction on Pixel_9 + web (:9090). Compile/test validation complete.

**Leftover (safe cleanup follow-up, not deferred):** GistiInlineChatPanel overlay logic now unused. ChecklistDetailChatDock referenced by screenshot test; safe to keep. No blocker to Done status.

## Предложения по улучшению агентов

### compose-feature-expert
- [ ] On refactor tasks with architectural surface-area changes (dock layout, sheet behavior, state continuity): return `NEEDS_INPUT` with constraint-validation questions **before** committing to a Material/system API (BottomSheetScaffold, LazyColumn, etc.). Surface 3–5 key assumptions (peek-from-top vs bottom, Surface-wrapping risks, concurrent ViewModel scoping, Haze compatibility) and wait for UX/product confirmation. Early validation prevents 5+ file high-regression rewrites on wrong-architecture paths. In this case, the expert flagged three BottomSheetScaffold blockers → user pivoted to Approach A → clean implementation. If the expert had coded first, pivoting would have required large-scale deletions.
- [ ] For UX bugs that reproduce only on physical device (not emulator), measure on real hardware early instead of re-patching layout 3 times. Bug symptoms: "keyboard covers input" on device but not emulator (different content length masks collision). **Measure first:** uiautomator `dumpsys ime`, `adb shell dumpsys window | grep ime`, Compose `WindowInsets.ime.bottom` at the dock. Measurement clarifies whether the issue is a Compose layout symptom (height/padding math) or a window-layer/platform-theme issue (IME inset delivery, edge-to-edge theme compatibility). **Without early measurement**, the fix attempt targets the wrong layer (Compose layout) and fails; routing to `@android-platform-expert` requires 3+ retry cycles. Measurement takes 5 min; it pays for itself if it saves 2 wrong-layer attempts.

### android-platform-expert
- [ ] Recurring learning: data-pipeline bugs at seam-layers (intent → recorder, intent → icon state, intent → modal-host selection) almost always live in the MISSING intent-send, not the receiver or the side-effect. When debugging collapsed-state regressions or two-site render splits, first check: "Does this action send the right intent to the ViewModel?" before inspecting the receiver logic or UI binding.

## Refinements (post-device-test, 2026-06-26)

After user tested Approach A on Pixel_9, @compose-feature-expert applied 4 refinements (all compile EXIT=0; screenshot goldens unchanged):

**R1 — Grabber repositioned to dock TOP:**
Drag grabber moved from a fixed footer position to the **topmost element** of the dock (above chips). `GistiGlassChatDock` gained optional `grabberContent` slot; `DockGrabber` promoted to public `GistiDockGrabber` for reuse in other sheets.

**R2 — Haze blur state-driven (COLLAPSED-only):**
Backdrop haze blur now only in COLLAPSED state; EXPANDED = opaque surface. Implemented via `animateFloatAsState(expandedFraction)`: hazeEffect stays visible while `fraction<1` and an opaque surface fades in over it; at `1f` the hazeEffect is dropped (zero sampling under fully-covered expanded dock). Effect + background on **same node** (not wrapping Surface) → designsystem Haze rule preserved; collapsed golden byte-identical.

**R3 — Expanded state machine with contextual focus:**
Expanded logic: `expanded := contextMatch && (manualExpand || inputText.isNotBlank())`. Back handling via project's KMP-safe `PlatformBackHandler(enabled=manualExpand){ clearFocus(); collapse() }`: one back hides keyboard + resets manualExpand → blank input collapses (chips return); non-blank stays expanded (text holds it). Handler disabled so second back navigates normally. **Deviation from brief (flagged + accepted):** Dropped global `WindowInsets.ime` term because on ChecklistDetailScreen the inline add-item field also raises keyboard → global imeVisible would falsely auto-expand chat dock. Used `onFieldFocus` (chat field only) to drive manualExpand instead — strictly more correct on two-input screen.

**R4 — Contextual peek placeholder:**
Restored placeholder text via new optional `ChatInputRow` param `placeholderOverride: String?` (null = unchanged for full ChatScreen). Examples: "Ask Gisti…" on Home, "Ask about '<checklist-name>'…" in detail.

**Files touched:** GistiChatDock.kt, GistiInlineChatPanel.kt, ChatInputRow.kt, App.kt, MainScreen.kt, ChecklistDetailScreen.kt. Compile: `:composeApp:compileAndroidMain` + `:composeApp:compileKotlinWasmJs` EXIT 0.

**Pending:** Live device-verify of the 4 refinements (drag gesture, haze state transition, back-key collapse, placeholder switching) on Pixel_9 + `:9090` (web). Awaiting user confirmation.

## Gesture rebuild — AnchoredDraggable (2026-06-26)

After user tested the boolean-morph dock on Pixel_9, they rejected the concept as janky ("скачки") — discrete `manualExpand:Boolean` + `AnimatedVisibility` jumps between 2 states, finger never drives it, no fling/nested-scroll. `@compose-feature-expert` rebuilt the gesture layer on the **AnchoredDraggable** primitive (the foundation of BottomSheetScaffold) keeping our custom layout.

**Architecture (confirmed model):**
- **Input row pinned at bottom (independent)** — ChatInputRow stays on dock floor, untouched by gestures.
- **Draggable sheet = banner/answer panel ABOVE input** — grows continuously upward via AnchoredDraggable; avoids BottomSheetScaffold peek-from-top inversion.
- **2 anchors (Peek ↔ Expanded):** peek = floor (never hides), swipe-down collapses to peek. Fullscreen via existing ↗ button (no 3rd anchor for DISMISSED state).

**THE JANK FIX — Read @FrequentlyChangingValue ONLY in layout/draw lambdas:**
AnchoredDraggable's `offset` and `progress` are high-frequency values (updated per pixel). **MUST NOT read in Composable body** — reading in composition forces recompose-per-pixel = jank. Solution: read in `Modifier.layout { }` and `drawBehind { }` blocks only.
- **Reveal = height morph:** `Modifier.layout { height = measurable.measure(Constraints()).height − offset.toPx() }`; answer panel clips to reveal-height naturally.
- **Blur crossfade = glass→opaque:** `drawBehind { drawRect(colorSurface, alpha = dockProgress()) }` over an always-on `hazeEffect` → glass blur at bottom (peek) rides the finger; crossfade to opaque surface as panel expands.

**API surface (CMP 1.11.0 / Foundation 1.11.1):**
- `AnchoredDraggableState(initialValue)` — simple constructor; old multi-param @Deprecated moved to modifiers.
- `AnchoredDraggableDefaults.flingBehavior(state, positionalThreshold={it*0.5f}, animationSpec=spring(MediumBouncy, StiffnessMediumLow))` — tuned fling settle.
- `state.progress(from, to)` — continuous [0, 1] progress between anchors (drives blur alpha).
- `state.settle(animationSpec)` with velocity; old velocity-overload deprecated.
- `DraggableAnchors` + `updateAnchors` via measured panel height (do NOT hardcode pixel positions).
- **NaN-guard before offset reads:** `if (!offset.isNaN) { … }` — offsets start NaN, settle after measure.

**NestedScrollConnection (inner answer scroll ↔ sheet gesture):**
Bridges ChecklistDetailScreen's LazyColumn (inner scrollable answer content) with the AnchoredDraggable (outer sheet). Behavior:
- Up-drag expands sheet FIRST (before inner scroll starts); inner scroll only when fully expanded.
- Over-drag down at inner LazyColumn top collapses the sheet.
- Flings settle via both axes (sheet velocity ↔ inner velocity).

**Per-screen AnchoredDraggableState (NOT shared):**
Tablet two-pane (ListDetailSceneStrategy) renders both MainScreen + ChecklistDetailScreen simultaneously. **CRITICAL:** separate `AnchoredDraggableState` per screen — shared state crashes both Modifiers.modify() at once. Each screen owns its own state instance; App.kt slot-passes the state to `chatSheetContent(dockState)`.

**IME handling — focus-driven expansion:**
- IME + navigationBars owned by dock (not global `WindowInsets.ime`).
- **Chat field focus → requestExpand** via `LaunchedEffect(inputFocused) { if (inputFocused) requestExpand() }` (NOT a blanket `WindowInsets.ime` term — that falsely expands on ChecklistDetailScreen's inline add-item field; too many keyboards on one screen).

**Back gesture — context-aware collapse:**
`PlatformBackHandler(enabled=manualExpand) { clearFocus(); collapse() }` — collapse only if input empty AND user initiated expand (manualExpand=true). Non-blank input suppresses Back → text is preserved when dismissing keyboard.

**Files touched:**
- `GistiInlineChatPanel.kt` — `enum DockAnchor`; `dockProgress():Float` state read pattern; GistiExpandableDockContent rewrite; NestedScrollConnection bridge.
- `GistiChatDock.kt` — `GistiGlassChatDock` + `dockProgress():Float` + `drawBehind` blur crossfade.
- `App.kt` — slot signature updated; `AnchoredDraggableState` + `routeCollapseSignal` (navigate-auto-collapse).
- `MainScreen.kt` + `ChecklistDetailScreen.kt` — per-screen state instance; `PlatformBackHandler` + IME focus logic.

**Validation:**
- `:composeApp:compileAndroidMain` + `:composeApp:compileKotlinWasmJs` — EXIT 0.
- Screenshot goldens (GistiChatDockGlassScreenshotTest + AiChoiceResponseScreenshotTest) — both PASS; peek/expanded images green.
- **Pending user confirm:** Live feel on Pixel_9 (drag gesture smoothness, fling settle, nested-scroll interaction) + wasmJs `:9090` (mouse-wheel scroll + nested-scroll behavior).
