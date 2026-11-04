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
        isCallAvailable = isCallAvailable,
        isDebugBuild = false,
        isWeb = isWeb,
        stringResolver = fakeStringResolver,
    )

    private fun WelcomeOnboardingViewModel.toTryIt() {
        onIntent(OnboardingIntent.OnShowcaseCtaClick)
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
        vm.toTryIt()

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
        vm.toTryIt()

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
        vm.toTryIt()
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
        vm.toTryIt()
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
        vm.toTryIt()

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
        vm.toTryIt()

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
                vm.toTryIt()
                vm.onIntent(OnboardingIntent.OnSampleClick(SampleOrigin.Card))
                vm.onIntent(OnboardingIntent.OnSampleAnimationFinished)
            },
            "chip" to { vm -> vm.toTryIt(); vm.onIntent(OnboardingIntent.OnStarterChipClick(WelcomeStarterTemplate.TRIP)) },
            "typed" to { vm ->
                vm.toTryIt()
                vm.onIntent(OnboardingIntent.OnInputChanged("Birthday party"))
                vm.onIntent(OnboardingIntent.OnSendClick)
            },
            "plain" to { vm ->
                fakeAnalyzeRepository.analyzeShouldFail = true
                vm.toTryIt()
                vm.onIntent(OnboardingIntent.OnInputChanged("Birthday party"))
                vm.onIntent(OnboardingIntent.OnSendClick)
                testDispatcher.scheduler.advanceUntilIdle()
                vm.onIntent(OnboardingIntent.OnSavePlainListClick)
            },
            "own_file" to { vm -> vm.toTryIt(); vm.onIntent(OnboardingIntent.OnOwnSourceClick(AnalyzeInputKind.PDF)) },
            "chat" to { vm -> vm.toTryIt(); vm.onIntent(OnboardingIntent.OnChatClick) },
            "call" to { vm -> vm.toTryIt(); vm.onIntent(OnboardingIntent.OnCallClick) },
            "skip" to { vm -> vm.onIntent(OnboardingIntent.OnSkip) },
        )
        branches.forEach { (name, act) ->
            resetFakes()
            val vm = createViewModel()
            act(vm)
            advanceUntilIdle()
            if (name == "sample") {
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
        vm.toTryIt()

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
    fun skip_sendsSeedSkip_fromTryIt() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toTryIt()

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
        vm.toTryIt()
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
        vm.toTryIt()
        vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))

        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()

        assertEquals(OnboardingAiError.SignInRequired, vm.screenState.value.aiError)
    }

    @Test
    fun typed_aiFailure_thenSample_createsSample() = runTest(testDispatcher) {
        fakeAnalyzeRepository.analyzeShouldFail = true
        val vm = createViewModel()
        vm.toTryIt()
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
        vm.toTryIt()
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
        vm.toTryIt()
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
        vm.toTryIt()
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
        vm.toTryIt()
        vm.onIntent(OnboardingIntent.OnInputChanged("Some topic"))

        vm.onIntent(OnboardingIntent.OnSendClick)
        advanceUntilIdle()

        assertEquals("resolved_text", fakeAnalyzeRepository.lastCreateName)
    }

    @Test
    fun typed_createFails_showsBox_noActivation() = runTest(testDispatcher) {
        fakeAnalyzeRepository.createShouldFail = true
        val vm = createViewModel()
        vm.toTryIt()
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
        vm.toTryIt()
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
        vm.toTryIt()
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
        vm.toTryIt()
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
        vm.toTryIt()
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
        vm.toTryIt()
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
        vm.toTryIt()
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
        vm.toTryIt()
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
        vm.toTryIt()
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
        vm.toTryIt()

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
        vm.toTryIt()

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
        vm.toTryIt()

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
        vm.toTryIt()

        vm.onIntent(OnboardingIntent.OnCallClick)
        advanceUntilIdle()

        assertEquals(ShellLaunch.StartVoiceCall, fakeShellLaunch.pending.value)
        assertCompletedBeforeNavigation()
        assertTrue(fakeNavigator.navigatedToMainScreen)
        assertEquals(1, events(AnalyticsEvents.Onboarding.CALL_TAPPED).size)
        assertEquals("call", completedParams()[AnalyticsParams.SEED])
    }

    @Test
    fun chat_withText_requestsOpenChatWithTrimmedPrefill_notSent() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toTryIt()
        vm.onIntent(OnboardingIntent.OnInputChanged("  Plan my week \n"))

        vm.onIntent(OnboardingIntent.OnChatClick)
        advanceUntilIdle()

        assertEquals(ShellLaunch.OpenChat(prefill = "Plan my week"), fakeShellLaunch.pending.value)
        assertEquals(0, fakeAnalyzeRepository.analyzeCallCount)
        assertTrue(fakeNavigator.navigatedToMainScreen)
        assertEquals("true", events(AnalyticsEvents.Onboarding.CHAT_TAPPED).single()[AnalyticsParams.PREFILLED])
        assertEquals("chat", completedParams()[AnalyticsParams.SEED])
    }

    @Test
    fun chat_empty_prefillNull() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toTryIt()

        vm.onIntent(OnboardingIntent.OnChatClick)
        advanceUntilIdle()

        assertEquals(ShellLaunch.OpenChat(prefill = null), fakeShellLaunch.pending.value)
        assertEquals("false", events(AnalyticsEvents.Onboarding.CHAT_TAPPED).single()[AnalyticsParams.PREFILLED])
    }

    @Test
    fun chat_completionFails_leavesNoPendingShellRequest() = runTest(testDispatcher) {
        fakeUserDataRepository.throwOnNextUpdate = true
        val vm = createViewModel()
        vm.toTryIt()

        vm.onIntent(OnboardingIntent.OnChatClick)
        advanceUntilIdle()

        assertNull(fakeShellLaunch.pending.value, "a request without navigation would fire later, out of context")
        assertFalse(fakeNavigator.navigatedToMainScreen)
    }

    // ── 17. Own file ───────────────────────────────────────────────────────────

    @Test
    fun ownFile_navigatesToAnalyzeWithInput_onboardingSource_afterCompletion() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toTryIt()

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

    @Test
    fun tile_setsPendingFocus_consumeClears_backClears() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.onIntent(OnboardingIntent.OnShowcaseTileClick(OnboardingMode.Write))
        assertEquals(OnboardingStep.TryIt, vm.screenState.value.step)
        assertEquals(OnboardingMode.Write, vm.screenState.value.pendingFocus)
        assertEquals("write", events(AnalyticsEvents.Onboarding.MODE_SELECTED).single()[AnalyticsParams.MODE])

        vm.onIntent(OnboardingIntent.OnModeFocusConsumed)
        assertNull(vm.screenState.value.pendingFocus)

        vm.onIntent(OnboardingIntent.OnBack)
        vm.onIntent(OnboardingIntent.OnShowcaseTileClick(OnboardingMode.Talk))
        vm.onIntent(OnboardingIntent.OnBack)
        assertEquals(OnboardingStep.Showcase, vm.screenState.value.step)
        assertNull(vm.screenState.value.pendingFocus, "back clears an unconsumed highlight (I4)")
    }

    @Test
    fun cta_goesToTryIt_withNoHighlight() = runTest(testDispatcher) {
        val vm = createViewModel()

        vm.onIntent(OnboardingIntent.OnShowcaseCtaClick)

        assertEquals(OnboardingStep.TryIt, vm.screenState.value.step)
        assertNull(vm.screenState.value.pendingFocus)
        assertEquals("default", events(AnalyticsEvents.Onboarding.MODE_SELECTED).single()[AnalyticsParams.MODE])
    }

    @Test
    fun back_fromTryIt_keepsText_andIsNoOpOnShowcase() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.OnBack)
        assertEquals(OnboardingStep.Showcase, vm.screenState.value.step)

        vm.toTryIt()
        vm.onIntent(OnboardingIntent.OnInputChanged("Trip"))
        vm.onIntent(OnboardingIntent.OnBack)

        assertEquals(OnboardingStep.Showcase, vm.screenState.value.step)
        assertEquals("Trip", vm.screenState.value.inputText)
    }

    @Test
    fun stepCompleted_bothSteps_withNumericIndex() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toTryIt()

        vm.onIntent(OnboardingIntent.OnStarterChipClick(WelcomeStarterTemplate.TODO))
        advanceUntilIdle()

        val completedSteps = events(AnalyticsEvents.Onboarding.STEP_COMPLETED)
        assertEquals(listOf<Any?>(1, 2), completedSteps.map { it[AnalyticsParams.STEP_INDEX] })
        assertEquals(listOf<Any?>("showcase", "try_it"), completedSteps.map { it[AnalyticsParams.STEP] })
        assertTrue(completedSteps.all { it[AnalyticsParams.STEP_COUNT] == 2 })
        assertEquals(listOf<Any?>(1, 2), events(AnalyticsEvents.Onboarding.STEP_VIEWED).map { it[AnalyticsParams.STEP_INDEX] })
    }

    // ── 20. Busy ───────────────────────────────────────────────────────────────

    @Test
    fun busy_ignoresAllActions() = runTest(testDispatcher) {
        fakeAnalyzeRepository.blockAnalyzeUntilReleased = true
        val vm = createViewModel()
        vm.toTryIt()
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
            OnboardingIntent.OnShowcaseCtaClick,
        ).forEach { vm.onIntent(it) }
        runCurrent()

        assertEquals(busy, vm.screenState.value)
        assertEquals(1, fakeAnalyzeRepository.analyzeCallCount)
        assertEquals(0, fakeChecklistRepository.addChecklistCallCount)
        assertTrue(calls.isEmpty(), "no completion, no navigation: $calls")
        assertNull(fakeShellLaunch.pending.value)
        fakeAnalyzeRepository.releaseAnalyzeGate.complete(Unit)
        advanceUntilIdle()
    }

    // ── 21. Process death ──────────────────────────────────────────────────────

    @Test
    fun processDeath_restoresStepAndText_andCreatedIdPreventsDuplicate() = runTest(testDispatcher) {
        val handle = SavedStateHandle()
        fakeUserDataRepository.throwOnNextUpdate = true // the list lands, completion fails
        val vm1 = createViewModel(savedStateHandle = handle)
        vm1.toTryIt()
        vm1.onIntent(OnboardingIntent.OnInputChanged("draft"))
        vm1.onIntent(OnboardingIntent.OnStarterChipClick(WelcomeStarterTemplate.TRIP))
        advanceUntilIdle()
        assertEquals(1, fakeChecklistRepository.addChecklistCallCount)
        assertFalse(fakeNavigator.navigatedToChecklistDetail)

        // Process death: a new VM over the same SavedStateHandle.
        val vm2 = createViewModel(savedStateHandle = handle)
        assertEquals(OnboardingStep.TryIt, vm2.screenState.value.step)
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
