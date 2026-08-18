# Voice recording discard — dock peek surface still affected

**Status:** resolved
**Created:** 2026-07-17
**Resolved:** 2026-07-17 — superseded by Option B (window-coordinate gesture) in `ChatInputRow.kt`.
**Origin:** found while fixing the voice-recording "release-discards" bug (full-screen chat + full overlay fixed same day).

## Resolution (2026-07-17)

The multi-file dock fix described below was NOT needed. The cancel-vs-commit decision in the mic
press-and-hold gesture (`ChatInputRow.kt`) now measures drag in **window (root) coordinates** via
`LayoutCoordinates.localToWindow(...)`, anchored on the finger's absolute screen position. Because
the decision no longer depends on the node staying still, BOTH downward sources on the dock peek
(inset collapse **and** the `Expanded→Peek` auto-collapse) are neutralized with zero dock/focus
state-machine surgery. A stationary release SAVES; only a genuine ≥80dp upward slide cancels — on the
home dock, the full-screen chat, and the full overlay alike. The `frozenBottomInset` inset-freeze
(Option A) was reverted as no longer necessary. Details: `docs/work/compose-feature-expert-scratch-2026-07-17-voice-optionb.md`.

## Problem

Press-and-hold voice recording discards the take on release (finger stationary) because the input row moves down under the finger when the keyboard closes on record-start. Fixed for two of three `ChatInputRow` hosts via `Modifier.frozenBottomInset(frozen = isRecording, …)`:

- ✅ Full-screen `ChatScreen` (`feature/aichat/impl/.../presentation/ChatScreen.kt`)
- ✅ Full overlay `GistiFullChatOverlay` (`core/designsystem/.../components/gisti/GistiFullChatOverlay.kt`, wired from `App.kt`)
- ❌ **Dock peek `GistiExpandableDockContent`** — NOT fixed.

## Why the dock peek is harder

The peek bar has **two** downward sources when recording starts, not one:

1. Inset collapse (same as the fixed surfaces) — its `imePadding().navigationBarsPadding()` is owned by an **outer per-screen host** (`MainScreen` + `ChecklistDetailScreen`, where `GistiGlassChatDock` is placed), not by the dock component itself.
2. A `LaunchedEffect(chatFieldFocused)` that **auto-collapses the dock Expanded→Peek** when the field loses focus — and disabling the TextField on record-start drops focus. Freezing only the inset leaves this second shrink, so the bar still moves under the finger.

## Correct fix (multi-file)

`isRecording` must:
1. Freeze the host inset (`imePadding`) on **both** `MainScreen` and `ChecklistDetailScreen` where `GistiGlassChatDock` lives — via the same `frozenBottomInset` helper.
2. Be ORed into the dock's `keepExpanded` so the Expanded→Peek auto-collapse is suppressed while recording.

Touches the dock's anchor/focus state machine (historically fragile per its own comments) across 2+ screens — deliberately left out of the "smallest correct fix" per «don't ship the edge».

## Resume

«доделай voice-фикс в доке» / «dock peek запись сбрасывается». Verify live: from the small dock bar (not the ↗ full overlay), keyboard up, press-hold mic, release without moving → must SAVE.
