package com.antonchuraev.homesearchchecklist.aichat

import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
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
import kotlin.test.assertFalse
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

    // Real in-memory dual-write so the dispatcher test can read the surviving rows AND the count.
    override suspend fun deleteCompletedItems(checklistId: Long): Int {
        val fill = fillState.value.firstOrNull { it.checklistId == checklistId && it.isDefault } ?: return 0
        val completed = fill.items.filter { it.checked }
        if (completed.isEmpty()) return 0
        val completedLinkIds = completed.mapNotNull { it.templateItemId }.toSet()
        val completedLegacyTexts = completed.filter { it.templateItemId == null }.map { it.text }.toSet()
        updateFill(fill.copy(items = fill.items.filter { !it.checked }))
        checklistState.value.firstOrNull { it.id == checklistId }?.let { checklist ->
            updateChecklistTemplate(
                checklist.copy(
                    items = checklist.items.filterNot { it.id in completedLinkIds || it.text in completedLegacyTexts },
                ),
            )
        }
        return completed.size
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

/**
 * Remote config whose free-checklist ceiling is settable per test.
 *
 * The ceiling is a PARAMETER on purpose. [DefaultsRemoteConfig] above answers every key with the
 * caller's own default, so a limit test built on it passes just as happily against a number compiled
 * into the client — which is exactly the defect these tests exist for. Mirrors
 * `ConfigurableRemoteConfigProvider` in feature/create's test source set (that one is `internal` to
 * its module, so it cannot be imported here).
 */
private class ConfigurableRemoteConfig(private val maxChecklistsFree: Long) : RemoteConfigProvider {
    override suspend fun fetchAndActivate(): Boolean = true
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
    override fun getString(key: String, defaultValue: String): String = defaultValue
    override fun getLong(key: String, defaultValue: Long): Long = when (key) {
        RemoteConfigKeys.MAX_CHECKLISTS_FREE -> maxChecklistsFree
        else -> defaultValue
    }
}

/**
 * An analyzer that succeeds AND counts its invocations. The count is the assertion that the
 * free-tier gate runs BEFORE the paid Cloud Function call — a gate placed after it would still
 * return RequiresPremium while having already spent the user's credits.
 */
private class RecordingAnalyzer(
    private val suggested: List<String> = listOf("Milk", "Bread"),
) : AiAnalyzer {
    var callCount = 0
        private set

    override suspend fun analyze(inputData: AnalyzeInputData, targetChecklist: Checklist?): Result<AnalyzeResult> {
        callCount++
        return Result.success(
            AnalyzeResult(suggestedItems = suggested.map { ChecklistItem(text = it, checked = false) }),
        )
    }

    override suspend fun isAvailable(): Boolean = true
    override fun getSupportedInputTypes(): Set<KClass<out AnalyzeInputData>> = emptySet()
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

private fun buildDispatcher(
    repo: FakeChecklistRepository,
    premium: Boolean = true,
    remoteConfig: RemoteConfigProvider = DefaultsRemoteConfig,
    analyzer: AiAnalyzer = UnusedAnalyzer,
) = ToolCallDispatcherImpl(
    checklistRepository = repo,
    userDataRepository = FakeUserDataRepository(premium),
    aiAnalyzer = analyzer,
    attachmentStorage = UnusedAttachmentStorage,
    logger = NoOpLogger,
    activationCoordinator = NoOpActivationCoordinator,
    remoteConfigProvider = remoteConfig,
    dateFormatter = TokenDateFormatter,
    analyticsTracker = NoOpAnalytics,
)

/**
 * These tests assert dispatch behaviour, not reporting. The dispatcher guards its own tracker call
 * (`onChecklistCreated` wraps it in runCatching), so a no-op here cannot mask a failure that matters
 * to them — the `checklist_created` emit is covered where it is the subject, not here.
 */
private object NoOpAnalytics : AnalyticsTracker {
    override fun setUserId(userId: String) {}
    override fun setUserProperties(properties: Map<String, Any>) {}
    override fun screenView(name: String) {}
    override fun event(name: String, params: Map<String, Any>) {}
}

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

/** "Покупки" (id 1, fill 11) with two CHECKED rows (Молоко, Хлеб) and one unchecked (Яйца). */
private fun mixedRepo() = FakeChecklistRepository(
    seedChecklists = listOf(
        Checklist(
            id = 1L, name = "Покупки",
            items = listOf(
                templateItem("tpl_a", "Молоко"),
                templateItem("tpl_b", "Хлеб"),
                templateItem("tpl_c", "Яйца"),
            ),
        ),
    ),
    seedFills = listOf(
        ChecklistFill(
            id = 11L, checklistId = 1L, name = "Покупки", isDefault = true,
            items = listOf(
                fillItem("f_a", "Молоко", checked = true, templateItemId = "tpl_a"),
                fillItem("f_b", "Хлеб", checked = true, templateItemId = "tpl_b"),
                fillItem("f_c", "Яйца", checked = false, templateItemId = "tpl_c"),
            ),
        ),
    ),
)

/** Two lists whose names both contain [shared] — the ambiguity fixture. */
private fun twoListRepo(name1: String, name2: String) = FakeChecklistRepository(
    seedChecklists = listOf(
        Checklist(id = 1L, name = name1, items = emptyList()),
        Checklist(id = 2L, name = name2, items = emptyList()),
    ),
    seedFills = listOf(
        ChecklistFill(id = 11L, checklistId = 1L, name = name1, isDefault = true, items = emptyList()),
        ChecklistFill(id = 22L, checklistId = 2L, name = name2, isDefault = true, items = emptyList()),
    ),
)

/**
 * [count] ordinary projects (no Inbox), i.e. exactly the population the free-tier ceiling counts.
 * Fills are irrelevant here — creating a checklist never resolves an existing one.
 */
private fun repoWithProjects(count: Int) = FakeChecklistRepository(
    seedChecklists = (1..count).map { Checklist(id = it.toLong(), name = "List $it", items = emptyList()) },
)

/** A supported (image) attachment, so the create-from-attachment path reaches the analyzer. */
private val photoAttachment = ChatAttachment(
    sourcePath = "/tmp/receipt.jpg",
    mimeType = "image/jpeg",
    fileName = "receipt.jpg",
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
    // Target resolution — id first, name as fallback (id-hints, D2 2026-07-15)
    // ════════════════════════════════════════════════════════════════════════

    /** Two lists that a human cannot tell apart by name — the case ids exist for. */
    private fun twoShoppingListsRepo() = FakeChecklistRepository(
        seedChecklists = listOf(
            Checklist(id = 1L, name = "Покупки", items = emptyList()),
            Checklist(id = 2L, name = "Покупки", items = emptyList()),
        ),
        seedFills = listOf(
            ChecklistFill(id = 11L, checklistId = 1L, name = "Покупки", items = emptyList(), isDefault = true),
            ChecklistFill(id = 22L, checklistId = 2L, name = "Покупки", items = emptyList(), isDefault = true),
        ),
    )

    /**
     * THE point of id-hints. The name matches BOTH lists, so the name path can only shrug
     * (AmbiguousMatch) — but the user already answered "which list?" by tapping a chip, and the id
     * carries that answer. Dispatching must honour it instead of re-asking.
     */
    @Test
    fun addItem_withChecklistId_landsInThatList_evenThoughTheNameMatchesBoth() = runTest {
        val repo = twoShoppingListsRepo()
        val dispatcher = buildDispatcher(repo)

        val outcome = dispatcher.dispatch(
            ToolCall.AddItem(checklistId = 2L, checklistHint = "Покупки", itemText = "Молоко"),
        )

        assertIs<DispatchOutcome.Success>(outcome, "an id-addressed add is never ambiguous, got $outcome")
        assertEquals(listOf("Молоко"), repo.fill(22L).items.map { it.text }, "the item belongs to the list the id named")
        assertEquals(emptyList(), repo.fill(11L).items, "and must not appear in its same-named neighbour")
    }

    /**
     * Sabotage guard for a resolver written as "try the id, then try the name anyway". The id points
     * at a list that is gone (deleted between building the chip and tapping it) while a same-named
     * list survives. A name retry would silently redirect the write into a list the user never
     * chose — the exact failure ids exist to prevent, and worse than not writing at all because it
     * is invisible. Same reasoning as [UndoHandle] being id-only.
     */
    @Test
    fun addItem_withStaleChecklistId_reportsNotFound_ratherThanFallingBackToTheSameNamedSurvivor() = runTest {
        val repo = shoppingRepo() // only id=1 "Покупки" exists
        val dispatcher = buildDispatcher(repo)

        val outcome = dispatcher.dispatch(
            ToolCall.AddItem(checklistId = 99L, checklistHint = "Покупки", itemText = "Молоко"),
        )

        val notFound = assertIs<DispatchOutcome.NotFound>(
            outcome,
            "a gone id must fail loudly; falling back to the name would write into the wrong list, got $outcome",
        )
        assertEquals("chat_dispatch_no_checklist_match", notFound.messageKey)
        assertEquals(listOf("Покупки"), notFound.args, "the message names the list the user asked for")
        assertEquals(emptyList(), repo.fill(11L).items, "nothing may land in the surviving same-named list")
    }

    /**
     * The fallback the server path depends on: Layer 2/3 name lists, they cannot know local row ids.
     * A null id must behave exactly as it did before ids existed — this is an extension, not a
     * migration, and a dispatcher that started requiring ids would break every remote tool call.
     */
    @Test
    fun addItem_withoutChecklistId_stillResolvesByName() = runTest {
        val repo = shoppingRepo()
        val dispatcher = buildDispatcher(repo)

        val outcome = dispatcher.dispatch(
            ToolCall.AddItem(checklistHint = "покуп", itemText = "Молоко"),
        )

        assertIs<DispatchOutcome.Success>(outcome, "a fuzzy name still resolves, got $outcome")
        assertEquals(listOf("Молоко"), repo.fill(11L).items.map { it.text })
    }

    /** …and an id-less name matching several lists must still ask, not guess. */
    @Test
    fun addItem_withoutChecklistId_ambiguousName_stillAsksWhichList() = runTest {
        val repo = twoShoppingListsRepo()
        val dispatcher = buildDispatcher(repo)

        val outcome = dispatcher.dispatch(
            ToolCall.AddItem(checklistHint = "Покупки", itemText = "Молоко"),
        )

        val ambiguous = assertIs<DispatchOutcome.AmbiguousMatch>(outcome, "got $outcome")
        assertEquals(listOf("Покупки", "Покупки"), ambiguous.candidates, "both same-named lists are candidates")
        assertEquals(emptyList(), repo.fill(11L).items, "an unresolved add writes nothing")
        assertEquals(emptyList(), repo.fill(22L).items, "an unresolved add writes nothing")
    }

    /**
     * The id overrides a name that points somewhere else entirely. Not a hypothetical: the chip
     * builder pairs id+name from one row, so a mismatch means the pair was assembled wrong — and
     * an id that yields to a stale name would make that bug invisible instead of testable.
     */
    @Test
    fun completeItem_withChecklistId_ignoresAContradictingName() = runTest {
        val repo = FakeChecklistRepository(
            seedChecklists = listOf(
                Checklist(id = 1L, name = "Покупки", items = listOf(templateItem("tpl_1", "Молоко"))),
                Checklist(id = 2L, name = "Работа", items = listOf(templateItem("tpl_2", "Молоко"))),
            ),
            seedFills = listOf(
                ChecklistFill(
                    id = 11L, checklistId = 1L, name = "Покупки", isDefault = true,
                    items = listOf(fillItem("fill_1", "Молоко", templateItemId = "tpl_1")),
                ),
                ChecklistFill(
                    id = 22L, checklistId = 2L, name = "Работа", isDefault = true,
                    items = listOf(fillItem("fill_2", "Молоко", templateItemId = "tpl_2")),
                ),
            ),
        )
        val dispatcher = buildDispatcher(repo)

        val outcome = dispatcher.dispatch(
            ToolCall.CompleteItem(checklistId = 2L, checklistHint = "Покупки", itemText = "Молоко"),
        )

        assertIs<DispatchOutcome.Success>(outcome, "got $outcome")
        assertTrue(repo.fill(22L).items.single().checked, "the id decides the target")
        assertFalse(repo.fill(11L).items.single().checked, "the name must not pull the write back")
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

    // ════════════════════════════════════════════════════════════════════════
    // ClearCompleted — bulk clear checked items; picker (never prose) on ambiguity
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun clearCompleted_openList_removesCheckedReportsCountKeepsUnchecked() = runTest {
        val repo = mixedRepo()
        val dispatcher = buildDispatcher(repo)

        val outcome = dispatcher.dispatch(ToolCall.ClearCompleted(checklistHint = "Покупки", checklistId = 1L))

        val success = assertIs<DispatchOutcome.Success>(outcome, "got $outcome")
        assertEquals("chat_dispatch_completed_items_removed", success.messageKey)
        assertEquals(listOf("2", "Покупки"), success.args, "reports the removed count then the list name")
        assertEquals(1L, success.linkedChecklistId)
        // Only the unchecked row survives — in BOTH the fill and the template (dual-write).
        assertEquals(listOf("Яйца"), repo.fill(11L).items.map { it.text }, "fill keeps only the unchecked row")
        assertEquals(listOf("Яйца"), repo.checklist(1L).items.map { it.text }, "template mirrors the removal")
    }

    @Test
    fun clearCompleted_unambiguousHint_removesChecked() = runTest {
        val repo = mixedRepo()
        val dispatcher = buildDispatcher(repo)

        val outcome = dispatcher.dispatch(ToolCall.ClearCompleted(checklistHint = "Покуп"))

        assertIs<DispatchOutcome.Success>(outcome, "a hint matching exactly one list proceeds; got $outcome")
        assertEquals(1, repo.fill(11L).items.size, "the two checked rows are gone")
    }

    @Test
    fun clearCompleted_ambiguousHint_returnsAmbiguousMatch() = runTest {
        val repo = twoListRepo("Покупки дом", "Покупки офис")
        val dispatcher = buildDispatcher(repo)

        val outcome = dispatcher.dispatch(ToolCall.ClearCompleted(checklistHint = "Покупки"))

        val ambiguous = assertIs<DispatchOutcome.AmbiguousMatch>(outcome, "got $outcome")
        assertEquals(
            listOf("Покупки дом", "Покупки офис"),
            ambiguous.candidates,
            "the picker gets the candidate names — never a prose question",
        )
    }

    @Test
    fun clearCompleted_noTargetMultipleLists_returnsAmbiguousMatch() = runTest {
        val repo = twoListRepo("Покупки", "Работа")
        val dispatcher = buildDispatcher(repo)

        val outcome = dispatcher.dispatch(ToolCall.ClearCompleted(checklistHint = null))

        val ambiguous = assertIs<DispatchOutcome.AmbiguousMatch>(
            outcome,
            "a bulk clear never guesses across >1 list (unlike add/delete); got $outcome",
        )
        assertEquals(listOf("Покупки", "Работа"), ambiguous.candidates)
    }

    @Test
    fun clearCompleted_noTargetSingleList_clearsItWithoutAsking() = runTest {
        val repo = mixedRepo()
        val dispatcher = buildDispatcher(repo)

        val outcome = dispatcher.dispatch(ToolCall.ClearCompleted(checklistHint = null))

        assertIs<DispatchOutcome.Success>(outcome, "the sole list is cleared without asking; got $outcome")
        assertEquals(1, repo.fill(11L).items.size)
    }

    @Test
    fun clearCompleted_noCompletedItems_friendlySuccessNotError() = runTest {
        val repo = shoppingRepo(
            templateItems = listOf(templateItem("tpl_1", "Молоко")),
            fillItems = listOf(fillItem("f_1", "Молоко", checked = false, templateItemId = "tpl_1")),
        )
        val dispatcher = buildDispatcher(repo)

        val outcome = dispatcher.dispatch(ToolCall.ClearCompleted(checklistHint = "Покупки"))

        val success = assertIs<DispatchOutcome.Success>(
            outcome,
            "zero completed is a friendly success, not an error; got $outcome",
        )
        assertEquals("chat_dispatch_no_completed_items", success.messageKey)
        assertEquals(listOf("Покупки"), success.args)
        assertEquals(1, repo.fill(11L).items.size, "nothing was removed")
    }

    @Test
    fun clearCompleted_noChecklists_notFound() = runTest {
        val repo = FakeChecklistRepository()
        val dispatcher = buildDispatcher(repo)

        val outcome = dispatcher.dispatch(ToolCall.ClearCompleted(checklistHint = "Покупки"))

        val notFound = assertIs<DispatchOutcome.NotFound>(outcome, "got $outcome")
        assertEquals("chat_dispatch_no_checklists", notFound.messageKey)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Screen-aware targeting: an ID may reach the system Inbox, a NAME may not.
    //
    // The asymmetry is the whole contract. An id can only come from the client (a tapped chip,
    // the remembered default, the screen the dock is open over), so honouring it on the Inbox tab
    // is what stops "add milk" landing in an unrelated list. A NAME comes from the model, and the
    // Inbox is hidden from every picker and from the free-tier count — letting a name reach it
    // would be the one surface where it leaks.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun addItem_byId_reachesTheSystemInbox() = runTest {
        val repo = inboxRepo()
        val dispatcher = buildDispatcher(repo)

        val outcome = dispatcher.dispatch(
            ToolCall.AddItem(checklistHint = "Inbox", itemText = "Milk", checklistId = 9L),
        )

        assertIs<DispatchOutcome.Success>(outcome, "an id-targeted add must reach the Inbox; got $outcome")
        assertEquals(listOf("Milk"), repo.fill(99L).items.map { it.text })
        assertEquals(listOf("Milk"), repo.checklist(9L).items.map { it.text })
    }

    @Test
    fun addItem_byName_cannotReachTheSystemInbox() = runTest {
        val repo = inboxRepo()
        val dispatcher = buildDispatcher(repo)

        // Only the Inbox is named "Inbox"; the pool for a name is `projects`, which excludes it.
        val outcome = dispatcher.dispatch(ToolCall.AddItem(checklistHint = "Inbox", itemText = "Milk"))

        val notFound = assertIs<DispatchOutcome.NotFound>(outcome, "got $outcome")
        assertEquals("chat_dispatch_no_checklist_match", notFound.messageKey)
        assertTrue(repo.fill(99L).items.isEmpty(), "nothing may be written to the Inbox by name")
    }

    @Test
    fun renameChecklist_byInboxName_stillFails() = runTest {
        val repo = inboxRepo()
        val dispatcher = buildDispatcher(repo)

        val outcome = dispatcher.dispatch(
            ToolCall.RenameChecklist(checklistHint = "Inbox", newName = "Junk"),
        )

        val notFound = assertIs<DispatchOutcome.NotFound>(outcome, "got $outcome")
        assertEquals("chat_dispatch_no_checklist_match", notFound.messageKey)
        assertEquals("Inbox", repo.checklist(9L).name, "the Inbox must keep its name")
    }

    @Test
    fun readChecklist_withContextId_readsTheInbox() = runTest {
        val repo = inboxRepo(
            inboxTemplate = listOf(templateItem("tpl_i", "Call the bank")),
            inboxFill = listOf(fillItem("f_i", "Call the bank", templateItemId = "tpl_i")),
        )
        val dispatcher = buildDispatcher(repo)

        val outcome = dispatcher.dispatch(ToolCall.ReadChecklist(name = "Inbox", checklistId = 9L))

        val content = assertIs<DispatchOutcome.ChecklistContent>(outcome, "got $outcome")
        assertEquals("Inbox", content.checklistName)
        assertEquals(listOf("Call the bank"), content.items.map { it.text })
    }

    // ─── move_item ────────────────────────────────────────────────────────────

    @Test
    fun moveItem_movesTemplateAndFillOnBothSides() = runTest {
        val repo = inboxRepo(
            inboxTemplate = listOf(templateItem("tpl_i", "Call the bank")),
            inboxFill = listOf(fillItem("f_i", "Call the bank", templateItemId = "tpl_i")),
        )
        val dispatcher = buildDispatcher(repo)

        val outcome = dispatcher.dispatch(
            ToolCall.MoveItem(
                itemText = "Call the bank",
                toChecklistHint = "Покупки",
                fromChecklistId = 9L,
            ),
        )

        val success = assertIs<DispatchOutcome.Success>(outcome, "got $outcome")
        assertEquals("chat_dispatch_moved_item", success.messageKey)
        assertEquals(listOf("Call the bank", "Покупки"), success.args)
        // Destination gained BOTH rows, linked (an unlinked fill row renders at the bottom in
        // folder mode — the recurring template↔fill bug).
        assertEquals(listOf("Call the bank"), repo.fill(11L).items.map { it.text })
        assertEquals(listOf("Call the bank"), repo.checklist(1L).items.map { it.text })
        val movedFillItem = repo.fill(11L).items.single()
        assertEquals(
            repo.checklist(1L).items.single().id,
            movedFillItem.templateItemId,
            "the new fill row must link to the new template row",
        )
        // Source lost BOTH rows.
        assertTrue(repo.fill(99L).items.isEmpty(), "source fill row must be gone")
        assertTrue(repo.checklist(9L).items.isEmpty(), "source template row must be gone")
    }

    @Test
    fun moveItem_reminderBearingItem_isRefusedAndNothingIsWritten() = runTest {
        val repo = inboxRepo(
            inboxTemplate = listOf(templateItem("tpl_i", "Call the bank")),
            inboxFill = listOf(
                fillItem("f_i", "Call the bank", templateItemId = "tpl_i").withReminderAt(1_800_000_000_000L),
            ),
        )
        val dispatcher = buildDispatcher(repo)
        repo.calls.clear()

        val outcome = dispatcher.dispatch(
            ToolCall.MoveItem(
                itemText = "Call the bank",
                toChecklistHint = "Покупки",
                fromChecklistId = 9L,
            ),
        )

        val notFound = assertIs<DispatchOutcome.NotFound>(outcome, "got $outcome")
        assertEquals("chat_dispatch_move_blocked", notFound.messageKey)
        assertEquals(listOf("Call the bank"), notFound.args)
        assertTrue(repo.calls.isEmpty(), "a refused move must write nothing at all; wrote ${repo.calls}")
        assertEquals(1, repo.fill(99L).items.size, "the item stays where it is")
        assertTrue(repo.fill(11L).items.isEmpty(), "the destination stays untouched")
    }

    @Test
    fun moveItem_destinationEqualsSource_isANoOpSuccess() = runTest {
        // The item must really BE in "Покупки" for "already in that list" to be a true statement —
        // see moveItem_sameListButItemDoesNotExist_reportsNotFound for the other half.
        val repo = inboxRepo(
            projectTemplate = listOf(templateItem("tpl_p", "Call the bank")),
            projectFill = listOf(fillItem("f_p", "Call the bank", templateItemId = "tpl_p")),
        )
        val dispatcher = buildDispatcher(repo)
        repo.calls.clear()

        // Source id and destination name resolve to different rows here, so aim both at "Покупки".
        val outcome = dispatcher.dispatch(
            ToolCall.MoveItem(
                itemText = "Call the bank",
                toChecklistHint = "Покупки",
                fromChecklistId = 1L,
            ),
        )

        val success = assertIs<DispatchOutcome.Success>(outcome, "got $outcome")
        assertEquals("chat_dispatch_move_same_list", success.messageKey)
        assertEquals(listOf("Call the bank", "Покупки"), success.args)
        assertTrue(repo.calls.isEmpty(), "a same-list move must write nothing; wrote ${repo.calls}")
    }

    @Test
    fun moveItem_sameListButItemDoesNotExist_reportsNotFound() = runTest {
        // REGRESSION: the same-list branch used to return Success BEFORE looking the item up, so a
        // move of something that exists nowhere was reported to the model as "already filed there"
        // and relayed to the user as a fact about their data.
        val repo = inboxRepo()
        val dispatcher = buildDispatcher(repo)
        repo.calls.clear()

        val outcome = dispatcher.dispatch(
            ToolCall.MoveItem(
                itemText = "Pay rent",
                toChecklistHint = "Покупки",
                fromChecklistId = 1L,
            ),
        )

        val notFound = assertIs<DispatchOutcome.NotFound>(outcome, "got $outcome")
        assertEquals("chat_dispatch_item_not_found", notFound.messageKey)
        assertEquals(listOf("Pay rent", "Покупки"), notFound.args)
        assertTrue(repo.calls.isEmpty(), "nothing may be written; wrote ${repo.calls}")
    }

    @Test
    fun moveItem_unknownDestination_reportsItAndLeavesTheSourceIntact() = runTest {
        val repo = inboxRepo(
            inboxTemplate = listOf(templateItem("tpl_i", "Call the bank")),
            inboxFill = listOf(fillItem("f_i", "Call the bank", templateItemId = "tpl_i")),
        )
        val dispatcher = buildDispatcher(repo)
        repo.calls.clear()

        val outcome = dispatcher.dispatch(
            ToolCall.MoveItem(
                itemText = "Call the bank",
                toChecklistHint = "Nowhere",
                fromChecklistId = 9L,
            ),
        )

        val notFound = assertIs<DispatchOutcome.NotFound>(outcome, "got $outcome")
        assertEquals("chat_dispatch_no_checklist_match", notFound.messageKey)
        assertTrue(repo.calls.isEmpty(), "nothing may be written before the destination resolves")
        assertEquals(1, repo.fill(99L).items.size, "the item stays in the Inbox")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Free-tier ceiling — read from Remote Config, never compiled in
    //
    // The chat refuses a checklist the Home screen happily creates: the dispatcher gates on
    // `FREE_CHECKLIST_LIMIT = 4` while GetUserLimitsUseCase (and therefore every other surface)
    // reads `max_checklists_free`, which Remote Config serves as 5. A free user with 4 lists is
    // told to buy premium for the 5th. The refusal never opens the paywall — it comes back as a
    // snackbar — so nothing in the purchase funnel records it; the whole symptom is the bad refusal.
    //
    // Each test pairs TWO Remote Config values on purpose. One value cannot tell a read from a
    // literal: pinning the fake to the live 5 would pass against a constant bumped to 5, which is
    // the wrong fix (the number moves in the console, not in the client). Same argument as
    // feature/create's `observeUserLimits_exposesTheRemoteConfigFreeLimit_notAConstant`.
    //
    // Both call sites are covered because the gate block is duplicated verbatim
    // (handleCreateChecklist and handleCreateChecklistFromAttachment) — fixing one leaves the
    // other refusing.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun createChecklist_freeUserBelowTheRemoteConfigLimit_createsInsteadOfDemandingPremium() = runTest {
        // (a) the shipped symptom: RC serves 5, the user owns 4, the chat must create the 5th.
        val atLiveValue = repoWithProjects(4)
        val liveOutcome = buildDispatcher(
            atLiveValue,
            premium = false,
            remoteConfig = ConfigurableRemoteConfig(maxChecklistsFree = 5L),
        ).dispatch(ToolCall.CreateChecklist(name = "Trip", initialItems = listOf("Passport")))

        assertIs<DispatchOutcome.Success>(
            liveOutcome,
            "max_checklists_free = 5 and the user owns 4 — the 5th list is inside the free tier, got $liveOutcome",
        )
        assertTrue(
            atLiveValue.calls.any { it is RepoCall.AddChecklist && it.name == "Trip" },
            "the checklist must actually be written, not merely reported; calls = ${atLiveValue.calls}",
        )

        // (b) the discriminator: raising the console value must move the chat's ceiling with it.
        // A constant — 4 today, 5 after a "just bump it" fix — cannot follow.
        val atRaisedValue = repoWithProjects(6)
        val raisedOutcome = buildDispatcher(
            atRaisedValue,
            premium = false,
            remoteConfig = ConfigurableRemoteConfig(maxChecklistsFree = 7L),
        ).dispatch(ToolCall.CreateChecklist(name = "Renovation", initialItems = emptyList()))

        assertIs<DispatchOutcome.Success>(
            raisedOutcome,
            "raising max_checklists_free to 7 must let a free user with 6 lists create a 7th, got $raisedOutcome",
        )
        assertTrue(
            atRaisedValue.calls.any { it is RepoCall.AddChecklist && it.name == "Renovation" },
            "the checklist must actually be written; calls = ${atRaisedValue.calls}",
        )
    }

    /**
     * The other half of the same contract: reading the limit must not become "no limit". Free users
     * standing exactly ON the served ceiling still hit the paywall, and nothing is written.
     */
    @Test
    fun createChecklist_freeUserAtTheRemoteConfigLimit_stillRequiresPremium() = runTest {
        val atLiveValue = repoWithProjects(5)
        assertEquals(
            DispatchOutcome.RequiresPremium,
            buildDispatcher(
                atLiveValue,
                premium = false,
                remoteConfig = ConfigurableRemoteConfig(maxChecklistsFree = 5L),
            ).dispatch(ToolCall.CreateChecklist(name = "Sixth", initialItems = emptyList())),
            "5 lists against a served ceiling of 5 is the paywall case",
        )
        assertTrue(atLiveValue.calls.isEmpty(), "a refused create must write nothing; calls = ${atLiveValue.calls}")

        // A LOWERED console value must bite too — this is what a constant bumped to 5 would ignore.
        val atLoweredValue = repoWithProjects(4)
        assertEquals(
            DispatchOutcome.RequiresPremium,
            buildDispatcher(
                atLoweredValue,
                premium = false,
                remoteConfig = ConfigurableRemoteConfig(maxChecklistsFree = 4L),
            ).dispatch(ToolCall.CreateChecklist(name = "Fifth", initialItems = emptyList())),
            "lowering max_checklists_free to 4 must refuse a free user's 5th list",
        )
        assertTrue(atLoweredValue.calls.isEmpty(), "a refused create must write nothing; calls = ${atLoweredValue.calls}")
    }

    @Test
    fun createChecklistFromAttachment_freeUserBelowTheRemoteConfigLimit_createsInsteadOfDemandingPremium() = runTest {
        val atLiveValue = repoWithProjects(4)
        val liveAnalyzer = RecordingAnalyzer()
        val liveOutcome = buildDispatcher(
            atLiveValue,
            premium = false,
            remoteConfig = ConfigurableRemoteConfig(maxChecklistsFree = 5L),
            analyzer = liveAnalyzer,
        ).dispatch(ToolCall.CreateChecklistFromAttachment(attachments = listOf(photoAttachment)))

        val liveSuccess = assertIs<DispatchOutcome.Success>(
            liveOutcome,
            "max_checklists_free = 5 and the user owns 4 — the attachment must become the 5th list, got $liveOutcome",
        )
        assertEquals("chat_dispatch_created_from_attachment", liveSuccess.messageKey)
        assertEquals(1, liveAnalyzer.callCount, "the attachment must actually be analyzed")
        assertTrue(
            atLiveValue.calls.any { it is RepoCall.AddChecklist && it.name == "receipt" },
            "the generated checklist must be written; calls = ${atLiveValue.calls}",
        )

        val atRaisedValue = repoWithProjects(6)
        val raisedOutcome = buildDispatcher(
            atRaisedValue,
            premium = false,
            remoteConfig = ConfigurableRemoteConfig(maxChecklistsFree = 7L),
            analyzer = RecordingAnalyzer(),
        ).dispatch(ToolCall.CreateChecklistFromAttachment(attachments = listOf(photoAttachment)))

        assertIs<DispatchOutcome.Success>(
            raisedOutcome,
            "raising max_checklists_free to 7 must let a free user with 6 lists build a 7th from a file, got $raisedOutcome",
        )
        assertTrue(
            atRaisedValue.calls.any { it is RepoCall.AddChecklist && it.name == "receipt" },
            "the generated checklist must be written; calls = ${atRaisedValue.calls}",
        )
    }

    /**
     * The gate stays, and it stays IN FRONT of the Cloud Function: a paywall shown after the AI
     * call has already burned the user's credits for a checklist they never got.
     */
    @Test
    fun createChecklistFromAttachment_freeUserAtTheRemoteConfigLimit_requiresPremiumWithoutSpendingAnAiCall() = runTest {
        val atLiveValue = repoWithProjects(5)
        val liveAnalyzer = RecordingAnalyzer()
        assertEquals(
            DispatchOutcome.RequiresPremium,
            buildDispatcher(
                atLiveValue,
                premium = false,
                remoteConfig = ConfigurableRemoteConfig(maxChecklistsFree = 5L),
                analyzer = liveAnalyzer,
            ).dispatch(ToolCall.CreateChecklistFromAttachment(attachments = listOf(photoAttachment))),
            "5 lists against a served ceiling of 5 is the paywall case",
        )
        assertEquals(0, liveAnalyzer.callCount, "the free-tier gate must run BEFORE the paid analyze call")
        assertTrue(atLiveValue.calls.isEmpty(), "a refused create must write nothing; calls = ${atLiveValue.calls}")

        val atLoweredValue = repoWithProjects(4)
        val loweredAnalyzer = RecordingAnalyzer()
        assertEquals(
            DispatchOutcome.RequiresPremium,
            buildDispatcher(
                atLoweredValue,
                premium = false,
                remoteConfig = ConfigurableRemoteConfig(maxChecklistsFree = 4L),
                analyzer = loweredAnalyzer,
            ).dispatch(ToolCall.CreateChecklistFromAttachment(attachments = listOf(photoAttachment))),
            "lowering max_checklists_free to 4 must refuse a free user's 5th list",
        )
        assertEquals(0, loweredAnalyzer.callCount, "the free-tier gate must run BEFORE the paid analyze call")
        assertTrue(atLoweredValue.calls.isEmpty(), "a refused create must write nothing; calls = ${atLoweredValue.calls}")
    }
}

/**
 * "Покупки" (id 1, fill 11, empty) PLUS the system Inbox (id 9, fill 99, `isInbox = true`).
 *
 * The fake does not override [ChecklistRepository.projects], so it uses the interface default
 * (`checklists` minus the flagged row) — exactly the filter production uses, which is what makes
 * the id-vs-name asymmetry testable here at all.
 */
private fun inboxRepo(
    inboxTemplate: List<ChecklistItem> = emptyList(),
    inboxFill: List<ChecklistFillItem> = emptyList(),
    projectTemplate: List<ChecklistItem> = emptyList(),
    projectFill: List<ChecklistFillItem> = emptyList(),
) = FakeChecklistRepository(
    seedChecklists = listOf(
        Checklist(id = 1L, name = "Покупки", items = projectTemplate),
        Checklist(id = 9L, name = "Inbox", items = inboxTemplate, isInbox = true),
    ),
    seedFills = listOf(
        ChecklistFill(id = 11L, checklistId = 1L, name = "Покупки", items = projectFill, isDefault = true),
        ChecklistFill(id = 99L, checklistId = 9L, name = "Inbox", items = inboxFill, isDefault = true),
    ),
)
