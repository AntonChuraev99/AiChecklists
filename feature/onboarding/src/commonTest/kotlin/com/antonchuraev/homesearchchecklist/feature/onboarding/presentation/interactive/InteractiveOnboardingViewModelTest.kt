package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.navigation.api.NavCommand
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import com.antonchuraev.homesearchchecklist.feature.sharing.domain.formatter.ChecklistFormatter
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.TemplateCategory
import com.antonchuraev.homesearchchecklist.feature.create.domain.repository.TemplatesRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
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
    private lateinit var fakeReminderScheduler: FakeReminderScheduler
    private val checklistFormatter = ChecklistFormatter()

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
        fakeReminderScheduler = FakeReminderScheduler()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        templatesRepository: FakeTemplatesRepository = fakeTemplatesRepository
    ): InteractiveOnboardingViewModel {
        return InteractiveOnboardingViewModel(
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
            navigator = fakeNavigator,
            completeOnboardingUseCase = CompleteOnboardingUseCase(fakeUserDataRepository),
            templatesRepository = templatesRepository,
            checklistRepository = fakeChecklistRepository,
            analyticsTracker = fakeAnalyticsTracker,
            reminderScheduler = fakeReminderScheduler,
            checklistFormatter = checklistFormatter
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

    // --- Preview step ---

    /** Helper: navigate to ChecklistPreview step */
    private fun createViewModelAtPreview(
        category: OnboardingCategory = OnboardingCategory.TRAVEL,
        style: OrganizingStyle = OrganizingStyle.DETAILED,
        template: ChecklistTemplate = travelTemplate
    ): InteractiveOnboardingViewModel {
        val vm = createViewModelAtCustomize(category, style, template)
        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)
        vm.onIntent(InteractiveOnboardingIntent.OnCreatingComplete)
        assertEquals(InteractiveOnboardingStep.ChecklistPreview, vm.screenState.value.currentStep)
        return vm
    }

    @Test
    fun onCreatingComplete_initializesPreviewState() = runTest {
        val vm = createViewModelAtPreview()

        val state = vm.screenState.value
        val preview = state.preview
        // All enabled items from customize become preview items
        val enabledCount = state.customizedItems.count { it.isEnabled }
        assertEquals(enabledCount, preview.items.size)
        assertEquals(enabledCount, preview.originalItemCount)
        // All items start unchecked
        assertTrue(preview.items.all { !it.isChecked })
        // IDs are stable and unique
        assertEquals(preview.items.map { it.id }.toSet().size, preview.items.size)
    }

    @Test
    fun onCreatingComplete_disabledItems_excludedFromPreview() = runTest {
        val vm = createViewModelAtCustomize()
        // Disable first item ("Passport")
        vm.onIntent(InteractiveOnboardingIntent.OnToggleItem(0))
        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)
        vm.onIntent(InteractiveOnboardingIntent.OnCreatingComplete)

        val preview = vm.screenState.value.preview
        assertFalse(preview.items.any { it.text == "Passport" })
        assertEquals(travelTemplate.items.size - 1, preview.items.size)
    }

    @Test
    fun onPreviewItemToggle_togglesItemCheckedState() = runTest {
        val vm = createViewModelAtPreview()
        val itemId = vm.screenState.value.preview.items.first().id

        vm.onIntent(InteractiveOnboardingIntent.OnPreviewItemToggle(itemId))

        val toggled = vm.screenState.value.preview.items.first()
        assertTrue(toggled.isChecked)

        // Toggle back
        vm.onIntent(InteractiveOnboardingIntent.OnPreviewItemToggle(itemId))

        val unToggled = vm.screenState.value.preview.items.first()
        assertFalse(unToggled.isChecked)
    }

    @Test
    fun onPreviewItemToggle_autoDeleteCompleted_removesItem() = runTest {
        val vm = createViewModelAtCustomize()
        vm.onIntent(InteractiveOnboardingIntent.OnToggleAutoDeleteCompleted)
        assertTrue(vm.screenState.value.autoDeleteCompleted)
        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)
        vm.onIntent(InteractiveOnboardingIntent.OnCreatingComplete)

        val originalCount = vm.screenState.value.preview.items.size
        val itemId = vm.screenState.value.preview.items.first().id

        vm.onIntent(InteractiveOnboardingIntent.OnPreviewItemToggle(itemId))

        val afterToggle = vm.screenState.value.preview
        assertEquals(originalCount - 1, afterToggle.items.size)
        assertFalse(afterToggle.items.any { it.id == itemId })
        // originalItemCount stays the same
        assertEquals(originalCount, afterToggle.originalItemCount)
    }

    @Test
    fun onPreviewItemToggle_withoutAutoDelete_preservesItemCount() = runTest {
        val vm = createViewModelAtPreview()
        val originalCount = vm.screenState.value.preview.items.size
        val itemId = vm.screenState.value.preview.items.first().id

        vm.onIntent(InteractiveOnboardingIntent.OnPreviewItemToggle(itemId))

        assertEquals(originalCount, vm.screenState.value.preview.items.size)
    }

    @Test
    fun onPreviewItemToggle_invalidId_isNoOp() = runTest {
        val vm = createViewModelAtPreview()
        val beforeState = vm.screenState.value.preview

        vm.onIntent(InteractiveOnboardingIntent.OnPreviewItemToggle("nonexistent_id"))

        assertEquals(beforeState, vm.screenState.value.preview)
    }

    @Test
    fun onPreviewItemToggle_tracksAnalytics() = runTest {
        val vm = createViewModelAtPreview()
        val itemId = vm.screenState.value.preview.items.first().id

        vm.onIntent(InteractiveOnboardingIntent.OnPreviewItemToggle(itemId))

        assertTrue(fakeAnalyticsTracker.hasEvent("onboarding_preview_item_toggled"))
    }

    @Test
    fun onBack_fromChecklistPreview_resetsPreviewState() = runTest {
        val vm = createViewModelAtPreview()
        val itemId = vm.screenState.value.preview.items.first().id
        vm.onIntent(InteractiveOnboardingIntent.OnPreviewItemToggle(itemId))
        assertTrue(vm.screenState.value.preview.items.any { it.isChecked })

        vm.onIntent(InteractiveOnboardingIntent.OnBack)

        assertEquals(InteractiveOnboardingStep.Customize, vm.screenState.value.currentStep)
        // Preview state fully reset
        assertTrue(vm.screenState.value.preview.items.isEmpty())
        assertEquals(0, vm.screenState.value.preview.originalItemCount)
    }

    @Test
    fun onSaveChecklist_usesCustomizedItemsNotPreview() = runTest {
        val vm = createViewModelAtPreview()
        // Check off some items in preview
        val firstId = vm.screenState.value.preview.items.first().id
        vm.onIntent(InteractiveOnboardingIntent.OnPreviewItemToggle(firstId))

        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        // Saved checklist should have ALL enabled items (not affected by preview checks)
        val saved = assertNotNull(fakeChecklistRepository.lastAddedChecklist)
        assertEquals(travelTemplate.items.size, saved.items.size)
    }

    // --- Save checklist ---

    @Test
    fun onSaveChecklist_success_createsAndMovesToDiscoverMore() = runTest {
        val vm = createViewModelAtCustomize()
        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)
        vm.onIntent(InteractiveOnboardingIntent.OnCreatingComplete)

        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.screenState.value
        assertEquals(InteractiveOnboardingStep.DiscoverMore, state.currentStep)
        assertTrue(state.checklistCreated)
        assertFalse(state.isCreatingChecklist)
        assertNotNull(state.createdChecklistId)
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
    fun onBack_fromPaywall_returnsToDiscoverMore() = runTest {
        val vm = createViewModelAtCustomize()
        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)
        vm.onIntent(InteractiveOnboardingIntent.OnCreatingComplete)
        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()
        // Now at DiscoverMore, advance to Paywall
        vm.onIntent(InteractiveOnboardingIntent.OnDiscoverMoreContinue)

        assertEquals(InteractiveOnboardingStep.Paywall, vm.screenState.value.currentStep)

        vm.onIntent(InteractiveOnboardingIntent.OnBack)

        assertEquals(InteractiveOnboardingStep.DiscoverMore, vm.screenState.value.currentStep)
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

        // Step 6: ChecklistPreview → DiscoverMore (save)
        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(InteractiveOnboardingStep.DiscoverMore, vm.screenState.value.currentStep)
        assertTrue(vm.screenState.value.checklistCreated)

        // Step 7: DiscoverMore → Paywall
        vm.onIntent(InteractiveOnboardingIntent.OnDiscoverMoreContinue)
        assertEquals(InteractiveOnboardingStep.Paywall, vm.screenState.value.currentStep)
    }

    // --- Discover More step ---

    /** Helper: get VM to DiscoverMore step (save checklist from customize) */
    private fun createViewModelAtDiscoverMore(): InteractiveOnboardingViewModel {
        val vm = createViewModelAtCustomize()
        vm.onIntent(InteractiveOnboardingIntent.OnContinueFromCustomize)
        vm.onIntent(InteractiveOnboardingIntent.OnCreatingComplete)
        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(InteractiveOnboardingStep.DiscoverMore, vm.screenState.value.currentStep)
        return vm
    }

    @Test
    fun onDiscoverMoreContinue_advancesToPaywall() = runTest {
        val vm = createViewModelAtDiscoverMore()

        vm.onIntent(InteractiveOnboardingIntent.OnDiscoverMoreContinue)

        assertEquals(InteractiveOnboardingStep.Paywall, vm.screenState.value.currentStep)
    }

    @Test
    fun onDiscoverMoreContinue_tracksCompletedActionsCount() = runTest {
        val vm = createViewModelAtDiscoverMore()
        // Complete widget action
        vm.onIntent(InteractiveOnboardingIntent.OnWidgetInstructionDone)

        vm.onIntent(InteractiveOnboardingIntent.OnDiscoverMoreContinue)

        val events = fakeAnalyticsTracker.eventsNamed("onboarding_step_completed")
        val discoverEvent = events.firstOrNull { it["step"] == "discover_more_completed" }
        assertNotNull(discoverEvent)
        assertEquals("1", discoverEvent["actions_done"])
    }

    @Test
    fun onSkip_fromDiscoverMore_advancesToPaywall() = runTest {
        val vm = createViewModelAtDiscoverMore()

        vm.onIntent(InteractiveOnboardingIntent.OnSkip)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(InteractiveOnboardingStep.Paywall, vm.screenState.value.currentStep)
        // Should NOT complete onboarding (skip from DiscoverMore goes to Paywall, not exit)
        assertFalse(fakeUserDataRepository.onboardingMarkedPassed)
    }

    @Test
    fun onBack_fromDiscoverMore_isNoOp() = runTest {
        val vm = createViewModelAtDiscoverMore()

        vm.onIntent(InteractiveOnboardingIntent.OnBack)

        assertEquals(InteractiveOnboardingStep.DiscoverMore, vm.screenState.value.currentStep)
    }

    @Test
    fun onWidgetInstructionDone_marksWidgetCompleted() = runTest {
        val vm = createViewModelAtDiscoverMore()

        vm.onIntent(InteractiveOnboardingIntent.OnWidgetInstructionDone)

        assertTrue(vm.screenState.value.discoverMore.widgetCompleted)
        assertFalse(vm.screenState.value.discoverMore.reminderCompleted)
        assertFalse(vm.screenState.value.discoverMore.shareCompleted)
    }

    @Test
    fun onShareCompleted_marksShareCompleted() = runTest {
        val vm = createViewModelAtDiscoverMore()

        vm.onIntent(InteractiveOnboardingIntent.OnShareCompleted)

        assertTrue(vm.screenState.value.discoverMore.shareCompleted)
        assertFalse(vm.screenState.value.discoverMore.reminderCompleted)
        assertFalse(vm.screenState.value.discoverMore.widgetCompleted)
    }

    @Test
    fun onReminderPresetSelected_marksReminderCompleted() = runTest {
        val vm = createViewModelAtDiscoverMore()
        val futureMillis = com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis() + 3_600_000L

        vm.onIntent(InteractiveOnboardingIntent.OnReminderPresetSelected(futureMillis))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.screenState.value.discoverMore.reminderCompleted)
    }

    @Test
    fun onReminderPresetSelected_schedulesOneShot() = runTest {
        val vm = createViewModelAtDiscoverMore()
        val futureMillis = com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis() + 3_600_000L

        vm.onIntent(InteractiveOnboardingIntent.OnReminderPresetSelected(futureMillis))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeReminderScheduler.scheduleReminderCalls.size)
        assertEquals(0, fakeReminderScheduler.scheduleRepeatCalls.size)
    }

    @Test
    fun onSaveRepeatSchedule_schedulesRepeat() = runTest {
        val vm = createViewModelAtDiscoverMore()
        // Switch to REPEAT tab to init pendingRepeatConfig
        vm.onIntent(InteractiveOnboardingIntent.OnReminderTabSelected(
            com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab.REPEAT
        ))
        vm.onIntent(InteractiveOnboardingIntent.OnSaveRepeatSchedule)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fakeReminderScheduler.scheduleReminderCalls.size)
        assertEquals(1, fakeReminderScheduler.scheduleRepeatCalls.size)
        assertTrue(vm.screenState.value.discoverMore.reminderCompleted)
    }

    @Test
    fun onReminderPresetSelected_noChecklistId_isNoOp() = runTest {
        val vm = createViewModelAtCustomize()
        val futureMillis = com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis() + 3_600_000L

        vm.onIntent(InteractiveOnboardingIntent.OnReminderPresetSelected(futureMillis))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fakeReminderScheduler.scheduleReminderCalls.size)
        assertFalse(vm.screenState.value.discoverMore.reminderCompleted)
    }

    @Test
    fun onSaveChecklist_duplicate_isGuarded() = runTest {
        val vm = createViewModelAtDiscoverMore()
        // Already saved, try saving again
        vm.onIntent(InteractiveOnboardingIntent.OnSaveChecklist)
        testDispatcher.scheduler.advanceUntilIdle()

        // Should still be 1 call, not 2
        assertEquals(1, fakeChecklistRepository.addChecklistCallCount)
    }

    @Test
    fun onWidgetInstructionDone_tracksAnalytics() = runTest {
        val vm = createViewModelAtDiscoverMore()

        vm.onIntent(InteractiveOnboardingIntent.OnWidgetInstructionDone)

        val events = fakeAnalyticsTracker.eventsNamed("onboarding_step_completed")
        assertNotNull(events.firstOrNull { it["step"] == "discover_more_widget" })
    }

    @Test
    fun onShareCompleted_tracksAnalytics() = runTest {
        val vm = createViewModelAtDiscoverMore()

        vm.onIntent(InteractiveOnboardingIntent.OnShareCompleted)

        val events = fakeAnalyticsTracker.eventsNamed("onboarding_step_completed")
        assertNotNull(events.firstOrNull { it["step"] == "discover_more_share" })
    }

    @Test
    fun onReminderPresetSelected_tracksAnalytics() = runTest {
        val vm = createViewModelAtDiscoverMore()
        val futureMillis = com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis() + 3_600_000L

        vm.onIntent(InteractiveOnboardingIntent.OnReminderPresetSelected(futureMillis))
        testDispatcher.scheduler.advanceUntilIdle()

        val events = fakeAnalyticsTracker.eventsNamed("onboarding_step_completed")
        val reminderEvent = events.firstOrNull { it["step"] == "discover_more_reminder" }
        assertNotNull(reminderEvent)
        assertEquals("once", reminderEvent["type"])
    }

    // ── Mutual exclusion: separateCompleted vs autoDeleteCompleted ──

    @Test
    fun toggleSeparateCompleted_disablesAutoDelete() = runTest {
        val vm = createViewModelAtCustomize()
        vm.onIntent(InteractiveOnboardingIntent.OnToggleAutoDeleteCompleted)
        assertTrue(vm.screenState.value.autoDeleteCompleted)

        vm.onIntent(InteractiveOnboardingIntent.OnToggleSeparateCompleted)

        assertTrue(vm.screenState.value.separateCompleted)
        assertFalse(vm.screenState.value.autoDeleteCompleted)
    }

    @Test
    fun toggleAutoDeleteCompleted_disablesSeparateCompleted() = runTest {
        val vm = createViewModelAtCustomize()
        vm.onIntent(InteractiveOnboardingIntent.OnToggleSeparateCompleted)
        assertTrue(vm.screenState.value.separateCompleted)

        vm.onIntent(InteractiveOnboardingIntent.OnToggleAutoDeleteCompleted)

        assertTrue(vm.screenState.value.autoDeleteCompleted)
        assertFalse(vm.screenState.value.separateCompleted)
    }

    @Test
    fun toggleSeparateCompletedOff_doesNotReenableAutoDelete() = runTest {
        val vm = createViewModelAtCustomize()
        // Both are false initially. Turn separate on, then off.
        vm.onIntent(InteractiveOnboardingIntent.OnToggleSeparateCompleted)
        vm.onIntent(InteractiveOnboardingIntent.OnToggleSeparateCompleted)

        assertFalse(vm.screenState.value.separateCompleted)
        assertFalse(vm.screenState.value.autoDeleteCompleted)
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

    private class FakeReminderScheduler : ChecklistReminderScheduler {
        data class ReminderCall(val checklistId: Long, val triggerAtMillis: Long)
        data class RepeatCall(val checklistId: Long, val triggerAtMillis: Long)

        val scheduleReminderCalls = mutableListOf<ReminderCall>()
        val scheduleRepeatCalls = mutableListOf<RepeatCall>()

        override fun scheduleReminder(checklistId: Long, triggerAtMillis: Long) {
            scheduleReminderCalls.add(ReminderCall(checklistId, triggerAtMillis))
        }
        override fun cancelReminder(checklistId: Long) {}
        override suspend fun rescheduleAllActiveReminders() {}
        override fun scheduleRepeat(checklistId: Long, triggerAtMillis: Long) {
            scheduleRepeatCalls.add(RepeatCall(checklistId, triggerAtMillis))
        }
        override fun cancelRepeat(checklistId: Long) {}
        override suspend fun rescheduleAllActiveRepeats() {}
        override fun scheduleItemReminder(checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long) {}
        override fun cancelItemReminder(checklistId: Long, fillId: Long, itemId: String) {}
        override fun scheduleItemRepeat(checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long) {}
        override fun cancelItemRepeat(checklistId: Long, fillId: Long, itemId: String) {}
    }

    private class FakeChecklistRepository : ChecklistRepository {
        var shouldThrowOnAdd = false
        var addChecklistCallCount = 0
        var lastAddedChecklist: Checklist? = null
        private var savedChecklistId: Long? = null

        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())

        override suspend fun addChecklist(checklist: Checklist): Long {
            addChecklistCallCount++
            if (shouldThrowOnAdd) throw RuntimeException("Room write error")
            lastAddedChecklist = checklist
            savedChecklistId = 1L
            return 1L
        }

        override suspend fun updateChecklist(checklist: Checklist) {}
        override suspend fun updateChecklistTemplate(checklist: Checklist) {}
        override suspend fun deleteChecklist(checklist: Checklist) {}

        override suspend fun getChecklistById(id: Long): Checklist? {
            // Return saved checklist after addChecklist was called
            return if (id == savedChecklistId) lastAddedChecklist?.copy(id = id) else null
        }
        override fun observeChecklistById(id: Long): Flow<Checklist?> =
            flowOf(if (id == savedChecklistId) lastAddedChecklist?.copy(id = id) else null)
        override suspend fun reorderChecklists(orderedIds: List<Long>) {}
        override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setReminder(checklistId: Long, reminderAt: Long?) {}
        override suspend fun countActiveReminders(): Int = 0
        override suspend fun getActiveReminders(): List<ChecklistReminderInfo> = emptyList()
        override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? {
            // Return a default fill matching the saved checklist
            val checklist = lastAddedChecklist ?: return null
            if (checklistId != savedChecklistId) return null
            return ChecklistFill(
                id = 1L,
                checklistId = checklistId,
                name = "",
                items = checklist.items.map { ChecklistFillItem(text = it.text, checked = false) },
                isDefault = true
            )
        }
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
        override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo> = emptyList()
        override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addAttachment(fillId: Long, itemId: String, attachment: com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment) = Unit
        override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) = Unit
        override suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getTotalAdditionalFillCount(): Int = 0
        override suspend fun getWeeklyChecklistCount(): Int = 0
        override val weeklyChecklistCount: Flow<Int> = flowOf(0)
        override suspend fun getAllItemRemindersForRescheduling(): List<ItemReminderInfo> = emptyList()
    }

    private class FakeAppNavigator : AppNavigator {
        var navigatedToMainScreen = false
        var lastMainClearBackStack = false

        override val commands: Flow<NavCommand> = emptyFlow()
        override val events: SharedFlow<AppNavEvent> = MutableSharedFlow()
        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
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
        override fun navigateToChecklistDetail(checklistId: Long, focusItemId: String?, clearBackStack: Boolean) {}
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
