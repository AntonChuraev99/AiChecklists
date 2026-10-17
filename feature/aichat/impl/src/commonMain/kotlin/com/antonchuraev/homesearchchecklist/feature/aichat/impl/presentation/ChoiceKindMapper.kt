package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceKind
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall

/**
 * The [ChoiceKind] of a single write-intent choice block built for [this] tool call — the header
 * type tile of redesign B, pass 2.
 *
 * Exhaustive on purpose: a new [ToolCall] variant must decide its tile here instead of silently
 * rendering none. Null where the design has no tile for the action (complete, attach, rename) and for
 * the read-only variants, which never reach a choice block.
 */
internal fun ToolCall.choiceKind(): ChoiceKind? = when (this) {
    is ToolCall.AddItem,
    is ToolCall.AddItems -> ChoiceKind.AddItems

    is ToolCall.CreateChecklist,
    is ToolCall.CreateChecklistFromAttachment -> ChoiceKind.CreateChecklist

    is ToolCall.SetItemReminder,
    is ToolCall.MoveAllReminders -> ChoiceKind.Reminder

    // Clearing completed items removes them for good — the same irreversible tile as a delete.
    is ToolCall.DeleteItem,
    is ToolCall.ClearCompleted -> ChoiceKind.Delete

    is ToolCall.MoveItem -> ChoiceKind.Move

    is ToolCall.CompleteItem,
    is ToolCall.AttachToItem,
    is ToolCall.RenameChecklist,
    is ToolCall.FindItemsQuery,
    is ToolCall.ReadChecklist -> null
}
