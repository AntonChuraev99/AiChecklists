package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.navigation.NavController
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.Entitlements
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Tests repeat rule configuration intents in ChecklistDetailViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistDetailRepeatRuleTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeChecklistRepository
    private lateinit var scheduler: FakeReminderScheduler

    private val testChecklist = Checklist(
        id = 1L,
        name = "Test Checklist",
        items = listOf(
            ChecklistItem("Item 1"),
            ChecklistItem("Item 2")
        )
    )

    private val testFill = ChecklistFill(
        id = 1L,
        checklistId = 1L,
        name = "",
        items = listOf(
            ChecklistFillItem("Item 1", checked = false),
            ChecklistFillItem("Item 2", checked = false)
        ),
        createdAt = 0L,
        isDefault = true
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeChecklistRepository().apply {
            storedChecklist = testChecklist
            defaultFillFlow.value = testFill
        }
        scheduler = FakeReminderScheduler()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        navigator: FakeAppNavigator = FakeAppNavigator(),
        analyticsTracker: FakeAnalyticsTracker = FakeAnalyticsTracker(),
        paywallRepository: FakePaywallRepository = FakePaywallRepository()
    ): ChecklistDetailViewModel {
        val datastore = AppDatastore(
            PreferenceDataStoreFactory.createWithPath {
                "build/test_prefs_repeat_${Random.nextLong()}.preferences_pb".toPath()
            },
            testDispatcher
        )

        return ChecklistDetailViewModel(
            checklistId = 1L,
            repository = repository,
            navigator = navigator,
            getUserLimitsUseCase = GetUserLimitsUseCase(
                FakeRemoteConfigProvider(),
                repository,
                paywallRepository
            ),
            analyticsTracker = analyticsTracker,
            reminderScheduler = scheduler,
            datastore = datastore
        )
    }

    private fun contentState(vm: ChecklistDetailViewModel): ChecklistDetailState.Content {
        return vm.screenState.value as ChecklistDetailState.Content
    }

    // --- Open repeat rule sheet ---

    @Test
    fun onRepeatRuleClick_opensSheet_withDefaultConfig() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)

        val state = contentState(vm)
        assertTrue(state.showRepeatRuleSheet)
        val config = checkNotNull(state.pendingRepeatConfig)
        assertEquals(RepeatType.DAILY, config.type)
        assertEquals(1, config.interval)
    }

    @Test
    fun onRepeatRuleClick_closesReminderSheet() = runTest {
        val vm = createViewModel()
        // Simulate reminder sheet is open
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)

        val state = contentState(vm)
        assertFalse(state.showReminderSheet)
        assertTrue(state.showRepeatRuleSheet)
    }

    @Test
    fun onRepeatRuleClick_withExistingRule_populatesConfig() = runTest {
        val rule = ReminderRepeatRule(
            type = RepeatType.WEEKLY,
            interval = 2,
            weekDays = setOf(1, 3, 5),
            resetChecks = true
        )
        repository.storedChecklist = testChecklist.copy(
            reminderAt = 1000L,
            repeatRule = rule
        )

        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)

        val config = contentState(vm).pendingRepeatConfig!!
        assertEquals(RepeatType.WEEKLY, config.type)
        assertEquals(2, config.interval)
        assertEquals(setOf(1, 3, 5), config.weekDays)
        assertTrue(config.resetChecks)
    }

    // --- Type selection ---

    @Test
    fun onRepeatTypeSelected_daily_updatesConfig() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)
        vm.onIntent(ChecklistDetailIntent.OnRepeatTypeSelected(RepeatType.DAILY))

        val config = contentState(vm).pendingRepeatConfig!!
        assertEquals(RepeatType.DAILY, config.type)
        assertEquals(1, config.interval)
    }

    @Test
    fun onRepeatTypeSelected_weekly_updatesConfig() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)
        vm.onIntent(ChecklistDetailIntent.OnRepeatTypeSelected(RepeatType.WEEKLY))

        assertEquals(RepeatType.WEEKLY, contentState(vm).pendingRepeatConfig!!.type)
    }

    @Test
    fun onRepeatTypeSelected_null_resetsToDefault() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)
        vm.onIntent(ChecklistDetailIntent.OnRepeatTypeSelected(RepeatType.MONTHLY))
        vm.onIntent(ChecklistDetailIntent.OnRepeatTypeSelected(null))

        val config = contentState(vm).pendingRepeatConfig!!
        assertEquals(RepeatType.DAILY, config.type)
        assertEquals(1, config.interval)
    }

    // --- Interval ---

    @Test
    fun onRepeatIntervalChanged_updatesConfig() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)
        vm.onIntent(ChecklistDetailIntent.OnRepeatIntervalChanged(3))

        assertEquals(3, contentState(vm).pendingRepeatConfig!!.interval)
    }

    @Test
    fun onRepeatIntervalChanged_clampsToRange() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)

        vm.onIntent(ChecklistDetailIntent.OnRepeatIntervalChanged(0))
        assertEquals(1, contentState(vm).pendingRepeatConfig!!.interval)

        vm.onIntent(ChecklistDetailIntent.OnRepeatIntervalChanged(150))
        assertEquals(99, contentState(vm).pendingRepeatConfig!!.interval)
    }

    // --- Weekday toggle ---

    @Test
    fun onWeekDayToggled_addsDay() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)
        vm.onIntent(ChecklistDetailIntent.OnWeekDayToggled(1)) // Monday

        assertTrue(1 in contentState(vm).pendingRepeatConfig!!.weekDays)
    }

    @Test
    fun onWeekDayToggled_removesDay() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)
        vm.onIntent(ChecklistDetailIntent.OnWeekDayToggled(1))
        vm.onIntent(ChecklistDetailIntent.OnWeekDayToggled(1))

        assertFalse(1 in contentState(vm).pendingRepeatConfig!!.weekDays)
    }

    // --- Reset checks toggle ---

    @Test
    fun onResetChecksToggled_updatesConfig() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)
        vm.onIntent(ChecklistDetailIntent.OnResetChecksToggled(true))

        assertTrue(contentState(vm).pendingRepeatConfig!!.resetChecks)
    }

    // --- End condition ---

    @Test
    fun onEndConditionClick_showsPicker() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)
        vm.onIntent(ChecklistDetailIntent.OnEndConditionClick)

        assertTrue(contentState(vm).showEndConditionPicker)
    }

    @Test
    fun onEndConditionSelected_updatesConfig_closesPicker() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)
        vm.onIntent(ChecklistDetailIntent.OnEndConditionClick)
        vm.onIntent(ChecklistDetailIntent.OnEndConditionSelected(RepeatEndCondition.AfterCount(5)))

        val state = contentState(vm)
        assertFalse(state.showEndConditionPicker)
        assertEquals(RepeatEndCondition.AfterCount(5), state.pendingRepeatConfig!!.endCondition)
    }

    // --- Dismiss ---

    @Test
    fun onDismissRepeatRuleSheet_closesAll() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)
        vm.onIntent(ChecklistDetailIntent.OnDismissRepeatRuleSheet)

        val state = contentState(vm)
        assertFalse(state.showRepeatRuleSheet)
        assertNull(state.pendingRepeatConfig)
        assertFalse(state.showEndConditionPicker)
    }

    // --- Save repeat rule on existing reminder ---

    @Test
    fun onSaveRepeatRule_withExistingReminder_updatesRepository() = runTest {
        repository.storedChecklist = testChecklist.copy(reminderAt = 5000L)

        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)
        vm.onIntent(ChecklistDetailIntent.OnRepeatTypeSelected(RepeatType.WEEKLY))
        vm.onIntent(ChecklistDetailIntent.OnWeekDayToggled(1))
        vm.onIntent(ChecklistDetailIntent.OnWeekDayToggled(3))
        vm.onIntent(ChecklistDetailIntent.OnSaveRepeatRule)

        val state = contentState(vm)
        assertFalse(state.showRepeatRuleSheet)
        assertNull(state.pendingRepeatConfig)
        assertNotNull(state.checklist.repeatRule)
        assertEquals(RepeatType.WEEKLY, state.checklist.repeatRule!!.type)
        assertEquals(setOf(1, 3), state.checklist.repeatRule!!.weekDays)
        assertEquals(0, state.checklist.repeatOccurrenceCount) // Reset on rule change
    }

    @Test
    fun onSaveRepeatRule_withoutReminder_closesSheetOnly() = runTest {
        // No reminder set
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)
        vm.onIntent(ChecklistDetailIntent.OnRepeatTypeSelected(RepeatType.DAILY))
        vm.onIntent(ChecklistDetailIntent.OnSaveRepeatRule)

        val state = contentState(vm)
        assertFalse(state.showRepeatRuleSheet)
        assertNotNull(state.repeatRuleSummary) // Summary saved for later use
        // Rule not persisted until reminder is set
        assertNull(state.checklist.repeatRule)
    }

    // --- PendingRepeatConfig.toRule() ---

    @Test
    fun pendingRepeatConfig_toRule_convertsCorrectly() {
        val config = PendingRepeatConfig(
            type = RepeatType.WEEKLY,
            interval = 2,
            weekDays = setOf(1, 5),
            endCondition = RepeatEndCondition.AfterCount(10),
            resetChecks = true
        )

        val rule = config.toRule()
        assertEquals(RepeatType.WEEKLY, rule.type)
        assertEquals(2, rule.interval)
        assertEquals(setOf(1, 5), rule.weekDays)
        assertEquals(RepeatEndCondition.AfterCount(10), rule.endCondition)
        assertTrue(rule.resetChecks)
    }

    @Test
    fun pendingRepeatConfig_toRule_clearsWeekDaysForNonWeekly() {
        val config = PendingRepeatConfig(
            type = RepeatType.DAILY,
            weekDays = setOf(1, 2, 3) // Should be ignored for daily
        )

        val rule = config.toRule()
        assertNull(rule.weekDays) // Cleaned up
    }

    // --- Recurring reminder limits ---

    @Test
    fun onRepeatRuleClick_freeUser_withExistingRecurring_navigatesToPaywall() = runTest {
        repository.recurringReminderCount = 1 // Already at limit
        val navigator = FakeAppNavigator()
        val vm = createViewModel(navigator = navigator)
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)

        // Wait for coroutine to complete


        assertEquals("detail_recurring_limit", navigator.lastPaywallSource)
        // Sheet should NOT be open
        val state = contentState(vm)
        assertFalse(state.showRepeatRuleSheet)
    }

    @Test
    fun onRepeatRuleClick_freeUser_noExistingRecurring_opensSheet() = runTest {
        repository.recurringReminderCount = 0
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)



        val state = contentState(vm)
        assertTrue(state.showRepeatRuleSheet)
    }

    @Test
    fun onRepeatRuleClick_premiumUser_alwaysOpensSheet() = runTest {
        repository.recurringReminderCount = 10 // Many recurring, but premium
        val premiumStatus = SubscriptionStatus(
            isActive = true,
            activeEntitlements = setOf(Entitlements.PREMIUM)
        )
        val vm = createViewModel(
            paywallRepository = FakePaywallRepository(premiumStatus)
        )
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)



        val state = contentState(vm)
        assertTrue(state.showRepeatRuleSheet)
    }

    @Test
    fun onRepeatRuleClick_freeUser_editingExistingRule_skipsLimitCheck() = runTest {
        // Even at limit, editing existing recurring should be allowed
        repository.recurringReminderCount = 1
        repository.storedChecklist = testChecklist.copy(
            repeatRule = ReminderRepeatRule(
                type = RepeatType.DAILY,
                interval = 1,
                endCondition = RepeatEndCondition.Never
            ),
            reminderAt = 1000L
        )
        val navigator = FakeAppNavigator()
        val vm = createViewModel(navigator = navigator)
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)



        // Sheet should open (editing, not creating new)
        val state = contentState(vm)
        assertTrue(state.showRepeatRuleSheet)
        assertNull(navigator.lastPaywallSource)
    }

    // --- Analytics ---

    @Test
    fun onRepeatRuleClick_limitHit_tracksAnalytics() = runTest {
        repository.recurringReminderCount = 1
        val tracker = FakeAnalyticsTracker()
        val vm = createViewModel(analyticsTracker = tracker)
        vm.onIntent(ChecklistDetailIntent.OnRepeatRuleClick)



        assertTrue(tracker.events.any { it.first == "recurring_limit_hit" })
    }

    @Test
    fun removeReminder_withRecurringRule_tracksRecurringCancelled() = runTest {
        repository.storedChecklist = testChecklist.copy(
            reminderAt = 1000L,
            repeatRule = ReminderRepeatRule(
                type = RepeatType.DAILY,
                interval = 1,
                endCondition = RepeatEndCondition.Never
            ),
            repeatOccurrenceCount = 5
        )
        val tracker = FakeAnalyticsTracker()
        val vm = createViewModel(analyticsTracker = tracker)
        vm.onIntent(ChecklistDetailIntent.OnRemoveReminder)



        val event = tracker.events.find { it.first == "recurring_reminder_cancelled" }
        assertNotNull(event)
        assertEquals("5", event.second["total_occurrences"])
    }

    @Test
    fun removeReminder_withoutRecurringRule_tracksRegularCancelled() = runTest {
        repository.storedChecklist = testChecklist.copy(reminderAt = 1000L)
        val tracker = FakeAnalyticsTracker()
        val vm = createViewModel(analyticsTracker = tracker)
        vm.onIntent(ChecklistDetailIntent.OnRemoveReminder)



        assertTrue(tracker.events.any { it.first == "reminder_cancelled" })
        assertFalse(tracker.events.any { it.first == "recurring_reminder_cancelled" })
    }

    // --- Test doubles ---

    private class FakeAnalyticsTracker : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun setUserId(userId: String) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {
            events.add(name to params)
        }
    }

    private class FakeChecklistRepository : ChecklistRepository {
        var storedChecklist: Checklist? = null
        val defaultFillFlow = MutableStateFlow<ChecklistFill?>(null)
        var lastReminderWithRule: Triple<Long, Long?, ReminderRepeatRule?>? = null

        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())
        override suspend fun addChecklist(checklist: Checklist): Long = 1L
        override suspend fun updateChecklist(checklist: Checklist) {}
        override suspend fun updateChecklistTemplate(checklist: Checklist) {}
        override suspend fun deleteChecklist(checklist: Checklist) {}
        override suspend fun getChecklistById(id: Long): Checklist? = storedChecklist
        override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setReminder(checklistId: Long, reminderAt: Long?) {}
        override suspend fun countActiveReminders(): Int = 0
        override suspend fun getActiveReminders(): List<ChecklistReminderInfo> = emptyList()
        override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? = defaultFillFlow.value
        override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = defaultFillFlow
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override suspend fun getFillById(id: Long): ChecklistFill? = null
        override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun addFill(fill: ChecklistFill): Long = 1L
        override suspend fun updateFill(fill: ChecklistFill) {}
        override suspend fun deleteFill(fill: ChecklistFill) {}
        override suspend fun reorderChecklists(orderedIds: List<Long>) {}
        override suspend fun setReminderWithRule(checklistId: Long, reminderAt: Long?, repeatRule: ReminderRepeatRule?) {
            lastReminderWithRule = Triple(checklistId, reminderAt, repeatRule)
        }
        override suspend fun advanceRecurringReminder(checklistId: Long, nextReminderAt: Long?, newCount: Int) {}
        override suspend fun clearRecurringReminder(checklistId: Long) {}
        override suspend fun setRepeatRule(checklistId: Long, rule: ReminderRepeatRule?) {}
        override suspend fun resetDefaultFillChecks(checklistId: Long) {}
        var recurringReminderCount: Int = 0
        override suspend fun countRecurringReminders(): Int = recurringReminderCount
        override suspend fun getPastDueRecurringReminders(nowMillis: Long): List<com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistRecurringInfo> = emptyList()
    }

    private class FakeAppNavigator : AppNavigator {
        override fun installNavController(navController: NavController) {}
        override fun onBack() {}
        override fun navigateToOnboarding() {}
        override fun navigateToMainScreen(clearBackStack: Boolean) {}
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
        var lastPaywallSource: String? = null
        override fun navigateToPaywall(source: String) { lastPaywallSource = source }
        override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) {}
        override fun navigateToShareChecklist(checklistId: Long) {}
    }

    private class FakeRemoteConfigProvider : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getString(key: String, defaultValue: String): String = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
    }

    private class FakePaywallRepository(
        status: SubscriptionStatus = SubscriptionStatus.FREE
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

    private class FakeReminderScheduler : ChecklistReminderScheduler {
        val scheduled = mutableListOf<Pair<Long, Long>>()
        override fun schedule(checklistId: Long, triggerAtMillis: Long) {
            scheduled.add(checklistId to triggerAtMillis)
        }
        override fun cancel(checklistId: Long) {}
        override suspend fun rescheduleAllActive() {}
    }
}
