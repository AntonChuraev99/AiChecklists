---
title: "Firestore Sync: Fill Mutations Must Re-Upload Parent Checklist"
date: 2026-06-12
type: bug-fix
modules: [feature/checklist, core/datastore]
keywords: [embedded-array-sync, firestore-lww, fill-mutations, updatedAt-bump, checked-items-not-syncing, syncStatus-PENDING_UPLOAD, whole-document-merge]
project: checklists
---

# Firestore Sync: Fill Mutations Must Re-Upload Parent Checklist

## Problem / Context

Gisti stores each checklist as **one Firestore document** with its items' state (checked, notes, attachments, priority, reminders) as an **embedded array** inside it — there are no per-fill documents. Last-Write-Wins (LWW) merge is keyed on the **parent checklist's `updatedAt`**.

**Symptom:** User toggles a checkbox in Android → web never sees the checked state. Add/delete item from Android → web sees it. Toggling notes or attachments → nothing. The asymmetry leaked because operations like "add item" call `updateChecklistTemplate()` (which dirties the parent), while "toggle checkbox" only called `updateFill()` (which left the parent `SYNCED`).

**Root cause:** `SyncRepositoryImpl.pushPendingChanges()` uses a two-pass strategy:
- Checklist loop: uploads only where `syncStatus != 0` (PENDING_UPLOAD or PENDING_DELETE). Parent=SYNCED → **skip**.
- Fill loop: processes **only** `PENDING_DELETE` fills; ignores `PENDING_UPLOAD` → never sent to cloud.

Net effect: a checkbox toggle marks the fill `PENDING_UPLOAD` but the parent remains `SYNCED`, so push never touches it, and the embedded-fill change never reaches Firestore. When the web app fetches, it compares `updatedAt` values (unchanged), and a LWW merge **skips** the parent entirely.

**Why this is subtle:** The whole-document merge strategy is sound (simple, avoids field-level conflict resolution). But the push logic treated fill-mutations as independent entities instead of recognizing that fills are **embedded** — a fill change is **not** a separate upload; it's part of the parent's state and requires the parent to be re-uploaded with a fresh `updatedAt`.

## Solution

### 1. Extract parent upload helper + re-upload dirty fill parents

**File:** `SyncRepositoryImpl.pushPendingChanges()`

Introduce `uploadChecklistEntity()` helper that uploads a single checklist and advances its `updatedAt` to push time (so LWW merge on other devices accepts it). Add a second pass **after the checklist loop** that iterates over all `PENDING_UPLOAD` fills whose parent was not already uploaded (skipped because parent is SYNCED):

```kotlin
private suspend fun uploadChecklistEntity(
    entity: ChecklistEntity,
    dataSource: FirestoreSyncDataSource
): AppResult<Unit> {
    val nowMs = currentTimeMillis()
    val updated = entity.copy(updatedAt = nowMs)
    return dataSource.uploadChecklist(updated).mapSuccess {
        checklistDao.updateUpdatedAt(entity.id, nowMs)
    }
}

// In pushPendingChanges, after checklist loop:
val uploadedChecklistIds = result.getOrNull().orEmpty().toSet()
val dirtyFillParentIds = fillDao.getPendingUploadFills().map { it.checklistId }.toSet() - uploadedChecklistIds
for (parentId in dirtyFillParentIds) {
    val parent = checklistDao.getById(parentId) ?: continue
    uploadChecklistEntity(parent, dataSource)
}
```

**Why:** Embedding means a fill change requires the parent to be re-uploaded. The parent's `updatedAt` is the only thing remote devices compare in LWW. Skipping it leaves the fill stranded on the device.

### 2. New DAO: touch-for-sync without content mutation

**File:** `ChecklistDao.kt`

Add `touchForSync(id: String, updatedAt: Long)`:

```kotlin
@Query("UPDATE checklist_entities SET syncStatus = 1, updatedAt = :updatedAt WHERE id = :id AND syncStatus != 2")
suspend fun touchForSync(id: String, updatedAt: Long)
```

This marks a checklist `PENDING_UPLOAD` and bumps `updatedAt` **without modifying fields**. Guard `syncStatus != 2` prevents resurrecting a queued deletion.

**Use cases:**
- `deleteFill()`: hard-delete the fill locally, then touch parent so next push re-uploads it without the fill → remote devices' `reconcileDeletedFills()` drop it.
- Any future fill-level change that doesn't touch the parent's own fields (currently: checkbox, note, attachment, priority, reset).

### 3. Fill mutations must dirty themselves + parent

**File:** `ChecklistRepositoryImpl.kt`

For every fill mutation (checkbox toggle, note edit, attachment, priority, reset), mark the fill `PENDING_UPLOAD`:

