package com.antonchuraev.homesearchchecklist.feature.checklist.data.sync

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleUser
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistEntity
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillEntity
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncRepositoryImplTest {

    private val uid = "uid-1"
    private val testUser = GoogleUser(firebaseUid = uid, email = "a@b.c", displayName = "A")

    // ─── System under test factory ───────────────────────────────────────

    private fun TestScope.newRepo(): SyncRepositoryImpl =
        SyncRepositoryImpl(
            checklistDao = dao,
            fillDao = fillDao,
            firestoreDataSource = firestore,
            authRepository = auth,
            initialUploadGate = gate,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            logger = NoopLogger,
        )

    // ─── Sync-data builders ──────────────────────────────────────────────

    private fun remote(
        cloudId: String,
        name: String,
        updatedAt: Long = 100L,
        fills: List<FillSyncData> = emptyList(),
    ) =
        ChecklistSyncData(
            cloudId = cloudId,
            name = name,
            itemsJson = "[]",
            updatedAt = updatedAt,
            fills = fills,
        )

    private fun remoteFill(cloudId: String, updatedAt: Long = 100L) =
        FillSyncData(
            cloudId = cloudId,
            name = "",
            itemsJson = "[]",
            updatedAt = updatedAt,
        )

    private fun localSynced(
        id: Long,
        cloudId: String,
        name: String,
        updatedAt: Long = 100L,
    ) = ChecklistEntity(
        id = id,
        name = name,
        items = emptyList(),
        cloudId = cloudId,
        userId = uid,
        updatedAt = updatedAt,
        syncStatus = SyncStatus.SYNCED.value,
        isDeleted = false,
    )

    private fun localPendingUpload(
        id: Long,
        cloudId: String,
        name: String,
        updatedAt: Long = 100L,
    ) = ChecklistEntity(
        id = id,
        name = name,
        items = emptyList(),
        cloudId = cloudId,
        userId = uid,
        updatedAt = updatedAt,
        syncStatus = SyncStatus.PENDING_UPLOAD.value,
        isDeleted = false,
    )

    // ─── Tests: reconciliation (the core data-loss bug) ──────────────────

    @Test
    fun reconcile_removesLocalSyncedAbsentFromCloud() = runTest {
        // Local has 3 SYNCED checklists; cloud (after a web delete) returns only 1.
        dao.checklists.addAll(
            listOf(
                localSynced(1L, "c1", "Keep"),
                localSynced(2L, "c2", "DeletedOnWeb-A"),
                localSynced(3L, "c3", "DeletedOnWeb-B"),
            )
        )
        firestore.fetchResult = AppResult.Success(listOf(remote("c1", "Keep")))

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        val remaining = dao.checklists.map { it.cloudId }.toSet()
        assertEquals(setOf("c1"), remaining, "stale c2/c3 must be reconciled away")
    }

    @Test
    fun reconcile_keepsPendingUploadNotInCloud() = runTest {
        // A freshly created local checklist (PENDING_UPLOAD) is not yet in the cloud.
        // It must NOT be reconciled away, otherwise new offline checklists vanish.
        dao.checklists.addAll(
            listOf(
                localSynced(1L, "c1", "Synced"),
                localPendingUpload(2L, "c2", "BrandNewLocal"),
            )
        )
        firestore.fetchResult = AppResult.Success(listOf(remote("c1", "Synced")))

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        val names = dao.checklists.map { it.name }.toSet()
        assertTrue("BrandNewLocal" in names, "PENDING_UPLOAD row must survive reconciliation")
        assertTrue("Synced" in names)
    }

    @Test
    fun reconcile_skippedOnFetchError() = runTest {
        // A transient fetch error must delete nothing.
        dao.checklists.addAll(
            listOf(
                localSynced(1L, "c1", "A"),
                localSynced(2L, "c2", "B"),
            )
        )
        firestore.fetchResult = AppResult.Error(Exception("network down"))

        val repo = newRepo()
        auth.currentUserOverride = testUser

        val result = repo.pullAndMerge()

        assertTrue(result is AppResult.Error)
        assertEquals(2, dao.checklists.size, "no rows may be removed on fetch error")
    }

    @Test
    fun reconcile_removesFillsOfStaleChecklist() = runTest {
        dao.checklists.add(localSynced(1L, "c1", "DeletedOnWeb"))
        fillDao.fills.addAll(
            listOf(
                fillEntity(id = 10L, checklistId = 1L, cloudId = "f1"),
                fillEntity(id = 11L, checklistId = 1L, cloudId = "f2"),
            )
        )
        firestore.fetchResult = AppResult.Success(emptyList())

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        assertTrue(dao.checklists.isEmpty())
        assertTrue(fillDao.fills.isEmpty(), "fills of a reconciled checklist must be removed too")
    }

    @Test
    fun reconcile_idempotentOnSecondPull() = runTest {
        // Simulates the listener firing pullAndMerge twice in a row.
        dao.checklists.addAll(
            listOf(
                localSynced(1L, "c1", "Keep"),
                localSynced(2L, "c2", "Stale"),
            )
        )
        firestore.fetchResult = AppResult.Success(listOf(remote("c1", "Keep")))

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()
        repo.pullAndMerge() // second snapshot — nothing left to reconcile

        assertEquals(setOf("c1"), dao.checklists.map { it.cloudId }.toSet())
    }

    // ─── Tests: per-fill reconciliation (the deferred follow-up) ─────────

    @Test
    fun reconcileFills_removesSyncedAbsentFromCloud() = runTest {
        // Checklist c1 survives; on another device two of its three fills were
        // deleted. Remote is newer (UPDATE branch) and carries only surviving f1.
        dao.checklists.add(localSynced(1L, "c1", "List", updatedAt = 100L))
        fillDao.fills.addAll(
            listOf(
                fillEntity(id = 10L, checklistId = 1L, cloudId = "f1"),
                fillEntity(id = 11L, checklistId = 1L, cloudId = "f2"),
                fillEntity(id = 12L, checklistId = 1L, cloudId = "f3"),
            )
        )
        firestore.fetchResult = AppResult.Success(
            listOf(remote("c1", "List", updatedAt = 200L, fills = listOf(remoteFill("f1")))),
        )

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        assertEquals(
            setOf("f1"),
            fillDao.fills.map { it.cloudId }.toSet(),
            "stale fills f2/f3 absent from the newer cloud snapshot must be removed",
        )
    }

    @Test
    fun reconcileFills_keepsPendingUploadFill() = runTest {
        // f2 was just created locally (PENDING_UPLOAD) and is not yet in the cloud.
        // It must survive reconciliation despite being absent from remote.fills.
        dao.checklists.add(localSynced(1L, "c1", "List", updatedAt = 100L))
        fillDao.fills.addAll(
            listOf(
                fillEntity(id = 10L, checklistId = 1L, cloudId = "f1"),
                fillEntity(
                    id = 11L,
                    checklistId = 1L,
                    cloudId = "f2",
                    syncStatus = SyncStatus.PENDING_UPLOAD.value,
                ),
            )
        )
        firestore.fetchResult = AppResult.Success(
            listOf(remote("c1", "List", updatedAt = 200L, fills = listOf(remoteFill("f1")))),
        )

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        val remaining = fillDao.fills.map { it.cloudId }.toSet()
        assertTrue("f2" in remaining, "PENDING_UPLOAD fill must survive reconciliation")
        assertTrue("f1" in remaining)
    }

    @Test
    fun reconcileFills_skippedWhenChecklistSkipped() = runTest {
        // Local checklist is NEWER than remote (SKIP branch). Fills must be left
        // untouched — reconciling against a stale snapshot would wipe a local fill.
        dao.checklists.add(localSynced(1L, "c1", "LocalWins", updatedAt = 300L))
        fillDao.fills.addAll(
            listOf(
                fillEntity(id = 10L, checklistId = 1L, cloudId = "f1"),
                fillEntity(id = 11L, checklistId = 1L, cloudId = "f2"),
            )
        )
        firestore.fetchResult = AppResult.Success(
            listOf(remote("c1", "CloudOlder", updatedAt = 200L, fills = listOf(remoteFill("f1")))),
        )

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        assertEquals(
            setOf("f1", "f2"),
            fillDao.fills.map { it.cloudId }.toSet(),
            "SKIP branch must not reconcile fills (local checklist is authoritative)",
        )
    }

    @Test
    fun reconcileFills_scopedToOwningChecklist() = runTest {
        // c1 gets an UPDATE that drops all its fills; c2 is a different checklist.
        // c2's fill must never be considered by c1's per-checklist reconciliation.
        dao.checklists.addAll(
            listOf(
                localSynced(1L, "c1", "Updated", updatedAt = 100L),
                localSynced(2L, "c2", "Other", updatedAt = 100L),
            )
        )
        fillDao.fills.addAll(
            listOf(
                fillEntity(id = 10L, checklistId = 1L, cloudId = "f1"),
                fillEntity(id = 20L, checklistId = 2L, cloudId = "g1"),
            )
        )
        firestore.fetchResult = AppResult.Success(
            listOf(
                remote("c1", "Updated", updatedAt = 200L, fills = emptyList()),
                remote("c2", "Other", updatedAt = 100L, fills = listOf(remoteFill("g1"))),
            ),
        )

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        val remaining = fillDao.fills.map { it.cloudId }.toSet()
        assertFalse("f1" in remaining, "c1's own UPDATE reconciliation drops its absent fill")
        assertTrue("g1" in remaining, "c2's fill must not be touched by c1 reconciliation")
    }

    // ─── Tests: merge correctness (must not regress) ─────────────────────

    @Test
    fun mergeRemote_insertsNewChecklist() = runTest {
        firestore.fetchResult = AppResult.Success(listOf(remote("c1", "FromCloud", updatedAt = 50L)))

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        assertEquals(1, dao.checklists.size)
        assertEquals("FromCloud", dao.checklists.first().name)
    }

    @Test
    fun mergeRemote_updatesWhenRemoteNewer() = runTest {
        dao.checklists.add(localSynced(1L, "c1", "OldName", updatedAt = 100L))
        firestore.fetchResult = AppResult.Success(listOf(remote("c1", "NewName", updatedAt = 200L)))

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        assertEquals("NewName", dao.checklists.single { it.cloudId == "c1" }.name)
    }

    @Test
    fun mergeRemote_skipsWhenLocalNewer() = runTest {
        dao.checklists.add(localSynced(1L, "c1", "LocalWins", updatedAt = 300L))
        firestore.fetchResult = AppResult.Success(listOf(remote("c1", "CloudOlder", updatedAt = 200L)))

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        assertEquals("LocalWins", dao.checklists.single { it.cloudId == "c1" }.name)
    }

    // ─── Tests: initial-upload gate (resurrection root cause #2) ─────────

    @Test
    fun initialUpload_runsOnlyOnFirstLink() = runTest {
        // One local guest checklist exists before login.
        dao.checklists.add(localPendingUpload(1L, "c1", "Guest"))
        firestore.fetchResult = AppResult.Success(emptyList())

        val repo = newRepo()

        // First authentication → pipeline should run initialUpload and mark the gate.
        auth.emitAuthenticated(testUser)
        testScheduler.advanceUntilIdle()

        assertEquals(1, firestore.initialUploadCallCount, "first link must upload")
        assertTrue(gate.doneUids.contains(uid), "gate must be marked after first upload")

        // Reset transient cloud state and re-authenticate (app restart).
        firestore.fetchResult = AppResult.Success(firestore.uploaded.toList())
        auth.emitNotAuthenticated()
        auth.emitAuthenticated(testUser)
        testScheduler.advanceUntilIdle()

        assertEquals(
            1,
            firestore.initialUploadCallCount,
            "second link with same uid must NOT re-run initialUpload",
        )
    }

    @Test
    fun initialUpload_gateNotMarkedOnUploadFailure() = runTest {
        dao.checklists.add(localPendingUpload(1L, "c1", "Guest"))
        firestore.uploadBatchResult = AppResult.Error(Exception("upload failed"))
        firestore.fetchResult = AppResult.Success(emptyList())

        val repo = newRepo()
        auth.emitAuthenticated(testUser)
        testScheduler.advanceUntilIdle()

        assertFalse(
            gate.doneUids.contains(uid),
            "gate must stay unmarked so the one-time upload retries next start",
        )
    }

    // ─── Tests: push backfills missing cloudId (Android→Web upload bug) ──

    @Test
    fun push_backfillsCloudIdForLegacyChecklistAndUploads() = runTest {
        // A legacy checklist created before cloud sync existed: PENDING_UPLOAD but
        // cloudId == null. It used to hit `val cid = entity.cloudId ?: continue` and be
        // silently skipped, so it never reached the cloud (the web app showed nothing).
        dao.checklists.add(
            ChecklistEntity(
                id = 1L,
                name = "Поиск работы",
                items = emptyList(),
                cloudId = null,
                userId = uid,
                updatedAt = 100L,
                syncStatus = SyncStatus.PENDING_UPLOAD.value,
                isDeleted = false,
            ),
        )
        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pushPendingChanges()

        val row = dao.checklists.single()
        assertNotNull(row.cloudId, "legacy null cloudId must be backfilled, not skipped")
        assertEquals(
            SyncStatus.SYNCED.value,
            row.syncStatus,
            "checklist must be marked SYNCED after a successful upload",
        )
        assertTrue(
            firestore.uploaded.any { it.cloudId == row.cloudId },
            "the backfilled checklist must actually be uploaded to the cloud",
        )
    }

    @Test
    fun gate_isPerUid() = runTest {
        gate.doneUids.add("other-uid")
        // A guest checklist must exist so initialUpload() reaches uploadBatch — otherwise
        // an empty getAllActive() legitimately skips the upload and the per-uid run is
        // not observable via initialUploadCallCount.
        dao.checklists.add(localPendingUpload(1L, "c1", "Guest"))

        val repo = newRepo()
        auth.emitAuthenticated(testUser)
        testScheduler.advanceUntilIdle()

        // uid-1 was never marked → initialUpload still ran for it.
        assertEquals(1, firestore.initialUploadCallCount)
        assertTrue(gate.doneUids.contains(uid))
        assertTrue(gate.doneUids.contains("other-uid"))
    }

    // ─── Fakes ───────────────────────────────────────────────────────────

    private val dao = FakeChecklistDao()
    private val fillDao = FakeFillDao()
    private val firestore = FakeFirestore()
    private val gate = FakeGate()
    private val auth = FakeAuth()

    private fun fillEntity(
        id: Long,
        checklistId: Long,
        cloudId: String?,
        syncStatus: Int = SyncStatus.SYNCED.value,
        updatedAt: Long = 100L,
    ) =
        ChecklistFillEntity(
            id = id,
            checklistId = checklistId,
            name = "",
            coverImagePath = null,
            items = emptyList<ChecklistFillItem>(),
            createdAt = 0L,
            cloudId = cloudId,
            userId = uid,
            updatedAt = updatedAt,
            syncStatus = syncStatus,
            isDeleted = false,
        )

    /**
     * In-memory [ChecklistDao]. Only the methods exercised by the sync pipeline carry
     * real behaviour; the rest are no-op/empty stubs (this DAO has 30+ members).
     */
    private class FakeChecklistDao : ChecklistDao {
        val checklists = mutableListOf<ChecklistEntity>()
        private var nextId = 1000L

        override suspend fun getPendingSync(): List<ChecklistEntity> =
            checklists.filter { it.syncStatus != SyncStatus.SYNCED.value }

        override suspend fun getByCloudId(cloudId: String): ChecklistEntity? =
            checklists.firstOrNull { it.cloudId == cloudId }

        override suspend fun getSyncedCloudIds(): List<String> =
            checklists.filter { it.syncStatus == SyncStatus.SYNCED.value && it.cloudId != null }
                .mapNotNull { it.cloudId }

        override suspend fun getAllActive(): List<ChecklistEntity> =
            checklists.filter { !it.isDeleted }

        override suspend fun assignUserIdToAll(userId: String) {
            replaceWith { if (it.userId == null) it.copy(userId = userId) else it }
        }

        override suspend fun assignCloudId(id: Long, cloudId: String) {
            replaceWith { if (it.id == id) it.copy(cloudId = cloudId) else it }
        }

        override suspend fun markSynced(id: Long, status: Int, updatedAt: Long) {
            replaceWith { if (it.id == id) it.copy(syncStatus = status, updatedAt = updatedAt) else it }
        }

        override suspend fun insert(checklist: ChecklistEntity): Long {
            val id = if (checklist.id == 0L) nextId++ else checklist.id
            checklists.removeAll { it.id == id }
            checklists.add(checklist.copy(id = id))
            return id
        }

        override suspend fun update(checklist: ChecklistEntity) {
            replaceWith { if (it.id == checklist.id) checklist else it }
        }

        private fun replaceWith(transform: (ChecklistEntity) -> ChecklistEntity) {
            val mapped = checklists.map(transform)
            checklists.clear()
            checklists.addAll(mapped)
        }

        override suspend fun deleteById(id: Long) {
            checklists.removeAll { it.id == id }
        }

        // ── Unused stubs ──
        override fun observeChecklists(): Flow<List<ChecklistEntity>> = flowOf(emptyList())
        override suspend fun getById(id: Long): ChecklistEntity? = checklists.firstOrNull { it.id == id }
        override fun observeChecklistById(id: Long): Flow<ChecklistEntity?> = flowOf(null)
        override suspend fun updateSyncStatus(id: Long, status: Int) {}
        override suspend fun softDelete(id: Long, updatedAt: Long) {}
        override suspend fun setSeparateCompleted(id: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(id: Long, value: Boolean) {}
        override suspend fun updatePosition(id: Long, position: Int) {}
        override suspend fun incrementAllPositions() {}
        override suspend fun getAllOrderedByPosition(): List<ChecklistEntity> = emptyList()
        override suspend fun updateReminder(id: Long, reminderAt: Long?) {}
        override suspend fun countActiveReminders(nowMillis: Long): Int = 0
        override suspend fun getActiveReminders(nowMillis: Long): List<ChecklistReminderInfo> = emptyList()
        override suspend fun setRepeatSchedule(id: Long, repeatRuleJson: String?, timeMinutes: Int?, nextAt: Long?) {}
        override suspend fun advanceRepeatSchedule(id: Long, nextAt: Long?, newCount: Int) {}
        override suspend fun clearRepeatSchedule(id: Long) {}
        override suspend fun countActiveRepeatSchedules(): Int = 0
        override suspend fun getActiveRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getWeeklyChecklistCount(): Int = 0
        override fun observeWeeklyChecklistCount(): Flow<Int> = flowOf(0)
    }

    /** In-memory [ChecklistFillDao] — same approach as [FakeChecklistDao]. */
    private class FakeFillDao : ChecklistFillDao {
        val fills = mutableListOf<ChecklistFillEntity>()
        private var nextId = 2000L

        override suspend fun getActiveFillsByChecklistId(checklistId: Long): List<ChecklistFillEntity> =
            fills.filter { it.checklistId == checklistId && !it.isDeleted }

        override suspend fun getPendingSync(): List<ChecklistFillEntity> =
            fills.filter { it.syncStatus != SyncStatus.SYNCED.value }

        override suspend fun getByCloudId(cloudId: String): ChecklistFillEntity? =
            fills.firstOrNull { it.cloudId == cloudId }

        override suspend fun getSyncedFillCloudIds(checklistId: Long): List<String> =
            fills.filter {
                it.checklistId == checklistId &&
                    it.syncStatus == SyncStatus.SYNCED.value &&
                    it.cloudId != null
            }.mapNotNull { it.cloudId }

        override suspend fun assignUserIdToAll(userId: String) {
            replaceWith { if (it.userId == null) it.copy(userId = userId) else it }
        }

        override suspend fun assignCloudId(id: Long, cloudId: String) {
            replaceWith { if (it.id == id) it.copy(cloudId = cloudId) else it }
        }

        override suspend fun markSynced(id: Long, status: Int, updatedAt: Long) {
            replaceWith { if (it.id == id) it.copy(syncStatus = status, updatedAt = updatedAt) else it }
        }

        override suspend fun insert(fill: ChecklistFillEntity): Long {
            val id = if (fill.id == 0L) nextId++ else fill.id
            fills.removeAll { it.id == id }
            fills.add(fill.copy(id = id))
            return id
        }

        private fun replaceWith(transform: (ChecklistFillEntity) -> ChecklistFillEntity) {
            val mapped = fills.map(transform)
            fills.clear()
            fills.addAll(mapped)
        }

        override suspend fun deleteById(id: Long) {
            fills.removeAll { it.id == id }
        }

        override suspend fun deleteByChecklistId(checklistId: Long) {
            fills.removeAll { it.checklistId == checklistId }
        }

        // ── Unused stubs ──
        override fun observeFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFillEntity>> = flowOf(emptyList())
        override fun observeDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFillEntity?> = flowOf(null)
        override suspend fun getDefaultFillByChecklistId(checklistId: Long): ChecklistFillEntity? = null
        override fun observeAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFillEntity>> = flowOf(emptyList())
        override suspend fun getById(id: Long): ChecklistFillEntity? = fills.firstOrNull { it.id == id }
        override suspend fun getCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun getTotalAdditionalFillCount(): Int = 0
        override suspend fun getAllDefaultFills(): List<ChecklistFillEntity> = emptyList()
        override suspend fun getAllFillsByChecklistId(checklistId: Long): List<ChecklistFillEntity> =
            fills.filter { it.checklistId == checklistId }
        override suspend fun updateSyncStatus(id: Long, status: Int) {}
    }

    private class FakeGate : InitialUploadGate {
        val doneUids = mutableSetOf<String>()
        override suspend fun isInitialUploadDone(uid: String) = doneUids.contains(uid)
        override suspend fun markInitialUploadDone(uid: String) {
            doneUids.add(uid)
        }
    }

    private class FakeAuth : GoogleAuthRepository {
        private val state = MutableStateFlow<GoogleAuthState>(GoogleAuthState.NotAuthenticated)
        override val authState: StateFlow<GoogleAuthState> = state

        /** Overrides currentUser for direct method calls without driving the pipeline. */
        var currentUserOverride: GoogleUser? = null
        override val currentUser: GoogleUser?
            get() = currentUserOverride ?: (state.value as? GoogleAuthState.Authenticated)?.user

        fun emitAuthenticated(user: GoogleUser) {
            currentUserOverride = user
            state.value = GoogleAuthState.Authenticated(user)
        }

        fun emitNotAuthenticated() {
            currentUserOverride = null
            state.value = GoogleAuthState.NotAuthenticated
        }

        override suspend fun signInWithGoogle(): Result<GoogleUser> = Result.failure(NotImplementedError())
        override suspend fun signOut() {}
        override suspend fun getIdToken(): String? = null
        override suspend fun restoreSession() {}
    }

    private class FakeFirestore : FirestoreSyncDataSource {
        var fetchResult: AppResult<List<ChecklistSyncData>> = AppResult.Success(emptyList())
        var uploadBatchResult: AppResult<Unit> = AppResult.Success(Unit)
        var initialUploadCallCount = 0
        val uploaded = mutableListOf<ChecklistSyncData>()

        override fun observeChecklistIds(userId: String): Flow<AppResult<List<String>>> = emptyFlow()
        override fun observeChecklist(userId: String, cloudId: String): Flow<AppResult<ChecklistSyncData>> = emptyFlow()

        override suspend fun uploadChecklist(userId: String, data: ChecklistSyncData): AppResult<Unit> {
            uploaded.add(data)
            return AppResult.Success(Unit)
        }

        override suspend fun deleteChecklist(userId: String, cloudId: String): AppResult<Unit> {
            uploaded.removeAll { it.cloudId == cloudId }
            return AppResult.Success(Unit)
        }

        override suspend fun uploadBatch(userId: String, checklists: List<ChecklistSyncData>): AppResult<Unit> {
            initialUploadCallCount++
            if (uploadBatchResult is AppResult.Success) uploaded.addAll(checklists)
            return uploadBatchResult
        }

        override suspend fun fetchAllChecklists(userId: String): AppResult<List<ChecklistSyncData>> = fetchResult
    }
}

/** Minimal no-op logger for tests. */
private object NoopLogger : AppLogger {
    override fun debug(tag: String, message: String) {}
    override fun info(tag: String, message: String) {}
    override fun warning(tag: String, message: String) {}
    override fun error(tag: String, message: String, throwable: Throwable?) {}
}
