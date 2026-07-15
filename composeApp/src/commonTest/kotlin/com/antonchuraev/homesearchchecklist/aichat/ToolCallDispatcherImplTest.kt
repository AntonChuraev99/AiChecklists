package com.antonchuraev.homesearchchecklist.aichat

import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.UndoHandle
import com.antonchuraev.homesearchchecklist.feature.aichat.api.format.ChatDateFormatter
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.analyzer.AiAnalyzer
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ════════════════════════════════════════════════════════════════════════════
// D1 rollback contract for ToolCallDispatcherImpl (2026-07-15 AI-chat Stage 1).
//
// THE RISK THIS FILE EXISTS FOR
//   The chat's forward path resolves items by fuzzy text: handleDeleteItem picks
//   `fill.items.firstOrNull { it.text.contains(toolCall.itemText, ignoreCase = true) }`.
//   That is fine for a user-typed command ("delete milk" — they meant whichever milk), but it is
//   catastrophic as an UNDO strategy: undoing "add Молоко" to a list that ALREADY had a "Молоко"
//   would remove the user's original row and keep ours. Undo must therefore address rows by the
//   ids captured in the UndoHandle at mutation time, never by text.
//
//   [undo_addedItem_removesOnlyAddedRow_whenSameTextAlreadyExists] is the discriminator: a
//   text-matching rollback deletes both rows (0 left) or the wrong one (id mismatch); only an
//   id-addressed rollback leaves exactly the pre-existing row.
//
// Fakes, not mocks: [FakeChecklistRepository] is a real in-memory store, so an assertion reads the
// resulting DATA (which rows survived, with which ids) rather than which methods were called.
// Ordered [calls] are recorded only where the ORDER itself is the contract (move = add-then-remove).
// ════════════════════════════════════════════════════════════════════════════

// ─── Recorded repository operations (order is load-bearing for move) ─────────

private sealed interface RepoCall {
    data class UpdateFill(val fillId: Long, val itemTexts: List<String>) : RepoCall
    data class UpdateTemplate(val checklistId: Long, val itemTexts: List<String>) : RepoCall
    data class AddChecklist(val name: String) : RepoCall
}

/**
 * In-memory [ChecklistRepository] covering exactly the surface the dispatcher touches.
 * Everything else throws, so a silent dependency on an unstubbed call surfaces as a failure
 * rather than as a fake "success".
 */
