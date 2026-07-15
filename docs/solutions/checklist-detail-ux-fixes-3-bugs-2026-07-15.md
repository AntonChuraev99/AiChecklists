---
title: "ChecklistDetail: swipe-background flicker, undo dropping folder, delete-folder in overflow, persist-after-pop race"
date: 2026-07-15
type: bug-fix
modules: [feature/home]
keywords: [ChecklistDetail, SwipeToDismissBox, backgroundContent, bouncy-spring, undo-snapshot, ChecklistItem, parentId, folder-delete, viewModelScope, appScope, Nav3-entry-scope, test-flake, DataStore]
project: gisti
---

# ChecklistDetail — four fixes, 2026-07-15

Commits: `37b30bd4`, `ad8d0d7c`, `1430fac2`, `592756ec` (base `b82e0ca7`).

Bugs 1–3 came from the user; bug 4 was found by the bug-pattern review gate and was *introduced* by bug 3's fix.

## 1. Red delete slab flickered when checking an item

**Root cause.** `SwipeToDismissBox` (material3 1.9.0) renders `backgroundContent` **unconditionally** — its source is `Row(content = backgroundContent, modifier = Modifier.matchParentSize())` placed first in the Box. The slab sits under every card always; it is invisible only because the card covers it.

The completion flourish in `ChecklistItemCard` breaks that cover:

```kotlin
completionScale.animateTo(1.06f, tween(durationMillis = 110))
completionScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
```

`DampingRatioMediumBouncy` is underdamped — by definition it oscillates around the target rather than easing into it, so returning to `1f` it dips **below** 1f for a few frames. `graphicsLayer` scale is draw-only, so the card's slot stays the same size while the drawn card shrinks inside it, and the slab shows around the edges.

**Fix** (`ChecklistDetailScreen.kt`, `SwipeableChecklistItemCard`):

```kotlin
val isSwiping by remember {
    derivedStateOf { dismissState.dismissDirection != SwipeToDismissBoxValue.Settled }
}

SwipeToDismissBox(
    state = dismissState,
    backgroundContent = { if (isSwiping) SwipeDeleteBackground() },
    ...
)
```

`dismissDirection`, not `targetValue`. Per the M3 source it is offset-derived and NaN-safe:

```kotlin
val dismissDirection: SwipeToDismissBoxValue
    get() = when {
        offset == 0f || offset.isNaN() -> SwipeToDismissBoxValue.Settled
        offset > 0f -> SwipeToDismissBoxValue.StartToEnd
        else -> SwipeToDismissBoxValue.EndToStart
    }
```

so the slab appears from the first pixel of a drag. `targetValue` only flips past the positional threshold — the slab would stay hidden while the finger is already dragging, i.e. a swipe regression. `derivedStateOf` keeps recomposition to the true/false flip instead of every drag frame.

**Why not dampen the bounce.** Clamping the scale (`coerceAtLeast(1f)`) or dropping the spring hides the symptom and leaves the slab lying under every card, ready to resurface on the next draw-only effect (shadow, rounding, a future animation). The defect is that the slab exists when no swipe is happening.

## 2. Undo of a swipe-delete threw the item out of its folder

**Root cause.** Folder membership lives **only on the template node** (`ChecklistItem.parentId`, null = root; `type` = ITEM/FOLDER). The fill is flat and links back via `ChecklistFillItem.templateItemId`. The undo snapshot kept only the node's *text*:

```kotlin
data class UndoableDeleteItem(
    val fillItem: ChecklistFillItem,
    val checklistItemText: String,   // ← everything structural is gone
    val originalFillIndex: Int,
    val originalChecklistIndex: Int,
)
```

and `undoDeleteItem` rebuilt the node from it:

```kotlin
ChecklistItem(text = undo.checklistItemText)   // constructor defaults
```

One omission, four losses: `parentId = null` (the item the user saw jump to the checklist root), `type = ITEM` (a deleted folder would return as a plain item), `priority = 0` (star silently lost), `weekday = null`.

**Fix.** Snapshot the node whole and re-insert it **as-is, original id included**:

