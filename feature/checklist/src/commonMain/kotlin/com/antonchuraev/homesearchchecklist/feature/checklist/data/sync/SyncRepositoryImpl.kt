package com.antonchuraev.homesearchchecklist.feature.checklist.data.sync

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentCloudPaths
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentCloudStoragePort
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistEntity
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillEntity
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.toDomain
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.SyncStatus
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.SyncRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.SyncState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class SyncRepositoryImpl(
    private val checklistDao: ChecklistDao,
    private val fillDao: ChecklistFillDao,
    private val firestoreDataSource: FirestoreSyncDataSource,
    private val authRepository: GoogleAuthRepository,
    private val initialUploadGate: InitialUploadGate,
    private val attachmentCloudStorage: AttachmentCloudStoragePort,
    private val scope: CoroutineScope,
    private val logger: AppLogger,
) : SyncRepository {

    companion object {
        private const val TAG = "Sync"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Disabled)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private var authListenerJob: Job? = null
    private var cloudListenerJob: Job? = null

    private val currentUserId: String?
        get() = authRepository.currentUser?.firebaseUid

    init {
        log("init: starting auth state listener")
        authListenerJob = authRepository.authState
            .onEach { state ->
                when (state) {
                    is GoogleAuthState.Authenticated -> {
                        logInfo("auth: authenticated uid=${state.user.firebaseUid}")
                        _syncState.value = SyncState.Idle
                        scope.launch { onUserAuthenticated(state.user.firebaseUid) }
                    }
                    is GoogleAuthState.Loading -> log("auth: loading...")
                    is GoogleAuthState.Error -> {
                        logError("auth: error=${state.message}")
                        _syncState.value = SyncState.Disabled
                        stopListening()
                    }
                    is GoogleAuthState.NotAuthenticated -> {
                        log("auth: not authenticated, sync disabled")
                        _syncState.value = SyncState.Disabled
                        stopListening()
                    }
                }
            }
            .launchIn(scope)
    }

    override fun observeCloudChecklistIds(): Flow<AppResult<List<String>>> {
        val uid = currentUserId ?: return emptyFlow()
        return firestoreDataSource.observeChecklistIds(uid)
    }

    override fun observeCloudChecklist(cloudId: String): Flow<AppResult<Checklist>> {
        val uid = currentUserId ?: return emptyFlow()
        return firestoreDataSource.observeChecklist(uid, cloudId).map { result ->
            when (result) {
                is AppResult.Success -> AppResult.Success(result.data.toDomain())
                is AppResult.Error -> result
                is AppResult.Loading -> result
            }
        }
    }

    override suspend fun pushPendingChanges(): AppResult<Unit> {
        val uid = currentUserId ?: run {
            // Not signed in yet — nothing to push (sync requires auth). This is a
            // documented no-op, NOT a failure: dirty rows stay marked and push on the
            // next emission once auth is ready. Returning an Error here surfaced a
            // Crashlytics non-fatal on every pre-auth cold-start race (issue 2018d810),
            // a benign self-healing condition — a Success no-op is the correct result.
            log("push: skipped — not authenticated yet (no-op)")
            return AppResult.Success(Unit)
        }
        _syncState.value = SyncState.Syncing
        return try {
            val pendingChecklists = checklistDao.getPendingSync()
            log("push: ${pendingChecklists.size} pending checklists")
            val uploadedChecklistIds = mutableSetOf<Long>()
            for (entity in pendingChecklists) {
                if (entity.syncStatus == SyncStatus.PENDING_DELETE.value) {
                    val cid = entity.cloudId
                    if (cid != null) {
                        log("push: deleting '${entity.name}' cloudId=$cid")
                        firestoreDataSource.deleteChecklist(uid, cid)
                    } else {
                        // Legacy row that never reached the cloud — nothing to delete
                        // remotely, just drop it locally.
                        log("push: deleting local-only '${entity.name}' (no cloudId)")
                    }
                    checklistDao.deleteById(entity.id)
                } else {
                    uploadChecklistEntity(uid, entity)
                    uploadedChecklistIds += entity.id
                }
            }

            // Fills are embedded inside their parent checklist's Firestore document, so a
            // fill edited in place (item checked/unchecked, note, attachment, item added
            // or removed within the fill) marks ONLY the fill PENDING_UPLOAD while its
            // parent checklist stays SYNCED — and a SYNCED parent is absent from the loop
            // above. Without this pass such edits never reach the cloud: the long-standing
            // "checked items don't sync to web" bug. Re-upload the parent of every
            // PENDING_UPLOAD fill (skipping parents already uploaded above), which carries
            // the fill content and bumps the document's updatedAt so other devices' LWW
            // merge accepts it. PENDING_UPLOAD fills under an already-uploaded parent were
            // marked SYNCED by uploadChecklistEntity, so they no longer appear here.
            val pendingFills = fillDao.getPendingSync()
            val dirtyFillParentIds = pendingFills
                .filter { it.syncStatus == SyncStatus.PENDING_UPLOAD.value }
                .map { it.checklistId }
                .toSet() - uploadedChecklistIds
            if (dirtyFillParentIds.isNotEmpty()) {
                log("push: ${dirtyFillParentIds.size} checklist(s) with dirty fills — re-uploading parent")
            }
            for (checklistId in dirtyFillParentIds) {
                val parent = checklistDao.getById(checklistId) ?: continue
                // A pending-delete / already-removed parent is handled by the delete path;
                // don't resurrect it here.
                if (parent.isDeleted || parent.syncStatus == SyncStatus.PENDING_DELETE.value) continue
                uploadChecklistEntity(uid, parent)
            }

            // Drop any local PENDING_DELETE fill tombstones: the parent re-upload above
            // already removed them from the cloud document via active-fills filtering.
            for (fill in pendingFills) {
                if (fill.syncStatus == SyncStatus.PENDING_DELETE.value) {
                    fillDao.deleteById(fill.id)
                }
            }

            _syncState.value = SyncState.Idle
            logInfo("push: complete")
            AppResult.Success(Unit)
        } catch (e: Exception) {
            logError("push: ERROR ${e.message}", e)
            _syncState.value = SyncState.Error(e.message ?: "Sync failed")
            AppResult.Error(e)
        }
    }

    /**
     * Uploads any not-yet-uploaded attachment bytes for [fill] to Firebase Storage, stamping
     * [com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment.storagePath].
     * Returns the fill with updated items (persisted to Room) so the synced itemsJson carries the
     * cloud keys. A failed upload leaves storagePath null — that attachment retries on the next push
     * while the rest of the document still syncs.
     *
     * Only fields inside the items JSON change here; updatedAt/syncStatus are intentionally left as
     * they are so the caller still bumps/marks them exactly as before. fillDao.insert() is an upsert
     * on the existing id, so the row identity (and thus the subsequent markSynced(id)) is preserved.
     */
    private suspend fun uploadPendingAttachments(uid: String, fill: ChecklistFillEntity): ChecklistFillEntity {
        val hasPending = fill.items.any { item ->
            item.attachments.any { a -> a.storagePath == null && a.path.isNotBlank() }
        }
        if (!hasPending) return fill
        var changed = false
        val updatedItems = fill.items.map { item ->
            if (item.attachments.none { it.storagePath == null && it.path.isNotBlank() }) return@map item
            val updatedAtts = item.attachments.map { att ->
                if (att.storagePath != null || att.path.isBlank()) return@map att
                val key = AttachmentCloudPaths.forAttachment(uid, fill.id, item.id, att.id, att.fileName)
                when (val r = attachmentCloudStorage.upload(att.path, key)) {
                    is AppResult.Success -> {
                        changed = true
                        att.copy(storagePath = key)
                    }
                    is AppResult.Error -> {
                        logError("attachment upload failed id=${att.id}: ${r.exception.message}", r.exception)
                        att
                    }
                    is AppResult.Loading -> att
                }
            }
            item.withAttachments(updatedAtts)
        }
        if (!changed) return fill
        val updatedFill = fill.copy(items = updatedItems)
        // Persist the stamped storagePath; updatedAt/syncStatus untouched (caller bumps/marks).
        fillDao.insert(updatedFill)
        return updatedFill
    }

    /**
     * Resolves the cloudId a checklist must be uploaded under, generating and PERSISTING one for
     * legacy rows created before cloud sync existed.
     *
     * MIGRATION_14_15 added `cloudId TEXT DEFAULT NULL` alongside `syncStatus INTEGER NOT NULL
     * DEFAULT 0`, i.e. every pre-sync checklist is (cloudId = null, syncStatus = SYNCED). Both
     * upload paths must resolve that null: the push path reaches only PENDING rows
     * (getPendingSync filters `syncStatus != 0`), so for a legacy SYNCED row initialUpload() is the
     * ONLY path that ever sees it — before this existed it fabricated `cloudId = ""` and Firestore
     * rejected the whole batch.
     *
     * Persisting is what makes it idempotent: without assignCloudId the next start would mint a
     * DIFFERENT id for the same checklist and duplicate it in the cloud.
     */
    private suspend fun ensureCloudId(entity: ChecklistEntity): String =
        entity.cloudId ?: generateCloudId().also { newId ->
            logInfo("backfilling missing cloudId for '${entity.name}' -> $newId")
            checklistDao.assignCloudId(entity.id, newId)
        }

    /**
     * Per-fill analogue of [ensureCloudId]. A blank fill identity is worse than a rejected write:
     * fills are matched across devices by cloudId, so a `""` fill would make
     * [mergeRemoteChecklist]'s `fillDao.getByCloudId("")` match an unrelated blank-id fill and
     * corrupt the merge.
     */
    private suspend fun ensureFillCloudId(fill: ChecklistFillEntity): String =
        fill.cloudId ?: generateCloudId().also { newId ->
            logInfo("backfilling missing cloudId for fill id=${fill.id} -> $newId")
            fillDao.assignCloudId(fill.id, newId)
        }

    /**
     * Uploads one checklist (with its active fills embedded) to Firestore and, on
     * success, marks the checklist and those fills SYNCED.
     *
     * Missing cloudIds are resolved (and persisted) by [ensureCloudId] / [ensureFillCloudId].
     *
     * updatedAt is bumped to push time both in the uploaded document and locally: a fill
     * edited in place does NOT advance its parent's updatedAt, so re-uploading the parent
     * with its stale timestamp would be SKIPped by other devices' Last-Write-Wins merge
     * and the embedded-fill change would never propagate. Writing the same fresh value to
     * the cloud and to Room keeps the two copies consistent and strictly newer than the
     * copy other devices hold.
     */
    private suspend fun uploadChecklistEntity(
        uid: String,
        entity: ChecklistEntity,
    ) {
        val cid = ensureCloudId(entity)
        // Upload not-yet-uploaded attachment bytes BEFORE serializing fills, so the
        // itemsJson embedded in the cloud document carries each attachment's storagePath
        // (the cross-device anchor). Stamping persists to Room too — id is preserved, so
        // the markSynced(it.id) below still targets the same rows.
        val rawFills = fillDao.getActiveFillsByChecklistId(entity.id)
        val fills = rawFills.map { uploadPendingAttachments(uid, it) }
        val fillSyncData = fills.map { fill -> fill.toFillSyncData(cloudId = ensureFillCloudId(fill)) }
        val now = currentTimeMillis()
        val syncData = entity.copy(updatedAt = now).toSyncData(cloudId = cid, fills = fillSyncData)
        log("push: uploading '${entity.name}' cloudId=$cid, ${fills.size} fills")
        when (val result = firestoreDataSource.uploadChecklist(uid, syncData)) {
            is AppResult.Success -> {
                checklistDao.markSynced(entity.id, updatedAt = now)
                fills.forEach { fillDao.markSynced(it.id, updatedAt = now) }
                log("push: '${entity.name}' synced OK")
            }
            is AppResult.Error ->
                logError("push: '${entity.name}' FAILED: ${result.exception.message}", result.exception)
            is AppResult.Loading -> Unit
        }
    }

    override suspend fun initialUpload(): AppResult<Unit> {
        val uid = currentUserId ?: return AppResult.Error(Exception("Not authenticated"))
        _syncState.value = SyncState.Syncing
        return try {
            log("initialUpload: assigning userId=$uid to all local data")
            checklistDao.assignUserIdToAll(uid)
            fillDao.assignUserIdToAll(uid)

            val allChecklists = checklistDao.getAllActive()
            log("initialUpload: ${allChecklists.size} active checklists to upload")
            val syncDataList = allChecklists.map { entity ->
                // getAllActive() is NOT filtered by syncStatus, so unlike the push path this sees
                // legacy (cloudId = null, SYNCED) rows — they must be backfilled here or they reach
                // Firestore with a blank document id and sink the whole batch.
                val cid = ensureCloudId(entity)
                val fills = fillDao.getActiveFillsByChecklistId(entity.id)
                    .map { uploadPendingAttachments(uid, it) }
                val fillSyncData = fills.map { fill -> fill.toFillSyncData(cloudId = ensureFillCloudId(fill)) }
                entity.toSyncData(cloudId = cid, fills = fillSyncData)
            }

            if (syncDataList.isNotEmpty()) {
                log("initialUpload: batch uploading ${syncDataList.size} checklists")
                when (val result = firestoreDataSource.uploadBatch(uid, syncDataList)) {
                    is AppResult.Success -> {
                        val now = currentTimeMillis()
                        allChecklists.forEach { checklistDao.markSynced(it.id, updatedAt = now) }
                        logInfo("initialUpload: batch OK, all marked synced")
                    }
                    is AppResult.Error -> {
                        // Propagate the failure so the caller (onUserAuthenticated) does NOT
                        // mark the one-time gate — the upload must retry on the next start
                        // instead of being silently skipped forever (which would strand
                        // pre-login local data off the cloud).
                        logError("initialUpload: batch FAILED: ${result.exception.message}", result.exception)
                        _syncState.value = SyncState.Error(result.exception.message ?: "Initial upload failed")
                        return AppResult.Error(result.exception)
                    }
                    is AppResult.Loading -> Unit
                }
            } else {
                log("initialUpload: no checklists to upload")
            }

            _syncState.value = SyncState.Idle
            AppResult.Success(Unit)
        } catch (e: Exception) {
            logError("initialUpload: ERROR ${e.message}", e)
            _syncState.value = SyncState.Error(e.message ?: "Initial upload failed")
            AppResult.Error(e)
        }
    }

    override suspend fun pullAndMerge(): AppResult<Unit> {
        val uid = currentUserId ?: return AppResult.Error(Exception("Not authenticated"))
        _syncState.value = SyncState.Syncing
        log("pull: fetching all checklists from Firestore for uid=$uid")
        return try {
            when (val result = firestoreDataSource.fetchAllChecklists(uid)) {
                is AppResult.Success -> {
                    log("pull: received ${result.data.size} remote checklists")
                    for (remote in result.data) {
                        mergeRemoteChecklist(remote)
                    }
                    // Reconciliation runs ONLY on a full successful fetch (this branch).
                    // A partial/failed fetch (Error/Loading) must NOT delete anything,
                    // otherwise a transient network error would wipe local data.
                    reconcileDeletedRemotely(result.data)
                    _syncState.value = SyncState.Idle
                    log("pull: merge complete")
                    AppResult.Success(Unit)
                }
                is AppResult.Error -> {
                    logError("pull: FAILED: ${result.exception.message}", result.exception)
                    _syncState.value = SyncState.Error(result.exception.message ?: "Pull failed")
                    result
                }
                is AppResult.Loading -> AppResult.Loading
            }
        } catch (e: Exception) {
            logError("pull: ERROR ${e.message}", e)
            _syncState.value = SyncState.Error(e.message ?: "Pull failed")
            AppResult.Error(e)
        }
    }

    override suspend fun startListening() {
        val uid = currentUserId ?: return
        cloudListenerJob?.cancel()
        log("listener: starting real-time listener for uid=$uid")
        cloudListenerJob = firestoreDataSource.observeChecklistIds(uid)
            .onEach { result ->
                if (result is AppResult.Success) {
                    log("listener: snapshot received, ${result.data.size} IDs — pulling")
                    pullAndMerge()
                } else if (result is AppResult.Error) {
                    log("listener: error: ${result.exception.message}")
                }
            }
            .launchIn(scope)
    }

    override suspend fun stopListening() {
        cloudListenerJob?.cancel()
        cloudListenerJob = null
        log("listener: stopped")
    }

    private suspend fun onUserAuthenticated(userId: String) {
        logInfo("onAuth: starting sync pipeline for uid=$userId")
        // Claim any local "guest" rows (created before login) for this uid.
        // Idempotent (WHERE userId IS NULL) — safe to run on every start.
        checklistDao.assignUserIdToAll(userId)
        fillDao.assignUserIdToAll(userId)

        // One-time bulk upload of pre-existing local data — ONLY on the first link
        // of this uid. On subsequent starts this is skipped so that checklists
        // deleted on another device are not resurrected by re-uploading the full
        // local set. Firestore is the source of truth from here on.
        if (!initialUploadGate.isInitialUploadDone(userId)) {
            logInfo("onAuth: first link for uid=$userId — running initialUpload()")
            val result = initialUpload()
            if (result is AppResult.Success) {
                initialUploadGate.markInitialUploadDone(userId)
                logInfo("onAuth: initialUpload done, gate marked for uid=$userId")
            } else {
                // Do NOT mark the gate on failure — retry the one-time upload on the
                // next start rather than silently skipping it forever.
                logError("onAuth: initialUpload failed — gate NOT marked, will retry next start")
            }
        } else {
            log("onAuth: initialUpload already done for uid=$userId — skipping")
        }

        // Push local pending changes (PENDING_UPLOAD -> upload, PENDING_DELETE ->
        // hard-delete in cloud) BEFORE pulling, so reconciliation sees an up-to-date
        // cloud and our just-pushed rows are present in the remote set.
        pushPendingChanges()
        pullAndMerge()
        startListening()
        logInfo("onAuth: sync pipeline started")
    }

    private suspend fun mergeRemoteChecklist(remote: ChecklistSyncData) {
        val local = checklistDao.getByCloudId(remote.cloudId)
        if (local == null) {
            // An incoming system-Inbox document must be reconciled against the Inbox this device may
            // have created for itself before it ever knew about the account's one — inserting it
            // blind leaves two flagged rows and strands one of them (see [resolveIncomingInbox]).
            if (remote.isInbox && !resolveIncomingInbox(remote)) return
            log("merge: NEW '${remote.name}' cloudId=${remote.cloudId}")
            val domainChecklist = remote.toDomain()
            val localId = checklistDao.insert(
                domainChecklist.toInsertEntity(isInbox = resolveIsInbox(remote, local = null)),
            )
            for (fillData in remote.fills) {
                fillDao.insert(fillData.toInsertEntity(checklistId = localId))
            }
        } else if (local.syncStatus == SyncStatus.PENDING_UPLOAD.value) {
            // Local has UNSYNCED edits that have not yet reached the cloud. The remote
            // snapshot we are merging is necessarily older than (or, in a sync race, an echo
            // of a partial state behind) these edits — the next pushPendingChanges() carries
            // the local changes up and the cloud catches up. Overwriting here would clobber a
            // just-made local change (e.g. a reorder) with a stale cloud document.
            //
            // This is the safety net for the reorder race: finalizeReorder writes the new
            // order PENDING_UPLOAD; if a push stamped a fresh updatedAt onto an intermediate
            // stale state and the real-time listener echoed it back, remote.updatedAt could be
            // >= local.updatedAt with STALE items. LWW alone would then pick the stale remote.
            // Gating on PENDING_UPLOAD keeps the local unsynced order until it is pushed.
            //
            // SYNCED rows are unaffected: a genuinely newer remote from ANOTHER device still
            // wins via the LWW branch below, so cross-device edits are not lost.
            log("merge: SKIP '${remote.name}' (local has pending unsynced edits)")
        } else if (remote.updatedAt > local.updatedAt) {
            log("merge: UPDATE '${remote.name}' remote=${remote.updatedAt} > local=${local.updatedAt}")
            // isInbox is WRITE-ONCE locally, not last-write-wins: a document written by an older
            // build or by the MCP worker carries no isInbox, decodes to false, and a plain overwrite
            // would demote the system Inbox into an ordinary checklist that then appears in Projects.
            // [resolveIsInbox] applies exactly that rule, bounded to ONE flagged row per device.
            val updated = remote.toDomain().toUpdateEntity(
                localId = local.id,
                isInbox = resolveIsInbox(remote, local = local),
            )
            checklistDao.update(updated)
            // Merge fills by cloudId: insert ones the cloud has but we don't, and
            // overwrite local fills with a newer remote version.
            for (fillData in remote.fills) {
                val existingFill = fillDao.getByCloudId(fillData.cloudId)
                if (existingFill == null) {
                    fillDao.insert(fillData.toInsertEntity(checklistId = local.id))
                } else if (
                    existingFill.syncStatus != SyncStatus.PENDING_UPLOAD.value &&
                    fillData.updatedAt > existingFill.updatedAt
                ) {
                    // Same PENDING_UPLOAD guard at the per-fill level: an in-place fill edit
                    // (checkbox/note/attachment) not yet pushed must not be overwritten by an
                    // older/echoed remote fill. SYNCED fills still take a genuinely newer remote.
                    fillDao.insert(fillData.toUpdateEntity(localId = existingFill.id, checklistId = local.id))
                }
            }
            // ...then reconcile per-fill deletions: a fill SYNCED locally but absent
            // from this (newer) remote snapshot was deleted on another device.
            reconcileDeletedFills(localChecklistId = local.id, remoteFills = remote.fills)
        } else {
            log("merge: SKIP '${remote.name}' (local is newer or equal)")
        }
    }

    /**
     * Resolves the `isInbox` value a merged row must be written with, keeping the system-Inbox
     * identity SINGLE on this device.
     *
     * The write-once rule (`remote.isInbox || local.isInbox`) is preserved verbatim — see
     * [toUpdateEntity] for why a remote `false` may never clear a local `true`. What this adds is the
     * bound the rule was missing: the flag is granted only while NO OTHER row holds it. Two
     * `isInbox = 1` rows cannot both be the Inbox — [ChecklistDao.getInbox] and `observeInbox()` each
     * take one of them, and the loser silently drops out of the projects list, the pickers, the
     * widget and MCP with no way to reach it again. A visible ordinary project is a far better
     * outcome than an unreachable row, so the second candidate is written unflagged and logged.
     *
     * Deciding against the CURRENT database state (rather than against the checklist being merged)
     * is what makes repeated pulls idempotent: once a winner exists, every later pull re-derives the
     * same answer for both rows.
     *
     * @param local the row being updated, or null when the merge is inserting a new one.
     */
    private suspend fun resolveIsInbox(remote: ChecklistSyncData, local: ChecklistEntity?): Boolean {
        if (!remote.isInbox && local?.isInbox != true) return false
        val existingInbox = checklistDao.getInbox()
        if (existingInbox == null || existingInbox.id == local?.id) return true
        logWarn(
            "merge: '${remote.name}' cloudId=${remote.cloudId} claims the system Inbox but local " +
                "id=${existingInbox.id} already holds it — writing it as an ordinary project so it " +
                "stays reachable",
        )
        return false
    }

    /**
     * Reconciles an incoming system-Inbox document against the Inbox row this device already has,
     * and reports whether the document may be inserted at all.
     *
     * How the collision happens: after a reinstall (or on a second device) the Inbox tab roots the
     * cold start, `ensureInbox()` finds an empty database and mints a brand-new Inbox row with a new
     * cloudId, and the pull that follows carries the account's ORIGINAL Inbox document. Nothing
     * matches the two by cloudId, so a blind insert leaves two `isInbox = 1` rows — and only one of
     * them is ever returned by [ChecklistDao.getInbox] / `observeInbox()`, so the other becomes
     * unreachable: absent from the Inbox tab, from Projects, from the pickers, the widget and MCP.
     *
     * Three outcomes, all of them losing nothing:
     *  1. the local Inbox is a throwaway (no task, no reminder, no repeat) and the incoming one
     *     carries content — retire the local row and let the account's real Inbox take the tab;
     *  2. the incoming document is itself a throwaway — a duplicate with nothing in it, so it is not
     *     materialised at all (an empty second "Inbox" in Projects would just eat a free-tier slot).
     *     If it later gains content on its own device, a subsequent pull re-evaluates it as case 1
     *     or 3;
     *  3. both hold content — the document is inserted, and [resolveIsInbox] demotes it to an
     *     ordinary visible project so everything stays reachable while one row keeps the flag.
     *
     * Case 1 is deliberately asymmetric (throwaway local vs content-bearing remote): two devices can
     * never both satisfy it against each other, so they cannot delete each other's Inbox document in
     * a loop. Case 2 stops the common "both devices auto-created an empty Inbox" state from
     * cluttering Projects, and self-heals the moment either side captures its first task.
     *
     * The retirement uses the ordinary PENDING_DELETE tombstone rather than a hard delete: the empty
     * row may already have been pushed by `initialUpload()`, and only the tombstone makes the next
     * push remove that document too — left in the cloud it would come back on every pull.
     */
    private suspend fun resolveIncomingInbox(remote: ChecklistSyncData): Boolean {
        val localInbox = checklistDao.getInbox() ?: return true
        // "Throwaway" = nothing to lose on either side. Reminder/repeat are checked on top of the
        // task scan: a task-less Inbox can still carry a schedule the user set, and that is content.
        val remoteIsThrowaway = remote.holdsNoTasks() &&
            remote.reminderAt == null &&
            remote.repeatRule == null
        if (remoteIsThrowaway) {
            logWarn(
                "merge: empty duplicate Inbox cloudId=${remote.cloudId} — not materialising it, " +
                    "local Inbox id=${localInbox.id} keeps the tab",
            )
            return false
        }
        val localIsThrowaway = localInbox.holdsNoTasks() &&
            localInbox.reminderAt == null &&
            localInbox.repeatRule == null
        if (localIsThrowaway) {
            // Fills first, then the row — same order as reconcileDeletedRemotely, so no fill is left
            // pointing at a checklist that is on its way out.
            fillDao.deleteByChecklistId(localInbox.id)
            checklistDao.softDelete(localInbox.id, updatedAt = currentTimeMillis())
            logInfo(
                "merge: retired empty locally-created Inbox id=${localInbox.id} in favour of cloud " +
                    "Inbox cloudId=${remote.cloudId}",
            )
        }
        // Case 3 needs no log here — resolveIsInbox reports the demotion when it writes the row.
        return true
    }

    /** True when this local row carries no task at all — neither template items nor any fill item. */
    private suspend fun ChecklistEntity.holdsNoTasks(): Boolean =
        items.isEmpty() && fillDao.getActiveFillsByChecklistId(id).all { it.items.isEmpty() }

    /**
     * Remote twin of [ChecklistEntity.holdsNoTasks]. Quick capture writes the template + fill PAIR,
     * so the template alone would answer for an Inbox written by this app — the fills are scanned
     * too so that a fill-only writer (an older build, the MCP worker) can never make a task-bearing
     * document look empty and get itself discarded.
     */
    private fun ChecklistSyncData.holdsNoTasks(): Boolean =
        toDomain().items.isEmpty() &&
            fills.all { fill ->
                json.decodeFromString(
                    ListSerializer(ChecklistFillItem.serializer()),
                    fill.itemsJson.ifEmpty { "[]" },
                ).isEmpty()
            }

    /**
     * Removes local SYNCED checklists that are absent from the cloud fetch — i.e.
     * they were hard-deleted on another device. Firestore is the source of truth:
     * "document gone" == "deleted everywhere".
     *
     * SAFETY:
     * - Only touches SYNCED rows ([ChecklistDao.getSyncedCloudIds] filters on
     *   syncStatus == 0). PENDING_UPLOAD(1) (e.g. a checklist just created locally,
     *   not yet in the cloud) and PENDING_DELETE(2) are NEVER reconciled away.
     * - Caller guarantees this runs only on a full successful fetch.
     *
     * Idempotent: a second pass finds no stale ids (the rows are already gone), so
     * the per-snapshot listener can call it repeatedly without harm.
     */
    private suspend fun reconcileDeletedRemotely(remoteChecklists: List<ChecklistSyncData>) {
        val remoteCloudIds = remoteChecklists.map { it.cloudId }.toSet()
        val staleCloudIds = checklistDao.getSyncedCloudIds().toSet() - remoteCloudIds
        if (staleCloudIds.isEmpty()) return
        log("reconcile: ${staleCloudIds.size} local SYNCED checklist(s) absent from cloud — removing")
        for (cid in staleCloudIds) {
            val entity = checklistDao.getByCloudId(cid) ?: continue
            // Remove the checklist's fills first, then the checklist itself (hard
            // local delete — no PENDING_DELETE, the cloud already has none).
            fillDao.deleteByChecklistId(entity.id)
            checklistDao.deleteById(entity.id)
            logInfo("reconcile: removed local '${entity.name}' (absent from cloud)")
        }
    }

    /**
     * Per-fill analogue of [reconcileDeletedRemotely], scoped to one checklist.
     * After the UPDATE branch has merged [remoteFills], any local fill that is
     * SYNCED but whose cloudId is absent from [remoteFills] was hard-deleted on
     * another device => remove it locally.
     *
     * Called ONLY from the UPDATE branch of [mergeRemoteChecklist] — i.e. when the
     * remote checklist is newer and therefore authoritative for this checklist's
     * fills. The SKIP branch (local newer) intentionally leaves fills untouched:
     * reconciling there could wipe a local fill edit against a stale snapshot.
     *
     * SAFETY (mirror of the checklist-level rules):
     * - Only SYNCED fills are considered ([ChecklistFillDao.getSyncedFillCloudIds]
     *   filters on syncStatus == 0). A PENDING_UPLOAD(1) fill just created locally
     *   is not yet in the cloud and must survive; PENDING_DELETE(2) is pushed by
     *   pushPendingChanges, not reconciled here.
     * - The caller runs this only on a full successful fetch.
     *
     * Idempotent: a second merge finds no stale ids (the rows are already gone).
     */
    private suspend fun reconcileDeletedFills(localChecklistId: Long, remoteFills: List<FillSyncData>) {
        val remoteFillCloudIds = remoteFills.map { it.cloudId }.toSet()
        val staleFillCloudIds =
            fillDao.getSyncedFillCloudIds(localChecklistId).toSet() - remoteFillCloudIds
        if (staleFillCloudIds.isEmpty()) return
        log("reconcile: ${staleFillCloudIds.size} local SYNCED fill(s) absent from cloud — removing")
        for (cid in staleFillCloudIds) {
            val fill = fillDao.getByCloudId(cid) ?: continue
            fillDao.deleteById(fill.id)
            logInfo("reconcile: removed local fill cloudId=$cid (absent from cloud)")
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun generateCloudId(): String = Uuid.random().toString()

    private fun log(msg: String) = logger.debug(TAG, msg)
    private fun logInfo(msg: String) = logger.info(TAG, msg)
    private fun logWarn(msg: String) = logger.warning(TAG, msg)
    private fun logError(msg: String, e: Throwable? = null) = logger.error(TAG, msg, e)

    // ── Mapping helpers ──

    /**
     * [cloudId] is a REQUIRED parameter, deliberately shadowing the entity's own nullable
     * `cloudId`: the cloud identity must arrive already resolved (via [ensureCloudId]) from the
     * caller. It used to read `cloudId ?: ""` off the receiver, which let initialUpload() ship a
     * blank Firestore document id and fail every legacy user's entire first sync. Keeping the
     * fallback out of the mapper makes that class of bug a compile error, not a runtime one.
     */
    private fun ChecklistEntity.toSyncData(
        cloudId: String,
        fills: List<FillSyncData>,
    ) = ChecklistSyncData(
        cloudId = cloudId,
        name = name,
        itemsJson = json.encodeToString(ListSerializer(ChecklistItem.serializer()), items),
        reminderAt = reminderAt,
        repeatRule = repeatRule?.let { json.encodeToString(ReminderRepeatRule.serializer(), it) },
        repeatTimeOfDayMinutes = repeatTimeOfDayMinutes,
        repeatNextAt = repeatNextAt,
        repeatOccurrenceCount = repeatOccurrenceCount,
        reminderFullScreen = reminderFullScreen,
        separateCompleted = separateCompleted,
        position = position,
        autoDeleteCompleted = autoDeleteCompleted,
        viewMode = viewMode.name,
        foldersEnabled = foldersEnabled,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        isInbox = isInbox,
        fills = fills,
    )

    /** Fill analogue of [toSyncData] — see there for why [cloudId] is a required parameter. */
    private fun ChecklistFillEntity.toFillSyncData(cloudId: String) =
        FillSyncData(
            cloudId = cloudId,
            name = name,
            itemsJson = json.encodeToString(ListSerializer(ChecklistFillItem.serializer()), items),
            coverImagePath = coverImagePath,
            createdAt = createdAt,
            isDefault = isDefault,
            updatedAt = updatedAt,
            isDeleted = isDeleted,
        )

    private fun ChecklistSyncData.toDomain() = Checklist(
        name = name,
        items = json.decodeFromString(ListSerializer(ChecklistItem.serializer()), itemsJson.ifEmpty { "[]" }),
        reminderAt = reminderAt,
        repeatRule = repeatRule?.let { runCatching { json.decodeFromString(ReminderRepeatRule.serializer(), it) }.getOrNull() },
        repeatTimeOfDayMinutes = repeatTimeOfDayMinutes,
        repeatNextAt = repeatNextAt,
        repeatOccurrenceCount = repeatOccurrenceCount,
        reminderFullScreen = reminderFullScreen,
        separateCompleted = separateCompleted,
        position = position,
        autoDeleteCompleted = autoDeleteCompleted,
        viewMode = ChecklistViewMode.entries
            .firstOrNull { it.name == viewMode } ?: ChecklistViewMode.Standard,
        foldersEnabled = foldersEnabled,
        cloudId = cloudId,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        isInbox = isInbox,
    )

    /**
     * @param isInbox the value to persist. Defaults to the remote's, but the merge passes the value
     *   [resolveIsInbox] arrived at: a cloud document may carry the flag while this device already
     *   has an Inbox row of its own, and inserting a second flagged row makes one of the two
     *   unreachable.
     */
    private fun Checklist.toInsertEntity(
        isInbox: Boolean = this.isInbox,
    ) = com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistEntity(
        name = name,
        items = items,
        reminderAt = reminderAt,
        repeatRule = repeatRule,
        repeatTimeOfDayMinutes = repeatTimeOfDayMinutes,
        repeatNextAt = repeatNextAt,
        repeatOccurrenceCount = repeatOccurrenceCount,
        reminderFullScreen = reminderFullScreen,
        separateCompleted = separateCompleted,
        position = position,
        autoDeleteCompleted = autoDeleteCompleted,
        viewMode = viewMode,
        foldersEnabled = foldersEnabled,
        cloudId = cloudId,
        userId = currentUserId,
        updatedAt = updatedAt,
        syncStatus = SyncStatus.SYNCED.value,
        isDeleted = isDeleted,
        isInbox = isInbox,
    )

    /**
     * @param isInbox the value to persist. Defaults to the remote's, but the LWW caller passes
     *   `remote.isInbox || local.isInbox`: the flag is **write-once locally**. A device on an older
     *   build (or the MCP worker) writes a document with no `isInbox`; it decodes to false, and a
     *   plain overwrite here would silently demote the system Inbox to an ordinary checklist that
     *   then pops up in the Projects list. Never let a remote `false` clear a local `true`.
     */
    private fun Checklist.toUpdateEntity(
        localId: Long,
        isInbox: Boolean = this.isInbox,
    ) = com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistEntity(
        id = localId,
        name = name,
        items = items,
        reminderAt = reminderAt,
        repeatRule = repeatRule,
        repeatTimeOfDayMinutes = repeatTimeOfDayMinutes,
        repeatNextAt = repeatNextAt,
        repeatOccurrenceCount = repeatOccurrenceCount,
        reminderFullScreen = reminderFullScreen,
        separateCompleted = separateCompleted,
        position = position,
        autoDeleteCompleted = autoDeleteCompleted,
        viewMode = viewMode,
        foldersEnabled = foldersEnabled,
        cloudId = cloudId,
        userId = currentUserId,
        updatedAt = updatedAt,
        syncStatus = SyncStatus.SYNCED.value,
        isDeleted = isDeleted,
        isInbox = isInbox,
    )

    private fun FillSyncData.toInsertEntity(checklistId: Long) =
        com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillEntity(
            checklistId = checklistId,
            name = name,
            items = json.decodeFromString(ListSerializer(ChecklistFillItem.serializer()), itemsJson.ifEmpty { "[]" }),
            coverImagePath = coverImagePath,
            createdAt = createdAt,
            isDefault = isDefault,
            cloudId = cloudId,
            userId = currentUserId,
            updatedAt = updatedAt,
            syncStatus = SyncStatus.SYNCED.value,
            isDeleted = isDeleted,
        )

    private fun FillSyncData.toUpdateEntity(localId: Long, checklistId: Long) =
        com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillEntity(
            id = localId,
            checklistId = checklistId,
            name = name,
            items = json.decodeFromString(ListSerializer(ChecklistFillItem.serializer()), itemsJson.ifEmpty { "[]" }),
            coverImagePath = coverImagePath,
            createdAt = createdAt,
            isDefault = isDefault,
            cloudId = cloudId,
            userId = currentUserId,
            updatedAt = updatedAt,
            syncStatus = SyncStatus.SYNCED.value,
            isDeleted = isDeleted,
        )
}
