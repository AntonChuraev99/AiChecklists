---
title: "Attachment cloud sync via Firebase Storage (cross-device file bytes)"
date: 2026-06-24
type: feature
modules: [core/common, feature/checklist, feature/home, feature/paywall, composeApp]
keywords: [attachment, firebase-storage, cross-device-sync, cloud-upload-download, OPFS, fill-item, lazy-load, RC-limits, app-check]
project: gisti-ai-checklists
---

# Attachment cloud sync via Firebase Storage

## Problem / Context

File attachments (images, PDFs) created on one device (Android) only synced metadata; **bytes remained local** and were never uploaded to cloud. On another device (Web), the same signed-in user saw the attachment listed but could not open it — "local file not found."

Cross-device sync required a backend for file bytes. Prior state: metadata-only via embedded Firestore JSON; user experience broken on secondary devices.

## Solution

**Three-layer architecture:**

1. **Firebase Storage backend** — managed GCS bucket (`*.firebasestorage.app`). Egress: 100 GB/month free tier. Security: App Check + Auth UID in Rules. SDK: Android native `firebase-storage`, wasmJs Firebase JS Storage (ESM), iOS stub.

2. **Domain model** — `Attachment` gains optional field `storagePath: String? = null` (cloud key format: `users/{uid}/attachments/{fillId}/{itemId}/{attachmentId}.{ext}`). Bytes never touch Firestore (1 MiB limit, LWW conflict). Path only.

3. **Upload (Phase 3) — sync-layer, not ViewModel:**
   - New method `uploadPendingAttachments()` in `SyncRepositoryImpl` scans fill for `attachment.storagePath == null && path.isNotBlank()` (locally saved, not yet uploaded).
   - Computes cloud key via `AttachmentCloudPaths.forAttachment(uid, fillId, itemId, attachmentId, ext)`.
   - Calls `attachmentCloudStorage.upload(localPath, storagePath)` on `Dispatchers.IO` (Android) / Web Worker thread (wasmJs).
   - On success: stamps `storagePath` via `ChecklistFillItem.withAttachments(...)`, persist `fill` to DB, done.
   - On error: logs, leaves `storagePath = null`, retries next sync cycle.
   - **Sync invariant:** attachment mutations mark parent fill `PENDING_UPLOAD`, bump `updatedAt`, trigger `pushPendingChanges` ⇒ retry/offline/backfill work automatically.

4. **Download (Phase 4) — lazy, composable-level:**
   - New composable `rememberMaterializedAttachment(attachment, localStoragePort, cloudStoragePort)` injects both storage ports.
   - Logic: if local file exists (size > 0), Ready immediately. If `storagePath == null`, Ready (never uploaded or deleted). Else: download from cloud, cache locally, Ready or Error.
   - `AttachmentThumbnail` and `AttachmentFullscreenViewer` use it.
   - Existing fallback chain (Coil custom fetcher on Android, OpfsImageFetcher on web) unchanged — they read from local cache post-materialization.

5. **Cleanup (Phase 5) — cloud-first, before local delete:**
   - `deleteChecklist(checklistId)` → cascade: delete cloud objects (all attachments in all fills) before local DB delete.
   - `removeAttachment(fillId, itemId, attachmentId)` → `cloudStorage.delete(storagePath)` before removing from items.
   - `deleteFill(fillId)` → delete all cloud attachments before local fill delete.
   - Best-effort: errors logged, idempotent.
   - **Known v1 limitation:** offline delete of one attachment → orphan in cloud (no persistent pending-delete queue). Acceptable; candidate for v2.

6. **Limits (Phase 6) — RC-driven, per-tier:**
   - Free: `maxAttachmentsPerItem = 3` (Remote Config key `max_attachments_per_item_free`, default 3).
   - Premium: `maxAttachmentsPerItem = Int.MAX_VALUE` (unlimited).
   - Via `UserLimits` + `GetUserLimitsUseCase` (reads RC, fallback to constant 3).
   - ViewModel gate: `handleAddAttachment` checks limit, snackbar on exceed.
   - web init.js mirrors Android defaults: `rc.defaultConfig.max_attachments_per_item_free = 3`.

