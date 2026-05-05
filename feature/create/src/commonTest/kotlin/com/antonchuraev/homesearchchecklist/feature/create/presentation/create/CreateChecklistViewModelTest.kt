package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.navigation.api.NavCommand
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateChecklistViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var fakeRepo: FakeChecklistRepository
    private lateinit var fakeNavigator: FakeAppNavigator
    private lateinit var fakeAnalytics: RecordingAnalyticsTracker

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeChecklistRepository()
        fakeNavigator = FakeAppNavigator()
        fakeAnalytics = RecordingAnalyticsTracker()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(editChecklistId: Long? = null): CreateChecklistViewModel =
        CreateChecklistViewModel(editChecklistId, fakeRepo, fakeNavigator, fakeAnalytics)

    // ── State shape ──────────────────────────────────────────────────

    @Test
    fun state_defaultsIncludeEmptyEditingFields() {
        val state = CreateChecklistState()
        assertNull(state.editingItemId)
        assertEquals("", state.editingItemText)
    }

    @Test
    fun state_copyPreservesEditingFields() {
        val initial = CreateChecklistState(editingItemId = "id-1", editingItemText = "draft")
        val updated = initial.copy(editingItemText = "updated")
        assertEquals("id-1", updated.editingItemId)
        assertEquals("updated", updated.editingItemText)
    }

    @Test
    fun checklistItem_withText_preservesIdAndChecked() {
        val item = ChecklistItem("buy milk", checked = true)
        val renamed = item.withText("buy milk 2L")
        assertEquals(item.id, renamed.id)
        assertTrue(renamed.checked)
        assertEquals("buy milk 2L", renamed.text)
    }

    // ── ViewModel behavior ──────────────────────────────────────────

    @Test
    fun onStartItemEdit_setsEditingStateWithExistingItemText() = testScope.runTest {
        val vm = createViewModel()
        vm.onIntent(CreateChecklistIntent.OnNewItemTextChange("buy milk"))
        vm.onIntent(CreateChecklistIntent.OnAddItemFromInput)
        advanceUntilIdle()
        val itemId = vm.screenState.value.items[0].id

        vm.onIntent(CreateChecklistIntent.OnStartItemEdit(itemId))
        advanceUntilIdle()

        val state = vm.screenState.value
        assertEquals(itemId, state.editingItemId)
        assertEquals("buy milk", state.editingItemText)
    }

    @Test
    fun onConfirmItemEdit_withNonBlankText_replacesItemText_resetsEditing() = testScope.runTest {
        val vm = createViewModel()
        vm.onIntent(CreateChecklistIntent.OnNewItemTextChange("buy milk"))
        vm.onIntent(CreateChecklistIntent.OnAddItemFromInput)
        advanceUntilIdle()
        val itemId = vm.screenState.value.items[0].id

        vm.onIntent(CreateChecklistIntent.OnStartItemEdit(itemId))
        vm.onIntent(CreateChecklistIntent.OnItemEditTextChange("buy milk 2L"))
        vm.onIntent(CreateChecklistIntent.OnConfirmItemEdit)
        advanceUntilIdle()

        val state = vm.screenState.value
        assertEquals("buy milk 2L", state.items[0].text)
        assertEquals(itemId, state.items[0].id, "Item id must be preserved on rename")
        assertNull(state.editingItemId)
        assertEquals("", state.editingItemText)
    }

    @Test
    fun onConfirmItemEdit_withBlankText_revertsWithoutChangingItems_resetsEditing() = testScope.runTest {
        val vm = createViewModel()
        vm.onIntent(CreateChecklistIntent.OnNewItemTextChange("buy milk"))
        vm.onIntent(CreateChecklistIntent.OnAddItemFromInput)
        advanceUntilIdle()
        val itemId = vm.screenState.value.items[0].id

        vm.onIntent(CreateChecklistIntent.OnStartItemEdit(itemId))
        vm.onIntent(CreateChecklistIntent.OnItemEditTextChange("   "))
        vm.onIntent(CreateChecklistIntent.OnConfirmItemEdit)
        advanceUntilIdle()

        val state = vm.screenState.value
        assertEquals("buy milk", state.items[0].text, "Blank edit must revert to original text")
        assertNull(state.editingItemId)
        assertEquals("", state.editingItemText)
    }

    @Test
    fun onStartItemEdit_whenPreviousEditOpen_commitsPrevious_andStartsNew() = testScope.runTest {
        val vm = createViewModel()
        vm.onIntent(CreateChecklistIntent.OnNewItemTextChange("buy milk"))
        vm.onIntent(CreateChecklistIntent.OnAddItemFromInput)
        vm.onIntent(CreateChecklistIntent.OnNewItemTextChange("clean room"))
        vm.onIntent(CreateChecklistIntent.OnAddItemFromInput)
        advanceUntilIdle()
        // New items appear at the TOP — [clean room, buy milk]
        val cleanRoomId = vm.screenState.value.items[0].id
        val buyMilkId = vm.screenState.value.items[1].id

        vm.onIntent(CreateChecklistIntent.OnStartItemEdit(buyMilkId))
        vm.onIntent(CreateChecklistIntent.OnItemEditTextChange("buy milk 2L"))
        vm.onIntent(CreateChecklistIntent.OnStartItemEdit(cleanRoomId))
        advanceUntilIdle()

        val state = vm.screenState.value
        val buyMilk = state.items.first { it.id == buyMilkId }
        assertEquals(
            "buy milk 2L",
            buyMilk.text,
            "Previous edit must be auto-committed when switching to another item"
        )
        assertEquals(cleanRoomId, state.editingItemId)
        assertEquals("clean room", state.editingItemText)
    }

    @Test
    fun onCancelItemEdit_resetsEditingState_withoutChangingItems() = testScope.runTest {
        val vm = createViewModel()
        vm.onIntent(CreateChecklistIntent.OnNewItemTextChange("buy milk"))
        vm.onIntent(CreateChecklistIntent.OnAddItemFromInput)
        advanceUntilIdle()
        val itemId = vm.screenState.value.items[0].id

        vm.onIntent(CreateChecklistIntent.OnStartItemEdit(itemId))
        vm.onIntent(CreateChecklistIntent.OnItemEditTextChange("buy milk 2L"))
        vm.onIntent(CreateChecklistIntent.OnCancelItemEdit)
        advanceUntilIdle()

        val state = vm.screenState.value
        assertEquals("buy milk", state.items[0].text, "Cancel must not apply the pending text")
        assertNull(state.editingItemId)
        assertEquals("", state.editingItemText)
    }

    @Test
    fun onSaveClick_withOpenEdit_commitsPendingEdit_beforePersisting() = testScope.runTest {
        fakeRepo.loadResult = Checklist(
            id = 1L,
            name = "Test list",
            items = listOf(ChecklistItem("buy milk"))
        )
        val vm = createViewModel(editChecklistId = 1L)
        advanceUntilIdle()
        val itemId = vm.screenState.value.items[0].id

        vm.onIntent(CreateChecklistIntent.OnStartItemEdit(itemId))
        vm.onIntent(CreateChecklistIntent.OnItemEditTextChange("buy milk 2L"))
        vm.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        val persisted = fakeRepo.lastUpdatedChecklist
        assertEquals("buy milk 2L", persisted?.items?.get(0)?.text)
        assertEquals(itemId, persisted?.items?.get(0)?.id)
        assertTrue(fakeNavigator.backInvoked, "Edit flow must navigate back after save")
    }

    // ── Fakes ───────────────────────────────────────────────────────

    private class FakeChecklistRepository : ChecklistRepository {
        var loadResult: Checklist? = null
        var lastUpdatedChecklist: Checklist? = null
        var lastAddedChecklist: Checklist? = null

        override val checklists: Flow<List<Checklist>> = emptyFlow()

        override suspend fun addChecklist(checklist: Checklist): Long {
            lastAddedChecklist = checklist
            return 1L
        }

        override suspend fun updateChecklist(checklist: Checklist) {
            lastUpdatedChecklist = checklist
        }

        override suspend fun updateChecklistTemplate(checklist: Checklist) {
            lastUpdatedChecklist = checklist
        }

        override suspend fun deleteChecklist(checklist: Checklist) = notUsed()
        override suspend fun getChecklistById(id: Long): Checklist? = loadResult
        override suspend fun reorderChecklists(orderedIds: List<Long>) = notUsed()

        override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) = notUsed()
        override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) = notUsed()

        override suspend fun setReminder(checklistId: Long, reminderAt: Long?) = notUsed()
        override suspend fun countActiveReminders(): Int = 0
        override suspend fun getActiveReminders(): List<ChecklistReminderInfo> = emptyList()
        override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? = null

        override suspend fun setRepeatSchedule(
            checklistId: Long,
            rule: ReminderRepeatRule,
            timeOfDayMinutes: Int,
            firstTriggerAt: Long
        ) = notUsed()

        override suspend fun advanceRepeatSchedule(checklistId: Long, nextAt: Long?, newCount: Int) = notUsed()
        override suspend fun clearRepeatSchedule(checklistId: Long) = notUsed()
        override suspend fun resetDefaultFillChecks(checklistId: Long) = notUsed()
        override suspend fun countActiveRepeatSchedules(): Int = 0
        override suspend fun getActiveRepeatSchedules(): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> = emptyList()

        override suspend fun getTotalAdditionalFillCount(): Int = 0
        override suspend fun getWeeklyChecklistCount(): Int = 0
        override suspend fun getAllItemRemindersForRescheduling(): List<ItemReminderInfo> = emptyList()

        override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = emptyFlow()
        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = emptyFlow()
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = emptyFlow()
        override suspend fun getFillById(id: Long): ChecklistFill? = null
        override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun addFill(fill: ChecklistFill): Long = 0L
        override suspend fun updateFill(fill: ChecklistFill) = notUsed()
        override suspend fun deleteFill(fill: ChecklistFill) = notUsed()

        private fun notUsed(): Nothing = error("FakeChecklistRepository: method not wired for this test")
    }

    private class FakeAppNavigator : AppNavigator {
        var backInvoked = false
        var navigatedToMainScreen = false

        override val commands: Flow<NavCommand> = emptyFlow()

        private val _events = MutableSharedFlow<AppNavEvent>()
        override val events: SharedFlow<AppNavEvent> = _events.asSharedFlow()

        override fun showWidgetInstruction() {}
        override fun onBack() { backInvoked = true }
        override fun navigateToOnboarding() {}
        override fun navigateToInteractiveOnboarding() {}
        override fun navigateToMainScreen(clearBackStack: Boolean) { navigatedToMainScreen = true }
        override fun navigateToDebugMenu() {}
        override fun navigateToStoreScreenshot() {}
        override fun navigateToCreateChecklistScreen(templateId: Int?) {}
        override fun navigateToEditChecklist(checklistId: Long) {}
        override fun navigateToTemplatesScreen() {}
        override fun navigateToTemplatePreview(templateId: String) {}
        override fun navigateToAnalyzeScreen(checklistId: Long?, fillDefault: Boolean) {}
        override fun navigateToAnalyzeResultPreview() {}
        override fun navigateToChecklistDetail(checklistId: Long, clearBackStack: Boolean) {}
        override fun navigateToFillDetail(fillId: Long, clearBackStack: Boolean) {}
        override fun navigateToFillsList(checklistId: Long) {}
        override fun navigateToPaywall(source: String) {}
        override fun navigateToPaywallVariant(source: String, forceVariant: String) {}
        override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) {}
        override fun navigateToShareChecklist(checklistId: Long) {}
        override fun navigateToUpdateFeed() {}
        override fun navigateToSettings() {}
        override fun navigateToScreenCatalog() {}
    }

    private class RecordingAnalyticsTracker : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()

        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) { events.add(name to params) }
    }
}
