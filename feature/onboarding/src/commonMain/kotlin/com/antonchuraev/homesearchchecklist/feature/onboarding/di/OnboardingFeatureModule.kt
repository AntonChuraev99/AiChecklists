package com.antonchuraev.homesearchchecklist.feature.onboarding.di

import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.OnboardingViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val onboardingFeatureModule = module {
    viewModelOf(::OnboardingViewModel)
}

