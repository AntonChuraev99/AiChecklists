---
title: "Web Single-Pane Layout — Disable List-Detail Two-Pane on wasmJs"
date: 2026-05-29
type: feature
modules: [composeApp, core/navigation]
keywords: [Navigation 3, SceneStrategy, SinglePaneSceneStrategy, wasmJs, list-detail two-pane, platform-specific, adaptive-layout]
project: checklists
---

# Web Single-Pane Layout — Disable List-Detail Two-Pane on wasmJs

## Problem / Context

The app originally shipped with a **list-detail two-pane layout** on all platforms (Android, iOS, wasmJs) — using `androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy` to display the checklist list beside the detail pane on Medium/Expanded windows.

However, on the **web platform (wasmJs)**, this layout is inappropriate:
- Browser windows on desktop are typically **narrow** (single-column viewing).
- The two-pane layout wastes horizontal space and confuses the navigation flow.
- Users expect the checklist list to fill the available space; tapping a checklist should replace it in place, not open a second panel beside it.

**The fix**: Enable **single-pane layout on web only** by substituting `SinglePaneSceneStrategy` for `ListDetailSceneStrategy` when running on wasmJs. This change applies exclusively to the web platform; Android and iOS remain unchanged.

## Solution

### Three-File Change

#### 1. New file: `NavLayoutPolicy.kt`

Created `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/navigation/NavLayoutPolicy.kt`:

```kotlin
package com.antonchuraev.homesearchchecklist.navigation

private const val PLATFORM_WEB = "web"

/**
 * Decides whether the navigation layout should be **single-pane** (every
 * destination is full-screen; opening a checklist replaces the list in place)
 * instead of the **list-detail two-pane** layout used on wide windows.
 *
 * - **Web (wasmJs)** → single-pane. On a wide browser window the checklist list
 *   should fill the whole content area, and tapping a checklist should replace
 *   it in place — not sit beside it in a second detail pane.
 * - **Android / iOS** → list-detail two-pane is kept (returns `false`), so the
 *   Medium/Expanded experience on those platforms is unchanged.
 *
 * Kept as a pure function (taking the platform name rather than calling
 * `getPlatformName()` itself) so the decision is unit-testable on the JVM host
 * without a Compose runtime — mirrors how `classifyWindowWidth` is split out of
 * the `@Composable rememberAppWindowSizeClass`.
 *
 * @param platformName value from `getPlatformName()` — `"web"`, `"android"`, or `"ios"`.
 */
internal fun shouldUseSinglePaneLayout(platformName: String): Boolean =
    platformName == PLATFORM_WEB
```

**Why a pure function**: Makes the decision **testable on the JVM host** (no Compose runtime required), mirrors the existing `classifyWindowWidth()` pattern for window-size decisions, and centralizes the platform check so changes propagate everywhere.

#### 2. Update `App.kt`

Added imports and platform-aware scene strategy instantiation:

```kotlin
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.antonchuraev.homesearchchecklist.navigation.shouldUseSinglePaneLayout
// ... existing imports ...

// Around line 248–253 in App.kt:
val platformName = remember { getPlatformName() }
val sceneStrategy = if (shouldUseSinglePaneLayout(platformName)) {
    remember { SinglePaneSceneStrategy<NavKey>() }
} else {
    rememberListDetailSceneStrategy<NavKey>()
}

// ... pass sceneStrategy to NavDisplay:
NavDisplay(
    backStack = navigator.backStack,
    onBack = { navigator.onBack() },
    sceneStrategy = sceneStrategy,
    // ... rest of NavDisplay params
)
```

**Why this approach**: 
- Navigation 3's `SceneStrategy` is the **only thing** that controls layout mode. 
- The entry metadata (`listPane { ... }` / `detailPane { ... }` markers in the `entryProvider` block) is **ignored by** `SinglePaneSceneStrategy`, so those metadata blocks require **no changes**.
- This is the minimal diff: swap one object at runtime based on platform, leave all other navigation code untouched.
- `ListDetailSceneStrategy` (multi-pane) and `SinglePaneSceneStrategy` (single-pane) both implement the same `SceneStrategy<NavKey>` interface, so the type is stable.

#### 3. New file: `NavLayoutPolicyTest.kt`

Created `composeApp/src/commonTest/kotlin/com/antonchuraev/homesearchchecklist/navigation/NavLayoutPolicyTest.kt`:

```kotlin
package com.antonchuraev.homesearchchecklist.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavLayoutPolicyTest {

    @Test
    fun shouldUseSinglePaneLayout_web_returnsTrue() {
        assertTrue(shouldUseSinglePaneLayout("web"))
    }

    @Test
    fun shouldUseSinglePaneLayout_android_returnsFalse() {
        assertFalse(shouldUseSinglePaneLayout("android"))
    }

    @Test
    fun shouldUseSinglePaneLayout_ios_returnsFalse() {
        assertFalse(shouldUseSinglePaneLayout("ios"))
    }

    @Test
    fun shouldUseSinglePaneLayout_unknownPlatform_returnsFalse() {
        assertFalse(shouldUseSinglePaneLayout("desktop"))
        assertFalse(shouldUseSinglePaneLayout(""))
        assertFalse(shouldUseSinglePaneLayout("Web")) // case-sensitive
    }
}
```

