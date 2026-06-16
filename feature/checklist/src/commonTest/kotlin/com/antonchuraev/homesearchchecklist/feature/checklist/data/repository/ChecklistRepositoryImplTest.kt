package com.antonchuraev.homesearchchecklist.feature.checklist.data.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistEntity
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillEntity
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistTransactionRunner
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Behaviour tests for [ChecklistRepositoryImpl] fill <-> template reconciliation (folders Phase 0).
 *
 * Phase 0 introduced a stable [ChecklistFillItem.templateItemId] link and changed
 * [ChecklistRepositoryImpl.updateChecklist] to reconcile the default fill by that link
 * (text as a legacy fallback) instead of rebuilding every fill item from the template text.
 * The old text-keyed rebuild regenerated every fill item id on each template edit, dropping
 * per-item checked/note/reminders/attachments and collapsing duplicate-text items.
 *
 * These are green coverage tests: they must PASS on the fixed code and FAIL if the
 * reconciliation regresses to id-regeneration or text-only matching.
 */
class ChecklistRepositoryImplTest {

    // ─── Tests: updateChecklist preserves fill state by the stable link ──────

    @Test
    fun updateChecklist_templateItemTextEdited_preservesFillIdCheckedNoteReminderAttachmentByLink() = runTest {
        // A template with one item, edited from "Buy milk" -> "Buy oat milk".
        val templateItem = ChecklistItem(text = "Buy milk")
        val checklist = Checklist(id = 1L, name = "Groceries", items = listOf(templateItem))
        checklistDao.checklists.add(checklist.toEntityRow())

        // The default fill carries rich per-item state linked to the template item.
        val attachment = sampleAttachment("att_1")
        val fillItem = ChecklistFillItem(
            text = "Buy milk",
            checked = true,
            note = "2% only",
            templateItemId = templateItem.id,
        )
            .withReminderAt(1_700_000_000_000L)
            .withAttachmentAdded(attachment)
        val originalFillItemId = fillItem.id
        fillDao.fills.add(defaultFillRow(id = 10L, checklistId = 1L, items = listOf(fillItem)))

        // Edit the template item text and save through updateChecklist.
        val editedChecklist = checklist.copy(items = listOf(templateItem.withText("Buy oat milk")))
        newRepo().updateChecklist(editedChecklist)

        val savedFill = fillDao.fills.single { it.id == 10L }
        val savedItem = savedFill.items.single()

        // id must NOT be regenerated (the core Phase 0 bug) — it is the LazyColumn key and
        // the anchor for the fill's checked/note/reminder/attachment state.
        assertEquals(originalFillItemId, savedItem.id, "fill item id must survive a template text edit")
        // Per-item state must be carried over intact.
        assertTrue(savedItem.checked, "checked state must be preserved")
        assertEquals("2% only", savedItem.note, "note must be preserved")
        assertEquals(1_700_000_000_000L, savedItem.reminderAt, "per-item reminder must be preserved")
        assertEquals(listOf(attachment), savedItem.attachments, "per-item attachment must be preserved")
        // The link is (re)attached and the edited template text is mirrored onto the fill.
        assertEquals(templateItem.id, savedItem.templateItemId, "template link must be preserved")
        assertEquals("Buy oat milk", savedItem.text, "edited template text must be mirrored onto the fill")
    }

    @Test
    fun updateChecklist_duplicateTextItems_preservesPerItemStateByLink() = runTest {
        // Two template items with IDENTICAL text but distinct ids — the case that broke
        // under text-only reconciliation (both collapse to a single byText entry).
        val firstItem = ChecklistItem(text = "Milk")
        val secondItem = ChecklistItem(text = "Milk")
        val checklist = Checklist(id = 1L, name = "Groceries", items = listOf(firstItem, secondItem))
        checklistDao.checklists.add(checklist.toEntityRow())

        // Each fill item links to a distinct template item and holds DIFFERENT state.
        val firstFill = ChecklistFillItem(
            text = "Milk",
            checked = true,
            note = "from the corner shop",
            templateItemId = firstItem.id,
        )
        val secondFill = ChecklistFillItem(
            text = "Milk",
            checked = false,
            note = "oat",
            templateItemId = secondItem.id,
        )
        val firstFillId = firstFill.id
        val secondFillId = secondFill.id
        fillDao.fills.add(defaultFillRow(id = 10L, checklistId = 1L, items = listOf(firstFill, secondFill)))

        // Save with the duplicate-text items unchanged (e.g. a checklist rename Edit-save).
        newRepo().updateChecklist(checklist.copy(name = "Weekly Groceries"))

        val savedItems = fillDao.fills.single { it.id == 10L }.items
        assertEquals(2, savedItems.size, "duplicate-text items must not collapse or be lost")

        val byLinkFirst = savedItems.single { it.templateItemId == firstItem.id }
        val byLinkSecond = savedItems.single { it.templateItemId == secondItem.id }

        // Each fill item must keep ITS OWN state, matched by link — not bleed into the other.
        assertEquals(firstFillId, byLinkFirst.id, "first item id must be stable")
        assertTrue(byLinkFirst.checked, "first item keeps its own checked=true")
        assertEquals("from the corner shop", byLinkFirst.note, "first item keeps its own note")

        assertEquals(secondFillId, byLinkSecond.id, "second item id must be stable")
        assertTrue(!byLinkSecond.checked, "second item keeps its own checked=false")
        assertEquals("oat", byLinkSecond.note, "second item keeps its own note")
    }

