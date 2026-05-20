package com.antonchuraev.homesearchchecklist.feature.debug.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OnboardingsViewModel(
    private val appNavigator: AppNavigator
) : AppViewModel<OnboardingsState, OnboardingsIntent, Nothing>() {

    private val _screenState = MutableStateFlow(OnboardingsState())
    override val screenState: StateFlow<OnboardingsState> = _screenState.asStateFlow()

    override fun onIntent(intent: OnboardingsIntent) {
        when (intent) {
            is OnboardingsIntent.OnBack -> appNavigator.onBack()
            is OnboardingsIntent.LaunchVariant -> when (intent.variant) {
                OnboardingVariant.Interactive -> appNavigator.navigateToInteractiveOnboarding()
                OnboardingVariant.Slides -> appNavigator.navigateToOnboarding()
            }
        }
    }
}
