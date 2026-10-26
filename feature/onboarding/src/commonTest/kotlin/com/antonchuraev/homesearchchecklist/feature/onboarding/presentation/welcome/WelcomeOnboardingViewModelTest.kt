package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import androidx.lifecycle.SavedStateHandle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AiEntrySource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.NavExperimentResolver
import com.antonchuraev.homesearchchecklist.core.common.api.NavVariant
import com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase.EnsureInboxUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
import kotlin.test.assertIs
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
    private lateinit var fakeAnalyzeRepository: FakeAnalyzeRepository
    private lateinit var fakeActivationCoordinator: RecordingActivationCoordinator
    private lateinit var fakeRemoteConfigProvider: FakeRemoteConfigProvider
    private lateinit var fakeAnalyticsTracker: RecordingAnalyticsTracker
    private lateinit var fakeUserDataRepository: FakeUserDataRepository

    // Echoes a stable, non-blank placeholder per key — enough to assert item count / non-emptiness.
    private val fakeStringResolver = WelcomeStringResolver { "resolved_text" }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeNavigator = FakeAppNavigator()
        fakeChecklistRepository = FakeChecklistRepository()
        fakeAnalyzeRepository = FakeAnalyzeRepository()
        fakeActivationCoordinator = RecordingActivationCoordinator()
        fakeRemoteConfigProvider = FakeRemoteConfigProvider()
        fakeAnalyticsTracker = RecordingAnalyticsTracker()
        fakeUserDataRepository = FakeUserDataRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Defaults to the CLASSIC (v1) shell: the pre-existing branch-3 tests pin the "separate default
     * checklist + detail" contract, which is still exactly what v1 does (it has no Inbox). The v2
     * Inbox path is covered by the `*_v2_*` tests below, which pass [NavVariant.V2] explicitly.
     */
    private fun createViewModel(
        variant: NavVariant = NavVariant.CONTROL,
        // Analytics fire only outside debug builds; the funnel tests pass false to record them.
        isDebugBuild: Boolean = true,
    ): WelcomeOnboardingViewModel =
        WelcomeOnboardingViewModel(
            savedStateHandle = SavedStateHandle(),
            navigator = fakeNavigator,
            completeOnboardingUseCase = CompleteOnboardingUseCase(fakeUserDataRepository),
            checklistRepository = fakeChecklistRepository,
            analyzeRepository = fakeAnalyzeRepository,
            activationCoordinator = fakeActivationCoordinator,
            analyticsTracker = fakeAnalyticsTracker,
            logger = NoOpLogger(),
            navExperimentResolver = FixedNavResolver(variant),
            // Real use case over the fake repository, so the test drives the true ensure path
            // (including its null-on-failure contract) rather than a stub.
            ensureInbox = EnsureInboxUseCase(fakeChecklistRepository, fakeAnalyticsTracker, NoOpLogger()),
            isDebugBuild = isDebugBuild,
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
    fun createFirstChecklist_typedNoChip_generatesViaAiAndOpensCreatedChecklistDetail() = runTest {
        // Branch 2: free text with no chip generates the checklist via AI IN PLACE (no Analyze
        // hand-off, no preview) and lands on the created checklist's detail screen.
        fakeAnalyzeRepository.resultName = "Birthday Party"
        val vm = createViewModel()
        vm.onIntent(WelcomeOnboardingIntent.OnInputChanged("Birthday party prep"))

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        // analyzeData was called with the typed prompt as RawText.
        assertEquals(1, fakeAnalyzeRepository.analyzeCallCount)
        val rawText = assertIs<AnalyzeInputData.RawText>(fakeAnalyzeRepository.lastInput)
        assertEquals("Birthday party prep", rawText.text)
        // createChecklistFromResult was called with the AI-suggested name.
        assertEquals(1, fakeAnalyzeRepository.createCallCount)
        assertEquals("Birthday Party", fakeAnalyzeRepository.lastCreateName)
        // Onboarding completed; landed on the CREATED checklist's detail with a cleared back stack.
        assertTrue(fakeUserDataRepository.onboardingMarkedPassed)
        assertTrue(fakeNavigator.navigatedToChecklistDetail)
        assertTrue(fakeNavigator.lastDetailClearBackStack)
        assertEquals(FakeAnalyzeRepository.CREATED_ID, fakeNavigator.lastDetailChecklistId)
        // It does NOT divert to the Analyze screen anymore.
        assertFalse(fakeNavigator.navigatedToAnalyzeScreen)
        // Activation funnel reproduced (the Analyze preview confirm used to do this): the created
        // checklist id was handed to the coordinator.
        assertEquals(FakeAnalyzeRepository.CREATED_ID, fakeActivationCoordinator.lastChecklistId)
        // The loader stays raised through navigation by design: clearBackStack tears the onboarding
        // screen/VM down, so the loader is replaced directly by the checklist detail (resetting it
        // here would flash the final step for one frame). Navigation happening — asserted above — is
        // the success signal.
    }

    @Test
    fun createFirstChecklist_typedNoChip_blankAiName_fallsBackToDefaultName() = runTest {
        // The server omitted a name → the VM falls back to the localized default (the fake resolver
        // echoes "resolved_text" for any key, including the default-name resource).
        fakeAnalyzeRepository.resultName = null
        val vm = createViewModel()
        vm.onIntent(WelcomeOnboardingIntent.OnInputChanged("Some topic"))

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("resolved_text", fakeAnalyzeRepository.lastCreateName)
        assertTrue(fakeNavigator.navigatedToChecklistDetail)
    }

    @Test
    fun createFirstChecklist_typedNoChip_analyzeError_snackbarsAndStaysOnStep() = runTest {
        fakeAnalyzeRepository.analyzeShouldFail = true
        val vm = createViewModel()
        vm.onIntent(WelcomeOnboardingIntent.OnInputChanged("Birthday party prep"))

        val effects = mutableListOf<WelcomeOnboardingSideEffect>()
        val collectJob = launch { vm.sideEffect.collect { effects.add(it) } }

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        // Generation failed → no checklist created, no navigation, loader cleared, snackbar shown.
        assertEquals(0, fakeAnalyzeRepository.createCallCount)
        assertFalse(fakeNavigator.navigatedToChecklistDetail)
        assertFalse(vm.screenState.value.isGeneratingAi)
        assertEquals(WelcomeOnboardingViewModel.ERROR_KEY, vm.screenState.value.error)
        assertTrue(
            effects.any {
                it is WelcomeOnboardingSideEffect.ShowSnackbar &&
                    it.messageKey == WelcomeOnboardingViewModel.ERROR_KEY
            },
        )
        collectJob.cancel()
    }

    @Test
    fun createFirstChecklist_typedNoChip_createError_snackbarsAndStaysOnStep() = runTest {
        // analyzeData succeeds but createChecklistFromResult fails → same error handling.
        fakeAnalyzeRepository.createShouldFail = true
        val vm = createViewModel()
        vm.onIntent(WelcomeOnboardingIntent.OnInputChanged("Birthday party prep"))

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeAnalyzeRepository.analyzeCallCount)
        assertEquals(1, fakeAnalyzeRepository.createCallCount)
        assertFalse(fakeNavigator.navigatedToChecklistDetail)
        assertFalse(vm.screenState.value.isGeneratingAi)
        assertEquals(WelcomeOnboardingViewModel.ERROR_KEY, vm.screenState.value.error)
        // Activation funnel must NOT fire when creation failed.
        assertNull(fakeActivationCoordinator.lastChecklistId)
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

    // --- Final step, branch 3 in the v2 shell: tips go into the Inbox ---

    @Test
    fun createFirstChecklist_v2_emptyInput_appendsTipsToInboxAndOpensMain() = runTest {
        val vm = createViewModel(NavVariant.V2)

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        assertInboxGotTipsAndLandedOnMain()
    }

    /**
     * The activation funnel reads `onboarding_completed.checklist_created` as "the user left
     * onboarding with starter content". The v2 Inbox tips are that content just as v1's "My
     * Checklist" is, so both report `true`; only `seed` tells the two shells apart.
     */
    @Test
    fun createFirstChecklist_v2_emptyInput_completedEventCountsStarterContent() = runTest {
        val vm = createViewModel(NavVariant.V2, isDebugBuild = false)

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        val params = completedEventParams()
        assertEquals("true", params["checklist_created"])
        assertEquals("inbox", params["seed"])
    }

    @Test
    fun createFirstChecklist_v1_emptyInput_completedEventKeepsDefaultSeed() = runTest {
        val vm = createViewModel(NavVariant.CONTROL, isDebugBuild = false)

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        val params = completedEventParams()
        assertEquals("true", params["checklist_created"])
        assertEquals("default", params["seed"])
    }

    private fun completedEventParams(): Map<String, Any> {
        val completed = fakeAnalyticsTracker.events.filter { it.first == AnalyticsEvents.Onboarding.COMPLETED }
        assertEquals(1, completed.size, "expected exactly one onboarding_completed, got $completed")
        return completed.single().second
    }

    @Test
    fun createFirstChecklist_v2_whitespaceOnly_treatedAsEmptyAppendsTipsToInbox() = runTest {
        val vm = createViewModel(NavVariant.V2)
        vm.onIntent(WelcomeOnboardingIntent.OnInputChanged("   "))

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        assertInboxGotTipsAndLandedOnMain()
        // Whitespace must not divert to the AI branch either.
        assertEquals(0, fakeAnalyzeRepository.analyzeCallCount)
    }

    @Test
    fun createFirstChecklist_v2_appendsAfterExistingInboxTasks() = runTest {
        // An Inbox that already holds a task (e.g. synced from another device) keeps it: the tips
        // are appended, never written over the existing items.
        val existingTemplate = ChecklistItem(text = "Existing")
        fakeChecklistRepository.inboxChecklist = Checklist(
            id = FakeChecklistRepository.INBOX_ID,
            name = "Inbox",
            items = listOf(existingTemplate),
            isInbox = true,
        )
        fakeChecklistRepository.inboxFill = ChecklistFill(
            id = FakeChecklistRepository.INBOX_FILL_ID,
            checklistId = FakeChecklistRepository.INBOX_ID,
            name = "Inbox",
            items = listOf(
                ChecklistFillItem(text = "Existing", checked = false, templateItemId = existingTemplate.id),
            ),
            isDefault = true,
        )
        val vm = createViewModel(NavVariant.V2)

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        val tips = WelcomeStarterTemplate.DEFAULT_STARTER_ITEMS.size
        val template = assertNotNull(fakeChecklistRepository.inboxChecklist)
        val fill = assertNotNull(fakeChecklistRepository.inboxFill)
        assertEquals(1 + tips, template.items.size)
        assertEquals(1 + tips, fill.items.size)
        assertEquals("Existing", template.items.first().text)
        assertEquals("Existing", fill.items.first().text)
    }

    @Test
    fun createFirstChecklist_v2_ensureInboxFails_snackbarsAndStaysOnStep() = runTest {
        // EnsureInboxUseCase turns the repository failure into null; the VM must surface it.
        fakeChecklistRepository.ensureInboxThrows = true
        val vm = createViewModel(NavVariant.V2)
        val effects = mutableListOf<WelcomeOnboardingSideEffect>()
        val collectJob = launch { vm.sideEffect.collect { effects.add(it) } }

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTipsFailureKeptUserOnStep(vm, effects)
        collectJob.cancel()
    }

    @Test
    fun createFirstChecklist_v2_inboxHasNoDefaultFill_snackbarsAndStaysOnStep() = runTest {
        fakeChecklistRepository.inboxCreatesFill = false
        val vm = createViewModel(NavVariant.V2)
        val effects = mutableListOf<WelcomeOnboardingSideEffect>()
        val collectJob = launch { vm.sideEffect.collect { effects.add(it) } }

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTipsFailureKeptUserOnStep(vm, effects)
        // Neither half was written — no template-only orphan.
        assertTrue(assertNotNull(fakeChecklistRepository.inboxChecklist).items.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun createFirstChecklist_v2_completeFailsAfterWrites_retryDoesNotDuplicateTips() = runTest {
        // The tips land, then marking onboarding passed fails → snackbar, user taps the CTA again.
        // The retry must finish the flow without writing the tips a second time.
        fakeUserDataRepository.throwOnNextUpdate = true
        val vm = createViewModel(NavVariant.V2)
        val effects = mutableListOf<WelcomeOnboardingSideEffect>()
        val collectJob = launch { vm.sideEffect.collect { effects.add(it) } }

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        // First tap: failure surfaced, user still on the step.
        assertFalse(vm.screenState.value.isCreating)
        assertEquals(WelcomeOnboardingViewModel.ERROR_KEY, vm.screenState.value.error)
        assertTrue(effects.any { it is WelcomeOnboardingSideEffect.ShowSnackbar })
        assertFalse(fakeNavigator.navigatedToMainScreen)

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        // Second tap: exactly one copy of the tips, and the flow completed.
        assertInboxGotTipsAndLandedOnMain()
        collectJob.cancel()
    }

    @Test
    fun createFirstChecklist_v2_chipSelected_stillCreatesSeparateChecklist() = runTest {
        // Regression guard: only the empty-input branch moved to the Inbox. A chip is a themed list
        // and stays its own project with its own detail screen.
        val vm = createViewModel(NavVariant.V2)
        vm.onIntent(WelcomeOnboardingIntent.OnTemplateSelected(WelcomeStarterTemplate.TRIP.key))

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeChecklistRepository.addChecklistCallCount)
        val saved = assertNotNull(fakeChecklistRepository.lastAddedChecklist)
        assertEquals(WelcomeStarterTemplate.TRIP.itemRes.size, saved.items.size)
        assertTrue(fakeNavigator.navigatedToChecklistDetail)
        assertFalse(fakeNavigator.navigatedToMainScreen)
        assertEquals(0, fakeChecklistRepository.ensureInboxCallCount)
    }

    @Test
    fun createFirstChecklist_control_emptyInput_neverTouchesInbox() = runTest {
        // v1 has no Inbox screen: writing tips there would strand them where the shell never looks.
        val vm = createViewModel(NavVariant.CONTROL)

        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fakeChecklistRepository.ensureInboxCallCount)
        assertEquals(1, fakeChecklistRepository.addChecklistCallCount)
        assertTrue(fakeNavigator.navigatedToChecklistDetail)
    }

    private fun assertInboxGotTipsAndLandedOnMain() {
        val tips = WelcomeStarterTemplate.DEFAULT_STARTER_ITEMS.size
        // No separate checklist.
        assertEquals(0, fakeChecklistRepository.addChecklistCallCount)
        // Both halves of the pair were written, and every fill row links to its template item.
        val template = assertNotNull(fakeChecklistRepository.inboxChecklist)
        val fill = assertNotNull(fakeChecklistRepository.inboxFill)
        assertEquals(tips, template.items.size)
        assertEquals(tips, fill.items.size)
        assertTrue(template.items.all { it.text.isNotBlank() })
        assertEquals(template.items.map { it.id }, fill.items.map { it.templateItemId })
        assertEquals(template.items.map { it.text }, fill.items.map { it.text })
        assertTrue(fill.items.none { it.checked })
        assertTrue(fill.items.all { it.reminderAt == null && it.repeatRule == null })
        // Landed on main (= Inbox tab in v2), not on a detail screen; onboarding is done.
        assertTrue(fakeNavigator.navigatedToMainScreen)
        assertTrue(fakeNavigator.lastMainClearBackStack)
        assertFalse(fakeNavigator.navigatedToChecklistDetail)
        assertTrue(fakeUserDataRepository.onboardingMarkedPassed)
    }

    private fun assertTipsFailureKeptUserOnStep(
        vm: WelcomeOnboardingViewModel,
        effects: List<WelcomeOnboardingSideEffect>,
    ) {
        val state = vm.screenState.value
        assertFalse(state.isCreating)
        assertEquals(WelcomeOnboardingViewModel.ERROR_KEY, state.error)
        assertTrue(
            effects.any {
                it is WelcomeOnboardingSideEffect.ShowSnackbar &&
                    it.messageKey == WelcomeOnboardingViewModel.ERROR_KEY
            },
        )
        assertFalse(fakeNavigator.navigatedToMainScreen)
        assertFalse(fakeNavigator.navigatedToChecklistDetail)
        assertFalse(fakeUserDataRepository.onboardingMarkedPassed)
        assertEquals(0, fakeChecklistRepository.addChecklistCallCount)
    }

    // --- More ways to start (multimodal → Analyze hub) ---

    @Test
    fun onMoreWaysToStart_completesOnboardingAndOpensAnalyzeHubWithDefaults() = runTest {
        val vm = createViewModel()
        // Even with text typed, the card opens the Analyze hub (no prompt forwarded) — it is a
        // separate entry from the typed/chip CTA branches.
        vm.onIntent(WelcomeOnboardingIntent.OnInputChanged("ignored by the card"))

        vm.onIntent(WelcomeOnboardingIntent.OnMoreWaysToStart)
        testDispatcher.scheduler.advanceUntilIdle()

        // Onboarding completed BEFORE the hand-off (user can't return to onboarding from Analyze).
        assertTrue(fakeUserDataRepository.onboardingMarkedPassed)
        // Opened the Analyze hub with ALL defaults: no prompt, no auto-analyze, default-fill off —
        // the hub lets the user pick the input type (Photo/PDF/voice/link) themselves.
        assertTrue(fakeNavigator.navigatedToAnalyzeScreen)
        assertNull(fakeNavigator.lastAnalyzeInitialText)
        assertFalse(fakeNavigator.lastAnalyzeAutoAnalyze)
        assertFalse(fakeNavigator.lastAnalyzeFillDefault)
        // The card never creates a checklist itself and never routes to a detail/main screen.
        assertEquals(0, fakeChecklistRepository.addChecklistCallCount)
        assertFalse(fakeNavigator.navigatedToChecklistDetail)
        assertFalse(fakeNavigator.navigatedToMainScreen)
    }

    @Test
    fun onMoreWaysToStart_whileCreating_isNoOp() = runTest {
        // The in-flight guard mirrors handleCreateFirstChecklist: a second trigger while a create is
        // already running must not double-complete onboarding or fire a second navigation.
        fakeChecklistRepository.blockAddUntilReleased = true
        val vm = createViewModel()
        // Start a create to flip isCreating=true (the fake repo suspends inside addChecklist).
        vm.onIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.screenState.value.isCreating)

        vm.onIntent(WelcomeOnboardingIntent.OnMoreWaysToStart)
        testDispatcher.scheduler.advanceUntilIdle()

        // Guard held: the card path did not run while a create was in flight.
        assertFalse(fakeNavigator.navigatedToAnalyzeScreen)

        // Let the parked create finish so no coroutine is left suspended at test teardown.
        fakeChecklistRepository.releaseAddGate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
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

        // When set, addChecklist suspends until [releaseAddGate] completes — lets a test hold the VM
        // in its isCreating=true window to exercise the in-flight guard.
        var blockAddUntilReleased = false
        val releaseAddGate = CompletableDeferred<Unit>()

        // --- System Inbox (v2 branch 3) ---
        // Mirrors the real ensureInbox: creates the flagged row + its empty default fill once.
        var inboxChecklist: Checklist? = null
        var inboxFill: ChecklistFill? = null
        var ensureInboxThrows = false
        var inboxCreatesFill = true
        var ensureInboxCallCount = 0

        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())

        override fun observeInbox(): Flow<Checklist?> = flowOf(inboxChecklist)

        override suspend fun ensureInbox(name: String): Long {
            ensureInboxCallCount++
            if (ensureInboxThrows) throw RuntimeException("Room write error")
            if (inboxChecklist == null) {
                inboxChecklist = Checklist(id = INBOX_ID, name = name, items = emptyList(), isInbox = true)
                if (inboxCreatesFill) {
                    inboxFill = ChecklistFill(
                        id = INBOX_FILL_ID,
                        checklistId = INBOX_ID,
                        name = name,
                        items = emptyList(),
                        isDefault = true,
                    )
                }
            }
            return INBOX_ID
        }

        override suspend fun addChecklist(checklist: Checklist): Long {
            addChecklistCallCount++
            if (shouldThrowOnAdd) throw RuntimeException("Room write error")
            if (blockAddUntilReleased) releaseAddGate.await()
            lastAddedChecklist = checklist
            savedChecklistId = 1L
            return 1L
        }

        override suspend fun updateChecklist(checklist: Checklist) {}
        override suspend fun updateChecklistTemplate(checklist: Checklist) {
            if (checklist.id == INBOX_ID) inboxChecklist = checklist
        }
        override suspend fun deleteChecklist(checklist: Checklist) {}

        override suspend fun getChecklistById(id: Long): Checklist? = when (id) {
            INBOX_ID -> inboxChecklist
            savedChecklistId -> lastAddedChecklist?.copy(id = id)
            else -> null
        }
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
        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> =
            flowOf(if (checklistId == INBOX_ID) inboxFill else null)
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override suspend fun getFillById(id: Long): ChecklistFill? = null
        override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun addFill(fill: ChecklistFill): Long = 1L
        override suspend fun updateFill(fill: ChecklistFill) {
            if (fill.checklistId == INBOX_ID) inboxFill = fill
        }
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

        companion object {
            const val INBOX_ID = 900L
            const val INBOX_FILL_ID = 901L
        }
    }

    /** Fixed shell for the test — `ensureResolved()` is the only member the VM reads. */
    private class FixedNavResolver(private val variant: NavVariant) : NavExperimentResolver {
        override fun currentArm(): NavVariant = variant
        override suspend fun ensureResolved(): NavVariant = variant
        override suspend fun setVariant(variant: NavVariant) {}
        override suspend fun clearVariant() {}
        override fun isArmAssigned(): Boolean = true
    }

    private class FakeAppNavigator : AppNavigator {
        var navigatedToMainScreen = false
        var lastMainClearBackStack = false
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
            lastMainClearBackStack = clearBackStack
        }
        override fun navigateToDebugMenu() {}
        override fun navigateToStoreScreenshot() {}
        override fun navigateToCreateChecklistScreen(templateId: Int?, initialText: String?) {}
        override fun navigateToEditChecklist(checklistId: Long) {}
        override fun navigateToTemplatesScreen() {}
        override fun navigateToTemplatePreview(templateId: String) {}
        override fun navigateToAnalyzeWithInput(
            inputKind: AnalyzeInputKind,
            entrySource: AiEntrySource,
        ) = Unit

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

        // When set, the next update() throws once (a failed DataStore write), then behaves normally.
        var throwOnNextUpdate = false

        override fun getUserDataFlow(): StateFlow<UserData> = dataFlow
        override suspend fun getUserData(): UserData = dataFlow.value

        override suspend fun update(userData: UserData) {
            if (throwOnNextUpdate) {
                throwOnNextUpdate = false
                throw RuntimeException("DataStore write error")
            }
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
        val events = mutableListOf<Pair<String, Map<String, Any>>>()

        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {
            events.add(name to params)
        }
    }

    /**
     * Drives the typed-text AI branch: [analyzeData] returns an empty-item [AnalyzeResult] (the count
     * isn't asserted — only the name flow + side effects matter), [createChecklistFromResult] returns
     * a checklist with [CREATED_ID]. Either step can be made to fail to exercise the error path.
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

    /** Records the checklist id handed to the activation funnel (or null if never called). */
    private class RecordingActivationCoordinator : ActivationCoordinator {
        var lastChecklistId: Long? = null
        var lastUserChecklistId: Long? = null

        override val reminderOptInRequests: SharedFlow<Long> = MutableSharedFlow()

        override suspend fun onAiChecklistCreated(checklistId: Long) {
            lastChecklistId = checklistId
        }

        override suspend fun onUserChecklistCreated(checklistId: Long) {
            lastUserChecklistId = checklistId
        }
        override suspend fun reportReminderOptInOutcome(granted: Boolean) {}
    }

    private class FakeRemoteConfigProvider : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getString(key: String, defaultValue: String): String = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
    }
}