private class FakeChecklistRepository(
    seedChecklists: List<Checklist> = emptyList(),
    seedFills: List<ChecklistFill> = emptyList(),
) : ChecklistRepository {

    private val checklistState = MutableStateFlow(seedChecklists)
    private val fillState = MutableStateFlow(seedFills)
    val calls = mutableListOf<RepoCall>()
    private var nextChecklistId = (seedChecklists.maxOfOrNull { it.id } ?: 0L) + 1

    override val checklists: Flow<List<Checklist>> = checklistState
    override val weeklyChecklistCount: Flow<Int> = checklistState.map { 0 }

    fun checklist(id: Long): Checklist = checklistState.value.first { it.id == id }
    fun fill(id: Long): ChecklistFill = fillState.value.first { it.id == id }
    fun fillOf(checklistId: Long): ChecklistFill = fillState.value.first { it.checklistId == checklistId }

    override suspend fun updateFill(fill: ChecklistFill) {
        calls.add(RepoCall.UpdateFill(fill.id, fill.items.map { it.text }))
        fillState.value = fillState.value.map { if (it.id == fill.id) fill else it }
    }

    override suspend fun updateChecklistTemplate(checklist: Checklist) {
        calls.add(RepoCall.UpdateTemplate(checklist.id, checklist.items.map { it.text }))
        checklistState.value = checklistState.value.map { if (it.id == checklist.id) checklist else it }
    }

    override suspend fun addChecklist(checklist: Checklist): Long {
        calls.add(RepoCall.AddChecklist(checklist.name))
        val id = nextChecklistId++
        checklistState.value = checklistState.value + checklist.copy(id = id)
        fillState.value = fillState.value + ChecklistFill(
            id = id * 100,
            checklistId = id,
            name = checklist.name,
            items = checklist.items.map {
                ChecklistFillItem(text = it.text, checked = false, templateItemId = it.id)
            },
            isDefault = true,
        )
        return id
    }

    override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? =
        fillState.value.firstOrNull { it.checklistId == checklistId && it.isDefault }

    override suspend fun getChecklistById(id: Long): Checklist? = checklistState.value.firstOrNull { it.id == id }
    override fun observeChecklistById(id: Long): Flow<Checklist?> = checklistState.map { l -> l.firstOrNull { it.id == id } }
    override suspend fun getFillById(id: Long): ChecklistFill? = fillState.value.firstOrNull { it.id == id }
    override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> =
        fillState.map { l -> l.filter { it.checklistId == checklistId } }
    override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> =
        fillState.map { l -> l.firstOrNull { it.checklistId == checklistId && it.isDefault } }
    override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> =
        fillState.map { l -> l.filter { it.checklistId == checklistId && !it.isDefault } }
    override suspend fun getRemindersInRange(fromMs: Long, toMs: Long) =
        emptyList<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo>()
    override fun observeRemindersInRange(fromMs: Long, toMs: Long) =
        flowOf(emptyList<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo>())

    // ── Unused by the dispatcher: fail loudly rather than pretend ──
    override suspend fun updateChecklist(checklist: Checklist) = unsupported("updateChecklist")
    override suspend fun deleteChecklist(checklist: Checklist) = unsupported("deleteChecklist")
    override suspend fun reorderChecklists(orderedIds: List<Long>) = unsupported("reorderChecklists")
    override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) = unsupported("setSeparateCompleted")
    override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) = unsupported("setAutoDeleteCompleted")
    override suspend fun setFoldersEnabled(checklistId: Long, value: Boolean) = unsupported("setFoldersEnabled")
    override suspend fun setReminder(checklistId: Long, reminderAt: Long?) = unsupported("setReminder")
    override suspend fun countActiveReminders(): Int = unsupported("countActiveReminders")
    override suspend fun getActiveReminders() = unsupported("getActiveReminders")
    override suspend fun getAllItemRemindersForRescheduling() = unsupported("getAllItemRemindersForRescheduling")
    override suspend fun setRepeatSchedule(
        checklistId: Long,
        rule: com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule,
        timeOfDayMinutes: Int,
        firstTriggerAt: Long,
    ) = unsupported("setRepeatSchedule")
    override suspend fun advanceRepeatSchedule(checklistId: Long, nextAt: Long?, newCount: Int) = unsupported("advanceRepeatSchedule")
    override suspend fun clearRepeatSchedule(checklistId: Long) = unsupported("clearRepeatSchedule")
    override suspend fun resetDefaultFillChecks(checklistId: Long) = unsupported("resetDefaultFillChecks")
    override suspend fun countActiveRepeatSchedules(): Int = unsupported("countActiveRepeatSchedules")
    override suspend fun getActiveRepeatSchedules() = unsupported("getActiveRepeatSchedules")
    override suspend fun getPastDueRepeatSchedules(nowMillis: Long) = unsupported("getPastDueRepeatSchedules")
    override suspend fun getTotalAdditionalFillCount(): Int = unsupported("getTotalAdditionalFillCount")
    override suspend fun getWeeklyChecklistCount(): Int = unsupported("getWeeklyChecklistCount")
    override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = unsupported("togglePriority")
    override suspend fun addAttachment(
        fillId: Long,
        itemId: String,
        attachment: com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment,
    ) = unsupported("addAttachment")
    override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) = unsupported("removeAttachment")
    override suspend fun getFillCountByChecklistId(checklistId: Long): Int = unsupported("getFillCountByChecklistId")
    override suspend fun addFill(fill: ChecklistFill): Long = unsupported("addFill")
    override suspend fun deleteFill(fill: ChecklistFill) = unsupported("deleteFill")
    override suspend fun reorderItems(fill: ChecklistFill, checklist: Checklist) = unsupported("reorderItems")

    private fun unsupported(name: String): Nothing =
        throw UnsupportedOperationException("FakeChecklistRepository.$name is not part of the dispatcher contract")
}

