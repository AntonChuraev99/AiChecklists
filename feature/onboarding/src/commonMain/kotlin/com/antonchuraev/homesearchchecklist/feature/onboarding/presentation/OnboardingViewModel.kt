package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val navigator: AppNavigator,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase
) : AppViewModel<OnboardingState, OnboardingIntent, Nothing>() {

    private val _screenState = MutableStateFlow(OnboardingState())
    override val screenState: StateFlow<OnboardingState> = _screenState.asStateFlow()

    override fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.OnNextPage -> handleNextPage()
            OnboardingIntent.OnSkip -> completeOnboarding()
            is OnboardingIntent.OnPageSelected -> updatePage(intent.page)
        }
    }

    private fun handleNextPage() {
        val currentState = _screenState.value
        if (currentState.currentPage < currentState.totalPages - 1) {
            _screenState.update { it.copy(currentPage = it.currentPage + 1) }
        } else {
            completeOnboarding()
        }
    }

    private fun updatePage(page: Int) {
        _screenState.update { it.copy(currentPage = page) }
    }

    private fun completeOnboarding() {
        viewModelScope.launch {
            completeOnboardingUseCase()
            navigator.navigateToMainScreen(clearBackStack = true)
        }
    }
}
