package com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model

/**
 * Id-based snapshot of a just-applied reversible mutation.
 *
 * Emitted by [ToolCallDispatcher.dispatch] inside [DispatchOutcome.Success.undo] for the two
 * reversible write operations (add item / complete item) so the chat can offer a post-hoc
 * "Undo" chip instead of a pre-hoc confirmation question ("ceremony proportional to
 * reversibility" — D1).
 *
 * Rollback NEVER matches by text: `handleDeleteItem` resolves its target with
 * `contains(ignoreCase = true)`, so a text-matched undo would happily remove a same-named row
 * the user already had instead of the one we just added. Every field here is a stable id
 * captured at mutation time; [itemText] is display copy only (chip labels / result strings)
 * and must not be used to locate a row.
 */
sealed interface UndoHandle {
    /** The checklist the mutation landed in — always known (the dispatcher resolved it). */
    val checklistId: Long

    /**
     * An item added by [ToolCall.AddItem]. Undo removes BOTH the fill row ([fillItemId]) and its
     * template row ([templateItemId]) — the template write is what dirties the parent checklist,
     * without it the removal never syncs.
     *
     * @param checklistName Display name of the target list, used to exclude it from the
     *                      "move to another list" picker and to label the move result.
     */
    data class AddedItem(
        override val checklistId: Long,
        val checklistName: String,
        val fillId: Long,
        val fillItemId: String,
        val templateItemId: String,
        val itemText: String,
    ) : UndoHandle

    /**
     * An item checked off by [ToolCall.CompleteItem]. Undo flips the same fill row back to
     * unchecked; the template carries no checked state, so no template write is needed.
     */
    data class CompletedItem(
        override val checklistId: Long,
        val fillId: Long,
        val fillItemId: String,
        val itemText: String,
    ) : UndoHandle
}
