---
title: "Single-Source-of-Truth Color Function for Dock + Navbar Sync"
date: 2026-06-30
type: decision
modules: [core/designsystem, feature/home]
keywords: [gistiDockColor, color-sync, navbar-sync, single-source-truth, windowInsets, material3]
project: gisti-ai-checklists
---

# Single-Source-of-Truth Color for Dock + System-Navigation Bar Sync

## Проблема / Контекст
When a dockable component (e.g., `GistiChatDock`) and the system-navigation bar must visually match, their background colors were historically duplicated across 3+ files:
- The dock component itself (`GistiChatDock.kt`)
- MainScreen.kt (navbar `windowInsetsBottomHeight(navigationBars).background(...)`)
- ChecklistDetailScreen.kt (navbar `windowInsetsBottomHeight(navigationBars).background(...)`)
- Debug utilities

Editing one location left the others stale, causing visual mismatch (e.g., dock is white, navbar is grey). This is a **dual-source color desync** antipattern.

## Решение
Extract a single public `@Composable @ReadOnlyComposable fun gistiDockColor(): Color` in the dock's module (`GistiChatDock.kt`). Both the dock component AND the navbar inset backgrounds reference this one function:

```kotlin
@Composable
@ReadOnlyComposable
fun gistiDockColor(): Color {
  return if (DockDesignDebug.useLegacyDock) {
    MaterialTheme.colorScheme.surfaceContainerLow  // old flat grey
  } else {
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerLow
    else MaterialTheme.colorScheme.surfaceContainerLowest  // new crisp white
  }
}
```

Used in:
- `GistiChatDock.kt`: `Surface(shape=top-28dp-rounded, color=gistiDockColor())`
- `MainScreen.kt`: `windowInsetsBottomHeight(navigationBars).background(gistiDockColor())`
- `ChecklistDetailScreen.kt`: `windowInsetsBottomHeight(navigationBars).background(gistiDockColor())`

## Почему именно так
1. **Single source of truth:** One edit updates all three places. No stale duplicates.
2. **Composable + ReadOnlyComposable:** Ensures the color respects theme changes and debug toggles at runtime.
3. **Isolated in the dock module:** The dock is the canonical owner of its visual identity; navbar just borrows the color.
4. **Pattern reusable:** Any future dockable, drawer, or sheet can follow the same approach (extract `<componentName>Color()` in the component's module, share it with system-insets).

### Alternatives rejected
- **Hardcoded colours in each file:** Creates the very dual-source problem we're solving. Leads to desync after the first edit.
- **A global `DockColorProvider` context:** Adds indirection and complexity; a simple `@Composable` function is clearer and more flexible.
- **Keep navbar color separate:** Introduces visual mismatch when dock color changes. Violates the design principle that visually adjacent UI elements should sync.
- **Use `windowBackground` color directly:** Doesn't account for component-specific color choices (e.g., dock might be white while the rest of the page is cream).

## Примеры

**Before (dual-source, easy to desync):**
```kotlin
// GistiChatDock.kt
Surface(..., color = MaterialTheme.colorScheme.surfaceContainerLow)

// MainScreen.kt
windowInsetsBottomHeight(navigationBars).background(MaterialTheme.colorScheme.surfaceContainerLow)
  // ^ if dock changes, navbar doesn't

// ChecklistDetailScreen.kt
windowInsetsBottomHeight(navigationBars).background(MaterialTheme.colorScheme.surfaceContainerLow)
  // ^ if dock changes, navbar doesn't
```

**After (single source):**
```kotlin
// GistiChatDock.kt
fun gistiDockColor(): Color = ...  // canonical

Surface(..., color = gistiDockColor())

// MainScreen.kt
windowInsetsBottomHeight(navigationBars).background(gistiDockColor())
  // always in sync

// ChecklistDetailScreen.kt
windowInsetsBottomHeight(navigationBars).background(gistiDockColor())
  // always in sync
```

## Связанные файлы
- `core/designsystem/src/commonMain/kotlin/.../GistiChatDock.kt` — `gistiDockColor()` definition + dock Surface implementation
- `feature/home/src/commonMain/kotlin/.../MainScreen.kt` — navbar background uses `gistiDockColor()`
- `feature/home/src/commonMain/kotlin/.../ChecklistDetailScreen.kt` — navbar background uses `gistiDockColor()`
