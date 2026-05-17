package com.antonchuraev.homesearchchecklist.core.datastore.impl

import com.antonchuraev.homesearchchecklist.core.datastore.api.AiChatPreferencesRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import kotlinx.coroutines.flow.Flow

private const val KEY_DEEP_THINKING_ENABLED = "ai_chat_deep_thinking_enabled"
private const val DEFAULT_DEEP_THINKING = false

class AiChatPreferencesRepositoryImpl(
    private val dataStore: AppDatastore,
) : AiChatPreferencesRepository {

    override val deepThinkingEnabledFlow: Flow<Boolean> =
        dataStore.observeBoolean(KEY_DEEP_THINKING_ENABLED, DEFAULT_DEEP_THINKING)

    override suspend fun setDeepThinkingEnabled(enabled: Boolean) {
        dataStore.saveBoolean(KEY_DEEP_THINKING_ENABLED, enabled)
    }
}
