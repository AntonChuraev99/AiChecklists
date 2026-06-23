package com.antonchuraev.homesearchchecklist.feature.onboarding.di

import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.OnboardingViewModel
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.InteractiveOnboardingViewModel
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.WelcomeOnboardingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val onboardingFeatureModule = module {
    viewModel {
        OnboardingViewModel(
            savedStateHandle = get(),
            navigator = get(),
            completeOnboardingUseCase = get(),
            analyticsTracker = get(),
            isDebugBuild = get(named("isDebugBuild")),
        )
    }
    viewModel {
        InteractiveOnboardingViewModel(
            savedStateHandle = get(),
            navigator = get(),
            completeOnboardingUseCase = get(),
            templatesRepository = get(),
            checklistRepository = get(),
            analyticsTracker = get(),
            reminderScheduler = get(),
            checklistFormatter = get(),
            isDebugBuild = get(named("isDebugBuild")),
        )
    }
    viewModel {
        WelcomeOnboardingViewModel(
            savedStateHandle = get(),
            navigator = get(),
            completeOnboardingUseCase = get(),
            checklistRepository = get(),
            analyzeRepository = get(),
            activationCoordinator = get(),
            remoteConfigProvider = get(),
            analyticsTracker = get(),
            logger = get(),
            isDebugBuild = get(named("isDebugBuild")),
        )
    }
}

