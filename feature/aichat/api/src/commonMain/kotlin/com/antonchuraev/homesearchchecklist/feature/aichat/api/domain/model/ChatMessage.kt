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
    /**
     * The original user input text that produced this [ChatIntent.Unknown] response.
     * When non-null on an assistant message, [ChatMessageBubble] renders an "Ask AI"
     * TextButton that dispatches [OnAskAiFallback] → Layer 3 escalation (3 credits).
     *
     * TRANSIENT — NOT persisted to Room. The "Ask AI" button disappears on app restart;
     * this is intentional and avoids a Room migration. The field defaults to null on
     * the [ChatHistoryRepositoryImpl.toChatMessage] path (it maps only persisted columns).
     */
    val askAiForText: String? = null,
    /**
     * Premium daily credit allowance to render in the "Become Pro" CTA of this message.
     * Non-null only on the out-of-credits reply ([ChatIntent.InsufficientCredits]); the bubble
     * then shows the CTA, which navigates to the paywall.
     *
     * The number comes from Remote Config (`ai_daily_limit_premium`), NOT a literal — the label
     * promises a specific allowance and a hardcoded one silently lies the day that key changes.
     *
     * TRANSIENT — NOT persisted to Room, same as [askAiForText]: the CTA disappears on app
     * restart rather than lingering as a stale offer, and no migration is needed.
     */
    val paywallCtaCredits: Int? = null,
)
