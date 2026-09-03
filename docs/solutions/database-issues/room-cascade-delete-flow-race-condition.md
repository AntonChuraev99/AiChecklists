---
title: SQLiteConstraintException FOREIGN KEY failure after checklist deletion
category: database-issues
tags: [room, sqlite, foreign-key, cascade-delete, race-condition, flow, coroutines, job-cancellation]
symptoms:
  - SQLiteConstraintException with code 787 SQLITE_CONSTRAINT_FOREIGNKEY
  - Crash occurs after deleting a checklist
  - Error in ChecklistFillDao_Impl.insert method
  - App attempts to insert fill for non-existent checklist
module: feature/home
severity: critical
date_documented: 2026-01-25
---

# SQLiteConstraintException: FOREIGN KEY constraint failed after deleting checklist

## Problem Summary

After deleting a checklist, the app crashed with:

```
android.database.sqlite.SQLiteConstraintException: FOREIGN KEY constraint failed (code 787 SQLITE_CONSTRAINT_FOREIGNKEY)
    at ChecklistFillDao_Impl.insert(ChecklistFillDao_Impl.kt:54)
```

## Root Cause Analysis

The crash occurred due to a **race condition** between SQLite's CASCADE DELETE mechanism and Kotlin Flow's reactive data collection in `ChecklistDetailViewModel`.

The database schema defines a foreign key relationship between `checklist_fills` and `checklists` tables with `onDelete = ForeignKey.CASCADE`, meaning when a checklist is deleted, all its associated fills are automatically removed by SQLite.

When the user triggered checklist deletion, the following sequence occurred:

1. `deleteChecklist()` called `repository.deleteChecklist()` which executed `DELETE FROM checklists WHERE id = :id`
2. SQLite's CASCADE constraint automatically deleted all fills with that `checklistId`
3. The active `combine()` Flow in `loadData()` immediately received an emission with `defaultFill == null`
4. The old code detected the missing default fill and attempted to recreate it via `createMissingDefaultFill()`
5. This INSERT operation failed because the parent checklist no longer existed, violating the foreign key constraint

The fundamental issue was that the ViewModel's reactive Flow was still observing database changes during deletion. When the CASCADE removed fills, the Flow interpreted this as "the default fill is missing and needs recreation" rather than "the checklist is being deleted and we should stop observing."

## Solution

The fix involves two key changes: tracking the data loading job and canceling it before deletion.

### Step 1: Add Job Tracking

Store a reference to the coroutine job that collects database flows:

```kotlin
class ChecklistDetailViewModel(...) : AppViewModel<...>() {

    private var loadDataJob: Job? = null  // Track the loading job

    private fun loadData() {
        loadDataJob = viewModelScope.launch {  // Assign the job
            // ... existing combine/collect logic ...
        }
    }
}
```

### Step 2: Handle Null Default Fill Gracefully

When the default fill is null, transition to `NotFound` state instead of attempting recreation:

```kotlin
combine(
    repository.getDefaultFillByChecklistId(checklistId),
    repository.getAdditionalFillsByChecklistId(checklistId)
) { defaultFill, additionalFills ->
    defaultFill to additionalFills
}.collect { (defaultFill, additionalFills) ->
    if (defaultFill == null) {
        _screenState.value = ChecklistDetailState.NotFound  // Don't recreate!
        return@collect
    }
    updateOrCreateContentState(checklist, defaultFill, additionalFills.size)
}
```

### Step 3: Cancel Flow Before Deletion

Cancel the data loading job before executing the delete operation:

```kotlin
private fun deleteChecklist() {
    val state = _screenState.value
    if (state !is ChecklistDetailState.Content) return

    updateContentState { it.copy(showDeleteConfirmation = false) }
    loadDataJob?.cancel()  // Cancel BEFORE delete to prevent race condition

    viewModelScope.launch {
        repository.deleteChecklist(state.checklist)
        navigator.onBack()
    }
}
```

### Step 4: Remove Unnecessary Code

The `createMissingDefaultFill()` function and `isCreatingDefaultFill` flag were removed entirely. The default fill is already created in `ChecklistRepositoryImpl.addChecklist()`.

## Why It Works

This solution eliminates the race condition by ensuring the Flow collector is stopped before any database mutations that would trigger CASCADE operations. By calling `loadDataJob?.cancel()` before `repository.deleteChecklist()`, we guarantee that:

1. The `combine()` collector is no longer active when CASCADE DELETE removes the fills
2. No null emissions are processed after deletion begins
3. No attempt is made to recreate fills for a deleted checklist

## Files Changed

| File | Change |
|------|--------|
| `feature/home/.../ChecklistDetailViewModel.kt` | Added job tracking, cancel before delete, removed workaround |
| `feature/checklist/.../ChecklistDao.kt` | Added `@Update` method |
| `feature/checklist/.../ChecklistRepositoryImpl.kt` | Use `update()` instead of `insert()` for updates |

## Prevention Strategies

### Coroutine Lifecycle Management

- **Cancel flows before deletion**: Always cancel any active Flow collection on an entity before deleting it
- **Use Job references**: Store flow collection jobs in variables so they can be explicitly cancelled
- **Use `takeWhile` operator**: Auto-stop collection when entity becomes invalid:
  ```kotlin
  repository.getChecklistFlow(id)
      .takeWhile { it != null }
      .collect { ... }
  ```

### Architecture Pattern

- **Deletion state flag**: Set `isDeleting = true` before deletion, check in collectors
- **Navigation-first pattern**: Navigate away before deletion completes (pops ViewModel, stops flows)
- **Defensive null handling**: Never perform write operations when parent entity is null

### Code Review Checklist

- [ ] Does this feature have a Flow that observes an entity that can be deleted?
- [ ] What happens when the Flow emits null after deletion?
- [ ] Are there any INSERT/UPDATE operations triggered by Flow emissions?
- [ ] Is the Flow cancelled BEFORE the delete operation starts?
- [ ] Does this entity have CASCADE DELETE children? Are there Flows on children?

### Testing Recommendations

```kotlin
@Test
fun `delete during active flow collection should not crash`() = runTest {
    // Start observing
    val job = launch { viewModel.checklistFlow.collect { } }
    advanceUntilIdle()

    // Delete while observing
    viewModel.deleteChecklist()
    advanceUntilIdle()

    // Should complete without exception
    job.cancel()
}
```

## Key Principle

> Treat deletion as a terminal state. Once deletion is initiated, no other operations should be performed on that entity or its children. Cancel observers first, navigate away, then delete.

## Related Documentation

- [MVI Pattern Architecture](../architecture/mvi-pattern.md)
- [KMP Patterns](../architecture/kmp-patterns.md)
- [Plan Document](../../plans/2026-01-25-fix-foreign-key-crash-after-delete-plan.md)
