package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive

import androidx.navigation.NavController
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.TemplateCategory
import com.antonchuraev.homesearchchecklist.feature.create.domain.repository.TemplatesRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class InteractiveOnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeNavigator: FakeAppNavigator
    private lateinit var fakeTemplatesRepository: FakeTemplatesRepository
    private lateinit var fakeChecklistRepository: FakeChecklistRepository
    private lateinit var fakeAnalyticsTracker: RecordingAnalyticsTracker
    private lateinit var fakeUserDataRepository: FakeUserDataRepository

    private val travelTemplate = ChecklistTemplate(
        id = "travel_packing",
        name = "Travel Packing",
        icon = "luggage",
        category = "travel",
        items = listOf("Passport", "Tickets", "Hotel booking", "Adapter", "Camera")
    )

    private val workTemplate = ChecklistTemplate(
        id = "meeting_prep",
        name = "Meeting Prep",
        icon = "briefcase",
        category = "work",
        items = listOf("Agenda", "Slides", "Notes", "Recording setup")
    )

    private val testTemplates = listOf(travelTemplate, workTemplate)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeNavigator = FakeAppNavigator()
        fakeTemplatesRepository = FakeTemplatesRepository(testTemplates)
        fakeChecklistRepository = FakeChecklistRepository()
        fakeAnalyticsTracker = RecordingAnalyticsTracker()
        fakeUserDataRepository = FakeUserDataRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        templatesRepository: FakeTemplatesRepository = fakeTemplatesRepository
    ): InteractiveOnboardingViewModel {
        return InteractiveOnboardingViewModel(
            navigator = fakeNavigator,
            completeOnboardingUseCase = CompleteOnboardingUseCase(fakeUserDataRepository),
            templatesRepository = templatesRepository,
            checklistRepository = fakeChecklistRepository,
            analyticsTracker = fakeAnalyticsTracker
        )
    }

    // --- Initial state ---

    @Test
    fun initialState_isWelcomeStep() = runTest {
        val vm = createViewModel()

        assertEquals(InteractiveOnboardingStep.Welcome, vm.screenState.value.currentStep)
        assertNull(vm.screenState.value.selectedCategory)
        assertNull(vm.screenState.value.matchedTemplate)
        assertFalse(vm.screenState.value.isCreatingChecklist)
        assertFalse(vm.screenState.value.checklistCreated)
    }

    @Test
    fun init_tracksOnboardingStarted() = runTest {
        createViewModel()

        assertTrue(fakeAnalyticsTracker.hasEvent("onboarding_started"))
        assertEquals("interactive", fakeAnalyticsTracker.getEventParam("onboarding_started", "variant"))
    }

    // --- Step navigation ---

    @Test
    fun onGetStarted_updatesStepToCategorySelection() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnGetStarted)

        assertEquals(InteractiveOnboardingStep.CategorySelection, vm.screenState.value.currentStep)
    }

    @Test
    fun onGetStarted_tracksStepCompleted() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnGetStarted)

        val events = fakeAnalyticsTracker.eventsNamed("onboarding_step_completed")
        val welcomeEvent = events.firstOrNull { it["step"] == "welcome_completed" }
        assertNotNull(welcomeEvent)
        assertEquals("interactive", welcomeEvent["variant"])
    }

    // --- Category selection ---

    @Test
    fun onCategorySelected_templateFound_setsMatchedTemplate() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.ChecklistPreview, state.currentStep)
        assertEquals(OnboardingCategory.TRAVEL, state.selectedCategory)
        val template = assertNotNull(state.matchedTemplate)
        assertEquals("travel_packing", template.id)
    }

    @Test
    fun onCategorySelected_noTemplate_usesFallback() = runTest {
        val vm = createViewModel(templatesRepository = FakeTemplatesRepository(emptyList()))
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.EDUCATION))

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.ChecklistPreview, state.currentStep)
        val fallback = assertNotNull(state.matchedTemplate)
        // Fallback template has id prefixed with "onboarding_fallback_"
        assertTrue(fallback.id.startsWith("onboarding_fallback_"))
        assertEquals("My Checklist", fallback.name)
        assertEquals(5, fallback.items.size)
    }

    @Test
    fun onCategorySelected_matchesByCategory_whenPreferredIdMissing() = runTest {
        val travelByCategory = ChecklistTemplate(
            id = "some_travel_list",
            name = "Travel Essentials",
            icon = "plane",
            category = "travel",
            items = listOf("Item 1", "Item 2")
        )
        val vm = createViewModel(templatesRepository = FakeTemplatesRepository(listOf(travelByCategory)))
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))

        val state = vm.screenState.value
        val matched = assertNotNull(state.matchedTemplate)
        assertEquals("some_travel_list", matched.id)
    }

    @Test
    fun onCategorySelected_tracksCategorySelected() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.WORK))

        val events = fakeAnalyticsTracker.eventsNamed("onboarding_step_completed")
        val categoryEvent = events.firstOrNull { it["step"] == "category_selected" }
        assertNotNull(categoryEvent)
        assertEquals("WORK", categoryEvent["category"])
    }

    // --- Save checklist ---

    @Test
    fun onSaveChecklist_success_createsAndMovesToPaywall() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))
        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.Paywall, state.currentStep)
        assertTrue(state.checklistCreated)
        assertFalse(state.isCreatingChecklist)
        assertEquals(1, fakeChecklistRepository.addChecklistCallCount)
    }

    @Test
    fun onSaveChecklist_createsChecklistWithTemplateItems() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))
        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        val saved = assertNotNull(fakeChecklistRepository.lastAddedChecklist)
        assertEquals("Travel Packing", saved.name)
        assertEquals(travelTemplate.items.size, saved.items.size)
        assertEquals("Passport", saved.items.first().text)
    }

    @Test
    fun onSaveChecklist_roomError_proceedsToPaywallWithoutCrash() = runTest {
        fakeChecklistRepository.shouldThrowOnAdd = true
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))
        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.Paywall, state.currentStep)
        assertFalse(state.isCreatingChecklist)
        // checklistCreated stays false when save failed
        assertFalse(state.checklistCreated)
    }

    @Test
    fun onSaveChecklist_roomError_tracksErrorAnalytics() = runTest {
        fakeChecklistRepository.shouldThrowOnAdd = true
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))
        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeAnalyticsTracker.hasEvent("onboarding_checklist_error"))
    }

    @Test
    fun onSaveChecklist_success_tracksChecklistCreated() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))
        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        val events = fakeAnalyticsTracker.eventsNamed("onboarding_step_completed")
        val createdEvent = events.firstOrNull { it["step"] == "checklist_created" }
        assertNotNull(createdEvent)
        assertEquals("travel_packing", createdEvent["template"])
    }

    @Test
    fun onSaveChecklist_withNoMatchedTemplate_doesNothing() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Skip category selection so matchedTemplate is null
        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        // No checklist should be created when template is null
        assertEquals(0, fakeChecklistRepository.addChecklistCallCount)
        // Step should not change
        assertEquals(InteractiveOnboardingStep.Welcome, vm.screenState.value.currentStep)
    }

    // --- Skip ---

    @Test
    fun onSkip_tracksAnalyticsAndCompletes() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnSkip)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeAnalyticsTracker.hasEvent("onboarding_skipped"))
        assertEquals("interactive", fakeAnalyticsTracker.getEventParam("onboarding_skipped", "variant"))
        assertTrue(fakeUserDataRepository.onboardingMarkedPassed)
        assertTrue(fakeNavigator.navigatedToMainScreen)
    }

    @Test
    fun onSkip_includesCurrentStepInAnalytics() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Skip from Welcome step
        vm.onIntent(InteractiveOnboardingIntent.OnSkip)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            InteractiveOnboardingStep.Welcome.name,
            fakeAnalyticsTracker.getEventParam("onboarding_skipped", "step")
        )
    }

    // --- Back navigation ---

    @Test
    fun onBack_fromPreview_returnsToCategoryAndClearsTemplate() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnGetStarted)
        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))
        assertEquals(InteractiveOnboardingStep.ChecklistPreview, vm.screenState.value.currentStep)

        vm.onIntent(InteractiveOnboardingIntent.OnBack)

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.CategorySelection, state.currentStep)
        assertNull(state.matchedTemplate)
        assertNull(state.selectedCategory)
    }

    @Test
    fun onBack_fromCategory_returnsToWelcome() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnGetStarted)
        assertEquals(InteractiveOnboardingStep.CategorySelection, vm.screenState.value.currentStep)

        vm.onIntent(InteractiveOnboardingIntent.OnBack)

        assertEquals(InteractiveOnboardingStep.Welcome, vm.screenState.value.currentStep)
    }

    @Test
    fun onBack_fromPaywall_returnsToChecklistPreview() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnGetStarted)
        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))
        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(InteractiveOnboardingStep.Paywall, vm.screenState.value.currentStep)

        vm.onIntent(InteractiveOnboardingIntent.OnBack)

        assertEquals(InteractiveOnboardingStep.ChecklistPreview, vm.screenState.value.currentStep)
    }

    @Test
    fun onBack_fromWelcome_isNoOp() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(InteractiveOnboardingStep.Welcome, vm.screenState.value.currentStep)

        vm.onIntent(InteractiveOnboardingIntent.OnBack)

        assertEquals(InteractiveOnboardingStep.Welcome, vm.screenState.value.currentStep)
    }

    // --- Test doubles ---

    private class FakeTemplatesRepository(
        private val templates: List<ChecklistTemplate>
    ) : TemplatesRepository {
        override suspend fun getTemplates(): List<ChecklistTemplate> = templates
        override suspend fun getTemplatesByCategory(): List<TemplateCategory> = emptyList()
        override suspend fun getTemplateById(id: String): ChecklistTemplate? =
            templates.firstOrNull { it.id == id }
    }

    private class FakeChecklistRepository : ChecklistRepository {
        var shouldThrowOnAdd = false
        var addChecklistCallCount = 0
        var lastAddedChecklist: Checklist? = null

        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())

        override suspend fun addChecklist(checklist: Checklist): Long {
            addChecklistCallCount++
            if (shouldThrowOnAdd) throw RuntimeException("Room write error")
            lastAddedChecklist = checklist
            return 1L
        }

        override suspend fun updateChecklist(checklist: Checklist) {}
        override suspend fun updateChecklistTemplate(checklist: Checklist) {}
        override suspend fun deleteChecklist(checklist: Checklist) {}
        override suspend fun getChecklistById(id: Long): Checklist? = null
        override suspend fun reorderChecklists(orderedIds: List<Long>) {}
        override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) {}
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
        override suspend fun setRepeatSchedule(
            checklistId: Long,
            rule: ReminderRepeatRule,
            timeOfDayMinutes: Int,
            firstTriggerAt: Long
        ) {}
        override suspend fun advanceRepeatSchedule(checklistId: Long, nextAt: Long?, newCount: Int) {}
        override suspend fun clearRepeatSchedule(checklistId: Long) {}
        override suspend fun resetDefaultFillChecks(checklistId: Long) {}
        override suspend fun countActiveRepeatSchedules(): Int = 0
        override suspend fun getActiveRepeatSchedules(): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getTotalAdditionalFillCount(): Int = 0
    }

    private class FakeAppNavigator : AppNavigator {
        var navigatedToMainScreen = false
        var lastMainClearBackStack = false

        override fun installNavController(navController: NavController) {}
        override fun onBack() {}
        override fun navigateToOnboarding() {}
        override fun navigateToInteractiveOnboarding() {}
        override fun navigateToMainScreen(clearBackStack: Boolean) {
            navigatedToMainScreen = true
            lastMainClearBackStack = clearBackStack
        }
        override fun navigateToDebugMenu() {}
        override fun navigateToStoreScreenshot() {}
        override fun navigateToCreateChecklistScreen(templateId: Int?) {}
        override fun navigateToEditChecklist(checklistId: Long) {}
        override fun navigateToTemplatesScreen() {}
        override fun navigateToTemplatePreview(templateId: String) {}
        override fun navigateToAnalyzeScreen(checklistId: Long?, fillDefault: Boolean) {}
        override fun navigateToAnalyzeResultPreview() {}
        override fun navigateToChecklistDetail(checklistId: Long, clearBackStack: Boolean) {}
        override fun navigateToFillDetail(fillId: Long, clearBackStack: Boolean) {}
        override fun navigateToFillsList(checklistId: Long) {}
        override fun navigateToPaywall(source: String) {}
        override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) {}
        override fun navigateToShareChecklist(checklistId: Long) {}
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

        fun hasEvent(name: String): Boolean = events.any { it.first == name }

        fun getEventParam(name: String, param: String): Any? =
            events.firstOrNull { it.first == name }?.second?.get(param)

        fun eventsNamed(name: String): List<Map<String, Any>> =
            events.filter { it.first == name }.map { it.second }
    }
}
