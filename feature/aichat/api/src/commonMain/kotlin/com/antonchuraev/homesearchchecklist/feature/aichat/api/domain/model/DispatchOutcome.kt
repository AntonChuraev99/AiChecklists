package com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model

/**
 * Result of dispatching a [ToolCall] to the underlying repository layer.
 *
 * [Success.messageKey] refers to a `chat_dispatch_*` string resource; [Success.args]
 * populate `%1$s` / `%2$s` positional placeholders in that resource.
 * [AmbiguousMatch.candidates] lists checklist names when the hint matched >1 list.
 * [NotFound.messageKey] refers to a `chat_dispatch_*` string resource explaining what
 * was not found; [NotFound.args] populate positional placeholders.
 * [RequiresPremium] gates write operations for free-tier users (future enforcement).
 */
sealed interface DispatchOutcome {
    data class Success(
        val messageKey: String,
        val args: List<String> = emptyList(),
        /**
         * The checklist that was affected by this operation, if applicable.
         * Non-null for AddItem, DeleteItem, CompleteItem, SetItemReminder, CreateChecklist.
         * Null for FindItems, MoveAllReminders, and any error outcomes.
         * Surfaced in [ChatMessageBubble] as an "Open checklist" deeplink button.
         */
        val linkedChecklistId: Long? = null,
    ) : DispatchOutcome

    data class AmbiguousMatch(val candidates: List<String>) : DispatchOutcome

    /**
     * Result of a [ToolCall.ReadChecklist] — the actual items of one checklist.
     * Read-only; carries the D3 payload (item text) the agent serializes back to
     * Gemini as a function_response so the model can reason over real list contents.
     */
    data class ChecklistContent(
        val checklistName: String,
        val items: List<ReadChecklistItem>,
        val checklistId: Long? = null,
    ) : DispatchOutcome

    data class NotFound(
        val messageKey: String,
        val args: List<String> = emptyList(),
    ) : DispatchOutcome

    data object RequiresPremium : DispatchOutcome
}

/** One item returned by [DispatchOutcome.ChecklistContent] (read-only agent payload). */
data class ReadChecklistItem(
    val text: String,
    val checked: Boolean,
)
