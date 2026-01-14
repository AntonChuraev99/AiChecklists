package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class OnboardingViewModel(
    private val navigator: AppNavigator
) : AppViewModel<OnboardingState, OnboardingIntent, Nothing>() {

    private val _screenState = MutableStateFlow(OnboardingState())
    override val screenState: StateFlow<OnboardingState> = _screenState.asStateFlow()

    override fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.OnNextPage -> handleNextPage()
            OnboardingIntent.OnSkip -> navigateToMain()
            is OnboardingIntent.OnPageSelected -> updatePage(intent.page)
        }
    }

    private fun handleNextPage() {
        val currentState = _screenState.value
        if (currentState.currentPage < currentState.totalPages - 1) {
            _screenState.update { it.copy(currentPage = it.currentPage + 1) }
        } else {
            navigateToMain()
        }
    }

    private fun updatePage(page: Int) {
        _screenState.update { it.copy(currentPage = page) }
    }

    private fun navigateToMain() {
        navigator.navigateToMainScreen()
    }
}
