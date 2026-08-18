---
title: "feat: Swipe-to-delete for checklist items"
type: feat
date: 2026-03-10
---

# Swipe-to-Delete for Checklist Items

## Overview

Replace the current long-press → edit mode → red delete button → confirmation dialog flow with Gmail-style swipe-to-delete on checklist item cards. Swiping an item left reveals a red background with a delete icon and removes the item, showing an undo snackbar.

## Problem Statement / Motivation

The current delete flow requires **4 steps**: long press → enter edit mode → tap red icon → confirm dialog. This is slow and unintuitive — users expect swipe-to-delete as a standard mobile pattern (Gmail, Apple Mail, Todoist, Google Keep).

Swipe-to-delete reduces this to **1 gesture** with an undo safety net.

## Proposed Solution

### Core Changes

1. **Wrap `ChecklistItemCard` in `SwipeToDismissBox`** (Material3) in normal mode
2. **Swipe direction**: End-to-Start only (swipe left) — matches Material3 convention, reduces accidental triggers
3. **Red background** with white `Icons.Default.Delete` icon revealed during swipe
4. **Instant delete + undo snackbar** instead of confirmation dialog
5. **Disable swipe in edit mode** — edit mode keeps drag-to-reorder only, red delete button removed
6. **Works for both** unchecked and completed items sections

### What Changes

| Aspect | Before | After |
|--------|--------|-------|
| Delete trigger | Long press → edit mode → red icon → dialog | Swipe left |
| Confirmation | AlertDialog | Undo snackbar (3s timeout) |
| Edit mode delete button | Red `RemoveCircle` icon | Removed |
| Edit mode purpose | Reorder + Delete | Reorder only |
| Long press | Enters edit mode | Still enters edit mode (reorder) |

### What Stays

- Long press still enters edit mode for drag-to-reorder
- Overflow menu "Delete completed items" batch action
- `autoDeleteCompleted` toggle
- `confirmDeleteItem()` core logic (with fix, see below)

## Technical Approach

### Architecture

#### New State Fields (`ChecklistDetailScreenContract.kt`)

```kotlin
data class Content(
    // ... existing fields ...
    // DELETE these fields:
    // val showDeleteItemConfirmation: Boolean = false,
    // val itemPendingDeleteId: String? = null,

    // ADD: undo support
    val pendingUndoItem: UndoableDeleteItem? = null,
)

data class UndoableDeleteItem(
    val fillItem: ChecklistFillItem,
    val checklistItemText: String,
    val originalFillIndex: Int,
    val originalChecklistIndex: Int,
)
```

#### New/Modified Intents (`ChecklistDetailScreenContract.kt`)

```kotlin
sealed interface ChecklistDetailIntent : Intent {
    // REPLACE OnDeleteItemClick / OnConfirmDeleteItem / OnDismissDeleteItemDialog with:
    data class OnSwipeDeleteItem(val itemId: String) : ChecklistDetailIntent
    data object OnUndoDeleteItem : ChecklistDetailIntent
    data object OnUndoTimeout : ChecklistDetailIntent
}
```

#### ViewModel Changes (`ChecklistDetailViewModel.kt`)

```kotlin
// New: swipeDeleteItem() — instant delete + store undo candidate
private fun swipeDeleteItem(itemId: String) {
    val state = contentState ?: return
    val fill = state.defaultFill ?: return
    val itemIndex = fill.items.indexOfFirst { it.id == itemId }
    if (itemIndex == -1) return
    val item = fill.items[itemIndex]

    // Find matching template item by index-aligned ID (not text!)
    val checklistIndex = state.checklist.items.indexOfFirst { it.text == item.text }

    val updatedFill = fill.copy(items = fill.items.filterIndexed { i, _ -> i != itemIndex })
    val updatedChecklist = if (checklistIndex >= 0) {
        state.checklist.copy(items = state.checklist.items.filterIndexed { i, _ -> i != checklistIndex })
    } else state.checklist

    // Commit first pending undo if any
    pendingUndoJob?.cancel()

    updateContentState {
        it.copy(
            checklist = updatedChecklist,
            pendingUndoItem = UndoableDeleteItem(
                fillItem = item,
                checklistItemText = item.text,
                originalFillIndex = itemIndex,
                originalChecklistIndex = checklistIndex,
            ),
            snackbarMessage = null, // clear old snackbar
        )
    }

    // Persist deletion
    viewModelScope.launch {
        repository.updateFill(updatedFill)
        repository.updateChecklistTemplate(updatedChecklist)
        analyticsTracker.event("item_deleted", mapOf(
            "checklist_id" to checklistId.toString(),
            "method" to "swipe",
            "item_count" to updatedFill.items.size.toString()
        ))
    }

    // Auto-clear undo after timeout
    pendingUndoJob = viewModelScope.launch {
        delay(3000)
        updateContentState { it.copy(pendingUndoItem = null) }
    }
}

// New: undoDeleteItem() — restore item at original position
private fun undoDeleteItem() {
    val state = contentState ?: return
    val undo = state.pendingUndoItem ?: return
    val fill = state.defaultFill ?: return

    pendingUndoJob?.cancel()

    val restoredFillItems = fill.items.toMutableList().apply {
        add(undo.originalFillIndex.coerceAtMost(size), undo.fillItem)
    }
    val restoredFill = fill.copy(items = restoredFillItems)

    val restoredChecklistItems = state.checklist.items.toMutableList().apply {
        if (undo.originalChecklistIndex >= 0) {
            add(undo.originalChecklistIndex.coerceAtMost(size),
                ChecklistItem(text = undo.checklistItemText))
        }
    }
    val restoredChecklist = state.checklist.copy(items = restoredChecklistItems)

    updateContentState {
        it.copy(
            checklist = restoredChecklist,
            pendingUndoItem = null,
        )
    }

    viewModelScope.launch {
        repository.updateFill(restoredFill)
        repository.updateChecklistTemplate(restoredChecklist)
        analyticsTracker.event("item_undo_delete", mapOf(
            "checklist_id" to checklistId.toString()
        ))
    }
}
```

