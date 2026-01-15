package com.antonchuraev.homesearchchecklist.feature.home.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetSubscriptionStatusUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.formatExpirationDate
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class MainScreenViewModel(
    private val repository: ChecklistRepository,
    private val appNavigator: AppNavigator,
    private val getSubscriptionStatusUseCase: GetSubscriptionStatusUseCase,
    private val userDataRepository: UserDataRepository,
    private val getUserLimitsUseCase: GetUserLimitsUseCase,
) : AppViewModel<MainScreenState, MainScreenIntent, Nothing>() {

    private val _showLimitDialog = MutableStateFlow(false)

    override val screenState: StateFlow<MainScreenState> = combine(
        repository.checklists,
        getSubscriptionStatusUseCase(),
        userDataRepository.getUserDataFlow().map { it.aiCredits },
        getUserLimitsUseCase(),
        _showLimitDialog
    ) { checklists, subscriptionStatus, aiCredits, userLimits, showLimitDialog ->
        MainScreenState.Success(
            checklists = checklists,
            subscriptionStatus = subscriptionStatus,
            formattedExpirationDate = subscriptionStatus.expirationDate?.let {
                formatExpirationDate(it)
            },
            aiCredits = aiCredits,
            userLimits = userLimits,
            showLimitReachedDialog = showLimitDialog
        )
    }.defaultStateIn(MainScreenState.Loading)

    override fun onIntent(intent: MainScreenIntent) {
        when (intent) {
            MainScreenIntent.OnAddChecklistClick -> handleAddChecklistClick()
            MainScreenIntent.OnAddChecklistFromTemplatesClick -> handleAddChecklistFromTemplatesClick()
            MainScreenIntent.OnAiAnalyzeClick -> appNavigator.navigateToAnalyzeScreen()
            is MainScreenIntent.OnChecklistClick -> appNavigator.navigateToChecklistDetail(intent.checklist.id)
            MainScreenIntent.OnPremiumBannerClick -> handlePremiumOrCreditsClick()
            MainScreenIntent.OnCreditsClick -> handlePremiumOrCreditsClick()
            MainScreenIntent.OnDismissLimitDialog -> _showLimitDialog.update { false }
            MainScreenIntent.OnUpgradeToPremiumClick -> {
                _showLimitDialog.update { false }
                appNavigator.navigateToPaywall()
            }
        }
    }

    private fun handleAddChecklistClick() {
        val currentState = screenState.value
        if (currentState is MainScreenState.Success) {
            val limits = currentState.userLimits
            if (limits != null && !limits.canCreateChecklist) {
                _showLimitDialog.update { true }
            } else {
                // Navigate to Templates screen where user can choose template,
                // create manually, or create with AI
                appNavigator.navigateToTemplatesScreen()
            }
        }
    }

    private fun handleAddChecklistFromTemplatesClick() {
        val currentState = screenState.value
        if (currentState is MainScreenState.Success) {
            val limits = currentState.userLimits
            if (limits != null && !limits.canCreateChecklist) {
                _showLimitDialog.update { true }
            } else {
                appNavigator.navigateToTemplatesScreen()
            }
        }
    }

    private fun handlePremiumOrCreditsClick() {
        val currentState = screenState.value
        if (currentState is MainScreenState.Success) {
            if (currentState.subscriptionStatus.isActive) {
                appNavigator.navigateToSubscriptionStatus()
            } else {
                appNavigator.navigateToPaywall()
            }
        }
    }
}


