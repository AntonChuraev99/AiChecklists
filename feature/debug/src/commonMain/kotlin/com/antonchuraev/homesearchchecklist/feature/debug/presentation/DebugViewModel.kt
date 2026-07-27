package com.antonchuraev.homesearchchecklist.feature.debug.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.datastore.api.NavExperimentPrefsRepository
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DebugViewModel(
    private val appNavigator: AppNavigator,
    private val userDataRepository: UserDataRepository,
    private val checklistRepository: ChecklistRepository,
    private val navExperimentPrefs: NavExperimentPrefsRepository,
    private val logger: AppLogger
) : AppViewModel<DebugScreenState, DebugScreenIntent, Nothing>() {

    private val _screenState = MutableStateFlow(DebugScreenState())
    override val screenState: StateFlow<DebugScreenState> = _screenState.asStateFlow()

    init {
        loadNavArm()
    }

    override fun onIntent(intent: DebugScreenIntent) {
        when (intent) {
            DebugScreenIntent.OnBackClick -> appNavigator.onBack()
            DebugScreenIntent.ShowInfoDialog -> _screenState.value =
                _screenState.value.copy(showInfoDialog = true)

            DebugScreenIntent.HideInfoDialog -> _screenState.value =
                _screenState.value.copy(showInfoDialog = false)

            DebugScreenIntent.ResetOnboarding -> resetOnboarding()
            DebugScreenIntent.ClearData -> clearData()
            DebugScreenIntent.CreateTestChecklists -> createTestChecklists()
            DebugScreenIntent.OpenStoreScreenshot -> appNavigator.navigateToStoreScreenshot()
            DebugScreenIntent.TestRestoreCredits -> testRestoreCredits()
            DebugScreenIntent.DismissRestoreCreditsResult -> _screenState.value =
                _screenState.value.copy(restoreCreditsResult = null)
            DebugScreenIntent.OpenInteractiveOnboarding -> appNavigator.navigateToInteractiveOnboarding()
            DebugScreenIntent.OpenScreenCatalog -> appNavigator.navigateToScreenCatalog()
            DebugScreenIntent.OpenOnboardings -> appNavigator.navigateToOnboardings()
            DebugScreenIntent.ShowCancelReasonSheet -> _screenState.value =
                _screenState.value.copy(showCancelReasonSheet = true)
            DebugScreenIntent.HideCancelReasonSheet -> _screenState.value =
                _screenState.value.copy(showCancelReasonSheet = false)

            is DebugScreenIntent.SetNavArm -> setNavArm(intent.arm)
        }
    }

    /** Shows what will apply on the NEXT launch — the current process has already latched its arm. */
    private fun loadNavArm() {
        viewModelScope.launch {
            runCatching { navExperimentPrefs.getNavArm() }
                .onSuccess { arm -> _screenState.value = _screenState.value.copy(navArm = arm) }
                .onFailure { logger.error(TAG, "failed to read the persisted nav arm: ${it.message}", it) }
        }
    }

    /**
     * Forces the nav A/B arm, or clears the override when [arm] is null.
     *
     * Writes the value the resolver consults BEFORE Remote Config, which is the only way to reach the
     * v2 shell while no console parameter exists. The empty string is the repository's absent
     * sentinel, so it is how the override is removed.
     */
    private fun setNavArm(arm: String?) {
        viewModelScope.launch {
            runCatching { navExperimentPrefs.setNavArm(arm ?: "") }
                .onSuccess { _screenState.value = _screenState.value.copy(navArm = arm) }
                .onFailure { logger.error(TAG, "failed to write the nav arm '$arm': ${it.message}", it) }
        }
    }

    private companion object {
        private const val TAG = "DebugViewModel"
    }

    private fun resetOnboarding() {
        viewModelScope.launch {
            userDataRepository.update(UserData(isOnboardingPassed = false))
        }
    }

    private fun clearData() {
        viewModelScope.launch {
            // Deliberately the UNFILTERED flow: "clear data" must also delete the v2 arm's hidden
            // system Inbox. Reading `projects` here would leave it (and every task captured in it)
            // behind, so a wiped install would silently resurrect old content.
            val checklists = checklistRepository.checklists.first()
            checklists.forEach { checklist ->
                checklistRepository.deleteChecklist(checklist)
            }
            userDataRepository.update(UserData(isOnboardingPassed = false))
        }
    }

    private fun createTestChecklists() {
        viewModelScope.launch {
            val testChecklists = listOf(
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
                )
            )

            testChecklists.forEach { checklist ->
                checklistRepository.addChecklist(checklist)
            }
        }
    }

    private fun testRestoreCredits() {
        viewModelScope.launch {
            _screenState.value = _screenState.value.copy(isRestoreCreditsLoading = true)

            userDataRepository.restoreCreditsAfterPurchase()
                .onSuccess { credits ->
                    _screenState.value = _screenState.value.copy(
                        isRestoreCreditsLoading = false,
                        restoreCreditsResult = RestoreCreditsResult.Success(credits)
                    )
                }
                .onFailure { error ->
                    _screenState.value = _screenState.value.copy(
                        isRestoreCreditsLoading = false,
                        restoreCreditsResult = RestoreCreditsResult.Error(
                            error.message ?: "Unknown error"
                        )
                    )
                }
        }
    }
}
