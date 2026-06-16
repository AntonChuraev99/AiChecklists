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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * TDD tests for ItemDetailsSheet intents in ChecklistDetailViewModel (Phase B).
 *
 * Covers:
 * - OnItemTapForDetails: opens sheet (itemDetailsSheetFor set)
 * - OnDismissItemDetailsSheet: closes sheet (itemDetailsSheetFor null)
 * - OnItemReminderClick while sheet open: closes details sheet, opens reminder sheet
 * - OnAddNoteClick while sheet open: closes details sheet, opens note dialog
 * - OnDeleteItemFromSheet: cancels reminders if active, removes from fill+template, closes sheet
 * - Free-tier paywall gate still triggers when reminder click comes via sheet
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistDetailItemDetailsSheetTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeChecklistRepository
    private lateinit var scheduler: FakeReminderScheduler
    private lateinit var navigator: FakeAppNavigator

    private val itemNoReminder = ChecklistFillItem("Task A", checked = false)

    private val repeatRule = ReminderRepeatRule(
        type = RepeatType.DAILY,
        interval = 1,
        weekDays = null,
        endCondition = RepeatEndCondition.Never,
        resetChecks = false
    )
    private val itemWithReminder = ChecklistFillItem(
        text = "Task B",
        checked = false,
    ).withReminderAt(System.currentTimeMillis() + 3_600_000L)

    private val itemWithRepeat = ChecklistFillItem(
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
        items = listOf(itemNoReminder, itemWithReminder, itemWithRepeat),
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
                "build/test_prefs_details_sheet_${Random.nextLong()}.preferences_pb".toPath()
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
            attachmentStorage = FakeAttachmentStorage(),
            calendarEventLauncher = FakeCalendarEventLauncher(),
        )
    }

    private fun contentState(vm: ChecklistDetailViewModel): ChecklistDetailState.Content =
        vm.screenState.value as ChecklistDetailState.Content

    // ── OnItemTapForDetails ───────────────────────────────────────────────

    @Test
    fun onItemTapForDetails_setsItemDetailsSheetFor() = runTest {
        val vm = createViewModel()
        assertNull(contentState(vm).itemDetailsSheetFor)

        vm.onIntent(ChecklistDetailIntent.OnItemTapForDetails(itemNoReminder.id))

        assertEquals(itemNoReminder.id, contentState(vm).itemDetailsSheetFor)
    }

    @Test
    fun onItemTapForDetails_differentItem_updatesSheetTarget() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemTapForDetails(itemNoReminder.id))
        assertEquals(itemNoReminder.id, contentState(vm).itemDetailsSheetFor)

        vm.onIntent(ChecklistDetailIntent.OnItemTapForDetails(itemWithReminder.id))
        assertEquals(itemWithReminder.id, contentState(vm).itemDetailsSheetFor)
    }

    // ── OnDismissItemDetailsSheet ─────────────────────────────────────────

    @Test
    fun onDismissItemDetailsSheet_clearsItemDetailsSheetFor() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemTapForDetails(itemNoReminder.id))
        assertEquals(itemNoReminder.id, contentState(vm).itemDetailsSheetFor)

        vm.onIntent(ChecklistDetailIntent.OnDismissItemDetailsSheet)

        assertNull(contentState(vm).itemDetailsSheetFor)
    }

    @Test
    fun onDismissItemDetailsSheet_doesNotPersistAnything() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemTapForDetails(itemNoReminder.id))
        vm.onIntent(ChecklistDetailIntent.OnDismissItemDetailsSheet)

        assertNull(repository.lastUpdatedFill)
        assertTrue(scheduler.cancelledItemReminders.isEmpty())
        assertTrue(scheduler.cancelledItemRepeats.isEmpty())
    }

    // ── OnItemReminderClick while details sheet open ──────────────────────

    @Test
    fun onItemReminderClick_whileDetailsSheetOpen_closesDetailsSheetThenOpensReminderSheet() = runTest {
        val vm = createViewModel()
        // Open details sheet first
        vm.onIntent(ChecklistDetailIntent.OnItemTapForDetails(itemNoReminder.id))
        assertEquals(itemNoReminder.id, contentState(vm).itemDetailsSheetFor)

        // Now trigger reminder click for the same item
        vm.onIntent(ChecklistDetailIntent.OnItemReminderClick(itemNoReminder.id))

        val state = contentState(vm)
        // Details sheet closed
        assertNull(state.itemDetailsSheetFor)
        // Reminder sheet opened
        assertEquals(itemNoReminder.id, state.itemReminderSheetFor)
    }

    // ── OnAddNoteClick while details sheet open ───────────────────────────

    @Test
    fun onAddNoteClick_whileDetailsSheetOpen_closesDetailsSheetThenOpensNoteDialog() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemTapForDetails(itemNoReminder.id))
        assertEquals(itemNoReminder.id, contentState(vm).itemDetailsSheetFor)

        vm.onIntent(ChecklistDetailIntent.OnAddNoteClick(itemNoReminder.id))

        val state = contentState(vm)
        // Details sheet closed
        assertNull(state.itemDetailsSheetFor)
        // Note dialog opened
        assertEquals(itemNoReminder.id, state.noteDialogItemId)
    }

    // ── OnDeleteItemFromSheet without reminder ────────────────────────────

    @Test
    fun onDeleteItemFromSheet_withoutReminder_justDeletes() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemTapForDetails(itemNoReminder.id))

        vm.onIntent(ChecklistDetailIntent.OnDeleteItemFromSheet(itemNoReminder.id))

        // No scheduler calls
        assertTrue(scheduler.cancelledItemReminders.isEmpty())
        assertTrue(scheduler.cancelledItemRepeats.isEmpty())

        // Fill persisted without the item
        val savedFill = repository.lastUpdatedFill
        assertNotNull(savedFill)
        assertFalse(savedFill.items.any { it.id == itemNoReminder.id })

        // Template also updated
        val savedChecklist = repository.lastUpdatedChecklist
        assertNotNull(savedChecklist)
        assertFalse(savedChecklist.items.any { it.text == itemNoReminder.text })

        // Sheet closed
        assertNull(contentState(vm).itemDetailsSheetFor)
    }

    // ── OnDeleteItemFromSheet with one-shot reminder ──────────────────────

    @Test
    fun onDeleteItemFromSheet_withActiveReminder_cancelsReminderAndDeletes() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemTapForDetails(itemWithReminder.id))

        vm.onIntent(ChecklistDetailIntent.OnDeleteItemFromSheet(itemWithReminder.id))

        // Both cancel methods called
        assertTrue(scheduler.cancelledItemReminders.any { it == itemWithReminder.id })
        assertTrue(scheduler.cancelledItemRepeats.any { it == itemWithReminder.id })

        // Item removed from fill
        val savedFill = repository.lastUpdatedFill
        assertNotNull(savedFill)
        assertFalse(savedFill.items.any { it.id == itemWithReminder.id })

        // Sheet closed
        assertNull(contentState(vm).itemDetailsSheetFor)
    }

    // ── OnDeleteItemFromSheet with repeat reminder ────────────────────────

    @Test
    fun onDeleteItemFromSheet_withRepeatReminder_cancelsRepeatAndDeletes() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemTapForDetails(itemWithRepeat.id))

        vm.onIntent(ChecklistDetailIntent.OnDeleteItemFromSheet(itemWithRepeat.id))

        assertTrue(scheduler.cancelledItemReminders.any { it == itemWithRepeat.id })
        assertTrue(scheduler.cancelledItemRepeats.any { it == itemWithRepeat.id })

        val savedFill = repository.lastUpdatedFill
        assertNotNull(savedFill)
        assertFalse(savedFill.items.any { it.id == itemWithRepeat.id })

        assertNull(contentState(vm).itemDetailsSheetFor)
    }

    // ── Free-tier paywall gate still works when coming from details sheet ─

    @Test
    fun onItemReminderClick_freeTierGate_opensLockedSheetWhenComingFromDetails() = runTest {
        repository.activeRemindersCount = 1
        val vm = createViewModel(paywallRepository = FakePaywallRepository(SubscriptionStatus.FREE))

        // Open details sheet, then tap reminder from it
        vm.onIntent(ChecklistDetailIntent.OnItemTapForDetails(itemNoReminder.id))
        vm.onIntent(ChecklistDetailIntent.OnItemReminderClick(itemNoReminder.id))

        // Details sheet cleared (closed before reminder sheet opens — Approach A)
        assertNull(contentState(vm).itemDetailsSheetFor)
        // Reminder sheet opened in locked mode
        assertEquals(itemNoReminder.id, contentState(vm).itemReminderSheetFor)
        assertTrue(contentState(vm).itemReminderSheetLocked)
        // No paywall navigation
        assertNull(navigator.lastPaywallSource)
    }

    // ── OnStartItemTextEdit ───────────────────────────────────────────────

    @Test
    fun onStartItemTextEdit_setsEditingItemTextForAndDraft() = runTest {
        val vm = createViewModel()
        val state = contentState(vm)
        assertNull(state.editingItemTextFor)
        assertEquals("", state.editingItemTextDraft)

        vm.onIntent(ChecklistDetailIntent.OnItemTapForDetails(itemNoReminder.id))
        vm.onIntent(ChecklistDetailIntent.OnStartItemTextEdit(itemNoReminder.id))

        val updated = contentState(vm)
        assertEquals(itemNoReminder.id, updated.editingItemTextFor)
        assertEquals(itemNoReminder.text, updated.editingItemTextDraft)
    }

    // ── OnItemTextDraftChange ─────────────────────────────────────────────

    @Test
    fun onItemTextDraftChange_updatesDraftWithoutMutatingFill() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemTapForDetails(itemNoReminder.id))
        vm.onIntent(ChecklistDetailIntent.OnStartItemTextEdit(itemNoReminder.id))

        vm.onIntent(ChecklistDetailIntent.OnItemTextDraftChange("Updated text"))

        val updated = contentState(vm)
        assertEquals("Updated text", updated.editingItemTextDraft)
        // Fill must NOT be modified yet — only draft changes
        assertNull(repository.lastUpdatedFill)
    }

    // ── OnConfirmItemTextEdit — blank text → cancel ───────────────────────

    @Test
    fun onConfirmItemTextEdit_blankText_cancelsWithoutUpdatingRepo() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemTapForDetails(itemNoReminder.id))
        vm.onIntent(ChecklistDetailIntent.OnStartItemTextEdit(itemNoReminder.id))
        vm.onIntent(ChecklistDetailIntent.OnItemTextDraftChange("   "))

        vm.onIntent(ChecklistDetailIntent.OnConfirmItemTextEdit)

        val updated = contentState(vm)
        assertNull(updated.editingItemTextFor)
        assertEquals("", updated.editingItemTextDraft)
        // Repo must NOT be touched
        assertNull(repository.lastUpdatedFill)
        assertNull(repository.lastUpdatedChecklist)
    }

    // ── OnConfirmItemTextEdit — same text → no-op ─────────────────────────

    @Test
    fun onConfirmItemTextEdit_sameText_cancelsWithoutUpdatingRepo() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemTapForDetails(itemNoReminder.id))
        vm.onIntent(ChecklistDetailIntent.OnStartItemTextEdit(itemNoReminder.id))
        // draft already equals item.text — no change
        vm.onIntent(ChecklistDetailIntent.OnItemTextDraftChange(itemNoReminder.text))

        vm.onIntent(ChecklistDetailIntent.OnConfirmItemTextEdit)

        val updated = contentState(vm)
        assertNull(updated.editingItemTextFor)
        assertNull(repository.lastUpdatedFill)
        assertNull(repository.lastUpdatedChecklist)
    }

    // ── OnConfirmItemTextEdit — valid new text → dual update ─────────────

    @Test
    fun onConfirmItemTextEdit_validText_updatesBothFillAndTemplate() = runTest {
        val vm = createViewModel()
        val newText = "Renamed item"

        vm.onIntent(ChecklistDetailIntent.OnItemTapForDetails(itemNoReminder.id))
        vm.onIntent(ChecklistDetailIntent.OnStartItemTextEdit(itemNoReminder.id))
        vm.onIntent(ChecklistDetailIntent.OnItemTextDraftChange(newText))
        vm.onIntent(ChecklistDetailIntent.OnConfirmItemTextEdit)

        // Edit mode closed
        val updated = contentState(vm)
        assertNull(updated.editingItemTextFor)
        assertEquals("", updated.editingItemTextDraft)

        // Fill updated with new text
        val savedFill = repository.lastUpdatedFill
        assertNotNull(savedFill)
        val updatedItem = savedFill.items.firstOrNull { it.id == itemNoReminder.id }
        assertNotNull(updatedItem)
        assertEquals(newText, updatedItem.text)

        // Template also updated — template items keyed by original text (ids differ from fill)
        val savedChecklist = repository.lastUpdatedChecklist
        assertNotNull(savedChecklist)
        // The original text must no longer appear; new text must be present
        assertFalse(savedChecklist.items.any { it.text == itemNoReminder.text })
        assertTrue(savedChecklist.items.any { it.text == newText })
    }

    // ── OnDismissItemDetailsSheet cancels active edit ─────────────────────

    @Test
    fun onDismissItemDetailsSheet_cancelsActiveEdit() = runTest {
        val vm = createViewModel()
        vm.onIntent(ChecklistDetailIntent.OnItemTapForDetails(itemNoReminder.id))
        vm.onIntent(ChecklistDetailIntent.OnStartItemTextEdit(itemNoReminder.id))
        vm.onIntent(ChecklistDetailIntent.OnItemTextDraftChange("draft text"))

        // Dismiss while in edit mode
        vm.onIntent(ChecklistDetailIntent.OnDismissItemDetailsSheet)

        val updated = contentState(vm)
        assertNull(updated.itemDetailsSheetFor)
        assertNull(updated.editingItemTextFor)
        assertEquals("", updated.editingItemTextDraft)
        // No persist call
        assertNull(repository.lastUpdatedFill)
    }

    // ─── Test doubles ─────────────────────────────────────────────────────

    private class FakeReminderScheduler : ChecklistReminderScheduler {
        val cancelledItemReminders = mutableListOf<String>()
        val cancelledItemRepeats = mutableListOf<String>()

        override fun scheduleReminder(checklistId: Long, triggerAtMillis: Long) {}
        override fun cancelReminder(checklistId: Long) {}
        override suspend fun rescheduleAllActiveReminders() {}
        override fun scheduleRepeat(checklistId: Long, triggerAtMillis: Long) {}
        override fun cancelRepeat(checklistId: Long) {}
        override suspend fun rescheduleAllActiveRepeats() {}
        override fun scheduleItemReminder(checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long) {}
        override fun cancelItemReminder(checklistId: Long, fillId: Long, itemId: String) {
            cancelledItemReminders.add(itemId)
        }
        override fun scheduleItemRepeat(checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long) {}
        override fun cancelItemRepeat(checklistId: Long, fillId: Long, itemId: String) {
            cancelledItemRepeats.add(itemId)
        }
    }

    private class FakeChecklistRepository : ChecklistRepository {
        var storedChecklist: Checklist? = null
        val defaultFillFlow = MutableStateFlow<ChecklistFill?>(null)
        var lastUpdatedFill: ChecklistFill? = null
        var lastUpdatedChecklist: Checklist? = null
        var activeRemindersCount: Int = 0

        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())
        override suspend fun addChecklist(checklist: Checklist): Long = 1L
        override suspend fun updateChecklist(checklist: Checklist) {}
        override suspend fun updateChecklistTemplate(checklist: Checklist) { lastUpdatedChecklist = checklist }
        override suspend fun deleteChecklist(checklist: Checklist) {}
        override suspend fun getChecklistById(id: Long): Checklist? = storedChecklist
        override fun observeChecklistById(id: Long): Flow<Checklist?> = flowOf(storedChecklist)
        override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setFoldersEnabled(checklistId: Long, value: Boolean) {}
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
        override suspend fun reorderItems(fill: ChecklistFill, checklist: Checklist) {}
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
        override fun navigateToAddToChecklistPicker(text: String, purpose: com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose) {}
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
        override suspend fun getOfferings(offeringId: String): Result<PaywallOffering?> = Result.success(null)
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

    private class FakeAttachmentStorage : com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort {
        override suspend fun storeAttachment(sourcePath: String, fillId: Long, itemId: String, attachmentId: String, originalFileName: String): String? = null
        override suspend fun deleteAttachment(path: String) {}
        override suspend fun deleteAttachmentsFor(fillId: Long, itemId: String) {}
        override suspend fun deleteAttachmentsForFill(fillId: Long) {}
        override suspend fun probeImage(path: String, mimeType: String?): Pair<Int?, Int?> = null to null
        override suspend fun sizeOf(path: String): Long = 0L
    }
}
