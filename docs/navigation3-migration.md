# Navigation 3 Migration Guide

Migrated from Navigation 2 (`androidx.navigation:navigation-compose`) to Navigation 3 (`androidx.navigation3`) in commit `9fe64d23`. This document captures the concept mapping and runtime differences for future reference.

## Concept Mapping

| Navigation 2 | Navigation 3 | Notes |
|---|---|---|
| `NavHost { composable(...) { } }` | `NavDisplay(backStack, entryProvider { entry<R> { } })` | `NavDisplay` reads the backStack snapshot directly |
| `NavController` | `NavBackStack` (`SnapshotStateList<NavKey>`) | No controller object; the list IS the stack |
| `navController.navigate(route)` | `backStack.add(route)` | Synchronous, no Channel, no coroutine needed |
| `navController.popBackStack()` | `backStack.removeLastOrNull()` | Synchronous |
| `popUpTo(root) { inclusive = true }` | `backStack.clear(); backStack.add(root)` | Explicit clear + seed |
| `NavCommand` (project wrapper) | Direct `AppNavigatorImpl` mutation of `backStack` | `NavCommand` sealed class removed |
| `rememberNavController()` | `rememberNavBackStack(startDestination)` | Created in `App.kt`, passed to `AdaptiveNavigationShell` |
| `composable<Route>` block | `entry<Route> { NavEntry(it) { Screen(...) } }` | `NavEntry` carries the metadata slot |
| `NavOptionsBuilder.launchSingleTop` | `backStack.removeAll { it is Route }; backStack.add(Route)` | Explicit deduplication |

## How the Channel Race Was Solved

Navigation 2 in this project routed commands through `Channel.BUFFERED` in `AppNavigatorImpl`, then `collectAsState()` consumed them in a `LaunchedEffect`. This created a race: if the `LaunchedEffect` had not started collecting before the first navigation command arrived (e.g., Splash → Onboarding), the command was lost or replayed at the wrong time.

Navigation 3 eliminates the race:

1. `NavBackStack` is a `SnapshotStateList`. Mutations are synchronous and immediately visible to Compose.
2. `AppNavigatorImpl.init { }` seeds the initial destination (`Splash`) directly into the stack before the first frame — no channel, no coroutine, no race window.
3. All subsequent navigate calls mutate the list synchronously. `NavDisplay` recomposes in the same frame.

## Deeplinks

`UpdateFeedDeepLinkHandler` (and any other deeplink entry point) routes via `AppNavigator` methods, which now mutate `NavBackStack` directly. The handler has no knowledge of Nav 2 vs Nav 3; it calls `appNavigator.navigateTo*()` as before. The change is transparent.

```kotlin
// UpdateFeedDeepLinkHandler — unchanged call site
appNavigator.navigateToAiChat()        // internally: backStack.add(AiChatRoute)
appNavigator.navigateToCalendar()      // internally: backStack.add(CalendarRoute)
```

## ReminderReceiver Cold-Start

When a reminder fires and the app is not running, `ReminderReceiver` creates a `PendingIntent` that opens `MainActivity` with the checklist ID as an Intent extra. `MainActivity` calls `appNavigator.consumePendingChecklistId()` in `onResume`, which adds `ChecklistDetail(id)` to the back stack after `Main`. The flow is:

```
ReminderReceiver.onReceive()
  → PendingIntent → MainActivity.onResume()
    → appNavigator.consumePendingChecklistId()
      → backStack = [Splash, Main, ChecklistDetail(id)]
```

No Nav 2 graph or deeplink URI is used; the navigator owns the cold-start routing logic directly.

## FakeNavigator Pattern

All 16 test fakes that implement `AppNavigator` now expose:

```kotlin
override val backStack: NavBackStack = mutableStateListOf()
```

Tests can assert on `backStack.last()` instead of capturing `NavCommand` emissions. Example:

```kotlin
// Before (Nav 2)
val commands = mutableListOf<NavCommand>()
fakeNavigator.commands.collect { commands += it }
assertEquals(NavCommand.ToChecklistDetail(42L), commands.last())

// After (Nav 3)
assertEquals(ChecklistDetail(42L), fakeNavigator.backStack.last())
```

## Known Limitations

| Limitation | Impact | Mitigation |
|---|---|---|
| Navigation 3 is in **alpha** | API may change between releases | Pin the version in `libs.versions.toml`; review changelog before bumping |
| `ListDetailSceneStrategy` renders both panes simultaneously on Expanded | Both composables are active; avoid side effects that assume single-screen lifecycle | Scope heavy operations to `LaunchedEffect(Unit)` with lifecycle-aware collectors |
| wasmJs: `NavBackStack` relies on Compose snapshot state | Works correctly; no known issues as of Compose Multiplatform 1.9.3 | Smoke-test two-pane layout on `wasmJsBrowserDistribution` after any Nav 3 version bump |
| No built-in transition animations in Nav 3 alpha | Screen transitions are instant cuts | Custom `AnimatedContent` wrapper around `NavDisplay` content slot if animations are needed |
