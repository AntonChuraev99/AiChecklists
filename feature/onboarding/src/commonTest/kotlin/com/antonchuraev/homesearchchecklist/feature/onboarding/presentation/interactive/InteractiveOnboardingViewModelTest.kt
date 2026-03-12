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
        items = listOf("Passport", "Tickets", "Hotel booking", "Adapter", "Camera", "Charger", "Clothes")
    )

    private val vacationTemplate = ChecklistTemplate(
        id = "vacation_prep",
        name = "Vacation Prep",
        icon = "flight",
        category = "travel",
        items = listOf("Book flights", "Check passport", "Notify bank", "Pack bags")
    )

    private val workTemplate = ChecklistTemplate(
        id = "meeting_prep",
        name = "Meeting Prep",
        icon = "briefcase",
        category = "work",
        items = listOf("Agenda", "Slides", "Notes", "Recording setup")
    )

    private val testTemplates = listOf(travelTemplate, vacationTemplate, workTemplate)

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

    /** Navigate VM through category → style → template selection. Returns the VM. */
    private fun createViewModelAtCustomize(
        category: OnboardingCategory = OnboardingCategory.TRAVEL,
        style: OrganizingStyle = OrganizingStyle.DETAILED,
        template: ChecklistTemplate = travelTemplate
    ): InteractiveOnboardingViewModel {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(category))
        vm.onIntent(InteractiveOnboardingIntent.OnStyleSelected(style))
        vm.onIntent(InteractiveOnboardingIntent.OnTemplateSelected(template))
        return vm
    }

    // --- Initial state ---

    @Test
    fun initialState_isCategorySelectionStep() = runTest {
        val vm = createViewModel()

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.CategorySelection, state.currentStep)
        assertNull(state.selectedCategory)
        assertNull(state.selectedStyle)
        assertNull(state.selectedTemplate)
        assertTrue(state.customizedItems.isEmpty())
        assertEquals("", state.checklistName)
        assertFalse(state.isCreatingChecklist)
        assertFalse(state.checklistCreated)
        assertFalse(state.wasTemplateStepSkipped)
    }

    @Test
    fun init_tracksOnboardingStarted() = runTest {
        createViewModel()

        assertTrue(fakeAnalyticsTracker.hasEvent("onboarding_started"))
        assertEquals("interactive", fakeAnalyticsTracker.getEventParam("onboarding_started", "variant"))
    }

    // --- Category selection ---

    @Test
    fun onCategorySelected_filtersTemplatesAndAdvancesToStyleSelection() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.StyleSelection, state.currentStep)
        assertEquals(OnboardingCategory.TRAVEL, state.selectedCategory)
        assertTrue(state.availableTemplates.isNotEmpty())
        assertEquals("travel_packing", state.availableTemplates.first().id)
    }

    @Test
    fun onCategorySelected_noTemplates_usesFallback() = runTest {
        val vm = createViewModel(templatesRepository = FakeTemplatesRepository(emptyList()))
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.EDUCATION))

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.StyleSelection, state.currentStep)
        assertEquals(1, state.availableTemplates.size)
        val fallback = state.availableTemplates.first()
        assertTrue(fallback.id.startsWith("onboarding_fallback_"))
        assertEquals("My Checklist", fallback.name)
        assertEquals(5, fallback.items.size)
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
        assertEquals("interactive", categoryEvent["variant"])
    }

    // --- Style selection ---

    @Test
    fun onStyleSelected_advancesToTemplateSelection() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))
        vm.onIntent(InteractiveOnboardingIntent.OnStyleSelected(OrganizingStyle.MINIMALIST))

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.TemplateSelection, state.currentStep)
        assertEquals(OrganizingStyle.MINIMALIST, state.selectedStyle)
    }

    @Test
    fun onStyleSelected_tracksAnalytics() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))
        vm.onIntent(InteractiveOnboardingIntent.OnStyleSelected(OrganizingStyle.CHAOTIC))

        val events = fakeAnalyticsTracker.eventsNamed("onboarding_step_completed")
        val styleEvent = events.firstOrNull { it["step"] == "style_selected" }
        assertNotNull(styleEvent)
        assertEquals("CHAOTIC", styleEvent["style"])
    }

    // --- Auto-skip template selection (single template) ---

    @Test
    fun onStyleSelected_singleTemplate_autoSkipsToCustomize() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.WORK))
        vm.onIntent(InteractiveOnboardingIntent.OnStyleSelected(OrganizingStyle.DETAILED))

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.Customize, state.currentStep)
        assertEquals(workTemplate, state.selectedTemplate)
        assertEquals(workTemplate.items.size, state.customizedItems.size)
        assertEquals("Meeting Prep", state.checklistName)
    }

    @Test
    fun onStyleSelected_singleTemplate_setsWasTemplateStepSkipped() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.WORK))
        vm.onIntent(InteractiveOnboardingIntent.OnStyleSelected(OrganizingStyle.DETAILED))

        assertTrue(vm.screenState.value.wasTemplateStepSkipped)
    }

    @Test
    fun onStyleSelected_multipleTemplates_doesNotAutoSkip() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))
        vm.onIntent(InteractiveOnboardingIntent.OnStyleSelected(OrganizingStyle.DETAILED))

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.TemplateSelection, state.currentStep)
        assertFalse(state.wasTemplateStepSkipped)
        assertNull(state.selectedTemplate)
    }

    @Test
    fun onStyleSelected_singleTemplate_tracksAutoSkipAnalytics() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.WORK))
        vm.onIntent(InteractiveOnboardingIntent.OnStyleSelected(OrganizingStyle.MINIMALIST))

        val events = fakeAnalyticsTracker.eventsNamed("onboarding_step_completed")
        assertNotNull(events.firstOrNull { it["step"] == "style_selected" })
        val autoEvent = events.firstOrNull { it["step"] == "template_auto_selected" }
        assertNotNull(autoEvent)
        assertEquals("meeting_prep", autoEvent["template"])
    }

    @Test
    fun onBack_fromCustomize_whenSkipped_returnsToStyleAndClearsState() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.WORK))
        vm.onIntent(InteractiveOnboardingIntent.OnStyleSelected(OrganizingStyle.DETAILED))
        assertEquals(InteractiveOnboardingStep.Customize, vm.screenState.value.currentStep)
        assertTrue(vm.screenState.value.wasTemplateStepSkipped)

        vm.onIntent(InteractiveOnboardingIntent.OnBack)

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.StyleSelection, state.currentStep)
        assertNull(state.selectedStyle)
        assertNull(state.selectedTemplate)
        assertTrue(state.customizedItems.isEmpty())
        assertEquals("", state.checklistName)
        assertFalse(state.wasTemplateStepSkipped)
    }

    // --- Template selection & style application ---

    @Test
    fun onTemplateSelected_minimalist_takesFiveItems() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))
        vm.onIntent(InteractiveOnboardingIntent.OnStyleSelected(OrganizingStyle.MINIMALIST))
        vm.onIntent(InteractiveOnboardingIntent.OnTemplateSelected(travelTemplate))

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.Customize, state.currentStep)
        assertEquals(5, state.customizedItems.size)
        assertEquals("Passport", state.customizedItems.first().text)
        assertTrue(state.customizedItems.all { it.isEnabled })
    }

    @Test
    fun onTemplateSelected_detailed_takesAllItems() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))
        vm.onIntent(InteractiveOnboardingIntent.OnStyleSelected(OrganizingStyle.DETAILED))
        vm.onIntent(InteractiveOnboardingIntent.OnTemplateSelected(travelTemplate))

        val state = vm.screenState.value
        assertEquals(travelTemplate.items.size, state.customizedItems.size)
    }

    @Test
    fun onTemplateSelected_chaotic_addsExtraItems() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))
        vm.onIntent(InteractiveOnboardingIntent.OnStyleSelected(OrganizingStyle.CHAOTIC))
        vm.onIntent(InteractiveOnboardingIntent.OnTemplateSelected(travelTemplate))

        val state = vm.screenState.value
        // Original items + 3 extras
        assertEquals(travelTemplate.items.size + 3, state.customizedItems.size)
        assertTrue(state.customizedItems.any { it.text == "Emergency snack stash" })
    }

    @Test
    fun onTemplateSelected_setsChecklistNameFromTemplate() = runTest {
        val vm = createViewModelAtCustomize()

        assertEquals("Travel Packing", vm.screenState.value.checklistName)
        assertEquals(travelTemplate, vm.screenState.value.selectedTemplate)
    }

    @Test
    fun onTemplateSelected_tracksAnalytics() = runTest {
        val vm = createViewModelAtCustomize()

        val events = fakeAnalyticsTracker.eventsNamed("onboarding_step_completed")
        val templateEvent = events.firstOrNull { it["step"] == "template_selected" }
        assertNotNull(templateEvent)
        assertEquals("travel_packing", templateEvent["template"])
    }

    // --- Customize step ---

    @Test
    fun onToggleItem_togglesIsEnabled() = runTest {
        val vm = createViewModelAtCustomize()

        assertTrue(vm.screenState.value.customizedItems[0].isEnabled)

        vm.onIntent(InteractiveOnboardingIntent.OnToggleItem(0))

        assertFalse(vm.screenState.value.customizedItems[0].isEnabled)
    }

    @Test
    fun onToggleItem_toggleBack() = runTest {
        val vm = createViewModelAtCustomize()

        vm.onIntent(InteractiveOnboardingIntent.OnToggleItem(0))
        assertFalse(vm.screenState.value.customizedItems[0].isEnabled)

        vm.onIntent(InteractiveOnboardingIntent.OnToggleItem(0))
        assertTrue(vm.screenState.value.customizedItems[0].isEnabled)
    }

    @Test
    fun onToggleItem_invalidIndex_noOp() = runTest {
        val vm = createViewModelAtCustomize()
        val itemsBefore = vm.screenState.value.customizedItems

        vm.onIntent(InteractiveOnboardingIntent.OnToggleItem(999))

        assertEquals(itemsBefore, vm.screenState.value.customizedItems)
    }

    @Test
    fun onChecklistNameChanged_updatesName() = runTest {
        val vm = createViewModelAtCustomize()

        vm.onIntent(InteractiveOnboardingIntent.OnChecklistNameChanged("My Trip"))

        assertEquals("My Trip", vm.screenState.value.checklistName)
    }

    @Test
    fun onContinueFromCustomize_advancesToCreating() = runTest {
        val vm = createViewModelAtCustomize()

        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)

        assertEquals(InteractiveOnboardingStep.Creating, vm.screenState.value.currentStep)
    }

    @Test
    fun onContinueFromCustomize_tracksAnalytics() = runTest {
        val vm = createViewModelAtCustomize()

        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)

        val events = fakeAnalyticsTracker.eventsNamed("onboarding_step_completed")
        assertNotNull(events.firstOrNull { it["step"] == "customization_completed" })
    }

    // --- Creating step ---

    @Test
    fun onCreatingComplete_advancesToChecklistPreview() = runTest {
        val vm = createViewModelAtCustomize()
        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)
        assertEquals(InteractiveOnboardingStep.Creating, vm.screenState.value.currentStep)

        vm.onIntent(InteractiveOnboardingIntent.OnCreatingComplete)

        assertEquals(InteractiveOnboardingStep.ChecklistPreview, vm.screenState.value.currentStep)
    }

    // --- Save checklist ---

    @Test
    fun onSaveChecklist_success_createsAndMovesToPaywall() = runTest {
        val vm = createViewModelAtCustomize()
        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)
        vm.onIntent(InteractiveOnboardingIntent.OnCreatingComplete)

        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.Paywall, state.currentStep)
        assertTrue(state.checklistCreated)
        assertFalse(state.isCreatingChecklist)
        assertEquals(1, fakeChecklistRepository.addChecklistCallCount)
    }

    @Test
    fun onSaveChecklist_usesChecklistNameAndEnabledItemsOnly() = runTest {
        val vm = createViewModelAtCustomize()
        // Disable first item, change name
        vm.onIntent(InteractiveOnboardingIntent.OnToggleItem(0))
        vm.onIntent(InteractiveOnboardingIntent.OnChecklistNameChanged("My Custom List"))
        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)
        vm.onIntent(InteractiveOnboardingIntent.OnCreatingComplete)

        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        val saved = assertNotNull(fakeChecklistRepository.lastAddedChecklist)
        assertEquals("My Custom List", saved.name)
        // First item was disabled, so item count = total - 1
        assertEquals(travelTemplate.items.size - 1, saved.items.size)
        // "Passport" (index 0) was disabled, should not be in saved items
        assertFalse(saved.items.any { it.text == "Passport" })
    }

    @Test
    fun onSaveChecklist_blankName_usesTemplateName() = runTest {
        val vm = createViewModelAtCustomize()
        vm.onIntent(InteractiveOnboardingIntent.OnChecklistNameChanged(""))
        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)
        vm.onIntent(InteractiveOnboardingIntent.OnCreatingComplete)

        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        val saved = assertNotNull(fakeChecklistRepository.lastAddedChecklist)
        assertEquals("Travel Packing", saved.name)
    }

    @Test
    fun onSaveChecklist_roomError_proceedsToPaywallWithoutCrash() = runTest {
        fakeChecklistRepository.shouldThrowOnAdd = true
        val vm = createViewModelAtCustomize()
        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)
        vm.onIntent(InteractiveOnboardingIntent.OnCreatingComplete)

        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.Paywall, state.currentStep)
        assertFalse(state.isCreatingChecklist)
        assertFalse(state.checklistCreated)
    }

    @Test
    fun onSaveChecklist_roomError_tracksErrorAnalytics() = runTest {
        fakeChecklistRepository.shouldThrowOnAdd = true
        val vm = createViewModelAtCustomize()
        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)
        vm.onIntent(InteractiveOnboardingIntent.OnCreatingComplete)

        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeAnalyticsTracker.hasEvent("onboarding_checklist_error"))
    }

    @Test
    fun onSaveChecklist_allItemsDisabled_doesNothing() = runTest {
        val vm = createViewModelAtCustomize()
        // Disable all items
        for (i in vm.screenState.value.customizedItems.indices) {
            vm.onIntent(InteractiveOnboardingIntent.OnToggleItem(i))
        }
        assertTrue(vm.screenState.value.customizedItems.all { !it.isEnabled })

        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)
        vm.onIntent(InteractiveOnboardingIntent.OnCreatingComplete)

        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fakeChecklistRepository.addChecklistCallCount)
        assertEquals(InteractiveOnboardingStep.ChecklistPreview, vm.screenState.value.currentStep)
    }

    @Test
    fun onSaveChecklist_success_tracksChecklistCreated() = runTest {
        val vm = createViewModelAtCustomize()
        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)
        vm.onIntent(InteractiveOnboardingIntent.OnCreatingComplete)

        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        val events = fakeAnalyticsTracker.eventsNamed("onboarding_step_completed")
        val createdEvent = events.firstOrNull { it["step"] == "checklist_created" }
        assertNotNull(createdEvent)
        assertEquals("travel_packing", createdEvent["template"])
        assertEquals("DETAILED", createdEvent["style"])
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

        vm.onIntent(InteractiveOnboardingIntent.OnSkip)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            InteractiveOnboardingStep.CategorySelection.name,
            fakeAnalyticsTracker.getEventParam("onboarding_skipped", "step")
        )
    }

    @Test
    fun onSkip_fromLaterStep_includesCorrectStep() = runTest {
        val vm = createViewModelAtCustomize()

        vm.onIntent(InteractiveOnboardingIntent.OnSkip)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            InteractiveOnboardingStep.Customize.name,
            fakeAnalyticsTracker.getEventParam("onboarding_skipped", "step")
        )
    }

    // --- Back navigation ---

    @Test
    fun onBack_fromCategorySelection_isNoOp() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(InteractiveOnboardingStep.CategorySelection, vm.screenState.value.currentStep)

        vm.onIntent(InteractiveOnboardingIntent.OnBack)

        assertEquals(InteractiveOnboardingStep.CategorySelection, vm.screenState.value.currentStep)
    }

    @Test
    fun onBack_fromStyleSelection_returnsToCategoryAndClearsState() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))
        assertEquals(InteractiveOnboardingStep.StyleSelection, vm.screenState.value.currentStep)

        vm.onIntent(InteractiveOnboardingIntent.OnBack)

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.CategorySelection, state.currentStep)
        assertNull(state.selectedCategory)
        assertTrue(state.availableTemplates.isEmpty())
    }

    @Test
    fun onBack_fromTemplateSelection_returnsToStyleAndClearsStyle() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))
        vm.onIntent(InteractiveOnboardingIntent.OnStyleSelected(OrganizingStyle.DETAILED))
        assertEquals(InteractiveOnboardingStep.TemplateSelection, vm.screenState.value.currentStep)

        vm.onIntent(InteractiveOnboardingIntent.OnBack)

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.StyleSelection, state.currentStep)
        assertNull(state.selectedStyle)
    }

    @Test
    fun onBack_fromCustomize_returnsToTemplateAndClearsItems() = runTest {
        val vm = createViewModelAtCustomize()
        assertEquals(InteractiveOnboardingStep.Customize, vm.screenState.value.currentStep)

        vm.onIntent(InteractiveOnboardingIntent.OnBack)

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.TemplateSelection, state.currentStep)
        assertNull(state.selectedTemplate)
        assertTrue(state.customizedItems.isEmpty())
        assertEquals("", state.checklistName)
    }

    @Test
    fun onBack_fromCreating_isNoOp() = runTest {
        val vm = createViewModelAtCustomize()
        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)
        assertEquals(InteractiveOnboardingStep.Creating, vm.screenState.value.currentStep)

        vm.onIntent(InteractiveOnboardingIntent.OnBack)

        assertEquals(InteractiveOnboardingStep.Creating, vm.screenState.value.currentStep)
    }

    @Test
    fun onBack_fromChecklistPreview_returnsToCustomize() = runTest {
        val vm = createViewModelAtCustomize()
        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)
        vm.onIntent(InteractiveOnboardingIntent.OnCreatingComplete)
        assertEquals(InteractiveOnboardingStep.ChecklistPreview, vm.screenState.value.currentStep)

        vm.onIntent(InteractiveOnboardingIntent.OnBack)

        assertEquals(InteractiveOnboardingStep.Customize, vm.screenState.value.currentStep)
    }

    @Test
    fun onBack_fromPaywall_returnsToChecklistPreview() = runTest {
        val vm = createViewModelAtCustomize()
        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)
        vm.onIntent(InteractiveOnboardingIntent.OnCreatingComplete)
        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(InteractiveOnboardingStep.Paywall, vm.screenState.value.currentStep)

        vm.onIntent(InteractiveOnboardingIntent.OnBack)

        assertEquals(InteractiveOnboardingStep.ChecklistPreview, vm.screenState.value.currentStep)
    }

    // --- Full flow ---

    @Test
    fun fullFlow_categoryToPaywall() = runTest {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Step 1: CategorySelection → StyleSelection
        vm.onIntent(InteractiveOnboardingIntent.OnCategorySelected(OnboardingCategory.TRAVEL))
        assertEquals(InteractiveOnboardingStep.StyleSelection, vm.screenState.value.currentStep)

        // Step 2: StyleSelection → TemplateSelection
        vm.onIntent(InteractiveOnboardingIntent.OnStyleSelected(OrganizingStyle.DETAILED))
        assertEquals(InteractiveOnboardingStep.TemplateSelection, vm.screenState.value.currentStep)

        // Step 3: TemplateSelection → Customize
        vm.onIntent(InteractiveOnboardingIntent.OnTemplateSelected(travelTemplate))
        assertEquals(InteractiveOnboardingStep.Customize, vm.screenState.value.currentStep)

        // Step 4: Customize → Creating
        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)
        assertEquals(InteractiveOnboardingStep.Creating, vm.screenState.value.currentStep)

        // Step 5: Creating → ChecklistPreview
        vm.onIntent(InteractiveOnboardingIntent.OnCreatingComplete)
        assertEquals(InteractiveOnboardingStep.ChecklistPreview, vm.screenState.value.currentStep)

        // Step 6: ChecklistPreview → Paywall (save)
        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(InteractiveOnboardingStep.Paywall, vm.screenState.value.currentStep)
        assertTrue(vm.screenState.value.checklistCreated)
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
