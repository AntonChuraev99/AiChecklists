package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.common.api.AiEntrySource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxDisplayOptions
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxDisplayPrefsRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxLayout
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxSort
import com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar.CalendarEvent
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar.CalendarEventLauncher
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.SmartDateParser
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.ParsedDateToken
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase.EnsureInboxUseCase
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.NoOpAppLogger
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
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * `ai_entry_tapped` as emitted by the Inbox tab — the event's own regression net.
 *
 * The event shipped with ZERO tests while the credits chip beside it got four. That is the same
 * hole the event exists to close: an unattributed tap is what made the "v2 shell has no route to
 * Analyze" outage invisible for a whole release, so a typo in a `wire` value, a dropped
 * `input_type` or a `source` that no longer matches the surface it names would all sail through
 * green while the funnel silently read zero.
 *
 * Every assertion pins the WHOLE param map, never one key. An EXTRA dimension is as much a defect
 * as a missing one: Amplitude registers a property name on first ingest and cannot un-register it.
 *
 * Robolectric rather than a plain `commonTest` class because [InboxViewModel]'s `init` resolves the
 * Inbox title through Compose Resources (`getString`) — that needs a real Android context, and
 * without one the whole class dies before any assertion runs.
 *
 * ⛔ No `runTest` here, deliberately. The tapped-source path is fully synchronous (report, then
 * navigate), and `runTest` adopts the scheduler of a `TestDispatcher` installed as
 * `Dispatchers.Main` — which is this ViewModel's `viewModelScope`. Its `observeClock()` is a
 * `while (true) { delay(…) }`, so the body would finish and `runTest` would then advance virtual
 * time through that loop forever: the task hangs with no failure and no output.
 *
 * Run: `./gradlew :feature:home:testAndroidHostTest --tests "*InboxAiEntryAnalyticsTest*"`
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InboxAiEntryAnalyticsTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * The empty-Inbox door.
     *
     * `inbox_empty` and the sparse door are deliberately two values: "the list was empty" and "the
     * list had a task or two" are different user states and collapsing them hides which of the two
     * actually converts.
     */
    @Test
    fun aiSourceTap_fromTheEmptyInboxDoor_emitsTheFullParamMap() {
        val analytics = RecordingAnalytics()
        val vm = buildViewModel(analytics = analytics)

        vm.sendIntent(
            InboxIntent.OnAiSourceTapped(AnalyzeInputKind.VOICE, AiEntrySource.INBOX_EMPTY)
        )

        assertEquals(
            listOf(
                "ai_entry_tapped" to mapOf<String, Any>(
                    "destination" to "analyze",
                    "source" to "inbox_empty",
                    "input_type" to "voice",
                ),
            ),
            analytics.events,
            "The empty-Inbox door must report destination + source + material and nothing else — " +
                "has_query/query_len belong to the Templates empty-search door alone",
        )
    }

    /**
     * The sparse-Inbox door.
     *
     * The wire value names the LIST STATE (`inbox_sparse`), not a position. It used to say
     * `inbox_header` while the row rendered at the foot of the list, under the add-task row — a
     * taxonomy value that describes where a thing sits is wrong the first time the layout moves,
     * and every future reader inherits the wrong reading with no way to detect it.
     */
    @Test
    fun aiSourceTap_fromTheSparseInboxDoor_reportsTheListState_notAPosition() {
        val analytics = RecordingAnalytics()
        val vm = buildViewModel(analytics = analytics)

        vm.sendIntent(
            InboxIntent.OnAiSourceTapped(AnalyzeInputKind.PHOTO, AiEntrySource.INBOX_SPARSE)
        )

        assertEquals(
            listOf(
                "ai_entry_tapped" to mapOf<String, Any>(
                    "destination" to "analyze",
                    "source" to "inbox_sparse",
                    "input_type" to "photo",
                ),
            ),
            analytics.events,
        )
    }

    /**
     * Reporting comes FIRST, and it is unconditional.
     *
     * The tap is the fact being measured: if navigation later fails or the user backs straight out,
     * "someone reached for Analyze" still has to be true in the data. That is why both halves live
     * in one intent — splitting them across layers is how the v2 credits chip ended up navigating
     * while reporting nothing.
     */
    @Test
    fun aiSourceTap_reportsAndThenNavigatesWithTheSameMaterialAndDoor() {
        val analytics = RecordingAnalytics()
        val navigator = FakeNavigator()
        val vm = buildViewModel(analytics = analytics, navigator = navigator)

        vm.sendIntent(
            InboxIntent.OnAiSourceTapped(AnalyzeInputKind.PDF, AiEntrySource.INBOX_EMPTY)
        )

        assertEquals(
            listOf(AnalyzeInputKind.PDF to AiEntrySource.INBOX_EMPTY),
            navigator.analyzeEntries,
            "Analyze must open on the material the user named, carrying the door that opened it",
        )
        assertEquals(
            "pdf",
            analytics.events.single().second["input_type"],
            "…and the SAME spelling must be on the wire, or ai_entry_tapped -> ai_analyze_started " +
                "cannot be joined on input_type",
        )
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    private fun buildViewModel(
        analytics: AnalyticsTracker,
        navigator: FakeNavigator = FakeNavigator(),
    ): InboxViewModel {
        val repository = FakeChecklistRepository()
        val paywallRepository = FakePaywallRepository()
        val userDataRepository = FakeUserDataRepository()
        return InboxViewModel(
            repository = repository,
            // Its OWN tracker, not the recorder: bootstrapping the system Inbox emits
            // `inbox_system_created`, which has nothing to do with the door under test. Sharing one
            // tracker would put a startup event in front of every assertion and make these tests
            // fail whenever the bootstrap's instrumentation changes.
            ensureInbox = EnsureInboxUseCase(repository, NoOpAnalytics, NoOpAppLogger),
            reminderScheduler = NoOpScheduler(),
            displayPrefs = FakeDisplayPrefs(),
            navigator = navigator,
            analytics = analytics,
            getUserLimitsUseCase = GetUserLimitsUseCase(
                remoteConfigProvider = FakeRemoteConfigProvider(),
                checklistRepository = repository,
                paywallRepository = paywallRepository,
                userDataRepository = userDataRepository,
            ),
            attachmentStorage = NoOpAttachmentStorage(),
            calendarEventLauncher = NoOpCalendarEventLauncher(),
            smartDateParser = NoOpSmartDateParser,
            logger = NoOpAppLogger,
        )
    }

    // ─── Fakes ────────────────────────────────────────────────────────────────

    /** Recognises nothing: these tests are about the AI doors, not about Smart-Add. */
    private object NoOpSmartDateParser : SmartDateParser {
        override fun parse(input: String, now: Long, timeZone: TimeZone): ParsedDateToken? = null
    }

    private object NoOpAnalytics : AnalyticsTracker {
        override fun setUserId(userId: String) = Unit
        override fun setUserProperties(properties: Map<String, Any>) = Unit
        override fun screenView(name: String) = Unit
        override fun event(name: String, params: Map<String, Any>) = Unit
    }

    /** Keeps name AND params so an assertion can pin the whole map, not one key of it. */
    private class RecordingAnalytics : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun setUserId(userId: String) = Unit
        override fun setUserProperties(properties: Map<String, Any>) = Unit
        override fun screenView(name: String) = Unit
        override fun event(name: String, params: Map<String, Any>) {
            events += name to params
        }
    }

    private class FakeNavigator : AppNavigator {
        /** (material, door) pairs handed to Analyze — the other half of an `ai_entry_tapped`. */
        val analyzeEntries = mutableListOf<Pair<AnalyzeInputKind, AiEntrySource>>()

        override val events: SharedFlow<AppNavEvent> = MutableSharedFlow()
        override val backStack: NavBackStack<NavKey> = NavBackStack()
        override fun onBack() {}
        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
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
        override fun navigateToAnalyzeWithInput(
            inputKind: AnalyzeInputKind,
            entrySource: AiEntrySource,
        ) {
            analyzeEntries += inputKind to entrySource
        }

        override fun navigateToAnalyzeScreen(
            checklistId: Long?,
            fillDefault: Boolean,
            initialText: String?,
            autoAnalyze: Boolean,
        ) {}
        override fun navigateToAnalyzeResultPreview() {}
        override fun navigateToChecklistDetail(
            checklistId: Long,
            focusItemId: String?,
            clearBackStack: Boolean,
        ) {}
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

    private class FakeChecklistRepository : ChecklistRepository {
        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())
        override val weeklyChecklistCount: Flow<Int> = flowOf(0)
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
        override suspend fun setRepeatSchedule(
            checklistId: Long,
            rule: ReminderRepeatRule,
            timeOfDayMinutes: Int,
            firstTriggerAt: Long,
        ) {}
        override suspend fun advanceRepeatSchedule(checklistId: Long, nextAt: Long?, newCount: Int) {}
        override suspend fun clearRepeatSchedule(checklistId: Long) {}
        override suspend fun resetDefaultFillChecks(checklistId: Long) {}
        override suspend fun countActiveRepeatSchedules(): Int = 0
        override suspend fun getActiveRepeatSchedules(): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getTotalAdditionalFillCount(): Int = 0
        override suspend fun getWeeklyChecklistCount(): Int = 0
        override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<TodayReminderInfo>> =
            flowOf(emptyList())
        override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<TodayReminderInfo> = emptyList()
        override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addAttachment(fillId: Long, itemId: String, attachment: Attachment) = Unit
        override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) = Unit
        override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = emptyFlow()
        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = flowOf(null)
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = emptyFlow()
        override suspend fun getFillById(id: Long): ChecklistFill? = null
        override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun addFill(fill: ChecklistFill): Long = 1L
        override suspend fun updateFill(fill: ChecklistFill) {}
        override suspend fun deleteFill(fill: ChecklistFill) {}
        override suspend fun reorderItems(fill: ChecklistFill, checklist: Checklist) {}
    }

    private class FakePaywallRepository : PaywallRepository {
        override val subscriptionStatus: Flow<SubscriptionStatus> = flowOf(SubscriptionStatus.FREE)
        override suspend fun getOfferings(offeringId: String): Result<PaywallOffering?> = Result.success(null)
        override suspend fun purchase(packageId: String): PurchaseResult = PurchaseResult.Cancelled
        override suspend fun restorePurchases(): RestoreResult = RestoreResult.NoActiveSubscription
        override suspend fun refreshSubscriptionStatus() {}
        override fun isConfigured(): Boolean = false
        override suspend fun logIn(appUserId: String): Result<LoginResult> =
            Result.success(LoginResult(subscriptionStatus = SubscriptionStatus.FREE, isNewCustomer = false))
        override suspend fun logOut(): Result<SubscriptionStatus> = Result.success(SubscriptionStatus.FREE)
    }

    private class FakeUserDataRepository : UserDataRepository {
        private val data = UserData(userId = "test", isPremium = false)
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
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
    }

    private class FakeDisplayPrefs : InboxDisplayPrefsRepository {
        override fun observeDisplayOptions(): Flow<InboxDisplayOptions> = flowOf(InboxDisplayOptions())
        override fun observePlanNudgeDismissedAt(): Flow<Long> = flowOf(0L)
        override suspend fun setLayout(layout: InboxLayout) {}
        override suspend fun setSort(sort: InboxSort) {}
        override suspend fun setShowCompleted(show: Boolean) {}
        override suspend fun setGroupByDate(group: Boolean) {}
        override suspend fun setPlanNudgeDismissedAt(atMillis: Long) {}
    }

    private class NoOpScheduler : ChecklistReminderScheduler {
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

    private class NoOpAttachmentStorage : AttachmentStoragePort {
        override suspend fun storeAttachment(
            sourcePath: String,
            fillId: Long,
            itemId: String,
            attachmentId: String,
            originalFileName: String,
        ): String? = null
        override suspend fun deleteAttachment(path: String) {}
        override suspend fun deleteAttachmentsFor(fillId: Long, itemId: String) {}
        override suspend fun deleteAttachmentsForFill(fillId: Long) {}
        override suspend fun probeImage(path: String, mimeType: String?): Pair<Int?, Int?> = null to null
        override suspend fun sizeOf(path: String): Long = 0L
    }

    private class NoOpCalendarEventLauncher : CalendarEventLauncher {
        override fun addEvent(event: CalendarEvent): Boolean = true
    }
}
