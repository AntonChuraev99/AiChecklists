package com.antonchuraev.homesearchchecklist.di

import com.antonchuraev.homesearchchecklist.aichat.ToolCallDispatcherImpl
import com.antonchuraev.homesearchchecklist.aichat.AndroidChatLocaleProvider
import com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher.ToolCallDispatcher
import com.antonchuraev.homesearchchecklist.feature.aichat.api.locale.ChatLocaleProvider
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
    single<ToolCallDispatcher> {
        ToolCallDispatcherImpl(
            checklistRepository = get(),
            userDataRepository = get(),
        )
    }
    single<ChatLocaleProvider> {
        AndroidChatLocaleProvider()
    }
}