private class FakeUserDataRepository(private val premium: Boolean = true) : UserDataRepository {
    private val flow = MutableStateFlow(UserData(userId = "u1", aiCredits = 100, isPremium = premium))
    override fun getUserDataFlow(): StateFlow<UserData> = flow
    override suspend fun getUserData(): UserData = flow.value
    override suspend fun update(userData: UserData) { flow.value = userData }
    override suspend fun ensureUserRegistered(): Result<RegistrationData> =
        Result.success(RegistrationData(userData = flow.value, isNewUser = false))
    override suspend fun syncWithServer(): Result<RegistrationData> =
        Result.success(RegistrationData(userData = flow.value, isNewUser = false))
    override suspend fun isPaywallLinked(): Boolean = false
    override suspend fun setPaywallLinked(linked: Boolean) = Unit
    override suspend fun restoreCreditsAfterPurchase(): Result<Int> = Result.success(0)
    override suspend fun getFirstLaunchAtMillis(): Long = 0L
}

private object NoOpLogger : AppLogger {
    override fun debug(tag: String, message: String) = Unit
    override fun info(tag: String, message: String) = Unit
    override fun warning(tag: String, message: String) = Unit
    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}

private object UnusedAnalyzer : AiAnalyzer {
    override suspend fun analyze(inputData: AnalyzeInputData, targetChecklist: Checklist?): Result<AnalyzeResult> =
        throw UnsupportedOperationException("analyze is not part of the undo/move contract")
    override suspend fun isAvailable(): Boolean = false
    override fun getSupportedInputTypes(): Set<KClass<out AnalyzeInputData>> = emptySet()
}

private object UnusedAttachmentStorage : AttachmentStoragePort {
    override suspend fun storeAttachment(
        sourcePath: String,
        fillId: Long,
        itemId: String,
        attachmentId: String,
        originalFileName: String,
    ): String = throw UnsupportedOperationException("attachments are not part of the undo/move contract")
    override suspend fun deleteAttachment(path: String) = Unit
    override suspend fun deleteAttachmentsFor(fillId: Long, itemId: String) = Unit
    override suspend fun deleteAttachmentsForFill(fillId: Long) = Unit
    override suspend fun probeImage(path: String, mimeType: String?): Pair<Int?, Int?> = null to null
    override suspend fun sizeOf(path: String): Long = 0L
}

private object NoOpActivationCoordinator : ActivationCoordinator {
    override val reminderOptInRequests: kotlinx.coroutines.flow.SharedFlow<Long> =
        kotlinx.coroutines.flow.MutableSharedFlow()
    override suspend fun onAiChecklistCreated(checklistId: Long, activationBundleEnabled: Boolean) = Unit
    override suspend fun reportReminderOptInOutcome(granted: Boolean) = Unit
}

private object DefaultsRemoteConfig : RemoteConfigProvider {
    override suspend fun fetchAndActivate(): Boolean = true
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
    override fun getString(key: String, defaultValue: String): String = defaultValue
    override fun getLong(key: String, defaultValue: Long): Long = defaultValue
}

/** Deterministic, resource-free date copy (the real one needs Compose Resources). */
private object TokenDateFormatter : ChatDateFormatter {
    override suspend fun formatDateTime(epochMs: Long): String = "DT($epochMs)"
    override suspend fun formatDay(epochMs: Long): String = "DAY($epochMs)"
}

// ─── Fixtures ────────────────────────────────────────────────────────────────

/**
 * Builds a [ChecklistFillItem] with an EXPLICIT id.
 *
 * The public constructor always auto-generates `"${currentTimeMillis()}_${Random.nextInt(0,10000)}"`
 * and there is no `withId` (unlike [ChecklistItem]), so two rows created in the same millisecond can
 * collide — which is precisely the confusion the id-vs-text tests must not suffer from. The
 * generated `@Serializable` constructor is the one seam that accepts a fixed id.
 */
