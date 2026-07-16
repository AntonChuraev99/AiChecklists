package com.antonchuraev.homesearchchecklist.feature.aichat.impl.di

import com.antonchuraev.homesearchchecklist.core.datastore.api.AiChatPreferencesRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentTranscriptRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AiChatRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatAgentApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatClassifierApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatCompletionApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatHistoryRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.format.ChatDateFormatter
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscribeAudioApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.data.ChatAgentApiServiceImpl
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.data.ChatClassifierApiServiceImpl
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.data.ChatCompletionApiServiceImpl
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.data.TranscribeAudioApiServiceImpl
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.ChatViewModel
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview.ChatDateFormatterImpl
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview.ToolCallPreviewRenderer
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview.ToolCallPreviewRendererImpl
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.repository.AgentTranscriptRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.repository.AiChatRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.repository.ChatHistoryRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.AgentTranscriptDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChatHistoryDao
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
 * [ChecklistRepository] is bound in checklistFeatureModule and resolved via get() here.
 * [ChatHistoryDao] is exposed from checklistFeatureModule (see below).
 *
 * Registration order in appModule:
 *   modules(checklistFeatureModule, userFeatureModule, aiChatFeatureModule,
 *           /* app-level module with ToolCallDispatcher + ChatLocaleProvider */)
 */
// Layer 1 (LocalIntentRouterImpl) is intentionally NOT bound — decision 2026-07-15
// (docs/decisions/2026-07-15-remove-ai-chat-layer1.md). The parser and its 168 tests stay in the
// repo as a parked asset; re-routing it means reverting the disconnect commit, which restores
// this binding and the repository's `router` parameter together.
val aiChatFeatureModule = module {
    single<ChatAgentApiService> {
        ChatAgentApiServiceImpl(logger = get())
    }
    single<ChatClassifierApiService> {
        ChatClassifierApiServiceImpl(logger = get())
    }
    single<ChatCompletionApiService> {
        ChatCompletionApiServiceImpl(logger = get())
    }
    single<TranscribeAudioApiService> {
        TranscribeAudioApiServiceImpl(logger = get())
    }
    single<ChatHistoryRepository> {
        ChatHistoryRepositoryImpl(
            dao = get<ChatHistoryDao>(),
            logger = get(),
        )
    }
    single<AgentTranscriptRepository> {
        AgentTranscriptRepositoryImpl(
            dao = get<AgentTranscriptDao>(),
            logger = get(),
        )
    }
    single<AiChatRepository> {
        AiChatRepositoryImpl(
            classifierApi = get(),
            completionApi = get(),
            transcribeApi = get(),
            chatAgentApi = get(),
            userDataRepository = get(),
            aiChatPreferencesRepository = get(),
            logger = get(),
        )
    }
    // One date formatter for the whole chat: also resolved by ToolCallDispatcherImpl (composeApp)
    // so "reminder set for …" and its preview can never spell the same moment differently.
    single<ChatDateFormatter> {
        ChatDateFormatterImpl()
    }
    single<ToolCallPreviewRenderer> {
        ToolCallPreviewRendererImpl(
            dateFormatter = get(),
        )
    }
    viewModel {
        ChatViewModel(
            aiChatRepository = get(),
            toolCallDispatcher = get(),
            previewRenderer = get(),
            dateFormatter = get(),
            localeProvider = get(),
            chatHistoryRepository = get(),
            agentTranscriptRepository = get(),
            checklistRepository = get(),
            userDataRepository = get(),
            aiChatPreferencesRepository = get<AiChatPreferencesRepository>(),
            analytics = get(),
            aiModelExperimentTracker = get(),
            // Bound in remoteConfigModule (registered in appModule) — read for the Premium
            // credit allowance advertised by the out-of-credits paywall CTA.
            remoteConfigProvider = get(),
            logger = get(),
        )
    }
}