```kotlin
suspend fun updateFill(checklistId: String, fill: ChecklistFillEntity) {
    val now = currentTimeMillis()
    val dirty = fill.copy(updatedAt = now, syncStatus = 1) // PENDING_UPLOAD = 1
    fillDao.insert(dirty)
    // Parent will be re-uploaded in next sync cycle (by pushPendingChanges pass 2)
}

suspend fun deleteFill(checklistId: String, fillId: String) {
    fillDao.deleteFill(checklistId, fillId)
    checklistDao.touchForSync(checklistId, currentTimeMillis())
}

suspend fun togglePriority(checklistId: String, fillId: String, priority: Int) {
    val fill = fillDao.getById(fillId) ?: return
    val now = currentTimeMillis()
    fillDao.insert(fill.copy(priority = priority, updatedAt = now, syncStatus = 1))
    // Parent re-upload handled by pushPendingChanges pass 2
}

suspend fun addAttachment(checklistId: String, fillId: String, uri: Uri) {
    val fill = fillDao.getById(fillId) ?: return
    val now = currentTimeMillis()
    val updated = fill.copy(
        attachments = fill.attachments + AttachmentEntity(uri.toString(), now),
        updatedAt = now,
        syncStatus = 1
    )
    fillDao.insert(updated)
}

suspend fun removeAttachment(checklistId: String, fillId: String, attachmentId: String) {
    val fill = fillDao.getById(fillId) ?: return
    val now = currentTimeMillis()
    val updated = fill.copy(
        attachments = fill.attachments.filter { it.id != attachmentId },
        updatedAt = now,
        syncStatus = 1
    )
    fillDao.insert(updated)
}

suspend fun resetDefaultFillChecks(checklistId: String) {
    val fills = fillDao.getCheckedFillsByChecklist(checklistId)
    val now = currentTimeMillis()
    for (fill in fills) {
        fillDao.insert(fill.copy(isChecked = false, updatedAt = now, syncStatus = 1))
    }
}
```

**Critical:** Always set `updatedAt = currentTimeMillis()` and `syncStatus = PENDING_UPLOAD (1)` when persisting a fill change, otherwise it stays silent.

### 4. Integration with second push pass

When `pushPendingChanges` executes the fill-parent re-upload pass, the parent will include the freshly-marked `PENDING_UPLOAD` fills. The Firestore batch upload serializes the entire parent (with updated embedded array) and bumps `updatedAt`. On remote devices:

- `mergeRemoteChecklist` receives the refreshed parent with new `updatedAt`.
- LWW comparison: `remote.updatedAt > local.updatedAt` → **accept** the merge.
- Embedded fills are merged into local fills array (checked states now visible).

## Why Exactly This Way

1. **Don't create per-fill Firestore docs.** Checklists typically have 5–50 items. N documents per checklist = N+1 Firestore reads per sync. Stay with embedding.

2. **Don't use tombstones (isDeleted field).** A PENDING_DELETE fill is still deleted locally immediately (no "soft-delete" UI). The fill absence in the parent is the only signal. Next sync's `reconcileDeletedFills` on the other device drops it.

3. **Parent's updatedAt is the merge key.** It's the only timestamp compared in LWW. Fill timestamps are metadata; they don't drive merge acceptance.

4. **Two-pass push (checklists then dirty fill parents).** Ordering matters: checklist loop runs first, collecting IDs. Fill loop (second pass) then skips parents already uploaded (avoid double-upload). A single loop with interleaved re-uploads could double-send or miss dirty fills added during iteration.

5. **touchForSync guards syncStatus != 2.** Once PENDING_DELETE is queued, don't let a fill mutation "fix" the parent and cancel the queued deletion. The deletion's next sync will handle it. (This guard exists in DAO but is rarely needed in practice because deleteFill itself hard-deletes the fill; the guard is defensive.)

## Patterns & Reuse

**Apply this pattern whenever:**
- You add a new fill-level mutation (edit any field stored in the embedded array).
- You delete a whole fill and want the deletion to sync across devices.
- You're debugging why a fill change doesn't reach other devices.

**Checklist before adding a fill mutation:**
```
[ ] Does this mutation touch a field stored inside the fill object (array in Firestore)?
    → Mark fill PENDING_UPLOAD; parent re-upload is automatic (pushPendingChanges pass 2).
    
[ ] Is the mutation a whole-fill delete?
    → Call touchForSync on parent; reconcileDeletedFills on remote will drop it.
    
[ ] Does the mutation also touch a checklist field (template, title, color)?
    → Also call updateChecklistTemplate() (or it will eventually via a separate flow);
      or touch parent separately; whichever is clearer in domain logic.
    
[ ] Have you set updatedAt = now and syncStatus = PENDING_UPLOAD when persisting?
    → Yes: pass. No: you just created a silent-sync bug.
```

## Linked Concepts

- [[fill_only_mutations_must_dirty_parent]] — project memory, same pattern, shorter.
- `docs/solutions/firestore-sync-cross-platform-timestamp-and-backfill-2026-06-05.md` — timestamp deserialization & `asEpochMillis()` defensive read.
- `docs/solutions/data-layer/sync-per-fill-reconciliation-2026-05-30.md` — `reconcileDeletedFills` on the receiving end.

## Validation

- Red test: `push_uploadsParentOfDirtyFillWhenChecklistIsSynced` (fails on original code, green after fix) — simulates checkbox toggle, verifies parent is re-uploaded despite being SYNCED.
- Green tests: 4 mutation operations (add/remove attachment, toggle priority, reset, delete fill) mark fill PENDING_UPLOAD and/or trigger parent touch.
- Full suite: `:feature:checklist:testAndroidHostTest` (302 total tests, 0 failures, no regression).
- Cross-platform: `:composeApp:compileKotlinWasmJs` (Room KSP regenerated DAO with new queries).

## Known Limitation (Pre-Existing, Out of Scope)

Sync is whole-document LWW. If a user toggles a checkbox on device A while simultaneously editing the checklist title on device B, one of them will overwrite the other wholesale. This is a known trade-off of the embedded-array strategy. A field-level merge (only merge the changed fields, not the entire document) would be the proper long-term fix but requires significant architecture change and is deferred.
