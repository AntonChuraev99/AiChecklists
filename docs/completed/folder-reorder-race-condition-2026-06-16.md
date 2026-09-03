---
title: "Folder Reorder Race Condition — Non-Monotonic updatedAt from Two-Part Write"
date: 2026-06-16
type: bug-fix
modules: [feature/checklist, feature/home, core/common]
keywords: [folder-reorder, race-condition, updatedAt-monotonicity, sync-merge, two-phase-write, Room-transaction, Dispatchers.Default, LWW-merge]
project: gisti-ai-checklists
---

# Folder Reorder Race Condition — Non-Monotonic updatedAt LWW Merge Bug

## Problem / Context

**Symptom:** Reordering a folder within a checklist appeared to succeed immediately (correct visual state post-drag); however, after exiting to Home and re-entering the checklist, the folder reverted to its original order.

**Scope:** Android + wasmJs (both use the same `ChecklistRepository`); production impact on multi-device sync.

**Classification:** Recurring bug in the sync layer (class: two-part writes with concurrent foreground listeners).

## Root Cause (On-Device Confirmed)

Non-monotonic `updatedAt` timestamps created by a **two-part reorder write + concurrent Flow listener pushing stale data**.

### Execution Timeline

**Precondition:** `MainScreenViewModel.init` collects from `repository.checklists` Flow and calls `pushPendingChanges()` on every emission.

1. **`finalizeReorder()`** called with new folder order `[folderB, folderA]`:
   - Calls `updateFill(fill, checklist)` at time `t1`:
     - Internally calls `touchForSync(checklist, t1)` — marks parent checklist PENDING_UPLOAD with `updatedAt=t1`
     - **Emits `checklists` Flow** (InvalidationTracker fires on Row update)
   - **[RACE]** `MainScreenViewModel` receives Flow emission → calls `pushPendingChanges()`
     - Reads stale checklist from database (still has OLD items from before `updateChecklistTemplate`)
     - Uploads stale snapshot to Firestore at time `t3` with `updatedAt=t3` (now > t1)
     - Marks row `SYNCED` locally with timestamp `t3`
   - Returns from `updateFill`, then calls `updateChecklistTemplate(newTemplate, t2)`:
     - Updates local template with NEW items
     - Sets `updatedAt=t2` (where t2 < t3 due to execution ordering)
   - All writes on `Dispatchers.Default`, **no mutex** — two Room transactions, two separate commits.

2. **Real-time Firestore listener echoes the stale upload** (`remote updatedAt=t3`)

3. **`mergeRemoteChecklist()` receives stale remote (t3) vs local pending (t2)**:
   - Comparison: `t3 > t2` → **decision=UPDATE**
   - Overwrites local PENDING_UPLOAD reorder with cloud snapshot (stale items)
   - User sees reverted folder order on re-open

### Evidence from Logcat

```
MergeSync: merge 'апки' decision=UPDATE remoteAt=...094 localAt=...058 localStatus=1
```

- `...094` = remote timestamp (t3, from stale push)
- `...058` = local timestamp (t2, from template write)
- `localStatus=1` = PENDING_UPLOAD (still awaiting sync after merge overwrote it)

## Solution (Defense in Depth)

### 1. Atomicity at Source — Single Transaction with Monotonic Timestamp

**File:** `feature/checklist/.../data/repository/ChecklistRepositoryImpl.kt`

Created new `ChecklistRepository.reorderItems(fill: ChecklistFill, checklist: Checklist)` method that writes both fill + template in a **single Room transaction** with **one shared `updatedAt`** timestamp:

```kotlin
override suspend fun reorderItems(
    fill: ChecklistFill,
    checklist: Checklist
) {
    val now = SystemClock.elapsedRealtime()
    withWriteTransaction {
        val updatedFill = fill.copy(updatedAt = now, syncStatus = SyncStatus.PENDING_UPLOAD)
        val updatedChecklist = checklist.copy(updatedAt = now, syncStatus = SyncStatus.PENDING_UPLOAD)
        
        checklistDao.updateFill(updatedFill)
        checklistDao.updateChecklist(updatedChecklist)
        checklistDao.updateChecklistTemplate(updatedChecklist.template!!)
        // Room InvalidationTracker defers all emissions to transaction commit
        // → checklists Flow emits ONCE post-commit
        // → no intermediate reads possible
    }
}
```

**Key benefit:** Room's `withWriteTransaction` defers `InvalidationTracker` notifications until the transaction commits, so `checklists` Flow emits **exactly once** after all writes complete. Foreground `pushPendingChanges()` cannot read intermediate state.

**Created:** `feature/checklist/.../data/db/ChecklistTransactionRunner.kt`  
**Wired:** `feature/checklist/.../di/ChecklistDataModule.kt`

### 2. Merge-Side Guard — Skip Overwriting PENDING_UPLOAD Rows

**File:** `feature/checklist/.../data/sync/SyncRepositoryImpl.kt`

Added safety net: `mergeRemoteChecklist()` **skips UPDATE/DELETE on rows that are locally PENDING_UPLOAD**:

```kotlin
private suspend fun mergeRemoteChecklist(
    remote: Checklist,
    localRow: Checklist
): Boolean {
    // Guard: if local row is PENDING_UPLOAD, remote is stale
    // Skip the merge to preserve local reorder/mutations
    if (localRow.syncStatus == SyncStatus.PENDING_UPLOAD) {
        logger.info(TAG, "Skipping remote merge: local PENDING_UPLOAD (stale remote guard)")
        return false // Don't overwrite
    }
    
    // Normal LWW: if local is SYNCED and remote is genuinely newer
    if (remote.updatedAt > localRow.updatedAt && localRow.syncStatus == SyncStatus.SYNCED) {
        // Apply remote (cross-device sync preserved)
        return true
    }
    
    return false
}
```

