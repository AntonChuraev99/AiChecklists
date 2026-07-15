package com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.UndoHandle

/**
 * Executes a [ToolCall] against the application's data layer and returns a [DispatchOutcome].
 *
 * The interface is declared in api/ and implemented in composeApp/ (ToolCallDispatcherImpl).
 * This boundary avoids a circular dep: feature/aichat/impl → feature/checklist is allowed,
 * but the dispatcher also needs ChecklistRepository + fill mutation helpers that live in
 * feature/checklist; to keep feature/aichat/impl thin, the full dispatch logic (checklist
 * resolution by hint, fill update, template sync) belongs in the app module where all
 * repositories are available.
 *
 * Koin binding: single<ToolCallDispatcher> in composeApp's appModule.
 */
interface ToolCallDispatcher {
    suspend fun dispatch(toolCall: ToolCall): DispatchOutcome

    /**
     * Rolls back the mutation described by [handle], addressing rows by id only.
     *
     * @return [DispatchOutcome.Success] with a confirmation message key on rollback;
     *  [DispatchOutcome.NotFound] with `chat_undo_item_gone` when the row is already gone
     *  (the user deleted it by hand in the meantime) — never a silent no-op.
     */
    suspend fun undo(handle: UndoHandle): DispatchOutcome

    /**
     * Moves a just-added item to [targetChecklistName].
     *
     * Order is add-then-remove and it is load-bearing: if the second half fails the worst case
     * is a visible duplicate the user can delete, whereas remove-then-add would silently lose
     * the item. Resolution failures on the add half (ambiguous / unknown list name) are returned
     * as-is and nothing is removed.
     *
     * @return [DispatchOutcome.Success] carrying a FRESH [DispatchOutcome.Success.undo] handle
     *  pointing at the newly created rows, so the item can be moved onwards again.
     */
    suspend fun moveAddedItem(
        handle: UndoHandle.AddedItem,
        targetChecklistName: String,
    ): DispatchOutcome
}
