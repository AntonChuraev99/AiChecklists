package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResultHolder
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.InputDataType
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetSubscriptionStatusUseCase
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [AnalyzeViewModel.analyzeInput] success routing after the "create skips preview" change:
 *  - Creating a NEW checklist (not fill mode) skips the editable preview entirely — it creates the
 *    checklist via [AnalyzeRepository.createChecklistFromResult] and lands the user straight on the
 *    created checklist's detail (`navigateToChecklistDetail(clearBackStack = true)`).
 *  - FILL flows still go through the preview (`navigateToAnalyzeResultPreview`).
 *  - The activation funnel ([ActivationCoordinator.onAiChecklistCreated]) fires only for the
 *    activation hero (autoAnalyze), not for a manual create.
 *  - A create failure surfaces an error and does NOT navigate.
 *
 * Host-resolution note: the SUT's create path resolves the fallback name through Compose Resources
 * `getString(default_checklist_name)`, which is not resolvable on the JVM host. Every create-path
 * test therefore supplies a non-blank `suggestedName` so the `?:` fallback (the only `getString`
 * line on the create path) never runs — the AI-provided name is used verbatim.
 *
 * [AnalyzeResultHolder] is a process-global object; it is cleared in setup/teardown so the FILL
 * test's `set(...)` cannot leak into other tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyzeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeNavigator: FakeAppNavigator
    private lateinit var fakeAnalyzeRepository: FakeAnalyzeRepository
    private lateinit var fakeChecklistRepository: FakeChecklistRepository
    private lateinit var fakeUserDataRepository: FakeUserDataRepository
    private lateinit var fakeActivationCoordinator: RecordingActivationCoordinator
    private lateinit var fakeRemoteConfigProvider: FakeRemoteConfigProvider
    private lateinit var fakeAnalyticsTracker: RecordingAnalyticsTracker
    private lateinit var fakePaywallRepository: FakePaywallRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        AnalyzeResultHolder.clear()
        fakeNavigator = FakeAppNavigator()
        fakeAnalyzeRepository = FakeAnalyzeRepository()
        fakeChecklistRepository = FakeChecklistRepository()
        fakeUserDataRepository = FakeUserDataRepository()
        fakeActivationCoordinator = RecordingActivationCoordinator()
        fakeRemoteConfigProvider = FakeRemoteConfigProvider()
        fakeAnalyticsTracker = RecordingAnalyticsTracker()
        fakePaywallRepository = FakePaywallRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        AnalyzeResultHolder.clear()
    }

    private fun createViewModel(
        checklistId: Long? = null,
        fillDefault: Boolean = false,
        initialText: String? = null,
        autoAnalyze: Boolean = false,
    ): AnalyzeViewModel = AnalyzeViewModel(
        checklistId = checklistId,
        fillDefault = fillDefault,
        initialText = initialText,
        autoAnalyze = autoAnalyze,
        analyzeRepository = fakeAnalyzeRepository,
        checklistRepository = fakeChecklistRepository,
        appNavigator = fakeNavigator,
        userDataRepository = fakeUserDataRepository,
        getSubscriptionStatusUseCase = GetSubscriptionStatusUseCase(fakePaywallRepository),
        analyticsTracker = fakeAnalyticsTracker,
        activationCoordinator = fakeActivationCoordinator,
        remoteConfigProvider = fakeRemoteConfigProvider,
    )

    /** Drives a manual (non-hero) analysis: select RAW_TEXT, type a prompt, then tap Analyze. */
    private fun AnalyzeViewModel.startManualRawTextAnalysis(text: String) {
        onIntent(AnalyzeScreenIntent.OnInputTypeSelected(InputDataType.RAW_TEXT))
        onIntent(AnalyzeScreenIntent.OnTextInputChanged(text))
        onIntent(AnalyzeScreenIntent.OnAnalyzeClick)
    }

    @Test
    fun analyzeInput_newChecklistFromActivationHero_createsDirectlyAndOpensDetail() = runTest {
        // autoAnalyze=true + initialText + new checklist (checklistId=null): init runs analyzeInput()
        // itself; on success it must create the checklist directly and open its detail — NOT the
        // preview — and fire the activation funnel with the created id.
        fakeAnalyzeRepository.resultName = "Birthday Party"
        val vm = createViewModel(
            checklistId = null,
            initialText = "Birthday party",
            autoAnalyze = true,
        )
        advanceUntilIdle()

        // Created directly via the repository, exactly once, with the AI-suggested name.
        assertEquals(1, fakeAnalyzeRepository.createCallCount)
        assertEquals("Birthday Party", fakeAnalyzeRepository.lastCreateName)
        // Landed on the CREATED checklist's detail with a cleared back stack.
        assertTrue(fakeNavigator.navigatedToChecklistDetail)
        assertEquals(FakeAnalyzeRepository.CREATED_ID, fakeNavigator.lastDetailChecklistId)
        assertTrue(fakeNavigator.lastDetailClearBackStack)
        // The editable preview is skipped for create.
        assertFalse(fakeNavigator.navigatedToAnalyzeResultPreview)
        // Activation funnel fired with the created id (autoAnalyze hero path).
        assertEquals(FakeAnalyzeRepository.CREATED_ID, fakeActivationCoordinator.lastChecklistId)
        // CHECKLIST_CREATED analytics recorded with source=ai.
        val created = assertNotNull(
            fakeAnalyticsTracker.events.firstOrNull { it.first == AnalyticsEvents.Checklist.CREATED },
            "checklist_created event must be recorded",
        )
        assertEquals("ai", created.second["source"])
        // The loader stays raised through navigation by design (clearBackStack replaces this screen
        // directly; resetting isAnalyzing would flash the input screen for a frame). Navigation
        // happening — asserted above — is the success signal.
    }

    @Test
    fun analyzeInput_newChecklistManualNotHero_createsDirectlyButSkipsActivation() = runTest {
        // Manual create (autoAnalyze=false): still creates directly + opens detail, but the
        // activation funnel must NOT fire (that is reserved for the autoAnalyze hero).
        fakeAnalyzeRepository.resultName = "Groceries"
        val vm = createViewModel(checklistId = null, autoAnalyze = false)
        advanceUntilIdle()

        vm.startManualRawTextAnalysis("weekly groceries")
        advanceUntilIdle()

        assertEquals(1, fakeAnalyzeRepository.createCallCount)
        assertTrue(fakeNavigator.navigatedToChecklistDetail)
        assertEquals(FakeAnalyzeRepository.CREATED_ID, fakeNavigator.lastDetailChecklistId)
        assertFalse(fakeNavigator.navigatedToAnalyzeResultPreview)
        // No activation funnel for the manual path.
        assertNull(fakeActivationCoordinator.lastChecklistId)
    }

    // NOTE: the FILL routing (isFillMode/fillDefault → AnalyzeResultPreview, never direct-create) is
    // intentionally NOT unit-tested here. That branch resolves the fill name via Compose Resources
    // `getString(default_fill_name)` UNCONDITIONALLY, which throws "Resources.getSystem not mocked" on
    // the plain Android host test (no Robolectric) — the same limitation WelcomeOnboardingViewModelTest
    // sidesteps with an injected resolver (AnalyzeViewModel has no such seam). The create path tests
    // avoid it by supplying a non-blank suggestedName so the only create-path getString (the `?:`
    // fallback) never runs. Fill routing is guarded by a simple `if (isFillMode || fillDefault)` in
    // analyzeInput (unchanged behavior) and verified on-device.

    @Test
    fun analyzeInput_newChecklistCreateFails_setsErrorNoNavigation() = runTest {
        // createChecklistFromResult returns failure → error surfaced, loader cleared, no navigation,
        // no activation funnel.
        fakeAnalyzeRepository.resultName = "Birthday Party"
        fakeAnalyzeRepository.createShouldFail = true
        val vm = createViewModel(
            checklistId = null,
            initialText = "Birthday party",
            autoAnalyze = true,
        )
        advanceUntilIdle()

        assertEquals(1, fakeAnalyzeRepository.createCallCount)
        val state = vm.screenState.value
        assertNotNull(state.error, "create failure must surface an error")
        assertFalse(state.isAnalyzing)
        assertFalse(fakeNavigator.navigatedToChecklistDetail)
        assertFalse(fakeNavigator.navigatedToAnalyzeResultPreview)
        assertNull(fakeActivationCoordinator.lastChecklistId)
    }

    // ── Test doubles ────────────────────────────────────────────────────────────

    /**
     * Drives the create path: [analyzeData] returns a result with [resultName] as the suggested
     * name (kept non-blank by callers so the SUT never hits the `getString` fallback), and
     * [createChecklistFromResult] returns a checklist with [CREATED_ID]. Either step can fail.
     */
    private class FakeAnalyzeRepository : AnalyzeRepository {
        var analyzeShouldFail = false
        var createShouldFail = false
        var resultName: String? = "AI Name"

        var analyzeCallCount = 0
        var createCallCount = 0
        var lastInput: AnalyzeInputData? = null
        var lastCreateName: String? = null

        override suspend fun analyzeData(
            inputData: AnalyzeInputData,
            targetChecklist: Checklist?,
        ): Result<AnalyzeResult> {
            analyzeCallCount++
            lastInput = inputData
            if (analyzeShouldFail) return Result.failure(RuntimeException("analyze failed"))
            return Result.success(AnalyzeResult(suggestedItems = emptyList(), suggestedName = resultName))
        }

        override suspend fun createChecklistFromResult(
            name: String,
            result: AnalyzeResult,
        ): Result<Checklist> {
            createCallCount++
            lastCreateName = name
            if (createShouldFail) return Result.failure(RuntimeException("create failed"))
            return Result.success(Checklist(id = CREATED_ID, name = name, items = emptyList()))
        }

        override suspend fun applyToChecklist(checklistId: Long, result: AnalyzeResult): Result<Checklist> =
            Result.failure(NotImplementedError())

        override suspend fun createFillFromResult(
            checklistId: Long,
            fillName: String,
            result: AnalyzeResult,
        ): Result<Long> = Result.failure(NotImplementedError())

        override suspend fun isAnalyzerAvailable(): Boolean = true

        companion object {
            const val CREATED_ID = 42L
        }
    }

    private class FakeAppNavigator : AppNavigator {
        var navigatedToMainScreen = false
        var navigatedToChecklistDetail = false
        var lastDetailChecklistId: Long? = null
        var lastDetailClearBackStack = false
        var navigatedToAnalyzeScreen = false
        var navigatedToAnalyzeResultPreview = false

        override val events: SharedFlow<AppNavEvent> = MutableSharedFlow()
        override val backStack: NavBackStack<NavKey> = NavBackStack()
        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
        override fun onBack() {}
        override fun navigateToOnboarding() {}
        override fun navigateToInteractiveOnboarding() {}
        override fun navigateToWelcomeOnboarding() {}
        override fun navigateToMainScreen(clearBackStack: Boolean) {
            navigatedToMainScreen = true
        }
        override fun navigateToDebugMenu() {}
        override fun navigateToStoreScreenshot() {}
        override fun navigateToCreateChecklistScreen(templateId: Int?, initialText: String?) {}
        override fun navigateToEditChecklist(checklistId: Long) {}
        override fun navigateToTemplatesScreen() {}
        override fun navigateToTemplatePreview(templateId: String) {}
        override fun navigateToAnalyzeScreen(checklistId: Long?, fillDefault: Boolean, initialText: String?, autoAnalyze: Boolean) {
            navigatedToAnalyzeScreen = true
        }
        override fun navigateToAnalyzeResultPreview() {
            navigatedToAnalyzeResultPreview = true
        }
        override fun navigateToChecklistDetail(checklistId: Long, focusItemId: String?, clearBackStack: Boolean) {
            navigatedToChecklistDetail = true
            lastDetailChecklistId = checklistId
            lastDetailClearBackStack = clearBackStack
        }
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

    private class FakeUserDataRepository : UserDataRepository {
        private val dataFlow = MutableStateFlow(UserData())

        override fun getUserDataFlow(): StateFlow<UserData> = dataFlow
        override suspend fun getUserData(): UserData = dataFlow.value
        override suspend fun update(userData: UserData) {
            dataFlow.value = userData
        }
        override suspend fun ensureUserRegistered(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = UserData(), isNewUser = false))
        override suspend fun syncWithServer(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = UserData(), isNewUser = false))
        override suspend fun isPaywallLinked(): Boolean = false
        override suspend fun setPaywallLinked(linked: Boolean) {}
        override suspend fun restoreCreditsAfterPurchase(): Result<Int> = Result.success(0)
        override suspend fun getFirstLaunchAtMillis(): Long = 0L
    }

    private class RecordingAnalyticsTracker : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()

        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {
            events.add(name to params)
        }
    }

    /** Records the checklist id handed to the activation funnel (or null if never called). */
    private class RecordingActivationCoordinator : ActivationCoordinator {
        var lastChecklistId: Long? = null
        var lastBundleEnabled: Boolean? = null

        override val reminderOptInRequests: SharedFlow<Long> = MutableSharedFlow()

        override suspend fun onAiChecklistCreated(checklistId: Long, activationBundleEnabled: Boolean) {
            lastChecklistId = checklistId
            lastBundleEnabled = activationBundleEnabled
        }

        override suspend fun reportReminderOptInOutcome(granted: Boolean) {}
    }

    private class FakeRemoteConfigProvider : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getString(key: String, defaultValue: String): String = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
    }

    /** Backs [GetSubscriptionStatusUseCase] with a FREE (non-premium) status flow. */
    private class FakePaywallRepository : PaywallRepository {
        override val subscriptionStatus: Flow<SubscriptionStatus> = flowOf(SubscriptionStatus.FREE)
        override suspend fun getOfferings(offeringId: String): Result<PaywallOffering?> =
            Result.success(null)
        override suspend fun purchase(packageId: String): PurchaseResult = PurchaseResult.Cancelled
        override suspend fun restorePurchases(): RestoreResult = RestoreResult.NoActiveSubscription
        override suspend fun refreshSubscriptionStatus() {}
        override fun isConfigured(): Boolean = true
        override suspend fun logIn(appUserId: String): Result<LoginResult> =
            Result.failure(NotImplementedError())
        override suspend fun logOut(): Result<SubscriptionStatus> =
            Result.success(SubscriptionStatus.FREE)
    }

    private class FakeChecklistRepository : ChecklistRepository {
        // Returned by getChecklistById for fill mode (init's loadTargetChecklist).
        var checklistForId: Checklist? = null

        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())

        override suspend fun getChecklistById(id: Long): Checklist? = checklistForId
        override fun observeChecklistById(id: Long): Flow<Checklist?> = flowOf(checklistForId)

        override suspend fun addChecklist(checklist: Checklist): Long = 1L
        override suspend fun updateChecklist(checklist: Checklist) {}
        override suspend fun updateChecklistTemplate(checklist: Checklist) {}
        override suspend fun deleteChecklist(checklist: Checklist) {}
        override suspend fun reorderChecklists(orderedIds: List<Long>) {}
        override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setFoldersEnabled(checklistId: Long, value: Boolean) {}
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
        override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<TodayReminderInfo>> = flowOf(emptyList())
        override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<TodayReminderInfo> = emptyList()
        override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addAttachment(fillId: Long, itemId: String, attachment: Attachment) {}
        override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) {}
        override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = flowOf(null)
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override suspend fun getFillById(id: Long): ChecklistFill? = null
        override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun addFill(fill: ChecklistFill): Long = 1L
        override suspend fun updateFill(fill: ChecklistFill) {}
        override suspend fun deleteFill(fill: ChecklistFill) {}
        override suspend fun reorderItems(fill: ChecklistFill, checklist: Checklist) {}
    }
}
