package com.antonchuraev.homesearchchecklist.core.datastore.impl

import com.antonchuraev.homesearchchecklist.core.datastore.api.AiChatPreferencesRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val KEY_DEEP_THINKING_ENABLED = "ai_chat_deep_thinking_enabled"
private const val DEFAULT_DEEP_THINKING = false

/**
 * The default chat target list, stored as a STRING id.
 *
 * [AppDatastore] exposes no long accessor and no per-key remove, so the id rides as text and the
 * empty string is the "ask me every time" sentinel — that keeps "reset" a plain write instead of
 * widening the shared datastore API for one preference. A value that no longer parses (or names a
 * deleted list) resolves to null and the chat falls back to asking, which is the safe direction.
 */
private const val KEY_DEFAULT_CHECKLIST_ID = "ai_chat_default_checklist_id"
private const val NO_DEFAULT_CHECKLIST = ""

/**
 * The explicit AI response language, stored as its BCP-47 primary subtag ("en", "es", …).
 *
 * The empty string is the "Auto" sentinel (no override) — same string-storage convention as
 * [KEY_DEFAULT_CHECKLIST_ID], so "reset to Auto" is a plain write rather than a per-key remove.
 */
private const val KEY_RESPONSE_LANGUAGE = "ai_chat_response_language"
private const val NO_RESPONSE_LANGUAGE = ""

class AiChatPreferencesRepositoryImpl(
    private val dataStore: AppDatastore,
) : AiChatPreferencesRepository {

    override val deepThinkingEnabledFlow: Flow<Boolean> =
        dataStore.observeBoolean(KEY_DEEP_THINKING_ENABLED, DEFAULT_DEEP_THINKING)

    override suspend fun setDeepThinkingEnabled(enabled: Boolean) {
        dataStore.saveBoolean(KEY_DEEP_THINKING_ENABLED, enabled)
    }

    override val defaultChecklistIdFlow: Flow<Long?> =
        dataStore.observeString(KEY_DEFAULT_CHECKLIST_ID, NO_DEFAULT_CHECKLIST)
            .map { it.toLongOrNull() }

    override suspend fun setDefaultChecklistId(checklistId: Long?) {
        dataStore.saveString(KEY_DEFAULT_CHECKLIST_ID, checklistId?.toString() ?: NO_DEFAULT_CHECKLIST)
    }

    override val responseLanguageFlow: Flow<String?> =
        dataStore.observeString(KEY_RESPONSE_LANGUAGE, NO_RESPONSE_LANGUAGE)
            .map { it.ifEmpty { null } }

    override suspend fun setResponseLanguage(code: String?) {
        dataStore.saveString(KEY_RESPONSE_LANGUAGE, code ?: NO_RESPONSE_LANGUAGE)
    }
}
