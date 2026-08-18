---
title: "Global IME Lift for Bottom Bar in Edge-to-Edge AppScaffold"
date: 2026-05-30
type: bug-fix
modules: [core/designsystem, composeApp]
keywords: [IME, keyboard, WindowInsets, AppScaffold, bottomBar, edge-to-edge, wrappedBottomBar, contentWindowInsets]
project: checklists
---

# AppScaffold Global IME Lift for Bottom Bar

## Problem / Context

Users reported that when the soft keyboard opens, bottom-bar elements (e.g., input rows, buttons in floating UI) were hidden behind the IME instead of being pushed above it. The manifest already contained `windowSoftInputMode=adjustResize`, but it was ineffective because `MainActivity.enableEdgeToEdge()` (via `decorFitsSystemWindows=false`) disables manifest-based system window handling — **the Compose layer must handle IME insets explicitly**.

## Solution

Wrapped the `bottomBar` composable slot in `AppScaffold` with a `Box` applying `WindowInsets.ime.union(WindowInsets.navigationBars)` padding. The `union` operation computes the maximum inset on each side:
- When keyboard is closed: uses navigation bar height
- When keyboard is open: uses keyboard height (which is always ≥ nav bar)

```kotlin
Box(
    modifier = Modifier
        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
) {
    bottomBar()
}
```

This lifts the caller's `bottomBar` composable above the IME without requiring changes to any of the 19 caller sites — `windowInsetsPadding` **consumes** the insets, so existing `.navigationBarsPadding()` in bottom-bar implementations (8 screens) resolve to zero padding (no double-inset).

## Why Exactly This Approach

1. **Manifest `windowSoftInputMode` is inert in edge-to-edge mode.** Framework system window handling is disabled; Compose must manage all insets. No amount of manifest tweaking fixes this — the UI framework doesn't read it.

2. **Point of fix must be the generic container.** Patching individual bottom-bar implementations (ChatInputRow, ItemDetailsSheet, etc.) would require changes across 8+ files. The design system container knows the structure (topBar/bottomBar/content slots) → fix once, benefit everywhere.

3. **`union(NavigationBars)` not `ime` alone.** IME only applies when keyboard is visible. Without `union`, the bottom bar collapses to zero height when keyboard closes, hiding persistent floating UI (always-visible buttons, persistent input hints). `union` ensures minimum height = navigation bar when IME is hidden.

4. **`windowInsetsPadding` consumes insets.** Callers that already apply `.navigationBarsPadding()` on their bottom-bar content expect the padding to apply when navbar is visible. By consuming it at the wrapper level, we guarantee navbar is padded ✓ and IME is padded ✓ without duplicate code.

## Critical Pitfall: contentWindowInsets Override

`AppScaffold` passes `contentWindowInsets` to the content slot. The original fix set this to `WindowInsets(0, 0, 0, 0)` to avoid double-navbar in content. **However, AppScaffold conditionally renders topBar** (only if title/onBack/navIcon provided). For screens without a top bar:

- `Scaffold(..., contentWindowInsets = WindowInsets.ZERO, contentPadding = it, content = { ... })` sets `it.top = 0`
- Content slot receives zero top inset → content slides **under** status bar (regression on MainScreen)

**Fix:** Use `contentWindowInsets = WindowInsets.statusBars` instead:
- `statusBars.top` preserved → top-bar-less screens still have status-bar padding
- `statusBars.bottom = 0` → does not interfere with wrappedBottomBar's IME padding

```kotlin
contentWindowInsets = WindowInsets.statusBars  // not ZERO
```

Lesson: When overriding `contentWindowInsets` in a Scaffold with conditional topBar, preserve at least the top inset to account for screens that don't render the top app bar.

## Examples

### Before (IME hides bottom bar)
```
┌─────────────────┐
│ Status Bar      │
├─────────────────┤
│ Content...      │
│                 │
│ [Input Row]     │ ← hides when keyboard opens
└─────────────────┘
[Keyboard]
```

### After (IME lifts bottom bar)
```
┌─────────────────┐
│ Status Bar      │
├─────────────────┤
│ Content...      │
├─────────────────┤
│ [Input Row]     │ ← always visible, pushed above keyboard
├─────────────────┤
[Keyboard        ]
```

## Related Files

- `core/designsystem/src/commonMain/kotlin/.../AppScaffold.kt` — wrappedBottomBar implementation
- `composeApp/src/androidMain/MainActivity.kt` — `enableEdgeToEdge()` which triggers the issue
- 19 caller sites (e.g., HomeScreen, ChecklistDetailScreen, ChatScreen, AnalyzeScreen, PaywallScreen) — no changes required

## Testing Notes

- **Visual validation:** Open any bottom-bar screen, tap text field or input row, observe that the bar slides above keyboard (not hidden behind it)
- **Regression check:** Verify MainScreen (no top app bar) does not slide under status bar when back arrow is not present
- **Screens with custom Scaffold:** ChatScreen uses Material3 Scaffold directly, not AppScaffold — unaffected by this change. Its IME handling via `WindowInsets.ime.getBottom()` for reverseLayout scroll remains independent.

## Deployment

No special steps — compiled into the APK. The fix applies globally to all AppScaffold-based screens on the next release.

## References

- WindowInsets union behavior: `androidx.compose.foundation.layout.WindowInsets.union()` (max on each edge)
- Edge-to-edge design: Material Design 3 spec and `enableEdgeToEdge()` documentation
- Related pattern: [Web Single-Pane Layout Pattern — Navigation 3 Scene Strategy](../../../active/web_single_pane_layout_pattern.md) (platform-specific layout handling, different context but same inset-awareness mindset)