## Why This Way

### Firebase Storage vs. alternatives:

- **vs. GCS directly:** Same bucket, but without Firebase layer. Requires 2 extra Cloud Functions (signed-URL endpoints) + manual security. Same cost, more work. Firebase SDK provides App Check native support.
- **vs. Cloudflare R2:** Zero egress (appealing), but ROI only at 100+ GB/month egress. Startup: ~$0 savings. Requires extra Worker + manual JWT verification. No App Check equivalent.

### Upload in sync-layer, not ViewModel:

- Attachment mutation = fill mutation (like checkbox toggle, title). Natural home: sync layer.
- Automatic retry/offline/backfill for free — sync already handles it.
- ViewModel stays clean of storage logic; tested separately via FakeAttachmentCloudStorage.

### Lazy download, not eager:

- Eager download on every sync = wasted bandwidth (user may never open the attachment).
- Lazy at composable level = minimal blast radius.
- Fallback fetchers (Coil, OpfsImageFetcher) remain read-only cache; no changes to them.

### Cloud-first cleanup:

- Cloud orphans eat quota forever (indefinite cost).
- Local delete can retry; cloud delete errors are logged but accepted.

### RC-driven limits:

- No code deploy to change free-tier cap; manage in Firebase Console.
- Pattern proven by `recurring-reminders-rc-driven-limit` (used since 2026-05-12).

## Examples

### Upload (triggered in sync):

```kotlin
private suspend fun uploadPendingAttachments(uid: String, fill: ChecklistFillEntity) {
  fill.items.flatMap { it.attachments }
    .filter { it.storagePath == null && it.path.isNotBlank() }
    .forEach { attachment ->
      val ext = attachment.path.substringAfterLast(".")
      val storagePath = AttachmentCloudPaths.forAttachment(
        uid, fill.id, /* itemId */ "", attachment.id, ext
      )
      when (val result = attachmentCloudStorage.upload(attachment.path, storagePath)) {
        is AppResult.Success -> {
          // Update fill with storagePath, persist
          val updated = fill.copy(
            items = fill.items.map { item ->
              item.copy(
                attachments = item.attachments.map { a ->
                  if (a.id == attachment.id) a.copy(storagePath = storagePath) else a
                }
              )
            }
          )
          fillDao.insert(updated)
        }
        is AppResult.Error -> AppLogger.error("SyncRepository", "upload failed", result.exception)
      }
    }
}
```

### Download (lazy, composable-level):

```kotlin
@Composable
fun rememberMaterializedAttachment(
  attachment: Attachment,
  localStoragePort: AttachmentStoragePort,
  cloudStoragePort: AttachmentCloudStoragePort
): AttachmentMaterializeState {
  val coroutineScope = rememberCoroutineScope()
  val state = remember { mutableStateOf<AttachmentMaterializeState>(Loading) }

  LaunchedEffect(attachment.storagePath + attachment.path) {
    state.value = try {
      if (localStoragePort.sizeOf(attachment.path) > 0) Ready else
      if (attachment.storagePath == null) Ready else {
        when (cloudStoragePort.download(attachment.storagePath, attachment.path)) {
          is AppResult.Success -> Ready
          is AppResult.Error -> Error
        }
      }
    } catch (e: Exception) { Error }
  }
  return state.value
}
```

### Cleanup (removeAttachment):

```kotlin
suspend fun removeAttachment(fillId: String, itemId: String, attachmentId: String) {
  val fill = fillDao.getFill(fillId) ?: return
  val attachment = fill.items
    .find { it.id == itemId }?.attachments
    .find { it.id == attachmentId } ?: return

  // Cloud-first
  if (attachment.storagePath != null) {
    when (val result = attachmentCloudStorage.delete(attachment.storagePath)) {
      is AppResult.Error -> AppLogger.error("Repository", "delete cloud failed", result.exception)
      else -> {}
    }
  }

  // Local delete
  val updated = fill.copy(
    items = fill.items.map { item ->
      if (item.id == itemId) 
        item.copy(attachments = item.attachments.filterNot { it.id == attachmentId })
      else item
    }
  )
  fillDao.insert(updated)
}
```

## Related Files

