package com.antonchuraev.homesearchchecklist.feature.onboarding.di

import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.OnboardingViewModel
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.InteractiveOnboardingViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val onboardingFeatureModule = module {
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::InteractiveOnboardingViewModel)
}

