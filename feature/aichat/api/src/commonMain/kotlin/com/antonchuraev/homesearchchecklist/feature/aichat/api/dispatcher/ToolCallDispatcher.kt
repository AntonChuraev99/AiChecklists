package com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall

/**
 * Executes a [ToolCall] against the application's data layer and returns a [DispatchOutcome].
 *
 * The interface is declared in api/ and implemented in composeApp/ (ToolCallDispatcherImpl).
 * This boundary avoids a circular dep: feature/aichat/impl → feature/checklist is allowed,
 * but the dispatcher also needs ChecklistRepository + fill mutation helpers that live in
 * feature/checklist; to keep feature/aichat/impl thin, the full dispatch logic (checklist
 * resolution by hint, fill update, template sync) belongs in the app module where all
 * repositories are available.
 *
 * Koin binding: single<ToolCallDispatcher> in composeApp's appModule.
 */
interface ToolCallDispatcher {
    suspend fun dispatch(toolCall: ToolCall): DispatchOutcome
}
