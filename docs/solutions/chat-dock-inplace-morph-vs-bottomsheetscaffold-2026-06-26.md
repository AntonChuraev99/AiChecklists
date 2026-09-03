---
title: "Chat Dock Architecture: In-Place Morph vs BottomSheetScaffold"
date: 2026-06-26
type: architecture
modules: [core/designsystem, composeApp, feature/aichat, feature/home]
keywords: [chat-dock, bottomsheet, animation, haze-blur, state-continuity, tablet-layout, architectural-pivot]
project: checklists
---

# Chat Dock Architecture: In-Place Morph vs BottomSheetScaffold

## Problem / Context

The AI Chat dock had two separate render sites: a collapsed peek (ChatInputRow in AppScaffold) and a full-screen ChatScreen. This split caused **three concurrent bugs**:

1. Voice recording (press-hold mic in collapsed dock) → broken; the intent layer never sent OnVoiceRecordingStarted.
2. Feedback sheet (Leave Feedback action) → silent in collapsed dock; ChatFeedbackSheet was hosted only in ChatScreen.
3. Architectural smell → collapsed and expanded renders had duplicate/divergent state, forcing full re-architecture to unify.

**Initial plan:** Consolidate via Material3 BottomSheetScaffold (Material Design's intended peek-and-expand pattern). This approach promised to eliminate the two-site split with a single native API.

## Solution

**Pivot to in-place morph (Approach A)** instead of BottomSheetScaffold.

Approach A = a **single AnimatedVisibility-driven height morph** living inside the existing GistiChatDock haze shell:

```
GistiChatDock (hazeSource/hazeEffect container)
├─ hazeEffect (backdrop blur layer)
└─ Column (the dock surface)
   ├─ AnimatedVisibility(expanded=isExpanded, Alignment.Bottom) 
   │  ├─ BannerRow (answer preview, only when expanded)
   │  └─ AnswerFrame (scrollable answer, only when expanded)
   │
   └─ ChatInputRow (pinned at dock bottom, always visible)
       ├─ VoiceButton (press-hold mic → sendIntent + recorder.start)
       └─ FeedbackButton (tap → sendIntent + sheet)

App.kt level:
├─ chatUiState.feedbackTarget → ChatFeedbackSheet host
└─ ChatScreen (ChatRoute ViewModel distinct via NavDisplay decorator)
   ├─ ChatScreen full-screen render (when ChatRoute active)
   └─ No dock-level duplicate
```

**Key differences from BottomSheetScaffold:**

| Aspect | Approach A (In-Place Morph) | BottomSheetScaffold (Blocked) |
|--------|-----|-----|
| Peek composition | Real ChatInputRow (single instance) | BottomSheetScaffold wraps sheetContent in Surface |
| Peek-to-expanded transition | Height animation via AnimatedVisibility | Sheet slides up from Material Surface |
| Input position | Pinned at dock bottom (collapsing expands upward) | Top of sheetContent → layout inversion required |
| Haze compatibility | hazeEffect is sibling to dock; AnimatedVisibility is CHILD → sampling preserved | BottomSheetScaffold Surface clip graphicsLayer is ancestor → kills backdrop blur (non-negotiable design constraint) |
| Tablet two-pane (ListDetailSceneStrategy) | One AnimatedVisibility per dock per screen → independent state | One SheetState across two BottomSheetScaffolds → simultaneous compose crash |
| State continuity | Shared ChatViewModel singleton across dock collapse/expand | Same, but architecture forces layout re-engineering |

## Bugs Fixed

### Bug #1: Voice Recording Broken in Collapsed Dock

**Symptom:** Pressing mic icon in collapsed ChatInputRow → no recording started; icon doesn't animate.

**Root cause:** App-level dock slot received the press event but **never sent OnVoiceRecordingStarted intent to the ViewModel**. The receiver (sheetAudioRecorder) could start, but the ViewModel's `isRecording` StateFlow never flipped → overlay/icon state dead.

**Fix:**
```kotlin
// Before (dock slot, no intent)
VoiceButton(onPress = { sheetAudioRecorder.start() })  // Missing intent!

// After (dock slot, mirrors ChatScreen)
VoiceButton(
    onPress = { 
        sendIntent(OnVoiceRecordingStarted)  // ← Intent sent first
        sheetAudioRecorder.start()            // Side-effect follows state
    }
)
```

**Why this matters:** Data-pipeline bugs at seam layers (intent → receiver, intent → icon state) almost always live at the **missing intent-send**, not the receiver or UI binding. Inspection of the receiver logic or state-binding is a dead-end if the intent layer is silent.

### Bug #2: Feedback Sheet Silent in Collapsed Dock

**Symptom:** Tapping "Leave Feedback" (thumb-down) in collapsed dock → feedback sheet never appears.

**Root cause:** ChatFeedbackSheet was hosted only inside ChatScreen; App-level dock had no sheet-host, so the intent had nowhere to render.

**Fix:** Move ChatFeedbackSheet to App-level host driven by `chatUiState.feedbackTarget`:
```kotlin
App.kt:
if (chatUiState.feedbackTarget != null) {
    ChatFeedbackSheet(target = chatUiState.feedbackTarget, onDismiss = { })
}
```

Safe across dock-state transitions because:
- ChatRoute composable (full ChatScreen) has its own ViewModel via `rememberViewModelStoreNavEntryDecorator` → distinct from App-level ChatViewModel.
- App-level ChatViewModel persists singleton state (feedbackTarget, recording overlay, etc.) across route navigation.
- Nested ViewModels don't collide; scope is explicit per-compose-tree.

### Bug #3: Architecture Smell

**Symptom:** Duplicate render site (collapsed dock + full ChatScreen) meant voice + feedback logic had to be maintained in two places, and one site was always crippled by the split.

**Fix:** Unified single render (Approach A morph inside dock) eliminates the need for ChatScreen's separate dock-duplicate code paths. All logic flows through one ViewModel, one input-handler, one state.

## Why This Approach (not BottomSheetScaffold)

**Three blockers forced the pivot away from BottomSheetScaffold:**

1. **Blocker A (UX layout inversion):** Material3 BottomSheetScaffold's peek-from-top design (sheetContent sits atop peek state) inverts the intended answer-above/input-below layout. Accepting inversion would break product UX; designing a custom sheet to avoid inversion negates the "system API" benefit.

2. **Blocker B (Haze backdrop blow-up):** BottomSheetScaffold wraps sheetContent in a Material3 Surface with clip graphicsLayer as an ancestor. Haze sampling (used for the dock's backdrop blur) is **incompatible** with clip ancestors — the blur falls through or fails silently. Haze 2.0.0+ broke sampling on Material3 surfaces; team explicitly requires Haze 1.7.2 (older, stable). Approach B forces a choice: ship broken blur or bypass Material's wrapper. Approach A keeps Haze as a sibling → sampling survives.

3. **Blocker C (tablet crash):** Tablet layouts (ListDetailSceneStrategy) compose MainScreen + ChecklistDetailScreen simultaneously. Each has its own dock instance. If both use BottomSheetScaffold with a single App-level SheetState, **the two scaffolds compete for state** → crashes during simultaneous expand/collapse. Approach B would require per-screen SheetState + complex state-sync logic. Approach A uses per-screen AnimatedVisibility (stateless expand flag) → no competition.

**Trade-off:** Approach A requires custom animation + manual gesture handling (mic press doesn't interfere with dock drag). Approach B was "system-provided," but system API assumptions didn't match Gisti's constraints. Early expert validation of these constraints prevented committing to the wrong path.

## Lessons Learned

### For Agents on Refactor/Architecture Tasks

On surface-area changes involving a Material/system API (BottomSheetScaffold, LazyColumn, Pager, etc.), **validate 3–5 key assumptions before coding**:
- Does the API's default behavior (peek-from-top, wrapping Surface, state scoping) match the UX design?
- Are there known incompatibilities with libraries you must preserve (Haze, custom gestures, IME)?
- Does the API scale to your app's layout complexity (tablet two-pane, nested routes, concurrent screens)?

Return `NEEDS_INPUT` with constraint questions and wait for confirmation. Early validation prevented a ~5-file high-regression rewrite in this case; without it, we would have coded BottomSheetScaffold, discovered blocker B (Haze sampling) during screenshot testing, then pivoted.

### For Agents on Bug-Fixing Data Pipelines

When debugging two-site render splits or "action works in one place but not another" bugs, **suspect the missing intent-send** before inspecting receiver logic. The pattern:
- Slot A presses button → intent sent → ViewModel updates → receiver fires → icon synced ✓
- Slot B presses button → intent NOT sent → receiver never fires → icon dead ✗

Inspection of the receiver/icon logic is a red herring; the bug is at the intent layer.

## Validation

- `:composeApp:compileAndroidMain` + `:core:designsystem:compileAndroidMain` + `:feature:aichat:impl:compileAndroidMain` → EXIT 0.
- `:composeApp:compileKotlinWasmJs` → EXIT 0 (no platform-specific BottomSheet; expect/actual not needed).
- ChecklistDockGlassScreenshotTest (collapsed + expanded goldens) → PASS.
- AiChoiceResponseScreenshotTest → PASS (choice chips unchanged).
- Device e2e (press-hold mic, dock morph, feedback-sheet, route auto-collapse) → pending user verification.

## References

- Active doc: `docs/active/chat-dock-bottomsheetscaffold-3bugfix-2026-06-26.md` (before archive)
- Haze rule: `docs/decisions/haze-backdrop-blur-constraints-composesystem.md` (Haze 1.7.2 only, no upgrades)
- Widget pattern (separate Glance + DI): `docs/solutions/widget-di-dao-and-glance-trampoline-2026-06-22.md` (related BottomSheet-like state scoping issue)
