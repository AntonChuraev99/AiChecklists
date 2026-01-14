package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State

data class OnboardingState(
    val currentPage: Int = 0,
    val totalPages: Int = 3
) : State

sealed interface OnboardingIntent : Intent {
    data object OnNextPage : OnboardingIntent
    data object OnSkip : OnboardingIntent
    data class OnPageSelected(val page: Int) : OnboardingIntent
}
