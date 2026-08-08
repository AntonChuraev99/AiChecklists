package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two pieces of the v2 create screen that do not exist yet: the Remote-Config-driven free limit
 * and the destructive Weekly switch.
 *
 * These reference the State/Intent contract fixed in DESIGN_SPEC §14
 * (`maxChecklists`, `weeklyMode`, `weeklySwitchConfirmOpen`, `nameErrorFocusSignal`,
 * `OnWeeklyToggled` / `OnWeeklySwitchConfirm` / `OnWeeklySwitchDismiss`), so until that contract
 * lands this file fails to COMPILE — which is the red phase for an API that has no members yet.
 * Once it compiles, the assertions below are what has to go green.
 *
 * Run: ./gradlew :feature:create:testAndroidHostTest --tests "*CreateProjectLimitAndWeeklyTest*"
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateProjectLimitAndWeeklyTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var repository: RecordingChecklistRepository
    private lateinit var navigator: RecordingCreateNavigator
    private lateinit var analytics: RecordingCreateAnalytics

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = RecordingChecklistRepository().withChecklistCount(0)
        navigator = RecordingCreateNavigator()
        analytics = RecordingCreateAnalytics()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(maxChecklistsFree: Long): CreateChecklistViewModel =
        CreateChecklistViewModel(
            editChecklistId = null,
            initialText = null,
            checklistRepository = repository,
            appNavigator = navigator,
            analyticsTracker = analytics,
            getUserLimitsUseCase = GetUserLimitsUseCase(
                remoteConfigProvider = ConfigurableRemoteConfigProvider(maxChecklistsFree),
                checklistRepository = repository,
                paywallRepository = FakeCreatePaywallRepository(isPremium = false),
                userDataRepository = FakeCreateUserDataRepository(isPremium = false),
            ),
            reminderScheduler = RecordingReminderScheduler(),
            logger = RecordingCreateLogger(),
            // Settings, Weekly and the limit banner exist only on the v2 project form.
            useProjectForm = true,
        )

    // ── C11. The free limit is read, not hardcoded ───────────────────────────

    /**
     * Catches: the limit banner quoting a number compiled into the client.
     *
     * TWO Remote Config values, asserted in one test on purpose: a constant satisfies "shows 5" just
     * as well as a real read does, and the project has already shipped exactly that mistake
     * (`FREE_CHECKLIST_LIMIT = 4` against an RC value of 5). Only a pair of different values can
     * tell a read from a literal.
     */
    @Test
    fun observeUserLimits_exposesTheRemoteConfigFreeLimit_notAConstant() = testScope.runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        val atDefault = createViewModel(maxChecklistsFree = 5L)
        advanceUntilIdle()
        assertEquals(
            5,
            atDefault.screenState.value.maxChecklists,
            "The screen must publish the free project limit Remote Config currently serves",
        )

        val atRaisedLimit = createViewModel(maxChecklistsFree = 7L)
        advanceUntilIdle()
        assertEquals(
            7,
            atRaisedLimit.screenState.value.maxChecklists,
            "Raising max_checklists_free must move the number on screen — a hardcoded limit cannot",
        )
    }

    /**
     * The limit banner appears only once the user is actually at the limit, and the form keeps
     * working: hitting a plan ceiling is not an input error, and blocking the draft would punish the
     * intent to create.
     */
    @Test
    fun observeUserLimits_atTheFreeLimit_locksCreationWithoutLockingTheForm() = testScope.runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        repository.withChecklistCount(5)

        val viewModel = createViewModel(maxChecklistsFree = 5L)
        advanceUntilIdle()

        assertFalse(
            viewModel.screenState.value.canCreateChecklist,
            "Five of five projects used must read as locked",
        )
        assertEquals(
            5,
            viewModel.screenState.value.maxChecklists,
            "The banner still has to name the limit it is reporting",
        )

        viewModel.onIntent(CreateChecklistIntent.OnNameChange("Sixth project"))
        viewModel.onIntent(CreateChecklistIntent.OnNewItemTextChange("first task"))
        viewModel.onIntent(CreateChecklistIntent.OnAddItemFromInput)
        advanceUntilIdle()

        assertEquals(
            "Sixth project",
            viewModel.screenState.value.name,
            "The draft must survive the limit — the user goes to the paywall and comes back to it",
        )
        assertEquals(1, viewModel.screenState.value.items.size, "Tasks must still be addable")
    }

    // ── C12 (second half). Blank name aims the user at the field ─────────────

    /**
     * Catches: an error that is set but never brought into view.
     *
     * A `nameError` string alone is invisible when the name field has scrolled off the top of a long
     * form, so the spec pairs it with a monotonic focus signal the screen consumes to scroll and
     * refocus. Asserted as a number so it is observable without a resource environment (the error
     * COPY is covered by CreateProjectBlankNameFeedbackTest under Robolectric).
     */
    @Test
    fun onSaveClick_withBlankName_bumpsTheNameErrorFocusSignal() = testScope.runTest {
        val viewModel = createViewModel(maxChecklistsFree = 5L)
        val before = viewModel.screenState.value.nameErrorFocusSignal

        viewModel.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        assertTrue(
            viewModel.screenState.value.nameErrorFocusSignal > before,
            "A rejected submit must ask the screen to bring the name field back into view",
        )
    }

    // ── C13. Weekly is destructive → it asks first ───────────────────────────

    /**
     * Catches: flipping Weekly on a list that already has tasks and dropping them without asking.
     *
     * A Weekly project is created with `items = emptyList()` and filled per weekday, so the switch
     * cannot carry the tasks over. Losing typed work to a toggle with no confirmation is the failure
     * this fences; the toggle must stay OFF until the user says yes.
     */
    @Test
    fun onWeeklyToggled_withTasksAlreadyTyped_asksBeforeSwitching() = testScope.runTest {
        val viewModel = createViewModel(maxChecklistsFree = 5L)
        viewModel.onIntent(CreateChecklistIntent.OnNewItemTextChange("buy milk"))
        viewModel.onIntent(CreateChecklistIntent.OnAddItemFromInput)
        advanceUntilIdle()

        viewModel.onIntent(CreateChecklistIntent.OnWeeklyToggled(true))
        advanceUntilIdle()

        val state = viewModel.screenState.value
        assertTrue(state.weeklySwitchConfirmOpen, "Switching to Weekly with tasks must ask first")
        assertFalse(state.weeklyMode, "Weekly must not apply before the user confirms")
        assertEquals(
            listOf("buy milk"),
            state.items.map { it.text },
            "The tasks must still be there while the question is on screen",
        )
    }

    @Test
    fun onWeeklySwitchDismiss_keepsTheTasks_andLeavesWeeklyOff() = testScope.runTest {
        val viewModel = createViewModel(maxChecklistsFree = 5L)
        viewModel.onIntent(CreateChecklistIntent.OnNewItemTextChange("buy milk"))
        viewModel.onIntent(CreateChecklistIntent.OnAddItemFromInput)
        viewModel.onIntent(CreateChecklistIntent.OnWeeklyToggled(true))
        advanceUntilIdle()

        viewModel.onIntent(CreateChecklistIntent.OnWeeklySwitchDismiss)
        advanceUntilIdle()

        val state = viewModel.screenState.value
        assertFalse(state.weeklySwitchConfirmOpen, "Declining must close the question")
        assertFalse(state.weeklyMode, "Declining must leave the list Standard")
        assertEquals(listOf("buy milk"), state.items.map { it.text }, "Declining must keep the tasks")
    }

    @Test
    fun onWeeklySwitchConfirm_appliesWeeklyMode_andClearsTheTasks() = testScope.runTest {
        val viewModel = createViewModel(maxChecklistsFree = 5L)
        viewModel.onIntent(CreateChecklistIntent.OnNewItemTextChange("buy milk"))
        viewModel.onIntent(CreateChecklistIntent.OnAddItemFromInput)
        viewModel.onIntent(CreateChecklistIntent.OnWeeklyToggled(true))
        advanceUntilIdle()

        viewModel.onIntent(CreateChecklistIntent.OnWeeklySwitchConfirm)
        advanceUntilIdle()

        val state = viewModel.screenState.value
        assertTrue(state.weeklyMode, "Confirming must switch the project to Weekly")
        assertFalse(state.weeklySwitchConfirmOpen, "Confirming must close the question")
        assertTrue(
            state.items.isEmpty(),
            "A Weekly project starts empty — tasks the user was warned about are dropped",
        )
    }

    /**
     * The no-question path: with nothing to lose, Weekly applies straight away.
     *
     * Without this, "always ask" would satisfy the test above — and an extra dialog on an empty form
     * is friction with no purpose.
     */
    @Test
    fun onWeeklyToggled_onAnEmptyForm_switchesWithoutAsking() = testScope.runTest {
        val viewModel = createViewModel(maxChecklistsFree = 5L)

        viewModel.onIntent(CreateChecklistIntent.OnWeeklyToggled(true))
        advanceUntilIdle()

        val state = viewModel.screenState.value
        assertTrue(state.weeklyMode, "Nothing to lose — Weekly applies immediately")
        assertFalse(state.weeklySwitchConfirmOpen, "No confirmation when there are no tasks")
    }
}