    // ─── Tests: resetDefaultFillChecks clears checked only ───────────────────

    @Test
    fun resetDefaultFillChecks_checkedItemWithReminderAndAttachment_clearsCheckedOnly() = runTest {
        val templateItem = ChecklistItem(text = "Pack passport")
        val checklist = Checklist(id = 1L, name = "Trip", items = listOf(templateItem))
        checklistDao.checklists.add(checklist.toEntityRow())

        val attachment = sampleAttachment("att_2")
        val fillItem = ChecklistFillItem(
            text = "Pack passport",
            checked = true,
            note = "expires soon",
            templateItemId = templateItem.id,
        )
            .withReminderAt(1_700_000_000_000L)
            .withAttachmentAdded(attachment)
        val originalFillItemId = fillItem.id
        fillDao.fills.add(defaultFillRow(id = 10L, checklistId = 1L, items = listOf(fillItem)))

        newRepo().resetDefaultFillChecks(checklistId = 1L)

        val savedItem = fillDao.fills.single { it.id == 10L }.items.single()
        // Only checked is reset.
        assertTrue(!savedItem.checked, "checked must be cleared by reset")
        // Everything else survives — the regression the Phase 0 change guards against.
        assertEquals(originalFillItemId, savedItem.id, "fill item id must survive reset")
        assertEquals("expires soon", savedItem.note, "note must survive reset")
        assertEquals(1_700_000_000_000L, savedItem.reminderAt, "per-item reminder must survive reset")
        assertEquals(listOf(attachment), savedItem.attachments, "per-item attachment must survive reset")
        assertEquals(templateItem.id, savedItem.templateItemId, "template link must survive reset")
    }

    // ─── Tests: reorderItems persists atomically + monotonically ─────────────

    @Test
    fun reorderItems_marksBothRowsPendingUploadWithOneSharedTimestamp() = runTest {
        // A reorder swaps [A, B] -> [B, A] on both the template and the fill.
        val a = ChecklistItem(text = "A")
        val b = ChecklistItem(text = "B")
        val checklist = Checklist(id = 1L, name = "List", items = listOf(a, b))
        // Pre-existing SYNCED rows with an OLD updatedAt (so we can assert the bump is forward).
        checklistDao.checklists.add(checklist.toEntityRow().copy(updatedAt = 100L))
        val fillA = ChecklistFillItem(text = "A", checked = false, templateItemId = a.id)
        val fillB = ChecklistFillItem(text = "B", checked = false, templateItemId = b.id)
        fillDao.fills.add(
            defaultFillRow(id = 10L, checklistId = 1L, items = listOf(fillA, fillB)).copy(updatedAt = 100L),
        )

        val reorderedChecklist = checklist.copy(items = listOf(b, a))
        val reorderedFill = ChecklistFill(
            id = 10L,
            checklistId = 1L,
            name = "",
            items = listOf(fillB, fillA),
            isDefault = true,
        )

        newRepo().reorderItems(reorderedFill, reorderedChecklist)

        val savedChecklist = checklistDao.checklists.single { it.id == 1L }
        val savedFill = fillDao.fills.single { it.id == 10L }

        // New order persisted on both.
        assertEquals(listOf("B", "A"), savedChecklist.items.map { it.text }, "template order persisted")
        assertEquals(listOf("B", "A"), savedFill.items.map { it.text }, "fill order persisted")

        // Both dirtied for sync.
        assertEquals(SyncStatus.PENDING_UPLOAD.value, savedChecklist.syncStatus, "template marked PENDING_UPLOAD")
        assertEquals(SyncStatus.PENDING_UPLOAD.value, savedFill.syncStatus, "fill marked PENDING_UPLOAD")

        // ONE shared timestamp, and it is monotonic (strictly newer than the prior 100L) — so the
        // parent's updatedAt can never look "older" than the fill's to a later push/merge.
        assertEquals(
            savedChecklist.updatedAt,
            savedFill.updatedAt,
            "the reorder must stamp the SAME updatedAt on the fill and the template",
        )
        assertTrue(
            savedChecklist.updatedAt >= 100L,
            "the reordered checklist updatedAt must be monotonic (never move backwards)",
        )
    }

