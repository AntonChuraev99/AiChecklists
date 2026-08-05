package com.antonchuraev.homesearchchecklist.feature.checklist.data.sync

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleUser
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentCloudStoragePort
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistEntity
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistRow
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillEntity
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
            attachmentCloudStorage = attachmentCloud,
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

    // ── Item-aware builders (for the PENDING_UPLOAD merge-guard tests) ──
    // Items are serialized exactly as production does (the same JSON + serializer the
    // SUT uses), so the round-trip through ChecklistSyncData.toDomain() is faithful.

    private val itemsJson = Json { ignoreUnknownKeys = true }

    /** Template items with the given texts (stable ids derived from text so order is comparable). */
    private fun items(vararg texts: String): List<ChecklistItem> =
        texts.map { ChecklistItem(text = it).withId("id-$it") }

    /** Fill items with the given texts (stable ids derived from text). */
    private fun fillItems(vararg texts: String): List<ChecklistFillItem> =
        texts.map { ChecklistFillItem(text = it, checked = false) }

    private fun remoteWithItems(
        cloudId: String,
        name: String,
        updatedAt: Long,
        items: List<ChecklistItem>,
    ) = ChecklistSyncData(
        cloudId = cloudId,
        name = name,
        itemsJson = itemsJson.encodeToString(ListSerializer(ChecklistItem.serializer()), items),
        updatedAt = updatedAt,
        fills = emptyList(),
    )

    private fun remoteFillWithItems(
        cloudId: String,
        updatedAt: Long,
        items: List<ChecklistFillItem>,
    ) = FillSyncData(
        cloudId = cloudId,
        name = "",
        itemsJson = itemsJson.encodeToString(ListSerializer(ChecklistFillItem.serializer()), items),
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

    /** A system-Inbox row as `ensureInbox()` leaves it, already pushed (SYNCED) by the first sync. */
    private fun localInbox(
        id: Long,
        cloudId: String,
        items: List<ChecklistItem> = emptyList(),
        updatedAt: Long = 100L,
    ) = localSynced(id, cloudId, "Inbox", updatedAt).copy(isInbox = true, items = items)

    /** Cloud twin of [localInbox] — an Inbox document as another device wrote it. */
    private fun remoteInbox(
        cloudId: String,
        updatedAt: Long = 200L,
        items: List<ChecklistItem> = emptyList(),
        fills: List<FillSyncData> = emptyList(),
    ) = ChecklistSyncData(
        cloudId = cloudId,
        name = "Inbox",
        itemsJson = itemsJson.encodeToString(ListSerializer(ChecklistItem.serializer()), items),
        updatedAt = updatedAt,
        isInbox = true,
        fills = fills,
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

    // ─── Tests: isInbox is write-once locally, NOT last-write-wins ───────
    //
    // The LWW branch replaces the whole local row from the remote snapshot. A document written by a
    // device on an older build (or by the MCP worker) carries no `isInbox`; it decodes to false, and
    // a plain overwrite would silently demote the system Inbox into an ordinary checklist that then
    // pops up in the Projects list and eats a free-tier slot. The guard is a one-line `||` deep
    // inside mergeRemoteChecklist — exactly the kind of edit that gets dropped in a rebase.

    @Test
    fun mergeRemote_newerRemoteWithoutIsInbox_doesNotDemoteLocalInbox() = runTest {
        dao.checklists.add(localSynced(1L, "c1", "Inbox", updatedAt = 100L).copy(isInbox = true))
        // remote() leaves isInbox at its default false — an old-build / MCP write.
        firestore.fetchResult = AppResult.Success(listOf(remote("c1", "Inbox", updatedAt = 200L)))

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        assertTrue(
            dao.checklists.single { it.cloudId == "c1" }.isInbox,
            "a remote false must never clear a local isInbox = true — the Inbox would become a project",
        )
    }

    @Test
    fun mergeRemote_newerRemoteWithIsInbox_promotesLocalRow() = runTest {
        // Second device: the Inbox arrives from the cloud on a row that is already local (created
        // before this device knew about the flag). It must become the Inbox, not stay a project —
        // otherwise ensureInbox() creates a SECOND one and the user ends up with two.
        dao.checklists.add(localSynced(1L, "c1", "Inbox", updatedAt = 100L))
        firestore.fetchResult = AppResult.Success(
            listOf(remote("c1", "Inbox", updatedAt = 200L).copy(isInbox = true)),
        )

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        assertTrue(dao.checklists.single { it.cloudId == "c1" }.isInbox)
    }

    @Test
    fun mergeRemote_insertsNewInboxWithFlagPreserved() = runTest {
        // NEW branch (toInsertEntity): a fresh pull on a second device must carry the flag, or that
        // device shows the Inbox in its Projects list and creates its own duplicate.
        firestore.fetchResult = AppResult.Success(
            listOf(remote("c1", "Inbox", updatedAt = 50L).copy(isInbox = true)),
        )

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        assertTrue(dao.checklists.single { it.cloudId == "c1" }.isInbox)
    }

    // ─── Tests: the system Inbox stays SINGLE across the create/pull race ──
    //
    // ensureInbox() dedupes against the LOCAL database only. A cold start that roots at the Inbox tab
    // therefore mints a brand-new Inbox row + cloudId before the first pull can deliver the account's
    // own Inbox document (reinstall, or a second device). The two never match by cloudId, and the
    // blind insert left BOTH rows flagged: getInbox()/observeInbox() return one of them and the other
    // drops out of the Inbox tab, Projects, the pickers, the widget and MCP with no way back — every
    // task the user had captured, invisible. Every outcome below must keep one flag and lose nothing.
    //
    // The fetch sets carry the LOCAL Inbox's own document too, because that is the production shape:
    // onUserAuthenticated pushes before it pulls, so the freshly created row is already in the cloud.

    @Test
    fun mergeRemote_remoteInboxWithTasks_replacesThrowawayLocalInbox() = runTest {
        // The reinstall shape: the local Inbox was auto-created seconds ago and holds nothing, while
        // the cloud carries the account's real Inbox under a different cloudId.
        dao.checklists.add(localInbox(1L, "local-inbox"))
        fillDao.fills.add(fillEntity(id = 10L, checklistId = 1L, cloudId = "local-fill"))
        firestore.fetchResult = AppResult.Success(
            listOf(
                remoteInbox(
                    "cloud-inbox",
                    items = items("Call the dentist"),
                    fills = listOf(
                        remoteFillWithItems("cloud-fill", updatedAt = 200L, items = fillItems("Call the dentist")),
                    ),
                ),
                remoteInbox("local-inbox"),
            ),
        )

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        val inbox = dao.checklists.single { it.isInbox && !it.isDeleted }
        assertEquals("cloud-inbox", inbox.cloudId, "the account's own Inbox must take the tab")
        assertEquals(listOf("Call the dentist"), inbox.items.map { it.text })
        assertEquals(
            listOf("Call the dentist"),
            fillDao.fills.single { it.cloudId == "cloud-fill" }.items.map { it.text },
            "the cloud Inbox's fill items must come with it",
        )

        // The throwaway row is TOMBSTONED, not merely unflagged and not hard-deleted: only
        // PENDING_DELETE makes the next push remove the document initialUpload already wrote for it,
        // and a document left in the cloud would be re-delivered as a duplicate on every pull.
        val retired = dao.checklists.single { it.cloudId == "local-inbox" }
        assertTrue(retired.isDeleted, "the empty local Inbox must be retired")
        assertEquals(SyncStatus.PENDING_DELETE.value, retired.syncStatus)
        assertTrue(fillDao.fills.none { it.checklistId == 1L }, "its empty fills go with it")
    }

    @Test
    fun mergeRemote_bothInboxesHoldTasks_incomingLandsAsAVisibleProject() = runTest {
        // Both devices captured before either could sync. Nothing may be deleted here, so the loser
        // becomes an ordinary project the user can see and empty — never a second invisible Inbox.
        dao.checklists.add(localInbox(1L, "local-inbox", items = items("captured here")))
        firestore.fetchResult = AppResult.Success(
            listOf(
                remoteInbox("cloud-inbox", items = items("captured there")),
                remoteInbox("local-inbox", items = items("captured here")),
            ),
        )

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        val inbox = dao.checklists.single { it.isInbox && !it.isDeleted }
        assertEquals("local-inbox", inbox.cloudId, "an Inbox holding tasks is never retired")
        assertEquals(listOf("captured here"), inbox.items.map { it.text })

        val landed = dao.checklists.single { it.cloudId == "cloud-inbox" }
        assertFalse(landed.isInbox, "the loser must be an ordinary project, not a second Inbox")
        assertFalse(landed.isDeleted)
        assertEquals(
            listOf("captured there"),
            landed.items.map { it.text },
            "no task may be dropped — the row is demoted, its content untouched",
        )
    }

    @Test
    fun mergeRemote_throwawayDuplicateInbox_isNotMaterialised() = runTest {
        // The common second-device state: both auto-created Inboxes are still empty. Materialising
        // the other one would put an empty "Inbox" in Projects and eat a free-tier checklist slot,
        // and it carries nothing to lose. It self-heals: once it gains a task it merges as usual.
        dao.checklists.add(localInbox(1L, "local-inbox"))
        firestore.fetchResult = AppResult.Success(
            listOf(remoteInbox("local-inbox"), remoteInbox("other-inbox")),
        )

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        assertEquals(1, dao.checklists.size, "an empty duplicate Inbox must not become a row")
        assertEquals("local-inbox", dao.checklists.single().cloudId)
        assertTrue(dao.checklists.single().isInbox)
    }

    @Test
    fun mergeRemote_ordinaryChecklistStillInsertsWhileAnInboxExists() = runTest {
        // The guard must be invisible to every non-Inbox document: same insert as before.
        dao.checklists.add(localInbox(1L, "local-inbox"))
        firestore.fetchResult = AppResult.Success(
            listOf(
                remoteInbox("local-inbox"),
                remoteWithItems("c1", "Groceries", updatedAt = 200L, items = items("Milk")),
            ),
        )

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        val inserted = dao.checklists.single { it.cloudId == "c1" }
        assertEquals("Groceries", inserted.name)
        assertFalse(inserted.isInbox)
        assertEquals(listOf("Milk"), inserted.items.map { it.text })
        assertTrue(dao.checklists.single { it.cloudId == "local-inbox" }.isInbox, "the Inbox is untouched")
    }

    @Test
    fun mergeRemote_duplicateInboxResolution_isIdempotentAcrossPulls() = runTest {
        // The real-time listener re-pulls the same snapshot on every cloud change, so the resolution
        // must converge, not oscillate: no new row, no re-retirement, no flag ping-pong.
        dao.checklists.add(localInbox(1L, "local-inbox"))
        firestore.fetchResult = AppResult.Success(
            listOf(
                remoteInbox("cloud-inbox", items = items("Call the dentist")),
                remoteInbox("local-inbox"),
            ),
        )

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()
        repo.pullAndMerge()

        assertEquals(
            2,
            dao.checklists.size,
            "the adopted Inbox plus the retired row's tombstone — a repeat pull must add nothing",
        )
        val inbox = dao.checklists.single { it.isInbox && !it.isDeleted }
        assertEquals("cloud-inbox", inbox.cloudId)
        assertEquals(listOf("Call the dentist"), inbox.items.map { it.text })
        assertTrue(dao.checklists.single { it.cloudId == "local-inbox" }.isDeleted)
    }

    @Test
    fun mergeRemote_newerRemoteClaimingInboxOnAnotherRow_doesNotCreateASecondInbox() = runTest {
        // The UPDATE branch of the same rule: a document that claims the flag while another local row
        // already holds it (an older build's write, or a checklist promoted on a device whose own
        // Inbox differs) must not turn its row into a second Inbox.
        dao.checklists.add(localInbox(1L, "local-inbox"))
        dao.checklists.add(localSynced(2L, "c2", "Groceries", updatedAt = 100L))
        firestore.fetchResult = AppResult.Success(
            listOf(
                remoteInbox("local-inbox"),
                remote("c2", "Groceries", updatedAt = 200L).copy(isInbox = true),
            ),
        )

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        assertEquals(
            "local-inbox",
            dao.checklists.single { it.isInbox && !it.isDeleted }.cloudId,
            "exactly one row may carry the flag, and it stays the one that already had it",
        )
        assertFalse(dao.checklists.single { it.cloudId == "c2" }.isInbox)
    }

    // ─── Tests: PENDING_UPLOAD merge guard (the reorder-clobber race) ────
    //
    // Root cause of "reorder reverts after leaving the screen": finalizeReorder persisted the
    // new order PENDING_UPLOAD, but a sync push triggered between its two old writes uploaded an
    // intermediate STALE snapshot and stamped it with a fresh updatedAt. The real-time listener
    // echoed that stale-but-newer snapshot back, and the merge — keyed only on LWW — UPDATEd the
    // local row, overwriting the unsynced reorder with the old order. The guard: a PENDING_UPLOAD
    // local row keeps its unsynced edits until the next push carries them up; a newer remote may
    // only win over a SYNCED local row (a genuine cross-device edit).

    @Test
    fun mergeRemote_doesNotOverwritePendingUploadWithNewerStaleRemote() = runTest {
        // Local: the just-reordered order [B, A], still PENDING_UPLOAD (not yet pushed).
        // Remote: the STALE pre-reorder order [A, B], but carrying a NEWER updatedAt — exactly the
        // echo a racing push produced (stamped now=...094 onto the stale half, local=...058).
        val localOrder = items("B", "A")
        dao.checklists.add(
            localPendingUpload(1L, "c1", "List", updatedAt = 58L).copy(items = localOrder),
        )
        firestore.fetchResult = AppResult.Success(
            listOf(remoteWithItems("c1", "List", updatedAt = 94L, items = items("A", "B"))),
        )

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        val merged = dao.checklists.single { it.cloudId == "c1" }
        assertEquals(
            listOf("B", "A"),
            merged.items.map { it.text },
            "a PENDING_UPLOAD local reorder must NOT be overwritten by a newer-but-stale remote echo",
        )
        assertEquals(
            SyncStatus.PENDING_UPLOAD.value,
            merged.syncStatus,
            "the local row must remain PENDING_UPLOAD so the next push carries the reorder up",
        )
    }

    @Test
    fun mergeRemote_doesNotOverwritePendingUploadWithEqualTimestampStaleRemote() = runTest {
        // Same race, the boundary case: remote updatedAt EQUAL to local. LWW ('>') would SKIP this
        // anyway, but the explicit PENDING_UPLOAD guard makes the intent unambiguous and durable.
        dao.checklists.add(
            localPendingUpload(1L, "c1", "List", updatedAt = 70L).copy(items = items("B", "A")),
        )
        firestore.fetchResult = AppResult.Success(
            listOf(remoteWithItems("c1", "List", updatedAt = 70L, items = items("A", "B"))),
        )

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        assertEquals(
            listOf("B", "A"),
            dao.checklists.single { it.cloudId == "c1" }.items.map { it.text },
            "equal-timestamp stale remote must not overwrite a pending local reorder either",
        )
    }

    @Test
    fun mergeRemote_stillAppliesNewerRemoteWhenLocalSynced() = runTest {
        // The guard must NOT break genuine cross-device LWW: when the local row is SYNCED, a newer
        // remote from another device still wins (the reorder there is the latest truth).
        dao.checklists.add(
            localSynced(1L, "c1", "List", updatedAt = 58L).copy(items = items("A", "B")),
        )
        firestore.fetchResult = AppResult.Success(
            listOf(remoteWithItems("c1", "List", updatedAt = 94L, items = items("B", "A"))),
        )

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        assertEquals(
            listOf("B", "A"),
            dao.checklists.single { it.cloudId == "c1" }.items.map { it.text },
            "a newer remote from another device must still win over a SYNCED local row",
        )
    }

    @Test
    fun mergeFills_doesNotOverwritePendingUploadFillWithNewerRemote() = runTest {
        // Per-fill analogue: the checklist is SYNCED and gets a newer remote (UPDATE branch), but
        // one of its fills was edited locally (PENDING_UPLOAD) and not yet pushed. That fill must
        // not be overwritten by the remote fill, while a SYNCED sibling fill still takes the
        // newer remote version.
        dao.checklists.add(localSynced(1L, "c1", "List", updatedAt = 100L))
        fillDao.fills.addAll(
            listOf(
                fillEntity(id = 10L, checklistId = 1L, cloudId = "f1", updatedAt = 100L)
                    .copy(items = fillItems("synced-old")),
                fillEntity(
                    id = 11L,
                    checklistId = 1L,
                    cloudId = "f2",
                    syncStatus = SyncStatus.PENDING_UPLOAD.value,
                    updatedAt = 150L,
                ).copy(items = fillItems("local-edit")),
            ),
        )
        firestore.fetchResult = AppResult.Success(
            listOf(
                remote(
                    "c1",
                    "List",
                    updatedAt = 200L,
                    fills = listOf(
                        remoteFillWithItems("f1", updatedAt = 200L, items = fillItems("synced-new")),
                        remoteFillWithItems("f2", updatedAt = 200L, items = fillItems("stale-remote")),
                    ),
                ),
            ),
        )

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pullAndMerge()

        assertEquals(
            listOf("synced-new"),
            fillDao.fills.single { it.cloudId == "f1" }.items.map { it.text },
            "a SYNCED fill must take the newer remote version",
        )
        assertEquals(
            listOf("local-edit"),
            fillDao.fills.single { it.cloudId == "f2" }.items.map { it.text },
            "a PENDING_UPLOAD fill must keep its unsynced local edit, not the stale remote",
        )
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

    @Test
    fun initialUpload_legacyChecklistWithoutCloudId_uploadsWithResolvedCloudId() = runTest {
        // The legacy-user shape produced by MIGRATION_14_15: it added `cloudId TEXT DEFAULT NULL`
        // and `syncStatus INTEGER NOT NULL DEFAULT 0` — so every checklist that predates cloud sync
        // is (cloudId = null, syncStatus = SYNCED). SYNCED rows are excluded by getPendingSync()
        // (`WHERE syncStatus != 0`), so the backfill inside uploadChecklistEntity NEVER sees them;
        // initialUpload() is the only path that does — and it read the null cloudId straight through
        // `cloudId ?: ""`, fabricating a blank identity. Firestore then rejects the whole batch, the
        // one-time gate stays unmarked, and NOTHING of a legacy user's data ever reaches the cloud
        // (retried, and failing identically, on every app start).
        dao.checklists.add(
            ChecklistEntity(
                id = 1L,
                name = "Legacy",
                items = emptyList(),
                cloudId = null,
                userId = null,
                updatedAt = 100L,
                syncStatus = SyncStatus.SYNCED.value,
                isDeleted = false,
            ),
        )
        fillDao.fills.add(fillEntity(id = 10L, checklistId = 1L, cloudId = null))

        val repo = newRepo()
        auth.currentUserOverride = testUser

        val result = repo.initialUpload()

        assertTrue(result is AppResult.Success, "a legacy checklist must not fail the initial upload")

        val uploadedChecklist = firestore.uploaded.single()
        assertTrue(
            uploadedChecklist.cloudId.isNotBlank(),
            "the uploaded checklist must carry a real cloudId, never a fabricated blank identity",
        )
        assertTrue(
            uploadedChecklist.fills.single().cloudId.isNotBlank(),
            "the uploaded fill must carry a real cloudId — a blank one collides with every other " +
                "blank-id fill in mergeRemoteChecklist's getByCloudId lookup",
        )

        // The resolved identity must be PERSISTED, otherwise the next start backfills a DIFFERENT
        // cloudId and the same checklist is duplicated in the cloud.
        val persisted = dao.checklists.single()
        assertEquals(
            uploadedChecklist.cloudId,
            persisted.cloudId,
            "the backfilled cloudId must be persisted to Room and match what was uploaded",
        )
        assertEquals(
            uploadedChecklist.fills.single().cloudId,
            fillDao.fills.single().cloudId,
            "the backfilled fill cloudId must be persisted to Room and match what was uploaded",
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

    // ─── Tests: dirty fill under a SYNCED checklist (checked-items-don't-sync) ──

    @Test
    fun push_uploadsParentOfDirtyFillWhenChecklistIsSynced() = runTest {
        // Reproduces the production bug "checked items don't sync to web". Toggling a
        // checkbox marks ONLY the fill PENDING_UPLOAD; its parent checklist stays
        // SYNCED. The parent is therefore absent from checklistDao.getPendingSync(),
        // and the old fill loop only handled PENDING_DELETE — so the new checked state
        // never reached the cloud. The push must re-upload the parent (fills are
        // embedded in the parent's Firestore document) so the item state propagates.
        dao.checklists.add(localSynced(1L, "c1", "Groceries", updatedAt = 100L))
        fillDao.fills.add(
            fillEntity(
                id = 10L,
                checklistId = 1L,
                cloudId = "f1",
                syncStatus = SyncStatus.PENDING_UPLOAD.value,
                updatedAt = 150L,
            ),
        )
        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pushPendingChanges()

        val uploadedParent = firestore.uploaded.singleOrNull { it.cloudId == "c1" }
        assertNotNull(
            uploadedParent,
            "parent checklist of a dirty fill must be re-uploaded so item state reaches the cloud",
        )
        assertTrue(
            uploadedParent.fills.any { it.cloudId == "f1" },
            "the re-uploaded parent document must carry the dirty fill's content",
        )
        assertTrue(
            uploadedParent.updatedAt > 100L,
            "parent updatedAt must be bumped above the stale local value, else other " +
                "devices' Last-Write-Wins merge SKIPs the change and fills never propagate",
        )
        assertEquals(
            SyncStatus.SYNCED.value,
            fillDao.fills.single().syncStatus,
            "the dirty fill must be cleared to SYNCED once its parent is uploaded",
        )
    }

    @Test
    fun push_doesNotDoubleUploadWhenChecklistAndItsFillBothDirty() = runTest {
        // When the checklist itself is PENDING_UPLOAD its fills are already uploaded as
        // part of it. The dirty-fill pass must not upload the same parent a second time.
        dao.checklists.add(localPendingUpload(1L, "c1", "List", updatedAt = 100L))
        fillDao.fills.add(
            fillEntity(
                id = 10L,
                checklistId = 1L,
                cloudId = "f1",
                syncStatus = SyncStatus.PENDING_UPLOAD.value,
                updatedAt = 150L,
            ),
        )
        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pushPendingChanges()

        assertEquals(
            1,
            firestore.uploaded.count { it.cloudId == "c1" },
            "parent checklist must be uploaded exactly once",
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

    // ─── Tests: attachment-bytes upload on push (cross-device sync, Phase 3) ──
    //
    // When a fill carries an attachment whose bytes have not yet reached Firebase Storage
    // (storagePath == null && path is a real local handle), pushing the parent checklist must
    // upload those bytes and stamp the attachment's storagePath — the cross-device anchor that
    // lets another device of the same user lazily download the file. A failed byte-upload must
    // leave storagePath null (so it retries next push) WITHOUT failing the whole push, and the
    // Firestore document must still be uploaded so the rest of the change syncs.

    @Test
    fun pushPendingChanges_fillHasAttachmentWithoutStoragePath_uploadsBytesAndStampsStoragePath() = runTest {
        // Parent PENDING_UPLOAD so uploadChecklistEntity runs; one active fill holds one item
        // with an un-uploaded attachment (storagePath = null, local path present).
        dao.checklists.add(localPendingUpload(1L, "c1", "List"))
        val att = attachment(id = "att1", path = "/local/photo.jpg", fileName = "photo.jpg", storagePath = null)
        val fill = fillEntityWithItem(id = 10L, checklistId = 1L, cloudId = "f1", attachment = att)
        val itemId = fill.items.single().id
        fillDao.fills.add(fill)

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pushPendingChanges()

        val expectedKey = "users/$uid/attachments/10/$itemId/att1.jpg"
        assertEquals(
            listOf("/local/photo.jpg" to expectedKey),
            attachmentCloud.uploadCalls,
            "the un-uploaded attachment's bytes must be uploaded exactly once, to the AttachmentCloudPaths key",
        )

        // Persisted fill in Room carries the stamped storagePath.
        val persisted = fillDao.fills.single { it.cloudId == "f1" }
        assertEquals(
            expectedKey,
            persisted.items.single().attachments.single().storagePath,
            "the persisted fill's attachment must be stamped with the cloud key",
        )

        // The uploaded Firestore document's embedded fill itemsJson must carry the storagePath too,
        // so the cross-device anchor actually reaches the cloud.
        val uploadedFill = firestore.uploaded.single { it.cloudId == "c1" }.fills.single { it.cloudId == "f1" }
        val uploadedItems = itemsJson.decodeFromString(
            ListSerializer(ChecklistFillItem.serializer()),
            uploadedFill.itemsJson,
        )
        assertEquals(
            expectedKey,
            uploadedItems.single().attachments.single().storagePath,
            "the uploaded Firestore document must embed the attachment's storagePath",
        )
    }

    @Test
    fun pushPendingChanges_attachmentAlreadyHasStoragePath_skipsUpload() = runTest {
        // Attachment already uploaded on a previous push (storagePath set) → no re-upload.
        dao.checklists.add(localPendingUpload(1L, "c1", "List"))
        val att = attachment(
            id = "att1",
            path = "/local/photo.jpg",
            fileName = "photo.jpg",
            storagePath = "users/$uid/attachments/10/already/att1.jpg",
        )
        fillDao.fills.add(fillEntityWithItem(id = 10L, checklistId = 1L, cloudId = "f1", attachment = att))

        val repo = newRepo()
        auth.currentUserOverride = testUser

        repo.pushPendingChanges()

        assertTrue(
            attachmentCloud.uploadCalls.isEmpty(),
            "an attachment that already has a storagePath must NOT be re-uploaded",
        )
    }

    @Test
    fun pushPendingChanges_uploadFails_keepsStoragePathNull_andDoesNotFailPush() = runTest {
        // The byte-upload to Storage fails. The attachment must stay storagePath = null (retry next
        // push), push must still succeed, and the Firestore document must still be uploaded.
        dao.checklists.add(localPendingUpload(1L, "c1", "List"))
        val att = attachment(id = "att1", path = "/local/photo.jpg", fileName = "photo.jpg", storagePath = null)
        fillDao.fills.add(fillEntityWithItem(id = 10L, checklistId = 1L, cloudId = "f1", attachment = att))
        attachmentCloud.uploadResult = AppResult.Error(Exception("storage upload failed"))

        val repo = newRepo()
        auth.currentUserOverride = testUser

        val result = repo.pushPendingChanges()

        assertTrue(result is AppResult.Success, "a failed attachment byte-upload must not fail the whole push")
        assertEquals(1, attachmentCloud.uploadCalls.size, "the upload was attempted once")
        assertNull(
            fillDao.fills.single { it.cloudId == "f1" }.items.single().attachments.single().storagePath,
            "a failed upload must leave storagePath null so it retries on the next push",
        )
        assertTrue(
            firestore.uploaded.any { it.cloudId == "c1" },
            "the checklist document must still be uploaded even when an attachment byte-upload fails",
        )
    }

    // ─── Fakes ───────────────────────────────────────────────────────────

    private val dao = FakeChecklistDao()
    private val fillDao = FakeFillDao()
    private val firestore = FakeFirestore()
    private val gate = FakeGate()
    private val auth = FakeAuth()
    private val attachmentCloud = FakeAttachmentCloudStorage()

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

    /** Attachment fixture with controllable id/path/fileName/storagePath for the upload tests. */
    private fun attachment(
        id: String,
        path: String,
        fileName: String,
        storagePath: String?,
    ) = Attachment(
        id = id,
        path = path,
        fileName = fileName,
        mimeType = "image/jpeg",
        sizeBytes = 1_024L,
        createdAt = 0L,
        storagePath = storagePath,
    )

    /**
     * A fill carrying a single item holding one [attachment]. [ChecklistFillItem] auto-generates
     * its id (no setter), so the caller reads the generated id off the returned entity to build the
     * expected cloud key — the SUT uses that same id, so the key assertion stays faithful.
     */
    private fun fillEntityWithItem(
        id: Long,
        checklistId: Long,
        cloudId: String?,
        attachment: Attachment,
        syncStatus: Int = SyncStatus.SYNCED.value,
        updatedAt: Long = 100L,
    ): ChecklistFillEntity {
        val item = ChecklistFillItem(text = "item", checked = false)
            .withAttachmentAdded(attachment)
        return fillEntity(id, checklistId, cloudId, syncStatus, updatedAt).copy(items = listOf(item))
    }

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

        override suspend fun touchForSync(id: Long, updatedAt: Long) {
            replaceWith {
                if (it.id == id && it.syncStatus != SyncStatus.PENDING_DELETE.value) {
                    it.copy(syncStatus = SyncStatus.PENDING_UPLOAD.value, updatedAt = updatedAt)
                } else {
                    it
                }
            }
        }

        override suspend fun getInbox(): ChecklistEntity? =
            checklists.firstOrNull { it.isInbox && !it.isDeleted }

        override suspend fun softDelete(id: Long, updatedAt: Long) {
            replaceWith {
                if (it.id == id) {
                    it.copy(
                        isDeleted = true,
                        syncStatus = SyncStatus.PENDING_DELETE.value,
                        updatedAt = updatedAt,
                    )
                } else {
                    it
                }
            }
        }

        // ── Unused stubs ──
        override fun observeChecklists(): Flow<List<ChecklistEntity>> = flowOf(emptyList())
        override fun observeChecklistRows(): Flow<List<ChecklistRow>> = flowOf(emptyList())
        override fun observeProjectRows(): Flow<List<ChecklistRow>> = flowOf(emptyList())
        override fun observeProjects(): Flow<List<ChecklistEntity>> = flowOf(emptyList())
        override suspend fun getAllProjectsOrderedByPosition(): List<ChecklistEntity> =
            checklists.filter { !it.isDeleted && !it.isInbox }.sortedBy { it.position }
        override suspend fun getById(id: Long): ChecklistEntity? = checklists.firstOrNull { it.id == id }
        override fun observeChecklistById(id: Long): Flow<ChecklistEntity?> = flowOf(null)
        override suspend fun updateSyncStatus(id: Long, status: Int) {}
        override suspend fun setSeparateCompleted(id: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(id: Long, value: Boolean) {}
        override suspend fun setFoldersEnabled(id: Long, value: Boolean) {}
        override suspend fun updatePosition(id: Long, position: Int) {}
        override suspend fun incrementAllPositions() {}
        override suspend fun getAllOrderedByPosition(): List<ChecklistEntity> = emptyList()
        override suspend fun updateReminder(id: Long, reminderAt: Long?) {}
        override suspend fun updateReminderFullScreen(id: Long, fullScreen: Boolean) {}
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

    /**
     * In-memory cloud storage. Records every [upload] call as a (localPath, storagePath) pair so
     * tests can assert what bytes were transferred and to which cloud key. [uploadResult] defaults
     * to Success (existing tests have no attachments, so the path is never hit); the attachment
     * tests flip it to Error to exercise the failed-upload branch.
     */
    private class FakeAttachmentCloudStorage : AttachmentCloudStoragePort {
        val uploadCalls = mutableListOf<Pair<String, String>>()
        var uploadResult: AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun upload(localPath: String, storagePath: String): AppResult<Unit> {
            uploadCalls.add(localPath to storagePath)
            return uploadResult
        }

        override suspend fun download(storagePath: String, localPath: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun delete(storagePath: String): AppResult<Unit> = AppResult.Success(Unit)
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

        /**
         * Mirrors the real Firestore SDK's document-path invariant, which this fake would
         * otherwise silently swallow: a cloudId is a path SEGMENT, and every backend rejects a
         * blank one. On Android `checklistRef(uid, "")` collapses to `users/{uid}/checklists`
         * (3 segments = a collection, not a document) and the SDK throws IllegalArgumentException
         * — taking the whole batch, and therefore the user's entire initial sync, down with it.
         *
         * Without this guard a fake that happily stores `cloudId = ""` would make a test go green
         * exactly where production is red, "verifying" a bug into existence.
         */
        private fun requireUploadableIdentity(data: ChecklistSyncData) {
            require(data.cloudId.isNotBlank()) {
                "blank cloudId for checklist '${data.name}' — Firestore rejects an empty path segment"
            }
            data.fills.forEach { fill ->
                require(fill.cloudId.isNotBlank()) {
                    "blank cloudId for a fill of '${data.name}' — an empty fill identity collides " +
                        "with every other blank-id fill on merge"
                }
            }
        }

        override fun observeChecklistIds(userId: String): Flow<AppResult<List<String>>> = emptyFlow()
        override fun observeChecklist(userId: String, cloudId: String): Flow<AppResult<ChecklistSyncData>> = emptyFlow()
        override fun observeUserDoc(userId: String): Flow<AppResult<UserDocSyncData?>> = emptyFlow()
        override suspend fun findUserIdByGoogleUid(googleUid: String): AppResult<String?> = AppResult.Success(null)

        override suspend fun uploadChecklist(userId: String, data: ChecklistSyncData): AppResult<Unit> {
            requireUploadableIdentity(data)
            uploaded.add(data)
            return AppResult.Success(Unit)
        }

        override suspend fun deleteChecklist(userId: String, cloudId: String): AppResult<Unit> {
            uploaded.removeAll { it.cloudId == cloudId }
            return AppResult.Success(Unit)
        }

        override suspend fun uploadBatch(userId: String, checklists: List<ChecklistSyncData>): AppResult<Unit> {
            initialUploadCallCount++
            checklists.forEach { requireUploadableIdentity(it) }
            if (uploadBatchResult is AppResult.Success) uploaded.addAll(checklists)
            return uploadBatchResult
        }

        override suspend fun fetchAllChecklists(userId: String): AppResult<List<ChecklistSyncData>> = fetchResult

        override suspend fun fetchGalleryTemplate(slug: String): AppResult<GalleryTemplateSyncData?> =
            AppResult.Success(null)
    }
}

/** Minimal no-op logger for tests. */
private object NoopLogger : AppLogger {
    override fun debug(tag: String, message: String) {}
    override fun info(tag: String, message: String) {}
    override fun warning(tag: String, message: String) {}
    override fun error(tag: String, message: String, throwable: Throwable?) {}
}
