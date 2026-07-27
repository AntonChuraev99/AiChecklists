package com.antonchuraev.homesearchchecklist.feature.debug.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScreenCatalogViewModel(
    private val appNavigator: AppNavigator,
    private val userDataRepository: UserDataRepository,
    private val checklistRepository: ChecklistRepository
) : AppViewModel<ScreenCatalogState, ScreenCatalogIntent, Nothing>() {

    private val _screenState = MutableStateFlow(ScreenCatalogState())
    override val screenState: StateFlow<ScreenCatalogState> = _screenState.asStateFlow()

    // Stable template ID from the create feature defaults (used for TemplatePreview navigation).
    // If this ID does not exist in the current Remote Config payload the preview will show an
    // empty/error state — that is acceptable for a debug-only screen.
    private val debugTemplateId = "daily_tasks"

    init {
        viewModelScope.launch {
            val userData = userDataRepository.getUserData()
            // .projects, not .checklists: the displayed count must match what the free-tier gate and
            // the Home list count, and the system Inbox is excluded from both. Reading the
            // unfiltered stream reported N+1 for every v2-arm user and made this debug readout
            // disagree with the app while diagnosing the nav experiment.
            val projects = checklistRepository.projects.first()
            _screenState.value = _screenState.value.copy(
                isPremium = userData.isPremium,
                aiCredits = userData.aiCredits,
                checklistCount = projects.size
            )
        }
    }

    override fun onIntent(intent: ScreenCatalogIntent) {
        when (intent) {
            ScreenCatalogIntent.ResetToEmpty -> resetToEmpty()
            ScreenCatalogIntent.SeedWithData -> seedWithData(checklistCount = 3, isPremium = false, aiCredits = 10)
            ScreenCatalogIntent.SeedWithFreeLimit -> seedWithData(checklistCount = 4, isPremium = false, aiCredits = 10)
            ScreenCatalogIntent.SeedAsPremium -> seedWithData(checklistCount = 3, isPremium = true, aiCredits = 300)

            ScreenCatalogIntent.NavigateOnboarding -> appNavigator.navigateToOnboarding()
            ScreenCatalogIntent.NavigateInteractiveOnboarding -> appNavigator.navigateToInteractiveOnboarding()
            ScreenCatalogIntent.NavigateMain -> appNavigator.navigateToMainScreen()

            ScreenCatalogIntent.NavigateTemplates -> appNavigator.navigateToTemplatesScreen()
            ScreenCatalogIntent.NavigateTemplatePreview -> appNavigator.navigateToTemplatePreview(debugTemplateId)
            ScreenCatalogIntent.NavigateCreateNew -> appNavigator.navigateToCreateChecklistScreen(templateId = null)
            ScreenCatalogIntent.NavigateCreateEdit -> {
                val id = _screenState.value.lastSeededChecklistId ?: return
                appNavigator.navigateToEditChecklist(id)
            }

            ScreenCatalogIntent.NavigateChecklistDetail -> {
                val id = _screenState.value.lastSeededChecklistId ?: return
                appNavigator.navigateToChecklistDetail(id)
            }
            ScreenCatalogIntent.NavigateFillDetail -> {
                val id = _screenState.value.lastSeededFillId ?: return
                appNavigator.navigateToFillDetail(id)
            }
            ScreenCatalogIntent.NavigateFillsList -> {
                val id = _screenState.value.lastSeededChecklistId ?: return
                appNavigator.navigateToFillsList(id)
            }
            ScreenCatalogIntent.NavigateShareChecklist -> {
                val id = _screenState.value.lastSeededChecklistId ?: return
                appNavigator.navigateToShareChecklist(id)
            }

            ScreenCatalogIntent.NavigateAnalyzeEmpty -> appNavigator.navigateToAnalyzeScreen(checklistId = null)
            ScreenCatalogIntent.NavigateAnalyzeForChecklist -> {
                val id = _screenState.value.lastSeededChecklistId ?: return
                appNavigator.navigateToAnalyzeScreen(checklistId = id)
            }
            // AnalyzeResultPreview renders whatever ViewModel state happens to be (empty state preview)
            ScreenCatalogIntent.NavigateAnalyzeResultPreview -> appNavigator.navigateToAnalyzeResultPreview()

            ScreenCatalogIntent.NavigatePaywall -> appNavigator.navigateToPaywall(source = "screen_catalog")
            ScreenCatalogIntent.NavigatePaywallTimeline -> appNavigator.navigateToPaywallVariant(source = "debug", forceVariant = "timeline")
            ScreenCatalogIntent.NavigatePaywallFeatures -> appNavigator.navigateToPaywallVariant(source = "debug", forceVariant = "features")
            ScreenCatalogIntent.NavigatePaywallCompare  -> appNavigator.navigateToPaywallVariant(source = "debug", forceVariant = "compare")
            ScreenCatalogIntent.NavigateSubscriptionStatusSuccess -> appNavigator.navigateToSubscriptionStatus(showSuccessMessage = true)
            ScreenCatalogIntent.NavigateSubscriptionStatusPending -> appNavigator.navigateToSubscriptionStatus(showSuccessMessage = false)

            ScreenCatalogIntent.NavigateSettings -> appNavigator.navigateToSettings()
            ScreenCatalogIntent.NavigateUpdateFeed -> appNavigator.navigateToUpdateFeed()
            ScreenCatalogIntent.NavigateStoreScreenshot -> appNavigator.navigateToStoreScreenshot()

            ScreenCatalogIntent.OnBack -> appNavigator.onBack()
        }
    }

    private fun resetToEmpty() {
        viewModelScope.launch {
            _screenState.value = _screenState.value.copy(isWorking = true)
            clearAllChecklists()
            userDataRepository.update(
                UserData(isOnboardingPassed = true, isPremium = false, aiCredits = 10)
            )
            _screenState.value = _screenState.value.copy(
                isWorking = false,
                seedSummary = "Seeded 0 checklists, premium=false, credits=10",
                isPremium = false,
                aiCredits = 10,
                checklistCount = 0,
                lastSeededChecklistId = null,
                lastSeededFillId = null
            )
        }
    }

    private fun seedWithData(checklistCount: Int, isPremium: Boolean, aiCredits: Int) {
        viewModelScope.launch {
            _screenState.value = _screenState.value.copy(isWorking = true)
            clearAllChecklists()

            val demoChecklists = buildDemoChecklists().take(checklistCount)
            var firstChecklistId: Long? = null
            var firstFillId: Long? = null

            demoChecklists.forEachIndexed { index, checklist ->
                val id = checklistRepository.addChecklist(checklist)
                if (index == 0) {
                    firstChecklistId = id
                    // The default fill is created automatically by the repository; retrieve it.
                    val fill = checklistRepository.getDefaultFillOneShot(id)
                    firstFillId = fill?.id
                }
            }

            userDataRepository.update(
                UserData(isOnboardingPassed = true, isPremium = isPremium, aiCredits = aiCredits)
            )

            _screenState.value = _screenState.value.copy(
                isWorking = false,
                seedSummary = "Seeded $checklistCount checklists, premium=$isPremium, credits=$aiCredits",
                isPremium = isPremium,
                aiCredits = aiCredits,
                checklistCount = checklistCount,
                lastSeededChecklistId = firstChecklistId,
                lastSeededFillId = firstFillId
            )
        }
    }

    /**
     * Deliberately reads the UNFILTERED [ChecklistRepository.checklists], unlike the count above:
     * this is the destructive "wipe everything" helper behind Reset-to-empty and every Seed action,
     * and it must delete the hidden system Inbox too. On `.projects` the Inbox would survive a
     * "Seeded 0 checklists" reset, `ensureInbox` would silently reuse the stale row on the next
     * Inbox entry, and the debug screen would report a state the database does not have — the exact
     * confusion this readout exists to remove. Same reasoning as `DebugViewModel.clearData()`.
     */
    private suspend fun clearAllChecklists() {
        val existing = checklistRepository.checklists.first()
        existing.forEach { checklistRepository.deleteChecklist(it) }
    }

    private fun buildDemoChecklists(): List<Checklist> = listOf(
        Checklist(
            name = "Shopping List",
            items = listOf(
                ChecklistItem("Milk", false),
                ChecklistItem("Bread", true),
                ChecklistItem("Eggs", false),
                ChecklistItem("Butter", true),
                ChecklistItem("Cheese", false)
            )
        ),
        Checklist(
            name = "Project Tasks",
            items = listOf(
                ChecklistItem("Design mockups", true),
                ChecklistItem("Implement UI", true),
                ChecklistItem("Write tests", false),
                ChecklistItem("Deploy to production", false)
            )
        ),
        Checklist(
            name = "Daily Routine",
            items = listOf(
                ChecklistItem("Morning exercise", false),
                ChecklistItem("Read for 30 minutes", false),
                ChecklistItem("Review emails", true),
                ChecklistItem("Team standup", true),
                ChecklistItem("Deep work session", false),
                ChecklistItem("Plan tomorrow", false)
            )
        ),
        Checklist(
            name = "Travel Packing",
            items = listOf(
                ChecklistItem("Passport", false),
                ChecklistItem("Phone charger", false),
                ChecklistItem("Headphones", false),
                ChecklistItem("Toiletries", false)
            )
        )
    )
}
