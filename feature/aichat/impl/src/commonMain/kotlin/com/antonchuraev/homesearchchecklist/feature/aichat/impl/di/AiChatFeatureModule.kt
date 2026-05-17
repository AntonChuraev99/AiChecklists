package com.antonchuraev.homesearchchecklist.feature.aichat.impl.di

import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.LocalIntentRouter
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AiChatRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatClassifierApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.data.ChatClassifierApiServiceImpl
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.parser.LocalIntentRouterImpl
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.ChatViewModel
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview.ToolCallPreviewRenderer
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview.ToolCallPreviewRendererImpl
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.repository.AiChatRepositoryImpl
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin bindings for feature/aichat/impl.
 *
 * Note: [ToolCallDispatcher] and [ChatLocaleProvider] are NOT bound here.
 * Their implementations live in composeApp and are registered in the app-level Koin module,
 * because [ToolCallDispatcher] requires access to the full ChecklistRepository graph.
 *
 * [UserDataRepository] is bound in userFeatureModule and resolved via get() here.
 *
 * Registration order in appModule:
 *   modules(userFeatureModule, aiChatFeatureModule, /* app-level module with ToolCallDispatcher + ChatLocaleProvider */)
 */
val aiChatFeatureModule = module {
    single<LocalIntentRouter> {
        LocalIntentRouterImpl(
            dateParser = get(),
            logger = get(),
        )
    }
    single<ChatClassifierApiService> {
        ChatClassifierApiServiceImpl(logger = get())
    }
    single<AiChatRepository> {
        AiChatRepositoryImpl(
            router = get(),
            classifierApi = get(),
            userDataRepository = get(),
            logger = get(),
        )
    }
    single<ToolCallPreviewRenderer> {
        ToolCallPreviewRendererImpl()
    }
    viewModel {
        ChatViewModel(
            aiChatRepository = get(),
            toolCallDispatcher = get(),
            previewRenderer = get(),
            localeProvider = get(),
            logger = get(),
        )
    }
}
