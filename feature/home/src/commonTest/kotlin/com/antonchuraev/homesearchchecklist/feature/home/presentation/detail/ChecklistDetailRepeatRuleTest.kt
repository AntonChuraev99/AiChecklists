package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.navigation.api.NavCommand
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.Entitlements
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
                paywallRepository,
                FakeUserDataRepository()
            ),
            analyticsTracker = analyticsTracker,
            reminderScheduler = scheduler,
            datastore = datastore,
            smartDateParser = FakeSmartDateParser(),
            attachmentStorage = FakeAttachmentStorage(),
        )
    }

    private fun contentState(vm: ChecklistDetailViewModel): ChecklistDetailState.Content {
        return vm.screenState.value as ChecklistDetailState.Content
    }

    // --- Open repeat rule sheet ---

    @Test
    fun onRepeatRuleClick_opensSheet_withDefaultConfig() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))

        val state = contentState(vm)
        assertNotNull(state.pendingRepeatConfig)
        val config = checkNotNull(state.pendingRepeatConfig)
        assertEquals(RepeatType.DAILY, config.type)
        assertEquals(1, config.interval)
    }

    @Test
    fun onRepeatRuleClick_closesReminderSheet() = runTest {
        val vm = createViewModel()
        // Open reminder sheet then switch to repeat tab
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))

        val state = contentState(vm)
        // Repeat is shown as a tab inside the reminder sheet
        assertTrue(state.showReminderSheet)
        assertNotNull(state.pendingRepeatConfig)
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
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))

        val config = contentState(vm).pendingRepeatConfig!!
        assertEquals(RepeatType.WEEKLY, config.type)
        assertEquals(2, config.interval)
        assertEquals(setOf(1, 3, 5), config.weekDays)
        assertTrue(config.resetChecks)
    }

    @Test
    fun onReminderClick_withActiveRepeat_opensRepeatTabWithSavedTime() = runTest {
        // Bug regression: opening reminder sheet when repeat is active must populate
        // pendingRepeatConfig from existing rule, not default 09:00. Otherwise the
        // CurrentRepeatCard and Time-of-day picker show 09:00 even though the saved
        // time is different.
        val rule = ReminderRepeatRule(
            type = RepeatType.DAILY,
            interval = 1
        )
        val savedTimeMinutes = 12 * 60 // 12:00
        repository.storedChecklist = testChecklist.copy(
            repeatRule = rule,
            repeatTimeOfDayMinutes = savedTimeMinutes,
            repeatNextAt = 9_999_999_999L // active repeat → defaultTab = REPEAT
        )

        val vm = createViewModel()
        // Single intent — no manual tab switch. Sheet opens directly on REPEAT tab.
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)

        val state = contentState(vm)
        assertEquals(ReminderTab.REPEAT, state.activeReminderTab)
        val config = state.pendingRepeatConfig
        assertNotNull(config, "pendingRepeatConfig must be initialised when sheet opens on REPEAT tab")
        assertEquals(12, config.timeHour)
        assertEquals(0, config.timeMinute)
        assertEquals(RepeatType.DAILY, config.type)
    }

    // --- Type selection ---

    @Test
    fun onRepeatTypeSelected_daily_updatesConfig() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        vm.onIntent(ChecklistDetailIntent.OnRepeatTypeSelected(RepeatType.DAILY))

        val config = contentState(vm).pendingRepeatConfig!!
        assertEquals(RepeatType.DAILY, config.type)
        assertEquals(1, config.interval)
    }

    @Test
    fun onRepeatTypeSelected_weekly_updatesConfig() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        vm.onIntent(ChecklistDetailIntent.OnRepeatTypeSelected(RepeatType.WEEKLY))

        assertEquals(RepeatType.WEEKLY, contentState(vm).pendingRepeatConfig!!.type)
    }

    // --- Interval ---

    @Test
    fun onRepeatIntervalChanged_updatesConfig() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        vm.onIntent(ChecklistDetailIntent.OnRepeatIntervalChanged(3))

        assertEquals(3, contentState(vm).pendingRepeatConfig!!.interval)
    }

    @Test
    fun onRepeatIntervalChanged_clampsToRange() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))

        vm.onIntent(ChecklistDetailIntent.OnRepeatIntervalChanged(0))
        assertEquals(1, contentState(vm).pendingRepeatConfig!!.interval)

        vm.onIntent(ChecklistDetailIntent.OnRepeatIntervalChanged(150))
        assertEquals(99, contentState(vm).pendingRepeatConfig!!.interval)
    }

    // --- Weekday toggle ---

    @Test
    fun onWeekDayToggled_addsDay() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        vm.onIntent(ChecklistDetailIntent.OnWeekDayToggled(1)) // Monday

        assertTrue(1 in contentState(vm).pendingRepeatConfig!!.weekDays)
    }

    @Test
    fun onWeekDayToggled_removesDay() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        vm.onIntent(ChecklistDetailIntent.OnWeekDayToggled(1))
        vm.onIntent(ChecklistDetailIntent.OnWeekDayToggled(1))

        assertFalse(1 in contentState(vm).pendingRepeatConfig!!.weekDays)
    }

    // --- Reset checks toggle ---

    @Test
    fun onResetChecksToggled_updatesConfig() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        vm.onIntent(ChecklistDetailIntent.OnResetChecksToggled(true))

        assertTrue(contentState(vm).pendingRepeatConfig!!.resetChecks)
    }

    // --- End condition ---

    @Test
    fun onEndConditionClick_showsPicker() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        vm.onIntent(ChecklistDetailIntent.OnEndConditionClick)

        assertTrue(contentState(vm).showEndConditionPicker)
    }

    @Test
    fun onEndConditionSelected_updatesConfig_closesPicker() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))
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
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        vm.onIntent(ChecklistDetailIntent.OnDismissReminderUI)

        val state = contentState(vm)
        assertFalse(state.showReminderSheet)
        assertNull(state.pendingRepeatConfig)
        assertFalse(state.showEndConditionPicker)
    }

    // --- Save repeat rule on existing reminder ---

    @Test
    fun onSaveRepeatRule_withExistingReminder_updatesRepository() = runTest {
        repository.storedChecklist = testChecklist.copy(reminderAt = 5000L)

        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        vm.onIntent(ChecklistDetailIntent.OnRepeatTypeSelected(RepeatType.WEEKLY))
        vm.onIntent(ChecklistDetailIntent.OnWeekDayToggled(1))
        vm.onIntent(ChecklistDetailIntent.OnWeekDayToggled(3))
        vm.onIntent(ChecklistDetailIntent.OnSaveRepeatSchedule)

        val state = contentState(vm)
        assertFalse(state.showReminderSheet)
        assertNull(state.pendingRepeatConfig)
        assertNotNull(state.checklist.repeatRule)
        assertEquals(RepeatType.WEEKLY, state.checklist.repeatRule!!.type)
        assertEquals(setOf(1, 3), state.checklist.repeatRule!!.weekDays)
        assertEquals(0, state.checklist.repeatOccurrenceCount) // Reset on rule change
    }

    @Test
    fun onSaveRepeatRule_withoutReminder_savesRepeatSchedule() = runTest {
        // No reminder set — repeat saves independently
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        vm.onIntent(ChecklistDetailIntent.OnRepeatTypeSelected(RepeatType.DAILY))
        vm.onIntent(ChecklistDetailIntent.OnSaveRepeatSchedule)

        val state = contentState(vm)
        assertFalse(state.showReminderSheet)
        assertNotNull(state.repeatRuleSummary) // Summary saved
        // Repeat schedule is persisted (independent of reminder)
        assertNotNull(repository.lastRepeatSchedule)
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
        repository.repeatScheduleCount = 1 // Already at limit
        val navigator = FakeAppNavigator()
        val vm = createViewModel(navigator = navigator)
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))



        assertEquals("detail_recurring_limit", navigator.lastPaywallSource)
        // Pending config should NOT be initialized
        val state = contentState(vm)
        assertNull(state.pendingRepeatConfig)
    }

    @Test
    fun onRepeatRuleClick_freeUser_noExistingRecurring_opensSheet() = runTest {
        repository.repeatScheduleCount = 0
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))



        val state = contentState(vm)
        assertNotNull(state.pendingRepeatConfig)
    }

    @Test
    fun onRepeatRuleClick_premiumUser_alwaysOpensSheet() = runTest {
        repository.repeatScheduleCount = 10 // Many recurring, but premium
        val premiumStatus = SubscriptionStatus(
            isActive = true,
            activeEntitlements = setOf(Entitlements.PREMIUM)
        )
        val vm = createViewModel(
            paywallRepository = FakePaywallRepository(premiumStatus)
        )
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))



        val state = contentState(vm)
        assertNotNull(state.pendingRepeatConfig)
    }

    @Test
    fun onRepeatRuleClick_freeUser_editingExistingRule_skipsLimitCheck() = runTest {
        // Even at limit, editing existing recurring should be allowed
        repository.repeatScheduleCount = 1
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
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))



        // Pending config should be initialized (editing, not creating new)
        val state = contentState(vm)
        assertNotNull(state.pendingRepeatConfig)
        assertNull(navigator.lastPaywallSource)
    }

    // --- Analytics ---

    @Test
    fun onRepeatRuleClick_limitHit_tracksAnalytics() = runTest {
        repository.repeatScheduleCount = 1
        val tracker = FakeAnalyticsTracker()
        val vm = createViewModel(analyticsTracker = tracker)
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))



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

    // ─── Analytics params tests ─────────────────────────────

    @Test
    fun saveRepeatSchedule_tracksPresetName_daily() = runTest {
        val tracker = FakeAnalyticsTracker()
        val vm = createViewModel(analyticsTracker = tracker)
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        vm.onIntent(ChecklistDetailIntent.OnSmartPresetSelected(
            PendingRepeatConfig(type = RepeatType.DAILY, interval = 1)
        ))
        vm.onIntent(ChecklistDetailIntent.OnSaveRepeatSchedule)

        val event = tracker.events.find { it.first == "repeat_schedule_set" }
        assertNotNull(event)
        assertEquals("daily", event.second["preset"])
        assertEquals("false", event.second["is_edit"])
        assertEquals("Never", event.second["end_condition"])
    }

    @Test
    fun saveRepeatSchedule_tracksPresetName_weekdays() = runTest {
        val tracker = FakeAnalyticsTracker()
        val vm = createViewModel(analyticsTracker = tracker)
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        vm.onIntent(ChecklistDetailIntent.OnSmartPresetSelected(
            PendingRepeatConfig(type = RepeatType.WEEKLY, interval = 1, weekDays = setOf(1, 2, 3, 4, 5))
        ))
        vm.onIntent(ChecklistDetailIntent.OnSaveRepeatSchedule)

        val event = tracker.events.find { it.first == "repeat_schedule_set" }
        assertNotNull(event)
        assertEquals("weekdays", event.second["preset"])
        assertEquals("1,2,3,4,5", event.second["week_days"])
    }

    @Test
    fun saveRepeatSchedule_tracksPresetName_custom() = runTest {
        val tracker = FakeAnalyticsTracker()
        val vm = createViewModel(analyticsTracker = tracker)
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        vm.onIntent(ChecklistDetailIntent.OnSmartPresetSelected(
            PendingRepeatConfig(type = RepeatType.DAILY, interval = 1, isCustom = true)
        ))
        vm.onIntent(ChecklistDetailIntent.OnSaveRepeatSchedule)

        val event = tracker.events.find { it.first == "repeat_schedule_set" }
        assertNotNull(event)
        assertEquals("custom", event.second["preset"])
    }

    @Test
    fun saveRepeatSchedule_existingRule_tracksIsEditTrue() = runTest {
        repository.storedChecklist = testChecklist.copy(
            repeatRule = ReminderRepeatRule(type = RepeatType.DAILY, interval = 1),
            repeatOccurrenceCount = 3
        )
        val tracker = FakeAnalyticsTracker()
        val vm = createViewModel(analyticsTracker = tracker)
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        vm.onIntent(ChecklistDetailIntent.OnSmartPresetSelected(
            PendingRepeatConfig(type = RepeatType.WEEKLY, interval = 2)
        ))
        vm.onIntent(ChecklistDetailIntent.OnSaveRepeatSchedule)

        val event = tracker.events.find { it.first == "repeat_schedule_set" }
        assertNotNull(event)
        assertEquals("true", event.second["is_edit"])
        assertEquals("biweekly", event.second["preset"])
    }

    @Test
    fun removeRepeatSchedule_tracksTotalOccurrences() = runTest {
        repository.storedChecklist = testChecklist.copy(
            repeatRule = ReminderRepeatRule(type = RepeatType.DAILY, interval = 1),
            repeatOccurrenceCount = 7
        )
        val tracker = FakeAnalyticsTracker()
        val vm = createViewModel(analyticsTracker = tracker)
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        vm.onIntent(ChecklistDetailIntent.OnRemoveRepeatSchedule)

        val event = tracker.events.find { it.first == "repeat_schedule_cancelled" }
        assertNotNull(event)
        assertEquals("7", event.second["total_occurrences"])
    }

    // ─── isCustom fix tests ──────────────────────────────────

    @Test
    fun intervalChange_afterPreset_setsIsCustomTrue() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        vm.onIntent(ChecklistDetailIntent.OnSmartPresetSelected(
            PendingRepeatConfig(type = RepeatType.DAILY, interval = 1)
        ))
        assertFalse(contentState(vm).pendingRepeatConfig!!.isCustom)

        vm.onIntent(ChecklistDetailIntent.OnRepeatIntervalChanged(3))
        assertTrue(contentState(vm).pendingRepeatConfig!!.isCustom)
    }

    @Test
    fun weekDayToggle_afterPreset_setsIsCustomTrue() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        vm.onIntent(ChecklistDetailIntent.OnSmartPresetSelected(
            PendingRepeatConfig(type = RepeatType.WEEKLY, interval = 1, weekDays = setOf(1, 2, 3, 4, 5))
        ))
        assertFalse(contentState(vm).pendingRepeatConfig!!.isCustom)

        vm.onIntent(ChecklistDetailIntent.OnWeekDayToggled(5)) // Remove Friday
        assertTrue(contentState(vm).pendingRepeatConfig!!.isCustom)
    }

    // ─── Smart preset tests ────────────────────────────────

    @Test
    fun smartPreset_weekdays_setsCorrectConfig() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))

        val preset = PendingRepeatConfig(
            type = RepeatType.WEEKLY,
            interval = 1,
            weekDays = setOf(1, 2, 3, 4, 5)
        )
        vm.onIntent(ChecklistDetailIntent.OnSmartPresetSelected(preset))

        val state = vm.screenState.value as ChecklistDetailState.Content
        val config = state.pendingRepeatConfig
        assertNotNull(config)
        assertEquals(RepeatType.WEEKLY, config.type)
        assertEquals(1, config.interval)
        assertEquals(setOf(1, 2, 3, 4, 5), config.weekDays)
        assertFalse(config.isCustom)
    }

    @Test
    fun smartPreset_biweekly_setsCorrectConfig() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))

        val preset = PendingRepeatConfig(
            type = RepeatType.WEEKLY,
            interval = 2
        )
        vm.onIntent(ChecklistDetailIntent.OnSmartPresetSelected(preset))

        val state = vm.screenState.value as ChecklistDetailState.Content
        val config = state.pendingRepeatConfig
        assertNotNull(config)
        assertEquals(RepeatType.WEEKLY, config.type)
        assertEquals(2, config.interval)
        assertTrue(config.weekDays.isEmpty())
    }

    @Test
    fun smartPreset_quarterly_setsCorrectConfig() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))

        val preset = PendingRepeatConfig(
            type = RepeatType.MONTHLY,
            interval = 3
        )
        vm.onIntent(ChecklistDetailIntent.OnSmartPresetSelected(preset))

        val state = vm.screenState.value as ChecklistDetailState.Content
        val config = state.pendingRepeatConfig
        assertNotNull(config)
        assertEquals(RepeatType.MONTHLY, config.type)
        assertEquals(3, config.interval)
    }

    @Test
    fun smartPreset_yearly_setsCorrectConfig() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))

        vm.onIntent(ChecklistDetailIntent.OnRepeatTypeSelected(RepeatType.YEARLY))

        val state = vm.screenState.value as ChecklistDetailState.Content
        val config = state.pendingRepeatConfig
        assertNotNull(config)
        assertEquals(RepeatType.YEARLY, config.type)
        assertEquals(1, config.interval)
        assertTrue(config.weekDays.isEmpty())
    }

    @Test
    fun smartPreset_weekdays_savesCorrectRule() = runTest {
        repository.storedChecklist = testChecklist.copy(reminderAt = 1000L)
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))

        val preset = PendingRepeatConfig(
            type = RepeatType.WEEKLY,
            interval = 1,
            weekDays = setOf(1, 2, 3, 4, 5)
        )
        vm.onIntent(ChecklistDetailIntent.OnSmartPresetSelected(preset))
        vm.onIntent(ChecklistDetailIntent.OnSaveRepeatSchedule)

        val saved = repository.lastRepeatSchedule
        assertNotNull(saved)
        val rule = saved.second
        assertNotNull(rule)
        assertEquals(RepeatType.WEEKLY, rule.type)
        assertEquals(setOf(1, 2, 3, 4, 5), rule.weekDays)
    }

    @Test
    fun smartPreset_yearly_savesCorrectRule() = runTest {
        repository.storedChecklist = testChecklist.copy(reminderAt = 1000L)
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnReminderClick)
        vm.onIntent(ChecklistDetailIntent.OnReminderTabSelected(ReminderTab.REPEAT))

        vm.onIntent(ChecklistDetailIntent.OnRepeatTypeSelected(RepeatType.YEARLY))
        vm.onIntent(ChecklistDetailIntent.OnSaveRepeatSchedule)

        val saved = repository.lastRepeatSchedule
        assertNotNull(saved)
        val rule = saved.second
        assertNotNull(rule)
        assertEquals(RepeatType.YEARLY, rule.type)
        assertEquals(1, rule.interval)
        assertNull(rule.weekDays)
    }

    // --- Test doubles ---

    private class FakeAnalyticsTracker : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {
            events.add(name to params)
        }
    }

    private class FakeChecklistRepository : ChecklistRepository {
        var storedChecklist: Checklist? = null
        val defaultFillFlow = MutableStateFlow<ChecklistFill?>(null)
        // Tracks calls to setRepeatSchedule: (checklistId, rule, timeOfDayMinutes, firstTriggerAt)
        var lastRepeatSchedule: Pair<Long, ReminderRepeatRule>? = null

        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())
        override suspend fun addChecklist(checklist: Checklist): Long = 1L
        override suspend fun updateChecklist(checklist: Checklist) {}
        override suspend fun updateChecklistTemplate(checklist: Checklist) {}
        override suspend fun deleteChecklist(checklist: Checklist) {}
        override suspend fun getChecklistById(id: Long): Checklist? = storedChecklist
        override fun observeChecklistById(id: Long): Flow<Checklist?> = flowOf(storedChecklist)
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
        override suspend fun setRepeatSchedule(checklistId: Long, rule: ReminderRepeatRule, timeOfDayMinutes: Int, firstTriggerAt: Long) {
            lastRepeatSchedule = checklistId to rule
        }
        override suspend fun advanceRepeatSchedule(checklistId: Long, nextAt: Long?, newCount: Int) {}
        override suspend fun clearRepeatSchedule(checklistId: Long) {}
        override suspend fun resetDefaultFillChecks(checklistId: Long) {}
        var repeatScheduleCount: Int = 0
        override suspend fun countActiveRepeatSchedules(): Int = repeatScheduleCount
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
        override val commands: Flow<NavCommand> = emptyFlow()
        override val events: SharedFlow<AppNavEvent> = MutableSharedFlow()
        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
        override fun onBack() {}
        override fun navigateToOnboarding() {}
        override fun navigateToInteractiveOnboarding() {}
        override fun navigateToMainScreen(clearBackStack: Boolean) {}
        override fun navigateToDebugMenu() {}
        override fun navigateToStoreScreenshot() {}
        override fun navigateToCreateChecklistScreen(templateId: Int?) {}
        override fun navigateToEditChecklist(checklistId: Long) {}
        override fun navigateToTemplatesScreen() {}
        override fun navigateToTemplatePreview(templateId: String) {}
        override fun navigateToAnalyzeScreen(checklistId: Long?, fillDefault: Boolean) {}
        override fun navigateToAnalyzeResultPreview() {}
        override fun navigateToChecklistDetail(checklistId: Long, focusItemId: String?, clearBackStack: Boolean) {}
        override fun navigateToFillDetail(fillId: Long, clearBackStack: Boolean) {}
        override fun navigateToFillsList(checklistId: Long) {}
        var lastPaywallSource: String? = null
        override fun navigateToPaywall(source: String) { lastPaywallSource = source }
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
        override fun scheduleReminder(checklistId: Long, triggerAtMillis: Long) {
            scheduled.add(checklistId to triggerAtMillis)
        }
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