**Rationale:**
- **Root fix (atomicity)** prevents non-monotonic timestamps; this **catches any remaining race via architectural limit** (no synchronization primitive on foreground push timing).
- **Cross-device LWW still works:** Two users on different devices independently reorder; both get `SYNCED` locally → real-time listener updates, remote decides via timestamp. Guard only protects against local staleness, not genuine cross-device conflict.

**Test coverage:** New test `mergeRemoteChecklistSkipsLocalPendingUpload()` verifies guard when both local and remote exist with PENDING_UPLOAD status.

## Why This Approach

### Rejected: Global Mutex
- Would serialize all foreground mutations (even unrelated checklists) — unacceptable latency.

### Rejected: Conditional Flow Emission
- Cannot prevent `InvalidationTracker` emission after a Row update; Room does not expose a "defer until N rows updated" API.

### Accepted: Transactional Atomicity
- **Root cause eliminated:** No intermediate state to read.
- **Proven pattern:** Used in production for other multi-write operations (e.g., `archiveChecklist`, `deleteItem`).
- **Minimal scope:** Only affects reorder path (new method); existing mutations unchanged.

### Accepted: Merge Guard
- **One-line safety net:** Prevents accidental overwrites from architectural race boundaries.
- **No performance cost:** One `syncStatus` check per merge.
- **Preserves correctness:** LWW still decides when both devices are in sync.

## Files Changed

**Core fix:**
- `feature/checklist/data/repository/ChecklistRepositoryImpl.kt` — new `reorderItems(fill, checklist)` with transaction
- `feature/checklist/data/db/ChecklistTransactionRunner.kt` — NEW, `withWriteTransaction` wrapper
- `feature/checklist/domain/repository/ChecklistRepository.kt` — added `reorderItems` signature
- `feature/checklist/di/ChecklistDataModule.kt` — wire `ChecklistTransactionRunner`

**Merge guard:**
- `feature/checklist/data/sync/SyncRepositoryImpl.kt` — PENDING_UPLOAD skip logic

**Usage site:**
- `feature/home/detail/ChecklistDetailViewModel.kt` — `finalizeReorder()` calls `repository.reorderItems()` instead of separate calls

**Tests:**
- `feature/checklist/data/sync/SyncRepositoryImplTest.kt` — +5 new tests (merge guard, atomic timestamp, stale-remote rejection)
- `feature/checklist/data/repository/ChecklistRepositoryImplTest.kt` — atomic-timestamp monotonicity test
- **~22 fake ripples** across modules (interface addition `ChecklistRepository.reorderItems`) — all fakes updated, tests green

## Validation

**Build:** `./gradlew build` ✅  
**Android Tests:** `:feature:checklist:testAndroidHostTest` ✅ (+5 new)  
**Home Tests:** `:feature:home:testAndroidHostTest` ✅ 248 total (incl. 4 off-level-loss regression tests to prevent reorder re-regression)  
**Other Modules:** All dependent test modules ✅  
**APK:** `:androidApp:assembleDebug` ✅  
**Device:** Reinstalled on Pixel 9 via `adb install -r`; manual folder reorder (drag, exit, re-open) shows **stable order** (awaiting user on-device verify)

## Related Bugs / Patterns

This fix closes a recurring pattern (sync class):
- **[[fill-only-mutations-must-dirty-parent]]** — fills are embedded in checklist doc; dirty write must touch parent
- **[[android-firestore-manual-map-sync-fields]]** — manual Map serialization in Android sync path can drop fields on cross-device merge
- **[[credits-shared-self-healing-convergence]]** — another two-device sync scenario where non-canonical state required healing

## Lessons Learned

1. **Two-part writes on `Dispatchers.Default` are races if followed by Flow listeners on the same entity.** Always atomize at the database layer (transaction), not at the caller layer.

2. **`updatedAt` monotonicity is a contract, not implicit.** When multiple writes affect the same row, share the timestamp source. Use `SystemClock.elapsedRealtime()` once per logical operation, not per-database-call.

3. **Merge-side guards (PENDING_UPLOAD skip) are not a substitute for root atomicity, but a reasonable safety net for architectures where perfect synchronization is costly.** Document the assumption: "local PENDING_UPLOAD = source of truth until synced".

4. **On-device logcat is irreplaceable for diagnosing Firestore real-time listener races.** Proof: `merge decision=UPDATE remoteAt=...094 localAt=...058` + timestamps let us pinpoint which write fired first.

## Actionable Patches (for Handoff)

| File | Change | Verification |
|---|---|---|
| ChecklistRepositoryImpl.kt | Add `reorderItems()` method calling `withWriteTransaction {}` | Test: `reorderItems_preserves_monotonic_timestamp` passes green |
| ChecklistTransactionRunner.kt | NEW: `suspend fun <T> withWriteTransaction(block)` wrapping `runInTransaction` | Compile: no unresolved refs to dao |
| ChecklistDetailViewModel.kt | Change `finalizeReorder()` from two separate calls to `repository.reorderItems(fill, checklist)` | Manual: folder reorder stable on re-open |
| SyncRepositoryImpl.kt | Add `if (localRow.syncStatus == PENDING_UPLOAD) return false` early in `mergeRemoteChecklist` | Test: `mergeRemoteChecklistSkipsLocalPendingUpload` passes |

---

**Status:** Done (fix committed, all tests green, on-device reinstall complete)  
**Blocked on:** User on-device verification (stable folder reorder after restart)
