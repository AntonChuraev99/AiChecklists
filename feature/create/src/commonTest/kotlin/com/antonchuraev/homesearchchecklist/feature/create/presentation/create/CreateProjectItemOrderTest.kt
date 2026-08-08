package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
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

/**
 * v2 "create project" screen — task ordering and blank-name feedback.
 *
 * Both behaviours are observable on the ViewModel alone, so they are tested there rather than
 * through the composable: the ordering is a pure state transform, and the blank-name response is a
 * state field the screen only renders. A UI test would add Robolectric to prove the same two facts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateProjectItemOrderTest {

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

    private fun createViewModel(): CreateChecklistViewModel = CreateChecklistViewModel(
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
            paywallRepository = FakeCreatePaywallRepository(isPremium = false),
            userDataRepository = FakeCreateUserDataRepository(isPremium = false),
        ),
        reminderScheduler = RecordingReminderScheduler(),
        logger = RecordingCreateLogger(),
        // Appending is a v2 behaviour: the classic arm keeps prepending (its input sits ABOVE the
        // list), and CreateChecklistViewModelTest holds that baseline.
        useProjectForm = true,
    )

    // ── C10. New tasks are appended, not prepended ───────────────────────────

    /**
     * Catches: a new task landing anywhere other than the bottom of the list.
     *
     * The redesigned screen puts the inline "add a task" row at the END of the list, the way the
     * checklist-detail screen and Todoist both do. A prepending ViewModel under an appending input
     * row means the row you just typed into jumps away from your finger and the list reads
     * bottom-to-top — which is also the order it would be SAVED in.
     */
    @Test
    fun onAddItemFromInput_withExistingItems_appendsNewItemToTheEnd() = testScope.runTest {
        val viewModel = createViewModel()

        viewModel.onIntent(CreateChecklistIntent.OnNewItemTextChange("buy milk"))
        viewModel.onIntent(CreateChecklistIntent.OnAddItemFromInput)
        viewModel.onIntent(CreateChecklistIntent.OnNewItemTextChange("clean room"))
        viewModel.onIntent(CreateChecklistIntent.OnAddItemFromInput)
        advanceUntilIdle()

        assertEquals(
            listOf("buy milk", "clean room"),
            viewModel.screenState.value.items.map { it.text },
            "Tasks must keep the order they were typed in — the newest one goes to the END of the list",
        )
    }

    /**
     * The saved checklist carries the same order the user saw, not a reversed one.
     *
     * Split from the state assertion above because these are two different failures: state order is
     * what the user reads while typing, persisted order is what every other screen (detail, widget,
     * share, sync) reads forever after.
     */
    @Test
    fun onSaveClick_afterAddingTasks_persistsThemInTypedOrder() = testScope.runTest {
        val viewModel = createViewModel()

        viewModel.onIntent(CreateChecklistIntent.OnNameChange("Trip"))
        viewModel.onIntent(CreateChecklistIntent.OnNewItemTextChange("passport"))
        viewModel.onIntent(CreateChecklistIntent.OnAddItemFromInput)
        viewModel.onIntent(CreateChecklistIntent.OnNewItemTextChange("tickets"))
        viewModel.onIntent(CreateChecklistIntent.OnAddItemFromInput)
        viewModel.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        val persisted = assertNotNull(repository.lastAddedChecklist, "Expected the project to be created")
        assertEquals(
            listOf("passport", "tickets"),
            persisted.items.map { it.text },
            "Persisted task order must match the order the user typed",
        )
    }

    // C12 (blank-name feedback) lives in androidHostTest/CreateProjectBlankNameFeedbackTest, not
    // here: the error copy comes from `getString(Res.string.create_error_name_required)`, and
    // Compose Resources cannot resolve in a plain JVM host test — the call dies with "Method
    // getSystem in android.content.res.Resources not mocked" (verified with a throwaway probe), so
    // the test would go red for the environment rather than for the behaviour.
}
