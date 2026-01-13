package com.antonchuraev.homesearchchecklist.feature.debug.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
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
    private val checklistRepository: ChecklistRepository
) : AppViewModel<DebugScreenState, DebugScreenIntent, Nothing>() {

    private val _screenState = MutableStateFlow(DebugScreenState())
    override val screenState: StateFlow<DebugScreenState> = _screenState.asStateFlow()

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
        }
    }

    private fun resetOnboarding() {
        viewModelScope.launch {
            userDataRepository.update(UserData(isOnboardingPassed = false))
        }
    }

    private fun clearData() {
        viewModelScope.launch {
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
}
