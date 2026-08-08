package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
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
import kotlin.test.assertNotNull

/**
 * Saving an edit must never widen into "rewrite the row from what the form happens to hold".
 *
 * `updateChecklist` persists the WHOLE entity, and this form shows a strict subset of it — so every
 * field it does not show has to survive the save. Both arms share this path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateProjectEditSaveTest {

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

    private fun createViewModel(useProjectForm: Boolean = true): CreateChecklistViewModel =
        CreateChecklistViewModel(
            editChecklistId = EXISTING_ID,
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

    private fun existingRow() = Checklist(
        id = EXISTING_ID,
        name = "Groceries",
        items = listOf(ChecklistItem("milk", false)),
        reminderAt = 1_700_000_000_000L,
        viewMode = ChecklistViewMode.Weekly,
        foldersEnabled = true,
        separateCompleted = true,
        position = 7,
        cloudId = "cloud-1",
    )

    /**
     * The ordinary path: the form was seeded from the row, so its fields are the row's own values.
     * Everything the form never shows — cloudId, reminderAt, position — has to come through
     * untouched.
     */
    @Test
    fun saveEdit_afterTheRowLoaded_keepsEveryFieldTheFormDoesNotShow() = testScope.runTest {
        repository.loadResult = existingRow()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(CreateChecklistIntent.OnNameChange("Groceries 2"))
        viewModel.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        val saved = assertNotNull(repository.lastUpdatedChecklist, "Edit save must write the row")
        assertEquals("Groceries 2", saved.name)
        assertEquals("cloud-1", saved.cloudId, "cloudId must survive an edit")
        assertEquals(1_700_000_000_000L, saved.reminderAt, "reminderAt must survive an edit")
        assertEquals(7, saved.position, "position must survive an edit")
    }

    /**
     * Catches: Save tapped inside the window where the async load has not landed.
     *
     * The form's settings fields are still at their DEFAULTS there (Standard, no folders), so
     * writing them back downgrades a Weekly list and clears its folders — and the older shape, a
     * bare `Checklist(id, name, items)`, wiped cloudId/reminderAt/position on top of that. The row
     * has to be re-read and only the two fields the user demonstrably typed applied.
     */
    @Test
    fun saveEdit_beforeTheRowLoaded_rereadsIt_insteadOfWritingFormDefaults() = testScope.runTest {
        // First answer (the init load) is null — as if the read had not completed; the save path
        // then re-reads and gets the real row.
        repository.loadResultSequence += null
        repository.loadResult = existingRow()

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(CreateChecklistIntent.OnNameChange("Typed before load"))
        viewModel.onIntent(CreateChecklistIntent.OnSaveClick)
        advanceUntilIdle()

        val saved = assertNotNull(repository.lastUpdatedChecklist, "Edit save must still write")
        assertEquals("Typed before load", saved.name, "The typed name is what the user owns")
        assertEquals(
            ChecklistViewMode.Weekly,
            saved.viewMode,
            "A Weekly list must not be downgraded by a form that never showed its view mode",
        )
        assertEquals(true, saved.foldersEnabled, "Folders must not be cleared by form defaults")
        assertEquals("cloud-1", saved.cloudId, "cloudId must survive")
        assertEquals(1_700_000_000_000L, saved.reminderAt, "reminderAt must survive")
        assertEquals(7, saved.position, "position must survive")
    }

    private companion object {
        const val EXISTING_ID = 42L
    }
}
