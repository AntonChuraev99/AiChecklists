package com.antonchuraev.homesearchchecklist.feature.checklist.data.sync

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
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
    private val scope: CoroutineScope,
) : SyncRepository {

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
                        log("auth: authenticated uid=${state.user.firebaseUid}")
                        _syncState.value = SyncState.Idle
                        scope.launch { onUserAuthenticated(state.user.firebaseUid) }
                    }
                    is GoogleAuthState.Loading -> log("auth: loading...")
                    is GoogleAuthState.Error -> {
                        log("auth: error=${state.message}")
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
                        log("push: '${entity.name}' FAILED: ${result.exception.message}")
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
            log("push: complete")
            AppResult.Success(Unit)
        } catch (e: Exception) {
            log("push: ERROR ${e.message}")
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
                val result = firestoreDataSource.uploadBatch(uid, syncDataList)
                if (result is AppResult.Success) {
                    val now = currentTimeMillis()
                    allChecklists.forEach { checklistDao.markSynced(it.id, updatedAt = now) }
                    log("initialUpload: batch OK, all marked synced")
                } else if (result is AppResult.Error) {
                    log("initialUpload: batch FAILED: ${result.exception.message}")
                }
            } else {
                log("initialUpload: no checklists to upload")
            }

            _syncState.value = SyncState.Idle
            AppResult.Success(Unit)
        } catch (e: Exception) {
            log("initialUpload: ERROR ${e.message}")
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
                    _syncState.value = SyncState.Idle
                    log("pull: merge complete")
                    AppResult.Success(Unit)
                }
                is AppResult.Error -> {
                    log("pull: FAILED: ${result.exception.message}")
                    _syncState.value = SyncState.Error(result.exception.message ?: "Pull failed")
                    result
                }
                is AppResult.Loading -> AppResult.Loading
            }
        } catch (e: Exception) {
            log("pull: ERROR ${e.message}")
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
        log("onAuth: starting sync pipeline for uid=$userId")
        checklistDao.assignUserIdToAll(userId)
        fillDao.assignUserIdToAll(userId)
        initialUpload()
        pullAndMerge()
        startListening()
        log("onAuth: sync pipeline started")
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

    private fun log(msg: String) {
        println("[Sync] $msg")
    }

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
