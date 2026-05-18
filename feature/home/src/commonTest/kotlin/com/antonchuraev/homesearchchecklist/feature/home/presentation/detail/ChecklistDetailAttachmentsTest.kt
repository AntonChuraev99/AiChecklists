package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.navigation.api.NavCommand
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ViewModel tests for attachment intents in ChecklistDetailViewModel (Phase 4).
 *
 * Covers:
 * - handleAddAttachment_freeTierAtLimit_emitsPremiumSnackbar
 * - handleAddAttachment_premium_setsPickerTrigger
 * - handleAttachmentPicked_storeFails_emitsLoadErrorSnackbar
 * - handleAttachmentPicked_sizeOverLimit_deletesFileAndEmitsSnackbar
 * - handleAttachmentPicked_success_callsRepoAddAttachment
 * - handleDeleteAttachment_callsRepoRemove_andClosesViewerIfLastAttachment
 * - handleAttachmentClick_setsViewerState
 * - handleOpenAttachmentExternally_setsPendingOpenExternallyState
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistDetailAttachmentsTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeAttachmentsRepository
    private lateinit var attachmentStorage: ControllableAttachmentStorage

    // A stub attachment for use in fill items that already have an attachment.
    private val existingAttachment = Attachment(
        id = "att_existing",
        path = "/data/attachments/10/item1/att_existing.jpg",
        fileName = "photo.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 500_000L,
        createdAt = 0L,
        width = 800,
        height = 600,
    )

    // Item with no attachments — for free-tier limit tests.
    private val emptyItem = ChecklistFillItem("Buy milk", checked = false)

    // Item already at the free-tier attachment limit (3 attachments).
    private val fullItem = ChecklistFillItem(
        text = "Full item",
        checked = false,
    ).withAttachments(
        listOf(
            existingAttachment.copy(id = "att_1", path = "/p/1"),
            existingAttachment.copy(id = "att_2", path = "/p/2"),
            existingAttachment.copy(id = "att_3", path = "/p/3"),
        ),
    )

    // Item with exactly one attachment — used for "last attachment" delete tests.
    private val singleAttachmentItem = ChecklistFillItem(
        text = "Single attach",
        checked = false,
    ).withAttachments(listOf(existingAttachment))

    private val testChecklist = Checklist(
        id = 1L,
        name = "Test Checklist",
        items = listOf(
            ChecklistItem("Buy milk"),
            ChecklistItem("Full item"),
            ChecklistItem("Single attach"),
        ),
    )

    private val testFill = ChecklistFill(
        id = 10L,
        checklistId = 1L,
        name = "",
        items = listOf(emptyItem, fullItem, singleAttachmentItem),
        createdAt = 0L,
        isDefault = true,
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        attachmentStorage = ControllableAttachmentStorage()
        repository = FakeAttachmentsRepository().apply {
            storedChecklist = testChecklist
            defaultFillFlow.value = testFill
        }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createViewModel(
        paywallRepository: PaywallRepository = FakePaywallRepository(),
    ): ChecklistDetailViewModel {
        val datastore = AppDatastore(
            PreferenceDataStoreFactory.createWithPath {
                "build/test_prefs_attachments_${Random.nextLong()}.preferences_pb".toPath()
            },
            testDispatcher,
        )
        return ChecklistDetailViewModel(
            checklistId = 1L,
            repository = repository,
            navigator = FakeAppNavigator(),
            getUserLimitsUseCase = GetUserLimitsUseCase(
                FakeRemoteConfigProvider(),
                repository,
                paywallRepository,
                FakeUserDataRepository(),
            ),
            analyticsTracker = FakeAnalyticsTracker(),
            reminderScheduler = FakeReminderScheduler(),
            datastore = datastore,
            smartDateParser = FakeSmartDateParser(),
            attachmentStorage = attachmentStorage,
        )
    }

    private fun contentState(vm: ChecklistDetailViewModel): ChecklistDetailState.Content =
        vm.screenState.value as ChecklistDetailState.Content

    // ── Test 1: free user at limit gets premium snackbar, picker NOT triggered ─

    @Test
    fun handleAddImageAttachment_freeTierAtLimit_emitsPremiumSnackbar() = runTest {
        // fullItem already has FREE_ATTACHMENT_LIMIT_PER_ITEM (3) attachments.
        val vm = createViewModel(paywallRepository = FakePaywallRepository(SubscriptionStatus.FREE))

        vm.onIntent(ChecklistDetailIntent.OnAddImageAttachment(itemId = fullItem.id))

        val state = contentState(vm)
        assertEquals(ChecklistDetailViewModel.SNACKBAR_ATTACHMENT_PREMIUM_LIMIT, state.snackbarMessage)
        // Picker must NOT be triggered — user should upgrade first.
        assertTrue(!state.triggerImagePicker)
        assertTrue(!state.triggerFilePicker)
    }

    // ── Test 2: premium user gets picker trigger set ───────────────────────────

    @Test
    fun handleAddImageAttachment_premium_setsImagePickerTrigger() = runTest {
        // fullItem has 3 attachments but premium = no limit.
        val premiumStatus = SubscriptionStatus(isActive = true, activeEntitlements = setOf(Entitlements.PREMIUM))
        val vm = createViewModel(paywallRepository = FakePaywallRepository(premiumStatus))

        vm.onIntent(ChecklistDetailIntent.OnAddImageAttachment(itemId = fullItem.id))

        val state = contentState(vm)
        assertTrue(state.triggerImagePicker)
        assertTrue(!state.triggerFilePicker)
        assertEquals(fullItem.id, state.pendingAttachmentItemId)
        assertNull(state.snackbarMessage)
    }

    // ── Test 3: storeAttachment returns null → load-error snackbar ─────────────

    @Test
    fun handleAttachmentPicked_storeFails_emitsLoadErrorSnackbar() = runTest {
        attachmentStorage.storeResult = null  // simulate storage failure
        val vm = createViewModel()

        vm.onIntent(
            ChecklistDetailIntent.OnAttachmentPicked(
                itemId = emptyItem.id,
                sourcePath = "/tmp/source.jpg",
                fileName = "source.jpg",
                mimeType = "image/jpeg",
            ),
        )

        val state = contentState(vm)
        assertEquals(ChecklistDetailViewModel.SNACKBAR_ATTACHMENT_LOAD_ERROR, state.snackbarMessage)
        assertNull(state.pendingAttachmentItemId)
    }

    // ── Test 4: file too large → delete + snackbar ─────────────────────────────

    @Test
    fun handleAttachmentPicked_sizeOverLimit_deletesFileAndEmitsSnackbar() = runTest {
        val storedPath = "/data/attachments/10/${emptyItem.id}/att_new.jpg"
        attachmentStorage.storeResult = storedPath
        // sizeOf returns over the 10 MB limit.
        attachmentStorage.sizeResult = ChecklistDetailViewModel.MAX_ATTACHMENT_SIZE_BYTES + 1L
        val vm = createViewModel()

        vm.onIntent(
            ChecklistDetailIntent.OnAttachmentPicked(
                itemId = emptyItem.id,
                sourcePath = "/tmp/source.jpg",
                fileName = "source.jpg",
                mimeType = "image/jpeg",
            ),
        )

        val state = contentState(vm)
        assertEquals(ChecklistDetailViewModel.SNACKBAR_ATTACHMENT_TOO_LARGE, state.snackbarMessage)
        assertNull(state.pendingAttachmentItemId)
        // The oversized file must have been deleted.
        assertTrue(attachmentStorage.deletedPaths.contains(storedPath))
    }

    // ── Test 5: success path → repository.addAttachment called ────────────────

    @Test
    fun handleAttachmentPicked_success_callsRepoAddAttachment() = runTest {
        val storedPath = "/data/attachments/10/${emptyItem.id}/att_new.jpg"
        attachmentStorage.storeResult = storedPath
        attachmentStorage.sizeResult = 500_000L   // well under 10 MB limit
        attachmentStorage.probeResult = 800 to 600
        val vm = createViewModel()

        vm.onIntent(
            ChecklistDetailIntent.OnAttachmentPicked(
                itemId = emptyItem.id,
                sourcePath = "/tmp/source.jpg",
                fileName = "source.jpg",
                mimeType = "image/jpeg",
            ),
        )

        // Repository must have received the addAttachment call.
        assertEquals(1, repository.addAttachmentCalls.size)
        val (callFillId, callItemId, callAttachment) = repository.addAttachmentCalls.first()
        assertEquals(testFill.id, callFillId)
        assertEquals(emptyItem.id, callItemId)
        assertEquals(storedPath, callAttachment.path)
        assertEquals("source.jpg", callAttachment.fileName)
        assertEquals("image/jpeg", callAttachment.mimeType)
        assertEquals(500_000L, callAttachment.sizeBytes)
        assertEquals(800, callAttachment.width)
        assertEquals(600, callAttachment.height)

        // State is cleaned up.
        val state = contentState(vm)
        assertNull(state.pendingAttachmentItemId)
        assertNull(state.snackbarMessage)
    }

    // ── Test 6: delete last attachment closes viewer, calls repo ───────────────

    @Test
    fun handleDeleteAttachment_callsRepoRemove_andClosesViewerIfLastAttachment() = runTest {
        val vm = createViewModel()
        // First open the viewer for singleAttachmentItem.
        vm.onIntent(ChecklistDetailIntent.OnAttachmentClick(existingAttachment.id))

        // Viewer should be open.
        assertNotNull(contentState(vm).attachmentViewerState)

        // Now delete the only attachment.
        vm.onIntent(
            ChecklistDetailIntent.OnDeleteAttachment(
                itemId = singleAttachmentItem.id,
                attachmentId = existingAttachment.id,
            ),
        )

        // Repository received the remove call.
        assertEquals(1, repository.removeAttachmentCalls.size)
        val (callFillId, callItemId, callAttachmentId) = repository.removeAttachmentCalls.first()
        assertEquals(testFill.id, callFillId)
        assertEquals(singleAttachmentItem.id, callItemId)
        assertEquals(existingAttachment.id, callAttachmentId)

        // Viewer must be closed proactively (last attachment gone).
        assertNull(contentState(vm).attachmentViewerState)

        // Snackbar confirms deletion.
        assertEquals(ChecklistDetailViewModel.SNACKBAR_ATTACHMENT_DELETED, contentState(vm).snackbarMessage)
    }

    // ── Test 7: OnAttachmentClick sets viewer state with correct itemId ────────

    @Test
    fun handleAttachmentClick_setsViewerState() = runTest {
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnAttachmentClick(existingAttachment.id))

        val viewer = contentState(vm).attachmentViewerState
        assertNotNull(viewer)
        assertEquals(singleAttachmentItem.id, viewer.itemId)
        assertEquals(existingAttachment.id, viewer.initialAttachmentId)
    }

    // ── Test 8: OnOpenAttachmentExternally sets pendingOpenExternallyPath ──────

    @Test
    fun handleOpenAttachmentExternally_setsPendingOpenExternallyState() = runTest {
        val vm = createViewModel()

        vm.onIntent(ChecklistDetailIntent.OnOpenAttachmentExternally(existingAttachment.id))

        val state = contentState(vm)
        assertEquals(existingAttachment.path, state.pendingOpenExternallyPath)
        assertEquals(existingAttachment.mimeType, state.pendingOpenExternallyMimeType)
    }

    // ── Fakes ─────────────────────────────────────────────────────────────────

    /**
     * Controllable fake: callers set [storeResult], [sizeResult], [probeResult].
     * [deletedPaths] records every [deleteAttachment] call for assertion.
     */
    private inner class ControllableAttachmentStorage : AttachmentStoragePort {
        var storeResult: String? = "/data/attachments/stored_file"
        var sizeResult: Long = 100_000L
        var probeResult: Pair<Int?, Int?> = null to null
        val deletedPaths = mutableListOf<String>()

        override suspend fun storeAttachment(
            sourcePath: String,
            fillId: Long,
            itemId: String,
            attachmentId: String,
            originalFileName: String,
        ): String? = storeResult

        override suspend fun deleteAttachment(path: String) {
            deletedPaths += path
        }

        override suspend fun deleteAttachmentsFor(fillId: Long, itemId: String) {}
        override suspend fun deleteAttachmentsForFill(fillId: Long) {}
        override suspend fun probeImage(path: String, mimeType: String?): Pair<Int?, Int?> = probeResult
        override suspend fun sizeOf(path: String): Long = sizeResult
    }

    /** Tracking repository: records addAttachment / removeAttachment calls. */
    private class FakeAttachmentsRepository : ChecklistRepository {
        var storedChecklist: Checklist? = null
        val defaultFillFlow = MutableStateFlow<ChecklistFill?>(null)

        data class AddAttachmentCall(val fillId: Long, val itemId: String, val attachment: Attachment)
        data class RemoveAttachmentCall(val fillId: Long, val itemId: String, val attachmentId: String)

        val addAttachmentCalls = mutableListOf<AddAttachmentCall>()
        val removeAttachmentCalls = mutableListOf<RemoveAttachmentCall>()

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
        override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<TodayReminderInfo>> = flowOf(emptyList())
        override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<TodayReminderInfo> = emptyList()
        override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)

        override suspend fun addAttachment(fillId: Long, itemId: String, attachment: Attachment) {
            addAttachmentCalls += AddAttachmentCall(fillId, itemId, attachment)
        }

        override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) {
            removeAttachmentCalls += RemoveAttachmentCall(fillId, itemId, attachmentId)
        }
    }

    private class FakePaywallRepository(
        status: SubscriptionStatus = SubscriptionStatus.FREE,
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
    }

    private class FakeRemoteConfigProvider : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getString(key: String, defaultValue: String): String = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
    }

    private class FakeAnalyticsTracker : AnalyticsTracker {
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {}
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

    private class FakeSmartDateParser :
        com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.SmartDateParser {
        override fun parse(
            input: String,
            now: Long,
            timeZone: kotlinx.datetime.TimeZone,
        ): com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.ParsedDateToken? = null
    }
}
