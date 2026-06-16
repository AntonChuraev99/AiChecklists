package com.antonchuraev.homesearchchecklist.feature.checklist.data.sync

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillDao
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
        val uid = currentUserId ?: return AppResult.Error(Exception("Not authenticated"))
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
     * Uploads one checklist (with its active fills embedded) to Firestore and, on
     * success, marks the checklist and those fills SYNCED.
     *
     * Backfills a missing cloudId for legacy checklists/fills created before cloud sync
     * existed (the column is nullable; such rows used to be silently skipped and never
     * reached the cloud — the web app then showed nothing).
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
        entity: com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistEntity,
    ) {
        val cid = entity.cloudId ?: generateCloudId().also { newId ->
            logInfo("push: backfilling missing cloudId for '${entity.name}' -> $newId")
            checklistDao.assignCloudId(entity.id, newId)
        }
        val fills = fillDao.getActiveFillsByChecklistId(entity.id)
        val fillSyncData = fills.map { fill ->
            val fillCid = fill.cloudId ?: generateCloudId().also { newId ->
                fillDao.assignCloudId(fill.id, newId)
            }
            fill.copy(cloudId = fillCid).toFillSyncData()
        }
        val now = currentTimeMillis()
        val syncData = entity.copy(cloudId = cid, updatedAt = now).toSyncData(fillSyncData)
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
                val fills = fillDao.getActiveFillsByChecklistId(entity.id)
                entity.toSyncData(fills.map { it.toFillSyncData() })
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
            log("merge: NEW '${remote.name}' cloudId=${remote.cloudId}")
            val domainChecklist = remote.toDomain()
            val localId = checklistDao.insert(domainChecklist.toInsertEntity())
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
            val updated = remote.toDomain().toUpdateEntity(localId = local.id)
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
    private fun logError(msg: String, e: Throwable? = null) = logger.error(TAG, msg, e)

    // ── Mapping helpers ──

    private fun com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistEntity.toSyncData(
        fills: List<FillSyncData>
    ) = ChecklistSyncData(
        cloudId = cloudId ?: "",
        name = name,
        itemsJson = json.encodeToString(ListSerializer(ChecklistItem.serializer()), items),
        reminderAt = reminderAt,
        repeatRule = repeatRule?.let { json.encodeToString(ReminderRepeatRule.serializer(), it) },
        repeatTimeOfDayMinutes = repeatTimeOfDayMinutes,
        repeatNextAt = repeatNextAt,
        repeatOccurrenceCount = repeatOccurrenceCount,
        separateCompleted = separateCompleted,
        position = position,
        autoDeleteCompleted = autoDeleteCompleted,
        viewMode = viewMode.name,
        foldersEnabled = foldersEnabled,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        fills = fills,
    )

    private fun com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillEntity.toFillSyncData() =
        FillSyncData(
            cloudId = cloudId ?: "",
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
        separateCompleted = separateCompleted,
        position = position,
        autoDeleteCompleted = autoDeleteCompleted,
        viewMode = ChecklistViewMode.entries
            .firstOrNull { it.name == viewMode } ?: ChecklistViewMode.Standard,
        foldersEnabled = foldersEnabled,
        cloudId = cloudId,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
    )

    private fun Checklist.toInsertEntity() = com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistEntity(
        name = name,
        items = items,
        reminderAt = reminderAt,
        repeatRule = repeatRule,
        repeatTimeOfDayMinutes = repeatTimeOfDayMinutes,
        repeatNextAt = repeatNextAt,
        repeatOccurrenceCount = repeatOccurrenceCount,
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
    )

    private fun Checklist.toUpdateEntity(localId: Long) = com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistEntity(
        id = localId,
        name = name,
        items = items,
        reminderAt = reminderAt,
        repeatRule = repeatRule,
        repeatTimeOfDayMinutes = repeatTimeOfDayMinutes,
        repeatNextAt = repeatNextAt,
        repeatOccurrenceCount = repeatOccurrenceCount,
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
