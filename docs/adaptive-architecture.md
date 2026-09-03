# Adaptive UI Architecture

Gisti supports three form factors — phone, tablet rail, and desktop drawer — driven by `WindowSizeClass` breakpoints. A single `AdaptiveNavigationShell` composable selects the layout variant; all screens remain unchanged.

## System Overview

```
WindowSizeClass
      │
      ├── Compact  (<600dp)   ─→ ModalNavigationDrawer   (hamburger button)
      ├── Medium   (600-839dp) ─→ NavigationRail          (left rail, no labels)
      └── Expanded (≥840dp)   ─→ PermanentNavigationDrawer (always-visible drawer)
                                          │
                              AdaptiveNavigationShell
                                          │
                              ┌───────────┴────────────┐
                          List pane               Detail pane
                       (always shown)        (Expanded only, ListDetailSceneStrategy)
```

## Navigation 3 + NavDisplay

`App.kt` uses `NavDisplay` (Navigation 3 alpha) backed by `NavBackStack` — a `SnapshotStateList<NavKey>`. There is no `NavController` or `NavHost`.

```
NavBackStack (SnapshotStateList<NavKey>)
      │
      └── NavDisplay(backStack, entryProvider = entryProvider { ... })
                │
                └── entry<Route> { NavEntry(it) { Screen(...) } }
```

**ListDetailSceneStrategy** attaches `listPane` / `detailPane` metadata to route entries. On Expanded width, `NavDisplay` renders both panes simultaneously; on Compact/Medium it stacks them. Active pairs:

| List screen | Detail screen |
|---|---|
| `Main` | `ChecklistDetail` |
| `Templates` | `TemplatePreview` |
| `Today` | `ChecklistDetail` |
| `Calendar` | `ChecklistDetail` |

Routes without metadata (Splash, Onboarding, Paywall, Analyze, etc.) always render full-width regardless of form factor.

## AppScaffold Adaptive Behavior

`AppScaffold` adapts its top bar to the current window size class:

| Form factor | TopAppBar variant | Scroll behavior |
|---|---|---|
| Compact | `CenterAlignedTopAppBar` | `exitUntilCollapsedScrollBehavior` |
| Medium / Expanded | `MediumTopAppBar` | `exitUntilCollapsedScrollBehavior` |

The `nestedScrollConnection` from `scrollBehavior` is wired into the content slot — screens do not need to set up their own scroll connection.

## Grid vs Column Layout

Screens choose their layout based on the available window width:

| Screen | Compact | Medium | Expanded |
|---|---|---|---|
| `MainScreen` | `LazyColumn` | `LazyVerticalGrid(2 cols)` | `LazyVerticalGrid(3 cols)` |
| `TemplatesScreen` | `LazyColumn` | `LazyVerticalGrid(2 cols)` | `LazyVerticalGrid(3 cols)` |
| `AnalyzeScreen` | `LazyColumn` | `LazyVerticalGrid(2 cols)` | `LazyVerticalGrid(2 cols)` |
| All other screens | `Column` / `LazyColumn` | `adaptiveContentWidth` clamp | `adaptiveContentWidth` clamp |

`Modifier.adaptiveContentWidth(maxWidthDp = 720)` centers single-column content on wide screens. Applied to all linear-layout screens as a wrapper modifier.

## Bottom Sheet vs Dialog

`AdaptiveSheetOrDialog` selects the presentation style automatically:

| Form factor | Presentation |
|---|---|
| Compact | `ModalBottomSheet` |
| Medium / Expanded | `AlertDialog` |

All item-detail sheets, reminder pickers, and settings panels use this composable. No per-screen branching needed.

## Key Files

| File | Purpose |
|---|---|
| `composeApp/.../App.kt` | `NavDisplay` + `entryProvider` + `ListDetailSceneStrategy` + `AdaptiveNavigationShell` wrapping 6 routes |
| `composeApp/.../navigation/AdaptiveNavigationShell.kt` | 3 layout variants (Compact/Medium/Expanded) |
| `core/designsystem/.../adaptive/AppWindowSizeClass.kt` | `expect/actual` window size (android: `androidx.window`, wasmJs: `window.innerWidth` + resize listener, iOS: `LocalWindowInfo`) |
| `core/navigation/api/.../AppNavigator.kt` | `backStack: NavBackStack` field + 30 navigate methods |
| `core/navigation/impl/.../AppNavigatorImpl.kt` | `NavBackStack` mutations (add / removeAt / clear) |
| `core/designsystem/.../containers/AppScaffold.kt` | `scrollBehavior` + adaptive `TopAppBar` |
| `core/designsystem/.../containers/AdaptiveContentWidth.kt` | `Modifier.adaptiveContentWidth(maxWidthDp = 720)` |
| `core/designsystem/.../containers/AdaptiveSheetOrDialog.kt` | `ModalBottomSheet` on Compact, `AlertDialog` on Expanded |

## Edge Cases

### ChatScreen — own Scaffold
`ChatScreen` keeps its own `Scaffold` (not `AppScaffold`). Reason: it has a pinned input row at the bottom that requires `imePadding()` applied inside the Scaffold content slot, not at the shell level. It is a top-level drawer destination but manages its own chrome.

### PaywallScreen — own Scaffold
`PaywallScreen` is a leaf screen with a custom full-bleed hero layout. It uses its own `Scaffold` and applies `statusBarsPadding()` / `navigationBarsPadding()` manually. It does not participate in the shell's top bar.

### Reorderable drag-and-drop — Compact only
`MainScreen` enables reorderable drag-and-drop (via `sh.calvin.reorderable 3.1.0`) only when the layout is `LazyColumn` (Compact). The library does not support `LazyVerticalGrid`, so tablet users cannot reorder. A hint snackbar is shown on Medium/Expanded when the user attempts a long-press. See `docs/adaptive-decisions.md` for the full trade-off.

### FormFactor previews
`FormFactorPreviews` annotation provides `@Preview` entries for all three breakpoints (360dp, 720dp, 1280dp). Apply it to any composable that branches on `WindowSizeClass` to catch layout regressions at design time.
