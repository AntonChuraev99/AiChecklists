package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.navigation.api.NavCommand
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.SmartDateParser
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.ChipDisplay
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.DayKey
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
import kotlinx.coroutines.flow.emptyFlow
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for Smart Add integration (Phase 3):
 * - OnItemInputChanged updates pendingItemInput state immediately
 * - Parser debounce: parsedToken appears 200ms after typing stops
 * - OnAddItemWithParse with token: strips matched substring, sets reminderAt
 * - OnAddItemWithParse without token: plain add (same as former OnAddItem)
 * - Only-trigger-phrase input: item is NOT added
 * - Double-fire guard: rapid OnAddItemWithParse only creates one item
 * - State cleared after add: pendingItemInput = "", parsedToken = null
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistDetailSmartAddTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeSmartAddRepository
    private lateinit var parser: ConfigurableSmartDateParser
    private lateinit var scheduler: RecordingReminderScheduler

    private val existingItem1 = ChecklistFillItem("Item 1", checked = false)
    private val existingItem2 = ChecklistFillItem("Item 2", checked = false)

    private val testChecklist = Checklist(
        id = 1L,
        name = "My List",
        items = listOf(ChecklistItem("Item 1"), ChecklistItem("Item 2"))
    )

    private val testFill = ChecklistFill(
        id = 10L,
        checklistId = 1L,
        name = "",
        items = listOf(existingItem1, existingItem2),
        createdAt = 0L,
        isDefault = true,
    )

    /** A ParsedDateToken representing "tomorrow at 07:00" (420 minutes) */
    private val tomorrowToken = ParsedDateToken(
        originalSubstring = "завтра 7 утра",
        startIndex = 14,       // position after "купить молоко " (14 chars)
        endIndex = 27,         // 14 + 13 ("завтра 7 утра".length == 13)
        display = ChipDisplay.RelativeDay(DayKey.TOMORROW, timeMinutes = 420),
        reminderAt = 9_000_000L,  // arbitrary future epoch millis for test
        repeatRule = null,
        timeOfDayMinutes = 420,
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        parser = ConfigurableSmartDateParser()
        scheduler = RecordingReminderScheduler()
        repository = FakeSmartAddRepository().apply {
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
                "build/test_prefs_smartadd_${Random.nextLong()}.preferences_pb".toPath()
            },
            testDispatcher
        )
        return ChecklistDetailViewModel(
            checklistId = 1L,
            repository = repository,
            navigator = FakeSmartAddNavigator(),
            getUserLimitsUseCase = GetUserLimitsUseCase(
                FakeSmartAddRemoteConfig(),
                repository,
                FakeSmartAddPaywallRepository(),
                FakeSmartAddUserDataRepository()
            ),
            analyticsTracker = FakeSmartAddAnalyticsTracker(),
            reminderScheduler = scheduler,
            datastore = datastore,
            smartDateParser = parser,
        )
    }

    private fun contentState(vm: ChecklistDetailViewModel): ChecklistDetailState.Content =
        vm.screenState.value as ChecklistDetailState.Content

    // ── Test 1: OnItemInputChanged updates pendingItemInput immediately ────────

    @Test
    fun onItemInputChanged_updatesState_immediately() = runTest {
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged("buy milk"))

        assertEquals("buy milk", contentState(vm).pendingItemInput)
    }

    // ── Test 2: Parser result appears after debounce window ───────────────────

    @Test
    fun onItemInputChanged_withParseableText_updatesParsedTokenAfterDebounce() = runTest {
        parser.nextResult = tomorrowToken
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged("купить молоко завтра 7 утра"))

        // Before debounce fires: parsedToken not yet set (text is staged but debounce pending)
        // With UnconfinedTestDispatcher + advanceTimeBy we can verify the timing:
        // Immediately after sendIntent the debounce coroutine is waiting 200ms.
        // Advance past the debounce window.
        advanceTimeBy(201)

        val token = contentState(vm).parsedToken
        assertNotNull(token, "parsedToken must be non-null after debounce fires")
        assertEquals(tomorrowToken.reminderAt, token.reminderAt)
    }

    // ── Test 3: Plain text clears parsedToken ─────────────────────────────────

    @Test
    fun onItemInputChanged_withPlainText_clearsParsedToken() = runTest {
        // First set a token
        parser.nextResult = tomorrowToken
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged("завтра 7 утра"))
        advanceTimeBy(201)
        assertNotNull(contentState(vm).parsedToken, "precondition: token set")

        // Now type plain text — parser returns null
        parser.nextResult = null
        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged("buy bread"))
        advanceTimeBy(201)

        assertNull(contentState(vm).parsedToken, "parsedToken must be null for plain text")
    }

    // ── Test 4: Debounce — rapid changes don't fire parser on each keystroke ──

    @Test
    fun onItemInputChanged_debounces_parserCalledOnceAfterTypingStops() = runTest {
        parser.nextResult = tomorrowToken
        val vm = createViewModel()

        // Fire 5 rapid keystrokes within the debounce window
        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged("з"))
        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged("за"))
        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged("зав"))
        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged("завт"))
        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged("завтра"))

        // Advance past debounce — parser should have been called once (for the last value only)
        advanceTimeBy(201)

        // Verify: only one parse call occurred. We verify indirectly by checking
        // the state has the token (not null) and parser call count = 1.
        assertEquals(1, parser.callCount, "parser must be called only once after debounce")
        assertNotNull(contentState(vm).parsedToken)
    }

    // ── Test 5: OnAddItemWithParse with token strips substring and sets reminder

    @Test
    fun onAddItemWithParse_withToken_createsItemStripsSubstringAndSetsReminder() = runTest {
        // Input: "купить молоко завтра 7 утра"
        // token.startIndex=14, token.endIndex=27 → stripped = "купить молоко"
        val inputText = "купить молоко завтра 7 утра"
        parser.nextResult = tomorrowToken
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged(inputText))
        advanceTimeBy(201) // let debounce fire to set parsedToken in state

        vm.onIntent(ChecklistDetailIntent.OnAddItemWithParse)

        // 3 items: 2 existing + 1 new
        val addedItems = repository.lastUpdatedFill?.items ?: error("fill must have been updated")
        assertEquals(3, addedItems.size, "fill must contain 2 existing + 1 new item")

        val newItem = addedItems.last()
        assertEquals("купить молоко", newItem.text, "item text must have trigger phrase stripped")
        assertEquals(tomorrowToken.reminderAt, newItem.reminderAt, "reminderAt must match token")

        // Alarm must have been scheduled
        assertTrue(scheduler.scheduledItemReminders.isNotEmpty(), "item reminder alarm must be scheduled")
        assertEquals(tomorrowToken.reminderAt, scheduler.scheduledItemReminders.first().triggerAtMillis)
    }

    // ── Test 6: OnAddItemWithParse without token creates plain item ───────────

    @Test
    fun onAddItemWithParse_withoutToken_createsPlainItem() = runTest {
        parser.nextResult = null
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged("buy bread"))
        advanceTimeBy(201)

        vm.onIntent(ChecklistDetailIntent.OnAddItemWithParse)

        val items = repository.lastUpdatedFill?.items ?: error("fill must have been updated")
        assertEquals(3, items.size)
        val newItem = items.last()
        assertEquals("buy bread", newItem.text)
        assertNull(newItem.reminderAt, "plain item must have no reminder")

        // No alarm scheduled for plain add
        assertTrue(scheduler.scheduledItemReminders.isEmpty(), "no alarm for plain item")
    }

    // ── Test 7: Only trigger phrase — item is NOT added ───────────────────────

    @Test
    fun onAddItemWithParse_strippingLeavesBlank_doesNotAdd() = runTest {
        // Input is only the trigger phrase — stripping it leaves blank text
        val onlyTrigger = "завтра 7 утра"
        val triggerOnlyToken = tomorrowToken.copy(
            originalSubstring = onlyTrigger,
            startIndex = 0,
            endIndex = onlyTrigger.length,
        )
        parser.nextResult = triggerOnlyToken
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged(onlyTrigger))
        advanceTimeBy(201)
        vm.onIntent(ChecklistDetailIntent.OnAddItemWithParse)

        // Fill must not have been updated (no call to updateFill)
        assertNull(repository.lastUpdatedFill, "fill must not be updated when stripped text is blank")
    }

    // ── Test 8: Double-fire guard — only one item created ─────────────────────

    @Test
    fun onAddItemWithParse_doubleFireGuard_onlyOneItemAdded() = runTest {
        parser.nextResult = null
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged("walk the dog"))
        advanceTimeBy(201)

        // Fire the intent twice in rapid succession
        vm.onIntent(ChecklistDetailIntent.OnAddItemWithParse)
        vm.onIntent(ChecklistDetailIntent.OnAddItemWithParse)

        // Only one item should have been added
        val items = repository.lastUpdatedFill?.items ?: error("fill must have been updated")
        assertEquals(3, items.size, "only 1 item must be added despite double-tap")
    }

    // ── Test 9a: Stripping leaves blank → hint shown, input preserved ────────

    @Test
    fun onAddItemWithParse_strippedBlank_emitsAddItemTextHint() = runTest {
        // Input is only the trigger phrase — after stripping, nothing remains.
        val onlyTrigger = "завтра 7 утра"
        val triggerOnlyToken = tomorrowToken.copy(
            originalSubstring = onlyTrigger,
            startIndex = 0,
            endIndex = onlyTrigger.length,
        )
        parser.nextResult = triggerOnlyToken
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged(onlyTrigger))
        advanceTimeBy(201)
        vm.onIntent(ChecklistDetailIntent.OnAddItemWithParse)

        // Fill must NOT be updated (no empty item added)
        assertNull(repository.lastUpdatedFill, "fill must not be updated when stripped text is blank")
        // Snackbar hint must be set
        assertEquals(
            ChecklistDetailViewModel.SNACKBAR_SMART_ADD_HINT_ADD_TEXT,
            contentState(vm).snackbarMessage,
            "snackbarMessage must equal SNACKBAR_SMART_ADD_HINT_ADD_TEXT"
        )
        // Input must NOT be cleared — user can add text after seeing the hint
        assertEquals(onlyTrigger, contentState(vm).pendingItemInput, "pendingItemInput must be preserved")
    }

    // ── Test 9b: Stranded preposition → time hint shown, input preserved ──────

    @Test
    fun onAddItemWithParse_strippedIsStrandedPrep_emitsAddTimeHint() = runTest {
        // "каждое воскресенье в" — after parser fix, endIndex=18 covers only
        // "каждое воскресенье". Stripping leaves " в".trim() = "в" (time preposition).
        val input = "каждое воскресенье в"
        val sundayToken = ParsedDateToken(
            originalSubstring = "каждое воскресенье",
            startIndex = 0,
            endIndex = 18,  // covers "каждое воскресенье" only — matches post-fix parser output
            display = tomorrowToken.display,
            reminderAt = 9_000_000L,
            repeatRule = null,
            timeOfDayMinutes = null,
        )
        parser.nextResult = sundayToken
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged(input))
        advanceTimeBy(201)
        vm.onIntent(ChecklistDetailIntent.OnAddItemWithParse)

        // Fill must NOT be updated
        assertNull(repository.lastUpdatedFill, "fill must not be updated for stranded preposition")
        // Time hint must be shown
        assertEquals(
            ChecklistDetailViewModel.SNACKBAR_SMART_ADD_HINT_ADD_TIME,
            contentState(vm).snackbarMessage,
            "snackbarMessage must equal SNACKBAR_SMART_ADD_HINT_ADD_TIME"
        )
        // Input must NOT be cleared — user needs it to append the time
        assertEquals(input, contentState(vm).pendingItemInput, "pendingItemInput must be preserved")
    }

    // ── Test 9c: Real text after trigger → item added normally (regression guard)

    @Test
    fun onAddItemWithParse_strippedHasRealText_addsItemNormally() = runTest {
        // "созвон каждое воскресенье" — endIndex covers "каждое воскресенье" at offset 7.
        // Stripping leaves "созвон " → trim → "созвон".
        val input = "созвон каждое воскресенье"
        val sundayToken = ParsedDateToken(
            originalSubstring = "каждое воскресенье",
            startIndex = 7,
            endIndex = 25,  // 7 + 18 = "каждое воскресенье"
            display = tomorrowToken.display,
            reminderAt = 9_000_000L,
            repeatRule = null,
            timeOfDayMinutes = null,
        )
        parser.nextResult = sundayToken
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged(input))
        advanceTimeBy(201)
        vm.onIntent(ChecklistDetailIntent.OnAddItemWithParse)

        // Item must be added with stripped text
        val items = repository.lastUpdatedFill?.items ?: error("fill must have been updated")
        assertEquals(3, items.size, "2 existing + 1 new item")
        assertEquals("созвон", items.last().text)
        // No snackbar hint emitted
        assertNull(contentState(vm).snackbarMessage, "no hint for successful add")
    }

    // ── Test 9: State cleared after successful add ────────────────────────────

    @Test
    fun onAddItemWithParse_clearsStateAfterAdd() = runTest {
        parser.nextResult = null
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnItemInputChanged("clean desk"))
        advanceTimeBy(201)
        vm.onIntent(ChecklistDetailIntent.OnAddItemWithParse)

        val state = contentState(vm)
        assertEquals("", state.pendingItemInput, "pendingItemInput must be cleared after add")
        assertNull(state.parsedToken, "parsedToken must be cleared after add")
    }

    // ─── Test doubles ──────────────────────────────────────────────────────────

    /**
     * Parser that returns a configurable result and counts how many times it was called.
     * Thread-safe for single-threaded test dispatcher.
     */
    private class ConfigurableSmartDateParser : SmartDateParser {
        var nextResult: ParsedDateToken? = null
        var callCount: Int = 0

        override fun parse(input: String, now: Long, timeZone: TimeZone): ParsedDateToken? {
            callCount++
            return nextResult
        }
    }

    private data class ScheduledItemReminder(
        val checklistId: Long,
        val fillId: Long,
        val itemId: String,
        val triggerAtMillis: Long,
    )

    private class RecordingReminderScheduler : ChecklistReminderScheduler {
        val scheduledItemReminders = mutableListOf<ScheduledItemReminder>()

        override fun scheduleReminder(checklistId: Long, triggerAtMillis: Long) {}
        override fun cancelReminder(checklistId: Long) {}
        override suspend fun rescheduleAllActiveReminders() {}
        override fun scheduleRepeat(checklistId: Long, triggerAtMillis: Long) {}
        override fun cancelRepeat(checklistId: Long) {}
        override suspend fun rescheduleAllActiveRepeats() {}
        override fun scheduleItemReminder(
            checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long
        ) {
            scheduledItemReminders.add(ScheduledItemReminder(checklistId, fillId, itemId, triggerAtMillis))
        }
        override fun cancelItemReminder(checklistId: Long, fillId: Long, itemId: String) {}
        override fun scheduleItemRepeat(
            checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long
        ) {}
        override fun cancelItemRepeat(checklistId: Long, fillId: Long, itemId: String) {}
    }

    private class FakeSmartAddRepository : ChecklistRepository {
        var storedChecklist: Checklist? = null
        val defaultFillFlow = MutableStateFlow<ChecklistFill?>(null)

        /** Records the last fill passed to updateFill. Null = never called. */
        var lastUpdatedFill: ChecklistFill? = null

        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())
        override suspend fun addChecklist(checklist: Checklist): Long = 1L
        override suspend fun updateChecklist(checklist: Checklist) {}
        override suspend fun updateChecklistTemplate(checklist: Checklist) {}
        override suspend fun deleteChecklist(checklist: Checklist) {}
        override suspend fun getChecklistById(id: Long): Checklist? = storedChecklist
        override fun observeChecklistById(id: Long): Flow<Checklist?> = flowOf(storedChecklist)
        override suspend fun reorderChecklists(orderedIds: List<Long>) {}
        override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) {}
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
        override suspend fun updateFill(fill: ChecklistFill) { lastUpdatedFill = fill }
        override suspend fun deleteFill(fill: ChecklistFill) {}
        override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<TodayReminderInfo>> = flowOf(emptyList())
        override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<TodayReminderInfo> = emptyList()
        override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeSmartAddNavigator : AppNavigator {
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
        override fun navigateToPaywall(source: String) {}
        override fun navigateToPaywallVariant(source: String, forceVariant: String) {}
        override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) {}
        override fun navigateToShareChecklist(checklistId: Long) {}
        override fun navigateToUpdateFeed() {}
        override fun navigateToSettings() {}
        override fun navigateToToday() {}
        override fun navigateToScreenCatalog() {}
    }

    private class FakeSmartAddPaywallRepository : PaywallRepository {
        override val subscriptionStatus: Flow<SubscriptionStatus> = flowOf(SubscriptionStatus.FREE)
        override suspend fun getOfferings(): Result<PaywallOffering?> = Result.success(null)
        override suspend fun purchase(packageId: String): PurchaseResult = PurchaseResult.Error("stub")
        override suspend fun restorePurchases(): RestoreResult = RestoreResult.Error("stub")
        override suspend fun refreshSubscriptionStatus() {}
        override fun isConfigured(): Boolean = true
        override suspend fun logIn(appUserId: String): Result<LoginResult> = Result.failure(NotImplementedError())
        override suspend fun logOut(): Result<SubscriptionStatus> = Result.failure(NotImplementedError())
    }

    private class FakeSmartAddUserDataRepository : UserDataRepository {
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

    private class FakeSmartAddRemoteConfig : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getString(key: String, defaultValue: String): String = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
    }

    private class FakeSmartAddAnalyticsTracker : AnalyticsTracker {
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {}
    }
}