private fun fillItem(
    id: String,
    text: String,
    checked: Boolean = false,
    templateItemId: String? = null,
): ChecklistFillItem = Json.decodeFromString(
    """{"text":"$text","checked":$checked,"id":"$id"""" +
        (templateItemId?.let { ""","templateItemId":"$it"""" } ?: "") +
        "}",
)

private fun templateItem(id: String, text: String): ChecklistItem =
    ChecklistItem(text = text, checked = false).withId(id)

private fun buildDispatcher(repo: FakeChecklistRepository, premium: Boolean = true) = ToolCallDispatcherImpl(
    checklistRepository = repo,
    userDataRepository = FakeUserDataRepository(premium),
    aiAnalyzer = UnusedAnalyzer,
    attachmentStorage = UnusedAttachmentStorage,
    logger = NoOpLogger,
    activationCoordinator = NoOpActivationCoordinator,
    remoteConfigProvider = DefaultsRemoteConfig,
    dateFormatter = TokenDateFormatter,
)

/** A single list "Покупки" (id 1, fill 11) pre-seeded with [items]. */
private fun shoppingRepo(
    templateItems: List<ChecklistItem> = emptyList(),
    fillItems: List<ChecklistFillItem> = emptyList(),
) = FakeChecklistRepository(
    seedChecklists = listOf(Checklist(id = 1L, name = "Покупки", items = templateItems)),
    seedFills = listOf(
        ChecklistFill(id = 11L, checklistId = 1L, name = "Покупки", items = fillItems, isDefault = true),
    ),
)

private fun DispatchOutcome.addedHandle(): UndoHandle.AddedItem {
    val success = assertIs<DispatchOutcome.Success>(this, "expected a Success outcome, got $this")
    val undo = assertNotNull(success.undo, "an AddItem Success must carry an undo handle")
    return assertIs<UndoHandle.AddedItem>(undo, "AddItem must produce an AddedItem handle")
}

class ToolCallDispatcherImplTest {

    // ════════════════════════════════════════════════════════════════════════
    // Undo — id-addressed, never text-addressed
    // ════════════════════════════════════════════════════════════════════════

    /**
     * THE flagship guard. The list already holds a "Молоко" the user added by hand; the chat adds a
     * second one; the user taps Undo. Only OUR row may disappear.
     *
     * Sabotage check: a rollback written as `filter { !it.text.contains("Молоко") }` leaves 0 rows;
     * one written as `removeFirst { it.text == ... }` leaves the wrong id. Both fail here.
     */
    @Test
    fun undo_addedItem_removesOnlyAddedRow_whenSameTextAlreadyExists() = runTest {
        val repo = shoppingRepo(
            templateItems = listOf(templateItem("tpl_old", "Молоко")),
            fillItems = listOf(fillItem("fill_old", "Молоко", templateItemId = "tpl_old")),
        )
        val dispatcher = buildDispatcher(repo)

        val handle = dispatcher.dispatch(
            ToolCall.AddItem(checklistHint = "Покупки", itemText = "Молоко"),
        ).addedHandle()
        assertEquals(2, repo.fill(11L).items.size, "precondition: the duplicate was added")

        val outcome = dispatcher.undo(handle)

        assertIs<DispatchOutcome.Success>(outcome, "undo of a present row must succeed")
        val survivors = repo.fill(11L).items
        assertEquals(1, survivors.size, "undo must remove exactly the row it added, not every «Молоко»")
        assertEquals(
            "fill_old",
            survivors.single().id,
            "the surviving row must be the user's ORIGINAL one — text-matching undo removes the wrong row",
        )
        val templateSurvivors = repo.checklist(1L).items
        assertEquals(1, templateSurvivors.size, "the template must keep exactly the pre-existing row")
        assertEquals("tpl_old", templateSurvivors.single().id)
    }

    /**
     * The fill row is what the user sees; the template row is what makes the checklist dirty and
     * therefore sync. Undoing only the fill leaves a phantom template item that reappears on the
     * next device — a silent divergence.
     */
    @Test
    fun undo_addedItem_removesFromBothFillAndTemplate() = runTest {
        val repo = shoppingRepo()
        val dispatcher = buildDispatcher(repo)
        val handle = dispatcher.dispatch(
            ToolCall.AddItem(checklistHint = "Покупки", itemText = "Молоко"),
        ).addedHandle()
        repo.calls.clear()

        dispatcher.undo(handle)

        assertEquals(emptyList(), repo.fill(11L).items.map { it.text }, "fill row must be gone")
        assertEquals(emptyList(), repo.checklist(1L).items.map { it.text }, "template row must be gone")
        assertTrue(
            repo.calls.any { it is RepoCall.UpdateTemplate && it.checklistId == 1L },
            "undo must WRITE the template (that write is what dirties the parent → sync); calls = ${repo.calls}",
        )
    }

    /** Un-completing addresses the same row by id and flips it back — the template holds no checked state. */
    @Test
    fun undo_completedItem_unchecksById() = runTest {
        val repo = shoppingRepo(
            templateItems = listOf(templateItem("tpl_1", "Молоко")),
            fillItems = listOf(
                fillItem("fill_1", "Молоко", checked = false, templateItemId = "tpl_1"),
                fillItem("fill_2", "Молоко и хлеб", checked = false),
            ),
        )
        val dispatcher = buildDispatcher(repo)
        val outcome = dispatcher.dispatch(ToolCall.CompleteItem(checklistHint = "Покупки", itemText = "Молоко"))
        val handle = assertIs<UndoHandle.CompletedItem>(
            assertIs<DispatchOutcome.Success>(outcome).undo,
            "CompleteItem must carry a CompletedItem handle",
        )
        assertTrue(repo.fill(11L).items.first { it.id == handle.fillItemId }.checked, "precondition: it got checked")

        dispatcher.undo(handle)

        val rows = repo.fill(11L).items
        assertEquals(
            listOf(false, false),
            rows.map { it.checked },
            "undo must uncheck the row it checked and touch no other",
        )
        assertEquals(listOf("fill_1", "fill_2"), rows.map { it.id }, "undo must not drop or reorder rows")
    }

    /**
     * The user may delete the row by hand before tapping Undo. Reporting NotFound keeps the promise
     * that every chat action produces visible feedback — a silent no-op reads as a broken button.
     */
    @Test
    fun undo_addedItem_whenRowAlreadyGone_returnsNotFound() = runTest {
        val repo = shoppingRepo()
        val dispatcher = buildDispatcher(repo)
        val handle = dispatcher.dispatch(
            ToolCall.AddItem(checklistHint = "Покупки", itemText = "Молоко"),
        ).addedHandle()
        // The user deletes it manually in the meantime.
        dispatcher.dispatch(ToolCall.DeleteItem(checklistHint = "Покупки", itemText = "Молоко"))
        assertEquals(emptyList(), repo.fill(11L).items, "precondition: the row is gone")

        val outcome = dispatcher.undo(handle)

        val notFound = assertIs<DispatchOutcome.NotFound>(outcome, "a vanished row must be reported, not silently skipped")
        assertEquals("chat_undo_item_gone", notFound.messageKey)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Move — add first, remove second
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Order is the contract, not an implementation detail: add-then-remove degrades to a visible
     * duplicate the user can delete, remove-then-add degrades to a silently lost item.
     */
    @Test
    fun moveAddedItem_addsToTargetThenRemovesFromSource() = runTest {
        val repo = FakeChecklistRepository(
            seedChecklists = listOf(
                Checklist(id = 1L, name = "Покупки", items = emptyList()),
                Checklist(id = 2L, name = "Работа", items = emptyList()),
            ),
            seedFills = listOf(
                ChecklistFill(id = 11L, checklistId = 1L, name = "Покупки", items = emptyList(), isDefault = true),
                ChecklistFill(id = 22L, checklistId = 2L, name = "Работа", items = emptyList(), isDefault = true),
            ),
        )
        val dispatcher = buildDispatcher(repo)
        val handle = dispatcher.dispatch(
            ToolCall.AddItem(checklistHint = "Покупки", itemText = "Молоко"),
        ).addedHandle()
        repo.calls.clear()

        val outcome = dispatcher.moveAddedItem(handle, targetChecklistName = "Работа")

        assertIs<DispatchOutcome.Success>(outcome, "a resolvable target must move")
        assertEquals(listOf("Молоко"), repo.fill(22L).items.map { it.text }, "item must land in the target list")
        assertEquals(emptyList(), repo.fill(11L).items.map { it.text }, "item must leave the source list")

        val addIndex = repo.calls.indexOfFirst { it is RepoCall.UpdateFill && it.fillId == 22L && "Молоко" in it.itemTexts }
        val removeIndex = repo.calls.indexOfFirst { it is RepoCall.UpdateFill && it.fillId == 11L && "Молоко" !in it.itemTexts }
        assertTrue(addIndex >= 0, "no write added «Молоко» to the target fill; calls = ${repo.calls}")
        assertTrue(removeIndex >= 0, "no write removed «Молоко» from the source fill; calls = ${repo.calls}")
        assertTrue(
            addIndex < removeIndex,
            "add must precede remove (a crash between them must duplicate, never lose); calls = ${repo.calls}",
        )
    }

    /**
     * If the target cannot be resolved the item must stay exactly where it is: removing it first and
     * discovering the ambiguity afterwards would destroy it.
     */
    @Test
    fun moveAddedItem_whenTargetAmbiguous_leavesSourceUntouched() = runTest {
        val repo = FakeChecklistRepository(
            seedChecklists = listOf(
                Checklist(id = 1L, name = "Покупки", items = emptyList()),
                Checklist(id = 2L, name = "Работа дом", items = emptyList()),
                Checklist(id = 3L, name = "Работа офис", items = emptyList()),
            ),
            seedFills = listOf(
                ChecklistFill(id = 11L, checklistId = 1L, name = "Покупки", items = emptyList(), isDefault = true),
                ChecklistFill(id = 22L, checklistId = 2L, name = "Работа дом", items = emptyList(), isDefault = true),
                ChecklistFill(id = 33L, checklistId = 3L, name = "Работа офис", items = emptyList(), isDefault = true),
            ),
        )
        val dispatcher = buildDispatcher(repo)
        val handle = dispatcher.dispatch(
            ToolCall.AddItem(checklistHint = "Покупки", itemText = "Молоко"),
        ).addedHandle()

        val outcome = dispatcher.moveAddedItem(handle, targetChecklistName = "Работа")

        val ambiguous = assertIs<DispatchOutcome.AmbiguousMatch>(outcome, "«Работа» matches two lists")
        assertEquals(listOf("Работа дом", "Работа офис"), ambiguous.candidates)
        assertEquals(
            listOf("Молоко"),
            repo.fill(11L).items.map { it.text },
            "an unresolved move must not remove the item from where it is",
        )
        assertEquals(handle.fillItemId, repo.fill(11L).items.single().id, "and must not re-create it under a new id")
        assertEquals(emptyList(), repo.fill(22L).items, "nothing may land in a candidate list")
        assertEquals(emptyList(), repo.fill(33L).items, "nothing may land in a candidate list")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Handle construction
    // ════════════════════════════════════════════════════════════════════════

    /**
     * The handle is the only thing standing between Undo and the fuzzy-text disaster above, so its
     * ids must point at the rows that were really written — asserted against the repository's actual
     * state, not against the handle itself.
     */
    @Test
    fun addItem_success_carriesUndoHandleWithBothIds() = runTest {
        val repo = shoppingRepo()
        val dispatcher = buildDispatcher(repo)

        val handle = dispatcher.dispatch(
            ToolCall.AddItem(checklistHint = "Покупки", itemText = "Молоко"),
        ).addedHandle()

        val storedFillItem = repo.fill(11L).items.single()
        val storedTemplateItem = repo.checklist(1L).items.single()
        assertEquals(storedFillItem.id, handle.fillItemId, "handle.fillItemId must address the row actually inserted")
        assertEquals(storedTemplateItem.id, handle.templateItemId, "handle.templateItemId must address the template row")
        assertEquals(
            storedTemplateItem.id,
            storedFillItem.templateItemId,
            "the inserted fill row must stay linked to its template row (unlinked rows sink to the bottom in folder mode)",
        )
        assertEquals(1L, handle.checklistId)
        assertEquals("Покупки", handle.checklistName, "the list name labels the move picker")
        assertEquals(11L, handle.fillId)
        assertEquals("Молоко", handle.itemText, "display copy for the chip label")
    }
}
