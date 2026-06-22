package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.preview

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResultHolder
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistNodeType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Preview-screen behavior for the three activation-flow fixes:
 *  - Fix 1: the AI-suggested name (already resolved by AnalyzeViewModel) reaches the title field.
 *  - Fix 2: "Use folders" is OFF by default → a flat checklist is created even when the AI returned
 *    a structure; opting in creates a structured (foldersEnabled) checklist.
 *  - Fix 3: a soft 10-item cap → the first 10 are included, the rest are held in an expandable
 *    section and are fully recoverable (add one / add all).
 *
 * [AnalyzeResultHolder] is a process-global object; each test seeds it via [seedHolder] and it is
 * cleared in setup/teardown to avoid cross-test pollution.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyzeResultPreviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        AnalyzeResultHolder.clear()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        AnalyzeResultHolder.clear()
    }

    private fun seedHolder(
        items: List<ChecklistItem>,
        suggestedName: String,
        hasFolders: Boolean,
    ) {
        AnalyzeResultHolder.set(
            items = items,
            suggestedName = suggestedName,
            summary = null,
            isFillMode = false,
            hasFolders = hasFolders,
        )
    }

    private fun vm(repo: ChecklistRepository = CapturingChecklistRepository()) =
        AnalyzeResultPreviewViewModel(
            appNavigator = NoOpNavigator(),
            checklistRepository = repo,
            analyticsTracker = NoOpAnalyticsTracker(),
            activationCoordinator = NoOpActivationCoordinator(),
            remoteConfigProvider = NoOpRemoteConfigProvider(),
        )

    // ── Fix 1: suggested name → title ─────────────────────────────────────────

    @Test
    fun loadData_nonBlankSuggestedName_populatesChecklistNameTitle() = runTest {
        seedHolder(
            items = listOf(ChecklistItem(text = "Passport"), ChecklistItem(text = "Charger")),
            suggestedName = "Trip Packing Checklist",
            hasFolders = false,
        )

        val viewModel = vm()
        advanceUntilIdle()

        assertEquals("Trip Packing Checklist", viewModel.screenState.value.checklistName)
    }

    // ── Fix 2: folders OFF by default ─────────────────────────────────────────

    @Test
    fun loadData_aiReturnedFolders_defaultsUseFoldersOff() = runTest {
        val folder = ChecklistItem(text = "Documents", type = ChecklistNodeType.FOLDER)
        val child = ChecklistItem(text = "Passport", parentId = folder.id)
        seedHolder(items = listOf(folder, child), suggestedName = "Trip", hasFolders = true)

        val viewModel = vm()
        advanceUntilIdle()

        val state = viewModel.screenState.value
        assertTrue(state.aiReturnedFolders, "toggle must be offered when AI returned a structure")
        assertFalse(state.useFolders, "folders must default OFF")
        // Flat path renders the leaves only (folders are not checklist items).
        assertEquals(listOf("Passport"), state.editableItems)
    }

    @Test
    fun createChecklist_foldersOffByDefault_createsFlatEvenWhenAiReturnedFolders() = runTest {
        val folder = ChecklistItem(text = "Documents", type = ChecklistNodeType.FOLDER)
        val child = ChecklistItem(text = "Passport", parentId = folder.id)
        val rootLeaf = ChecklistItem(text = "Book tickets")
        seedHolder(items = listOf(folder, child, rootLeaf), suggestedName = "Trip", hasFolders = true)

        val captor = CapturingChecklistRepository()
        val viewModel = vm(captor)
        advanceUntilIdle()

        viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnCreateChecklist)
        advanceUntilIdle()

        val saved = requireNotNull(captor.lastAddedChecklist)
        assertFalse(saved.foldersEnabled, "default (folders off) must create a FLAT checklist")
        // Flat = the two leaves, no folder node, all root + ITEM type.
        assertEquals(listOf("Passport", "Book tickets"), saved.items.map { it.text })
        assertTrue(saved.items.all { it.type == ChecklistNodeType.ITEM && it.parentId == null })
    }

    @Test
    fun createChecklist_foldersToggledOn_createsStructuredChecklist() = runTest {
        val folder = ChecklistItem(text = "Documents", type = ChecklistNodeType.FOLDER)
        val child = ChecklistItem(text = "Passport", parentId = folder.id)
        seedHolder(items = listOf(folder, child), suggestedName = "Trip", hasFolders = true)

        val captor = CapturingChecklistRepository()
        val viewModel = vm(captor)
        advanceUntilIdle()

        viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnUseFoldersChanged(true))
        viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnCreateChecklist)
        advanceUntilIdle()

        val saved = requireNotNull(captor.lastAddedChecklist)
        assertTrue(saved.foldersEnabled, "opting in must create a structured checklist")
        assertEquals(2, saved.items.size)
        val savedFolder = saved.items.first { it.id == folder.id }
        assertEquals(ChecklistNodeType.FOLDER, savedFolder.type)
        val savedChild = saved.items.first { it.id == child.id }
        assertEquals(folder.id, savedChild.parentId, "structure (parentId) must survive")
    }

    // ── Fix 3: soft 10-item cap ───────────────────────────────────────────────

    @Test
    fun loadData_moreThanTenItems_includesFirstTenAndHoldsRest() = runTest {
        val items = (1..14).map { ChecklistItem(text = "Item $it") }
        seedHolder(items = items, suggestedName = "Big", hasFolders = false)

        val viewModel = vm()
        advanceUntilIdle()

        val state = viewModel.screenState.value
        assertEquals(MAX_RECOMMENDED_ITEMS, state.editableItems.size)
        assertEquals(listOf("Item 1", "Item 10"), listOf(state.editableItems.first(), state.editableItems.last()))
        assertEquals(4, state.overflowItems.size)
        assertTrue(state.hasOverflow)
        assertEquals(listOf("Item 11", "Item 12", "Item 13", "Item 14"), state.overflowItems)
    }

    @Test
    fun loadData_tenOrFewerItems_noOverflow() = runTest {
        val items = (1..10).map { ChecklistItem(text = "Item $it") }
        seedHolder(items = items, suggestedName = "Exactly10", hasFolders = false)

        val viewModel = vm()
        advanceUntilIdle()

        val state = viewModel.screenState.value
        assertEquals(10, state.editableItems.size)
        assertFalse(state.hasOverflow)
    }

    @Test
    fun createChecklist_softCappedByDefault_createsOnlyTheIncludedTen() = runTest {
        val items = (1..14).map { ChecklistItem(text = "Item $it") }
        seedHolder(items = items, suggestedName = "Big", hasFolders = false)

        val captor = CapturingChecklistRepository()
        val viewModel = vm(captor)
        advanceUntilIdle()

        viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnCreateChecklist)
        advanceUntilIdle()

        val saved = requireNotNull(captor.lastAddedChecklist)
        assertEquals(MAX_RECOMMENDED_ITEMS, saved.items.size)
    }

    @Test
    fun addAllOverflowItems_thenCreate_includesEveryGeneratedItem() = runTest {
        val items = (1..14).map { ChecklistItem(text = "Item $it") }
        seedHolder(items = items, suggestedName = "Big", hasFolders = false)

        val captor = CapturingChecklistRepository()
        val viewModel = vm(captor)
        advanceUntilIdle()

        viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnAddAllOverflowItems)
        advanceUntilIdle()
        assertFalse(viewModel.screenState.value.hasOverflow, "overflow drains after add-all")
        assertEquals(14, viewModel.screenState.value.editableItems.size)

        viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnCreateChecklist)
        advanceUntilIdle()

        val saved = requireNotNull(captor.lastAddedChecklist)
        assertEquals(14, saved.items.size, "including all → every generated item is created")
    }

    @Test
    fun addSingleOverflowItem_movesItIntoIncludedSet() = runTest {
        val items = (1..12).map { ChecklistItem(text = "Item $it") }
        seedHolder(items = items, suggestedName = "Big", hasFolders = false)

        val viewModel = vm()
        advanceUntilIdle()
        // overflow = [Item 11, Item 12]; add the first.
        viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnAddOverflowItem(0))
        advanceUntilIdle()

        val state = viewModel.screenState.value
        assertEquals(11, state.editableItems.size)
        assertEquals("Item 11", state.editableItems.last())
        assertEquals(listOf("Item 12"), state.overflowItems)
    }

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class CapturingChecklistRepository(
        private val assignedId: Long = 42L,
    ) : NoopChecklistRepository() {
        var lastAddedChecklist: Checklist? = null
        override suspend fun addChecklist(checklist: Checklist): Long {
            lastAddedChecklist = checklist
            return assignedId
        }
    }

    private class NoOpAnalyticsTracker : AnalyticsTracker {
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {}
    }

    private class NoOpActivationCoordinator : ActivationCoordinator {
        private val _requests = MutableSharedFlow<Long>()
        override val reminderOptInRequests: SharedFlow<Long> = _requests.asSharedFlow()
        override suspend fun onAiChecklistCreated(checklistId: Long, activationBundleEnabled: Boolean) {}
        override suspend fun reportReminderOptInOutcome(granted: Boolean) {}
    }

    private class NoOpRemoteConfigProvider : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getString(key: String, defaultValue: String): String = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
    }

    private class NoOpNavigator : AppNavigator {
        override val backStack: NavBackStack<NavKey> = NavBackStack()
        private val _events = MutableSharedFlow<AppNavEvent>()
        override val events: SharedFlow<AppNavEvent> = _events.asSharedFlow()
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
        override fun navigateToAddToChecklistPicker(text: String, purpose: AddToChecklistPurpose) {}
    }

    private open class NoopChecklistRepository : ChecklistRepository {
        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())
        override suspend fun addChecklist(checklist: Checklist): Long = 1L
        override suspend fun updateChecklist(checklist: Checklist) {}
        override suspend fun updateChecklistTemplate(checklist: Checklist) {}
        override suspend fun deleteChecklist(checklist: Checklist) {}
        override suspend fun getChecklistById(id: Long): Checklist? = null
        override fun observeChecklistById(id: Long): Flow<Checklist?> = flowOf(null)
        override suspend fun reorderChecklists(orderedIds: List<Long>) {}
        override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setFoldersEnabled(checklistId: Long, value: Boolean) {}
        override suspend fun setReminder(checklistId: Long, reminderAt: Long?) {}
        override suspend fun countActiveReminders(): Int = 0
        override suspend fun getActiveReminders(): List<ChecklistReminderInfo> = emptyList()
        override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? = null
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
        override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<TodayReminderInfo>> = flowOf(emptyList())
        override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<TodayReminderInfo> = emptyList()
        override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addAttachment(fillId: Long, itemId: String, attachment: Attachment) {}
        override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) {}
        override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = flowOf(null)
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override suspend fun getFillById(id: Long): ChecklistFill? = null
        override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun addFill(fill: ChecklistFill): Long = 1L
        override suspend fun updateFill(fill: ChecklistFill) {}
        override suspend fun deleteFill(fill: ChecklistFill) {}
        override suspend fun reorderItems(fill: ChecklistFill, checklist: Checklist) {}
    }
}
