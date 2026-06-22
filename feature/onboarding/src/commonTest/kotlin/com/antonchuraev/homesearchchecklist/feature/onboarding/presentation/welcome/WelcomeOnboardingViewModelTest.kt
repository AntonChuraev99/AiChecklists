package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import androidx.lifecycle.SavedStateHandle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * Covers the final-step ("create my first checklist") three-branch resolution of name + starter
 * items, plus the step/skip flow.
 *
 * The ViewModel resolves item/name strings through Compose Resources (`getString`), which is not
 * resolvable on the JVM host — so we inject a fake [WelcomeStringResolver] that echoes a stable
 * non-blank string per key. That lets `addChecklist` actually run and the resolved items be
 * asserted by count/non-emptiness (their exact localized text is verified on-device, not here).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeOnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeNavigator: FakeAppNavigator
    private lateinit var fakeChecklistRepository: FakeChecklistRepository
    private lateinit var fakeAnalyticsTracker: RecordingAnalyticsTracker
    private lateinit var fakeUserDataRepository: FakeUserDataRepository

    // Echoes a stable, non-blank placeholder per key — enough to assert item count / non-emptiness.
    private val fakeStringResolver = WelcomeStringResolver { "resolved_text" }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeNavigator = FakeAppNavigator()
        fakeChecklistRepository = FakeChecklistRepository()
        fakeAnalyticsTracker = RecordingAnalyticsTracker()
        fakeUserDataRepository = FakeUserDataRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): WelcomeOnboardingViewModel = WelcomeOnboardingViewModel(
        savedStateHandle = SavedStateHandle(),
        navigator = fakeNavigator,
        completeOnboardingUseCase = CompleteOnboardingUseCase(fakeUserDataRepository),
        checklistRepository = fakeChecklistRepository,
        analyticsTracker = fakeAnalyticsTracker,
        logger = NoOpLogger(),
        isDebugBuild = true,
        stringResolver = fakeStringResolver,
    )

    // --- Initial state & step navigation ---

    @Test
    fun initialState_isWelcomeStep() = runTest {
        val vm = createViewModel()

        val state = vm.screenState.value
        assertEquals(WelcomeOnboardingStep.Welcome, state.currentStep)
        assertEquals("", state.inputText)
        assertEquals(null, state.selectedTemplateKey)
        assertFalse(state.isCreating)
    }

    @Test
    fun onNext_advancesThroughSteps() = runTest {
        val vm = createViewModel()

        vm.onIntent(WelcomeOnboardingIntent.OnNext)
        assertEquals(WelcomeOnboardingStep.Capture, vm.screenState.value.currentStep)
        vm.onIntent(WelcomeOnboardingIntent.OnNext)
        assertEquals(WelcomeOnboardingStep.Value, vm.screenState.value.currentStep)
        vm.onIntent(WelcomeOnboardingIntent.OnNext)
        assertEquals(WelcomeOnboardingStep.FirstChecklist, vm.screenState.value.currentStep)
    }

    @Test
    fun onNext_atLastStep_isNoOp() = runTest {
        val vm = createViewModel()
        repeat(3) { vm.onIntent(WelcomeOnboardingIntent.OnNext) }
        assertEquals(WelcomeOnboardingStep.FirstChecklist, vm.screenState.value.currentStep)

        vm.onIntent(WelcomeOnboardingIntent.OnNext)

        assertEquals(WelcomeOnboardingStep.FirstChecklist, vm.screenState.value.currentStep)
    }

    @Test
    fun onBack_fromWelcome_isNoOp() = runTest {
        val vm = createViewModel()

        vm.onIntent(WelcomeOnboardingIntent.OnBack)

        assertEquals(WelcomeOnboardingStep.Welcome, vm.screenState.value.currentStep)
    }

    @Test
    fun onBack_fromCapture_returnsToWelcome() = runTest {
        val vm = createViewModel()
        vm.onIntent(WelcomeOnboardingIntent.OnNext)
        assertEquals(WelcomeOnboardingStep.Capture, vm.screenState.value.currentStep)

        vm.onIntent(WelcomeOnboardingIntent.OnBack)

        assertEquals(WelcomeOnboardingStep.Welcome, vm.screenState.value.currentStep)
    }

    @Test
    fun onTemplateSelected_selectsAndClearsTypedText() = runTest {
        val vm = createViewModel()
        vm.onIntent(WelcomeOnboardingIntent.OnInputChanged("my own topic"))

        vm.onIntent(WelcomeOnboardingIntent.OnTemplateSelected(WelcomeStarterTemplate.TRIP.key))

        val state = vm.screenState.value
        assertEquals(WelcomeStarterTemplate.TRIP.key, state.selectedTemplateKey)
        assertEquals("", state.inputText)
    }

    @Test
    fun onTemplateSelected_reTap_deselects() = runTest {
        val vm = createViewModel()
        vm.onIntent(WelcomeOnboardingIntent.OnTemplateSelected(WelcomeStarterTemplate.TRIP.key))
        assertEquals(WelcomeStarterTemplate.TRIP.key, vm.screenState.value.selectedTemplateKey)

        vm.onIntent(WelcomeOnboardingIntent.OnTemplateSelected(WelcomeStarterTemplate.TRIP.key))

        assertEquals(null, vm.screenState.value.selectedTemplateKey)
    }

    // --- Final step: three-branch resolution ---

    @Test
    fun createFirstChecklist_chipSelected_usesChipNameAndChipItems() = runTest {
        val vm = createViewModel()
        vm.onIntent(WelcomeOnboardingIntent.OnTemplateSelected(WelcomeStarterTemplate.TRIP.key))

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        val saved = assertNotNull(fakeChecklistRepository.lastAddedChecklist)
        // Name resolved from the chip's name resource (non-blank placeholder from the fake resolver).
        assertEquals("resolved_text", saved.name)
        // Items always come from the chip's preset — count must match the chip's itemRes list.
        assertEquals(WelcomeStarterTemplate.TRIP.itemRes.size, saved.items.size)
        assertTrue(saved.items.all { it.text.isNotBlank() })
        assertEquals(1, fakeChecklistRepository.addChecklistCallCount)
    }

    @Test
    fun createFirstChecklist_chipWins_chipNameAndItemsEvenIfTextLingersInState() = runTest {
        // A selected chip is the unambiguous seed: its name + items always win, even if typed text
        // somehow lingers in state. (In normal UX selecting a chip clears the field AND hides it, so
        // this combination is unreachable from the UI; we inject typed text directly to prove the
        // resolution ignores it.) The chip branch never runs AI — it seeds from the chip's preset.
        val vm = createViewModel()
        vm.onIntent(WelcomeOnboardingIntent.OnTemplateSelected(WelcomeStarterTemplate.WORKOUT.key))
        vm.onIntent(WelcomeOnboardingIntent.OnInputChanged("Leg day"))
        // OnInputChanged does not clear the chip, so both are present in state here.
        assertEquals(WelcomeStarterTemplate.WORKOUT.key, vm.screenState.value.selectedTemplateKey)
        assertEquals("Leg day", vm.screenState.value.inputText)

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        val saved = assertNotNull(fakeChecklistRepository.lastAddedChecklist)
        // Name from the chip's name resource (the fake resolver echoes "resolved_text"), NOT "Leg day".
        assertEquals("resolved_text", saved.name)
        assertEquals(WelcomeStarterTemplate.WORKOUT.itemRes.size, saved.items.size)
        // Chip path must not divert to AI.
        assertFalse(fakeNavigator.navigatedToAnalyzeScreen)
        assertTrue(fakeNavigator.navigatedToChecklistDetail)
    }

    @Test
    fun createFirstChecklist_typedNoChip_runsAiAnalyzeFlowDoesNotCreateChecklist() = runTest {
        val vm = createViewModel()
        vm.onIntent(WelcomeOnboardingIntent.OnInputChanged("Birthday party prep"))

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        // Branch 2: free text with no chip hands off to the AI Analyze flow — the ViewModel must NOT
        // create a checklist itself (Analyze generates + previews + creates it via AI).
        assertEquals(0, fakeChecklistRepository.addChecklistCallCount)
        assertNull(fakeChecklistRepository.lastAddedChecklist)
        // Onboarding is completed BEFORE the hand-off so the user can't return to onboarding.
        assertTrue(fakeUserDataRepository.onboardingMarkedPassed)
        // Navigated to Analyze with the typed prompt + auto-analyze on; NOT to a checklist detail.
        assertTrue(fakeNavigator.navigatedToAnalyzeScreen)
        assertEquals("Birthday party prep", fakeNavigator.lastAnalyzeInitialText)
        assertTrue(fakeNavigator.lastAnalyzeAutoAnalyze)
        assertFalse(fakeNavigator.lastAnalyzeFillDefault)
        assertFalse(fakeNavigator.navigatedToChecklistDetail)
    }

    @Test
    fun createFirstChecklist_typedWhitespaceOnly_treatedAsEmptyUsesDefaultStarter() = runTest {
        val vm = createViewModel()
        vm.onIntent(WelcomeOnboardingIntent.OnInputChanged("   "))

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        val saved = assertNotNull(fakeChecklistRepository.lastAddedChecklist)
        // Blank-after-trim falls through to the default branch: default name + non-empty starter.
        assertEquals("resolved_text", saved.name)
        assertEquals(WelcomeStarterTemplate.DEFAULT_STARTER_ITEMS.size, saved.items.size)
        assertTrue(saved.items.isNotEmpty())
    }

    @Test
    fun createFirstChecklist_emptyInputNoChip_usesDefaultNameAndDefaultStarterItems() = runTest {
        val vm = createViewModel()

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        val saved = assertNotNull(fakeChecklistRepository.lastAddedChecklist)
        assertEquals("resolved_text", saved.name)
        // The "auto-create with steps" path — never blank.
        assertEquals(WelcomeStarterTemplate.DEFAULT_STARTER_ITEMS.size, saved.items.size)
        assertTrue(saved.items.isNotEmpty())
        assertTrue(saved.items.all { it.text.isNotBlank() })
    }

    @Test
    fun createFirstChecklist_navigatesToDetailWithClearedBackStack() = runTest {
        val vm = createViewModel()

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeNavigator.navigatedToChecklistDetail)
        assertTrue(fakeNavigator.lastDetailClearBackStack)
        assertEquals(1L, fakeNavigator.lastDetailChecklistId)
        assertTrue(fakeUserDataRepository.onboardingMarkedPassed)
    }

    @Test
    fun createFirstChecklist_repoError_setsErrorAndDoesNotNavigate() = runTest {
        fakeChecklistRepository.shouldThrowOnAdd = true
        val vm = createViewModel()

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.screenState.value
        assertFalse(state.isCreating)
        assertEquals(WelcomeOnboardingViewModel.ERROR_KEY, state.error)
        assertFalse(fakeNavigator.navigatedToChecklistDetail)
    }

    // --- Skip ---

    @Test
    fun onSkip_completesOnboardingToMainScreen() = runTest {
        val vm = createViewModel()

        vm.onIntent(WelcomeOnboardingIntent.OnSkip)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeUserDataRepository.onboardingMarkedPassed)
        assertTrue(fakeNavigator.navigatedToMainScreen)
        assertFalse(fakeNavigator.navigatedToChecklistDetail)
    }

    // --- Test doubles ---

    private class NoOpLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }

    private class FakeChecklistRepository : ChecklistRepository {
        var shouldThrowOnAdd = false
        var addChecklistCallCount = 0
        var lastAddedChecklist: Checklist? = null
        private var savedChecklistId: Long? = null

        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())

        override suspend fun addChecklist(checklist: Checklist): Long {
            addChecklistCallCount++
            if (shouldThrowOnAdd) throw RuntimeException("Room write error")
            lastAddedChecklist = checklist
            savedChecklistId = 1L
            return 1L
        }

        override suspend fun updateChecklist(checklist: Checklist) {}
        override suspend fun updateChecklistTemplate(checklist: Checklist) {}
        override suspend fun deleteChecklist(checklist: Checklist) {}

        override suspend fun getChecklistById(id: Long): Checklist? =
            if (id == savedChecklistId) lastAddedChecklist?.copy(id = id) else null
        override fun observeChecklistById(id: Long): Flow<Checklist?> =
            flowOf(if (id == savedChecklistId) lastAddedChecklist?.copy(id = id) else null)
        override suspend fun reorderChecklists(orderedIds: List<Long>) {}
        override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setFoldersEnabled(checklistId: Long, value: Boolean) {}
        override suspend fun setReminder(checklistId: Long, reminderAt: Long?) {}
        override suspend fun countActiveReminders(): Int = 0
        override suspend fun getActiveReminders(): List<ChecklistReminderInfo> = emptyList()
        override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? = null
        override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = flowOf(null)
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override suspend fun getFillById(id: Long): ChecklistFill? = null
        override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun addFill(fill: ChecklistFill): Long = 1L
        override suspend fun updateFill(fill: ChecklistFill) {}
        override suspend fun deleteFill(fill: ChecklistFill) {}
        override suspend fun reorderItems(fill: ChecklistFill, checklist: Checklist) {}
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
        override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo>> = flowOf(emptyList())
        override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo> = emptyList()
        override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addAttachment(fillId: Long, itemId: String, attachment: com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment) = Unit
        override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) = Unit
        override suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getTotalAdditionalFillCount(): Int = 0
        override suspend fun getWeeklyChecklistCount(): Int = 0
        override val weeklyChecklistCount: Flow<Int> = flowOf(0)
        override suspend fun getAllItemRemindersForRescheduling(): List<ItemReminderInfo> = emptyList()
    }

    private class FakeAppNavigator : AppNavigator {
        var navigatedToMainScreen = false
        var navigatedToChecklistDetail = false
        var lastDetailChecklistId: Long? = null
        var lastDetailClearBackStack = false
        var navigatedToAnalyzeScreen = false
        var lastAnalyzeInitialText: String? = null
        var lastAnalyzeAutoAnalyze = false
        var lastAnalyzeFillDefault = false

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
            lastAnalyzeInitialText = initialText
            lastAnalyzeAutoAnalyze = autoAnalyze
            lastAnalyzeFillDefault = fillDefault
        }
        override fun navigateToAnalyzeResultPreview() {}
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
        var onboardingMarkedPassed = false

        override fun getUserDataFlow(): StateFlow<UserData> = dataFlow
        override suspend fun getUserData(): UserData = dataFlow.value

        override suspend fun update(userData: UserData) {
            if (userData.isOnboardingPassed) onboardingMarkedPassed = true
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
        private val events = mutableListOf<Pair<String, Map<String, Any>>>()

        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {
            events.add(name to params)
        }
    }
}
