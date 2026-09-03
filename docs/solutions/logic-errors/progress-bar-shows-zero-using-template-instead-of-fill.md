---
title: Fix checklist progress calculation showing 0% despite checked items
category: logic-errors
tags: [checklist-progress, main-screen, state-management, progress-calculation, template-instance-pattern, kotlin-flow]
module: feature/home
symptoms:
  - Progress bar always displays 0% on main screen
  - Checked items not reflected in progress percentage
  - Progress shows incorrect value after checking items in fills
solved_date: 2026-01-25
severity: high
---

# Fix: Checklist Progress Bar Always Shows 0%

## Problem

On the main screen, the checklist progress bar always displayed **0%** even when items were checked in the detail view.

**Observable symptoms:**
- Progress bar empty regardless of actual progress
- Counter shows "0/N" for all checklists
- Detail screen shows correct progress, but main screen doesn't

## Root Cause

In the Kotlin Multiplatform app with Jetpack Compose, there's a fundamental architectural separation between:

- **`Checklist.items: List<ChecklistItem>`** - template items stored in the database with default state (always `checked = false`)
- **`ChecklistFill.items: List<ChecklistFillItem>`** - actual user progress data where the real checked state is persisted

The bug in `MainScreenContent.kt` was using the template items to calculate progress:

```kotlin
// BUG: Using template items which always have checked = false
val totalItems = checklist.items.size
val checkedItems = checklist.items.count { it.checked }  // Always counts 0!
```

This meant the progress calculation was always reading from the immutable template rather than the user's actual fill data. Even though users could check items in the detail view (which correctly updated the `ChecklistFill`), the main screen displayed 0% because it was reading stale template data.

**Key insight**: The app has a one-to-many relationship: one `Checklist` template can have multiple `ChecklistFill` records (one for each time the user "filled" the checklist). The default fill should be the source of truth for progress display on the main screen.

## Solution

### Step 1: Create a Data Class to Bridge Template and Progress

Create a new model that combines the template with its actual progress:

```kotlin
data class ChecklistWithProgress(
    val checklist: Checklist,
    val totalItems: Int,
    val checkedItems: Int
) {
    val progress: Float
        get() = if (totalItems > 0) checkedItems.toFloat() / totalItems else 0f
}
```

This class encapsulates both the template metadata and the computed progress metrics in one place.

### Step 2: Load Default Fill in the ViewModel

Modify the ViewModel to fetch the default fill for each checklist and use its items to calculate progress:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
private val checklistsWithProgress = repository.checklists.flatMapLatest { checklists ->
    if (checklists.isEmpty()) {
        flowOf(emptyList())
    } else {
        // Create a Flow for each checklist's progress
        val fillFlows = checklists.map { checklist ->
            repository.getDefaultFillByChecklistId(checklist.id).map { fill ->
                ChecklistWithProgress(
                    checklist = checklist,
                    // Use fill items if available, otherwise use template size
                    totalItems = fill?.items?.size ?: checklist.items.size,
                    // Count checked items from the fill, not the template
                    checkedItems = fill?.items?.count { it.checked } ?: 0
                )
            }
        }
        // Combine all flows to emit the complete list
        combine(fillFlows) { it.toList() }
    }
}
```

The key pattern here:
- For each checklist, fetch its default `ChecklistFill`
- If a fill exists, read `totalItems` and `checkedItems` from it
- If no fill exists (first time viewing), fall back to template size and 0 checked items
- Use `flatMapLatest` to reactively update progress when fills change

### Step 3: Update UI Components

Replace direct checklist references with the new `ChecklistWithProgress` type:

```kotlin
// Before
checklistItem.items.count { it.checked }  // WRONG

