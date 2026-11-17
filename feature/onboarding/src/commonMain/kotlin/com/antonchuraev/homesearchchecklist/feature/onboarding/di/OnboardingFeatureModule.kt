package com.antonchuraev.homesearchchecklist.feature.onboarding.di

import com.antonchuraev.homesearchchecklist.feature.aichat.api.locale.ChatLocaleProvider
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AiChatRepository
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.OnboardingVoiceTranscriber
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.WelcomeOnboardingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val onboardingFeatureModule = module {
    // params: isCallAvailable (Boolean) from the Route — App.kt computes it with the Ask-pill
    // call button's expression (FEATURE_SPEC §5.4). Absent → no Call entry.
    viewModel { params ->
        WelcomeOnboardingViewModel(
            savedStateHandle = get(),
            navigator = get(),
            completeOnboardingUseCase = get(),
            checklistRepository = get(),
            analyzeRepository = get(),
            activationCoordinator = get(),
            analyticsTracker = get(),
            logger = get(),
            navExperimentResolver = get(),
            ensureInbox = get(),
            shellLaunchRequests = get(),
            userDataRepository = get(),
            // The chat's transcription, with the chat's locale: one pipeline, one credit price.
            voiceTranscriber = run {
                val chatRepository = get<AiChatRepository>()
                val localeProvider = get<ChatLocaleProvider>()
                OnboardingVoiceTranscriber { path, mime ->
                    chatRepository.transcribeAudio(path, mime, localeProvider.current())
                }
            },
            isCallAvailable = params.getOrNull<Boolean>() ?: false,
            isDebugBuild = get(named("isDebugBuild")),
        )
    }
}
