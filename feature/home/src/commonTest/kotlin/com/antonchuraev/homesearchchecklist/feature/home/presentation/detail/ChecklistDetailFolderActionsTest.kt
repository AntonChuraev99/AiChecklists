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
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for Phase 4 folder node actions in [ChecklistDetailViewModel]: move (folder + leaf),
 * rename, and cascading delete (with per-item alarm cancellation).
 *
 * All mutations must go through the existing repository surface
 * ([ChecklistRepository.updateChecklistTemplate] + [ChecklistRepository.updateFill]); these
 * tests assert on [FakeChecklistRepository.lastUpdatedTemplate] / [lastUpdatedFill] and on the
 * [FakeReminderScheduler] cancellation lists.
 *
 * Tree shape used by most tests (folders enabled):
 *
 *   root
 *   ├── folderA            (FOLDER)
 *   │   ├── leafA1         (ITEM, linked fill, has one-shot reminder)
 *   │   └── folderAChild   (FOLDER)
 *   │       └── leafAC1    (ITEM, linked fill, no reminder)
 *   ├── folderB            (FOLDER)
 *   └── leafRoot           (ITEM, linked fill, no reminder)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistDetailFolderActionsTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeChecklistRepository
    private lateinit var scheduler: FakeReminderScheduler
    private lateinit var navigator: FakeAppNavigator

    // ── Template nodes ──
    private val folderA = ChecklistItem(text = "Folder A", type = ChecklistNodeType.FOLDER)
    private val folderAChild = ChecklistItem(
        text = "Folder A Child",
        type = ChecklistNodeType.FOLDER,
        parentId = folderA.id,
    )
    private val folderB = ChecklistItem(text = "Folder B", type = ChecklistNodeType.FOLDER)
    private val leafA1 = ChecklistItem(text = "Leaf A1", parentId = folderA.id)
    private val leafAC1 = ChecklistItem(text = "Leaf AC1", parentId = folderAChild.id)
    private val leafRoot = ChecklistItem(text = "Leaf Root")

    private val templateItems = listOf(folderA, folderAChild, folderB, leafA1, leafAC1, leafRoot)

    // ── Fill rows (flat, linked back via templateItemId) ──
    // Folder placeholder rows + leaf rows. leafA1's fill row carries an active one-shot reminder.
    private val fillFolderA = ChecklistFillItem("Folder A", checked = false, templateItemId = folderA.id)
    private val fillFolderAChild = ChecklistFillItem("Folder A Child", checked = false, templateItemId = folderAChild.id)
    private val fillFolderB = ChecklistFillItem("Folder B", checked = false, templateItemId = folderB.id)
    private val fillLeafA1 = ChecklistFillItem("Leaf A1", checked = false, templateItemId = leafA1.id)
        .withReminderAt(System.currentTimeMillis() + 3_600_000L)
    private val fillLeafAC1 = ChecklistFillItem("Leaf AC1", checked = false, templateItemId = leafAC1.id)
    private val fillLeafRoot = ChecklistFillItem("Leaf Root", checked = false, templateItemId = leafRoot.id)

    private val testChecklist = Checklist(
        id = 1L,
        name = "Test",
        items = templateItems,
        foldersEnabled = true,
    )

    private val testFill = ChecklistFill(
        id = 10L,
        checklistId = 1L,
        name = "",
        items = listOf(fillFolderA, fillFolderAChild, fillFolderB, fillLeafA1, fillLeafAC1, fillLeafRoot),
        createdAt = 0L,
        isDefault = true,
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeChecklistRepository().apply {
            storedChecklist = testChecklist
            defaultFillFlow.value = testFill
        }
        scheduler = FakeReminderScheduler()
        navigator = FakeAppNavigator()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** [folderId] = the drill-down level this VM is scoped to (null = checklist root). */
    private fun createViewModel(folderId: String? = null): ChecklistDetailViewModel {
        val datastore = AppDatastore(
            PreferenceDataStoreFactory.createWithPath {
                "build/test_prefs_folder_actions_${Random.nextLong()}.preferences_pb".toPath()
            },
            testDispatcher,
        )
        return ChecklistDetailViewModel(
            checklistId = 1L,
            currentFolderId = folderId,
            repository = repository,
            navigator = navigator,
            getUserLimitsUseCase = GetUserLimitsUseCase(
                FakeRemoteConfigProvider(),
                repository,
                FakePaywallRepository(),
                FakeUserDataRepository(),
            ),
            analyticsTracker = FakeAnalyticsTracker(),
            reminderScheduler = scheduler,
            datastore = datastore,
            smartDateParser = FakeSmartDateParser(),
            attachmentStorage = FakeAttachmentStorage(),
        )
    }

    private fun contentState(vm: ChecklistDetailViewModel): ChecklistDetailState.Content =
        vm.screenState.value as ChecklistDetailState.Content

    // ── Move ──────────────────────────────────────────────────────────────

    @Test
    fun moveNodeToFolder_leaf_changesParentId_persistsTemplate() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnMoveNodeToFolder(leafRoot.id, folderB.id))

        val saved = repository.lastUpdatedTemplate
        assertNotNull(saved, "template must be persisted on move")
        val moved = saved.items.firstOrNull { it.id == leafRoot.id }
        assertNotNull(moved)
        assertEquals(folderB.id, moved.parentId)
    }

    @Test
    fun moveNodeToFolder_folder_changesParentId_persistsTemplate() = runTest {
        val vm = createViewModel()
        // Move folderB under folderA (legal — folderB has no descendants).
        vm.onIntent(ChecklistDetailIntent.OnMoveNodeToFolder(folderB.id, folderA.id))

        val saved = repository.lastUpdatedTemplate
        assertNotNull(saved)
        assertEquals(folderA.id, saved.items.firstOrNull { it.id == folderB.id }?.parentId)
    }

    @Test
    fun moveNodeToFolder_toRoot_setsParentNull() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnMoveNodeToFolder(leafA1.id, null))

        val saved = repository.lastUpdatedTemplate
        assertNotNull(saved)
        assertNull(saved.items.firstOrNull { it.id == leafA1.id }?.parentId)
    }

    @Test
    fun moveNodeToFolder_intoItself_isBlocked_noWrite() = runTest {
        val vm = createViewModel()
        // Moving folderA under itself would create a cycle → canMove == false.
        vm.onIntent(ChecklistDetailIntent.OnMoveNodeToFolder(folderA.id, folderA.id))

        assertNull(repository.lastUpdatedTemplate, "illegal move must not persist")
    }

    @Test
    fun moveNodeToFolder_intoOwnDescendant_isBlocked_noWrite() = runTest {
        val vm = createViewModel()
        // Moving folderA under folderAChild (its own descendant) → cycle → blocked.
        vm.onIntent(ChecklistDetailIntent.OnMoveNodeToFolder(folderA.id, folderAChild.id))

        assertNull(repository.lastUpdatedTemplate, "moving into a descendant must not persist")
    }

    @Test
    fun moveNodeToFolder_toCurrentParent_isNoOp_noWrite() = runTest {
        val vm = createViewModel()
        // leafA1 already lives in folderA → no-op move.
        vm.onIntent(ChecklistDetailIntent.OnMoveNodeToFolder(leafA1.id, folderA.id))

        assertNull(repository.lastUpdatedTemplate, "no-op move must not persist")
    }

    @Test
    fun openMoveSheet_buildsTargets_disablesSelfAndDescendants() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnMoveNodeRequested(folderA.id))

        val state = contentState(vm)
        assertEquals(folderA.id, state.moveSheetForNodeId)

        val byId = state.moveTargets.associateBy { it.id }
        // Root row exists and is enabled (folderA currently lives at root? No — folderA.parentId is
        // null, so root IS its current parent → root row is current, hence disabled/no-op).
        val rootRow = byId[null]
        assertNotNull(rootRow)
        assertTrue(rootRow.isCurrentParent, "folderA lives at root → root is its current parent")
        assertFalse(rootRow.enabled)

        // folderA itself must be present but disabled (cycle).
        assertFalse(byId[folderA.id]?.enabled ?: true, "self must be disabled")
        // folderAChild is a descendant of folderA → disabled.
        assertFalse(byId[folderAChild.id]?.enabled ?: true, "descendant must be disabled")
        // folderB is unrelated → enabled.
        assertTrue(byId[folderB.id]?.enabled ?: false, "unrelated folder must be enabled")
    }

    @Test
    fun openMoveSheet_forLeaf_flagsCurrentParent() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnMoveNodeRequested(leafA1.id))

        val state = contentState(vm)
        val byId = state.moveTargets.associateBy { it.id }
        // leafA1 lives in folderA → that row is the current parent, disabled.
        assertTrue(byId[folderA.id]?.isCurrentParent ?: false)
        assertFalse(byId[folderA.id]?.enabled ?: true)
        // A leaf has no descendants → every other folder is a legal target.
        assertTrue(byId[folderB.id]?.enabled ?: false)
        assertTrue(byId[folderAChild.id]?.enabled ?: false)
    }

    @Test
    fun moveNodeToFolder_closesSheets() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnMoveNodeRequested(leafRoot.id))
        assertNotNull(contentState(vm).moveSheetForNodeId)

        vm.onIntent(ChecklistDetailIntent.OnMoveNodeToFolder(leafRoot.id, folderB.id))

        val state = contentState(vm)
        assertNull(state.moveSheetForNodeId)
        assertTrue(state.moveTargets.isEmpty())
        assertNull(state.folderActionsSheetFor)
    }

    // ── Reorder (off-level data-loss regression) ────────────────────────────

    @Test
    fun finalizeReorder_atRoot_preservesFoldersAndNestedItems() = runTest {
        // Root shows only leafRoot in the reorderable list (folders render separately; nested leaves
        // live inside them). Before the fix, finalizeReorder rebuilt the whole fill/template from
        // just that visible id → every folder + nested item was silently deleted and synced.
        val vm = createViewModel(folderId = null)
        vm.onIntent(ChecklistDetailIntent.OnFinalizeReorder(listOf(fillLeafRoot.id)))

        val savedFill = repository.lastUpdatedFill
        assertNotNull(savedFill, "fill must persist")
        assertEquals(
            testFill.items.map { it.id }.toSet(),
            savedFill.items.map { it.id }.toSet(),
            "no fill row may be dropped by a single-level reorder",
        )

        val savedTemplate = repository.lastUpdatedTemplate
        assertNotNull(savedTemplate, "template must persist")
        assertEquals(
            templateItems.map { it.id }.toSet(),
            savedTemplate.items.map { it.id }.toSet(),
            "no template node (folder or nested item) may be dropped",
        )
    }

    @Test
    fun finalizeReorder_insideFolder_preservesOtherLevels() = runTest {
        // Standing inside folderA, only leafA1 is visible. A reorder here must not touch sibling
        // folders, the folder containers, root items, or the other folder's subtree.
        val vm = createViewModel(folderId = folderA.id)
        vm.onIntent(ChecklistDetailIntent.OnFinalizeReorder(listOf(fillLeafA1.id)))

        val savedFill = repository.lastUpdatedFill
        assertNotNull(savedFill)
        assertEquals(testFill.items.map { it.id }.toSet(), savedFill.items.map { it.id }.toSet())

        val savedTemplate = repository.lastUpdatedTemplate
        assertNotNull(savedTemplate)
        assertEquals(templateItems.map { it.id }.toSet(), savedTemplate.items.map { it.id }.toSet())
    }

    @Test
    fun finalizeReorder_reordersVisibleSlice_keepsOffLevelInPlace() = runTest {
        // Two leaves directly under folderA so the reorder actually changes order. Off-level rows
        // (folderB, leafRoot, folderAChild subtree) must survive AND keep their slots.
        val leafA2 = ChecklistItem(text = "Leaf A2", parentId = folderA.id)
        val fillLeafA2 = ChecklistFillItem("Leaf A2", checked = false, templateItemId = leafA2.id)
        val customFill = testFill.copy(items = testFill.items + fillLeafA2)
        repository.storedChecklist = testChecklist.copy(items = templateItems + leafA2)
        repository.defaultFillFlow.value = customFill

        val vm = createViewModel(folderId = folderA.id)
        // Visible inside folderA: leafA1, leafA2 → reverse them.
        vm.onIntent(ChecklistDetailIntent.OnFinalizeReorder(listOf(fillLeafA2.id, fillLeafA1.id)))

        val savedFill = repository.lastUpdatedFill
        assertNotNull(savedFill)
        assertEquals(
            customFill.items.map { it.id }.toSet(),
            savedFill.items.map { it.id }.toSet(),
            "reorder must not drop any off-level row",
        )
        val a1Index = savedFill.items.indexOfFirst { it.id == fillLeafA1.id }
        val a2Index = savedFill.items.indexOfFirst { it.id == fillLeafA2.id }
        assertTrue(a2Index < a1Index, "the visible slice must actually be reordered (A2 before A1)")
    }

    // ── Rename ────────────────────────────────────────────────────────────

    @Test
    fun confirmFolderRename_updatesTemplateAndFillText() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRenameFolder(folderA.id))
        assertEquals(folderA.id, contentState(vm).folderRenameForId)

        vm.onIntent(ChecklistDetailIntent.OnFolderRenameDraftChange("Renamed A"))
        vm.onIntent(ChecklistDetailIntent.OnConfirmRenameFolder)

        // Template node text updated
        val savedTemplate = repository.lastUpdatedTemplate
        assertNotNull(savedTemplate)
        assertEquals("Renamed A", savedTemplate.items.firstOrNull { it.id == folderA.id }?.text)

        // Linked fill row text updated (by templateItemId)
        val savedFill = repository.lastUpdatedFill
        assertNotNull(savedFill)
        assertEquals("Renamed A", savedFill.items.firstOrNull { it.templateItemId == folderA.id }?.text)

        // Dialog + actions sheet closed
        val state = contentState(vm)
        assertNull(state.folderRenameForId)
        assertNull(state.folderActionsSheetFor)
    }

    @Test
    fun startFolderRename_fromActionsSheet_closesSheetSoDialogIsReachable() = runTest {
        val vm = createViewModel()
        // Open the folder actions sheet (long-press), then tap "Rename".
        vm.onIntent(ChecklistDetailIntent.OnFolderLongPress(folderA.id))
        assertEquals(folderA.id, contentState(vm).folderActionsSheetFor)

        vm.onIntent(ChecklistDetailIntent.OnRenameFolder(folderA.id))

        val state = contentState(vm)
        assertEquals(folderA.id, state.folderRenameForId, "rename dialog opens")
        // Regression: the actions sheet must close, otherwise the ModalBottomSheet covers the
        // rename dialog and its text field can't be reached.
        assertNull(state.folderActionsSheetFor, "actions sheet closes when rename starts")
    }

    @Test
    fun confirmFolderRename_blankName_doesNotPersist() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRenameFolder(folderA.id))
        vm.onIntent(ChecklistDetailIntent.OnFolderRenameDraftChange("   "))
        vm.onIntent(ChecklistDetailIntent.OnConfirmRenameFolder)

        assertNull(repository.lastUpdatedTemplate, "blank rename must not persist")
        assertNull(contentState(vm).folderRenameForId, "dialog still closes")
    }

    @Test
    fun confirmFolderRename_unchanged_doesNotPersist() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRenameFolder(folderA.id))
        // Draft equals current name
        vm.onIntent(ChecklistDetailIntent.OnFolderRenameDraftChange("Folder A"))
        vm.onIntent(ChecklistDetailIntent.OnConfirmRenameFolder)

        assertNull(repository.lastUpdatedTemplate, "no-op rename must not persist")
    }

    // ── Delete (cascade) ──────────────────────────────────────────────────

    @Test
    fun requestFolderDelete_computesCascadeCount() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnDeleteFolder(folderA.id))

        val state = contentState(vm)
        assertEquals(folderA.id, state.pendingFolderDeleteId)
        // folderA descendants: folderAChild, leafA1, leafAC1 → 3
        assertEquals(3, state.pendingFolderDeleteCount)
        // Actions sheet closed behind the confirm
        assertNull(state.folderActionsSheetFor)
    }

    @Test
    fun requestFolderDelete_emptyFolder_countZero() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnDeleteFolder(folderB.id))

        assertEquals(0, contentState(vm).pendingFolderDeleteCount)
    }

    @Test
    fun confirmFolderDelete_removesFolderAndAllDescendants_fromTemplateAndFill() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnDeleteFolder(folderA.id))
        vm.onIntent(ChecklistDetailIntent.OnConfirmDeleteFolder)

        val savedTemplate = repository.lastUpdatedTemplate
        assertNotNull(savedTemplate)
        val remainingIds = savedTemplate.items.map { it.id }.toSet()
        // folderA + its subtree gone
        assertFalse(folderA.id in remainingIds)
        assertFalse(folderAChild.id in remainingIds)
        assertFalse(leafA1.id in remainingIds)
        assertFalse(leafAC1.id in remainingIds)
        // Siblings untouched
        assertTrue(folderB.id in remainingIds)
        assertTrue(leafRoot.id in remainingIds)

        // Fill rows for the deleted subtree gone (matched by templateItemId)
        val savedFill = repository.lastUpdatedFill
        assertNotNull(savedFill)
        val remainingFillLinks = savedFill.items.mapNotNull { it.templateItemId }.toSet()
        assertFalse(folderA.id in remainingFillLinks)
        assertFalse(leafA1.id in remainingFillLinks)
        assertFalse(leafAC1.id in remainingFillLinks)
        assertTrue(leafRoot.id in remainingFillLinks)
    }

    @Test
    fun confirmFolderDelete_cancelsAlarmsForDescendantLeafWithReminder() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnDeleteFolder(folderA.id))
        vm.onIntent(ChecklistDetailIntent.OnConfirmDeleteFolder)

        // leafA1's FILL row had an active reminder → both cancels must fire, keyed by the FILL id.
        assertTrue(scheduler.cancelledItemReminders.any { it == fillLeafA1.id })
        assertTrue(scheduler.cancelledItemRepeats.any { it == fillLeafA1.id })
    }

    @Test
    fun confirmFolderDelete_doesNotCancelForLeavesWithoutReminder() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnDeleteFolder(folderA.id))
        vm.onIntent(ChecklistDetailIntent.OnConfirmDeleteFolder)

        // leafAC1 (descendant, no reminder) must NOT be cancelled.
        assertFalse(scheduler.cancelledItemReminders.any { it == fillLeafAC1.id })
    }

    @Test
    fun confirmFolderDelete_whileInsideDeletedFolder_navigatesBack() = runTest {
        // VM scoped to folderAChild; deleting its ancestor folderA must pop the back stack.
        val vm = createViewModel(folderId = folderAChild.id)
        vm.onIntent(ChecklistDetailIntent.OnDeleteFolder(folderA.id))
        vm.onIntent(ChecklistDetailIntent.OnConfirmDeleteFolder)

        assertTrue(navigator.backCalled, "must navigate back when standing inside a deleted subtree")
    }

    @Test
    fun confirmFolderDelete_whileAtRoot_doesNotNavigateBack() = runTest {
        val vm = createViewModel(folderId = null)
        vm.onIntent(ChecklistDetailIntent.OnDeleteFolder(folderA.id))
        vm.onIntent(ChecklistDetailIntent.OnConfirmDeleteFolder)

        assertFalse(navigator.backCalled, "root view stays put after deleting a child folder")
    }

    @Test
    fun confirmFolderDelete_emptyFolder_removesOnlyTheFolder_noCancel() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnDeleteFolder(folderB.id))
        vm.onIntent(ChecklistDetailIntent.OnConfirmDeleteFolder)

        val savedTemplate = repository.lastUpdatedTemplate
        assertNotNull(savedTemplate)
        assertFalse(savedTemplate.items.any { it.id == folderB.id })
        // Everything else still present
        assertTrue(savedTemplate.items.any { it.id == folderA.id })
        // No alarms cancelled (empty folder)
        assertTrue(scheduler.cancelledItemReminders.isEmpty())
    }

    // ── Reminder (Phase 5) ──────────────────────────────────────────────────
    //
    // A folder gets a reminder by reusing the leaf per-item reminder flow: its flat fill row holds
    // the reminder fields, so OnFolderReminderClick resolves that row's FILL id and delegates to
    // the existing handleItemReminderClick / saveItemReminder / removeItemReminder path.

    @Test
    fun onFolderReminderClick_opensReminderSheet_scopedToFolderFillItem() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnFolderReminderClick(folderB.id))

        val state = contentState(vm)
        // Sheet opened for the folder's FILL row id (not the template folder id), reusing the
        // leaf per-item reminder sheet.
        assertEquals(fillFolderB.id, state.itemReminderSheetFor)
        // Actions sheet closed behind the reminder sheet.
        assertNull(state.folderActionsSheetFor)
    }

    @Test
    fun onFolderReminderClick_thenSaveOneShot_persistsOnFolderFillRow_andSchedules() = runTest {
        val vm = createViewModel()
        val triggerAt = System.currentTimeMillis() + 3_600_000L

        vm.onIntent(ChecklistDetailIntent.OnFolderReminderClick(folderB.id))
        vm.onIntent(
            ChecklistDetailIntent.OnSaveItemReminder(
                itemId = fillFolderB.id,
                reminderAt = triggerAt,
                repeatRule = null,
                repeatTimeOfDayMinutes = null,
            )
        )

        // Reminder persisted on the FOLDER's fill row.
        val savedFill = repository.lastUpdatedFill
        assertNotNull(savedFill)
        val savedRow = savedFill.items.firstOrNull { it.id == fillFolderB.id }
        assertNotNull(savedRow)
        assertEquals(triggerAt, savedRow.reminderAt)

        // Scheduler called, keyed by the folder's FILL id.
        assertTrue(scheduler.scheduledItemReminders.any {
            it.itemId == fillFolderB.id && it.triggerAtMillis == triggerAt
        })

        // Sheet closed after save.
        assertNull(contentState(vm).itemReminderSheetFor)
    }

    @Test
    fun onFolderReminder_remove_clearsReminder_andCancels() = runTest {
        // Seed folderB's fill row with an active reminder so remove has something to clear.
        val fillFolderBWithReminder = fillFolderB
            .withReminderAt(System.currentTimeMillis() + 3_600_000L)
        repository.defaultFillFlow.value = testFill.copy(
            items = listOf(
                fillFolderA, fillFolderAChild, fillFolderBWithReminder,
                fillLeafA1, fillLeafAC1, fillLeafRoot,
            ),
        )
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnRemoveItemReminder(fillFolderB.id))

        // Both alarms cancelled, keyed by the folder's FILL id.
        assertTrue(scheduler.cancelledItemReminders.any { it == fillFolderB.id })
        assertTrue(scheduler.cancelledItemRepeats.any { it == fillFolderB.id })

        // Fill row reminder cleared.
        val savedFill = repository.lastUpdatedFill
        assertNotNull(savedFill)
        val savedRow = savedFill.items.firstOrNull { it.id == fillFolderB.id }
        assertNotNull(savedRow)
        assertNull(savedRow.reminderAt)
        assertFalse(savedRow.hasActiveReminder)
    }

    @Test
    fun onFolderReminderClick_legacyFolderWithoutFillRow_lazilyCreatesRow_andOpensSheet() = runTest {
        // Legacy folder: a FOLDER template node with NO linked fill row.
        val legacyFolder = ChecklistItem(text = "Legacy", type = ChecklistNodeType.FOLDER)
        repository.storedChecklist = testChecklist.copy(items = templateItems + legacyFolder)
        repository.defaultFillFlow.value = testFill // no row for legacyFolder
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnFolderReminderClick(legacyFolder.id))

        // A fill row was lazily created for the legacy folder and persisted.
        val savedFill = repository.lastUpdatedFill
        assertNotNull(savedFill, "lazy fill row must be persisted for a legacy folder")
        val createdRow = savedFill.items.firstOrNull { it.templateItemId == legacyFolder.id }
        assertNotNull(createdRow, "a linked fill row must exist after lazy creation")

        // The reminder sheet opened for that newly created row.
        assertEquals(createdRow.id, contentState(vm).itemReminderSheetFor)
    }

    @Test
    fun onFolderReminderClick_staleFolder_showsSnackbar_noSheet() = runTest {
        val vm = createViewModel()
        // A folder id that does not exist in the template (deleted/moved under the user).
        vm.onIntent(ChecklistDetailIntent.OnFolderReminderClick("ghost-folder-id"))

        val state = contentState(vm)
        assertEquals(
            ChecklistDetailViewModel.SNACKBAR_FOLDER_REMINDER_UNAVAILABLE,
            state.snackbarMessage,
        )
        assertNull(state.itemReminderSheetFor, "no reminder sheet for a missing folder")
        assertNull(state.folderActionsSheetFor)
    }

    @Test
    fun buildFolderState_exposesHasReminder_forFolderWithReminder() = runTest {
        // folderA's fill row carries an active reminder; folderB's does not.
        val fillFolderAWithReminder = fillFolderA
            .withReminderAt(System.currentTimeMillis() + 3_600_000L)
        repository.defaultFillFlow.value = testFill.copy(
            items = listOf(
                fillFolderAWithReminder, fillFolderAChild, fillFolderB,
                fillLeafA1, fillLeafAC1, fillLeafRoot,
            ),
        )
        val vm = createViewModel()

        val folders = contentState(vm).folders.associateBy { it.id }
        assertTrue(folders[folderA.id]?.hasReminder ?: false, "folderA has a reminder")
        assertFalse(folders[folderB.id]?.hasReminder ?: true, "folderB has no reminder")
    }

    @Test
    fun confirmFolderDelete_cancelsAlarmForFolderWithOwnReminder() = runTest {
        // folderB (empty) carries its OWN reminder on its fill row.
        val fillFolderBWithReminder = fillFolderB
            .withReminderAt(System.currentTimeMillis() + 3_600_000L)
        repository.defaultFillFlow.value = testFill.copy(
            items = listOf(
                fillFolderA, fillFolderAChild, fillFolderBWithReminder,
                fillLeafA1, fillLeafAC1, fillLeafRoot,
            ),
        )
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnDeleteFolder(folderB.id))
        vm.onIntent(ChecklistDetailIntent.OnConfirmDeleteFolder)

        // The deleted folder's OWN alarm must be cancelled (keyed by its FILL id).
        assertTrue(
            scheduler.cancelledItemReminders.any { it == fillFolderB.id },
            "deleting a folder with a reminder must cancel its own alarm",
        )
        assertTrue(scheduler.cancelledItemRepeats.any { it == fillFolderB.id })
    }

    // ── Dismiss ───────────────────────────────────────────────────────────

    @Test
    fun dismissFolderActions_clearsSheet() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnFolderLongPress(folderA.id))
        assertEquals(folderA.id, contentState(vm).folderActionsSheetFor)

        vm.onIntent(ChecklistDetailIntent.OnDismissFolderActions)
        assertNull(contentState(vm).folderActionsSheetFor)
    }

    @Test
    fun dismissMoveSheet_clearsTargets() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnMoveNodeRequested(leafRoot.id))
        assertNotNull(contentState(vm).moveSheetForNodeId)

        vm.onIntent(ChecklistDetailIntent.OnDismissMoveSheet)
        val state = contentState(vm)
        assertNull(state.moveSheetForNodeId)
        assertTrue(state.moveTargets.isEmpty())
    }

    @Test
    fun dismissDeleteFolder_clearsPending() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnDeleteFolder(folderA.id))
        assertNotNull(contentState(vm).pendingFolderDeleteId)

        vm.onIntent(ChecklistDetailIntent.OnDismissDeleteFolder)
        assertNull(contentState(vm).pendingFolderDeleteId)
        // Folder NOT deleted (no write)
        assertNull(repository.lastUpdatedTemplate)
    }

    // ── Folders on/off toggle + flatten (Phase 7) ────────────────────────────

    @Test
    fun toggleFoldersEnabled_whenOff_enables_noFlatten() = runTest {
        // A flat checklist (folders disabled) with only leaves.
        repository.storedChecklist = Checklist(
            id = 1L,
            name = "Flat",
            items = listOf(leafRoot),
            foldersEnabled = false,
        )
        repository.defaultFillFlow.value = testFill.copy(items = listOf(fillLeafRoot))
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnToggleFoldersEnabled)

        assertTrue(contentState(vm).foldersEnabled, "toggling on enables folders")
        assertEquals(true, repository.lastSetFoldersEnabled, "setFoldersEnabled(true) must persist")
        // Enabling is non-structural — no template/fill rewrite.
        assertNull(repository.lastUpdatedTemplate, "enabling must not rewrite the template")
        assertFalse(contentState(vm).showFlattenFoldersConfirm)
    }

    @Test
    fun toggleFoldersEnabled_weeklyMode_isNoOp() = runTest {
        // Weekly checklist with folders OFF — enabling must be blocked (mutual exclusion).
        repository.storedChecklist = Checklist(
            id = 1L,
            name = "Weekly",
            items = listOf(leafRoot),
            foldersEnabled = false,
            viewMode = ChecklistViewMode.Weekly,
        )
        repository.defaultFillFlow.value = testFill.copy(items = listOf(fillLeafRoot))
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnToggleFoldersEnabled)

        assertFalse(contentState(vm).foldersEnabled, "folders must stay off in Weekly mode")
        assertNull(repository.lastSetFoldersEnabled, "setFoldersEnabled must not be called in Weekly mode")
        assertFalse(contentState(vm).showFlattenFoldersConfirm)
    }

    @Test
    fun toggleFoldersEnabled_offWithNoFolders_disablesStraightAway() = runTest {
        // Folders ON but the checklist has no FOLDER nodes → no confirm, flip straight off.
        repository.storedChecklist = Checklist(
            id = 1L,
            name = "No folders",
            items = listOf(leafRoot),
            foldersEnabled = true,
        )
        repository.defaultFillFlow.value = testFill.copy(items = listOf(fillLeafRoot))
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnToggleFoldersEnabled)

        assertFalse(contentState(vm).foldersEnabled)
        assertFalse(contentState(vm).showFlattenFoldersConfirm, "no confirm needed when there are no folders")
        assertEquals(false, repository.lastSetFoldersEnabled)
    }

    @Test
    fun toggleFoldersEnabled_offWithFolders_opensConfirm_noWriteYet() = runTest {
        // Default testChecklist has folders → turning off must open the flatten confirm first.
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnToggleFoldersEnabled)

        val state = contentState(vm)
        assertTrue(state.showFlattenFoldersConfirm, "must ask before flattening")
        assertTrue(state.foldersEnabled, "folders stay on until the user confirms")
        assertNull(repository.lastSetFoldersEnabled, "nothing persisted until confirm")
        assertNull(repository.lastUpdatedTemplate)
    }

    @Test
    fun confirmDisableFolders_removesFolders_flattensLeavesToRoot() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnToggleFoldersEnabled)
        vm.onIntent(ChecklistDetailIntent.OnConfirmDisableFolders)

        val savedTemplate = repository.lastUpdatedTemplate
        assertNotNull(savedTemplate, "template must be persisted after flatten")
        // All FOLDER nodes gone.
        assertFalse(savedTemplate.items.any { it.type == ChecklistNodeType.FOLDER }, "no folders remain")
        // All leaves survive, lifted to root (parentId == null).
        val survivingIds = savedTemplate.items.map { it.id }.toSet()
        assertTrue(leafA1.id in survivingIds)
        assertTrue(leafAC1.id in survivingIds)
        assertTrue(leafRoot.id in survivingIds)
        assertTrue(savedTemplate.items.all { it.parentId == null }, "every surviving item is at the top level")
        // Flag persisted off.
        assertFalse(savedTemplate.foldersEnabled)
        assertEquals(false, repository.lastSetFoldersEnabled)
        // Confirm dialog closed.
        assertFalse(contentState(vm).showFlattenFoldersConfirm)
        assertFalse(contentState(vm).foldersEnabled)
    }

    @Test
    fun confirmDisableFolders_dropsFolderFillRows_keepsLeafFillRows() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnToggleFoldersEnabled)
        vm.onIntent(ChecklistDetailIntent.OnConfirmDisableFolders)

        val savedFill = repository.lastUpdatedFill
        assertNotNull(savedFill, "fill must be persisted after flatten")
        val remainingLinks = savedFill.items.mapNotNull { it.templateItemId }.toSet()
        // Folder placeholder fill rows removed.
        assertFalse(folderA.id in remainingLinks)
        assertFalse(folderAChild.id in remainingLinks)
        assertFalse(folderB.id in remainingLinks)
        // Leaf fill rows kept (their checked/note/attachment state survives).
        assertTrue(leafA1.id in remainingLinks)
        assertTrue(leafAC1.id in remainingLinks)
        assertTrue(leafRoot.id in remainingLinks)
    }

    @Test
    fun confirmDisableFolders_cancelsAlarmForFolderWithReminder() = runTest {
        // folderB carries its own reminder on its fill row (Phase 5).
        val fillFolderBWithReminder = fillFolderB
            .withReminderAt(System.currentTimeMillis() + 3_600_000L)
        repository.defaultFillFlow.value = testFill.copy(
            items = listOf(
                fillFolderA, fillFolderAChild, fillFolderBWithReminder,
                fillLeafA1, fillLeafAC1, fillLeafRoot,
            ),
        )
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnToggleFoldersEnabled)
        vm.onIntent(ChecklistDetailIntent.OnConfirmDisableFolders)

        // The removed folder's alarm must be cancelled, keyed by its FILL id.
        assertTrue(
            scheduler.cancelledItemReminders.any { it == fillFolderB.id },
            "flattening must cancel folder reminders",
        )
        assertTrue(scheduler.cancelledItemRepeats.any { it == fillFolderB.id })
    }

    @Test
    fun confirmDisableFolders_doesNotCancelLeafReminders() = runTest {
        // leafA1's fill row has an active one-shot reminder; leaves survive the flatten so their
        // alarms must NOT be cancelled.
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnToggleFoldersEnabled)
        vm.onIntent(ChecklistDetailIntent.OnConfirmDisableFolders)

        assertFalse(
            scheduler.cancelledItemReminders.any { it == fillLeafA1.id },
            "surviving leaf reminders must be preserved",
        )
    }

    @Test
    fun confirmDisableFolders_whileInsideFolder_navigatesBack() = runTest {
        // VM scoped to folderA; after flatten that level no longer exists → pop back.
        val vm = createViewModel(folderId = folderA.id)
        vm.onIntent(ChecklistDetailIntent.OnToggleFoldersEnabled)
        vm.onIntent(ChecklistDetailIntent.OnConfirmDisableFolders)

        assertTrue(navigator.backCalled, "must navigate back when standing inside a folder during flatten")
    }

    @Test
    fun dismissDisableFolders_doesNotFlatten() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnToggleFoldersEnabled)
        assertTrue(contentState(vm).showFlattenFoldersConfirm)

        vm.onIntent(ChecklistDetailIntent.OnDismissDisableFolders)

        val state = contentState(vm)
        assertFalse(state.showFlattenFoldersConfirm, "dialog dismissed")
        assertTrue(state.foldersEnabled, "folders stay on after cancel")
        assertNull(repository.lastUpdatedTemplate, "no flatten on cancel")
        assertNull(repository.lastSetFoldersEnabled, "flag untouched on cancel")
    }

    // ─── Test doubles ─────────────────────────────────────────────────────

    data class ScheduledItemReminder(val checklistId: Long, val fillId: Long, val itemId: String, val triggerAtMillis: Long)
    data class ScheduledItemRepeat(val checklistId: Long, val fillId: Long, val itemId: String, val triggerAtMillis: Long)

    private class FakeReminderScheduler : ChecklistReminderScheduler {
        val scheduledItemReminders = mutableListOf<ScheduledItemReminder>()
        val scheduledItemRepeats = mutableListOf<ScheduledItemRepeat>()
        val cancelledItemReminders = mutableListOf<String>()
        val cancelledItemRepeats = mutableListOf<String>()
        var notificationPermissionGranted = true

        override fun hasNotificationPermission(): Boolean = notificationPermissionGranted
        override fun scheduleReminder(checklistId: Long, triggerAtMillis: Long) {}
        override fun cancelReminder(checklistId: Long) {}
        override suspend fun rescheduleAllActiveReminders() {}
        override fun scheduleRepeat(checklistId: Long, triggerAtMillis: Long) {}
        override fun cancelRepeat(checklistId: Long) {}
        override suspend fun rescheduleAllActiveRepeats() {}
        override fun scheduleItemReminder(checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long) {
            scheduledItemReminders.add(ScheduledItemReminder(checklistId, fillId, itemId, triggerAtMillis))
        }
        override fun cancelItemReminder(checklistId: Long, fillId: Long, itemId: String) {
            cancelledItemReminders.add(itemId)
        }
        override fun scheduleItemRepeat(checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long) {
            scheduledItemRepeats.add(ScheduledItemRepeat(checklistId, fillId, itemId, triggerAtMillis))
        }
        override fun cancelItemRepeat(checklistId: Long, fillId: Long, itemId: String) {
            cancelledItemRepeats.add(itemId)
        }
    }

    private class FakeChecklistRepository : ChecklistRepository {
        var storedChecklist: Checklist? = null
        val defaultFillFlow = MutableStateFlow<ChecklistFill?>(null)
        var lastUpdatedFill: ChecklistFill? = null
        var lastUpdatedTemplate: Checklist? = null
        var activeRemindersCount: Int = 0
        // Phase 7: records the last value passed to setFoldersEnabled (null = never called).
        var lastSetFoldersEnabled: Boolean? = null

        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())
        override suspend fun addChecklist(checklist: Checklist): Long = 1L
        override suspend fun updateChecklist(checklist: Checklist) {}
        override suspend fun updateChecklistTemplate(checklist: Checklist) { lastUpdatedTemplate = checklist }
        override suspend fun deleteChecklist(checklist: Checklist) {}
        override suspend fun getChecklistById(id: Long): Checklist? = storedChecklist
        override fun observeChecklistById(id: Long): Flow<Checklist?> = flowOf(storedChecklist)
        override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setFoldersEnabled(checklistId: Long, value: Boolean) { lastSetFoldersEnabled = value }
        override suspend fun setReminder(checklistId: Long, reminderAt: Long?) {}
        override suspend fun countActiveReminders(): Int = activeRemindersCount
        override suspend fun getActiveReminders(): List<ChecklistReminderInfo> = emptyList()
        override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? = defaultFillFlow.value
        override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = defaultFillFlow
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override suspend fun getFillById(id: Long): ChecklistFill? = null
        override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun addFill(fill: ChecklistFill): Long = 1L
        override suspend fun updateFill(fill: ChecklistFill) { lastUpdatedFill = fill }
        override suspend fun deleteFill(fill: ChecklistFill) {}
        override suspend fun reorderChecklists(orderedIds: List<Long>) {}
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
        override suspend fun getAllItemRemindersForRescheduling(): List<ItemReminderInfo> = emptyList()
        override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo>> = flowOf(emptyList())
        override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo> = emptyList()
        override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addAttachment(fillId: Long, itemId: String, attachment: com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment) = Unit
        override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) = Unit
    }

    private class FakeUserDataRepository : UserDataRepository {
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

    private class FakeAppNavigator : AppNavigator {
        override val events: SharedFlow<AppNavEvent> = MutableSharedFlow()
        override val backStack: NavBackStack<NavKey> = NavBackStack()
        var backCalled: Boolean = false
        var lastFolderOpen: Pair<Long, String>? = null
        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
        override fun onBack() { backCalled = true }
        override fun navigateToOnboarding() {}
        override fun navigateToInteractiveOnboarding() {}
        override fun navigateToMainScreen(clearBackStack: Boolean) {}
        override fun navigateToDebugMenu() {}
        override fun navigateToStoreScreenshot() {}
        override fun navigateToCreateChecklistScreen(templateId: Int?, initialText: String?) {}
        override fun navigateToEditChecklist(checklistId: Long) {}
        override fun navigateToTemplatesScreen() {}
        override fun navigateToTemplatePreview(templateId: String) {}
        override fun navigateToAnalyzeScreen(checklistId: Long?, fillDefault: Boolean, initialText: String?) {}
        override fun navigateToAnalyzeResultPreview() {}
        override fun navigateToChecklistDetail(checklistId: Long, focusItemId: String?, clearBackStack: Boolean) {}
        override fun navigateToFolder(checklistId: Long, folderId: String) { lastFolderOpen = checklistId to folderId }
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

    private class FakeRemoteConfigProvider : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getString(key: String, defaultValue: String): String = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
    }

    private class FakePaywallRepository(
        status: SubscriptionStatus = SubscriptionStatus.FREE,
    ) : PaywallRepository {
        override val subscriptionStatus: Flow<SubscriptionStatus> = flowOf(status)
        override suspend fun getOfferings(): Result<PaywallOffering?> = Result.success(null)
        override suspend fun purchase(packageId: String): PurchaseResult = PurchaseResult.Error("not implemented")
        override suspend fun restorePurchases(): RestoreResult = RestoreResult.Error("not implemented")
        override suspend fun refreshSubscriptionStatus() {}
        override fun isConfigured(): Boolean = true
        override suspend fun logIn(appUserId: String): Result<LoginResult> = Result.failure(NotImplementedError())
        override suspend fun logOut(): Result<SubscriptionStatus> = Result.failure(NotImplementedError())
    }

    private class FakeAnalyticsTracker : AnalyticsTracker {
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {}
    }

    private class FakeSmartDateParser :
        com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.SmartDateParser {
        override fun parse(
            input: String,
            now: Long,
            timeZone: kotlinx.datetime.TimeZone,
        ): com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.ParsedDateToken? = null
    }

    private class FakeAttachmentStorage : com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort {
        override suspend fun storeAttachment(sourcePath: String, fillId: Long, itemId: String, attachmentId: String, originalFileName: String): String? = null
        override suspend fun deleteAttachment(path: String) {}
        override suspend fun deleteAttachmentsFor(fillId: Long, itemId: String) {}
        override suspend fun deleteAttachmentsForFill(fillId: Long) {}
        override suspend fun probeImage(path: String, mimeType: String?): Pair<Int?, Int?> = null to null
        override suspend fun sizeOf(path: String): Long = 0L
    }
}
