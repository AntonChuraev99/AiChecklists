---
paths:
  - "**/navigation/**"
  - "**/App.kt"
  - "**/*NavigationShell*.kt"
  - "**/*Navigator*.kt"
---

# Adaptive UI + Navigation 3

Full details: `docs/adaptive-architecture.md`. Migration guide + before/after: `docs/navigation3-migration.md`.

The app renders on phone (Compact), tablet (Medium), desktop/foldable (Expanded) via a single `AdaptiveNavigationShell` that picks navigation chrome from `WindowSizeClass`. `ListDetailSceneStrategy` enables two-pane on Expanded for four screen pairs (web swaps to `SinglePaneSceneStrategy`).

| Class | Width | Chrome | Layout |
|---|---|---|---|
| Compact | < 600dp | ModalNavigationDrawer (hamburger) | Single column |
| Medium | 600–839dp | NavigationRail | 2-col grid on grid screens |
| Expanded | ≥ 840dp | PermanentNavigationDrawer | 3-col grid + two-pane list/detail |

## Navigation model — Navigation 3 alpha, NO `NavController`

Back stack is `NavBackStack` (`SnapshotStateList<NavKey>`), mutated directly by `AppNavigatorImpl`. `NavDisplay` recomposes synchronously on every mutation — no Channel race, no `LaunchedEffect` timing issues. Routes are `@Serializable` objects implementing `AppNavRoute` (kotlinx.serialization). `SavedStateHandle[Route::field.name]` is unreliable in Nav3 alpha → always forward via `entry<Route> { route -> Composable(field = route.field) }` + Koin `parametersOf`.

**Key adaptive composables:** `AdaptiveNavigationShell` (`composeApp/.../navigation/`); `AppScaffold` (`CenterAlignedTopAppBar` on Compact, `MediumTopAppBar` + `exitUntilCollapsedScrollBehavior` on Medium/Expanded); `AdaptiveContentWidth` (`Modifier.adaptiveContentWidth(maxWidthDp=720)`); `AdaptiveSheetOrDialog` (`ModalBottomSheet` on Compact, `AlertDialog` on Expanded); `AppWindowSizeClass` (expect/actual — `androidx.window` Android, `window.innerWidth` wasmJs, `LocalWindowInfo` iOS).

## RULE: adding a new top-level (drawer) destination — update ALL THREE

1. `DrawerDestination` sealed class — add the entry.
2. `AdaptiveNavigationShell` — add to the destination list for **all three** variants (Compact/Medium/Expanded).
3. `App.kt` `entryProvider { }` — add the `entry<NewRoute> { }` block.

Miss any one → destination is unreachable from the shell even if the route is defined.

**Deeplink navigation pattern:** UI → `ViewModel.sendIntent(OnOpen…)` → ViewModel emits `SideEffect.NavigateTo…` → `App.kt` observes in `LaunchedEffect` → `navigator.navigate(Route(...))`. Decouples ViewModel from the navigator.
