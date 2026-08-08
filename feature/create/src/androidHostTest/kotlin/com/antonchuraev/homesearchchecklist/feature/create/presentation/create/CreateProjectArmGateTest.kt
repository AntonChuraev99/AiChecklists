package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.create_error_name_required
import aichecklists.core.designsystem.generated.resources.create_error_project_name_required
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
import org.jetbrains.compose.resources.getString
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The "Classic view" (CONTROL) arm must behave exactly as it did before the v2 redesign.
 *
 * Both arms reach the SAME create route, so every behaviour the redesign changed has to be gated or
 * the running `nav_variant` experiment stops measuring navigation and starts measuring a form
 * rewrite. This file pins the three behaviours that actually differ, arm by arm, in one place —
 * asserting them only on the v2 side would let the control silently inherit any of them.
 *
 * Robolectric because the blank-name assertions compare the resolved COPY: "Enter a checklist name"
 * versus "Enter a project name" is the whole point, and `getString` needs an Android resource
 * environment.
 *
 * Run: ./gradlew :feature:create:testAndroidHostTest --tests "*CreateProjectArmGateTest*"
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreateProjectArmGateTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var repository: RecordingChecklistRepository
    private lateinit var navigator: RecordingCreateNavigator
    private lateinit var analytics: RecordingCreateAnalytics

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = RecordingChecklistRepository().withChecklistCount(0)
        navigator = RecordingCreateNavigator()
        analytics = RecordingCreateAnalytics()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(useProjectForm: Boolean): CreateChecklistViewModel =
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
                paywallRepository = FakeCreatePaywallRepository(isPremium = false),
                userDataRepository = FakeCreateUserDataRepository(isPremium = false),
            ),
            reminderScheduler = RecordingReminderScheduler(),
            logger = RecordingCreateLogger(),
            useProjectForm = useProjectForm,
        )

    /**
     * Catches: the redesign's "append at the end" reaching the control arm.
     *
     * The classic screen puts its input ABOVE the list, so a task typed there has to appear
     * directly under it — the newest first. Appending under that input reverses the list the user
     * reads AND the order it is persisted in.
     */
    @Test
    fun onAddItemFromInput_inClassicArm_prependsTheNewTask() = testScope.runTest {
        val viewModel = createViewModel(useProjectForm = false)

        viewModel.onIntent(CreateChecklistIntent.OnNewItemTextChange("buy milk"))
        viewModel.onIntent(CreateChecklistIntent.OnAddItemFromInput)
        viewModel.onIntent(CreateChecklistIntent.OnNewItemTextChange("clean room"))
        viewModel.onIntent(CreateChecklistIntent.OnAddItemFromInput)
        advanceUntilIdle()

        assertEquals(
            listOf("clean room", "buy milk"),
            viewModel.screenState.value.items.map { it.text },
            "The classic arm puts new items at the TOP — that is where its input sits",
        )
    }

    /**
     * Catches: the control arm landing in the created project.
     *
     * "Where creation takes you" is one of the outcomes the experiment reads; moving it on both arms
     * makes the arms indistinguishable on exactly the dimension being measured.
     */
    @Test
    fun onSaveClick_inClassicArm_resetsToMainScreen_andNotIntoTheProject() = testScope.runTest {
        val viewModel = createViewModel(useProjectForm = false)

        viewModel.onIntent(CreateChecklistIntent.OnNameChange("Trip"))
        viewModel.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        assertTrue(navigator.navigatedToMainScreen, "The classic arm returns to the app root")
        assertNull(navigator.detailChecklistId, "The classic arm must not open the created project")
    }

    /**
     * The same tap on the v2 arm goes the other way — proving the gate is a real branch and not a
     * constant that happens to match the control.
     */
    @Test
    fun onSaveClick_inProjectForm_landsInTheCreatedProject() = testScope.runTest {
        val viewModel = createViewModel(useProjectForm = true)

        viewModel.onIntent(CreateChecklistIntent.OnNameChange("Trip"))
        viewModel.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        assertEquals(1L, navigator.detailChecklistId, "The v2 arm opens the project it just created")
        assertFalse(navigator.navigatedToMainScreen, "…and does not reset the stack to the app root")
    }

    /**
     * Catches: the v2 wording ("project") leaking into the classic arm, whose entire vocabulary —
     * title, CTA, section headers — still says "checklist".
     */
    @Test
    fun onSaveClick_withBlankName_usesTheCopyOfTheArmItIsRunningIn() = testScope.runTest {
        val classicCopy = getString(Res.string.create_error_name_required)
        val projectCopy = getString(Res.string.create_error_project_name_required)
        assertTrue(
            classicCopy != projectCopy,
            "Precondition: the two arms ship different copy, otherwise this test proves nothing",
        )

        val classic = createViewModel(useProjectForm = false)
        classic.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()
        assertEquals(
            classicCopy,
            classic.screenState.value.nameError,
            "The classic arm must keep saying \"checklist\"",
        )

        val projectForm = createViewModel(useProjectForm = true)
        projectForm.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()
        assertEquals(
            projectCopy,
            projectForm.screenState.value.nameError,
            "The v2 arm must say \"project\"",
        )
    }
}
