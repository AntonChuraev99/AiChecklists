package com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model

/**
 * Concrete operations extracted from classified intents.
 *
 * ─── Targeting a checklist: [checklistId] first, [checklistHint] as fallback ───
 *
 * [checklistHint] is a fuzzy name (e.g. "покупки", "shopping") resolved by name-match at
 * dispatch time. It is the ONLY target a server-built tool call can carry: Layer 2/3 name
 * lists, it does not know their local row ids.
 *
 * [checklistId] is the exact local checklist id, set ONLY where the client already knows
 * which row it means — a which-list chip the user tapped, the remembered default list, the
 * checklist open behind the dock. Null on every server-built call.
 *
 * **Why both.** A name is not an identity: two lists may legitimately be called "Shopping".
 * Resolving those by name makes both which-list chips dispatch the identical tool call, so
 * the picker asks a question whose answer it then throws away — the "which list… Shopping,
 * Shopping or Shopping?" failure. This mirrors [UndoHandle], which is id-only for the same
 * reason: `handleDeleteItem` matches item text with `contains(ignoreCase = true)`, so a
 * text-addressed rollback would remove a same-named row the user already had.
 *
 * **An id is a promise, not a hint.** When [checklistId] is set the dispatcher resolves by
 * it and does NOT retry by name if the row is gone (deleted between building the chip and
 * tapping it) — falling back would hand the write to a same-named neighbour, which is the
 * exact bug ids exist to prevent. Name resolution is for [checklistId] == null, not for
 * "the id missed".
 *
 * [at] in [SetItemReminder] is epoch millis resolved by [SmartDateParser] reuse.
 *
 * [fromDayStartMs] / [fromDayEndMs] / [toDayStartMs] in [MoveAllReminders] are
 * start-of-day epoch millis for the source and target dates.
 */
sealed interface ToolCall {
    data class AddItem(
        val checklistHint: String?,
        val itemText: String,
        val checklistId: Long? = null,
    ) : ToolCall

    data class DeleteItem(
        val checklistHint: String?,
        val itemText: String,
        val checklistId: Long? = null,
    ) : ToolCall

    data class CompleteItem(
        val checklistHint: String?,
        val itemText: String,
        val checklistId: Long? = null,
    ) : ToolCall

    data class CreateChecklist(
        val name: String,
        val initialItems: List<String>
    ) : ToolCall

    data class SetItemReminder(
        val checklistHint: String?,
        val itemText: String,
        val at: Long,
        val checklistId: Long? = null,
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
        val checklistId: Long? = null,
    ) : ToolCall

    /**
     * Batch-add multiple items to one checklist in a single operation (agent path).
     * Mirrors [AddItem] but updates the fill + template once with all [itemTexts].
     * The agent uses this when adding several items at once (e.g. confirming an
     * 8-item proposal) — see plan finding #2.
     */
    data class AddItems(
        val checklistHint: String?,
        val itemTexts: List<String>,
        val checklistId: Long? = null,
    ) : ToolCall

    /**
     * READ-ONLY (agent path): return the items of one checklist by name so the
     * model can ground an answer in real item text. This is the D3 privacy payload
     * — item text reaches Gemini only when the model explicitly requests it.
     * Resolves to [DispatchOutcome.ChecklistContent].
     */
    data class ReadChecklist(
        val name: String,
    ) : ToolCall

    /** Rename an existing checklist (agent path). */
    data class RenameChecklist(
        val checklistHint: String,
        val newName: String,
    ) : ToolCall

    /**
     * Bulk-remove every checked item from one checklist (agent path — "удали выполненные пункты").
     * Resolves like the item-targeting variants: [checklistId] first, [checklistHint] as fallback.
     *
     * [checklistHint] is the fuzzy list name the server sends (= `context_checklist` when a list is
     * open). Null when the agent named no list — the dispatcher then asks the which-list picker if
     * more than one checklist exists. [checklistId] is set only after a which-list chip is tapped,
     * so the retargeted call clears exactly the list the user picked (see [checklistId] rationale
     * on [AddItem]).
     */
    data class ClearCompleted(
        val checklistHint: String?,
        val checklistId: Long? = null,
    ) : ToolCall
}
