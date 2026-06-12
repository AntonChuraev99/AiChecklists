package com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ─── Time constants ───────────────────────────────────────────────────────────

/**
 * Epoch millis for 2026-05-13 12:00:00 UTC.
 * Start-of-day UTC = 2026-05-13 00:00:00 = 1_747_094_400_000
 * End-of-day UTC   = 2026-05-13 23:59:59 = 1_747_180_799_000
 */
private const val TEST_NOW_MS = 1_747_137_600_000L       // 2026-05-13 12:00 UTC
private const val START_OF_TODAY_MS = 1_747_094_400_000L  // 2026-05-13 00:00 UTC

/** Past-due: 7 days before today, at 09:00 UTC */
private const val PAST_DUE_7D_MS = START_OF_TODAY_MS - 7 * 86_400_000L + 9 * 3_600_000L

/** Past-due: yesterday at 09:00 UTC (clearly < startOfToday) */
private const val YESTERDAY_9AM_MS = START_OF_TODAY_MS - 15 * 3_600_000L

/** Today at 15:00 UTC (> TEST_NOW_MS) */
private const val TODAY_3PM_MS = START_OF_TODAY_MS + 15 * 3_600_000L

/** Tomorrow at 09:00 UTC */
private const val TOMORROW_9AM_MS = START_OF_TODAY_MS + 25 * 3_600_000L

/** Day after tomorrow at 10:00 UTC */
private const val DAY_AFTER_9AM_MS = START_OF_TODAY_MS + 49 * 3_600_000L

// ─── Fakes ───────────────────────────────────────────────────────────────────

/**
 * Fake ChecklistRepository with a mutable reminders flow.
 *
 * [remindersFlow] can be swapped via [emitReminders] to simulate reactive updates.
 */
private open class FakeChecklistRepository : ChecklistRepository {

    private val _remindersSharedFlow = MutableSharedFlow<List<TodayReminderInfo>>(replay = 1)

    suspend fun emitReminders(list: List<TodayReminderInfo>) {
        _remindersSharedFlow.emit(list)
    }

    // Default: start with empty list
    init {
        _remindersSharedFlow.tryEmit(emptyList())
    }

    override val checklists: Flow<List<Checklist>> = flowOf(emptyList())
    override suspend fun addChecklist(checklist: Checklist): Long = 0L
    override suspend fun updateChecklist(checklist: Checklist) {}
    override suspend fun updateChecklistTemplate(checklist: Checklist) {}
    override suspend fun deleteChecklist(checklist: Checklist) {}
    override suspend fun getChecklistById(id: Long): Checklist? = null
    override fun observeChecklistById(id: Long): Flow<Checklist?> = flowOf(null)
    override suspend fun reorderChecklists(orderedIds: List<Long>) {}
    override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {}
    override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) {}
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
    override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
    override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = flowOf(null)
    override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
    override suspend fun getFillById(id: Long): ChecklistFill? = null
    override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
    override suspend fun addFill(fill: ChecklistFill): Long = 0L
    override suspend fun updateFill(fill: ChecklistFill) {}
    override suspend fun deleteFill(fill: ChecklistFill) {}
    override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<TodayReminderInfo>> =
        _remindersSharedFlow
    override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<TodayReminderInfo> =
        _remindersSharedFlow.replayCache.firstOrNull() ?: emptyList()
    override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
    override suspend fun addAttachment(fillId: Long, itemId: String, attachment: com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment) = Unit
    override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) = Unit
}

private data class ChecklistDetailCall(val checklistId: Long, val focusItemId: String?)

private class FakeNavigator : AppNavigator {
    val checklistDetailCalls = mutableListOf<ChecklistDetailCall>()
    val navigatedFillIds = mutableListOf<Long>()
    var navigatedToTemplates = false

