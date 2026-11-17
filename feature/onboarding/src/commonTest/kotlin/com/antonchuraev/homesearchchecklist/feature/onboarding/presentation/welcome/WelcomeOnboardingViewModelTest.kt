package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import androidx.lifecycle.SavedStateHandle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AiEntrySource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.ChecklistSource
import com.antonchuraev.homesearchchecklist.core.common.api.NavExperimentResolver
import com.antonchuraev.homesearchchecklist.core.common.api.NavVariant
import com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.navigation.api.ShellLaunch
import com.antonchuraev.homesearchchecklist.core.navigation.api.ShellLaunchRequests
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscriptionOutcome
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiAnalyzeError
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
 * The refreshed `ai_welcome` onboarding (Showcase → Try it): FEATURE_SPEC §11.1 cases 1–21 plus the
 * Inbox-tips guarantees carried over from the old suite (append order, template/fill pairing, no
 * duplicate on retry, v1 untouched).
 *
 * Strings resolve through a fake [WelcomeStringResolver] (Compose Resources does not resolve on the
 * JVM host); exact localized text is verified on device. Analytics are recorded (isDebugBuild=false).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeOnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    /** Shared call log: completion and navigation write here so their ORDER can be asserted (I7). */
    private lateinit var calls: MutableList<String>
    private lateinit var fakeNavigator: FakeAppNavigator
    private lateinit var fakeChecklistRepository: FakeChecklistRepository
    private lateinit var fakeAnalyzeRepository: FakeAnalyzeRepository
    private lateinit var fakeActivationCoordinator: RecordingActivationCoordinator
    private lateinit var fakeAnalyticsTracker: RecordingAnalyticsTracker
    private lateinit var fakeUserDataRepository: FakeUserDataRepository
    private lateinit var fakeShellLaunch: FakeShellLaunchRequests
    private lateinit var fakeTranscriber: FakeVoiceTranscriber

    private val fakeStringResolver = WelcomeStringResolver { "resolved_text" }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        resetFakes()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun resetFakes() {
        calls = mutableListOf()
        fakeNavigator = FakeAppNavigator(calls)
        fakeChecklistRepository = FakeChecklistRepository()
        fakeAnalyzeRepository = FakeAnalyzeRepository()
        fakeActivationCoordinator = RecordingActivationCoordinator()
        fakeAnalyticsTracker = RecordingAnalyticsTracker()
        fakeUserDataRepository = FakeUserDataRepository(calls)
        fakeShellLaunch = FakeShellLaunchRequests()
        fakeTranscriber = FakeVoiceTranscriber()
    }

    private fun createViewModel(
        variant: NavVariant = NavVariant.V2,
        isCallAvailable: Boolean = true,
        isWeb: Boolean = false,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): WelcomeOnboardingViewModel = WelcomeOnboardingViewModel(
        savedStateHandle = savedStateHandle,
        navigator = fakeNavigator,
        completeOnboardingUseCase = CompleteOnboardingUseCase(fakeUserDataRepository),
        checklistRepository = fakeChecklistRepository,
        analyzeRepository = fakeAnalyzeRepository,
        activationCoordinator = fakeActivationCoordinator,
        analyticsTracker = fakeAnalyticsTracker,
        logger = NoOpLogger(),
        navExperimentResolver = FixedNavResolver(variant),
        // Real use case over the fake repository: the true ensure path, incl. null-on-failure.
        ensureInbox = EnsureInboxUseCase(fakeChecklistRepository, fakeAnalyticsTracker, NoOpLogger()),
        shellLaunchRequests = fakeShellLaunch,
        userDataRepository = fakeUserDataRepository,
        voiceTranscriber = fakeTranscriber,
        isCallAvailable = isCallAvailable,
        isDebugBuild = false,
        isWeb = isWeb,
        stringResolver = fakeStringResolver,
    )

    /**
     * Showcase → [mode]'s screen through its tile. Was `toBranch()` over the Showcase CTA, which the
     * owner removed (2026-11-17); the ViewModel's actions do not depend on the screen they come from,
     * so each test opens the screen that hosts its action in the new layout.
     */
    private fun WelcomeOnboardingViewModel.toBranch(mode: OnboardingMode = OnboardingMode.Write) {
        onIntent(OnboardingIntent.OnShowcaseTileClick(mode))
    }

    private fun TestScope.collectEffects(vm: WelcomeOnboardingViewModel): MutableList<OnboardingSideEffect> {
        val effects = mutableListOf<OnboardingSideEffect>()
        // Unconfined: subscribes right here and receives each emit in place. A collector on the
        // standard test dispatcher in backgroundScope is not advanced by advanceUntilIdle.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.sideEffect.collect { effects.add(it) } }
        return effects
    }

    private fun events(name: String) = fakeAnalyticsTracker.events.filter { it.first == name }.map { it.second }

    private fun completedParams(): Map<String, Any> {
        val completed = events(AnalyticsEvents.Onboarding.COMPLETED)
        assertEquals(1, completed.size, "expected exactly one onboarding_completed, got $completed")
        return completed.single()
    }

    private fun assertCompletedBeforeNavigation() {
        val complete = calls.indexOf(CALL_COMPLETE)
        val nav = calls.indexOfFirst { it.startsWith("nav:") }
        assertTrue(complete >= 0, "completeOnboarding never ran: $calls")
        assertTrue(nav >= 0, "no navigation: $calls")
        assertTrue(complete < nav, "completeOnboarding must run BEFORE navigation: $calls")
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun initialState_isShowcase_andCarriesCallAvailability() = runTest(testDispatcher) {
        val vm = createViewModel(isCallAvailable = false)

        val state = vm.screenState.value
        assertEquals(OnboardingStep.Showcase, state.step)
        assertEquals(OnboardingPhase.Idle, state.phase)
        assertFalse(state.isCallAvailable)
        val viewed = events(AnalyticsEvents.Onboarding.STEP_VIEWED).single()
        assertEquals("showcase", viewed[AnalyticsParams.STEP])
        assertEquals(1, viewed[AnalyticsParams.STEP_INDEX])
        assertEquals(2, viewed[AnalyticsParams.STEP_COUNT])
    }

    // ── 1–4. Sample ────────────────────────────────────────────────────────────

    @Test
    fun sample_createsExactlyOneChecklist_withLocalizedItems_noAiCall() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toBranch(OnboardingMode.Attach)

        vm.onIntent(OnboardingIntent.OnSampleClick(SampleOrigin.Card))
        advanceUntilIdle()

        assertEquals(1, fakeChecklistRepository.addChecklistCallCount)
        val saved = assertNotNull(fakeChecklistRepository.lastAddedChecklist)
        assertEquals("resolved_text", saved.name)
        assertEquals(6, saved.items.size)
        assertTrue(saved.items.all { it.text.isNotBlank() && !it.checked })
        assertTrue(saved.separateCompleted, "new lists set separateCompleted explicitly")
        // No dates, no reminders (owner decision).
        assertNull(saved.reminderAt)
        assertNull(saved.repeatRule)
        // No network, no credits.
        assertEquals(0, fakeAnalyzeRepository.analyzeCallCount)
        // A rehearsal, not the user's own content (§10 Q4): no reminder opt-in soft-ask.
        assertNull(fakeActivationCoordinator.lastUserChecklistId)
        assertEquals(SampleStage.Ready, (vm.screenState.value.phase as OnboardingPhase.SampleRunning).stage)
        val created = events(AnalyticsEvents.Checklist.CREATED).single()
        assertEquals(ChecklistSource.ONBOARDING_SAMPLE.wire, created[AnalyticsParams.SOURCE])
        assertEquals("card", events(AnalyticsEvents.Onboarding.SAMPLE_USED).single()[AnalyticsParams.ORIGIN])
    }

    @Test
    fun sample_doubleTap_insertsOnce() = runTest(testDispatcher) {
        fakeChecklistRepository.blockAddUntilReleased = true
        val vm = createViewModel()
        vm.toBranch(OnboardingMode.Attach)

        vm.onIntent(OnboardingIntent.OnSampleClick(SampleOrigin.Card))
        runCurrent()
        vm.onIntent(OnboardingIntent.OnSampleClick(SampleOrigin.Card))
        runCurrent()
        fakeChecklistRepository.releaseAddGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, fakeChecklistRepository.addChecklistCallCount)
        assertEquals(1, events(AnalyticsEvents.Onboarding.SAMPLE_USED).size)
    }

    @Test
    fun sample_navigatesOnlyAfterWriteAndAnimation_writeFirst() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toBranch(OnboardingMode.Attach)
        vm.onIntent(OnboardingIntent.OnSampleClick(SampleOrigin.Card))
        advanceUntilIdle()

        assertFalse(fakeNavigator.navigatedToChecklistDetail, "write done, animation not finished: stay")

        vm.onIntent(OnboardingIntent.OnSampleAnimationFinished)
        advanceUntilIdle()

        assertTrue(fakeNavigator.navigatedToChecklistDetail)
        assertTrue(fakeNavigator.lastDetailClearBackStack)
        assertEquals(1L, fakeNavigator.lastDetailChecklistId)
        assertEquals("sample", completedParams()[AnalyticsParams.SEED])
        assertEquals("true", completedParams()[AnalyticsParams.CHECKLIST_CREATED])
    }

    @Test
    fun sample_navigatesOnlyAfterWriteAndAnimation_animationFirst() = runTest(testDispatcher) {
        fakeChecklistRepository.blockAddUntilReleased = true
        val vm = createViewModel()
        vm.toBranch(OnboardingMode.Attach)
        vm.onIntent(OnboardingIntent.OnSampleClick(SampleOrigin.Card))
        runCurrent()

        vm.onIntent(OnboardingIntent.OnSampleAnimationFinished)
        runCurrent()
        assertFalse(fakeNavigator.navigatedToChecklistDetail, "animation done, write not finished: stay")

        fakeChecklistRepository.releaseAddGate.complete(Unit)
        advanceUntilIdle()

        assertTrue(fakeNavigator.navigatedToChecklistDetail)
        assertTrue(fakeNavigator.lastDetailClearBackStack)
    }

    @Test
    fun sample_writeTimeout_returnsToIdleWithSnackbar() = runTest(testDispatcher) {
        fakeChecklistRepository.blockAddUntilReleased = true
        val vm = createViewModel()
        val effects = collectEffects(vm)
        vm.toBranch(OnboardingMode.Attach)

        vm.onIntent(OnboardingIntent.OnSampleClick(SampleOrigin.Card))
        advanceUntilIdle() // the 5 s timeout elapses in virtual time

        assertEquals(OnboardingPhase.Idle, vm.screenState.value.phase)
        assertTrue(effects.any { it == OnboardingSideEffect.ShowSnackbar(WelcomeOnboardingViewModel.ERROR_KEY) })
        assertFalse(fakeNavigator.navigatedToChecklistDetail)
        assertFalse(fakeUserDataRepository.onboardingMarkedPassed)
        fakeChecklistRepository.releaseAddGate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun sample_writeFails_returnsToIdleWithSnackbar() = runTest(testDispatcher) {
        fakeChecklistRepository.shouldThrowOnAdd = true
        val vm = createViewModel()
        val effects = collectEffects(vm)
        vm.toBranch(OnboardingMode.Attach)

        vm.onIntent(OnboardingIntent.OnSampleClick(SampleOrigin.Card))
        advanceUntilIdle()

        assertEquals(OnboardingPhase.Idle, vm.screenState.value.phase)
        assertTrue(effects.any { it is OnboardingSideEffect.ShowSnackbar })
        assertTrue(events(AnalyticsEvents.Checklist.CREATED).isEmpty())
    }

    // ── 5. completeOnboarding before navigation, every branch ──────────────────

    @Test
    fun completeBeforeNavigation_everyBranch() = runTest(testDispatcher) {
        val branches: List<Pair<String, (WelcomeOnboardingViewModel) -> Unit>> = listOf(
            "sample" to { vm ->
                vm.toBranch(OnboardingMode.Attach)
                vm.onIntent(OnboardingIntent.OnSampleClick(SampleOrigin.Card))
                vm.onIntent(OnboardingIntent.OnSampleAnimationFinished)
            },
            "chip" to { vm -> vm.toBranch(); vm.onIntent(OnboardingIntent.OnStarterChipClick(WelcomeStarterTemplate.TRIP)) },
            "typed" to { vm ->
                vm.toBranch()
                vm.onIntent(OnboardingIntent.OnInputChanged("Birthday party"))
                vm.onIntent(OnboardingIntent.OnSendClick)
            },
            "plain" to { vm ->
                fakeAnalyzeRepository.analyzeShouldFail = true
                vm.toBranch()
                vm.onIntent(OnboardingIntent.OnInputChanged("Birthday party"))
                vm.onIntent(OnboardingIntent.OnSendClick)
                testDispatcher.scheduler.advanceUntilIdle()
                vm.onIntent(OnboardingIntent.OnSavePlainListClick)
            },
            "own_file" to { vm -> vm.toBranch(OnboardingMode.Attach); vm.onIntent(OnboardingIntent.OnOwnSourceClick(AnalyzeInputKind.PDF)) },
            "chat" to { vm -> vm.toBranch(OnboardingMode.Talk); vm.onIntent(OnboardingIntent.OnChatClick) },
            "call" to { vm -> vm.toBranch(OnboardingMode.Talk); vm.onIntent(OnboardingIntent.OnCallClick) },
            "starter" to { vm ->
                vm.toBranch(OnboardingMode.Talk)
                vm.onIntent(OnboardingIntent.OnTalkStarterClick(OnboardingTalkStarter.PlanWeek))
            },
            "showcase_example" to { vm ->
                vm.onIntent(OnboardingIntent.OnSampleClick(SampleOrigin.Showcase))
                vm.onIntent(OnboardingIntent.OnSampleAnimationFinished)
            },
            "skip" to { vm -> vm.onIntent(OnboardingIntent.OnSkip) },
        )
        branches.forEach { (name, act) ->
            resetFakes()
            val vm = createViewModel()
            act(vm)
            advanceUntilIdle()
            if (name == "sample" || name == "showcase_example") {
                // The animation-finished intent came before the write in this branch; send it again
                // once the write is done (order-independence is covered by its own test).
                vm.onIntent(OnboardingIntent.OnSampleAnimationFinished)
                advanceUntilIdle()
            }
            try {
                assertCompletedBeforeNavigation()
            } catch (e: AssertionError) {
                throw AssertionError("branch $name: ${e.message}", e)
            }
        }
    }

    // ── 6. onboarding_completed at most once ───────────────────────────────────

    @Test
    fun completed_sentOnce_whenCompletionFailsAndUserRetries() = runTest(testDispatcher) {
        fakeUserDataRepository.throwOnNextUpdate = true
        val vm = createViewModel()
        val effects = collectEffects(vm)
        vm.toBranch()

        vm.onIntent(OnboardingIntent.OnStarterChipClick(WelcomeStarterTemplate.TRIP))
        advanceUntilIdle()

        assertEquals(OnboardingPhase.Idle, vm.screenState.value.phase, "a failed completion returns to Idle (I2)")
        assertTrue(effects.any { it is OnboardingSideEffect.ShowSnackbar })
        assertFalse(fakeNavigator.navigatedToChecklistDetail)

        vm.onIntent(OnboardingIntent.OnStarterChipClick(WelcomeStarterTemplate.TRIP))
        advanceUntilIdle()

        assertTrue(fakeNavigator.navigatedToChecklistDetail)
        assertEquals(1, events(AnalyticsEvents.Onboarding.COMPLETED).size)
        // I6: the retry reused the list.
        assertEquals(1, fakeChecklistRepository.addChecklistCallCount)
    }

    // ── 7–8. Skip ──────────────────────────────────────────────────────────────

    @Test
    fun skip_sendsSeedSkip_fromShowcase() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.onIntent(OnboardingIntent.OnSkip)
        advanceUntilIdle()

        val skipped = events(AnalyticsEvents.Onboarding.SKIPPED).single()
        assertEquals("showcase", skipped[AnalyticsParams.STEP])
        assertEquals(1, skipped[AnalyticsParams.STEP_INDEX])
        assertEquals("skip", completedParams()[AnalyticsParams.SEED])
        assertEquals(1, completedParams()[AnalyticsParams.STEP_INDEX])
        assertTrue(fakeNavigator.navigatedToMainScreen)
        assertTrue(fakeNavigator.lastMainClearBackStack)
    }

    @Test
    fun skip_sendsSeedSkip_fromBranch() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toBranch()

        vm.onIntent(OnboardingIntent.OnSkip)
        advanceUntilIdle()

        assertEquals(2, events(AnalyticsEvents.Onboarding.SKIPPED).single()[AnalyticsParams.STEP_INDEX])
        assertEquals("skip", completedParams()[AnalyticsParams.SEED])
        assertEquals(2, completedParams()[AnalyticsParams.STEP_INDEX])
    }

    @Test
    fun skip_v2_appendsTipsToInbox_andCountsThemAsCreated() = runTest(testDispatcher) {
        val vm = createViewModel(NavVariant.V2)

        vm.onIntent(OnboardingIntent.OnSkip)
        advanceUntilIdle()

        assertInboxGotTipsAndLandedOnMain()
        assertEquals("true", completedParams()[AnalyticsParams.CHECKLIST_CREATED])
    }

    @Test
    fun skip_v2_appendsAfterExistingInboxTasks() = runTest(testDispatcher) {
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
            items = listOf(ChecklistFillItem(text = "Existing", checked = false, templateItemId = existingTemplate.id)),
            isDefault = true,
        )
        val vm = createViewModel(NavVariant.V2)

        vm.onIntent(OnboardingIntent.OnSkip)
        advanceUntilIdle()

        val tips = WelcomeStarterTemplate.DEFAULT_STARTER_ITEMS.size
        val template = assertNotNull(fakeChecklistRepository.inboxChecklist)
        val fill = assertNotNull(fakeChecklistRepository.inboxFill)
        assertEquals(1 + tips, template.items.size)
        assertEquals(1 + tips, fill.items.size)
        assertEquals("Existing", template.items.first().text)
        assertEquals("Existing", fill.items.first().text)
    }

    @Test
    fun skip_tipsWriteFails_stillCompletesAndNavigates() = runTest(testDispatcher) {
        fakeChecklistRepository.ensureInboxThrows = true
        val vm = createViewModel(NavVariant.V2)

        vm.onIntent(OnboardingIntent.OnSkip)
        advanceUntilIdle()

        assertTrue(fakeUserDataRepository.onboardingMarkedPassed)
        assertTrue(fakeNavigator.navigatedToMainScreen)
        assertEquals("false", completedParams()[AnalyticsParams.CHECKLIST_CREATED])
    }

    @Test
    fun skip_inboxHasNoDefaultFill_noTemplateOrphan_stillLeaves() = runTest(testDispatcher) {
        fakeChecklistRepository.inboxCreatesFill = false
        val vm = createViewModel(NavVariant.V2)

        vm.onIntent(OnboardingIntent.OnSkip)
        advanceUntilIdle()

        assertTrue(assertNotNull(fakeChecklistRepository.inboxChecklist).items.isEmpty(), "no template-only orphan")
        assertTrue(fakeNavigator.navigatedToMainScreen)
    }

    @Test
    fun skip_completeFailsAfterTips_retryDoesNotDuplicateTips() = runTest(testDispatcher) {
        fakeUserDataRepository.throwOnNextUpdate = true
        val vm = createViewModel(NavVariant.V2)
        val effects = collectEffects(vm)

        vm.onIntent(OnboardingIntent.OnSkip)
        advanceUntilIdle()
        assertTrue(effects.any { it is OnboardingSideEffect.ShowSnackbar })
        assertFalse(fakeNavigator.navigatedToMainScreen)

        vm.onIntent(OnboardingIntent.OnSkip)
        advanceUntilIdle()

        assertInboxGotTipsAndLandedOnMain()
    }

    @Test
    fun skip_v1_neverTouchesInbox() = runTest(testDispatcher) {
        val vm = createViewModel(NavVariant.CONTROL)

        vm.onIntent(OnboardingIntent.OnSkip)
        advanceUntilIdle()

        assertEquals(0, fakeChecklistRepository.ensureInboxCallCount)
        assertEquals(0, fakeChecklistRepository.addChecklistCallCount)
        assertTrue(fakeNavigator.navigatedToMainScreen)
        assertEquals("false", completedParams()[AnalyticsParams.CHECKLIST_CREATED])
    }

    // ── 9–12. Typed → AI ───────────────────────────────────────────────────────

    @Test
    fun typed_aiFailure_showsUnavailableBox_keepsText_noSnackbar() = runTest(testDispatcher) {
        fakeAnalyzeRepository.analyzeError = AiAnalyzeError.Network
        val vm = createViewModel()
        val effects = collectEffects(vm)
        vm.toBranch()
        vm.onIntent(OnboardingIntent.OnInputChanged("Birthday party prep"))

        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()

        val state = vm.screenState.value
        assertEquals(OnboardingPhase.Idle, state.phase)
        assertEquals(OnboardingAiError.Unavailable, state.aiError)
        assertEquals("Birthday party prep", state.inputText)
        assertTrue(effects.isEmpty(), "the box is the feedback, not a snackbar: $effects")
        assertFalse(fakeNavigator.navigatedToChecklistDetail)
        assertFalse(fakeUserDataRepository.onboardingMarkedPassed)
        val failed = events(AnalyticsEvents.Analyze.FAILED).single()
        assertEquals(AiEntrySource.ONBOARDING.wire, failed[AnalyticsParams.SOURCE])
        assertEquals("raw_text", failed[AnalyticsParams.INPUT_TYPE])
        assertEquals("network", failed[AnalyticsParams.FAILURE_REASON])
        val shown = events(AnalyticsEvents.Onboarding.AI_ERROR_SHOWN).single()
        assertEquals("unavailable", shown[AnalyticsParams.KIND])
    }

    @Test
    fun typed_aiFailure_webSignedOutCreditGate_showsSignInBox() = runTest(testDispatcher) {
        fakeAnalyzeRepository.analyzeError = AiAnalyzeError.Http(402, "Not enough credits")
        val vm = createViewModel(isWeb = true)
        vm.toBranch()
        vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))

        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()

        assertEquals(OnboardingAiError.SignInRequired, vm.screenState.value.aiError)
    }

    @Test
    fun typed_aiFailure_thenSample_createsSample() = runTest(testDispatcher) {
        fakeAnalyzeRepository.analyzeShouldFail = true
        val vm = createViewModel()
        vm.toBranch()
        vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))
        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()

        vm.onIntent(OnboardingIntent.OnSampleClick(SampleOrigin.AiError))
        advanceUntilIdle()
        vm.onIntent(OnboardingIntent.OnSampleAnimationFinished)
        advanceUntilIdle()

        assertEquals(6, assertNotNull(fakeChecklistRepository.lastAddedChecklist).items.size)
        assertTrue(fakeNavigator.navigatedToChecklistDetail)
        assertEquals("ai_error", events(AnalyticsEvents.Onboarding.SAMPLE_USED).single()[AnalyticsParams.ORIGIN])
        assertEquals("sample", events(AnalyticsEvents.Onboarding.FALLBACK_ACTION).single()[AnalyticsParams.ACTION])
    }

    @Test
    fun typed_aiFailure_thenPlainList_namesListFromText() = runTest(testDispatcher) {
        fakeAnalyzeRepository.analyzeShouldFail = true
        val vm = createViewModel()
        vm.toBranch()
        vm.onIntent(OnboardingIntent.OnInputChanged("  Birthday party prep  "))
        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()

        vm.onIntent(OnboardingIntent.OnSavePlainListClick)
        advanceUntilIdle()

        val saved = assertNotNull(fakeChecklistRepository.lastAddedChecklist)
        assertEquals("Birthday party prep", saved.name)
        assertEquals(WelcomeStarterTemplate.DEFAULT_STARTER_ITEMS.size, saved.items.size)
        assertTrue(saved.separateCompleted)
        assertNull(fakeActivationCoordinator.lastUserChecklistId)
        assertEquals(1, fakeAnalyzeRepository.analyzeCallCount, "the plain list never calls AI")
        assertTrue(fakeNavigator.navigatedToChecklistDetail)
        assertEquals("plain_list", completedParams()[AnalyticsParams.SEED])
        assertEquals(ChecklistSource.ONBOARDING_PLAIN.wire, events(AnalyticsEvents.Checklist.CREATED).single()[AnalyticsParams.SOURCE])
    }

    @Test
    fun plainList_createFails_keepsTheErrorBox() = runTest(testDispatcher) {
        fakeAnalyzeRepository.analyzeShouldFail = true
        val vm = createViewModel()
        val effects = collectEffects(vm)
        vm.toBranch()
        vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))
        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()
        fakeChecklistRepository.shouldThrowOnAdd = true

        vm.onIntent(OnboardingIntent.OnSavePlainListClick)
        advanceUntilIdle()

        assertEquals(OnboardingPhase.Idle, vm.screenState.value.phase)
        assertEquals(OnboardingAiError.Unavailable, vm.screenState.value.aiError, "the sample is still offered")
        assertTrue(effects.any { it is OnboardingSideEffect.ShowSnackbar })
    }

    @Test
    fun typed_success_emitsAnalyzeStartedCompleted_andChecklistCreatedOnboardingAi() = runTest(testDispatcher) {
        fakeAnalyzeRepository.resultName = "Birthday Party"
        val vm = createViewModel()
        vm.toBranch()
        vm.onIntent(OnboardingIntent.OnInputChanged("  Birthday party prep "))

        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()

        assertEquals("Birthday party prep", assertIs<AnalyzeInputData.RawText>(fakeAnalyzeRepository.lastInput).text)
        assertEquals("Birthday Party", fakeAnalyzeRepository.lastCreateName)
        val started = events(AnalyticsEvents.Analyze.STARTED).single()
        assertEquals(AiEntrySource.ONBOARDING.wire, started[AnalyticsParams.SOURCE])
        assertEquals("raw_text", started[AnalyticsParams.INPUT_TYPE])
        val completed = events(AnalyticsEvents.Analyze.COMPLETED).single()
        assertEquals(AiEntrySource.ONBOARDING.wire, completed[AnalyticsParams.SOURCE])
        assertEquals(0, completed[AnalyticsParams.ITEM_COUNT])
        assertEquals(ChecklistSource.ONBOARDING_AI.wire, events(AnalyticsEvents.Checklist.CREATED).single()[AnalyticsParams.SOURCE])
        assertEquals(FakeAnalyzeRepository.CREATED_ID, fakeActivationCoordinator.lastChecklistId)
        assertEquals(FakeAnalyzeRepository.CREATED_ID, fakeNavigator.lastDetailChecklistId)
        assertTrue(fakeNavigator.lastDetailClearBackStack)
        assertEquals("typed", completedParams()[AnalyticsParams.SEED])
        // The loader stays up through navigation (no flash of Try it).
        assertIs<OnboardingPhase.Leaving>(vm.screenState.value.phase)
    }

    @Test
    fun typed_blankAiName_fallsBackToDefaultName() = runTest(testDispatcher) {
        fakeAnalyzeRepository.resultName = null
        val vm = createViewModel()
        vm.toBranch()
        vm.onIntent(OnboardingIntent.OnInputChanged("Some topic"))

        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()

        assertEquals("resolved_text", fakeAnalyzeRepository.lastCreateName)
    }

    @Test
    fun typed_createFails_showsBox_noActivation() = runTest(testDispatcher) {
        fakeAnalyzeRepository.createShouldFail = true
        val vm = createViewModel()
        vm.toBranch()
        vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))

        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()

        assertEquals(OnboardingAiError.Unavailable, vm.screenState.value.aiError)
        assertNull(fakeActivationCoordinator.lastChecklistId)
        assertFalse(fakeNavigator.navigatedToChecklistDetail)
    }

    @Test
    fun typed_slowGeneration_flipsIsSlowAfter8s() = runTest(testDispatcher) {
        fakeAnalyzeRepository.blockAnalyzeUntilReleased = true
        val vm = createViewModel()
        vm.toBranch()
        vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))
        vm.onIntent(OnboardingIntent.OnSendClick)
        runCurrent()
        assertEquals(OnboardingPhase.AiGenerating("Trip", isSlow = false), vm.screenState.value.phase)

        advanceTimeBy(8_001)

        assertEquals(OnboardingPhase.AiGenerating("Trip", isSlow = true), vm.screenState.value.phase)
        fakeAnalyzeRepository.releaseAnalyzeGate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun typed_editAfterError_clearsBox() = runTest(testDispatcher) {
        fakeAnalyzeRepository.analyzeShouldFail = true
        val vm = createViewModel()
        vm.toBranch()
        vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))
        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()
        assertNotNull(vm.screenState.value.aiError)

        vm.onIntent(OnboardingIntent.OnInputChanged("Trip to Rome"))

        assertNull(vm.screenState.value.aiError)
    }

    @Test
    fun typed_analyzeEvents_carryOnboardingSurface() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toBranch()
        vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))

        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()

        // The in-place analysis is told apart from an Analyze screen opened from onboarding (same
        // source, no surface) — CSAT skips only this one.
        val surface = AnalyticsEvents.Analyze.SURFACE_ONBOARDING
        assertEquals(surface, events(AnalyticsEvents.Analyze.STARTED).single()[AnalyticsParams.SURFACE])
        assertEquals(surface, events(AnalyticsEvents.Analyze.COMPLETED).single()[AnalyticsParams.SURFACE])
    }

    @Test
    fun typed_retryWithSameText_reusesList_editedText_generatesAgain() = runTest(testDispatcher) {
        fakeUserDataRepository.throwOnNextUpdate = true // the list lands, completion fails
        val vm = createViewModel()
        val effects = collectEffects(vm)
        vm.toBranch()
        vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))
        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()
        assertEquals(OnboardingPhase.Idle, vm.screenState.value.phase)
        assertTrue(effects.any { it is OnboardingSideEffect.ShowSnackbar })
        assertFalse(fakeNavigator.navigatedToChecklistDetail)
        assertEquals(1, fakeAnalyzeRepository.analyzeCallCount)

        vm.onIntent(OnboardingIntent.OnInputChanged("Trip to Rome"))
        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()

        assertEquals(2, fakeAnalyzeRepository.analyzeCallCount, "an edited goal is a new request, not the old list")
        assertEquals("Trip to Rome", assertIs<AnalyzeInputData.RawText>(fakeAnalyzeRepository.lastInput).text)
        assertEquals(2, events(AnalyticsEvents.Checklist.CREATED).size)
        assertTrue(fakeNavigator.navigatedToChecklistDetail)
    }

    @Test
    fun typed_retryWithSameText_afterFailedCompletion_reusesList() = runTest(testDispatcher) {
        fakeUserDataRepository.throwOnNextUpdate = true
        val vm = createViewModel()
        vm.toBranch()
        vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))
        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()

        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()

        assertEquals(1, fakeAnalyzeRepository.analyzeCallCount, "I6: same text → no second AI call")
        assertEquals(1, events(AnalyticsEvents.Checklist.CREATED).size)
        assertEquals(FakeAnalyzeRepository.CREATED_ID, fakeNavigator.lastDetailChecklistId)
    }

    @Test
    fun typed_analyzeThrows_emitsFailed_andShowsBox() = runTest(testDispatcher) {
        fakeAnalyzeRepository.analyzeThrows = IllegalStateException("boom")
        val vm = createViewModel()
        vm.toBranch()
        vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))

        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()

        assertEquals(1, events(AnalyticsEvents.Analyze.STARTED).size)
        val failed = events(AnalyticsEvents.Analyze.FAILED).single()
        assertEquals(AiEntrySource.ONBOARDING.wire, failed[AnalyticsParams.SOURCE])
        assertEquals("boom", failed[AnalyticsParams.ERROR])
        assertTrue(events(AnalyticsEvents.Analyze.COMPLETED).isEmpty())
        assertEquals(OnboardingPhase.Idle, vm.screenState.value.phase)
        assertEquals(OnboardingAiError.Unavailable, vm.screenState.value.aiError)
        assertEquals(1, events(AnalyticsEvents.Onboarding.AI_ERROR_SHOWN).size)
    }

    @Test
    fun sampleFromAiErrorBox_writeFails_restoresTheBox() = runTest(testDispatcher) {
        fakeAnalyzeRepository.analyzeShouldFail = true
        val vm = createViewModel()
        val effects = collectEffects(vm)
        vm.toBranch()
        vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))
        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()
        assertEquals(OnboardingAiError.Unavailable, vm.screenState.value.aiError)
        fakeChecklistRepository.shouldThrowOnAdd = true

        vm.onIntent(OnboardingIntent.OnSampleClick(SampleOrigin.AiError))
        advanceUntilIdle()

        assertEquals(OnboardingPhase.Idle, vm.screenState.value.phase)
        assertEquals(OnboardingAiError.Unavailable, vm.screenState.value.aiError, "the plain list is still offered")
        assertTrue(effects.any { it is OnboardingSideEffect.ShowSnackbar })
        assertFalse(fakeNavigator.navigatedToChecklistDetail)
    }

    // ── 13. Empty send ─────────────────────────────────────────────────────────

    @Test
    fun emptySend_showsHint_noAiCall() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toBranch()
        vm.onIntent(OnboardingIntent.OnInputChanged("   "))

        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()

        assertTrue(vm.screenState.value.showEmptyInputHint)
        assertEquals(OnboardingPhase.Idle, vm.screenState.value.phase)
        assertEquals(0, fakeAnalyzeRepository.analyzeCallCount)
        assertEquals(0, fakeChecklistRepository.addChecklistCallCount)

        vm.onIntent(OnboardingIntent.OnInputChanged("T"))
        assertFalse(vm.screenState.value.showEmptyInputHint, "the next keystroke clears the hint")
    }

    // ── Chip ───────────────────────────────────────────────────────────────────

    @Test
    fun chip_createsPresetInOneTap_noAi_andOpensIt() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toBranch()

        vm.onIntent(OnboardingIntent.OnStarterChipClick(WelcomeStarterTemplate.TRIP))
        advanceUntilIdle()

        val saved = assertNotNull(fakeChecklistRepository.lastAddedChecklist)
        assertEquals("resolved_text", saved.name)
        assertEquals(WelcomeStarterTemplate.TRIP.itemRes.size, saved.items.size)
        assertTrue(saved.separateCompleted)
        assertEquals(0, fakeAnalyzeRepository.analyzeCallCount)
        assertNull(fakeActivationCoordinator.lastUserChecklistId)
        assertTrue(fakeNavigator.navigatedToChecklistDetail)
        assertEquals(0, fakeChecklistRepository.ensureInboxCallCount, "a chip is its own project, not Inbox tips")
        assertEquals("trip", events(AnalyticsEvents.Onboarding.CHIP_TAPPED).single()[AnalyticsParams.KEY])
        assertEquals(ChecklistSource.ONBOARDING_CHIP.wire, events(AnalyticsEvents.Checklist.CREATED).single()[AnalyticsParams.SOURCE])
        assertEquals("chip", completedParams()[AnalyticsParams.SEED])
    }

    @Test
    fun chip_doubleTap_insertsOnce() = runTest(testDispatcher) {
        fakeChecklistRepository.blockAddUntilReleased = true
        val vm = createViewModel()
        vm.toBranch()

        vm.onIntent(OnboardingIntent.OnStarterChipClick(WelcomeStarterTemplate.TRIP))
        runCurrent()
        vm.onIntent(OnboardingIntent.OnStarterChipClick(WelcomeStarterTemplate.GROCERIES))
        fakeChecklistRepository.releaseAddGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, fakeChecklistRepository.addChecklistCallCount)
    }

    // ── 14–16. Call / Chat ─────────────────────────────────────────────────────

    @Test
    fun call_hiddenWhenUnavailable() = runTest(testDispatcher) {
        val vm = createViewModel(isCallAvailable = false)
        vm.toBranch(OnboardingMode.Talk)

        vm.onIntent(OnboardingIntent.OnCallClick)
        advanceUntilIdle()

        assertFalse(vm.screenState.value.isCallAvailable)
        assertNull(fakeShellLaunch.pending.value)
        assertTrue(calls.none { it.startsWith("nav:") })
        assertTrue(events(AnalyticsEvents.Onboarding.CALL_TAPPED).isEmpty())
        assertTrue(events(AnalyticsEvents.Onboarding.COMPLETED).isEmpty())
    }

    @Test
    fun call_available_requestsStartVoiceCall_thenCompletes_thenMain() = runTest(testDispatcher) {
        val vm = createViewModel(isCallAvailable = true)
        vm.toBranch(OnboardingMode.Talk)

        vm.onIntent(OnboardingIntent.OnCallClick)
        advanceUntilIdle()

        assertEquals(ShellLaunch.StartVoiceCall, fakeShellLaunch.pending.value)
        assertCompletedBeforeNavigation()
        assertTrue(fakeNavigator.navigatedToMainScreen)
        assertEquals(1, events(AnalyticsEvents.Onboarding.CALL_TAPPED).size)
        assertEquals("call", completedParams()[AnalyticsParams.SEED])
    }

    @Test
    fun chat_withText_requestsOpenChatWithTrimmedPrefill_autoSent() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toBranch(OnboardingMode.Talk)
        vm.onIntent(OnboardingIntent.OnInputChanged("  Plan my week \n"))

        vm.onIntent(OnboardingIntent.OnChatClick)
        advanceUntilIdle()

        // Owner decision Q2 (2026-11-17): the user pressed Send on Talk — the chat receives it SENT.
        assertEquals(ShellLaunch.OpenChat(prefill = "Plan my week", autoSend = true), fakeShellLaunch.pending.value)
        assertEquals(0, fakeAnalyzeRepository.analyzeCallCount)
        assertTrue(fakeNavigator.navigatedToMainScreen)
        assertEquals("true", events(AnalyticsEvents.Onboarding.CHAT_TAPPED).single()[AnalyticsParams.PREFILLED])
        assertEquals("chat", completedParams()[AnalyticsParams.SEED])
    }

    @Test
    fun chat_empty_prefillNull() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toBranch(OnboardingMode.Talk)

        vm.onIntent(OnboardingIntent.OnChatClick)
        advanceUntilIdle()

        assertEquals(ShellLaunch.OpenChat(prefill = null, autoSend = false), fakeShellLaunch.pending.value)
        assertEquals("false", events(AnalyticsEvents.Onboarding.CHAT_TAPPED).single()[AnalyticsParams.PREFILLED])
    }

    @Test
    fun chat_completionFails_leavesNoPendingShellRequest() = runTest(testDispatcher) {
        fakeUserDataRepository.throwOnNextUpdate = true
        val vm = createViewModel()
        vm.toBranch(OnboardingMode.Talk)

        vm.onIntent(OnboardingIntent.OnChatClick)
        advanceUntilIdle()

        assertNull(fakeShellLaunch.pending.value, "a request without navigation would fire later, out of context")
        assertFalse(fakeNavigator.navigatedToMainScreen)
    }

    // ── 17. Own file ───────────────────────────────────────────────────────────

    @Test
    fun ownFile_navigatesToAnalyzeWithInput_onboardingSource_afterCompletion() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toBranch(OnboardingMode.Attach)

        vm.onIntent(OnboardingIntent.OnOwnSourceClick(AnalyzeInputKind.PHOTO))
        advanceUntilIdle()

        assertEquals(AnalyzeInputKind.PHOTO to AiEntrySource.ONBOARDING, fakeNavigator.lastAnalyzeWithInput)
        assertCompletedBeforeNavigation()
        // Home first, then Analyze on top: back from Analyze lands on Inbox, never on onboarding.
        assertEquals(listOf("nav:main", "nav:analyze"), calls.filter { it.startsWith("nav:") })
        assertTrue(fakeNavigator.lastMainClearBackStack)
        assertEquals("own_file_photo", completedParams()[AnalyticsParams.SEED])
        assertEquals("false", completedParams()[AnalyticsParams.CHECKLIST_CREATED])
        val door = events(AnalyticsEvents.AiEntry.TAPPED).single()
        assertEquals("analyze", door[AnalyticsParams.DESTINATION])
        assertEquals(AiEntrySource.ONBOARDING.wire, door[AnalyticsParams.SOURCE])
        assertEquals("photo", door[AnalyticsParams.INPUT_TYPE])
        assertEquals(0, fakeChecklistRepository.addChecklistCallCount)
    }

    // ── 18–19. Steps ───────────────────────────────────────────────────────────

    // tile_setsPendingFocus_consumeClears_backClears and cta_goesToTryIt_withNoHighlight were removed
    // with their contract items (owner, 2026-11-17): pendingFocus / OnModeFocusConsumed (each tile now
    // opens its own screen, nothing to highlight) and OnShowcaseCtaClick (the CTA is gone). Their
    // surviving halves are pinned below: tile → its screen + mode_selected, back → Showcase.

    @Test
    fun back_fromBranch_keepsText_andIsNoOpOnShowcase() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.OnBack)
        assertEquals(OnboardingStep.Showcase, vm.screenState.value.step)

        vm.toBranch()
        vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))
        vm.onIntent(OnboardingIntent.OnBack)

        assertEquals(OnboardingStep.Showcase, vm.screenState.value.step)
        assertEquals("Trip", vm.screenState.value.inputText)
    }

    @Test
    fun stepCompleted_bothSteps_withNumericIndex() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toBranch()

        vm.onIntent(OnboardingIntent.OnStarterChipClick(WelcomeStarterTemplate.TODO))
        advanceUntilIdle()

        val completedSteps = events(AnalyticsEvents.Onboarding.STEP_COMPLETED)
        assertEquals(listOf<Any?>(1, 2), completedSteps.map { it[AnalyticsParams.STEP_INDEX] })
        // `try_it` split into write / attach / talk (owner, 2026-11-17); the index stays 2.
        assertEquals(listOf<Any?>("showcase", "write"), completedSteps.map { it[AnalyticsParams.STEP] })
        assertTrue(completedSteps.all { it[AnalyticsParams.STEP_COUNT] == 2 })
        assertEquals(listOf<Any?>(1, 2), events(AnalyticsEvents.Onboarding.STEP_VIEWED).map { it[AnalyticsParams.STEP_INDEX] })
    }

    // ── 20. Busy ───────────────────────────────────────────────────────────────

    @Test
    fun busy_ignoresAllActions() = runTest(testDispatcher) {
        fakeAnalyzeRepository.blockAnalyzeUntilReleased = true
        val vm = createViewModel()
        vm.toBranch()
        vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))
        vm.onIntent(OnboardingIntent.OnSendClick)
        runCurrent()
        val busy = vm.screenState.value
        assertIs<OnboardingPhase.AiGenerating>(busy.phase)

        listOf(
            OnboardingIntent.OnSendClick,
            OnboardingIntent.OnBack,
            OnboardingIntent.OnSkip,
            OnboardingIntent.OnSampleClick(SampleOrigin.Card),
            OnboardingIntent.OnOwnSourceClick(AnalyzeInputKind.PDF),
            OnboardingIntent.OnStarterChipClick(WelcomeStarterTemplate.TRIP),
            OnboardingIntent.OnSavePlainListClick,
            OnboardingIntent.OnChatClick,
            OnboardingIntent.OnCallClick,
            OnboardingIntent.OnShowcaseTileClick(OnboardingMode.Talk),
            OnboardingIntent.OnTalkStarterClick(OnboardingTalkStarter.PlanWeek),
            OnboardingIntent.OnVoiceRecordingStarted,
        ).forEach { vm.onIntent(it) }
        runCurrent()

        assertEquals(busy, vm.screenState.value)
        assertEquals(1, fakeAnalyzeRepository.analyzeCallCount)
        assertEquals(0, fakeChecklistRepository.addChecklistCallCount)
        assertTrue(calls.isEmpty(), "no completion, no navigation: $calls")
        assertNull(fakeShellLaunch.pending.value)
        assertEquals(0, fakeTranscriber.calls.size)
        fakeAnalyzeRepository.releaseAnalyzeGate.complete(Unit)
        advanceUntilIdle()
    }

    // ── 21. Process death ──────────────────────────────────────────────────────

    @Test
    fun processDeath_restoresStepAndText_andCreatedIdPreventsDuplicate() = runTest(testDispatcher) {
        val handle = SavedStateHandle()
        fakeUserDataRepository.throwOnNextUpdate = true // the list lands, completion fails
        val vm1 = createViewModel(savedStateHandle = handle)
        vm1.toBranch()
        vm1.onIntent(OnboardingIntent.OnInputChanged("draft"))
        vm1.onIntent(OnboardingIntent.OnStarterChipClick(WelcomeStarterTemplate.TRIP))
        advanceUntilIdle()
        assertEquals(1, fakeChecklistRepository.addChecklistCallCount)
        assertFalse(fakeNavigator.navigatedToChecklistDetail)

        // Process death: a new VM over the same SavedStateHandle.
        val vm2 = createViewModel(savedStateHandle = handle)
        assertEquals(OnboardingStep.Write, vm2.screenState.value.step)
        assertEquals("draft", vm2.screenState.value.inputText)
        assertEquals(OnboardingPhase.Idle, vm2.screenState.value.phase)
        assertEquals(1, events(AnalyticsEvents.Onboarding.STARTED).size, "no second onboarding_started")

        vm2.onIntent(OnboardingIntent.OnStarterChipClick(WelcomeStarterTemplate.TRIP))
        advanceUntilIdle()

        assertEquals(1, fakeChecklistRepository.addChecklistCallCount, "the stored id is reused (I6)")
        assertEquals(1L, fakeNavigator.lastDetailChecklistId)
        assertEquals(1, events(AnalyticsEvents.Onboarding.COMPLETED).size, "no second onboarding_completed (I5)")
        assertEquals(1, events(AnalyticsEvents.Checklist.CREATED).size)
    }

    // ══ 2026-11-17: one screen per choice, voice dictation, auto-sent chat hand-off ══════════════
    // FEATURE_SPEC docs/designs/onboarding-steps-2026-11-17/FEATURE_SPEC.md §8.

    // ── S1. Each tile opens its own screen ─────────────────────────────────────

    @Test
    fun tile_opensItsOwnScreen_stepIndex2_andModeSelected() = runTest(testDispatcher) {
        OnboardingMode.entries.forEach { mode ->
            resetFakes()
            val vm = createViewModel()

            vm.onIntent(OnboardingIntent.OnShowcaseTileClick(mode))
            // A second tap lands on the branch (the step changed synchronously): no second event.
            vm.onIntent(OnboardingIntent.OnShowcaseTileClick(mode))

            assertEquals(mode.step, vm.screenState.value.step, "tile $mode")
            assertEquals(mode.wire, events(AnalyticsEvents.Onboarding.MODE_SELECTED).single()[AnalyticsParams.MODE])
            val viewed = events(AnalyticsEvents.Onboarding.STEP_VIEWED).last()
            assertEquals(mode.step.wire, viewed[AnalyticsParams.STEP])
            assertEquals(2, viewed[AnalyticsParams.STEP_INDEX])
            assertEquals(2, viewed[AnalyticsParams.STEP_COUNT])
            assertEquals("showcase", events(AnalyticsEvents.Onboarding.STEP_COMPLETED).single()[AnalyticsParams.STEP])
        }
    }

    // ── S2. Back from every branch → Showcase, text kept, branch-only UI cleared ──

    @Test
    fun back_fromEveryBranch_returnsToShowcase_keepsText_clearsBoxAndHints() = runTest(testDispatcher) {
        OnboardingMode.entries.forEach { mode ->
            resetFakes()
            fakeAnalyzeRepository.analyzeShouldFail = true
            val vm = createViewModel()
            vm.toBranch(OnboardingMode.Write)
            vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))
            vm.onIntent(OnboardingIntent.OnSendClick)
            advanceUntilIdle()
            assertNotNull(vm.screenState.value.aiError)
            vm.onIntent(OnboardingIntent.OnBack)
            assertNull(vm.screenState.value.aiError, "the box belongs to Write (I3)")
            vm.toBranch(mode)

            vm.onIntent(OnboardingIntent.OnBack)

            val state = vm.screenState.value
            assertEquals(OnboardingStep.Showcase, state.step, "back from $mode")
            assertEquals("Trip", state.inputText, "the goal is shared by Write and Talk and survives back")
            assertNull(state.aiError)
            assertFalse(state.showEmptyInputHint)
            assertFalse(state.showVoiceTooShortHint)
        }
    }

    @Test
    fun typedOnWrite_carriesIntoTalk_andTalkSendAutoSendsIt() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toBranch(OnboardingMode.Write)
        vm.onIntent(OnboardingIntent.OnInputChanged("Plan my week"))
        vm.onIntent(OnboardingIntent.OnBack)
        vm.toBranch(OnboardingMode.Talk)

        vm.onIntent(OnboardingIntent.OnChatClick)
        advanceUntilIdle()

        assertEquals(ShellLaunch.OpenChat(prefill = "Plan my week", autoSend = true), fakeShellLaunch.pending.value)
        assertEquals(0, fakeAnalyzeRepository.analyzeCallCount, "the chat owns the AI call, not onboarding")
        assertEquals("talk", events(AnalyticsEvents.Onboarding.STEP_COMPLETED).last()[AnalyticsParams.STEP])
    }

    // ── S3. "Show me an example" — the sample straight from Showcase ─────────

    @Test
    fun showcaseExample_runsTheSampleFromStep1_noStep2() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.onIntent(OnboardingIntent.OnSampleClick(SampleOrigin.Showcase))
        advanceUntilIdle()
        vm.onIntent(OnboardingIntent.OnSampleAnimationFinished)
        advanceUntilIdle()

        assertEquals(1, fakeChecklistRepository.addChecklistCallCount)
        assertEquals(6, assertNotNull(fakeChecklistRepository.lastAddedChecklist).items.size)
        assertEquals(0, fakeAnalyzeRepository.analyzeCallCount, "no AI, no credits — works with AI down")
        assertTrue(fakeNavigator.navigatedToChecklistDetail)
        assertEquals(OnboardingStep.Showcase, vm.screenState.value.step, "step 2 is never shown")
        assertEquals("showcase", events(AnalyticsEvents.Onboarding.SAMPLE_USED).single()[AnalyticsParams.ORIGIN])
        assertTrue(events(AnalyticsEvents.Onboarding.MODE_SELECTED).isEmpty())
        assertEquals("sample", completedParams()[AnalyticsParams.SEED])
        assertEquals(1, completedParams()[AnalyticsParams.STEP_INDEX])
        assertEquals(listOf<Any?>("showcase"), events(AnalyticsEvents.Onboarding.STEP_COMPLETED).map { it[AnalyticsParams.STEP] })
        assertTrue(events(AnalyticsEvents.Onboarding.FALLBACK_ACTION).isEmpty(), "not a fallback")
    }

    // ── S4. Talk starters: sent in the chat at once ────────────────────────────

    @Test
    fun talkStarter_opensChatWithItsTextAlreadySent() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toBranch(OnboardingMode.Talk)
        vm.onIntent(OnboardingIntent.OnInputChanged("ignored draft"))

        vm.onIntent(OnboardingIntent.OnTalkStarterClick(OnboardingTalkStarter.PackTrip))
        advanceUntilIdle()

        // The fake resolver stands in for getString(onboarding_talk_starter_2).
        assertEquals(ShellLaunch.OpenChat(prefill = "resolved_text", autoSend = true), fakeShellLaunch.pending.value)
        assertCompletedBeforeNavigation()
        assertTrue(fakeNavigator.navigatedToMainScreen)
        assertEquals("pack_trip", events(AnalyticsEvents.Onboarding.STARTER_TAPPED).single()[AnalyticsParams.KEY])
        assertEquals("chat", completedParams()[AnalyticsParams.SEED])
        assertEquals("talk", events(AnalyticsEvents.Onboarding.STEP_COMPLETED).last()[AnalyticsParams.STEP])
    }

    @Test
    fun talkStarter_completionFails_leavesNoPendingShellRequest() = runTest(testDispatcher) {
        fakeUserDataRepository.throwOnNextUpdate = true
        val vm = createViewModel()
        val effects = collectEffects(vm)
        vm.toBranch(OnboardingMode.Talk)

        vm.onIntent(OnboardingIntent.OnTalkStarterClick(OnboardingTalkStarter.PlanWeek))
        advanceUntilIdle()

        assertNull(fakeShellLaunch.pending.value, "a send without navigation would fire later, out of context")
        assertEquals(OnboardingPhase.Idle, vm.screenState.value.phase)
        assertTrue(effects.any { it == OnboardingSideEffect.ShowSnackbar(OnboardingSnackbar.CREATE_ERROR) })
    }

    // ── S5. Voice dictation: hold → clip → transcript in the field, NOT sent ──

    @Test
    fun voice_onWrite_transcriptLandsInField_nothingSent() = runTest(testDispatcher) {
        fakeTranscriber.gate = CompletableDeferred()
        fakeTranscriber.outcome = TranscriptionOutcome.Success("buy milk and eggs")
        val handle = SavedStateHandle()
        val vm = createViewModel(savedStateHandle = handle)
        vm.toBranch(OnboardingMode.Write)

        vm.onIntent(OnboardingIntent.OnVoiceRecordingStarted)
        assertEquals(OnboardingPhase.Recording(OnboardingStep.Write), vm.screenState.value.phase)
        assertTrue(vm.screenState.value.isRecording)

        vm.onIntent(OnboardingIntent.OnVoiceRecordingStopped("/tmp/clip.m4a", mimeType = "audio/webm"))
        runCurrent()
        assertEquals(OnboardingPhase.Transcribing(OnboardingStep.Write), vm.screenState.value.phase)
        assertTrue(vm.screenState.value.isTranscribing)

        fakeTranscriber.gate!!.complete(Unit)
        advanceUntilIdle()

        val state = vm.screenState.value
        assertEquals(OnboardingPhase.Idle, state.phase)
        assertEquals("buy milk and eggs", state.inputText)
        assertEquals(listOf("/tmp/clip.m4a" to "audio/webm"), fakeTranscriber.calls)
        assertEquals(0, fakeAnalyzeRepository.analyzeCallCount, "the user presses Send themselves")
        assertNull(fakeShellLaunch.pending.value)
        assertTrue(calls.isEmpty(), "no completion, no navigation: $calls")
        val voice = events(AnalyticsEvents.Onboarding.VOICE_RECORDED).single()
        assertEquals("write", voice[AnalyticsParams.SCREEN])
        assertEquals("transcribed", voice[AnalyticsParams.OUTCOME])
        // Process death after the transcript: it is restored like typed text.
        assertEquals("buy milk and eggs", createViewModel(savedStateHandle = handle).screenState.value.inputText)
    }

    @Test
    fun voice_appendsToTypedText_thenSendGeneratesFromBoth() = runTest(testDispatcher) {
        fakeTranscriber.outcome = TranscriptionOutcome.Success("for Sunday")
        val vm = createViewModel()
        vm.toBranch(OnboardingMode.Write)
        vm.onIntent(OnboardingIntent.OnInputChanged("Groceries "))

        vm.onIntent(OnboardingIntent.OnVoiceRecordingStarted)
        vm.onIntent(OnboardingIntent.OnVoiceRecordingStopped("/tmp/clip.m4a"))
        advanceUntilIdle()
        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()

        assertEquals("Groceries for Sunday", assertIs<AnalyzeInputData.RawText>(fakeAnalyzeRepository.lastInput).text)
        assertEquals("typed", completedParams()[AnalyticsParams.SEED])
    }

    @Test
    fun voice_onTalk_thenSend_autoSendsTheTranscriptInTheChat() = runTest(testDispatcher) {
        fakeTranscriber.outcome = TranscriptionOutcome.Success("plan my week")
        val vm = createViewModel()
        vm.toBranch(OnboardingMode.Talk)

        vm.onIntent(OnboardingIntent.OnVoiceRecordingStarted)
        vm.onIntent(OnboardingIntent.OnVoiceRecordingStopped("/tmp/clip.m4a"))
        advanceUntilIdle()
        assertNull(fakeShellLaunch.pending.value, "dictation only fills the field")

        vm.onIntent(OnboardingIntent.OnChatClick)
        advanceUntilIdle()

        assertEquals(ShellLaunch.OpenChat(prefill = "plan my week", autoSend = true), fakeShellLaunch.pending.value)
        assertEquals("talk", events(AnalyticsEvents.Onboarding.VOICE_RECORDED).single()[AnalyticsParams.SCREEN])
    }

    @Test
    fun voice_transcriptionFails_keepsTextEditable_withSnackbarPerCause() = runTest(testDispatcher) {
        val cases = listOf(
            TranscriptionOutcome.NetworkError to OnboardingSnackbar.TRANSCRIBE_ERROR,
            TranscriptionOutcome.ServiceError to OnboardingSnackbar.TRANSCRIBE_ERROR,
            TranscriptionOutcome.NotRegistered to OnboardingSnackbar.TRANSCRIBE_ERROR,
            TranscriptionOutcome.FileMissing to OnboardingSnackbar.TRANSCRIBE_ERROR,
            TranscriptionOutcome.EmptyTranscript to OnboardingSnackbar.TRANSCRIBE_EMPTY,
            TranscriptionOutcome.InsufficientCredits to OnboardingSnackbar.NO_CREDITS,
        )
        cases.forEach { (outcome, snackbar) ->
            resetFakes()
            fakeTranscriber.outcome = outcome
            val vm = createViewModel()
            val effects = collectEffects(vm)
            vm.toBranch(OnboardingMode.Write)
            vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))

            vm.onIntent(OnboardingIntent.OnVoiceRecordingStarted)
            vm.onIntent(OnboardingIntent.OnVoiceRecordingStopped("/tmp/clip.m4a"))
            advanceUntilIdle()

            val state = vm.screenState.value
            assertEquals(OnboardingPhase.Idle, state.phase, "$outcome")
            assertEquals("Trip", state.inputText, "$outcome: the typed text survives a failed dictation")
            assertEquals(listOf<OnboardingSideEffect>(OnboardingSideEffect.ShowSnackbar(snackbar)), effects.toList(), "$outcome")
            assertNull(state.aiError, "a dictation failure is not the AI-list box")
            // Still editable and usable: the field takes text and Send works.
            vm.onIntent(OnboardingIntent.OnInputChanged("Trip to Rome"))
            assertEquals("Trip to Rome", vm.screenState.value.inputText)
        }
    }

    @Test
    fun voice_transcriberThrows_isTreatedAsAFailure_notACrash() = runTest(testDispatcher) {
        fakeTranscriber.throws = IllegalStateException("boom")
        val vm = createViewModel()
        val effects = collectEffects(vm)
        vm.toBranch(OnboardingMode.Talk)

        vm.onIntent(OnboardingIntent.OnVoiceRecordingStarted)
        vm.onIntent(OnboardingIntent.OnVoiceRecordingStopped("/tmp/clip.m4a"))
        advanceUntilIdle()

        assertEquals(OnboardingPhase.Idle, vm.screenState.value.phase)
        assertTrue(effects.contains(OnboardingSideEffect.ShowSnackbar(OnboardingSnackbar.TRANSCRIBE_ERROR)))
        assertEquals("service_error", events(AnalyticsEvents.Onboarding.VOICE_RECORDED).single()[AnalyticsParams.OUTCOME])
    }

    @Test
    fun voice_pressEndedWithoutAudio_answersEveryReason_neverTranscribes() = runTest(testDispatcher) {
        val cases: List<Triple<OnboardingVoiceStop?, String?, String>> = listOf(
            // stop, snackbar, analytics outcome
            Triple(null, null, "cancelled"),
            Triple(OnboardingVoiceStop.UserCancelled, null, "cancelled"),
            Triple(OnboardingVoiceStop.TooShort, null, "too_short"),
            Triple(OnboardingVoiceStop.PermissionDenied, OnboardingSnackbar.MIC_DENIED, "permission_denied"),
            Triple(OnboardingVoiceStop.PermissionJustGranted, OnboardingSnackbar.MIC_HOLD_AGAIN, "permission_just_granted"),
            Triple(OnboardingVoiceStop.RecorderError, OnboardingSnackbar.RECORDING_FAILED, "recorder_error"),
        )
        cases.forEach { (stop, snackbar, outcome) ->
            resetFakes()
            val vm = createViewModel()
            val effects = collectEffects(vm)
            vm.toBranch(OnboardingMode.Write)
            vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))

            vm.onIntent(OnboardingIntent.OnVoiceRecordingStarted)
            vm.onIntent(OnboardingIntent.OnVoiceRecordingStopped(recordingPath = null, stop = stop))
            advanceUntilIdle()

            val state = vm.screenState.value
            assertEquals(OnboardingPhase.Idle, state.phase, "$stop: the recording state drops")
            assertEquals("Trip", state.inputText)
            assertEquals(stop == OnboardingVoiceStop.TooShort, state.showVoiceTooShortHint, "$stop")
            assertEquals(listOfNotNull(snackbar?.let { OnboardingSideEffect.ShowSnackbar(it) }), effects.toList(), "$stop")
            assertTrue(fakeTranscriber.calls.isEmpty(), "$stop: no audio, no credit")
            assertEquals(outcome, events(AnalyticsEvents.Onboarding.VOICE_RECORDED).single()[AnalyticsParams.OUTCOME])
        }
    }

    @Test
    fun voice_tooShortHint_clearedByTypingOrANewPress() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toBranch(OnboardingMode.Write)
        vm.onIntent(OnboardingIntent.OnVoiceRecordingStarted)
        vm.onIntent(OnboardingIntent.OnVoiceRecordingStopped(null, stop = OnboardingVoiceStop.TooShort))
        assertTrue(vm.screenState.value.showVoiceTooShortHint)

        vm.onIntent(OnboardingIntent.OnVoiceRecordingStarted)
        assertFalse(vm.screenState.value.showVoiceTooShortHint, "a new press clears it")
        vm.onIntent(OnboardingIntent.OnVoiceRecordingStopped(null, stop = OnboardingVoiceStop.TooShort))
        vm.onIntent(OnboardingIntent.OnInputChanged("T"))
        assertFalse(vm.screenState.value.showVoiceTooShortHint, "typing clears it")
    }

    @Test
    fun voice_releaseBeforeStart_dropsRecordingAtOnce_terminalStillAnswers() = runTest(testDispatcher) {
        val vm = createViewModel()
        val effects = collectEffects(vm)
        vm.toBranch(OnboardingMode.Talk)
        vm.onIntent(OnboardingIntent.OnVoiceRecordingStarted)

        // Finger up while the permission prompt is still on screen.
        vm.onIntent(OnboardingIntent.OnVoicePressReleasedBeforeStart)
        assertEquals(OnboardingPhase.Idle, vm.screenState.value.phase)
        assertTrue(effects.isEmpty(), "not an outcome yet")

        // The prompt is answered "deny": the press's single terminal callback.
        vm.onIntent(OnboardingIntent.OnVoiceRecordingStopped(null, stop = OnboardingVoiceStop.PermissionDenied))
        advanceUntilIdle()

        assertEquals(listOf<OnboardingSideEffect>(OnboardingSideEffect.ShowSnackbar(OnboardingSnackbar.MIC_DENIED)), effects.toList())
        assertEquals("talk", events(AnalyticsEvents.Onboarding.VOICE_RECORDED).single()[AnalyticsParams.SCREEN])
    }

    @Test
    fun voice_recordingAndTranscribing_areBusy_backSkipSendIgnored() = runTest(testDispatcher) {
        fakeTranscriber.gate = CompletableDeferred()
        val vm = createViewModel()
        vm.toBranch(OnboardingMode.Write)
        vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))

        vm.onIntent(OnboardingIntent.OnVoiceRecordingStarted)
        listOf(OnboardingIntent.OnBack, OnboardingIntent.OnSkip, OnboardingIntent.OnSendClick).forEach { vm.onIntent(it) }
        assertTrue(vm.screenState.value.isBusy)
        assertEquals(OnboardingStep.Write, vm.screenState.value.step)

        vm.onIntent(OnboardingIntent.OnVoiceRecordingStopped("/tmp/clip.m4a"))
        runCurrent()
        listOf(OnboardingIntent.OnBack, OnboardingIntent.OnSkip, OnboardingIntent.OnSendClick, OnboardingIntent.OnVoiceRecordingStarted)
            .forEach { vm.onIntent(it) }
        runCurrent()

        assertIs<OnboardingPhase.Transcribing>(vm.screenState.value.phase)
        assertEquals(OnboardingStep.Write, vm.screenState.value.step)
        assertEquals(0, fakeAnalyzeRepository.analyzeCallCount)
        assertTrue(calls.isEmpty(), "no completion, no navigation: $calls")
        fakeTranscriber.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(OnboardingPhase.Idle, vm.screenState.value.phase)
    }

    @Test
    fun voice_pressOutsideAComposerScreen_isIgnored() = runTest(testDispatcher) {
        listOf<OnboardingMode?>(null, OnboardingMode.Attach).forEach { mode ->
            resetFakes()
            val vm = createViewModel()
            mode?.let { vm.toBranch(it) }

            vm.onIntent(OnboardingIntent.OnVoiceRecordingStarted)

            assertEquals(OnboardingPhase.Idle, vm.screenState.value.phase, "no mic on ${vm.screenState.value.step}")
        }
    }

    @Test
    fun voice_resultArrivingDuringAnotherOperation_isDropped() = runTest(testDispatcher) {
        fakeAnalyzeRepository.blockAnalyzeUntilReleased = true
        val vm = createViewModel()
        vm.toBranch(OnboardingMode.Write)
        vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))
        vm.onIntent(OnboardingIntent.OnSendClick)
        runCurrent()

        vm.onIntent(OnboardingIntent.OnVoiceRecordingStopped("/tmp/late.m4a"))
        runCurrent()

        assertIs<OnboardingPhase.AiGenerating>(vm.screenState.value.phase)
        assertTrue(fakeTranscriber.calls.isEmpty())
        fakeAnalyzeRepository.releaseAnalyzeGate.complete(Unit)
        advanceUntilIdle()
    }

    // ── S5b. No mic on web without a Google link (0 credits until sign-in) ─────

    @Test
    fun voiceAvailability_android_true_evenUnlinked() = runTest(testDispatcher) {
        val vm = createViewModel(isWeb = false)
        advanceUntilIdle()
        assertTrue(vm.screenState.value.isVoiceAvailable)
    }

    @Test
    fun voiceAvailability_webLinked_true() = runTest(testDispatcher) {
        fakeUserDataRepository.setGoogleLinked(true)
        val vm = createViewModel(isWeb = true)
        advanceUntilIdle()
        assertTrue(vm.screenState.value.isVoiceAvailable)
    }

    @Test
    fun voiceAvailability_webUnlinked_false_andVoiceIntentsAreIgnored() = runTest(testDispatcher) {
        val vm = createViewModel(isWeb = true)
        val effects = collectEffects(vm)
        advanceUntilIdle()
        assertFalse(vm.screenState.value.isVoiceAvailable)
        vm.toBranch(OnboardingMode.Write)
        vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))

        vm.onIntent(OnboardingIntent.OnVoiceRecordingStarted)
        assertEquals(OnboardingPhase.Idle, vm.screenState.value.phase, "no mic, no recording state")
        vm.onIntent(OnboardingIntent.OnVoiceRecordingStopped("/tmp/stray.m4a"))
        advanceUntilIdle()

        assertTrue(fakeTranscriber.calls.isEmpty(), "no credit spent on a wallet that is empty by design")
        assertEquals(OnboardingPhase.Idle, vm.screenState.value.phase)
        assertEquals("Trip", vm.screenState.value.inputText)
        assertTrue(effects.isEmpty())
        assertTrue(events(AnalyticsEvents.Onboarding.VOICE_RECORDED).isEmpty())
        // Typing and Send still work: the composer is just without the mic.
        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()
        assertEquals(1, fakeAnalyzeRepository.analyzeCallCount)
    }

    @Test
    fun voiceAvailability_webLinkLandsLater_micAppears() = runTest(testDispatcher) {
        val vm = createViewModel(isWeb = true)
        advanceUntilIdle()
        assertFalse(vm.screenState.value.isVoiceAvailable)

        fakeUserDataRepository.setGoogleLinked(true)
        advanceUntilIdle()

        assertTrue(vm.screenState.value.isVoiceAvailable)
    }

    // ── S6. Process death: step and text restored on every branch ──────────────

    @Test
    fun processDeath_restoresEachBranch_andText() = runTest(testDispatcher) {
        OnboardingMode.entries.forEach { mode ->
            resetFakes()
            val handle = SavedStateHandle()
            val vm1 = createViewModel(savedStateHandle = handle)
            vm1.toBranch(mode)
            vm1.onIntent(OnboardingIntent.OnInputChanged("draft"))

            val vm2 = createViewModel(savedStateHandle = handle)

            assertEquals(mode.step, vm2.screenState.value.step, "$mode")
            assertEquals("draft", vm2.screenState.value.inputText)
            assertEquals(OnboardingPhase.Idle, vm2.screenState.value.phase)
            vm2.onIntent(OnboardingIntent.OnBack)
            assertEquals(OnboardingStep.Showcase, vm2.screenState.value.step, "back works after a restore")
        }
    }

    @Test
    fun processDeath_aStepSavedByTheOldBuild_restoresToShowcase() = runTest(testDispatcher) {
        // An app update between death and restore: the 11-04 build saved "TryIt", which no longer exists.
        val handle = SavedStateHandle(mapOf("onboarding_step" to "TryIt", "onboarding_input" to "draft"))

        val vm = createViewModel(savedStateHandle = handle)

        assertEquals(OnboardingStep.Showcase, vm.screenState.value.step)
        assertEquals("draft", vm.screenState.value.inputText)
    }

    @Test
    fun processDeath_duringRecordingOrTranscription_restoresIdle_onTheSameScreen() = runTest(testDispatcher) {
        fakeTranscriber.gate = CompletableDeferred()
        val handle = SavedStateHandle()
        val vm1 = createViewModel(savedStateHandle = handle)
        vm1.toBranch(OnboardingMode.Talk)
        vm1.onIntent(OnboardingIntent.OnVoiceRecordingStarted)
        vm1.onIntent(OnboardingIntent.OnVoiceRecordingStopped("/tmp/clip.m4a"))
        runCurrent()

        val vm2 = createViewModel(savedStateHandle = handle)

        assertEquals(OnboardingStep.Talk, vm2.screenState.value.step)
        assertEquals(OnboardingPhase.Idle, vm2.screenState.value.phase, "a transcription dies with the process")
        fakeTranscriber.gate!!.complete(Unit)
        advanceUntilIdle()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun assertInboxGotTipsAndLandedOnMain() {
        val tips = WelcomeStarterTemplate.DEFAULT_STARTER_ITEMS.size
        assertEquals(0, fakeChecklistRepository.addChecklistCallCount, "tips are Inbox tasks, not a project")
        val template = assertNotNull(fakeChecklistRepository.inboxChecklist)
        val fill = assertNotNull(fakeChecklistRepository.inboxFill)
        assertEquals(tips, template.items.size)
        assertEquals(tips, fill.items.size)
        assertTrue(template.items.all { it.text.isNotBlank() })
        assertEquals(template.items.map { it.id }, fill.items.map { it.templateItemId })
        assertEquals(template.items.map { it.text }, fill.items.map { it.text })
        assertTrue(fill.items.none { it.checked })
        assertTrue(fill.items.all { it.reminderAt == null && it.repeatRule == null })
        assertTrue(fakeNavigator.navigatedToMainScreen)
        assertTrue(fakeNavigator.lastMainClearBackStack)
        assertFalse(fakeNavigator.navigatedToChecklistDetail)
        assertTrue(fakeUserDataRepository.onboardingMarkedPassed)
    }

    // ── Test doubles ───────────────────────────────────────────────────────────

    private class NoOpLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }

    private class FakeVoiceTranscriber : OnboardingVoiceTranscriber {
        var outcome: TranscriptionOutcome = TranscriptionOutcome.Success("transcript")
        var throws: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        val calls = mutableListOf<Pair<String, String>>()

        override suspend fun transcribe(audioPath: String, mimeType: String): TranscriptionOutcome {
            calls += audioPath to mimeType
            gate?.await()
            throws?.let { throw it }
            return outcome
        }
    }

    private class FakeShellLaunchRequests : ShellLaunchRequests {
        override val pending = MutableStateFlow<ShellLaunch?>(null)
        override fun request(launch: ShellLaunch) {
            pending.value = launch
        }
        override fun consume(launch: ShellLaunch) {
            pending.compareAndSet(launch, null)
        }
    }

    private class FakeChecklistRepository : ChecklistRepository {
        var shouldThrowOnAdd = false
        var addChecklistCallCount = 0
        var lastAddedChecklist: Checklist? = null
        private var savedChecklistId: Long? = null

        var blockAddUntilReleased = false
        val releaseAddGate = CompletableDeferred<Unit>()

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

    private class FixedNavResolver(private val variant: NavVariant) : NavExperimentResolver {
        override fun currentArm(): NavVariant = variant
        override suspend fun ensureResolved(): NavVariant = variant
        override suspend fun setVariant(variant: NavVariant) {}
        override suspend fun clearVariant() {}
        override fun isArmAssigned(): Boolean = true
    }

    private class FakeAppNavigator(private val calls: MutableList<String>) : AppNavigator {
        var navigatedToMainScreen = false
        var lastMainClearBackStack = false
        var navigatedToChecklistDetail = false
        var lastDetailChecklistId: Long? = null
        var lastDetailClearBackStack = false
        var lastAnalyzeWithInput: Pair<AnalyzeInputKind, AiEntrySource>? = null

        override val events: SharedFlow<AppNavEvent> = MutableSharedFlow()
        override val backStack: NavBackStack<NavKey> = NavBackStack()
        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
        override fun onBack() {}
        override fun navigateToWelcomeOnboarding() {}
        override fun navigateToMainScreen(clearBackStack: Boolean) {
            calls += "nav:main"
            navigatedToMainScreen = true
            lastMainClearBackStack = clearBackStack
        }
        override fun navigateToDebugMenu() {}
        override fun navigateToStoreScreenshot() {}
        override fun navigateToCreateChecklistScreen(templateId: Int?, initialText: String?) {}
        override fun navigateToEditChecklist(checklistId: Long) {}
        override fun navigateToTemplatesScreen() {}
        override fun navigateToTemplatePreview(templateId: String) {}
        override fun navigateToAnalyzeWithInput(inputKind: AnalyzeInputKind, entrySource: AiEntrySource) {
            calls += "nav:analyze"
            lastAnalyzeWithInput = inputKind to entrySource
        }
        override fun navigateToAnalyzeScreen(checklistId: Long?, fillDefault: Boolean, initialText: String?, autoAnalyze: Boolean) {
            calls += "nav:analyze_hub"
        }
        override fun navigateToAnalyzeResultPreview() {}
        override fun navigateToChecklistDetail(checklistId: Long, focusItemId: String?, clearBackStack: Boolean) {
            calls += "nav:detail"
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

    private class FakeUserDataRepository(private val calls: MutableList<String>) : UserDataRepository {
        private val dataFlow = MutableStateFlow(UserData())
        var onboardingMarkedPassed = false

        /** The next update() throws once (a failed DataStore write), then behaves normally. */
        var throwOnNextUpdate = false

        override fun getUserDataFlow(): StateFlow<UserData> = dataFlow
        override suspend fun getUserData(): UserData = dataFlow.value

        fun setGoogleLinked(linked: Boolean) {
            dataFlow.value = dataFlow.value.copy(isGoogleLinked = linked)
        }

        override suspend fun update(userData: UserData) {
            if (throwOnNextUpdate) {
                throwOnNextUpdate = false
                throw RuntimeException("DataStore write error")
            }
            if (userData.isOnboardingPassed) {
                onboardingMarkedPassed = true
                calls += CALL_COMPLETE
            }
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

    private class FakeAnalyzeRepository : AnalyzeRepository {
        var analyzeShouldFail = false
        var analyzeError: Throwable? = null
        /** Thrown out of analyzeData instead of being returned as a Result.failure. */
        var analyzeThrows: Throwable? = null
        var createShouldFail = false
        var resultName: String? = "AI Name"
        var blockAnalyzeUntilReleased = false
        val releaseAnalyzeGate = CompletableDeferred<Unit>()

        var analyzeCallCount = 0
        var lastInput: AnalyzeInputData? = null
        var lastCreateName: String? = null

        override suspend fun analyzeData(
            inputData: AnalyzeInputData,
            targetChecklist: Checklist?,
        ): Result<AnalyzeResult> {
            analyzeCallCount++
            lastInput = inputData
            if (blockAnalyzeUntilReleased) releaseAnalyzeGate.await()
            analyzeThrows?.let { throw it }
            analyzeError?.let { return Result.failure(it) }
            if (analyzeShouldFail) return Result.failure(RuntimeException("analyze failed"))
            return Result.success(AnalyzeResult(suggestedItems = emptyList(), suggestedName = resultName))
        }

        override suspend fun createChecklistFromResult(name: String, result: AnalyzeResult): Result<Checklist> {
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

    private companion object {
        const val CALL_COMPLETE = "complete"
    }
}