```kotlin
// ChecklistDetailScreenContract.kt
data class UndoableDeleteItem(
    val fillItem: ChecklistFillItem,
    val checklistItem: ChecklistItem?,   // null = legacy row with no linked node (index == -1)
    val originalFillIndex: Int,
    val originalChecklistIndex: Int,
)

// swipeDeleteItem — getOrNull covers checklistIndex == -1
checklistItem = state.checklist.items.getOrNull(checklistIndex),

// undoDeleteItem
val restoredTemplateItem = undo.checklistItem
val restoredFillItem = if (restoredTemplateItem != null) {
    undo.fillItem.withTemplateItemId(restoredTemplateItem.id)  // no-op if already linked; repairs a legacy row
} else {
    undo.fillItem
}
```

**Original id, not a fresh one.** The old code minted a new id and re-linked the fill row to it. That holds only while the node has no children: a descendant's `parentId` still points at the old id, so undoing a deleted *folder* would orphan its whole subtree. Reusing the id removes that class. `withTemplateItemId` is kept: a no-op for a linked row, and it still repairs a legacy unlinked one.

Note `withTemplateItemId` is a `ChecklistFillItem` method — the fill row carries the link, the template node does not.

**Tests** (`ChecklistDetailFolderAddOrderTest`, red-first — all four failed before the fix): parent folder restored, leaf renders inside the folder rather than at root, `priority` kept, `weekday` kept, plus an invariant guard that the restored fill row links to a node that really exists.

## 3. Overflow sheet offered "Delete checklist" from inside a folder

Deleting the whole checklist from within one of its folders reads as a mis-tap; the folder is what the user is looking at.

The machinery already existed — `OnDeleteFolder(folderId)` intent, cascade count, and `DeleteFolderConfirmationDialog` rendered at screen level off `state.pendingFolderDeleteId` (so it is not tied to the folder-actions sheet and came along for free). The fix is 15 lines in `ChecklistDetailScreen.kt`:

```kotlin
// OverflowMenuSheet param
insideFolder: Boolean,

// the destructive row
text = stringResource(
    if (insideFolder) Res.string.folder_delete else Res.string.delete_checklist
),

// call site — same flag drives label AND action, so they cannot drift apart
insideFolder = state.currentFolderId != null,
onDeleteClick = {
    onIntent(ChecklistDetailIntent.OnDismissOverflowSheet)
    val folderId = state.currentFolderId
    if (folderId != null) onIntent(ChecklistDetailIntent.OnDeleteFolder(folderId))
    else onIntent(ChecklistDetailIntent.OnDeleteChecklistClick)
},
```

Passing a separate `onDeleteFolderClick` callback was rejected: the label would then be decided inside the sheet and the action at the call site — a ready-made setup for "says Delete folder, deletes the checklist".

## 4. The delete was persisted on a scope its own navigation had just killed

**Found by the bug-pattern gate, introduced by fix 3.** `confirmFolderDelete` pops synchronously and persists afterwards:

```kotlin
val viewingDeletedSubtree = currentFolderId != null && currentFolderId in removeIds
if (viewingDeletedSubtree) {
    poppedMissingLevel = true
    updateContentState { ... }
    navigator.onBack()        // synchronous, to avoid rendering a ghost level
}
...
viewModelScope.launch {       // ← scope of the entry just popped
    for (leaf in fillItemsToCancel) { reminderScheduler.cancelItemReminder(...) }
    repository.updateFill(updatedFill)
    repository.updateChecklistTemplate(updatedChecklist)
}
```

A ChecklistDetail ViewModel is keyed per checklist+folder (`koinViewModel(key = "checklist_detail_${checklistId}_${currentFolderId ?: "root"}")`, `ChecklistDetailScreen.kt`); each folder open pushes its own Nav3 entry (`App.kt`); `AppNavigatorImpl.onBack()` is `backStack.removeAt(backStack.size - 1)`. Pop → `onCleared()` → `viewModelScope` cancelled at the first suspension point above.

