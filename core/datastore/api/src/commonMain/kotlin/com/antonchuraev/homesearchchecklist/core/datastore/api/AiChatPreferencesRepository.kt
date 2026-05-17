package com.antonchuraev.homesearchchecklist.core.datastore.api

import kotlinx.coroutines.flow.Flow

/**
 * Repository for persisting AI Chat user preferences.
 *
 * Kept separate from [ThemeRepository] to avoid mixing UI-theme concerns with
 * feature-specific chat settings. Extend here as the chat feature grows.
 */
interface AiChatPreferencesRepository {
    /**
     * Emits `true` when the user has enabled "Deep Thinking" mode.
     *
     * When enabled, all chat requests bypass Layer 1 (local router) and Layer 2
     * (classifier) and go directly to Layer 3 (completeFreeForm, 3 credits each).
     * Default: `false` — normal 3-layer routing applies.
     */
    val deepThinkingEnabledFlow: Flow<Boolean>

    /** Persist the user's Deep Thinking opt-in. */
    suspend fun setDeepThinkingEnabled(enabled: Boolean)
}
