package com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model

/**
 * Concrete operations extracted from classified intents.
 *
 * [checklistHint] is a fuzzy name (e.g. "покупки", "shopping") used to resolve
 * the target checklist by name-match at dispatch time. Null means "active/default".
 *
 * [at] in [SetItemReminder] is epoch millis resolved by [SmartDateParser] reuse.
 *
 * [fromDayStartMs] / [fromDayEndMs] / [toDayStartMs] in [MoveAllReminders] are
 * start-of-day epoch millis for the source and target dates.
 */
sealed interface ToolCall {
    data class AddItem(
        val checklistHint: String?,
        val itemText: String
    ) : ToolCall

    data class DeleteItem(
        val checklistHint: String?,
        val itemText: String
    ) : ToolCall

    data class CompleteItem(
        val checklistHint: String?,
        val itemText: String
    ) : ToolCall

    data class CreateChecklist(
        val name: String,
        val initialItems: List<String>
    ) : ToolCall

    data class SetItemReminder(
        val checklistHint: String?,
        val itemText: String,
        val at: Long
    ) : ToolCall

    data class MoveAllReminders(
        val fromDayStartMs: Long,
        val fromDayEndMs: Long,
        val toDayStartMs: Long
    ) : ToolCall

    data class FindItemsQuery(
        val query: String
    ) : ToolCall

    /**
     * Attachment(s) with no accompanying item text: create a new checklist from the
     * attachment content via the AiAnalyzer service (Cloud Function path). Identical UX
     * to "Create via AI".
     *
     * [attachments] is non-empty by construction (the dispatcher should reject empty lists).
     */
    data class CreateChecklistFromAttachment(
        val attachments: List<ChatAttachment>,
    ) : ToolCall

    /**
     * Attachment(s) with a text hint pointing to an existing checklist item.
     * The dispatcher stores the files via [AttachmentStoragePort] and appends
     * them to [ChecklistFillItem.attachments] for the resolved item.
     *
     * [checklistHint] — fuzzy checklist name (may be null → use default checklist).
     * [itemText]      — fuzzy item text used to locate the target fill item.
     * [attachments]   — files to attach (non-empty).
     */
    data class AttachToItem(
        val checklistHint: String?,
        val itemText: String,
        val attachments: List<ChatAttachment>,
    ) : ToolCall
}
