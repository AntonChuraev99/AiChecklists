package com.antonchuraev.homesearchchecklist.di

import com.antonchuraev.homesearchchecklist.aichat.ActivationCoordinatorImpl
import com.antonchuraev.homesearchchecklist.aichat.AndroidChatLocaleProvider
import com.antonchuraev.homesearchchecklist.aichat.ToolCallDispatcherImpl
import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher.ToolCallDispatcher
import com.antonchuraev.homesearchchecklist.feature.aichat.api.locale.ChatLocaleProvider
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.analyzer.AiAnalyzer
import org.koin.dsl.module

/**
 * Koin module for app-level AI Chat bindings.
 *
 * [ToolCallDispatcherImpl] lives in composeApp (not feature/aichat/impl) because it
 * depends on [ChecklistRepository] and [UserDataRepository] — the full data-layer graph.
 * Feature modules must not create cross-feature dependencies.
 *
 * Registered as part of [appModule] via includes().
 */
val aiChatDispatcherModule = module {
    // Activation funnel coordinator — shared between the dispatcher (data) and App.kt (UI).
    // Singleton so its reminderOptInRequests SharedFlow is the same instance on both sides.
    single<ActivationCoordinator> {
        ActivationCoordinatorImpl(
            activationPrefs = get(),
            userDataRepository = get(),
            analytics = get(),
            logger = get(),
        )
    }
    single<ToolCallDispatcher> {
        ToolCallDispatcherImpl(
            checklistRepository = get(),
            userDataRepository = get(),
            aiAnalyzer = get<AiAnalyzer>(),
            attachmentStorage = get<AttachmentStoragePort>(),
            logger = get(),
            activationCoordinator = get(),
            remoteConfigProvider = get(),
        )
    }
    single<ChatLocaleProvider> {
        AndroidChatLocaleProvider()
    }
}