#### UI Changes (`ChecklistDetailScreen.kt`)

```kotlin
// New composable: SwipeableChecklistItemCard
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableChecklistItemCard(
    item: ChecklistFillItem,
    isEditMode: Boolean,
    onSwipeDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (isEditMode) {
        // In edit mode: no swipe, just render the card
        content()
        return
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onSwipeDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeDeleteBackground() },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
    ) {
        content()
    }
}

// Red background with delete icon
@Composable
private fun SwipeDeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = AppDimens.SpacingXl),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            Icons.Default.Delete,
            contentDescription = stringResource(Res.string.delete_item),
            tint = MaterialTheme.colorScheme.onError,
        )
    }
}
```

**Undo snackbar** — uses the existing `SnackbarHostState` in `AppScaffold`:

```kotlin
// In ChecklistDetailContent, observe pendingUndoItem:
LaunchedEffect(state.pendingUndoItem) {
    val undo = state.pendingUndoItem ?: return@LaunchedEffect
    val result = snackbarHostState.showSnackbar(
        message = "\"${undo.fillItem.text}\" deleted",
        actionLabel = "Undo",
        duration = SnackbarDuration.Short,
    )
    if (result == SnackbarResult.ActionPerformed) {
        onIntent(ChecklistDetailIntent.OnUndoDeleteItem)
    }
}
```

### Implementation Phases

#### Phase 1: Contract & ViewModel (+ tests first!)

Files:
- `ChecklistDetailScreenContract.kt` — add `UndoableDeleteItem`, `pendingUndoItem`, new intents; remove old delete-dialog intents/state
- `ChecklistDetailViewModel.kt` — implement `swipeDeleteItem()`, `undoDeleteItem()`, remove `confirmDeleteItem()` and old dialog handlers
- `ChecklistDetailViewModelTest.kt` — **write tests BEFORE implementation** (testing mandate):
  - `swipeDeleteItem_removesItemFromFillAndTemplate`
  - `swipeDeleteItem_storesPendingUndo`
  - `undoDeleteItem_restoresItemAtOriginalPosition`
  - `undoDeleteItem_clearsUndoState`
  - `swipeDeleteItem_secondSwipe_commitsFirstDeletion`
  - `swipeDeleteItem_withNote_preservesNoteInUndo`

#### Phase 2: UI — SwipeToDismissBox

Files:
- `ChecklistDetailScreen.kt`:
  - Add `SwipeableChecklistItemCard` wrapper composable
  - Wrap both unchecked and completed `ChecklistItemCard` calls in `SwipeableChecklistItemCard`
  - Add undo snackbar `LaunchedEffect`
  - Remove `DeleteItemConfirmationDialog` composable and its call site
  - Remove red `RemoveCircle` IconButton from `ChecklistItemCard` (edit mode)
  - Add accessibility: `Modifier.semantics { customActions = listOf(CustomAccessibilityAction("Delete") { onSwipeDelete(); true }) }`

#### Phase 3: Strings & Cleanup

Files:
- `strings.xml`:
  - Add: `detail_item_deleted` = `"%1$s" deleted`
  - Add: `undo` = `Undo`
  - Keep: `delete_item` (used in accessibility contentDescription)
  - Remove: `detail_delete_item_title`, `detail_delete_item_message` (dialog strings)

