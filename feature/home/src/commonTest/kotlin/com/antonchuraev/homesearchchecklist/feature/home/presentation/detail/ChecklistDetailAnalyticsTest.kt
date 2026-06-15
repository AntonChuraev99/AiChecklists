package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
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
import kotlin.test.assertTrue

/**
 * Tests analytics events fired by ChecklistDetailViewModel for:
 * - New analytics-only intents (quick-add, completed section, overflow menu)
 * - Enhanced existing events (item_added_quick, checklist_deleted)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistDetailAnalyticsTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var analyticsTracker: RecordingAnalyticsTracker
    private lateinit var repository: FakeChecklistRepository

    private val testChecklist = Checklist(
        id = 1L,
        name = "Test Checklist",
        items = listOf(
            ChecklistItem("Item 1"),
            ChecklistItem("Item 2"),
            ChecklistItem("Item 3")
        )
    )

    private val testFill = ChecklistFill(
        id = 1L,
        checklistId = 1L,
        name = "",
        items = listOf(
            ChecklistFillItem("Item 1", checked = false),
            ChecklistFillItem("Item 2", checked = true),
            ChecklistFillItem("Item 3", checked = false)
        ),
        createdAt = 0L,
        isDefault = true
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        analyticsTracker = RecordingAnalyticsTracker()
        repository = FakeChecklistRepository().apply {
            storedChecklist = testChecklist
            defaultFillFlow.value = testFill
        }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ChecklistDetailViewModel {
        val datastore = AppDatastore(
            PreferenceDataStoreFactory.createWithPath {
                "build/test_prefs_${Random.nextLong()}.preferences_pb".toPath()
            },
            testDispatcher
        )

        return ChecklistDetailViewModel(
            checklistId = 1L,
            repository = repository,
            navigator = FakeAppNavigator(),
            getUserLimitsUseCase = GetUserLimitsUseCase(
                FakeRemoteConfigProvider(),
                repository,
                FakePaywallRepository(),
                FakeUserDataRepository()
            ),
            analyticsTracker = analyticsTracker,
            reminderScheduler = FakeReminderScheduler(),
            datastore = datastore,
            smartDateParser = FakeSmartDateParser(),
            attachmentStorage = FakeAttachmentStorage(),
        )
    }

    // --- New analytics-only events ---

    @Test
    fun quickAddOpened_firesEvent() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnQuickAddOpened)

        assertTrue(analyticsTracker.hasEvent("quick_add_opened"))
    }

    @Test
    fun quickAddCancelled_hadTextTrue_firesEventWithParam() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnQuickAddCancelled(hadText = true))

        assertTrue(analyticsTracker.hasEvent("quick_add_cancelled"))
        assertEquals("true", analyticsTracker.getEventParam("quick_add_cancelled", "had_text"))
    }

    @Test
    fun quickAddCancelled_hadTextFalse_firesEventWithParam() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnQuickAddCancelled(hadText = false))

        assertTrue(analyticsTracker.hasEvent("quick_add_cancelled"))
        assertEquals("false", analyticsTracker.getEventParam("quick_add_cancelled", "had_text"))
    }

    @Test
    fun completedSectionToggle_expanded_firesExpandedEvent() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnCompletedSectionToggle(expanded = true, completedCount = 3))

        assertTrue(analyticsTracker.hasEvent("completed_section_expanded"))
        assertEquals("3", analyticsTracker.getEventParam("completed_section_expanded", "completed_count"))
    }

    @Test
    fun completedSectionToggle_collapsed_firesCollapsedEvent() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnCompletedSectionToggle(expanded = false, completedCount = 5))

        assertTrue(analyticsTracker.hasEvent("completed_section_collapsed"))
        assertEquals("5", analyticsTracker.getEventParam("completed_section_collapsed", "completed_count"))
    }

    // --- Enhanced existing events ---

    @Test
    fun overflowMenuClick_firesOverflowMenuOpenedEvent() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnOverflowMenuClick)

        assertTrue(analyticsTracker.hasEvent("overflow_menu_opened"))
    }

    @Test
    fun addItem_firesItemAddedQuickWithParams() = runTest {
        val vm = createViewModel()
        // OnItemInputChanged sets text; OnAddItemWithParse submits it (plain path — no parsedToken)
        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged("New item"))
        vm.onIntent(ChecklistDetailIntent.OnAddItemWithParse)

        assertTrue(analyticsTracker.hasEvent("item_added_quick"))
        assertEquals("1", analyticsTracker.getEventParam("item_added_quick", "checklist_id"))
        // 3 existing + 1 new = 4
        assertEquals("4", analyticsTracker.getEventParam("item_added_quick", "item_count"))
    }

    @Test
    fun deleteChecklist_firesChecklistDeletedWithParams() = runTest {
        val vm = createViewModel()
        // Show delete confirmation first, then confirm
        vm.onIntent(ChecklistDetailIntent.OnDeleteChecklistClick)
        vm.onIntent(ChecklistDetailIntent.OnConfirmDeleteChecklist)

        assertTrue(analyticsTracker.hasEvent("checklist_deleted"))
        assertEquals("1", analyticsTracker.getEventParam("checklist_deleted", "checklist_id"))
        assertEquals("3", analyticsTracker.getEventParam("checklist_deleted", "item_count"))
        assertEquals("overflow_menu", analyticsTracker.getEventParam("checklist_deleted", "source"))
    }

    // --- Test doubles ---

    private class RecordingAnalyticsTracker : AnalyticsTracker {
        private val events = mutableListOf<Pair<String, Map<String, Any>>>()

        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {
            events.add(name to params)
        }

        fun hasEvent(name: String): Boolean = events.any { it.first == name }
        fun getEventParam(name: String, param: String): Any? =
            events.firstOrNull { it.first == name }?.second?.get(param)
    }

    private class FakeChecklistRepository : ChecklistRepository {
        var storedChecklist: Checklist? = null
        val defaultFillFlow = MutableStateFlow<ChecklistFill?>(null)
        private val checklistsFlow = MutableStateFlow<List<Checklist>>(emptyList())

        override val checklists: Flow<List<Checklist>> = checklistsFlow
        override suspend fun addChecklist(checklist: Checklist): Long = 1L
        override suspend fun updateChecklist(checklist: Checklist) {}
        override suspend fun updateChecklistTemplate(checklist: Checklist) {}
        override suspend fun deleteChecklist(checklist: Checklist) {}
        override suspend fun getChecklistById(id: Long): Checklist? = storedChecklist
        override fun observeChecklistById(id: Long): Flow<Checklist?> = flowOf(storedChecklist)
        override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setFoldersEnabled(checklistId: Long, value: Boolean) {}
        override suspend fun setReminder(checklistId: Long, reminderAt: Long?) {}
        override suspend fun countActiveReminders(): Int = 0
        override suspend fun getActiveReminders(): List<ChecklistReminderInfo> = emptyList()
        override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? = defaultFillFlow.value
        override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = defaultFillFlow
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> =
            flowOf(emptyList())
        override suspend fun getFillById(id: Long): ChecklistFill? = null
        override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun addFill(fill: ChecklistFill): Long = 1L
        override suspend fun updateFill(fill: ChecklistFill) {}
        override suspend fun deleteFill(fill: ChecklistFill) {}
        override suspend fun reorderChecklists(orderedIds: List<Long>) {}

        // Independent repeat schedule
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
        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
        override fun onBack() {}
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

    private class FakePaywallRepository : PaywallRepository {
        override val subscriptionStatus: Flow<SubscriptionStatus> = flowOf(SubscriptionStatus.FREE)
        override suspend fun getOfferings(offeringId: String): Result<PaywallOffering?> = Result.success(null)
        override suspend fun purchase(packageId: String): PurchaseResult =
            PurchaseResult.Error("not implemented")
        override suspend fun restorePurchases(): RestoreResult =
            RestoreResult.Error("not implemented")
        override suspend fun refreshSubscriptionStatus() {}
        override fun isConfigured(): Boolean = true
        override suspend fun logIn(appUserId: String): Result<LoginResult> =
            Result.failure(NotImplementedError())
        override suspend fun logOut(): Result<SubscriptionStatus> =
            Result.failure(NotImplementedError())
    }

    private class FakeReminderScheduler : ChecklistReminderScheduler {
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

    private class FakeSmartDateParser : com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.SmartDateParser {
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
