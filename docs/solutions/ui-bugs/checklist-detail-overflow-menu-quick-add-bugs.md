---
title: "ChecklistDetailScreen: overflow menu, collapsible completed, quick-add — 10 bugs in 2 features"
category: ui-bugs
tags: [compose-multiplatform, state-management, room-database, keyboard-handling, focus-management, kmp, testing-gap]
module: feature:home (ChecklistDetailScreen)
symptom: "10 bug-fix iterations during implementation of overflow menu + quick-add features"
root_cause: "Implementation-first approach without unit tests; platform assumptions (BackHandler, onFocusChanged) untested; architectural decisions (DataStore vs Room, fill-only vs fill+template) discovered late"
date: 2026-02-26
---

# ChecklistDetailScreen: 10 bugs across 2 features

## Context

Two features were added to `ChecklistDetailScreen`:
1. **Overflow menu** (MoreVert) with "Separate completed" toggle and "Delete checklist"
2. **Quick-add** via toolbar "+" button with inline text input

Both features required ~10 bug-fix iterations before working correctly. This is unacceptable — the root cause is insufficient testing before and during implementation.

## Bugs Found (Chronological)

### Bug 1: Switch double-toggle
- **Symptom**: Toggling the switch visually fires twice (on → off instantly)
- **Root cause**: `Row(clickable)` + `Switch(onCheckedChange)` both handle the click
- **Fix**: `AppSwitch(onCheckedChange = null)` — let Row handle all clicks

### Bug 2: Switch not updating UI
- **Symptom**: Toggle clicks but UI doesn't change
- **Root cause**: `toggleSeparateCompleted()` saved to DataStore but no optimistic state update
- **Fix**: Add `updateContentState { it.copy(separateCompleted = newValue) }` before async save

### Bug 3: Switch color invisible on white background
- **Symptom**: Unchecked switch track blends with white surface
- **Root cause**: Default Material3 Switch colors lack contrast on white
- **Fix**: Created `AppSwitch` design system component with `outlineVariant` track color

### Bug 4: BackHandler unavailable in KMP commonMain
- **Symptom**: Compile error `Unresolved reference 'backhandler'`
- **Root cause**: `androidx.compose.ui.backhandler.BackHandler` doesn't exist in Compose Multiplatform 1.9.3; `activity-compose` is Android-only
- **Fix**: Abandoned BackHandler approach entirely

### Bug 5: Focus not clearing when keyboard hides
- **Symptom**: Press back → keyboard hides but cursor + blue border remain
- **Root cause**: `onFocusChanged` does NOT fire when keyboard hides on Android — focus stays `true`
- **Fix**: Track `WindowInsets.ime.getBottom()` to detect keyboard visibility changes

### Bug 6: `onFocusChanged` LaunchedEffect fires on first composition
- **Symptom**: `InlineAddItemInput` closes immediately after appearing
- **Root cause**: Initial `hasFocus = false` triggers `onClose()` in LaunchedEffect before focus is requested
- **Fix**: Replaced with `WindowInsets.ime` approach with `wasKeyboardVisible` tracking

### Bug 7: Inline input not hiding on back press
- **Symptom**: Keyboard closes, focus clears, but input row stays visible
- **Root cause**: `if (text.isBlank()) onClose()` — condition prevents close when user typed text
- **Fix**: Remove condition — always call `onClose()` when keyboard hides

### Bug 8: Toolbar "+" doesn't toggle
- **Symptom**: Pressing "+" again doesn't close the inline input
- **Root cause**: `addItemActive = true` — always sets true, never toggles
- **Fix**: `if (addItemActive) false else true` with scroll-to-bottom on open

### Bug 9: separateCompleted was global, not per-checklist
- **Symptom**: Toggling "Separate completed" in one checklist affects all checklists
- **Root cause**: Stored in DataStore (global key-value) instead of Room (per-entity)
- **Fix**: Room migration v4→v5, `ALTER TABLE checklists ADD COLUMN separateCompleted`

### Bug 10: Quick-add item not visible in Edit screen
- **Symptom**: Add item via "+", tap Edit — new item is missing
- **Root cause**: `addItem()` added `ChecklistFillItem` to fill but not `ChecklistItem` to checklist template
- **Fix**: Update both fill AND template; added `updateChecklistTemplate()` to avoid fill ID regeneration

## Root Cause Analysis

### Why 10 iterations?

