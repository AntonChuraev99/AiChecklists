# Chat-dock add "lands in middle" with folders — diagnosis (2026-06-30)

## Status: diagnosed + partial fix applied. Screen patch pending main agent (scope-guarded).

## Root cause
VM is CORRECT: new item is always appended LAST in `levelNodes`/`visibleFillItemIds` (folder mode uses
TEMPLATE order via `ChecklistTree.childrenOf`, new template item appended last → last among siblings).
Proven by 2 green tests covering optimistic + post-reload (fake re-emits on write).

"Middle" only happens with **separateCompleted = ON** (NOT folders — reproduces without folders):
- Screen renders `localNodes` (active/unchecked) then a "Completed" section BELOW.
- New (unchecked) item correctly lands as last ACTIVE row, ABOVE the completed section → by-design.
- Post-add auto-scroll (`ChecklistDetailScreen.kt` LaunchedEffect(addedItemSignal)) targets
  `totalItemsCount - 1` = last COMPLETED row → overshoots PAST the new item → user perceives "middle".

Secondary VM bug (FIXED): optimistic update in `addItemWithParse` updated `levelNodes`/`visibleFillItemIds`
but NOT `defaultFill` → new leaf had no backing row for 1 frame (fillItemById miss → can't render) and
auto-scroll saw stale count.

## Files changed (APPLIED, build green, full :feature:home:testAndroidHostTest GREEN)
- `feature/home/.../detail/ChecklistDetailViewModel.kt` — addItemWithParse optimistic block: now
  `val optimisticFill = updatedFill.withSortedItems()` + `defaultFill = optimisticFill` in the copy{}
  (mirrors updateOrCreateContentState exactly).
- `feature/home/src/commonTest/.../detail/ChecklistDetailFolderAddOrderTest.kt` — NEW, 3 tests:
  - separateCompletedOff → new item last in levelNodes (green, regression guard)
  - separateCompletedOn → new item last among UNCHECKED (green, regression guard)
  - optimisticState_defaultFillBacksEveryLevelNodeLeaf → RED before VM fix, GREEN after (desync guard)

## PENDING: ChecklistDetailScreen.kt scroll patch (main applies — user editing it concurrently)
Replace LaunchedEffect(state.addedItemSignal) body: target last ACTIVE node index
(headerCount + activeNodeCount - 1) instead of totalItemsCount-1. Full patch in final report.
NOTE: coordinator confirms scroll patch already applied.

## Bug 7 (delete-then-add order corruption + placement-anim jumps) — FIXED
Same optimistic-desync in the 3 delete/undo handlers. ChecklistDetailViewModel.kt:
- swipeDeleteItem copy: added optimisticFill = updatedFill.withSortedItems() + defaultFill +
  buildFolderState visibleFillItemIds/levelNodes (kept pendingUndoItem).
- deleteItemFromSheet copy: same (kept itemDetailsSheetFor = null).
- undoDeleteItem copy: optimistic base = restoredFill (re-linked), defaultFill + folderState
  (kept pendingUndoItem = null).
Tests added to ChecklistDetailFolderAddOrderTest.kt (4): swipeDelete/sheetDelete optimistic-absent,
deleteThenAdd user-repro (RED->GREEN), undoDelete restore. All 4 RED on stock VM (git stash verified),
GREEN after fix. Full :feature:home:testAndroidHostTest GREEN (one DataStore-I/O flake on undoDelete
under full-suite load, did not reproduce in 3 reruns). No screen change needed for Bug 7.