**Platform abstractions:**
- `core/common/api/src/commonMain/AttachmentCloudStoragePort.kt` — interface (upload/download/delete).
- `core/common/api/src/{androidMain,wasmJsMain,iosMain}/AttachmentCloudStorage.*.kt` — implementations.
- `core/common/api/src/commonMain/AttachmentCloudPaths.kt` — cloud key construction.

**Sync integration:**
- `feature/checklist/src/commonMain/.../data/sync/SyncRepositoryImpl.kt` — `uploadPendingAttachments()`.
- `feature/checklist/src/commonMain/.../domain/model/Attachment.kt` — model with `storagePath`.

**UI (lazy load):**
- `feature/home/src/commonMain/.../presentation/detail/AttachmentMaterialize.kt` — `rememberMaterializedAttachment`.
- `feature/home/src/commonMain/.../presentation/detail/{AttachmentThumbnail,AttachmentFullscreenViewer}.kt` — consumers.

**Cleanup:**
- `feature/checklist/src/commonMain/.../data/repository/ChecklistRepositoryImpl.kt` — `deleteChecklist/removeAttachment/deleteFill` with cloud cascade.

**Limits:**
- `feature/paywall/src/commonMain/.../domain/model/UserLimits.kt` — `maxAttachmentsPerItem`.
- `feature/paywall/src/commonMain/.../domain/usecase/GetUserLimitsUseCase.kt` — RC fetch.
- `core/remoteconfig/api/src/commonMain/.../RemoteConfigKeys.kt` — `MAX_ATTACHMENTS_PER_ITEM_FREE`.
- `composeApp/.../init.js.template` — `rc.defaultConfig.max_attachments_per_item_free = 3`.
- `gradle/remoteConfigDefaults.xml` — `<item name="max_attachments_per_item_free">3</item>`.

**Tests:**
- `feature/checklist/src/commonTest/.../ChecklistRepositoryImplTest.kt` — `removeAttachment_with/noStoragePath_*`.
- `feature/checklist/src/commonTest/.../SyncRepositoryImplTest.kt` — upload + sync-invariant.
- `feature/home/src/commonTest/.../AttachmentMaterializeTest.kt` — lazy-download state machine.
- `feature/paywall/src/commonTest/.../UserLimitsAttachmentTest.kt` — RC-limit coverage.

## Lessons Learned

1. **Embed in sync-layer, not ViewModel.** Storage operations belong where other fill mutations live. Automatic retry/offline/backfill is free. ViewModel stays lean.

2. **Lazy composable-level download = minimal blast radius.** No ViewModel refactor, fallback fetchers unchanged, isolated state machine.

3. **Cloud-first cleanup = cost protection.** Cloud orphans are indefinite costs; local delete retries naturally.

4. **RC-driven limits = flexibility without deploy.** Proven pattern; used in production since recurring-reminders (2026-05-12).

5. **Bucket pinning on Android SDK.** Firebase Storage Android SDK should use explicit bucket (`getInstance("gs://bucket-id")`) if google-services.json is stale or absent. Prevents silent default-bucket fallback.

6. **App Check enforcement on Rules, not in client.** Backend validation only; client check would be moot.

## Known Limitations (v1)

- **Offline delete → orphan:** Deleting one attachment offline may leave a cloud object (no persistent pending-delete queue). Acceptable for MVP; v2 candidate: persistent queue.
- **UI button state:** `ItemDetailsSheet` add-attachment button uses static `FREE_ATTACHMENT_LIMIT_PER_ITEM` constant for `enabled` (not dynamic `UserLimits`). Enforcement in ViewModel is correct; UI polish deferred.
- **App Check on web:** Requires `firebase.json` `storageBucket` configured AND JS SDK App Check initialized in init.js. Temp auth-only Rules adequate for dev; hardened rules + App Check in Console for prod.

## Deferred

- **Phase 7 e2e tests:** Cross-device integration tests (attach on A → pull on B → storagePath present, lazy-download resolves).
- **Security Rules hardening:** 10 MB size cap + content-type whitelist + per-user quota.
- **RC Config:**  `max_attachments_per_item_free` visible in Console (backend defaults 3 already in code).
