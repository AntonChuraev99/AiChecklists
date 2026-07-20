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

    /**
     * Emits the checklist id the user chose to make their default target for chat-created items,
     * or `null` when the chat should keep asking "which list?" every time (the default).
     *
     * Set ONLY through an explicit opt-in ("Remember my choice" on the which-list picker) and
     * always disclosed in the reply that follows. Cleared from chat settings — a sticky routing
     * preference the user cannot see or reset is a trap, not a convenience.
     */
    val defaultChecklistIdFlow: Flow<Long?>

    /** Persist (or clear, with `null`) the user's default chat target list. */
    suspend fun setDefaultChecklistId(checklistId: Long?)

    /**
     * Emits the BCP-47 primary subtag (e.g. "en", "es", "hi") of the language the user explicitly
     * pinned for AI replies, or `null` when the reply language should be decided automatically by
     * the server ("Auto" — the default).
     *
     * This is an explicit override only: `null` means "no override, let the server match the user's
     * message language". A non-null code is forwarded to the Layer-3 endpoints as `response_language`
     * so the model always answers in that language regardless of the input language.
     */
    val responseLanguageFlow: Flow<String?>

    /** Persist (or clear, with `null` = Auto) the user's explicit AI response language. */
    suspend fun setResponseLanguage(code: String?)
}
