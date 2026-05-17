package com.antonchuraev.homesearchchecklist.feature.aichat.api.repository

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Persistence layer for AI chat message history.
 *
 * Messages are stored in Room ([ChatHistoryEntry] table) and survive app restarts.
 * All callers (Free + Premium) write to the same store — storage cost is trivial.
 * User-level "clear history" is surfaced via [clear].
 */
interface ChatHistoryRepository {
    /**
     * Ordered flow of recent messages (ascending by timestamp).
     * Emits a fresh value whenever the underlying table changes.
     */
    fun observeRecent(limit: Int = 20): Flow<List<ChatMessage>>

    /** Append a single message to history. */
    suspend fun append(message: ChatMessage)

    /** Delete all history entries. */
    suspend fun clear()

    /** Returns the total number of stored messages. */
    suspend fun count(): Int
}
