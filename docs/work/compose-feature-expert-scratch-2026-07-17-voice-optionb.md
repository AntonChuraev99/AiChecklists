# Voice recording release-discards — Option B (window coords) + revert Option A (2026-07-17)

Status: applied + COMPILE GREEN (exit 0). NOT committed.

## Applied (working tree)
- `feature/aichat/impl/.../presentation/components/ChatInputRow.kt` — Option B: mic press-hold gesture
  measures cancel-vs-commit in WINDOW coords via `micLayoutCoordinates.localToWindow(...)`. Added
  imports Offset / LayoutCoordinates / onGloballyPositioned; `.onGloballyPositioned{}` on the mic
  gesture modifier; local `windowY(local)` helper; `dragY = windowY(change.position) - downWindowY`.
- `feature/aichat/impl/.../presentation/ChatScreen.kt` — REVERTED Option A: `.frozenBottomInset(...)`
  -> `.imePadding()`; import swap (remove frozenBottomInset, add imePadding); comment updated.
- `core/designsystem/.../components/gisti/GistiFullChatOverlay.kt` — REVERTED: removed `isRecording`
  param + KDoc; `.frozenBottomInset(...)` -> `.windowInsetsPadding(ime ∪ navbar)`; import swap back.
- `composeApp/.../App.kt` — removed `isRecording = chatUiState.isRecording` arg (+comment) from the
  GistiFullChatOverlay call (~:1390). Legit ChatInputRow `isRecording=` params untouched.
- Deleted untracked `core/designsystem/.../containers/FrozenBottomInset.kt`.

## Why revert Option A
Option B makes correctness independent of node movement -> fixes all 3 hosts (dock/full/overlay) with
one change. Frozen inset only smoothed 2 hosts and left the bar hovering over an empty strip while
recording -> not "clearly better at zero risk" -> reverted for simplicity + visual consistency.

## Closes deferred todo
`docs/todos/2026-07-17-voice-recording-dock-peek-still-discards.md` — dock peek fixed by Option B.

## Verify
Compile GREEN: :feature:aichat:impl:{compileAndroidMain,compileKotlinWasmJs} +
:composeApp:{compileAndroidMain,compileKotlinWasmJs}.
Live: home dock -> tap input (keyboard up) -> mic press-hold -> release WITHOUT moving = SAVE;
slide finger up >=80dp = CANCEL ("Release to cancel" copy); slide down = SAVE.
