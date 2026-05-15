package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.navigation.api.NavCommand
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private fun createViewModel(
        editChecklistId: Long? = null,
        checklistCount: Int = 0,
        isPremium: Boolean = false
    ): CreateChecklistViewModel {
        val checklistRepoWithCount = if (checklistCount > 0) {
            FakeChecklistRepositoryWithCount(fakeRepo, checklistCount)
        } else {
            fakeRepo
        }
        val getUserLimitsUseCase = buildGetUserLimitsUseCase(
            checklistRepo = checklistRepoWithCount,
            isPremium = isPremium
        )
        return CreateChecklistViewModel(
            editChecklistId,
            fakeRepo,
            fakeNavigator,
            fakeAnalytics,
            getUserLimitsUseCase
        )
    }

    private fun buildGetUserLimitsUseCase(
        checklistRepo: ChecklistRepository = fakeRepo,
        isPremium: Boolean = false
    ): GetUserLimitsUseCase = GetUserLimitsUseCase(
        remoteConfigProvider = FakeRemoteConfigProvider(),
        checklistRepository = checklistRepo,
        paywallRepository = FakePaywallRepository(isPremium),
        userDataRepository = FakeUserDataRepository(isPremium)
    )

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

    // ── Checklist limit gate ────────────────────────────────────────

    @Test
    fun onSaveClick_whenLocked_navigatesToPaywall_skipsAddChecklist() = testScope.runTest {
        val maxFree = RemoteConfigDefaults.MAX_CHECKLISTS_FREE.toInt()
        // Use UnconfinedTestDispatcher for the limits-observing coroutine to settle first
        val unconfinedDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(unconfinedDispatcher)

        val vm = createViewModel(editChecklistId = null, checklistCount = maxFree, isPremium = false)
        advanceUntilIdle()

        vm.onIntent(CreateChecklistIntent.OnNameChange("My List"))
        vm.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        assertEquals("checklist_limit", fakeNavigator.paywallSource,
            "Expected paywall navigation with source='checklist_limit'")
        assertNull(fakeRepo.lastAddedChecklist,
            "Expected no checklist added when at free limit")
    }

    @Test
    fun onSaveClick_whenInEditMode_doesNotGate() = testScope.runTest {
        val maxFree = RemoteConfigDefaults.MAX_CHECKLISTS_FREE.toInt()
        fakeRepo.loadResult = Checklist(
            id = 1L,
            name = "Existing",
            items = listOf(ChecklistItem("task"))
        )
        // Edit mode: checklistCount at max, but edit should bypass gate
        val vm = createViewModel(editChecklistId = 1L, checklistCount = maxFree, isPremium = false)
        advanceUntilIdle()

        vm.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        assertNull(fakeNavigator.paywallSource, "Edit mode must not route to paywall")
        assertTrue(fakeNavigator.backInvoked, "Edit mode save must navigate back")
    }

    @Test
    fun onSaveClick_whenFreeBelowLimit_addsChecklist() = testScope.runTest {
        val vm = createViewModel(editChecklistId = null, checklistCount = 0, isPremium = false)
        advanceUntilIdle()

        vm.onIntent(CreateChecklistIntent.OnNameChange("My List"))
        vm.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        assertNull(fakeNavigator.paywallSource, "No paywall when below limit")
        assertTrue(fakeNavigator.navigatedToMainScreen, "Expected navigation to main after save")
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
        override fun observeChecklistById(id: Long): Flow<Checklist?> = flowOf(loadResult)
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
        override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo> = emptyList()
        override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addAttachment(fillId: Long, itemId: String, attachment: com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment) = Unit
        override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) = Unit
        override suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> = emptyList()

        override suspend fun getTotalAdditionalFillCount(): Int = 0
        override suspend fun getWeeklyChecklistCount(): Int = 0
        override val weeklyChecklistCount: Flow<Int> = flowOf(0)
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
        var paywallSource: String? = null

        override val commands: Flow<NavCommand> = emptyFlow()

        private val _events = MutableSharedFlow<AppNavEvent>()
        override val events: SharedFlow<AppNavEvent> = _events.asSharedFlow()

        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
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
        override fun navigateToPaywall(source: String) { paywallSource = source }
        override fun navigateToPaywallVariant(source: String, forceVariant: String) {}
        override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) {}
        override fun navigateToShareChecklist(checklistId: Long) {}
        override fun navigateToUpdateFeed() {}
        override fun navigateToSettings() {}
        override fun navigateToToday() {}
        override fun navigateToCalendar() {}
        override fun navigateToScreenCatalog() {}
    }

    private class RecordingAnalyticsTracker : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()

        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) { events.add(name to params) }
    }

    /** Wraps FakeChecklistRepository to report a specific checklist count via [checklists] flow. */
    private class FakeChecklistRepositoryWithCount(
        private val delegate: FakeChecklistRepository,
        private val count: Int
    ) : ChecklistRepository by delegate {
        override val checklists: Flow<List<Checklist>> = flowOf(
            List(count) { Checklist(id = it.toLong(), name = "C$it", items = emptyList()) }
        )
    }

    private class FakePaywallRepository(
        private val isPremium: Boolean = false
    ) : PaywallRepository {
        override val subscriptionStatus: Flow<SubscriptionStatus> = flowOf(
            if (isPremium) SubscriptionStatus(isActive = true, activeEntitlements = setOf("AiChecklists Pro"))
            else SubscriptionStatus.FREE
        )
        override suspend fun getOfferings(): Result<PaywallOffering?> = Result.success(null)
        override suspend fun purchase(packageId: String): PurchaseResult = PurchaseResult.Error("stub")
        override suspend fun restorePurchases(): RestoreResult = RestoreResult.Error("stub")
        override suspend fun refreshSubscriptionStatus() {}
        override fun isConfigured(): Boolean = false
        override suspend fun logIn(appUserId: String): Result<LoginResult> = Result.failure(NotImplementedError())
        override suspend fun logOut(): Result<SubscriptionStatus> = Result.failure(NotImplementedError())
    }

    private class FakeUserDataRepository(private val isPremium: Boolean = false) : UserDataRepository {
        private val data = UserData(userId = "test", isPremium = isPremium)
        override fun getUserDataFlow(): StateFlow<UserData> = MutableStateFlow(data)
        override suspend fun getUserData(): UserData = data
        override suspend fun update(userData: UserData) {}
        override suspend fun ensureUserRegistered(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = data, isNewUser = false))
        override suspend fun syncWithServer(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = data, isNewUser = false))
        override suspend fun isPaywallLinked(): Boolean = false
        override suspend fun setPaywallLinked(linked: Boolean) {}
        override suspend fun restoreCreditsAfterPurchase(): Result<Int> = Result.success(0)
        override suspend fun getFirstLaunchAtMillis(): Long = 0L
    }

    private class FakeRemoteConfigProvider : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getString(key: String, defaultValue: String): String = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = when (key) {
            RemoteConfigKeys.MAX_CHECKLISTS_FREE -> RemoteConfigDefaults.MAX_CHECKLISTS_FREE
            else -> defaultValue
        }
    }
}
