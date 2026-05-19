package com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model

/**
 * Represents a single message in the AI chat dialog.
 *
 * [costCredits] is always 0 in Phase A (Layer 1 local routing).
 * [routedLayer] indicates which classification tier handled this message.
 */
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
    val timestamp: Long,
    val costCredits: Int = 0,
    val routedLayer: RoutingLayer? = null,
    /**
     * The checklist ID affected by the AI operation that produced this message, if any.
     * Non-null only for successful write-intent dispatch outcomes (AddItem, DeleteItem,
     * CompleteItem, SetItemReminder, CreateChecklist, CreateChecklistFromAttachment,
     * AttachToItem). Null for read intents (FindItems), bulk operations (MoveAllReminders),
     * error messages, and the welcome bubble.
     * Persisted to Room so the deeplink button survives navigation away/back.
     */
    val linkedChecklistId: Long? = null,
    /**
     * Files the user attached to this message (user messages only).
     * Stored as a transient list on the domain object; persisted to Room via JSON
     * TypeConverter in [ChatHistoryEntry.attachmentsJson] (added in MIGRATION_13_14).
     *
     * Empty list for assistant messages and legacy messages from before v13.
     */
    val attachments: List<ChatAttachment> = emptyList(),
)
