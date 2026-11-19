---
slug: sync-fill-reconciliation
date: 2026-05-30
status: resolved
resolved_date: 2026-05-30
type: feature
keywords: [sync, reconciliation, fills, firestore, server-authoritative, cross-device, delete-propagation]
blocking_reason: scope-control — checklist-level reconciliation (the reported data-loss bug) is fully fixed; per-fill deletion is a rarer, lower-impact case that deserves its own safe pass + tests rather than being bolted onto the merge UPDATE branch
resume_trigger: User reports «удаление fill не синкается между устройствами», OR next sync-related session
---

# Sync — per-fill deletion reconciliation across devices

## What's deferred

Cross-device deletion of an **individual fill** (not the whole checklist) is not propagated.
When checklist C survives but one of its fills is deleted on device B, device A keeps the
stale fill locally.

Today `mergeRemoteChecklist` (UPDATE branch, `SyncRepositoryImpl.kt`) merges fills by `cloudId`
(insert new / update newer) but never **removes** local fills that are absent from `remote.fills`.

Checklist-level reconciliation — the actual reported resurrection bug — **is fully fixed** by
`reconcileDeletedRemotely()` (removes local SYNCED checklists absent from the cloud snapshot, and
their fills go with them). This todo is only about the finer-grained per-fill case.

## Context

Part of the 2026-05-30 server-authoritative sync rework (Firestore = source of truth).
See `docs/active/sync-server-authoritative-2026-05-30.md`.

Code anchor: `feature/checklist/.../data/sync/SyncRepositoryImpl.kt` —
`// Pending: docs/todos/2026-05-30-sync-fill-reconciliation.md` in the merge UPDATE branch.

## Steps to resume

1. In `mergeRemoteChecklist` UPDATE branch, after merging `remote.fills`: compute local SYNCED
   fills of this checklist whose `cloudId` is absent from `remote.fills` → `fillDao.deleteById`.
2. **Safety mirror of checklist-level logic** — only touch `syncStatus == SYNCED(0)` fills; never
   delete `PENDING_UPLOAD(1)` (an additional fill just created locally, not yet in cloud).
3. Edge: SKIP branch (local checklist newer than remote) does **not** process `remote.fills` at
   all — decide whether fill-reconciliation should still run there or be skipped with the checklist.
4. Add a DAO query for synced fill cloudIds per checklist (mirror of `getSyncedCloudIds()`), e.g.
   `getSyncedFillCloudIds(checklistId): List<String>`.
5. Tests (mirror existing reconcile tests): `reconcileFills_removesSyncedAbsentFromCloud`,
   `reconcileFills_keepsPendingUploadFill`, `reconcileFills_skippedWhenChecklistSkipped`.

## Expected impact

- Low-to-medium. Editing/removing one fill on another device is far less common than deleting a
  whole checklist (which is fixed). No data-loss risk from the current state — only a stale fill
  lingers until the next checklist-level change re-syncs.

## Related

- Active: `docs/active/sync-server-authoritative-2026-05-30.md`
- Tests: `feature/checklist/src/commonTest/.../data/sync/SyncRepositoryImplTest.kt` (checklist-level pattern to mirror)

## Resolution (2026-05-30)

Implemented exactly as the 5-step plan above.

1. **DAO** — `ChecklistFillDao.getSyncedFillCloudIds(checklistId): List<String>`
   (`SELECT cloudId FROM checklist_fills WHERE checklistId = :checklistId AND syncStatus = 0 AND cloudId IS NOT NULL`),
   a per-checklist mirror of `ChecklistDao.getSyncedCloudIds()`.
2. **Reconcile** — new private `SyncRepositoryImpl.reconcileDeletedFills(localChecklistId, remoteFills)`;
   called at the end of the **UPDATE branch only** (after merging `remote.fills`). Removes local
   SYNCED fills whose `cloudId` is absent from the newer `remote.fills`.
3. **SKIP-branch decision** — fill-reconciliation is **NOT** run in the SKIP branch (local newer).
   Reconciling there could wipe a local fill edit against a stale snapshot — the inverse data-loss
   bug. Only the UPDATE branch (remote authoritative for this checklist) reconciles.
4. **Safety** — only `syncStatus == SYNCED(0)` fills are touched; PENDING_UPLOAD(1) survives,
   PENDING_DELETE(2) is left to `pushPendingChanges`. Runs only on a full successful fetch
   (inherited from `pullAndMerge` → reconcile-on-Success).
5. **Tests** — 4 added to `SyncRepositoryImplTest` (`commonTest`): `reconcileFills_removesSyncedAbsentFromCloud`,
   `reconcileFills_keepsPendingUploadFill`, `reconcileFills_skippedWhenChecklistSkipped`,
   `reconcileFills_scopedToOwningChecklist`.

**Validation:** `:feature:checklist:testAndroidHostTest` — 15 tests, 0 failures (11 existing + 4 new).
**Files:** `ChecklistFillDao.kt`, `SyncRepositoryImpl.kt`, `SyncRepositoryImplTest.kt`.
