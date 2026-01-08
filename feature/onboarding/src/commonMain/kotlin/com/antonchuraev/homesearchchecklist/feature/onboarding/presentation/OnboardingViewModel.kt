package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class OnboardingViewModel(
    private val navigator: AppNavigator
) : AppViewModel<OnboardingState, OnboardingIntent, Nothing>() {

    override val screenState: StateFlow<OnboardingState>
        get() = MutableStateFlow(OnboardingState)

    override fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.OnComplete -> navigator.navigateToMainScreen()
        }
    }
}

