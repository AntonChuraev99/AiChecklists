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
            for (entity in pendingChecklists) {
                val cid = entity.cloudId ?: continue
                if (entity.syncStatus == SyncStatus.PENDING_DELETE.value) {
                    log("push: deleting '${entity.name}' cloudId=$cid")
                    firestoreDataSource.deleteChecklist(uid, cid)
                    checklistDao.deleteById(entity.id)
                } else {
                    val fills = fillDao.getActiveFillsByChecklistId(entity.id)
                    val syncData = entity.toSyncData(fills.map { it.toFillSyncData() })
                    log("push: uploading '${entity.name}' cloudId=$cid, ${fills.size} fills")
                    val result = firestoreDataSource.uploadChecklist(uid, syncData)
                    if (result is AppResult.Success) {
                        checklistDao.markSynced(entity.id, updatedAt = currentTimeMillis())
                        fills.forEach { fillDao.markSynced(it.id, updatedAt = currentTimeMillis()) }
                        log("push: '${entity.name}' synced OK")
                    } else if (result is AppResult.Error) {
                        logError("push: '${entity.name}' FAILED: ${result.exception.message}", result.exception)
                    }
                }
            }

            val pendingFills = fillDao.getPendingSync()
            if (pendingFills.isNotEmpty()) log("push: ${pendingFills.size} pending fills (delete)")
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
        } else if (remote.updatedAt > local.updatedAt) {
            log("merge: UPDATE '${remote.name}' remote=${remote.updatedAt} > local=${local.updatedAt}")
            val updated = remote.toDomain().toUpdateEntity(localId = local.id)
            checklistDao.update(updated)
            // Fills are merged by cloudId (insert new / update newer) but NOT
            // reconciled: a fill deleted on another device is not removed locally.
            // Checklist-level reconciliation (the reported data-loss bug) is fully
            // handled by reconcileDeletedRemotely(). Per-fill deletion is a rarer,
            // lower-impact case and is deferred — see
            // Pending: docs/todos/2026-05-30-sync-fill-reconciliation.md
            for (fillData in remote.fills) {
                val existingFill = fillDao.getByCloudId(fillData.cloudId)
                if (existingFill == null) {
                    fillDao.insert(fillData.toInsertEntity(checklistId = local.id))
                } else if (fillData.updatedAt > existingFill.updatedAt) {
                    fillDao.insert(fillData.toUpdateEntity(localId = existingFill.id, checklistId = local.id))
                }
            }
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