Tests the pure function on the JVM host; runs as part of `:composeApp:testAndroidHostTest`.

## Why This Approach, Not Alternatives

### Alternative 1: expect/actual SceneStrategy

**Rejected** — Would require 4 files:
- `composeApp/src/commonMain/kotlin/.../SceneStrategy.kt` (expect fun)
- `composeApp/src/androidMain/kotlin/.../SceneStrategy.kt` (actual)
- `composeApp/src/iosMain/kotlin/.../SceneStrategy.kt` (actual)
- `composeApp/src/wasmJsMain/kotlin/.../SceneStrategy.kt` (actual)

Both `SinglePaneSceneStrategy` and `rememberListDetailSceneStrategy` are **available in commonMain** (no platform-specific import required), so a runtime platform check is simpler and avoids expect/actual boilerplate.

### Alternative 2: Modify `ListDetailSceneStrategy` configuration

**Rejected** — `ListDetailSceneStrategy` has no configuration flag to "disable two-pane mode". Its sole job is to split the navigation graph into list and detail panes. To turn it off, you must replace the strategy object entirely.

### Alternative 3: Query `WindowSizeClass` in `ListDetailSceneStrategy`

**Rejected** — The two-pane toggle is **not about window size** on web; it's about **platform capability**. A wide browser window should still use single-pane layout for consistency with the web platform's navigation paradigm (modal stacking, not side-by-side panels). Android users on a tablet expect two-pane; web users expect single-pane regardless of window width.

## Key Finding: API Source of Truth — klib Over Web Docs

When looking up Navigation 3 scene strategy classes, the **actual package is `androidx.navigation3.scene`**, not `androidx.navigation3.ui.*` (which is what web docs suggested).

**Why this matters**: Navigation 3 is in **alpha** and is a JetBrains fork of Material3 Adaptive (org.jetbrains.androidx.navigation3). The source of truth is the `.klib` artifact metadata (`navigation3-ui-wasm-js-1.0.0-alpha05.klib`), not web documentation, which may be outdated or refer to the original Material3 package structure.

**For future alpha-library troubleshooting**: Unpack the `.klib` file to inspect the actual classes and their imports, rather than relying on docs or IDE autocomplete (which may cache stale type info).

## Validation

✅ **Build**: `:composeApp:compileKotlinWasmJs` — BUILD SUCCESSFUL  
✅ **Tests**: `:composeApp:testAndroidHostTest` — 4/4 tests PASS (NavLayoutPolicyTest)  
✅ **Android Build**: `:androidApp:assembleDebug` — BUILD SUCCESSFUL (platform isolation verified)  
⚠️ **Manual Visual Check**: Single-pane on web not yet verified by opening the app on a wide browser window (deferred to user / QA)

## Remaining Work

**Deferred**: Open the web app on a wide browser window (e.g., desktop Chrome at 1920×1080) and verify that:
1. The checklist list fills the content area (no empty detail pane beside it)
2. Tapping a checklist replaces the list with the detail view
3. The back button (or navigation history) returns to the list

This is a manual UX check — no automated test covers the final visual layout (that requires a Playwright/Cypress E2E test or manual inspection).

## Affected Modules

| Module | Change | Reason |
|--------|--------|--------|
| `composeApp` | App.kt + NavLayoutPolicy.kt (new) | Main navigation layer |
| `core/navigation` | (implicit) — no source code change; metadata still present | Entry metadata (listPane/detailPane) works with both strategies |
| Tests | NavLayoutPolicyTest.kt (new) | Unit-test the platform decision |

## Files Modified / Added

- ✨ **NEW** `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/navigation/NavLayoutPolicy.kt` (27 lines)
- ✨ **NEW** `composeApp/src/commonTest/kotlin/com/antonchuraev/homesearchchecklist/navigation/NavLayoutPolicyTest.kt` (34 lines)
- ✏️ **MODIFIED** `composeApp/src/commonMain/kotlin/com/antonchuraev/homesearchchecklist/App.kt` (imports + 6-line strategy instantiation block)

## Lessons for Future Platform-Specific Navigation Work

1. **Scene Strategy is the single lever** for layout mode in Navigation 3. Don't try to reconfigure `ListDetailSceneStrategy` — replace the object.
2. **Runtime platform check is simpler than expect/actual** when both strategy classes are available in commonMain.
3. **Pure function for platform-aware decisions** (like `shouldUseSinglePaneLayout`) is testable on JVM and mirrors the pattern used for window-size decisions.
4. **Entry metadata (listPane/detailPane)** persists across strategy swaps — they're not the toggle.
5. **klib metadata is the source of truth for alpha libraries**, not web docs.