// After
checklistWithProgress.checkedItems        // CORRECT
checklistWithProgress.progress            // Use precomputed Float (0-1)
```

### Result

The main screen now correctly displays the actual user progress by reading from the `ChecklistFill` (user data) instead of the `Checklist` template (immutable reference). Progress updates reactively as the user checks/unchecks items in the detail view.

## Files Changed

| File | Change |
|------|--------|
| `MainScreenContract.kt` | Added `ChecklistWithProgress` data class |
| `MainScreenViewModel.kt` | Load default fills using `flatMapLatest` + `combine` |
| `MainScreenContent.kt` | Use `ChecklistWithProgress` instead of `Checklist` |
| `MainScreen.kt` | Updated callback signature |
| `ChecklistDetailViewModelTest.kt` | Fixed outdated test to use `defaultFill` |

## Prevention

### 1. Architectural Pattern Documentation

**Template/Instance Separation Pattern**: When your app has a "schema/instance" architecture, ensure clear distinction in code:

```kotlin
// TEMPLATE (definition, static)
data class Checklist(
    val id: Long,
    val name: String,
    val items: List<ChecklistItem>  // ← Always represents the template
)

// INSTANCE (user data, mutable)
data class ChecklistFill(
    val id: Long,
    val checklistId: Long,
    val items: List<ChecklistFillItem>  // ← Actual user state
)
```

**Critical Rule**: In presentation layers, always read instance/fill data when displaying user progress, completion status, or state-dependent UI. Never calculate progress from template items.

### 2. Type-Based Segregation

Create distinct view models or UI contracts that explicitly track which data source they use:

```kotlin
// ✓ GOOD: Explicit wrapper shows data comes from fill, not template
data class ChecklistWithProgress(
    val checklist: Checklist,           // Read-only reference
    val totalItems: Int,                // Derived from fill
    val checkedItems: Int,              // Derived from fill
)

// ✗ BAD: Unclear which data is used for progress
data class ChecklistDisplay(
    val checklist: Checklist,  // Template
    val fill: ChecklistFill?   // Instance - might be null or forgotten
)
```

### 3. ViewModel Composition Pattern

**Always compose instance data at ViewModel level**, not at UI level:

```kotlin
// ✓ GOOD: Load fill data in ViewModel, provide to UI
class MainScreenViewModel(repository: ChecklistRepository) {
    private val checklistsWithProgress = repository.checklists.flatMapLatest { ... }
}

// ✗ BAD: UI reads template data directly
@Composable
fun ChecklistCard(checklist: Checklist) {
    val progress = checklist.items.count { it.checked } / checklist.items.size  // WRONG!
}
```

## Checklist for Similar Issues

### Code Review Checklist

- [ ] **Data Source Verification**: When calculating progress/status, verify the code reads from instance/fill data, not template
- [ ] **Progress Calculation Isolation**: Progress metrics should only be calculated in ViewModel or contract class
- [ ] **Null Safety with Fallbacks**: When instance data is optional, verify fallback behavior is documented
- [ ] **Update Consistency**: When template is modified, check that instances are updated

### Testing Strategy

```kotlin
// Test: Progress reads from instance, not template
@Test
fun `progress calculation uses fill items, not template items`() {
    val checklist = Checklist(items = listOf(ChecklistItem("Item")))  // unchecked
    val fill = ChecklistFill(items = listOf(ChecklistFillItem("Item", checked = true)))

    val state = ChecklistWithProgress(
        checklist = checklist,
        totalItems = fill.items.size,
        checkedItems = fill.items.count { it.checked }
    )

    assertEquals(1f, state.progress)  // 1/1 from fill, not 0/1 from template
}
```

## Related Concepts

- **Template vs Instance Pattern** - Core architecture: Checklist (template) vs ChecklistFill (instance)
- **Kotlin Flow `flatMapLatest` + `combine`** - Reactive composition of multiple data streams
- **Room database relations** - One-to-many Checklist → ChecklistFill relationship
- **MVI state calculation** - ViewModel combines multiple Flows into single screenState

## Commit

```
fix(home): show progress from default fill on main screen
```
