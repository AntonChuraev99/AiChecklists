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
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.Entitlements
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
 * TDD tests for per-item reminder intents in ChecklistDetailViewModel (Phase 4).
 *
 * Covers:
 * - OnItemReminderClick: opens sheet for item with/without existing reminder
 * - OnSaveItemReminder: one-shot and recurring cases, prior schedule cancellation
 * - OnRemoveItemReminder: cancels both alarms, clears fields, closes sheet
 * - OnDismissItemReminderSheet: closes without persistence
 * - OnItemCheckedChange: cancels alarm when item has active reminder
 * - Free-tier gate: navigates to paywall when at limit, allows editing existing reminder
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistDetailItemReminderTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeChecklistRepository
    private lateinit var scheduler: FakeReminderScheduler
    private lateinit var navigator: FakeAppNavigator

    // A fill item with no reminder
    private val itemNoReminder = ChecklistFillItem("Task A", checked = false)

    // A fill item with an active one-shot reminder
    private val itemWithOneShotReminder = ChecklistFillItem(
        text = "Task B",
        checked = false,
    ).withReminderAt(System.currentTimeMillis() + 3_600_000L)

    // A fill item with an active repeat reminder
    private val repeatRule = ReminderRepeatRule(
        type = RepeatType.DAILY,
        interval = 1,
        weekDays = null,
        endCondition = RepeatEndCondition.Never,
        resetChecks = false
    )
    private val itemWithRepeatReminder = ChecklistFillItem(
        text = "Task C",
        checked = false,
    ).withRepeatRule(repeatRule, 9 * 60, System.currentTimeMillis() + 86_400_000L)

    private val testChecklist = Checklist(
        id = 1L,
        name = "Test",
        items = listOf(
            ChecklistItem("Task A"),
            ChecklistItem("Task B"),
            ChecklistItem("Task C"),
        )
    )

    private val testFill = ChecklistFill(
        id = 10L,
        checklistId = 1L,
        name = "",
        items = listOf(itemNoReminder, itemWithOneShotReminder, itemWithRepeatReminder),
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
        navigator = FakeAppNavigator()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        paywallRepository: FakePaywallRepository = FakePaywallRepository()
    ): ChecklistDetailViewModel {
        val datastore = AppDatastore(
            PreferenceDataStoreFactory.createWithPath {
                "build/test_prefs_item_reminder_${Random.nextLong()}.preferences_pb".toPath()
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
            analyticsTracker = FakeAnalyticsTracker(),
            reminderScheduler = scheduler,
            datastore = datastore,
            smartDateParser = FakeSmartDateParser(),
        )
    }

    private fun contentState(vm: ChecklistDetailViewModel): ChecklistDetailState.Content =
        vm.screenState.value as ChecklistDetailState.Content

    // ── OnItemReminderClick ────────────────────────────────────────────────

    @Test
    fun onItemReminderClick_opensItemSheet_forItemWithoutReminder() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemReminderClick(itemNoReminder.id))

        val state = contentState(vm)
        assertEquals(itemNoReminder.id, state.itemReminderSheetFor)
    }

    @Test
    fun onItemReminderClick_opensItemSheet_forItemWithExistingReminder() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemReminderClick(itemWithOneShotReminder.id))

        val state = contentState(vm)
        assertEquals(itemWithOneShotReminder.id, state.itemReminderSheetFor)
    }

    @Test
    fun onItemReminderClick_defaultTab_isREPEAT_whenItemHasRepeatButNoOneShot() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemReminderClick(itemWithRepeatReminder.id))

        val state = contentState(vm)
        assertEquals(itemWithRepeatReminder.id, state.itemReminderSheetFor)
        assertEquals(ReminderTab.REPEAT, state.activeItemReminderTab)
    }

    @Test
    fun onItemReminderClick_withRepeatRule_populatesPendingConfigWithSavedTime() = runTest {
        // Bug regression: opening per-item reminder sheet for an item with active
        // repeat must populate pendingRepeatConfig from item.repeatRule and
        // item.repeatTimeOfDayMinutes — otherwise CurrentRepeatCard / TimePicker
        // show default 09:00 instead of the saved value.
        val savedTimeMinutes = 12 * 60 // 12:00
        val itemWithCustomTime = ChecklistFillItem(text = "Task D", checked = false)
            .withRepeatRule(repeatRule, savedTimeMinutes, System.currentTimeMillis() + 86_400_000L)
        repository.defaultFillFlow.value = testFill.copy(
            items = testFill.items + itemWithCustomTime
        )

        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemReminderClick(itemWithCustomTime.id))

        val state = contentState(vm)
        assertEquals(ReminderTab.REPEAT, state.activeItemReminderTab)
        val config = state.pendingRepeatConfig
        assertNotNull(config, "pendingRepeatConfig must be initialised from item.repeatRule")
        assertEquals(12, config.timeHour)
        assertEquals(0, config.timeMinute)
        assertEquals(RepeatType.DAILY, config.type)
    }

    @Test
    fun onItemReminderClick_thenDismiss_clearsPendingConfig() = runTest {
        // Without clearing pendingRepeatConfig on dismiss, opening another item's
        // sheet would show the previous item's time/type.
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemReminderClick(itemWithRepeatReminder.id))
        assertNotNull(contentState(vm).pendingRepeatConfig)

        vm.onIntent(ChecklistDetailIntent.OnDismissItemReminderSheet)

        val state = contentState(vm)
        assertNull(state.itemReminderSheetFor)
        assertNull(state.pendingRepeatConfig)
        assertNull(state.repeatRuleSummary)
    }

    // ── Free-tier gate ─────────────────────────────────────────────────────

    @Test
    fun onItemReminderClick_freeUserAtLimit_opensLockedSheet_doesNotNavigatePaywall() = runTest {
        // Free user, 1 active reminder already exists, item has no reminder
        repository.activeRemindersCount = 1
        val vm = createViewModel(paywallRepository = FakePaywallRepository(SubscriptionStatus.FREE))

        vm.onIntent(ChecklistDetailIntent.OnItemReminderClick(itemNoReminder.id))

        assertNull(navigator.lastPaywallSource)
        assertEquals(itemNoReminder.id, contentState(vm).itemReminderSheetFor)
        assertTrue(contentState(vm).itemReminderSheetLocked)
    }

    @Test
    fun onItemReminderClick_freeUserAtLimit_butItemAlreadyHasReminder_opensSheet() = runTest {
        // Free user, at limit, but editing an EXISTING item reminder — should be allowed
        repository.activeRemindersCount = 1
        val vm = createViewModel(paywallRepository = FakePaywallRepository(SubscriptionStatus.FREE))

        vm.onIntent(ChecklistDetailIntent.OnItemReminderClick(itemWithOneShotReminder.id))

        // Should open sheet, NOT navigate to paywall
        assertNull(navigator.lastPaywallSource)
        assertEquals(itemWithOneShotReminder.id, contentState(vm).itemReminderSheetFor)
    }

    @Test
    fun onItemReminderClick_premiumUser_atLimit_opensSheetAnyway() = runTest {
        repository.activeRemindersCount = 5
        val premiumStatus = SubscriptionStatus(isActive = true, activeEntitlements = setOf(Entitlements.PREMIUM))
        val vm = createViewModel(paywallRepository = FakePaywallRepository(premiumStatus))

        vm.onIntent(ChecklistDetailIntent.OnItemReminderClick(itemNoReminder.id))

        assertNull(navigator.lastPaywallSource)
        assertEquals(itemNoReminder.id, contentState(vm).itemReminderSheetFor)
    }

    // ── OnSaveItemReminder (one-shot) ──────────────────────────────────────

    @Test
    fun onSaveItemReminder_oneShot_persistsAndSchedules() = runTest {
        val vm = createViewModel()
        val triggerAt = System.currentTimeMillis() + 3_600_000L
        vm.onIntent(ChecklistDetailIntent.OnItemReminderClick(itemNoReminder.id))

        vm.onIntent(ChecklistDetailIntent.OnSaveItemReminder(
            itemId = itemNoReminder.id,
            reminderAt = triggerAt,
            repeatRule = null,
            repeatTimeOfDayMinutes = null
        ))

        // Fill should be persisted with the reminder
        val savedFill = repository.lastUpdatedFill
        assertNotNull(savedFill)
        val savedItem = savedFill.items.firstOrNull { it.id == itemNoReminder.id }
        assertNotNull(savedItem)
        assertEquals(triggerAt, savedItem.reminderAt)

        // Scheduler should have been called
        assertTrue(scheduler.scheduledItemReminders.any {
            it.itemId == itemNoReminder.id && it.triggerAtMillis == triggerAt
        })

        // Sheet should be closed
        assertNull(contentState(vm).itemReminderSheetFor)
    }

    @Test
    fun onSaveItemReminder_recurring_persistsAndSchedules() = runTest {
        val vm = createViewModel()
        val rule = ReminderRepeatRule(
            type = RepeatType.DAILY,
            interval = 1,
            weekDays = null,
            endCondition = RepeatEndCondition.Never,
            resetChecks = false
        )
        vm.onIntent(ChecklistDetailIntent.OnItemReminderClick(itemNoReminder.id))

        vm.onIntent(ChecklistDetailIntent.OnSaveItemReminder(
            itemId = itemNoReminder.id,
            reminderAt = null,
            repeatRule = rule,
            repeatTimeOfDayMinutes = 9 * 60
        ))

        val savedFill = repository.lastUpdatedFill
        assertNotNull(savedFill)
        val savedItem = savedFill.items.firstOrNull { it.id == itemNoReminder.id }
        assertNotNull(savedItem)
        assertEquals(rule, savedItem.repeatRule)
        assertEquals(9 * 60, savedItem.repeatTimeOfDayMinutes)
        assertNotNull(savedItem.repeatNextAt)

        // Repeat scheduler called
        assertTrue(scheduler.scheduledItemRepeats.any { it.itemId == itemNoReminder.id })

        assertNull(contentState(vm).itemReminderSheetFor)
    }

    @Test
    fun onSaveItemReminder_switchOneShotToRecurring_cancelsPriorOneShotFirst() = runTest {
        val vm = createViewModel()
        // Item already has a one-shot reminder
        val newRule = ReminderRepeatRule(
            type = RepeatType.DAILY,
            interval = 1,
            weekDays = null,
            endCondition = RepeatEndCondition.Never,
            resetChecks = false
        )
        vm.onIntent(ChecklistDetailIntent.OnItemReminderClick(itemWithOneShotReminder.id))

        vm.onIntent(ChecklistDetailIntent.OnSaveItemReminder(
            itemId = itemWithOneShotReminder.id,
            reminderAt = null,
            repeatRule = newRule,
            repeatTimeOfDayMinutes = 9 * 60
        ))

        // Both cancel methods called for the old alarm before scheduling new
        assertTrue(scheduler.cancelledItemReminders.any { it == itemWithOneShotReminder.id })
        assertTrue(scheduler.cancelledItemRepeats.any { it == itemWithOneShotReminder.id })
        // New repeat was scheduled
        assertTrue(scheduler.scheduledItemRepeats.any { it.itemId == itemWithOneShotReminder.id })
    }

    // ── OnRemoveItemReminder ───────────────────────────────────────────────

    @Test
    fun onRemoveItemReminder_cancelsAndClearsAndClosesSheet() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemReminderClick(itemWithOneShotReminder.id))
        // Ensure sheet is open
        assertEquals(itemWithOneShotReminder.id, contentState(vm).itemReminderSheetFor)

        vm.onIntent(ChecklistDetailIntent.OnRemoveItemReminder(itemWithOneShotReminder.id))

        // Both cancel calls made (defensive)
        assertTrue(scheduler.cancelledItemReminders.any { it == itemWithOneShotReminder.id })
        assertTrue(scheduler.cancelledItemRepeats.any { it == itemWithOneShotReminder.id })

        // Persisted with cleared reminder
        val savedFill = repository.lastUpdatedFill
        assertNotNull(savedFill)
        val savedItem = savedFill.items.firstOrNull { it.id == itemWithOneShotReminder.id }
        assertNotNull(savedItem)
        assertNull(savedItem.reminderAt)
        assertNull(savedItem.repeatRule)

        // Sheet closed
        assertNull(contentState(vm).itemReminderSheetFor)
    }

    // ── OnDismissItemReminderSheet ─────────────────────────────────────────

    @Test
    fun onDismissItemReminderSheet_closesWithoutPersistence() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemReminderClick(itemNoReminder.id))
        assertEquals(itemNoReminder.id, contentState(vm).itemReminderSheetFor)

        vm.onIntent(ChecklistDetailIntent.OnDismissItemReminderSheet)

        assertNull(contentState(vm).itemReminderSheetFor)
        // No updateFill calls should have happened from dismiss alone
        assertNull(repository.lastUpdatedFill)
        assertTrue(scheduler.cancelledItemReminders.isEmpty())
        assertTrue(scheduler.cancelledItemRepeats.isEmpty())
        assertTrue(scheduler.scheduledItemReminders.isEmpty())
    }

    // ── OnItemReminderTabSelected ──────────────────────────────────────────

    @Test
    fun onItemReminderTabSelected_updatesTab() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemReminderTabSelected(ReminderTab.REPEAT))
        assertEquals(ReminderTab.REPEAT, contentState(vm).activeItemReminderTab)

        vm.onIntent(ChecklistDetailIntent.OnItemReminderTabSelected(ReminderTab.ONCE))
        assertEquals(ReminderTab.ONCE, contentState(vm).activeItemReminderTab)
    }

    // ── OnItemCheckedChange cleanup ────────────────────────────────────────

    @Test
    fun onItemCheckedChange_withActiveReminder_cancelsAlarm() = runTest {
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnItemCheckedChange(itemWithOneShotReminder.id, true))

        // Both cancel methods called
        assertTrue(scheduler.cancelledItemReminders.any { it == itemWithOneShotReminder.id })
        assertTrue(scheduler.cancelledItemRepeats.any { it == itemWithOneShotReminder.id })

        // Persisted with cleared reminder
        val savedFill = repository.lastUpdatedFill
        assertNotNull(savedFill)
        val savedItem = savedFill.items.firstOrNull { it.id == itemWithOneShotReminder.id }
        assertNotNull(savedItem)
        assertTrue(savedItem.checked)
        assertNull(savedItem.reminderAt)
    }

    @Test
    fun onItemCheckedChange_withActiveRepeatReminder_cancelsAlarm() = runTest {
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnItemCheckedChange(itemWithRepeatReminder.id, true))

        assertTrue(scheduler.cancelledItemReminders.any { it == itemWithRepeatReminder.id })
        assertTrue(scheduler.cancelledItemRepeats.any { it == itemWithRepeatReminder.id })

        val savedFill = repository.lastUpdatedFill
        assertNotNull(savedFill)
        val savedItem = savedFill.items.firstOrNull { it.id == itemWithRepeatReminder.id }
        assertNotNull(savedItem)
        assertNull(savedItem.repeatRule)
    }

    @Test
    fun onItemCheckedChange_withoutReminder_doesNotCancelAnything() = runTest {
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnItemCheckedChange(itemNoReminder.id, true))

        assertTrue(scheduler.cancelledItemReminders.isEmpty())
        assertTrue(scheduler.cancelledItemRepeats.isEmpty())
    }

    @Test
    fun onItemCheckedChange_uncheckingItem_doesNotCancelReminder() = runTest {
        val vm = createViewModel()

        // Unchecking should never cancel reminders
        vm.onIntent(ChecklistDetailIntent.OnItemCheckedChange(itemWithOneShotReminder.id, false))

        assertTrue(scheduler.cancelledItemReminders.isEmpty())
        assertTrue(scheduler.cancelledItemRepeats.isEmpty())
    }

    // ── OnItemReminderUpgradeClick ────────────────────────────────────────

    @Test
    fun onItemReminderUpgradeClick_navigatesPaywall_andClosesLockedSheet() = runTest {
        repository.activeRemindersCount = 1
        val vm = createViewModel(paywallRepository = FakePaywallRepository(SubscriptionStatus.FREE))
        vm.onIntent(ChecklistDetailIntent.OnItemReminderClick(itemNoReminder.id))
        // Verify locked sheet is open
        assertTrue(contentState(vm).itemReminderSheetLocked)

        vm.onIntent(ChecklistDetailIntent.OnItemReminderUpgradeClick)

        assertEquals("detail_item_reminder_limit", navigator.lastPaywallSource)
        assertNull(contentState(vm).itemReminderSheetFor)
        assertFalse(contentState(vm).itemReminderSheetLocked)
    }

    @Test
    fun onDismissItemReminderSheet_whenLocked_clearsLockFlag() = runTest {
        repository.activeRemindersCount = 1
        val vm = createViewModel(paywallRepository = FakePaywallRepository(SubscriptionStatus.FREE))
        vm.onIntent(ChecklistDetailIntent.OnItemReminderClick(itemNoReminder.id))
        assertTrue(contentState(vm).itemReminderSheetLocked)

        vm.onIntent(ChecklistDetailIntent.OnDismissItemReminderSheet)

        assertNull(contentState(vm).itemReminderSheetFor)
        assertFalse(contentState(vm).itemReminderSheetLocked)
        // dismiss WITHOUT upgrade does NOT navigate to paywall
        assertNull(navigator.lastPaywallSource)
    }

    // ─── Test doubles ─────────────────────────────────────────────────────

    data class ScheduledItemReminder(val checklistId: Long, val fillId: Long, val itemId: String, val triggerAtMillis: Long)
    data class ScheduledItemRepeat(val checklistId: Long, val fillId: Long, val itemId: String, val triggerAtMillis: Long)

    private class FakeReminderScheduler : ChecklistReminderScheduler {
        val scheduledItemReminders = mutableListOf<ScheduledItemReminder>()
        val scheduledItemRepeats = mutableListOf<ScheduledItemRepeat>()
        val cancelledItemReminders = mutableListOf<String>()
        val cancelledItemRepeats = mutableListOf<String>()

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
        var activeRemindersCount: Int = 0

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
        override fun navigateToChecklistDetail(checklistId: Long, clearBackStack: Boolean) {}
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
        override fun navigateToScreenCatalog() {}
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
}