**Why it was dormant until fix 3.** The only call site was the folder-actions sheet, which deletes a **child** folder on the current level, so `currentFolderId in removeIds` was always false through the UI. "Delete folder" in the overflow sheet fires it from **inside** the folder — the branch now runs every time.

**Symptoms it would produce:** folder gone visually, back on the parent it is still there (nothing written); or a partial write (`updateFill` landed, `updateChecklistTemplate` did not → template keeps folder + descendants, fill has no rows → folder renders empty, 0-of-N); or the alarm-cancel loop suspends first → reminders orphaned on deleted items. Wider window on wasmJs, where the OPFS worker is slower than Room.

**Fix.** Persist on a scope that outlives the entry — `single<CoroutineScope>` from `core/common/impl/.../CommonCoreModule.kt` (precedent: `SplashViewModel`), injected as the last constructor param and used in place of `viewModelScope.launch` in `confirmFolderDelete` only. This is the documented approach for work that must outlive its caller; `launch(NonCancellable)` would detach the coroutine from all control instead.

**Regression test** (`ChecklistDetailFolderActionsTest`): `confirmFolderDelete_whileInsideDeletedFolder_persistsDespiteViewModelBeingCleared`. It was verified to discriminate — reverting to `viewModelScope.launch` fails exactly it ("template delete must survive the ViewModel being cleared by its own pop"). The pre-existing `..._navigatesBack` passes either way, so it does not protect this. Cancellation is driven through the public API: `ViewModelProvider.create(store, viewModelFactory { ... })[VM::class]` then `store.clear()`.

## Bonus: the module's test suite was unbreakable-flaky, now it isn't

`:feature:home:testAndroidHostTest` was **285 tests / 33 failed**, with a different victim set each run — during this session it was measured at 21, 31 and 33 depending on the run, which made a full run useless as a signal (and nearly caused a real regression to be misread as noise; a baseline run settled it in 14 seconds).

Root cause: `PreferenceDataStoreFactory.createWithPath { }` defaults to a real `Dispatchers.IO` scope, whose work outlives `runTest` and resumes VM coroutines on `Dispatchers.Main` after `resetMain()` → `UncaughtExceptionsBeforeTest` in whichever test JUnit ran next. Pinned to `backgroundScope` across the fixtures → **286 / 0**, stable over 4 consecutive `--rerun-tasks` runs.

**Test double for `appScope`** (`TestAppScope.kt`): `CoroutineScope(backgroundScope.coroutineContext + testDispatcher)`. Three properties are needed at once and each obvious one-liner misses exactly one:

| Variant | Job independent of VM | Cancelled with test | Eager |
|---|---|---|---|
| `CoroutineScope(testDispatcher)` | yes | **no** — outlives `runTest`, cascades | yes |
| `backgroundScope` | yes | yes | **no** — Standard dispatcher, asserts read unwritten state |
| `backgroundScope.coroutineContext + testDispatcher` | yes | yes | yes |

`backgroundScope.coroutineContext` supplies the Job; `+ testDispatcher` overrides the dispatcher to the same Unconfined one Main uses, restoring the eager behaviour the existing asserts were written against.

## Files

Code:
- `feature/home/.../detail/ChecklistDetailScreen.kt`
- `feature/home/.../detail/ChecklistDetailScreenContract.kt`
- `feature/home/.../detail/ChecklistDetailViewModel.kt`
- `feature/home/.../di/HomeFeatureModule.kt`

Tests:
- `feature/home/src/commonTest/.../detail/ChecklistDetailFolderAddOrderTest.kt`
- `feature/home/src/commonTest/.../detail/ChecklistDetailFolderActionsTest.kt`
- `feature/home/src/commonTest/.../detail/ChecklistDetailViewModelTest.kt`
- `feature/home/src/commonTest/.../detail/TestAppScope.kt` (new)
- 7 further `ChecklistDetail*Test.kt` fixtures (DataStore scope pin)

Related: `memory/checklist-folder-unlinked-row-render-order.md`, `memory/checklist-detail-optimistic-state-sync.md`, `docs/solutions/checklist-nested-folders-architecture-2026-06-13.md`.
