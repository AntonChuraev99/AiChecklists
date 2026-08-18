---
title: AI Chat post-Phase B follow-up fixes (i18n + chat list + IME insets)
date: 2026-05-17
category: bug-fixes
complexity: Standard
impact: High
keywords: [ai-chat, ime-insets, adjustResize, windowSoftInputMode, edge-to-edge, compose, reverseLayout, sideEffect-message-key, i18n, viewmodel-i18n]
parent: docs/solutions/features/ai-chat-assistant-phase-b-2026-05-17.md
---

# AI Chat — Post-Phase B Follow-up Fixes

Three sequential bug-fix commits that landed after the Phase B solution document was finalised. Captured here so the Phase B solution stays clean (it documents the as-shipped scope) while these fixes are still discoverable to future sessions.

## Commits

| Commit | Title | What it fixed | Root cause class |
|---|---|---|---|
| `941d86b0` | `fix(aichat): localise vm messages and pin items` | 4 ViewModel hardcoded EN strings → SideEffect-with-messageKey pattern; first attempt at gap-fix via `Arrangement.spacedBy(_, Alignment.Bottom)` | i18n in ViewModel layer; partial layout fix |
| `e1b29b71` | `fix(aichat): use reverseLayout for chat list` | Replaced alignment-based pin with `reverseLayout = true` chat pattern after first attempt didn't reliably fix the gap | Wrong primitive (alignment) for the pattern (chat list) |
| **`a69752c7`** | **`fix(android): set adjustResize for ime in main activity`** | **ACTUAL root fix: AndroidManifest navlanguage missing `windowSoftInputMode="adjustResize"`, IME insets never reached Compose → all imePadding() / WindowInsets.ime calls were getting zero** | Android Window system level, NOT a Compose layer issue |

## Root cause of the gap (final analysis)

User reported a visible empty space between welcome bubble and input row, especially "after keyboard opens." Most puzzling clue: **"after taking a screenshot the bug disappears"**. That was the diagnostic — screenshot triggers `Window.onConfigurationChanged` → recompose with fresh insets → bug visually "fixes" until next state change.

### Why initial Compose-layer fixes didn't work

1. **Attempt 1: `Arrangement.spacedBy(SpacingMd, Alignment.Bottom)` on LazyColumn** — alignment computed inside the lazy layout, which caches across parent size changes. IME open didn't invalidate the alignment recomputation.

2. **Attempt 2: `reverseLayout = true`** — correct chat-list pattern (items pin bottom-up by definition, no alignment needed). Bug *still* visible because the underlying issue was **insets**, not alignment. With `reverseLayout` the items had nothing to pin against — the viewport wasn't shrinking when the IME appeared, so there was no "new bottom" for them to track.

### The real cause

`androidApp/src/main/AndroidManifest.xml` declared the application theme as `@android:style/Theme.Material.Light.NoActionBar` — a **legacy AOSP theme** whose default `windowSoftInputMode` is `stateUnspecified|adjustPan`.

`adjustPan` means: when the IME opens, the Activity window is **not resized**. The system just shifts focused content up to keep the input field visible. Pre-API 30 trick. **`WindowInsets.ime` never receives the IME bottom inset.**

Combined with MainActivity using `enableEdgeToEdge()` (correct Compose pattern), the result was: Compose owned the window decor and expected to manage IME via `WindowInsets`, but the system never sent the IME inset because the window wasn't being resized. So `Modifier.imePadding()` evaluated to **zero**. LazyColumn computed its viewport using the full screen height, leaving an apparent gap at the bottom.

### Verification

`androidApp/build/intermediates/incremental/debug/mergeDebugResources/merged.dir/values/values.xml`:
```xml
<item name="android:windowSoftInputMode">stateUnspecified|adjustPan</item>
```
(appeared at multiple lines — inherited from Theme.Material variants)

No source-level override existed in any `*.xml`, no manifest activity declaration of `windowSoftInputMode` before this fix.

### The fix

One line in MainActivity manifest declaration:
```xml
<activity
    android:exported="true"
    android:name="com.antonchuraev.homesearchchecklist.MainActivity"
    android:windowSoftInputMode="adjustResize">
```

After this, `WindowInsets.ime` carries the IME inset, `imePadding()` works, LazyColumn shrinks correctly when the keyboard opens, and `reverseLayout = true` pins messages exactly above the input row.

## Reusable rules

1. **Compose IME gap symptom → check `AndroidManifest.xml` windowSoftInputMode FIRST.** Before tweaking `imePadding`, `Arrangement`, `reverseLayout`, `WindowInsets`, anything in Compose. If the manifest doesn't set `adjustResize` (or the inherited theme defaults to `adjustPan`), no Compose-layer fix will work.

2. **`adjustPan` + `enableEdgeToEdge()` is a broken combo.** They contradict each other. Always pair `enableEdgeToEdge()` with `adjustResize`.

3. **Chat-like UI = `reverseLayout = true`**, never alignment tricks. Items append at the bottom naturally without recomputation. (Still correct pattern even if the original symptom was insets, not layout.)

4. **ViewModel must never hold user-facing text.** Emit SideEffect with `messageKey: String` + optional `args`. Composable layer resolves via `stringResource()` and round-trips back as a new Intent. Same applies to inline assistant messages, not just snackbars.

5. **Welcome / empty-state UI affordances live in composable scope**, not ViewModel state. `remember { ChatMessage(content = stringResource(...)) }` follows locale automatically.

## i18n strings added in 941d86b0

Added to **both** values/strings.xml and values-ru/strings.xml in the same commit:
- `chat_generic_error` — generic failure recovery
- `chat_apply_error` — preview apply failure
- `chat_extract_fail` — entity extraction couldn't pull details

Also wired existing keys via the new pattern:
- `chat_unknown_intent_hint`
- `chat_ambiguous_match` (with `%1$s` arg substitution)
- `chat_not_found` (still dispatcher-side, not via SideEffect yet)
- `chat_requires_premium`

## What's still pending after these fixes

Logged in `docs/todos/2026-05-17-ai-chat-phase-c.md` section C.1 (mostly-done update):
- `ToolCallDispatcherImpl.DispatchOutcome.Success.humanReadable` — still hardcoded EN inside dispatcher (e.g. `"Added «$itemText» to ${checklist.name}"`)
- `DispatchOutcome.NotFound.reason` — same situation

These didn't ship in the current i18n pass because they touch dispatcher contract, not ViewModel emit. Phase C.1 leftover.

## Files touched

- `androidApp/src/main/AndroidManifest.xml` (a69752c7)
- `feature/aichat/impl/.../presentation/ChatScreen.kt` (941d86b0, e1b29b71)
- `feature/aichat/impl/.../presentation/ChatScreenContract.kt` (941d86b0)
- `feature/aichat/impl/.../presentation/ChatViewModel.kt` (941d86b0)
- `feature/aichat/impl/.../presentation/ChatRoute.kt` (941d86b0)
- `core/designsystem/.../values/strings.xml` (941d86b0 — 3 new EN keys)
- `core/designsystem/.../values-ru/strings.xml` (941d86b0 — 3 new RU keys)