| Category | Bugs | Could tests have caught? |
|----------|------|--------------------------|
| State management | #1, #2, #7, #8 | **Yes** — ViewModel unit tests |
| Platform compatibility | #4, #5, #6 | **Partially** — requires device/emulator |
| Design/visual | #3 | No — visual inspection needed |
| Architecture | #9, #10 | **Yes** — repository integration tests |

**7 out of 10 bugs were catchable by automated tests.**

### What went wrong

1. **No unit tests written for new ViewModel methods** — `toggleSeparateCompleted()`, `addItem()` had zero test coverage
2. **No E2E tests for the new UI flow** — overflow menu, toggle, quick-add were only tested manually
3. **Platform assumptions not validated** — assumed `BackHandler` and `onFocusChanged` work in KMP without checking
4. **Architectural decision (DataStore) made without considering per-checklist requirement**

## Prevention: Test-First Mandate

### Unit tests that SHOULD have been written first

```kotlin
// ChecklistDetailViewModelTest.kt

@Test
fun toggleSeparateCompleted_updatesStateAndSavesToRoom() {
    // Given: checklist loaded with separateCompleted = false
    // When: OnToggleSeparateCompleted intent
    // Then: state.separateCompleted == true
    // And: repository.setSeparateCompleted called with (checklistId, true)
}

@Test
fun addItem_updatesFillandChecklistTemplate() {
    // Given: checklist with 3 items
    // When: OnAddItem("New task")
    // Then: fill has 4 items
    // And: checklist template has 4 items
    // And: repository.updateFill AND updateChecklistTemplate called
}

@Test
fun addItem_emptyText_doesNothing() {
    // Given: any state
    // When: OnAddItem("   ")
    // Then: no repository calls
}

@Test
fun toggleSeparateCompleted_loadsFromChecklist_notGlobal() {
    // Given: checklist A has separateCompleted = true
    // When: open checklist A
    // Then: state.separateCompleted == true
    // When: open checklist B (separateCompleted = false)
    // Then: state.separateCompleted == false
}
```

### E2E tests that SHOULD have been written

```kotlin
// ChecklistDetailOverflowMenuFlowTest.kt (extends BaseUiTest)

@Test
fun overflowMenu_separateCompleted_persistsAfterReopen() {
    // Create checklist → open detail → tap MoreVert → toggle switch
    // Go back → reopen same checklist → verify switch is ON
    // Open different checklist → verify switch is OFF
}

@Test
fun quickAdd_itemVisibleInEditScreen() {
    // Create checklist → open detail → tap "+"
    // Type "New item" → tap check → verify item in list
    // Tap Edit → verify "New item" in edit form
}

@Test
fun quickAdd_backPressClosesInput() {
    // Open detail → tap "+" → verify input visible
    // Press system back → verify input hidden
}

@Test
fun quickAdd_togglePlusButton() {
    // Open detail → tap "+" → verify input visible
    // Tap "+" again → verify input hidden
}
```

### Existing infrastructure to use

| Infrastructure | Location | Purpose |
|---------------|----------|---------|
| `BaseUiTest` | `composeApp/src/androidTest/.../BaseUiTest.kt` | Wait helpers, navigation, item management |
| `TestRunner` | `composeApp/src/androidTest/.../TestRunner.kt` | Skips RevenueCat init |
| `TestApplication` | `composeApp/src/androidTest/.../TestApplication.kt` | Test-safe Application class |
| `ChecklistDetailViewModelTest` | `feature/home/src/commonTest/...` | Existing ViewModel test class to extend |
| 13 E2E flow tests | `composeApp/src/androidTest/...` | Patterns for all major flows |

## Key Takeaways

1. **Write ViewModel unit tests BEFORE implementing new intents** — each `onIntent()` handler should have at least happy-path + error test
2. **Write E2E tests for new UI interactions** — especially state persistence (toggle, back press, navigation roundtrip)
3. **Validate KMP platform availability early** — check `expect/actual` requirements before choosing an API (`BackHandler`, `onFocusChanged`)
4. **Think about data scope at design time** — "is this global or per-entity?" should be answered in the plan, not during implementation
5. **Use `updateChecklistTemplate()` (not `updateChecklist()`)** when adding items from detail screen — avoids fill ID regeneration from sync logic

## Related Documentation

- Plan: `docs/plans/2026-02-26-feat-overflow-menu-collapsible-completed-quick-add-plan.md`
- E2E patterns: `docs/solutions/test-failures/e2e-test-suite-stabilization.md`
- Room cascades: `docs/solutions/database-issues/room-cascade-delete-flow-race-condition.md`
- Item ID pattern: `docs/solutions/features/enforced-auto-generated-id-pattern.md`
