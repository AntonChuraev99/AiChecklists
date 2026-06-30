package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistNodeType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.SmartDateParser
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.ParsedDateToken
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Repro for "chat-dock add lands in the MIDDLE of the list when folders are enabled" (bug report
 * 2026-06-30). Builds a realistic folders-enabled checklist (~15 nodes: folders + leaves at root,
 * one starred root leaf, a few checked leaves), drives the chat-dock item-create add path, and
 * asserts the new item is LAST in the visible folder-level order.
 *
 * The fake repository RE-EMITS on updateFill / updateChecklistTemplate to faithfully model Room's
 * post-write re-emission, so the assertions cover BOTH the optimistic snapshot AND the reload
 * state-mapping (withSortedItems + buildFolderState) in one run.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistDetailFolderAddOrderTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ReEmittingFakeRepository
    private lateinit var scheduler: NoOpScheduler

    // ── Template (root level): folder, folder, then root leaves; one starred; a couple checked ──
    private val folderA = ChecklistItem(text = "Groceries", type = ChecklistNodeType.FOLDER)
    private val folderB = ChecklistItem(text = "Hardware", type = ChecklistNodeType.FOLDER)
    private val leafA1 = ChecklistItem(text = "Milk", parentId = folderA.id)
    private val leafA2 = ChecklistItem(text = "Bread", parentId = folderA.id)
    private val leafA3 = ChecklistItem(text = "Eggs", parentId = folderA.id)
    private val leafB1 = ChecklistItem(text = "Screws", parentId = folderB.id)
    private val leafB2 = ChecklistItem(text = "Nails", parentId = folderB.id)
    private val rootLeaf1 = ChecklistItem(text = "Call plumber")
    private val rootLeaf2 = ChecklistItem(text = "Pay rent", priority = 1) // starred
    private val rootLeaf3 = ChecklistItem(text = "Book flight")
    private val rootLeaf4 = ChecklistItem(text = "Renew passport")
    private val rootLeaf5 = ChecklistItem(text = "Water plants")

    private val templateItems = listOf(
        folderA, folderB,
        leafA1, leafA2, leafA3, leafB1, leafB2,
        rootLeaf1, rootLeaf2, rootLeaf3, rootLeaf4, rootLeaf5,
    )

    // Fill rows (flat, linked via templateItemId). rootLeaf1 + rootLeaf4 checked. rootLeaf2 starred.
    private val fillFolderA = ChecklistFillItem("Groceries", checked = false, templateItemId = folderA.id)
    private val fillFolderB = ChecklistFillItem("Hardware", checked = false, templateItemId = folderB.id)
    private val fillA1 = ChecklistFillItem("Milk", checked = false, templateItemId = leafA1.id)
    private val fillA2 = ChecklistFillItem("Bread", checked = true, templateItemId = leafA2.id)
    private val fillA3 = ChecklistFillItem("Eggs", checked = false, templateItemId = leafA3.id)
    private val fillB1 = ChecklistFillItem("Screws", checked = false, templateItemId = leafB1.id)
    private val fillB2 = ChecklistFillItem("Nails", checked = false, templateItemId = leafB2.id)
    private val fillRoot1 = ChecklistFillItem("Call plumber", checked = true, templateItemId = rootLeaf1.id)
    private val fillRoot2 = ChecklistFillItem("Pay rent", checked = false, templateItemId = rootLeaf2.id)
        .withPriority(1)
    private val fillRoot3 = ChecklistFillItem("Book flight", checked = false, templateItemId = rootLeaf3.id)
    private val fillRoot4 = ChecklistFillItem("Renew passport", checked = true, templateItemId = rootLeaf4.id)
    private val fillRoot5 = ChecklistFillItem("Water plants", checked = false, templateItemId = rootLeaf5.id)

    private val fillItems = listOf(
        fillFolderA, fillFolderB,
        fillA1, fillA2, fillA3, fillB1, fillB2,
        fillRoot1, fillRoot2, fillRoot3, fillRoot4, fillRoot5,
    )

    private val baseChecklist = Checklist(
        id = 1L,
        name = "Errands",
        items = templateItems,
        foldersEnabled = true,
    )

    private val baseFill = ChecklistFill(
        id = 10L,
        checklistId = 1L,
        name = "",
        items = fillItems,
        createdAt = 0L,
        isDefault = true,
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        scheduler = NoOpScheduler()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ChecklistDetailViewModel {
        val datastore = AppDatastore(
            PreferenceDataStoreFactory.createWithPath {
                "build/test_prefs_folderorder_${Random.nextLong()}.preferences_pb".toPath()
            },
            testDispatcher,
        )
        return ChecklistDetailViewModel(
            checklistId = 1L,
            currentFolderId = null, // viewing checklist ROOT
            repository = repository,
            navigator = NoOpNavigator(),
            getUserLimitsUseCase = GetUserLimitsUseCase(
                FakeRemoteConfig(),
                repository,
                FakePaywall(),
                FakeUserData(),
            ),
            analyticsTracker = NoOpAnalytics(),
            reminderScheduler = scheduler,
            datastore = datastore,
            smartDateParser = NullParser(),
            attachmentStorage = NoOpAttachmentStorage(),
            calendarEventLauncher = FakeCalendarEventLauncher(),
            logger = NoOpAppLogger,
        )
    }

    private fun contentState(vm: ChecklistDetailViewModel): ChecklistDetailState.Content =
        vm.screenState.value as ChecklistDetailState.Content

    /** Maps the levelNodes list to readable labels for failure diagnostics. */
    private fun describe(state: ChecklistDetailState.Content): String {
        val byId = state.defaultFill?.items?.associateBy { it.id }.orEmpty()
        return state.levelNodes.joinToString(prefix = "[", postfix = "]") { node ->
            when (node) {
                is LevelNode.Folder -> "F:${node.model.name}"
                is LevelNode.Leaf -> "L:${byId[node.fillItemId]?.text ?: node.fillItemId}"
            }
        }
    }

    @Test
    fun chatDockAdd_foldersEnabled_separateCompletedOff_newItemIsLastInLevelNodes() = runTest {
        repository = ReEmittingFakeRepository(
            baseChecklist.copy(separateCompleted = false),
            baseFill,
        )
        val vm = createViewModel()

        // Drive the chat-dock item-create add path (plain text, no smart token).
        vm.onIntent(ChecklistDetailIntent.OnDockItemCreateOpened())
        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged("Buy stamps"))
        advanceTimeBy(201)
        vm.onIntent(ChecklistDetailIntent.OnAddItemWithParse)

        val state = contentState(vm)
        val newId = repository.lastUpdatedFill?.items?.last()?.id
            ?: error("fill must have been updated with the new item")

        val lastNode = state.levelNodes.lastOrNull()
        assertTrue(
            lastNode is LevelNode.Leaf && lastNode.fillItemId == newId,
            "new item must be LAST in levelNodes (root level). order=${describe(state)} newId=$newId",
        )
    }

    @Test
    fun chatDockAdd_foldersEnabled_separateCompletedOn_newItemIsLastAmongUnchecked() = runTest {
        // With separateCompleted ON the screen splits CHECKED leaves into a bottom section; the new
        // (unchecked) item should still be the last UNCHECKED leaf node in levelNodes.
        repository = ReEmittingFakeRepository(
            baseChecklist.copy(separateCompleted = true),
            baseFill,
        )
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnDockItemCreateOpened())
        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged("Buy stamps"))
        advanceTimeBy(201)
        vm.onIntent(ChecklistDetailIntent.OnAddItemWithParse)

        val state = contentState(vm)
        val newId = repository.lastUpdatedFill?.items?.last()?.id
            ?: error("fill must have been updated with the new item")
        val checkedIds = state.defaultFill?.items?.filter { it.checked }?.map { it.id }?.toSet().orEmpty()

        val lastUnchecked = state.levelNodes.lastOrNull { node ->
            node is LevelNode.Leaf && node.fillItemId !in checkedIds
        }
        assertEquals(
            newId,
            (lastUnchecked as? LevelNode.Leaf)?.fillItemId,
            "new item must be the LAST unchecked leaf in levelNodes. order=${describe(state)} newId=$newId",
        )
    }

    @Test
    fun chatDockAdd_optimisticState_defaultFillBacksEveryLevelNodeLeaf() = runTest {
        // PURE optimistic frame: the fake does NOT re-emit, so we observe the state the VM writes
        // synchronously in addItemWithParse (before the Room round-trip). Regression guard for the
        // desync where levelNodes/visibleFillItemIds referenced the new leaf but defaultFill did not
        // yet contain its backing row → the new row couldn't render and the auto-scroll saw a stale
        // item count.
        repository = ReEmittingFakeRepository(
            baseChecklist.copy(separateCompleted = false),
            baseFill,
            reEmitOnWrite = false,
        )
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnDockItemCreateOpened())
        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged("Buy stamps"))
        advanceTimeBy(201)
        vm.onIntent(ChecklistDetailIntent.OnAddItemWithParse)

        val state = contentState(vm)
        val fillIds = state.defaultFill?.items?.map { it.id }?.toSet().orEmpty()
        val leafIds = state.levelNodes.filterIsInstance<LevelNode.Leaf>().map { it.fillItemId }

        leafIds.forEach { id ->
            assertTrue(
                id in fillIds,
                "levelNodes leaf $id has no backing row in defaultFill (optimistic desync). " +
                    "order=${describe(state)}",
            )
        }
        // And the new item is the last leaf, backed by a real row.
        val newId = repository.lastUpdatedFill?.items?.last()?.id ?: error("fill update expected")
        assertTrue(newId in fillIds, "freshly added item $newId must be present in optimistic defaultFill")
    }

    // ─── Delete / undo optimistic-desync (Bug 7) ────────────────────────────────

    /** Asserts no LevelNode.Leaf references an id absent from defaultFill (no stale/missing rows). */
    private fun assertEveryLeafBacked(state: ChecklistDetailState.Content) {
        val fillIds = state.defaultFill?.items?.map { it.id }?.toSet().orEmpty()
        state.levelNodes.filterIsInstance<LevelNode.Leaf>().forEach { leaf ->
            assertTrue(
                leaf.fillItemId in fillIds,
                "levelNodes leaf ${leaf.fillItemId} has no backing row in defaultFill. order=${describe(state)}",
            )
        }
    }

    /** Asserts [id] is gone from defaultFill, levelNodes and visibleFillItemIds. */
    private fun assertItemAbsent(state: ChecklistDetailState.Content, id: String) {
        assertTrue(state.defaultFill?.items?.none { it.id == id } == true, "id $id still in defaultFill")
        assertTrue(
            state.levelNodes.filterIsInstance<LevelNode.Leaf>().none { it.fillItemId == id },
            "id $id still in levelNodes. order=${describe(state)}",
        )
        assertTrue(state.visibleFillItemIds?.contains(id) != true, "id $id still in visibleFillItemIds")
    }

    @Test
    fun swipeDelete_optimisticFrame_removesItemFromAllDerivedState() = runTest {
        repository = ReEmittingFakeRepository(baseChecklist.copy(separateCompleted = false), baseFill, reEmitOnWrite = false)
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnSwipeDeleteItem(fillRoot3.id))

        val state = contentState(vm)
        assertItemAbsent(state, fillRoot3.id)
        assertEveryLeafBacked(state)
    }

    @Test
    fun sheetDelete_optimisticFrame_removesItemFromAllDerivedState() = runTest {
        repository = ReEmittingFakeRepository(baseChecklist.copy(separateCompleted = false), baseFill, reEmitOnWrite = false)
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnDeleteItemFromSheet(fillA3.id))

        val state = contentState(vm)
        assertItemAbsent(state, fillA3.id)
        assertEveryLeafBacked(state)
    }

    @Test
    fun deleteThenAdd_userRepro_newItemLastAndNoStaleRows() = runTest {
        // User repro: delete an OLDER item positioned ABOVE others, then add. Without re-emit between
        // the two actions the add reads the post-delete optimistic state — which must already be free
        // of the deleted row, else the add re-introduces it and corrupts order.
        repository = ReEmittingFakeRepository(baseChecklist.copy(separateCompleted = false), baseFill, reEmitOnWrite = false)
        val vm = createViewModel()

        // Delete "Book flight" (rootLeaf3) — an unchecked root leaf with siblings below it.
        vm.onIntent(ChecklistDetailIntent.OnSwipeDeleteItem(fillRoot3.id))
        // Then add via the chat dock — reads state.defaultFill (must be the post-delete fill).
        vm.onIntent(ChecklistDetailIntent.OnDockItemCreateOpened())
        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged("Buy stamps"))
        advanceTimeBy(201)
        vm.onIntent(ChecklistDetailIntent.OnAddItemWithParse)

        val state = contentState(vm)
        val newId = repository.lastUpdatedFill?.items?.last()?.id ?: error("fill update expected")

        assertItemAbsent(state, fillRoot3.id)
        assertEveryLeafBacked(state)
        val lastNode = state.levelNodes.lastOrNull()
        assertTrue(
            lastNode is LevelNode.Leaf && lastNode.fillItemId == newId,
            "new item must be LAST in levelNodes after delete-then-add. order=${describe(state)} newId=$newId",
        )
    }

    @Test
    fun undoDelete_optimisticFrame_restoresItemIntoDerivedState() = runTest {
        repository = ReEmittingFakeRepository(baseChecklist.copy(separateCompleted = false), baseFill, reEmitOnWrite = false)
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnSwipeDeleteItem(fillRoot3.id))
        assertItemAbsent(contentState(vm), fillRoot3.id) // sanity: gone after delete

        vm.onIntent(ChecklistDetailIntent.OnUndoDeleteItem)

        val state = contentState(vm)
        assertTrue(state.defaultFill?.items?.any { it.id == fillRoot3.id } == true, "restored row missing from defaultFill")
        assertTrue(
            state.levelNodes.filterIsInstance<LevelNode.Leaf>().any { it.fillItemId == fillRoot3.id },
            "restored row missing from levelNodes. order=${describe(state)}",
        )
        assertEveryLeafBacked(state)
    }

    // ─── Bug 2 REAL root: UNLINKED fill rows bottom-dumped in folder mode ────────
    //
    // The user's real checklist has fill rows with templateItemId == null (legacy data / AI-chat
    // adds that didn't set the link). BEFORE the buildFolderState fix, the leaf loop skipped those
    // template leaves (no id link) and the root legacy-rows pass DUMPED the unlinked rows at the
    // BOTTOM — so a freshly added (linked) item rendered at the end of the LINKED section, i.e. ABOVE
    // the bottom-dumped unlinked rows = "lands in the middle". The fix resolves a leaf's row by
    // templateItemId first, then by a text match against unlinked rows, so unlinked rows render at
    // their TEMPLATE position; dedup guards stop the legacy pass re-appending a text-matched row.
    //
    // RED before the fix: assertion 1 saw [A, Folder, D, E, B, C] (B/C dumped last) instead of the
    // template order [A, B, C, Folder, D, E]; assertion 2 saw the new item BEFORE B/C, not last.

    /** Real-world fixture: foldersEnabled, root level, with UNLINKED leaf fill rows at positions 2-3. */
    private class UnlinkedFixture {
        val leafA = ChecklistItem(text = "Task A")
        val leafB = ChecklistItem(text = "Task B")
        val leafC = ChecklistItem(text = "Task C")
        val folder1 = ChecklistItem(text = "Folder 1", type = ChecklistNodeType.FOLDER)
        val leafD = ChecklistItem(text = "Task D")
        val leafE = ChecklistItem(text = "Task E")
        val leafF1 = ChecklistItem(text = "Inside 1", parentId = folder1.id)
        val leafF2 = ChecklistItem(text = "Inside 2", parentId = folder1.id)

        // Template (source of truth) root order: A, B, C, Folder1, D, E.
        val template = Checklist(
            id = 1L,
            name = "Real",
            foldersEnabled = true,
            separateCompleted = false,
            items = listOf(leafA, leafB, leafC, folder1, leafD, leafE, leafF1, leafF2),
        )

        // Fill rows: A/D/E/F1/F2 + folder LINKED; B and C UNLINKED (templateItemId == null, text only).
        val fillA = ChecklistFillItem("Task A", checked = false, templateItemId = leafA.id)
        val fillB = ChecklistFillItem("Task B", checked = false) // UNLINKED
        val fillC = ChecklistFillItem("Task C", checked = false) // UNLINKED
        val fillFolder1 = ChecklistFillItem("Folder 1", checked = false, templateItemId = folder1.id)
        val fillD = ChecklistFillItem("Task D", checked = false, templateItemId = leafD.id)
        val fillE = ChecklistFillItem("Task E", checked = false, templateItemId = leafE.id)
        val fillF1 = ChecklistFillItem("Inside 1", checked = false, templateItemId = leafF1.id)
        val fillF2 = ChecklistFillItem("Inside 2", checked = false, templateItemId = leafF2.id)

        val fill = ChecklistFill(
            id = 10L,
            checklistId = 1L,
            name = "",
            createdAt = 0L,
            isDefault = true,
            items = listOf(fillA, fillB, fillC, fillFolder1, fillD, fillE, fillF1, fillF2),
        )
    }

    /** Maps levelNodes to ids: Folder → model.id, Leaf → fillItemId. */
    private fun nodeIds(state: ChecklistDetailState.Content): List<String> =
        state.levelNodes.map { node ->
            when (node) {
                is LevelNode.Folder -> node.model.id
                is LevelNode.Leaf -> node.fillItemId
            }
        }

    @Test
    fun foldersMode_unlinkedFillRows_renderAtTemplatePosition_notBottom() = runTest {
        val fx = UnlinkedFixture()
        repository = ReEmittingFakeRepository(fx.template, fx.fill)
        val vm = createViewModel()

        val ids = nodeIds(contentState(vm))
        // Unlinked B/C render at their TEMPLATE position (between A and Folder1), NOT dumped last.
        assertEquals(
            listOf(fx.fillA.id, fx.fillB.id, fx.fillC.id, fx.folder1.id, fx.fillD.id, fx.fillE.id),
            ids,
            "levelNodes must follow template root order with unlinked rows in place",
        )
        // Dedup guards: no fillItemId appears twice.
        assertEquals(ids.size, ids.toSet().size, "no duplicate node ids: $ids")
    }

    @Test
    fun foldersMode_dockAddWithUnlinkedRows_newItemIsLast_notMiddle() = runTest {
        val fx = UnlinkedFixture()
        repository = ReEmittingFakeRepository(fx.template, fx.fill)
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnDockItemCreateOpened())
        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged("New Task"))
        advanceTimeBy(201)
        vm.onIntent(ChecklistDetailIntent.OnAddItemWithParse)

        val state = contentState(vm)
        val newId = repository.lastUpdatedFill?.items?.last()?.id ?: error("fill update expected")
        val ids = nodeIds(state)

        // The new (linked) item must be the LAST node — NOT above the unlinked rows ("middle").
        assertEquals(newId, ids.last(), "new item must be LAST in levelNodes despite unlinked rows. order=$ids")
        // The unlinked rows must stay ABOVE the new item (at their template position), not after it.
        assertTrue(
            ids.indexOf(fx.fillB.id) in 0 until ids.indexOf(newId) &&
                ids.indexOf(fx.fillC.id) in 0 until ids.indexOf(newId),
            "unlinked rows B/C must render ABOVE the newly added item. order=$ids",
        )
        // Dedup guards still hold after the add.
        assertEquals(ids.size, ids.toSet().size, "no duplicate node ids after add: $ids")
    }

    // ─── Test doubles ──────────────────────────────────────────────────────────

    /**
     * Fake that RE-EMITS the persisted checklist + fill on every write, modelling Room's reactive
     * re-emission. Both flows are reactive MutableStateFlows (unlike one-shot flowOf fakes), so the
     * VM's combine() reload path runs after each add.
     */
    private class ReEmittingFakeRepository(
        initialChecklist: Checklist,
        initialFill: ChecklistFill,
        /** When false, writes only record (no re-emit) so the OPTIMISTIC VM state stays observable. */
        private val reEmitOnWrite: Boolean = true,
    ) : ChecklistRepository {
        val checklistFlow = MutableStateFlow<Checklist?>(initialChecklist)
        val defaultFillFlow = MutableStateFlow<ChecklistFill?>(initialFill)

        var lastUpdatedFill: ChecklistFill? = null
        var lastUpdatedTemplate: Checklist? = null

        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())
        override suspend fun addChecklist(checklist: Checklist): Long = 1L
        override suspend fun updateChecklist(checklist: Checklist) {}
        override suspend fun updateChecklistTemplate(checklist: Checklist) {
            lastUpdatedTemplate = checklist
            // Room re-emits the template after a write.
            if (reEmitOnWrite) checklistFlow.value = checklistFlow.value?.copy(items = checklist.items)
        }
        override suspend fun deleteChecklist(checklist: Checklist) {}
        override suspend fun getChecklistById(id: Long): Checklist? = checklistFlow.value
        override fun observeChecklistById(id: Long): Flow<Checklist?> = checklistFlow
        override suspend fun reorderChecklists(orderedIds: List<Long>) {}
        override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setFoldersEnabled(checklistId: Long, value: Boolean) {}
        override suspend fun setReminder(checklistId: Long, reminderAt: Long?) {}
        override suspend fun countActiveReminders(): Int = 0
        override suspend fun getActiveReminders(): List<ChecklistReminderInfo> = emptyList()
        override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? = defaultFillFlow.value
        override suspend fun getAllItemRemindersForRescheduling(): List<ItemReminderInfo> = emptyList()
        override suspend fun setRepeatSchedule(checklistId: Long, rule: ReminderRepeatRule, timeOfDayMinutes: Int, firstTriggerAt: Long) {}
        override suspend fun advanceRepeatSchedule(checklistId: Long, nextAt: Long?, newCount: Int) {}
        override suspend fun clearRepeatSchedule(checklistId: Long) {}
        override suspend fun resetDefaultFillChecks(checklistId: Long) {}
        override suspend fun countActiveRepeatSchedules(): Int = 0
        override suspend fun getActiveRepeatSchedules(): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getTotalAdditionalFillCount(): Int = 0
        override suspend fun getWeeklyChecklistCount(): Int = 0
        override val weeklyChecklistCount: Flow<Int> = flowOf(0)
        override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = defaultFillFlow
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override suspend fun getFillById(id: Long): ChecklistFill? = null
        override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun addFill(fill: ChecklistFill): Long = 1L
        override suspend fun updateFill(fill: ChecklistFill) {
            lastUpdatedFill = fill
            // Room re-emits the fill after a write.
            if (reEmitOnWrite) defaultFillFlow.value = fill
        }
        override suspend fun deleteFill(fill: ChecklistFill) {}
        override suspend fun reorderItems(fill: ChecklistFill, checklist: Checklist) {}
        override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<TodayReminderInfo>> = flowOf(emptyList())
        override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<TodayReminderInfo> = emptyList()
        override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addAttachment(fillId: Long, itemId: String, attachment: com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment) = Unit
        override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) = Unit
    }

    private class NullParser : SmartDateParser {
        override fun parse(input: String, now: Long, timeZone: TimeZone): ParsedDateToken? = null
    }

    private class NoOpScheduler : ChecklistReminderScheduler {
        override fun scheduleReminder(checklistId: Long, triggerAtMillis: Long) {}
        override fun cancelReminder(checklistId: Long) {}
        override suspend fun rescheduleAllActiveReminders() {}
        override fun scheduleRepeat(checklistId: Long, triggerAtMillis: Long) {}
        override fun cancelRepeat(checklistId: Long) {}
        override suspend fun rescheduleAllActiveRepeats() {}
        override fun scheduleItemReminder(checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long) {}
        override fun cancelItemReminder(checklistId: Long, fillId: Long, itemId: String) {}
        override fun scheduleItemRepeat(checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long) {}
        override fun cancelItemRepeat(checklistId: Long, fillId: Long, itemId: String) {}
    }

    private class NoOpNavigator : AppNavigator {
        override val events: SharedFlow<AppNavEvent> = MutableSharedFlow()
        override val backStack: NavBackStack<NavKey> = NavBackStack()
        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
        override fun onBack() {}
        override fun navigateToOnboarding() {}
        override fun navigateToInteractiveOnboarding() {}
        override fun navigateToWelcomeOnboarding() {}
        override fun navigateToMainScreen(clearBackStack: Boolean) {}
        override fun navigateToDebugMenu() {}
        override fun navigateToStoreScreenshot() {}
        override fun navigateToCreateChecklistScreen(templateId: Int?, initialText: String?) {}
        override fun navigateToEditChecklist(checklistId: Long) {}
        override fun navigateToTemplatesScreen() {}
        override fun navigateToTemplatePreview(templateId: String) {}
        override fun navigateToAnalyzeScreen(checklistId: Long?, fillDefault: Boolean, initialText: String?, autoAnalyze: Boolean) {}
        override fun navigateToAnalyzeResultPreview() {}
        override fun navigateToChecklistDetail(checklistId: Long, focusItemId: String?, clearBackStack: Boolean) {}
        override fun navigateToFillDetail(fillId: Long, clearBackStack: Boolean) {}
        override fun navigateToFillsList(checklistId: Long) {}
        override fun navigateToPaywall(source: String) {}
        override fun navigateToPaywallVariant(source: String, forceVariant: String) {}
        override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) {}
        override fun navigateToShareChecklist(checklistId: Long) {}
        override fun navigateToUpdateFeed() {}
        override fun navigateToSettings() {}
        override fun navigateToToday() {}
        override fun navigateToCalendar() {}
        override fun navigateToAiChat() {}
        override fun navigateToScreenCatalog() {}
        override fun navigateToOnboardings() {}
        override fun navigateToAddToChecklistPicker(text: String, purpose: com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose) {}
    }

    private class FakePaywall : PaywallRepository {
        override val subscriptionStatus: Flow<SubscriptionStatus> = flowOf(SubscriptionStatus.FREE)
        override suspend fun getOfferings(offeringId: String): Result<PaywallOffering?> = Result.success(null)
        override suspend fun purchase(packageId: String): PurchaseResult = PurchaseResult.Error("stub")
        override suspend fun restorePurchases(): RestoreResult = RestoreResult.Error("stub")
        override suspend fun refreshSubscriptionStatus() {}
        override fun isConfigured(): Boolean = true
        override suspend fun logIn(appUserId: String): Result<LoginResult> = Result.failure(NotImplementedError())
        override suspend fun logOut(): Result<SubscriptionStatus> = Result.failure(NotImplementedError())
    }

    private class FakeUserData : UserDataRepository {
        private val userData = UserData(userId = "test", isPremium = false)
        private val flow = MutableStateFlow(userData)
        override fun getUserDataFlow(): StateFlow<UserData> = flow
        override suspend fun getUserData(): UserData = userData
        override suspend fun update(userData: UserData) {}
        override suspend fun ensureUserRegistered(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = userData, isNewUser = false))
        override suspend fun syncWithServer(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = userData, isNewUser = false))
        override suspend fun isPaywallLinked(): Boolean = false
        override suspend fun setPaywallLinked(linked: Boolean) {}
        override suspend fun restoreCreditsAfterPurchase(): Result<Int> = Result.success(0)
        override suspend fun getFirstLaunchAtMillis(): Long = 0L
    }

    private class FakeRemoteConfig : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getString(key: String, defaultValue: String): String = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
    }

    private class NoOpAnalytics : AnalyticsTracker {
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {}
    }

    private class NoOpAttachmentStorage : com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort {
        override suspend fun storeAttachment(sourcePath: String, fillId: Long, itemId: String, attachmentId: String, originalFileName: String): String? = null
        override suspend fun deleteAttachment(path: String) {}
        override suspend fun deleteAttachmentsFor(fillId: Long, itemId: String) {}
        override suspend fun deleteAttachmentsForFill(fillId: Long) {}
        override suspend fun probeImage(path: String, mimeType: String?): Pair<Int?, Int?> = null to null
        override suspend fun sizeOf(path: String): Long = 0L
    }
}