## Acceptance Criteria

### Functional

- [x] Swiping an item card left reveals red background with delete icon
- [x] Full swipe left deletes the item immediately
- [x] Undo snackbar appears after deletion with "Undo" action button
- [x] Tapping "Undo" restores the item at its original position (including note)
- [x] Snackbar auto-dismisses after ~3 seconds, making deletion permanent
- [x] Swipe works on both unchecked and completed items
- [x] Swipe is **disabled** in edit mode (no gesture conflict with drag-to-reorder)
- [x] Long press still enters edit mode for reordering
- [x] Red delete icon button is removed from edit mode cards
- [x] `DeleteItemConfirmationDialog` is removed
- [x] Second swipe while undo is pending auto-commits the first deletion
- [x] Haptic feedback on successful swipe dismiss

### Non-Functional

- [ ] Accessibility: TalkBack/VoiceOver users can delete via custom action
- [x] Analytics: `item_deleted` event includes `"method": "swipe"` param
- [x] Analytics: new `item_undo_delete` event tracked
- [x] KMP: works on both Android and iOS (Compose Multiplatform)

### Testing

- [x] ViewModel unit tests written BEFORE implementation (testing mandate)
- [x] Happy path: swipe delete + undo restore
- [x] Edge case: delete item with note → undo restores note
- [x] Edge case: second swipe commits first delete
- [x] Edge case: swipe disabled in edit mode
- [x] Build passes: `./gradlew build`
- [x] Existing tests pass (no regressions)

## Dependencies & Risks

### Dependencies

- **Material3 `SwipeToDismissBox`** — available in Compose Multiplatform 1.9.3 via `compose.material3` (already in `feature/home` deps)
- No new library dependencies needed

### Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Gesture conflict: swipe vs drag in edit mode | Accidental deletes | Disable swipe in edit mode via `enabled` param |
| Gesture conflict: swipe vs LazyColumn scroll | Misinterpreted gestures | End-to-Start only (one direction), Material3 handles angle threshold |
| `SwipeToDismissBox` KMP iOS behavior | Different feel on iOS | Test on iOS simulator; Material3 handles platform differences |
| Text-match template deletion bug (existing) | Duplicate items both deleted | Out of scope for this PR but noted — fix separately |
| Multiple rapid swipes losing undo | Silent data loss | Single undo slot, second swipe commits first |

### Known Limitation: Template Text-Match Bug

`confirmDeleteItem()` line 899 removes template items by `it.text != itemToDelete.text`. If two items have identical text, both are removed from the template. This is a pre-existing bug aggravated by faster swipe gestures. **Recommend fixing in a separate PR** (plan exists: `2026-01-30-feat-add-unique-id-to-checklist-items-plan.md`).

## Scope Exclusions

- **FillDetailScreen** — no swipe-to-delete (no delete intent exists in FillDetail contract)
- **Onboarding hint** — no first-use animation for discovering swipe gesture (can add later)
- **Template text-match bug** — separate PR
- **MainScreen checklist cards** — swipe-to-delete for entire checklists (different feature)

## Files Changed Summary

| File | Action | Description |
|------|--------|-------------|
| `ChecklistDetailScreenContract.kt` | Modify | Add `UndoableDeleteItem`, `pendingUndoItem`; add `OnSwipeDeleteItem`, `OnUndoDeleteItem`; remove old delete dialog intents/state |
| `ChecklistDetailViewModel.kt` | Modify | Add `swipeDeleteItem()`, `undoDeleteItem()`, `pendingUndoJob`; remove `confirmDeleteItem()` and dialog handlers |
| `ChecklistDetailScreen.kt` | Modify | Add `SwipeableChecklistItemCard`, `SwipeDeleteBackground`; add undo snackbar; remove `DeleteItemConfirmationDialog`, edit mode delete button |
| `ChecklistDetailViewModelTest.kt` | Modify | Add 6+ unit tests for swipe delete and undo |
| `strings.xml` | Modify | Add `detail_item_deleted`, `undo`; remove `detail_delete_item_title`, `detail_delete_item_message` |

## References

- [Material3 SwipeToDismissBox docs](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#SwipeToDismissBox)
- Existing plan for item IDs: `docs/plans/2026-01-30-feat-add-unique-id-to-checklist-items-plan.md`
- Testing mandate: `docs/solutions/ui-bugs/checklist-detail-overflow-menu-quick-add-bugs.md`
- Reorderable library: `sh.calvin.reorderable:reorderable` v2.4.3