    /** Convenience: all checklist IDs navigated to (ignoring focusItemId). */
    val navigatedChecklistIds: List<Long> get() = checklistDetailCalls.map { it.checklistId }

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
    override fun navigateToTemplatesScreen() { navigatedToTemplates = true }
    override fun navigateToTemplatePreview(templateId: String) {}
    override fun navigateToAnalyzeScreen(checklistId: Long?, fillDefault: Boolean, initialText: String?) {}
    override fun navigateToAnalyzeResultPreview() {}
    override fun navigateToChecklistDetail(checklistId: Long, focusItemId: String?, clearBackStack: Boolean) {
        checklistDetailCalls.add(ChecklistDetailCall(checklistId, focusItemId))
    }
    override fun navigateToFillDetail(fillId: Long, clearBackStack: Boolean) {
        navigatedFillIds.add(fillId)
    }
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

private class FakeAppLogger : AppLogger {
    val errors = mutableListOf<Pair<String, Throwable?>>()
    override fun debug(tag: String, message: String) {}
    override fun info(tag: String, message: String) {}
    override fun warning(tag: String, message: String) {}
    override fun error(tag: String, message: String, throwable: Throwable?) {
        errors.add(message to throwable)
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun checklistReminder(
    checklistId: Long = 1L,
    name: String = "My Checklist",
    reminderAt: Long,
    isRecurring: Boolean = false,
) = TodayReminderInfo.ChecklistLevel(
    checklistId = checklistId,
    checklistName = name,
    reminderAt = reminderAt,
    isRecurring = isRecurring,
)

private fun itemReminder(
    checklistId: Long = 1L,
    fillId: Long = 10L,
    itemId: String = "item_1",
    itemText: String = "Buy milk",
    reminderAt: Long,
    isRecurring: Boolean = false,
) = TodayReminderInfo.ItemLevel(
    checklistId = checklistId,
    checklistName = "Groceries",
    fillId = fillId,
    itemId = itemId,
    itemText = itemText,
    reminderAt = reminderAt,
    isRecurring = isRecurring,
)

// ─── Tests ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeChecklistRepository
    private lateinit var navigator: FakeNavigator
    private lateinit var logger: FakeAppLogger

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeChecklistRepository()
        navigator = FakeNavigator()
        logger = FakeAppLogger()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Subscribes to [CalendarViewModel.screenState] and suspends until the first
     * non-Loading emission. Required because [AppViewModel.defaultStateIn] uses
     * WhileSubscribed — reading .value before subscribing returns Loading forever.
     */
    private suspend fun CalendarViewModel.awaitState(): CalendarState =
        screenState.first { it !is CalendarState.Loading }

    private fun buildVm(): CalendarViewModel = CalendarViewModel(
        repository = repo,
        appNavigator = navigator,
        logger = logger,
        // Pin the clock to a known timestamp so tests don't break when run on
        // different machines or after the real calendar date advances past the
        // test's fixed constants.
        clock = { TEST_NOW_MS },
    )

    // ── 1. Happy path: 5 reminders across today/tomorrow/Friday ─────────────

    @Test
    fun happyPath_fiveReminders_producesCorrectDateHeadersAndRows() = runTest {
        val reminders = listOf(
            checklistReminder(checklistId = 1L, reminderAt = TODAY_3PM_MS),
            checklistReminder(checklistId = 2L, reminderAt = TODAY_3PM_MS + 3_600_000L),
            checklistReminder(checklistId = 3L, reminderAt = TOMORROW_9AM_MS),
            checklistReminder(checklistId = 4L, reminderAt = TOMORROW_9AM_MS + 3_600_000L),
            checklistReminder(checklistId = 5L, reminderAt = DAY_AFTER_9AM_MS),
        )
        repo.emitReminders(reminders)
        val vm = buildVm()
        val state = vm.awaitState()

        assertIs<CalendarState.Content>(state)
        // Headers: Today + Tomorrow + Day after = 3 date headers + 5 rows = 8 items
        val headers = state.agenda.filterIsInstance<AgendaItem.DateHeader>()
        val rows = state.agenda.filterIsInstance<AgendaItem.ReminderRow>()
        assertEquals(3, headers.size, "Expected 3 DateHeaders (today, tomorrow, day after)")
        assertEquals(5, rows.size, "Expected 5 ReminderRows")
        // Today header always present
        val todayHeader = headers.first { !it.isPastDue }
        assertTrue(todayHeader.label.isNotBlank())
    }

    // ── 2. Mixed checklist-level + item-level reminders ──────────────────────

    @Test
    fun happyPath_mixedReminderTypes_bothAppearInAgenda() = runTest {
        val reminders = listOf(
            checklistReminder(checklistId = 1L, reminderAt = TODAY_3PM_MS),
            itemReminder(fillId = 10L, itemId = "i1", reminderAt = TOMORROW_9AM_MS),
        )
        repo.emitReminders(reminders)
        val vm = buildVm()
        val state = vm.awaitState()

        assertIs<CalendarState.Content>(state)
        val rows = state.agenda.filterIsInstance<AgendaItem.ReminderRow>()
        assertEquals(2, rows.size)
        assertTrue(rows.any { it.info is TodayReminderInfo.ChecklistLevel })
        assertTrue(rows.any { it.info is TodayReminderInfo.ItemLevel })
    }

    // ── 3. Edge: 0 reminders → Empty (not Content with empty agenda) ─────────

    @Test
    fun edge_noReminders_producesEmptyState() = runTest {
        repo.emitReminders(emptyList())
        val vm = buildVm()
        val state = vm.awaitState()

        assertIs<CalendarState.Empty>(state)
    }

    // ── 4. Edge: past-due reminders → single "Past due" header, listed first ─

    @Test
    fun edge_pastDueReminders_groupedUnderSinglePastDueHeader_listedFirst() = runTest {
        val reminders = listOf(
            checklistReminder(checklistId = 1L, reminderAt = YESTERDAY_9AM_MS),
            checklistReminder(checklistId = 2L, reminderAt = PAST_DUE_7D_MS),
        )
        repo.emitReminders(reminders)
        val vm = buildVm()
        val state = vm.awaitState()

        assertIs<CalendarState.Content>(state)
        val items = state.agenda
        // First item must be "Past due" header
        val firstItem = items.first()
        assertIs<AgendaItem.DateHeader>(firstItem)
        assertTrue(firstItem.isPastDue, "First header must be the Past due header")
        assertEquals(CalendarViewModel.PAST_DUE_LABEL, firstItem.label)
        // Both past-due reminders follow immediately
        val pastDueRows = items.drop(1).takeWhile { it is AgendaItem.ReminderRow }
        assertEquals(2, pastDueRows.size, "Both past-due reminders should be under the Past due header")
    }

    // ── 5. Edge: reminder exactly at startOfToday midnight → Today, not Past due

    @Test
    fun edge_reminderAtStartOfToday_classifiedAsUpcoming() = runTest {
        // buildAgenda uses startOfDayMs logic; we feed a reminder at the UTC
        // midnight of the test date and verify it does NOT land in past-due group.
        // Since the VM uses currentTimeMillis() which is live, we test the pure
        // buildAgenda helper directly (it is internal).
        val vm = buildVm()
        val tz = kotlinx.datetime.TimeZone.UTC

        val startOfDay = vm.startOfDayMs(TEST_NOW_MS, tz)
        // Reminder exactly at midnight
        val atMidnight = TodayReminderInfo.ChecklistLevel(
            checklistId = 1L,
            checklistName = "Test",
            reminderAt = startOfDay,
            isRecurring = false,
        )

        // Pass the same nowMs + tz so buildAgenda's startOfToday aligns with startOfDay above
        val agenda = vm.buildAgenda(listOf(atMidnight), nowMs = TEST_NOW_MS, tz = tz)
        val pastDueHeader = agenda.filterIsInstance<AgendaItem.DateHeader>().firstOrNull { it.isPastDue }
        assertFalse(
            pastDueHeader != null,
            "Reminder at exactly startOfToday must not appear in Past due group"
        )
    }

    // ── 7. Reactivity: new reminder emitted while observed → agenda updates ──

    @Test
    fun reactivity_newReminderEmit_agendaUpdatesCorrectly() = runTest {
        // Start with one reminder
        repo.emitReminders(listOf(checklistReminder(checklistId = 1L, reminderAt = TODAY_3PM_MS)))
        val vm = buildVm()
        val firstState = vm.awaitState()
        assertIs<CalendarState.Content>(firstState)
        assertEquals(1, firstState.agenda.filterIsInstance<AgendaItem.ReminderRow>().size)

        // Emit a second reminder
        repo.emitReminders(
            listOf(
                checklistReminder(checklistId = 1L, reminderAt = TODAY_3PM_MS),
                checklistReminder(checklistId = 2L, reminderAt = TOMORROW_9AM_MS),
            )
        )

        // Await the updated state
        val updatedState = vm.screenState.first { s ->
            s is CalendarState.Content && s.agenda.filterIsInstance<AgendaItem.ReminderRow>().size == 2
        }
        assertIs<CalendarState.Content>(updatedState)
        assertEquals(2, updatedState.agenda.filterIsInstance<AgendaItem.ReminderRow>().size)
    }

    // ── 8a. Intent: OnReminderClick checklist-level → navigateToChecklistDetail (legacy name kept)

    @Test
    fun intent_onReminderClick_checklistLevel_navigatesToChecklistDetail() = runTest {
        val vm = buildVm()
        val info = checklistReminder(checklistId = 7L, reminderAt = TODAY_3PM_MS)

        vm.sendIntent(CalendarIntent.OnReminderClick(info))

        assertEquals(listOf(7L), navigator.navigatedChecklistIds)
        assertTrue(navigator.navigatedFillIds.isEmpty())
    }

    // ── 8b. Intent: OnReminderClick item-level → navigateToChecklistDetail with focusItemId ──

    @Test
    fun intent_onReminderClick_itemLevel_navigatesToChecklistDetailWithFocusItemId() = runTest {
        val vm = buildVm()
        val info = itemReminder(
            checklistId = 42L,
            fillId = 99L,
            itemId = "item-7",
            itemText = "Buy milk",
            reminderAt = TODAY_3PM_MS,
        )

        vm.sendIntent(CalendarIntent.OnReminderClick(info))

        assertEquals(1, navigator.checklistDetailCalls.size)
        assertEquals(42L, navigator.checklistDetailCalls[0].checklistId)
        assertEquals("item-7", navigator.checklistDetailCalls[0].focusItemId)
        assertTrue(navigator.navigatedFillIds.isEmpty(), "FillDetail must NOT be used for item-level reminders")
    }

    // ── 8c. Intent: OnReminderClick checklist-level → navigateToChecklistDetail without focusItemId

    @Test
    fun intent_onReminderClick_checklistLevel_navigatesToChecklistDetailWithoutFocusItemId() = runTest {
        val vm = buildVm()
        val info = checklistReminder(checklistId = 7L, reminderAt = TODAY_3PM_MS)

        vm.sendIntent(CalendarIntent.OnReminderClick(info))

        assertEquals(1, navigator.checklistDetailCalls.size)
        assertEquals(7L, navigator.checklistDetailCalls[0].checklistId)
        assertNull(navigator.checklistDetailCalls[0].focusItemId)
        assertTrue(navigator.navigatedFillIds.isEmpty())
    }

    // ── 11. Intent: OnCreateChecklistClick → navigateToTemplatesScreen ────────

    @Test
    fun intent_onCreateChecklistClick_navigatesToTemplates() = runTest {
        val vm = buildVm()

        vm.sendIntent(CalendarIntent.OnCreateChecklistClick)

        assertTrue(navigator.navigatedToTemplates)
    }

    // ── 12. Error: repository throws → Error state + AppLogger.error called
    //         OnRetry re-fetches and transitions back to Content on success ────

    @Test
    fun error_repositoryThrows_producesErrorState_andLoggerCalled() = runTest {
        // Use a repository that emits an error via its flow
        val errorRepo = object : FakeChecklistRepository() {
            private var shouldThrow = true
            private val _flow = MutableSharedFlow<List<TodayReminderInfo>>(replay = 1)

            override fun observeRemindersInRange(
                fromMs: Long,
                toMs: Long,
            ): Flow<List<TodayReminderInfo>> {
                return if (shouldThrow) {
                    kotlinx.coroutines.flow.flow { throw RuntimeException("DB failure") }
                } else {
                    _flow
                }
            }

            fun healAndEmit(list: List<TodayReminderInfo>) {
                shouldThrow = false
                _flow.tryEmit(list)
            }
        }

        val vm = CalendarViewModel(
            repository = errorRepo,
            appNavigator = navigator,
            logger = logger,
        )

        // First emission should be Error
        val errorState = vm.awaitState()
        assertIs<CalendarState.Error>(errorState)
        assertTrue(logger.errors.isNotEmpty(), "AppLogger.error must be called on repository failure")

        // Heal the repo, then retry
        errorRepo.healAndEmit(listOf(checklistReminder(reminderAt = TODAY_3PM_MS)))
        vm.sendIntent(CalendarIntent.OnRetry)

        // After retry, state should become Content
        val recovered = vm.screenState.first { it is CalendarState.Content }
        assertIs<CalendarState.Content>(recovered)
    }
}