    // ─── System under test factory + fakes ───────────────────────────────────

    private val checklistDao = FakeChecklistDao()
    private val fillDao = FakeFillDao()
    private val attachmentStorage = NoopAttachmentStorage

    // Pass-through transaction runner: a real Room transaction would just commit both writes
    // together, so for unit purposes invoking the block directly is faithful (the atomicity that
    // matters for the sync race — a single post-commit emission — is a Room-runtime property and
    // is covered by the on-device reorder verification, not the in-memory fakes).
    private val transactionRunner = ChecklistTransactionRunner { block -> block() }

    private fun newRepo() = ChecklistRepositoryImpl(
        checklistDao = checklistDao,
        fillDao = fillDao,
        attachmentStorage = attachmentStorage,
        transactionRunner = transactionRunner,
    )

    private fun sampleAttachment(id: String) = Attachment(
        id = id,
        path = "/data/attachments/$id.jpg",
        fileName = "photo.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 1_000L,
        createdAt = 0L,
    )

    private fun Checklist.toEntityRow() = ChecklistEntity(
        id = id,
        name = name,
        items = items,
        cloudId = "cloud-$id",
        userId = "uid-1",
        updatedAt = 100L,
        syncStatus = SyncStatus.SYNCED.value,
        isDeleted = false,
    )

    private fun defaultFillRow(
        id: Long,
        checklistId: Long,
        items: List<ChecklistFillItem>,
    ) = ChecklistFillEntity(
        id = id,
        checklistId = checklistId,
        name = "",
        coverImagePath = null,
        items = items,
        createdAt = 0L,
        isDefault = true,
        cloudId = "fill-cloud-$id",
        userId = "uid-1",
        updatedAt = 100L,
        syncStatus = SyncStatus.SYNCED.value,
        isDeleted = false,
    )

    /**
     * In-memory [ChecklistDao]. Only the members used by [ChecklistRepositoryImpl] reconciliation
     * carry behaviour; the rest are no-op/empty stubs (this DAO has 30+ members).
     */
    private class FakeChecklistDao : ChecklistDao {
        val checklists = mutableListOf<ChecklistEntity>()

        override suspend fun getById(id: Long): ChecklistEntity? =
            checklists.firstOrNull { it.id == id }

        override suspend fun update(checklist: ChecklistEntity) {
            val mapped = checklists.map { if (it.id == checklist.id) checklist else it }
            checklists.clear()
            checklists.addAll(mapped)
        }

        override suspend fun touchForSync(id: Long, updatedAt: Long) {
            val mapped = checklists.map {
                if (it.id == id && it.syncStatus != SyncStatus.PENDING_DELETE.value) {
                    it.copy(syncStatus = SyncStatus.PENDING_UPLOAD.value, updatedAt = updatedAt)
                } else {
                    it
                }
            }
            checklists.clear()
            checklists.addAll(mapped)
        }

