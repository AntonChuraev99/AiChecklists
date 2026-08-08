package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
 * The "Reminder" settings row (DESIGN_SPEC §2.1) and the "More options" disclosure.
 *
 * The reminder is the one setting on this form that changes behaviour OUTSIDE the app, and the
 * project it belongs to does not exist while the form is open — so the interesting property is not
 * "the state field changed" but "the alarm was armed against the id `addChecklist` returned". Both
 * halves are asserted: a test that only checked the persisted row would pass against a project whose
 * reminder never fires.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateProjectReminderAndOptionsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var repository: RecordingChecklistRepository
    private lateinit var navigator: RecordingCreateNavigator
    private lateinit var analytics: RecordingCreateAnalytics
    private lateinit var scheduler: RecordingReminderScheduler
    private lateinit var logger: RecordingCreateLogger

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = RecordingChecklistRepository().withChecklistCount(0)
        navigator = RecordingCreateNavigator()
        analytics = RecordingCreateAnalytics()
        scheduler = RecordingReminderScheduler()
        logger = RecordingCreateLogger()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(isPremium: Boolean = false): CreateChecklistViewModel =
        CreateChecklistViewModel(
            editChecklistId = null,
            initialText = null,
            checklistRepository = repository,
            appNavigator = navigator,
            analyticsTracker = analytics,
            getUserLimitsUseCase = GetUserLimitsUseCase(
                remoteConfigProvider = ConfigurableRemoteConfigProvider(
                    maxChecklistsFree = RemoteConfigDefaults.MAX_CHECKLISTS_FREE,
                ),
                checklistRepository = repository,
                paywallRepository = FakeCreatePaywallRepository(isPremium = isPremium),
                userDataRepository = FakeCreateUserDataRepository(isPremium = isPremium),
            ),
            reminderScheduler = scheduler,
            logger = logger,
            useProjectForm = true,
        )

    // ── Staged one-shot reminder ─────────────────────────────────────────────

    /**
     * Catches: a reminder that is only ever a field on the form.
     *
     * `ReminderScheduler` keys its alarms by checklist id, which does not exist until
     * `addChecklist()` returns — so the staged value has to be replayed afterwards. Dropping that
     * step leaves the user with a project they were told has a reminder and an alarm nobody set.
     */
    @Test
    fun stagedOneShotReminder_isPersistedAndArmedAgainstTheNewProject() = testScope.runTest {
        val viewModel = createViewModel()
        val triggerAt = currentTimeMillis() + 60 * 60 * 1000L

        viewModel.onIntent(CreateChecklistIntent.OnReminderClick)
        advanceUntilIdle()
        viewModel.onIntent(CreateChecklistIntent.OnReminderPresetSelected(triggerAt))
        advanceUntilIdle()

        val staged = viewModel.screenState.value
        assertEquals(triggerAt, staged.reminderAt, "The preset must be staged on the form")
        assertFalse(staged.reminderSheetOpen, "Picking a preset closes the sheet")
        assertNull(
            repository.reminderSetFor,
            "Nothing may be written before the project exists — there is no id to write it against",
        )

        viewModel.onIntent(CreateChecklistIntent.OnNameChange("Trip"))
        viewModel.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        assertEquals(1L to triggerAt, repository.reminderSetFor, "The reminder must reach the row")
        assertEquals(1L to triggerAt, scheduler.scheduledReminder, "…and the alarm must be armed")
    }

    /**
     * Catches: a repeat schedule staged but never turned into a first occurrence.
     *
     * `setRepeatSchedule` needs a concrete `firstTriggerAt`; a rule with no next occurrence is a
     * schedule that silently never fires.
     */
    @Test
    fun stagedRepeatSchedule_isPersistedWithAFirstOccurrence_andArmed() = testScope.runTest {
        val viewModel = createViewModel()

        viewModel.onIntent(CreateChecklistIntent.OnReminderClick)
        advanceUntilIdle()
        viewModel.onIntent(CreateChecklistIntent.OnReminderTabSelected(ReminderTab.REPEAT))
        advanceUntilIdle()
        assertNotNull(
            viewModel.screenState.value.pendingRepeatConfig,
            "Opening the REPEAT tab must seed an editable config",
        )

        viewModel.onIntent(CreateChecklistIntent.OnRepeatTypeSelected(RepeatType.WEEKLY))
        viewModel.onIntent(CreateChecklistIntent.OnRepeatTimeChanged(8, 30))
        viewModel.onIntent(CreateChecklistIntent.OnSaveRepeat)
        advanceUntilIdle()

        val staged = viewModel.screenState.value
        assertEquals(RepeatType.WEEKLY, staged.repeatRule?.type, "The rule must be staged")
        assertEquals(8 * 60 + 30, staged.repeatTimeOfDayMinutes, "…with the time the user picked")
        assertNull(staged.pendingRepeatConfig, "Saving the repeat closes the editor")

        viewModel.onIntent(CreateChecklistIntent.OnNameChange("Gym"))
        viewModel.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        val recorded = assertNotNull(
            repository.repeatScheduleSetFor,
            "The repeat must be written against the created project",
        )
        assertEquals(1L, recorded.checklistId)
        assertEquals(RepeatType.WEEKLY, recorded.rule.type)
        assertEquals(8 * 60 + 30, recorded.timeOfDayMinutes)
        assertTrue(
            recorded.firstTriggerAt > currentTimeMillis(),
            "The first occurrence must be in the future, otherwise the alarm fires never",
        )
        assertEquals(
            1L to recorded.firstTriggerAt,
            scheduler.scheduledRepeat,
            "The alarm must be armed for exactly the occurrence that was persisted",
        )
    }

    /**
     * The free-tier ceiling is answered BEFORE the sheet opens, with the sheet's own locked banner —
     * not by refusing at save time, which would be a dead tap on a control the user was allowed to
     * touch.
     */
    @Test
    fun onReminderClick_whenTheFreeOneShotQuotaIsSpent_opensTheSheetLocked() = testScope.runTest {
        repository.activeReminderCount = 1
        val viewModel = createViewModel(isPremium = false)
        advanceUntilIdle()

        viewModel.onIntent(CreateChecklistIntent.OnReminderClick)
        advanceUntilIdle()

        val state = viewModel.screenState.value
        assertTrue(state.reminderSheetOpen, "The sheet still opens — it carries the explanation")
        assertTrue(state.reminderSheetLocked, "…in its locked form")
    }

    @Test
    fun onReminderClick_forAPremiumUser_opensTheSheetUnlocked() = testScope.runTest {
        repository.activeReminderCount = 3
        val viewModel = createViewModel(isPremium = true)
        advanceUntilIdle()

        viewModel.onIntent(CreateChecklistIntent.OnReminderClick)
        advanceUntilIdle()

        assertFalse(
            viewModel.screenState.value.reminderSheetLocked,
            "Premium has no reminder ceiling to hit",
        )
    }

    /**
     * Creating without touching the row must not write or arm anything — the staged-reminder path
     * has to stay invisible to every project that does not use it.
     */
    @Test
    fun createWithoutAReminder_touchesNeitherTheRowNorTheScheduler() = testScope.runTest {
        val viewModel = createViewModel()

        viewModel.onIntent(CreateChecklistIntent.OnNameChange("Trip"))
        viewModel.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        assertNotNull(repository.lastAddedChecklist, "The project is still created")
        assertNull(repository.reminderSetFor, "No reminder was staged, so none may be written")
        assertNull(scheduler.scheduledReminder, "…and no alarm may be armed")
        assertNull(scheduler.scheduledRepeat)
    }

    // ── "More options" disclosure ────────────────────────────────────────────

    /**
     * Catches: the first tap on a tablet doing nothing.
     *
     * The disclosure's default depends on window size (collapsed on Compact, expanded on
     * Medium/Expanded), which the ViewModel cannot see. Deriving `!(stored ?: false)` here writes
     * `true` over a section that is ALREADY expanded, so the screen renders the same thing and the
     * user has to tap twice. The screen therefore reports what it is showing.
     */
    @Test
    fun onToggleMoreOptions_flipsTheValueTheScreenIsShowing_notAnAssumedDefault() = testScope.runTest {
        val viewModel = createViewModel()

        // Wide window: the screen is showing the section EXPANDED while the stored value is null.
        viewModel.onIntent(CreateChecklistIntent.OnToggleMoreOptions(currentlyExpanded = true))
        advanceUntilIdle()
        assertEquals(
            false,
            viewModel.screenState.value.moreOptionsExpanded,
            "The first tap must collapse what is on screen, not re-assert it",
        )

        viewModel.onIntent(CreateChecklistIntent.OnToggleMoreOptions(currentlyExpanded = false))
        advanceUntilIdle()
        assertEquals(true, viewModel.screenState.value.moreOptionsExpanded, "…and the next re-opens it")
    }

    /**
     * The Compact default is the mirror image: the section starts collapsed, so the first tap opens
     * it. Present so the assertion above cannot pass against a handler that always writes `false`.
     */
    @Test
    fun onToggleMoreOptions_fromCollapsed_expands() = testScope.runTest {
        val viewModel = createViewModel()

        viewModel.onIntent(CreateChecklistIntent.OnToggleMoreOptions(currentlyExpanded = false))
        advanceUntilIdle()

        assertEquals(true, viewModel.screenState.value.moreOptionsExpanded)
    }
}
