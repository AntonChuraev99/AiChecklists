package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val navigator: AppNavigator,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val analyticsTracker: AnalyticsTracker,
    private val isDebugBuild: Boolean = false,
) : AppViewModel<OnboardingState, OnboardingIntent, Nothing>() {

    private val _screenState = MutableStateFlow(OnboardingState())
    override val screenState: StateFlow<OnboardingState> = _screenState.asStateFlow()

    init {
        // Skip analytics when launched from the debug menu to avoid polluting
        // production Amplitude data with developer test sessions.
        if (!isDebugBuild) {
            val alreadyTracked = savedStateHandle.get<Boolean>(KEY_STARTED_TRACKED) == true
            if (!alreadyTracked) {
                analyticsTracker.event(AnalyticsEvents.Onboarding.STARTED, mapOf(AnalyticsParams.VARIANT to "slides"))
                savedStateHandle[KEY_STARTED_TRACKED] = true
            }
            // Always track ViewModel creation for diagnostics (helps identify process death)
            analyticsTracker.event(AnalyticsEvents.Onboarding.VM_CREATED, mapOf(
                AnalyticsParams.VARIANT to "slides",
                "is_restored" to alreadyTracked.toString()
            ))
        }
    }

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
        analyticsTracker.event(AnalyticsEvents.Onboarding.PAGE_VIEWED, mapOf(AnalyticsParams.PAGE to page.toString()))
    }

    private fun completeOnboarding() {
        val currentPage = _screenState.value.currentPage
        val totalPages = _screenState.value.totalPages
        val wasSkipped = currentPage < totalPages - 1

        viewModelScope.launch {
            if (!isDebugBuild) {
                if (wasSkipped) {
                    analyticsTracker.event(AnalyticsEvents.Onboarding.SKIPPED, mapOf(
                        AnalyticsParams.VARIANT to "slides",
                        AnalyticsParams.PAGE to currentPage.toString()
                    ))
                }
                analyticsTracker.event(AnalyticsEvents.Onboarding.COMPLETED, mapOf(AnalyticsParams.VARIANT to "slides"))
            }
            completeOnboardingUseCase()
            navigator.navigateToMainScreen(clearBackStack = true)
        }
    }

    companion object {
        private const val KEY_STARTED_TRACKED = "onboarding_started_tracked"
    }
}