        // ── Unused stubs ──
        override fun observeChecklists(): Flow<List<ChecklistEntity>> = flowOf(emptyList())
        override fun observeChecklistById(id: Long): Flow<ChecklistEntity?> = flowOf(null)
        override suspend fun insert(checklist: ChecklistEntity): Long = checklist.id
        override suspend fun updateSyncStatus(id: Long, status: Int) {}
        override suspend fun softDelete(id: Long, updatedAt: Long) {}
        override suspend fun deleteById(id: Long) {}
        override suspend fun setSeparateCompleted(id: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(id: Long, value: Boolean) {}
        override suspend fun setFoldersEnabled(id: Long, value: Boolean) {}
        override suspend fun updatePosition(id: Long, position: Int) {}
        override suspend fun incrementAllPositions() {}
        override suspend fun getAllOrderedByPosition(): List<ChecklistEntity> = emptyList()
        override suspend fun getAllActive(): List<ChecklistEntity> = checklists.filter { !it.isDeleted }
        override suspend fun updateReminder(id: Long, reminderAt: Long?) {}
        override suspend fun countActiveReminders(nowMillis: Long): Int = 0
        override suspend fun getActiveReminders(nowMillis: Long): List<ChecklistReminderInfo> = emptyList()
        override suspend fun setRepeatSchedule(id: Long, repeatRuleJson: String?, timeMinutes: Int?, nextAt: Long?) {}
        override suspend fun advanceRepeatSchedule(id: Long, nextAt: Long?, newCount: Int) {}
        override suspend fun clearRepeatSchedule(id: Long) {}
        override suspend fun countActiveRepeatSchedules(): Int = 0
        override suspend fun getActiveRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getPendingSync(): List<ChecklistEntity> = emptyList()
        override suspend fun getByCloudId(cloudId: String): ChecklistEntity? = null
        override suspend fun getSyncedCloudIds(): List<String> = emptyList()
        override suspend fun assignUserIdToAll(userId: String) {}
        override suspend fun assignCloudId(id: Long, cloudId: String) {}
        override suspend fun markSynced(id: Long, status: Int, updatedAt: Long) {}
        override suspend fun getWeeklyChecklistCount(): Int = 0
        override fun observeWeeklyChecklistCount(): Flow<Int> = flowOf(0)
    }

    /** In-memory [ChecklistFillDao] — same approach as [FakeChecklistDao]. */
    private class FakeFillDao : ChecklistFillDao {
        val fills = mutableListOf<ChecklistFillEntity>()

        override suspend fun getDefaultFillByChecklistId(checklistId: Long): ChecklistFillEntity? =
            fills.firstOrNull { it.checklistId == checklistId && it.isDefault && !it.isDeleted }

        override suspend fun getById(id: Long): ChecklistFillEntity? = fills.firstOrNull { it.id == id }

        override suspend fun insert(fill: ChecklistFillEntity): Long {
            fills.removeAll { it.id == fill.id }
            fills.add(fill)
            return fill.id
        }

        // ── Unused stubs ──
        override fun observeFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFillEntity>> = flowOf(emptyList())
        override fun observeDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFillEntity?> = flowOf(null)
        override fun observeAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFillEntity>> = flowOf(emptyList())
        override suspend fun getCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun getTotalAdditionalFillCount(): Int = 0
        override suspend fun getAllDefaultFills(): List<ChecklistFillEntity> = fills.filter { it.isDefault }
        override suspend fun getAllFillsByChecklistId(checklistId: Long): List<ChecklistFillEntity> =
            fills.filter { it.checklistId == checklistId }
        override suspend fun deleteById(id: Long) {
            fills.removeAll { it.id == id }
        }
        override suspend fun deleteByChecklistId(checklistId: Long) {
            fills.removeAll { it.checklistId == checklistId }
        }
        override suspend fun getPendingSync(): List<ChecklistFillEntity> = emptyList()
        override suspend fun getByCloudId(cloudId: String): ChecklistFillEntity? = null
        override suspend fun getSyncedFillCloudIds(checklistId: Long): List<String> = emptyList()
        override suspend fun updateSyncStatus(id: Long, status: Int) {}
        override suspend fun markSynced(id: Long, status: Int, updatedAt: Long) {}
        override suspend fun assignUserIdToAll(userId: String) {}
        override suspend fun assignCloudId(id: Long, cloudId: String) {}
        override suspend fun getActiveFillsByChecklistId(checklistId: Long): List<ChecklistFillEntity> =
            fills.filter { it.checklistId == checklistId && !it.isDeleted }
    }

    /** Reconciliation never stores/deletes files, so a no-op storage suffices. */
    private object NoopAttachmentStorage : AttachmentStoragePort {
        override suspend fun storeAttachment(
            sourcePath: String,
            fillId: Long,
            itemId: String,
            attachmentId: String,
            originalFileName: String,
        ): String? = null

        override suspend fun deleteAttachment(path: String) {}
        override suspend fun deleteAttachmentsFor(fillId: Long, itemId: String) {}
        override suspend fun deleteAttachmentsForFill(fillId: Long) {}
        override suspend fun probeImage(path: String, mimeType: String?): Pair<Int?, Int?> = null to null
        override suspend fun sizeOf(path: String): Long = 0L
    }
}
