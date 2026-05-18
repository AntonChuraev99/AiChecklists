package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Room entity for AI chat history persistence.
 *
 * One row per chat message. [role] is "user" or "assistant".
 * [routedLayer] is "Local" | "Classifier" | "FullChat" | null (assistant messages
 * get null or "FullChat" for Layer 3 responses).
 */
@Entity(tableName = "ai_chat_history")
data class ChatHistoryEntry(
    @PrimaryKey val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val costCredits: Int = 0,
    val routedLayer: String? = null,
    /** Foreign reference to the affected checklist; null for read/bulk/error messages. */
    val linkedChecklistId: Long? = null,
)
