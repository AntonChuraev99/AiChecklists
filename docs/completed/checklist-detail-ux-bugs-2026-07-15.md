# ChecklistDetail UX Bug Fixes (commonMain)

**Статус:** Done
**Дата старта:** 2026-07-15
**Start SHA:** b82e0ca7
**Project:** gisti
**Тип:** bug-fix
**Сложность:** Standard
**Impact:** Medium
**Затронутые модули:** feature/home (commonMain, commonTest)

⚠ INIT phase was skipped — the tasks arrived as quick bug reports and grew into Standard along the way. This doc was reconstructed at COMPLETE, so counters are approximate.

## Цель (продуктовая)

Three user-reported UX defects on ChecklistDetail (Android + Web): (1) red delete-slab flickering when checking an item off, (2) undo of a deleted item throwing it out of its folder, (3) overflow sheet offering "Delete checklist" while inside a folder. A fourth bug — a persist-after-pop race — was found by the review gate and had been *introduced* by fix 3.

## Технический план

1. **Slab flicker:** `SwipeToDismissBox` renders `backgroundContent` unconditionally; the completion scale-pop settles on a bouncy (underdamped) spring that dips below 1f → the drawn card shrinks inside its slot → slab shows. Gate the slab on `dismissDirection != Settled` (offset-derived, so it still appears from the first drag pixel).
2. **Undo folder loss:** `UndoableDeleteItem` snapshotted only `checklistItemText`; `undoDeleteItem` rebuilt the node via `ChecklistItem(text = ...)` → constructor defaults wiped `parentId`/`type`/`priority`/`weekday`. Snapshot the `ChecklistItem` whole; re-insert as-is with the original id.
3. **Delete folder from inside:** intent, cascade count and confirm dialog already existed. Add `insideFolder` param; label and action branch on the same `state.currentFolderId`.
4. **(found by gate) Persist-after-pop:** `confirmFolderDelete` pops its entry then persists on `viewModelScope` — a VM keyed per checklist+folder, so the pop cancels it. Move the persist to the app-wide scope.

## Лог итераций

### Итерация 1 — 2026-07-15 — main + @test-expert (red-first), @knowledge-scout

**Что сделано:**
1. `37b30bd4` — slab flicker: `backgroundContent = { if (isSwiping) SwipeDeleteBackground() }`, `isSwiping` = `derivedStateOf { dismissState.dismissDirection != Settled }`.
2. `ad8d0d7c` — undo folder loss: `UndoableDeleteItem.checklistItem: ChecklistItem?` replaces `checklistItemText: String`; node re-inserted as-is (original id → a descendant's `parentId` keeps pointing at a live node). 4 red-first repro tests written by @test-expert BEFORE the fix (validated by patching a stub: all went green, stub reverted).
3. `1430fac2` — overflow sheet: `insideFolder` param; `folder_delete` label + `OnDeleteFolder(folderId)`, both off `state.currentFolderId`.
4. `592756ec` — persist-after-pop race: `appScope: CoroutineScope` (from `single<CoroutineScope>` in `core:common:impl`, precedent `SplashViewModel`) injected; `confirmFolderDelete` persists there instead of `viewModelScope`. Discriminating regression test added.

**Почему так:**
1. Dampening the bounce would hide the symptom and leave the slab under every card, ready to resurface on the next draw-only effect. `dismissDirection` over `targetValue` — the latter only flips past the dismiss threshold, so the slab would be missing while the finger drags.
2. Rebuilding a node from text is the defect itself; the snapshot must carry structure. Original id over a fresh one: a fresh id orphans the subtree when the deleted node is a folder.
3. One flag drives both label and action so they cannot drift ("says Delete folder, deletes the checklist").
4. `appScope` is the documented approach for work that must outlive its caller; `launch(NonCancellable)` would detach the coroutine from all control.

**Баги/проблемы:**
- Bug 4 was dormant: before `1430fac2` the only `OnDeleteFolder` call site deleted a **child** folder, so `viewingDeletedSubtree` was never true through the UI. Fix 3 made the branch live on every use.
- Two failed attempts at the test fixtures (`CoroutineScope(testDispatcher)` → leaks past `runTest`; `backgroundScope` → Standard dispatcher, asserts read unwritten state). Stopped after two rather than guessing a third, and handed it to @test-expert, who landed `backgroundScope.coroutineContext + testDispatcher`.
- A full-module baseline run (33 failed **without** any of these changes, vs 21 and 31 with) prevented misreading a pre-existing flake as a fresh regression.

## Валидация

- `:feature:home:testAndroidHostTest` — **286 tests, 0 failed, 0 skipped**, re-verified with `--rerun-tasks`. Was 285/33 before: `PreferenceDataStoreFactory.createWithPath { }` took a real `Dispatchers.IO` scope that outlived `runTest` and resumed VM coroutines after `resetMain()`. Pinned to `backgroundScope`.
- Regression test for bug 4 verified to discriminate: reverting to `viewModelScope.launch` fails exactly it.
- Debug APK installed and exercised on Pixel_9 by the user; bugs 1–3 confirmed fixed.
- **NOT run:** `androidApp:connectedAndroidTest` (instrumented) and the wasmJs path. Bug 4's race has a wider window on wasmJs (OPFS worker slower than Room) and was not observed there.

## Выводы

4 bugs, 4 commits, 13 lines of production code for bug 4 itself. The gate earned its keep: bug 4 would have shipped as "deleted the folder, it came back after relaunch", and it was introduced by the very fix that exposed it.

## Предложения по улучшению агентов

### compose-feature-expert
- [ ] `SwipeToDismissBox` renders `backgroundContent` unconditionally (`Row(..., matchParentSize())` first in the Box) — it is hidden only by occlusion. Any draw-only shrink (bouncy spring undershoot, `graphicsLayer` scale) exposes it. Gate the background on `dismissDirection != Settled`, never dampen the animation.

### kotlin-expert
- [ ] Undo/restore must snapshot the whole domain object. Rebuilding from one field + a constructor is a silent multi-field regression: here `ChecklistItem(text = ...)` quietly reset `parentId`, `type`, `priority`, `weekday`.
- [ ] A ViewModel that navigates away (`navigator.onBack()`) and then persists in `viewModelScope` loses the write when the VM is entry-scoped. Inject an app-wide scope for work that must outlive the caller.
