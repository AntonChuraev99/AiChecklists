package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import aichecklists.core.designsystem.generated.resources.Res
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Create" with a blank project name must answer the user, not fail quietly.
 *
 * Robolectric, not a plain JVM host test: the answer IS a localized string
 * (`create_error_project_name_required`), and Compose Resources only resolve when an Android
 * resource environment exists — `getString` otherwise throws "Method getSystem in
 * android.content.res.Resources not mocked" and the assertion would report the environment instead
 * of the behaviour.
 *
 * Run: ./gradlew :feature:create:testAndroidHostTest --tests "*CreateProjectBlankNameFeedbackTest*"
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreateProjectBlankNameFeedbackTest {

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
        // The v2 project form: it owns both the "project" copy asserted below and the landing in the
        // created project. The classic arm's copy is covered by CreateProjectArmGateTest.
        useProjectForm = true,
    )

    /**
     * Catches: submitting with an empty name doing nothing visible — a silent `return`, or a
     * disabled button that never explains itself. Both read as a frozen screen.
     *
     * The error text is compared against the v2 key `create_error_project_name_required`
     * ("Enter a project name"), not the v1 `create_error_name_required` ("Enter a checklist name"):
     * the whole screen is a project form now, and an error that still says "checklist" is the same
     * copy drift the redesign is removing.
     */
    @Test
    fun onSaveClick_withBlankName_surfacesProjectNameError_andCreatesNothing() = testScope.runTest {
        val expectedMessage = getString(Res.string.create_error_project_name_required)
        val viewModel = createViewModel()

        viewModel.onIntent(CreateChecklistIntent.OnNewItemTextChange("pack bags"))
        viewModel.onIntent(CreateChecklistIntent.OnAddItemFromInput)
        viewModel.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        assertNull(repository.lastAddedChecklist, "A blank name must not create a project")
        assertNull(navigator.detailChecklistId, "A blank name must not navigate away")
        assertFalse(navigator.navigatedToMainScreen, "A blank name must not navigate away")
        val error = assertNotNull(
            viewModel.screenState.value.nameError,
            "A blank name must produce a visible error on the name field, not a silent no-op",
        )
        assertEquals(
            expectedMessage,
            error,
            "The error must be the v2 project-name copy",
        )
        assertTrue(
            viewModel.screenState.value.nameInvalid,
            "The field itself must read as invalid, not only carry a string: the highlight is what " +
                "survives a copy lookup that cannot resolve",
        )
    }

    /**
     * The counterpart: with a name the same call DOES create and navigate.
     *
     * Present so the test above cannot pass against a ViewModel that simply never saves anything.
     * The v2 arm lands IN the created project (`navigateToChecklistDetail`) rather than resetting
     * the stack to the app root, which in the v2 shell would throw the user out of the tab they
     * started in; the classic arm's `navigateToMainScreen` landing is asserted separately in
     * CreateChecklistViewModelTest and must stay different.
     */
    @Test
    fun onSaveClick_withName_createsProject_andLandsInIt() = testScope.runTest {
        val viewModel = createViewModel()

        viewModel.onIntent(CreateChecklistIntent.OnNameChange("Trip"))
        viewModel.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        assertNotNull(repository.lastAddedChecklist, "A named project must be created")
        assertEquals(
            1L,
            navigator.detailChecklistId,
            "Creating must leave the form and open the project that was just created",
        )
        assertFalse(
            navigator.navigatedToMainScreen,
            "The v2 arm must not reset the stack to the app root — that is the classic landing",
        )
        assertNull(viewModel.screenState.value.nameError, "A valid name must leave no error behind")
    }
}
