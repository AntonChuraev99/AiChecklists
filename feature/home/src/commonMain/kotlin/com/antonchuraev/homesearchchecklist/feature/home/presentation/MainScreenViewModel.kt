package com.antonchuraev.homesearchchecklist.feature.home.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetSubscriptionStatusUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.formatExpirationDate
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

class MainScreenViewModel(
    private val repository: ChecklistRepository,
    private val appNavigator: AppNavigator,
    private val getSubscriptionStatusUseCase: GetSubscriptionStatusUseCase,
) : AppViewModel<MainScreenState, MainScreenIntent, Nothing>() {

    override val screenState: StateFlow<MainScreenState> = combine(
        repository.checklists,
        getSubscriptionStatusUseCase()
    ) { checklists, subscriptionStatus ->
        MainScreenState.Success(
            checklists = checklists,
            subscriptionStatus = subscriptionStatus,
            formattedExpirationDate = subscriptionStatus.expirationDate?.let {
                formatExpirationDate(it)
            }
        )
    }.defaultStateIn(MainScreenState.Loading)

    override fun onIntent(intent: MainScreenIntent) {
        when (intent) {
            MainScreenIntent.OnAddChecklistClick -> appNavigator.navigateToCreateChecklistScreen(
                null
            )

            MainScreenIntent.OnAddChecklistFromTemplatesClick -> appNavigator.navigateToTemplatesScreen()
            MainScreenIntent.OnAiAnalyzeClick -> appNavigator.navigateToAnalyzeScreen()
            is MainScreenIntent.OnChecklistClick -> appNavigator.navigateToChecklistDetail(intent.checklist.id)
            MainScreenIntent.OnPremiumBannerClick -> handlePremiumBannerClick()
        }
    }

    private fun handlePremiumBannerClick() {
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


