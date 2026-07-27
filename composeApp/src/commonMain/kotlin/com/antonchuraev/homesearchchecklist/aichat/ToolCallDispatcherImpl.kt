package com.antonchuraev.homesearchchecklist.aichat

import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.core.common.api.ChecklistSource
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher.ToolCallDispatcher
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ReadChecklistItem
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.UndoHandle
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.mimeTypeToAttachmentSource
import com.antonchuraev.homesearchchecklist.feature.aichat.api.format.ChatDateFormatter
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.analyzer.AiAnalyzer
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.first

/**
 * App-level implementation of [ToolCallDispatcher].
 *
 * Lives in composeApp to access the full dependency graph:
 * [ChecklistRepository] for all data mutations,
 * [UserDataRepository] for premium gate on [ToolCall.CreateChecklist].
 *
 * Checklist resolution strategy ([resolveChecklistAndFill]):
 *   1. [ToolCall.checklistId] != null → exact id match. Set only when the client already knows
 *      which row it means (a tapped which-list chip / the remembered default / the open list),
 *      so it wins outright and never degrades to (2). A gone id → [DispatchOutcome.NotFound],
 *      NOT a name retry: a same-named neighbour taking the write is the bug ids exist to prevent.
 *   2. id == null, hint != null → fuzzy name match (substring, case-insensitive). This is the
 *      server path: Layer 2/3 name lists, they cannot know local ids.
 *      - 0 matches → [DispatchOutcome.NotFound]
 *      - 1 match → proceed
 *      - >1 matches → [DispatchOutcome.AmbiguousMatch] with up to [MAX_AMBIGUOUS_CANDIDATES] names.
 *        The chat turns these into which-list chips, and each chip comes back carrying an id (1).
 *   3. id == null, hint == null → use first checklist in the list (most recently positioned).
 *      If no checklists exist → [DispatchOutcome.NotFound].
 *
 * Premium gate:
 *   [ToolCall.CreateChecklist] checks [UserData.isPremium] + current checklist count
 *   against a hard-coded free limit (4). This mirrors [GetUserLimitsUseCase] logic
 *   without introducing a cross-feature use-case dependency into composeApp/aichat.
 *   Phase B: replace with [GetUserLimitsUseCase] injection for RC-driven limits.
 *   // Pending: docs/todos/2026-05-13-ai-chat-assistant.md
 */
class ToolCallDispatcherImpl(
    private val checklistRepository: ChecklistRepository,
    private val userDataRepository: UserDataRepository,
    private val aiAnalyzer: AiAnalyzer,
    private val attachmentStorage: AttachmentStoragePort,
    private val logger: AppLogger,
    private val activationCoordinator: ActivationCoordinator,
    private val remoteConfigProvider: RemoteConfigProvider,
    private val dateFormatter: ChatDateFormatter,
    private val analyticsTracker: AnalyticsTracker,
) : ToolCallDispatcher {

    // NOTE: every checklist enumeration in this file reads `checklistRepository.projects`, never
    // `.checklists`. The AI deliberately does NOT address the v2 system Inbox — it is hidden from
    // every picker, so letting the chat resolve/rename/clear it would be the one surface where it
    // leaks. It also keeps [FREE_CHECKLIST_LIMIT] counting the same population as
    // GetUserLimitsUseCase; otherwise the v2 arm would hit the paywall one checklist early through
    // the chat while the Home screen still showed a free slot.
    // In the control arm no row is flagged, so `projects` == `checklists`.

    companion object {
        private const val TAG = "ToolCallDispatcher"
        private const val FREE_CHECKLIST_LIMIT = 4 // mirrors RemoteConfigDefaults.MAX_CHECKLISTS_FREE
        private const val MAX_FIND_RESULTS = 10
        /** Free-tier max attachments per item (mirrors item-attachments FREE_ATTACHMENT_LIMIT_PER_ITEM). */
        private const val FREE_ATTACH_LIMIT_PER_ITEM = 3
        /**
         * Max candidate names returned on an ambiguous match — mirrors the UI choice-chip cap
         * (ChatViewModel.MAX_CHOICE_OPTIONS). The adaptive FlowRow wraps, so 6 stays readable.
         */
        private const val MAX_AMBIGUOUS_CANDIDATES = 6
    }

    override suspend fun dispatch(toolCall: ToolCall): DispatchOutcome = runCatching {
        when (toolCall) {
            is ToolCall.AddItem -> handleAddItem(toolCall)
            is ToolCall.DeleteItem -> handleDeleteItem(toolCall)
            is ToolCall.CompleteItem -> handleCompleteItem(toolCall)
            is ToolCall.CreateChecklist -> handleCreateChecklist(toolCall)
            is ToolCall.SetItemReminder -> handleSetItemReminder(toolCall)
            is ToolCall.MoveAllReminders -> handleMoveAllReminders(toolCall)
            is ToolCall.FindItemsQuery -> handleFind(toolCall)
            is ToolCall.CreateChecklistFromAttachment -> handleCreateChecklistFromAttachment(toolCall)
            is ToolCall.AttachToItem -> handleAttachToItem(toolCall)
            is ToolCall.AddItems -> handleAddItems(toolCall)
            is ToolCall.ReadChecklist -> handleReadChecklist(toolCall)
            is ToolCall.RenameChecklist -> handleRenameChecklist(toolCall)
            is ToolCall.ClearCompleted -> handleClearCompleted(toolCall)
        }
    }.getOrElse { e ->
        logger.error(TAG, "dispatch failed for ${toolCall::class.simpleName}", e)
        DispatchOutcome.NotFound("chat_dispatch_operation_failed", listOf(e.message ?: "unknown error"))
    }

    // ─── Undo / Move (D1 reversible path) ─────────────────────────────────────

    override suspend fun undo(handle: UndoHandle): DispatchOutcome = runCatching {
        when (handle) {
            is UndoHandle.AddedItem -> undoAddedItem(handle)
            is UndoHandle.CompletedItem -> undoCompletedItem(handle)
        }
    }.getOrElse { e ->
        logger.error(TAG, "undo failed for ${handle::class.simpleName}", e)
        DispatchOutcome.NotFound("chat_dispatch_operation_failed", listOf(e.message ?: "unknown error"))
    }

    /**
     * Removes the fill row AND its template row — the add wrote both, so a half-undo would leave
     * an orphan template item that reappears on the next fill reset. Rows are located by id only:
     * a text match would hit a same-named row the user already had.
     */
    private suspend fun undoAddedItem(handle: UndoHandle.AddedItem): DispatchOutcome {
        val fill = checklistRepository.getFillById(handle.fillId)
            ?: return DispatchOutcome.NotFound("chat_undo_item_gone")
        if (fill.items.none { it.id == handle.fillItemId }) {
            // The user deleted the row by hand between the add and the Undo tap. Visible reply,
            // never a silent skip.
            return DispatchOutcome.NotFound("chat_undo_item_gone")
        }
        // BY ID — never `!it.text.contains(handle.itemText)`. A text filter would also delete the
        // user's OWN same-named rows that existed before the chat touched the list: an undo that
        // destroys data it never created. The id guard above does NOT protect against that — it
        // passes, and the text filter then takes the extra rows with it.
        checklistRepository.updateFill(
            fill.copy(items = fill.items.filter { it.id != handle.fillItemId })
        )

        val checklist = checklistRepository.getChecklistById(handle.checklistId)
        if (checklist == null) {
            logger.warning(
                TAG,
                "undoAddedItem: checklist id=${handle.checklistId} is gone — fill row removed, template not reconciled",
            )
        } else {
            val remaining = checklist.items.filter { it.id != handle.templateItemId }
            if (remaining.size != checklist.items.size) {
                checklistRepository.updateChecklistTemplate(checklist.copy(items = remaining))
            }
        }

        return DispatchOutcome.Success(
            "chat_result_undone_add",
            listOf(handle.itemText),
            linkedChecklistId = handle.checklistId,
        )
    }

    /** Flips the row back to unchecked. No template write — the template carries no checked state. */
    private suspend fun undoCompletedItem(handle: UndoHandle.CompletedItem): DispatchOutcome {
        val fill = checklistRepository.getFillById(handle.fillId)
            ?: return DispatchOutcome.NotFound("chat_undo_item_gone")
        if (fill.items.none { it.id == handle.fillItemId }) {
            return DispatchOutcome.NotFound("chat_undo_item_gone")
        }
        checklistRepository.updateFill(
            fill.copy(
                items = fill.items.map {
                    if (it.id == handle.fillItemId) it.withChecked(false) else it
                },
            )
        )
        return DispatchOutcome.Success(
            "chat_result_undone_complete",
            listOf(handle.itemText),
            linkedChecklistId = handle.checklistId,
        )
    }

    override suspend fun moveAddedItem(
        handle: UndoHandle.AddedItem,
        targetChecklistName: String,
    ): DispatchOutcome = runCatching {
        // ADD FIRST, then remove. If the remove half fails the user sees a duplicate they can
        // delete; the reverse order would silently drop the item on a failed add.
        val added = handleAddItem(
            ToolCall.AddItem(checklistHint = targetChecklistName, itemText = handle.itemText)
        )
        // Resolution failure (unknown / ambiguous list) → surface as-is, remove NOTHING.
        val addedSuccess = added as? DispatchOutcome.Success ?: return@runCatching added
        val newHandle = addedSuccess.undo as? UndoHandle.AddedItem

        val removed = undo(handle)
        if (removed !is DispatchOutcome.Success) {
            // Move already happened — report success, but this duplicate must be traceable.
            logger.error(
                TAG,
                "moveAddedItem: added '${handle.itemText}' to '$targetChecklistName' but could not " +
                    "remove the original from '${handle.checklistName}' — the item now exists twice",
            )
        }

        DispatchOutcome.Success(
            "chat_result_moved_to",
            listOf(handle.itemText, newHandle?.checklistName ?: targetChecklistName),
            linkedChecklistId = addedSuccess.linkedChecklistId,
            // Fresh handle → the item can be moved onwards (including back) from the new list.
            undo = newHandle,
        )
    }.getOrElse { e ->
        logger.error(TAG, "moveAddedItem failed", e)
        DispatchOutcome.NotFound("chat_dispatch_operation_failed", listOf(e.message ?: "unknown error"))
    }

    // ─── AddItem ──────────────────────────────────────────────────────────────

    private suspend fun handleAddItem(toolCall: ToolCall.AddItem): DispatchOutcome {
        val (checklist, fill) = resolveChecklistAndFill(toolCall.checklistId, toolCall.checklistHint)
            ?: return resolveChecklistFailure(toolCall.checklistId, toolCall.checklistHint)

        // Create the template item first so the fill row can carry the stable templateItemId link.
        // Every other add path links them; without the link the row renders as an UNLINKED legacy row
        // and folder-mode dumps it at the bottom regardless of its template position — that mismatch
        // is what makes later-added items look like they "land in the middle".
        val newTemplateItem = ChecklistItem(text = toolCall.itemText, checked = false)
        val newFillItem = ChecklistFillItem(
            text = toolCall.itemText,
            checked = false,
            templateItemId = newTemplateItem.id,
        )
        val updatedFill = fill.copy(items = fill.items + newFillItem)
        checklistRepository.updateFill(updatedFill)

        // Add item to template (dual-update as per CLAUDE.md)
        val updatedChecklist = checklist.copy(items = checklist.items + newTemplateItem)
        checklistRepository.updateChecklistTemplate(updatedChecklist)

        // Reversible → hand back the ids so the chat can offer "Undo" instead of asking first.
        val undo = UndoHandle.AddedItem(
            checklistId = checklist.id,
            checklistName = checklist.name,
            fillId = fill.id,
            fillItemId = newFillItem.id,
            templateItemId = newTemplateItem.id,
            itemText = toolCall.itemText,
        )

        return if (toolCall.checklistHint != null) {
            DispatchOutcome.Success(
                "chat_dispatch_added_to",
                listOf(toolCall.itemText, checklist.name),
                linkedChecklistId = checklist.id,
                undo = undo,
            )
        } else {
            DispatchOutcome.Success(
                "chat_dispatch_added",
                listOf(toolCall.itemText),
                linkedChecklistId = checklist.id,
                undo = undo,
            )
        }
    }

    // ─── DeleteItem ───────────────────────────────────────────────────────────

    private suspend fun handleDeleteItem(toolCall: ToolCall.DeleteItem): DispatchOutcome {
        val (checklist, fill) = resolveChecklistAndFill(toolCall.checklistId, toolCall.checklistHint)
            ?: return resolveChecklistFailure(toolCall.checklistId, toolCall.checklistHint)

        val matchingFillItem = fill.items.firstOrNull { it.text.contains(toolCall.itemText, ignoreCase = true) }
            ?: return DispatchOutcome.NotFound("chat_dispatch_item_not_found", listOf(toolCall.itemText, checklist.name))

        val updatedFill = fill.copy(items = fill.items.filter { it.id != matchingFillItem.id })
        checklistRepository.updateFill(updatedFill)

        // Mirror deletion in template
        val matchingTemplateItem = checklist.items.firstOrNull { it.text.contains(toolCall.itemText, ignoreCase = true) }
        if (matchingTemplateItem != null) {
            val updatedChecklist = checklist.copy(items = checklist.items.filter { it.id != matchingTemplateItem.id })
            checklistRepository.updateChecklistTemplate(updatedChecklist)
        }

        return DispatchOutcome.Success("chat_dispatch_deleted", listOf(matchingFillItem.text, checklist.name), linkedChecklistId = checklist.id)
    }

    // ─── CompleteItem ─────────────────────────────────────────────────────────

    private suspend fun handleCompleteItem(toolCall: ToolCall.CompleteItem): DispatchOutcome {
        val (checklist, fill) = resolveChecklistAndFill(toolCall.checklistId, toolCall.checklistHint)
            ?: return resolveChecklistFailure(toolCall.checklistId, toolCall.checklistHint)

        val matchingItem = fill.items.firstOrNull { it.text.contains(toolCall.itemText, ignoreCase = true) }
            ?: return DispatchOutcome.NotFound("chat_dispatch_item_not_found", listOf(toolCall.itemText, checklist.name))

        if (matchingItem.checked) {
            // Nothing changed → nothing to undo (an "Undo" chip here would uncheck an item the
            // user checked themselves earlier).
            return DispatchOutcome.Success("chat_dispatch_already_done", listOf(matchingItem.text), linkedChecklistId = checklist.id)
        }

        val updatedFill = fill.copy(
            items = fill.items.map { if (it.id == matchingItem.id) it.withChecked(true) else it }
        )
        checklistRepository.updateFill(updatedFill)

        return DispatchOutcome.Success(
            "chat_dispatch_completed",
            listOf(matchingItem.text, checklist.name),
            linkedChecklistId = checklist.id,
            undo = UndoHandle.CompletedItem(
                checklistId = checklist.id,
                fillId = fill.id,
                fillItemId = matchingItem.id,
                itemText = matchingItem.text,
            ),
        )
    }

    // ─── CreateChecklist ──────────────────────────────────────────────────────

    private suspend fun handleCreateChecklist(toolCall: ToolCall.CreateChecklist): DispatchOutcome {
        // Premium gate: check if free user is at checklist limit
        val userData = userDataRepository.getUserData()
        if (!userData.isPremium) {
            val allChecklists = checklistRepository.projects.first()
            if (allChecklists.size >= FREE_CHECKLIST_LIMIT) {
                return DispatchOutcome.RequiresPremium
            }
        }

        val items = toolCall.initialItems.map { ChecklistItem(text = it, checked = false) }
        val newChecklist = Checklist(
            id = 0L,
            name = toolCall.name,
            items = items,
        )
        val newChecklistId = checklistRepository.addChecklist(newChecklist)
        onChecklistCreated(newChecklistId, ChecklistSource.CHAT, items.size)

        return when (toolCall.initialItems.size) {
            0 -> DispatchOutcome.Success("chat_dispatch_created_empty", listOf(toolCall.name), linkedChecklistId = newChecklistId)
            1 -> DispatchOutcome.Success("chat_dispatch_created_with_one", listOf(toolCall.name), linkedChecklistId = newChecklistId)
            else -> DispatchOutcome.Success("chat_dispatch_created_with_many", listOf(toolCall.name, toolCall.initialItems.size.toString()), linkedChecklistId = newChecklistId)
        }
    }

    // ─── SetItemReminder ──────────────────────────────────────────────────────

    private suspend fun handleSetItemReminder(toolCall: ToolCall.SetItemReminder): DispatchOutcome {
        val (checklist, fill) = resolveChecklistAndFill(toolCall.checklistId, toolCall.checklistHint)
            ?: return resolveChecklistFailure(toolCall.checklistId, toolCall.checklistHint)

        val matchingItem = fill.items.firstOrNull { it.text.contains(toolCall.itemText, ignoreCase = true) }
            ?: return DispatchOutcome.NotFound("chat_dispatch_item_not_found", listOf(toolCall.itemText, checklist.name))

        val updatedFill = fill.copy(
            items = fill.items.map {
                if (it.id == matchingItem.id) it.withReminderAt(toolCall.at) else it
            }
        )
        checklistRepository.updateFill(updatedFill)

        return DispatchOutcome.Success(
            "chat_dispatch_reminder_set",
            listOf(matchingItem.text, dateFormatter.formatDateTime(toolCall.at)),
            linkedChecklistId = checklist.id,
        )
    }

    // ─── MoveAllReminders ─────────────────────────────────────────────────────

    private suspend fun handleMoveAllReminders(toolCall: ToolCall.MoveAllReminders): DispatchOutcome {
        val reminders = checklistRepository.getRemindersInRange(
            toolCall.fromDayStartMs,
            toolCall.fromDayEndMs,
        )

        if (reminders.isEmpty()) {
            return DispatchOutcome.NotFound(
                "chat_dispatch_no_reminders_on_day",
                listOf(dateFormatter.formatDay(toolCall.fromDayStartMs)),
            )
        }

        val offsetMs = toolCall.toDayStartMs - toolCall.fromDayStartMs

        // Move checklist-level reminders
        val allChecklists = checklistRepository.projects.first()
        var movedCount = 0

        for (checklist in allChecklists) {
            val reminderAt = checklist.reminderAt ?: continue
            if (reminderAt in toolCall.fromDayStartMs..toolCall.fromDayEndMs) {
                val newChecklist = checklist.copy(reminderAt = reminderAt + offsetMs)
                checklistRepository.updateChecklistTemplate(newChecklist)
                movedCount++
            }
        }

        val targetDay = dateFormatter.formatDay(toolCall.toDayStartMs)
        return when (movedCount) {
            1 -> DispatchOutcome.Success("chat_dispatch_moved_one", listOf(targetDay))
            else -> DispatchOutcome.Success("chat_dispatch_moved_many", listOf(movedCount.toString(), targetDay))
        }
    }

    // ─── FindItemsQuery ───────────────────────────────────────────────────────

    private suspend fun handleFind(toolCall: ToolCall.FindItemsQuery): DispatchOutcome {
        if (toolCall.query.isBlank()) {
            return DispatchOutcome.NotFound("chat_dispatch_find_blank", emptyList())
        }

        val allChecklists = checklistRepository.projects.first()
        val results = mutableListOf<Pair<String, String>>() // (checklistName, itemText)

        for (checklist in allChecklists) {
            for (item in checklist.items) {
                if (item.text.contains(toolCall.query, ignoreCase = true)) {
                    results.add(checklist.name to item.text)
                    if (results.size >= MAX_FIND_RESULTS) break
                }
            }
            if (results.size >= MAX_FIND_RESULTS) break
        }

        return if (results.isEmpty()) {
            DispatchOutcome.NotFound("chat_dispatch_find_no_match", listOf(toolCall.query))
        } else {
            val summary = results.take(5).joinToString("; ") { (list, item) -> "«$item» in $list" }
            val suffix = if (results.size > 5) " (+${results.size - 5} more)" else ""
            DispatchOutcome.Success("chat_dispatch_find_success", listOf(results.size.toString(), "$summary$suffix"))
        }
    }

    // ─── CreateChecklistFromAttachment ───────────────────────────────────────

    /**
     * Routes attachment(s) through the [AiAnalyzer] service (same path as "Create via AI")
     * and creates a new checklist from the extracted items.
     *
     * Premium gate: same as [handleCreateChecklist] — free users are limited to
     * [FREE_CHECKLIST_LIMIT] total checklists (RC-driven; hardcoded here as fallback).
     * Credits gate: server-side. The Cloud Function (analyze_and_fill_checklist /
     * generate_checklist) atomically deducts credits inside a Firestore transaction
     * and returns the updated balance in the response.
     */
    private suspend fun handleCreateChecklistFromAttachment(
        toolCall: ToolCall.CreateChecklistFromAttachment,
    ): DispatchOutcome {
        // Premium gate
        val userData = userDataRepository.getUserData()
        if (!userData.isPremium) {
            val allChecklists = checklistRepository.projects.first()
            if (allChecklists.size >= FREE_CHECKLIST_LIMIT) {
                return DispatchOutcome.RequiresPremium
            }
        }

        if (toolCall.attachments.isEmpty()) {
            return DispatchOutcome.NotFound("chat_attach_no_files", emptyList())
        }

        // Use the first attachment to drive the analysis (multi-attachment support is
        // straightforward but Gemini API takes one primary input per call; Phase 3
        // can fan-out and merge results if needed).
        val primary = toolCall.attachments.first()
        val inputData = chatAttachmentToAnalyzeInput(primary)
            ?: return DispatchOutcome.NotFound("chat_attach_unsupported_type", listOf(primary.mimeType))

        logger.debug(TAG, "CreateChecklistFromAttachment: analyzing ${primary.fileName} via AiAnalyzer service")
        val result = aiAnalyzer.analyze(inputData, targetChecklist = null)

        return result.fold(
            onSuccess = { analyzeResult ->
                if (analyzeResult.suggestedItems.isEmpty()) {
                    return DispatchOutcome.NotFound("chat_attach_analyze_empty", listOf(primary.fileName))
                }
                val items = analyzeResult.suggestedItems.map {
                    ChecklistItem(text = it.text, checked = false)
                }
                // Derive checklist name from the file name (strip extension)
                val checklistName = primary.fileName
                    .substringBeforeLast('.')
                    .trim()
                    .ifBlank { primary.fileName }

                val newChecklist = Checklist(id = 0L, name = checklistName, items = items)
                val newId = checklistRepository.addChecklist(newChecklist)
                onChecklistCreated(newId, ChecklistSource.ATTACHMENT, items.size)
                logger.info(TAG, "CreateChecklistFromAttachment: created checklist '$checklistName' id=$newId items=${items.size}")
                DispatchOutcome.Success(
                    "chat_dispatch_created_from_attachment",
                    listOf(checklistName, items.size.toString()),
                    linkedChecklistId = newId,
                )
            },
            onFailure = { e ->
                logger.error(TAG, "CreateChecklistFromAttachment: analyze failed — ${e.message}", e)
                DispatchOutcome.NotFound("chat_attach_analyze_failed", listOf(primary.fileName))
            },
        )
    }

    // ─── AttachToItem ─────────────────────────────────────────────────────────

    /**
     * Stores attachment files via [AttachmentStoragePort] and appends them to the
     * matching [ChecklistFillItem.attachments] list.
     *
     * Free tier: max [FREE_ATTACH_LIMIT_PER_ITEM] attachments per item (mirrors
     * item-attachments quota). Premium: unlimited.
     *
     * File-first cleanup order (item-attachments solution principle): if DB write fails
     * after files are stored, the stored files become orphans — acceptable (next cleanup
     * cycle handles them). The reverse (DB written, file never stored) silently breaks
     * the attachment path in the UI — worse UX.
     */
    private suspend fun handleAttachToItem(toolCall: ToolCall.AttachToItem): DispatchOutcome {
        if (toolCall.attachments.isEmpty()) {
            return DispatchOutcome.NotFound("chat_attach_no_files", emptyList())
        }

        val (checklist, fill) = resolveChecklistAndFill(toolCall.checklistId, toolCall.checklistHint)
            ?: return resolveChecklistFailure(toolCall.checklistId, toolCall.checklistHint)

        // Item disambiguation — must produce exactly 1 match (AmbiguousMatch if >1)
        val matches = fill.items.filter {
            it.text.contains(toolCall.itemText, ignoreCase = true)
        }
        val matchingItem = when {
            matches.isEmpty() -> return DispatchOutcome.NotFound(
                "chat_dispatch_item_not_found",
                listOf(toolCall.itemText, checklist.name),
            )
            matches.size > 1 -> return DispatchOutcome.AmbiguousMatch(
                matches.take(MAX_AMBIGUOUS_CANDIDATES).map { it.text },
            )
            else -> matches.first()
        }

        // Free-tier attachment quota check
        val userData = userDataRepository.getUserData()
        val existingCount = matchingItem.attachments.size
        if (!userData.isPremium && existingCount >= FREE_ATTACH_LIMIT_PER_ITEM) {
            return DispatchOutcome.RequiresPremium
        }

        // Store files (file-first order)
        val storedAttachments = mutableListOf<Attachment>()
        for (chatAttachment in toolCall.attachments) {
            val stored = runCatching {
                attachmentStorage.storeAttachment(
                    sourcePath = chatAttachment.sourcePath,
                    fillId = fill.id,
                    itemId = matchingItem.id,
                    attachmentId = generateAttachmentId(),
                    originalFileName = chatAttachment.fileName,
                )
            }.getOrNull()

            if (stored == null) {
                logger.warning(TAG, "AttachToItem: failed to store ${chatAttachment.fileName}, skipping")
                continue
            }

            val (w, h) = runCatching {
                attachmentStorage.probeImage(stored, chatAttachment.mimeType)
            }.getOrDefault(Pair(null, null))

            storedAttachments.add(
                Attachment(
                    id = Attachment.generateId(),
                    path = stored,
                    fileName = chatAttachment.fileName,
                    mimeType = chatAttachment.mimeType,
                    sizeBytes = chatAttachment.sizeBytes,
                    createdAt = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                    width = w,
                    height = h,
                )
            )
        }

        if (storedAttachments.isEmpty()) {
            return DispatchOutcome.NotFound("chat_attach_store_failed", listOf(toolCall.attachments.first().fileName))
        }

        // Append to fill item (immutable update via withAttachments helper)
        val updatedItem = matchingItem.withAttachments(
            matchingItem.attachments + storedAttachments
        )
        val updatedFill = fill.copy(
            items = fill.items.map { if (it.id == matchingItem.id) updatedItem else it }
        )
        checklistRepository.updateFill(updatedFill)

        logger.info(TAG, "AttachToItem: attached ${storedAttachments.size} file(s) to '${matchingItem.text}' in '${checklist.name}'")

        return if (storedAttachments.size == 1) {
            DispatchOutcome.Success(
                "chat_dispatch_attached_one",
                listOf(storedAttachments.first().fileName, matchingItem.text, checklist.name),
                linkedChecklistId = checklist.id,
            )
        } else {
            DispatchOutcome.Success(
                "chat_dispatch_attached_many",
                listOf(storedAttachments.size.toString(), matchingItem.text, checklist.name),
                linkedChecklistId = checklist.id,
            )
        }
    }

    // ─── AddItems ─────────────────────────────────────────────────────────────

    private suspend fun handleAddItems(toolCall: ToolCall.AddItems): DispatchOutcome {
        if (toolCall.itemTexts.isEmpty()) {
            return DispatchOutcome.NotFound("chat_dispatch_add_empty", emptyList())
        }

        val (checklist, fill) = resolveChecklistAndFill(toolCall.checklistId, toolCall.checklistHint)
            ?: return resolveChecklistFailure(toolCall.checklistId, toolCall.checklistHint)

        // Pair each template item with its fill row via templateItemId (see handleAddItem note) so the
        // rows are never unlinked legacy rows that folder-mode would dump at the bottom.
        val newTemplateItems = toolCall.itemTexts.map { ChecklistItem(text = it, checked = false) }
        val newFillItems = newTemplateItems.map {
            ChecklistFillItem(text = it.text, checked = false, templateItemId = it.id)
        }
        val updatedFill = fill.copy(items = fill.items + newFillItems)
        checklistRepository.updateFill(updatedFill)

        val updatedChecklist = checklist.copy(items = checklist.items + newTemplateItems)
        checklistRepository.updateChecklistTemplate(updatedChecklist)

        return DispatchOutcome.Success(
            "chat_dispatch_added_many_to",
            listOf(toolCall.itemTexts.size.toString(), checklist.name),
            linkedChecklistId = checklist.id,
        )
    }

    // ─── ReadChecklist ────────────────────────────────────────────────────────

    private suspend fun handleReadChecklist(toolCall: ToolCall.ReadChecklist): DispatchOutcome {
        // Agent-only read: the model names a list, it never knows local row ids → name path.
        val (checklist, fill) = resolveChecklistAndFill(id = null, hint = toolCall.name)
            ?: return resolveChecklistFailure(id = null, hint = toolCall.name)

        val items = fill.items.map { item ->
            ReadChecklistItem(text = item.text, checked = item.checked)
        }
        return DispatchOutcome.ChecklistContent(
            checklistName = checklist.name,
            items = items,
            checklistId = checklist.id,
        )
    }

    // ─── RenameChecklist ──────────────────────────────────────────────────────

    private suspend fun handleRenameChecklist(toolCall: ToolCall.RenameChecklist): DispatchOutcome {
        val allChecklists = checklistRepository.projects.first()
        val matches = allChecklists.filter { it.name.contains(toolCall.checklistHint, ignoreCase = true) }
        val checklist = when {
            matches.isEmpty() -> return DispatchOutcome.NotFound(
                "chat_dispatch_no_checklist_match",
                listOf(toolCall.checklistHint),
            )
            matches.size == 1 -> matches.first()
            else -> return DispatchOutcome.AmbiguousMatch(matches.take(MAX_AMBIGUOUS_CANDIDATES).map { it.name })
        }

        val oldName = checklist.name
        checklistRepository.updateChecklistTemplate(checklist.copy(name = toolCall.newName))

        return DispatchOutcome.Success(
            "chat_dispatch_renamed",
            listOf(oldName, toolCall.newName),
            linkedChecklistId = checklist.id,
        )
    }

    // ─── ClearCompleted ───────────────────────────────────────────────────────

    /**
     * Bulk-clears the checked items of one checklist. Resolves the target with the same id-first,
     * name-fallback strategy the item tools use (see [resolveChecklistAndFill]); only the target is
     * resolved here — the repository re-loads and does the fill+template dual-write authoritatively.
     *
     * Ambiguity routes to the which-list picker, never a prose question:
     * - hint matches >1 list → [DispatchOutcome.AmbiguousMatch].
     * - no target and ≥2 lists exist → also [DispatchOutcome.AmbiguousMatch]. Unlike add/delete
     *   (which default to the sole/active list), a bulk clear is destructive enough that it never
     *   guesses across several lists.
     *
     * Zero checked items is a friendly Success ("nothing to remove"), not an error — the user asked
     * for a valid operation that happened to have no effect.
     */
    private suspend fun handleClearCompleted(toolCall: ToolCall.ClearCompleted): DispatchOutcome {
        val allChecklists = checklistRepository.projects.first()
        if (allChecklists.isEmpty()) {
            return DispatchOutcome.NotFound("chat_dispatch_no_checklists", emptyList())
        }

        val hint = toolCall.checklistHint
        val checklist = when {
            // Exact target (a tapped which-list chip) — resolve by id or fail; never degrade to name.
            toolCall.checklistId != null ->
                allChecklists.firstOrNull { it.id == toolCall.checklistId }
                    ?: return resolveChecklistFailure(toolCall.checklistId, hint)

            // Server-built call → fuzzy name match; 0 → not found, 1 → proceed, >1 → picker.
            hint != null -> {
                val matches = allChecklists.filter { it.name.contains(hint, ignoreCase = true) }
                when {
                    matches.isEmpty() ->
                        return DispatchOutcome.NotFound("chat_dispatch_no_checklist_match", listOf(hint))
                    matches.size > 1 ->
                        return DispatchOutcome.AmbiguousMatch(matches.take(MAX_AMBIGUOUS_CANDIDATES).map { it.name })
                    else -> matches.first()
                }
            }

            // No target: 1 list → clear it; ≥2 → ask which, never guess for a bulk delete.
            else -> allChecklists.singleOrNull()
                ?: return DispatchOutcome.AmbiguousMatch(allChecklists.take(MAX_AMBIGUOUS_CANDIDATES).map { it.name })
        }

        val removed = checklistRepository.deleteCompletedItems(checklist.id)
        return if (removed == 0) {
            DispatchOutcome.Success(
                "chat_dispatch_no_completed_items",
                listOf(checklist.name),
                linkedChecklistId = checklist.id,
            )
        } else {
            DispatchOutcome.Success(
                "chat_dispatch_completed_items_removed",
                listOf(removed.toString(), checklist.name),
                linkedChecklistId = checklist.id,
            )
        }
    }

    // ─── Attachment helpers ───────────────────────────────────────────────────

    /**
     * Converts a [ChatAttachment] to the appropriate [AnalyzeInputData] variant.
     * Returns null for unknown / unsupported MIME types.
     */
    private fun chatAttachmentToAnalyzeInput(attachment: ChatAttachment): AnalyzeInputData? =
        when (mimeTypeToAttachmentSource(attachment.mimeType)) {
            AttachmentSource.Image -> AnalyzeInputData.Photo(
                filePath = attachment.sourcePath,
                mimeType = attachment.mimeType,
            )
            AttachmentSource.Pdf -> AnalyzeInputData.PdfDocument(
                filePath = attachment.sourcePath,
                fileName = attachment.fileName,
            )
            AttachmentSource.Text -> AnalyzeInputData.TextFile(
                filePath = attachment.sourcePath,
            )
            AttachmentSource.Audio -> AnalyzeInputData.Audio(
                filePath = attachment.sourcePath,
                mimeType = attachment.mimeType,
            )
            null -> null
        }

    private fun generateAttachmentId(): String =
        "chat_attach_${kotlin.time.Clock.System.now().toEpochMilliseconds()}_${(0..99999).random()}"

    // ─── Activation funnel ────────────────────────────────────────────────────

    /**
     * Notifies the [ActivationCoordinator] that an AI-path checklist was created. The coordinator
     * owns the new-user / show-once gating, so this is a fire-and-forget hand-off from every create
     * path. Reads the `activation_bundle_v1` RC flag here (fail-open default ON — see
     * [RemoteConfigKeys.ACTIVATION_BUNDLE_V1]); never wrapped in a timeout.
     */
    /**
     * The single post-create hook for every checklist this dispatcher persists.
     *
     * Analytics + the activation funnel are folded into ONE call on purpose: they used to be two
     * independent concerns and only [notifyActivation] was ever wired here, so both chat creation
     * paths — including the flagship `create_checklist` tool call — were completely absent from
     * `checklist_created`. Binding them together means a future create path cannot pick up the
     * activation funnel while silently skipping the create funnel.
     *
     * Fire-and-report: an analytics failure must never turn a successful create into an error, so
     * the tracker call is guarded and only logged (the checklist IS created either way).
     */
    private suspend fun onChecklistCreated(checklistId: Long, source: ChecklistSource, itemCount: Int) {
        runCatching {
            analyticsTracker.event(
                AnalyticsEvents.Checklist.CREATED,
                mapOf(
                    AnalyticsParams.SOURCE to source.wire,
                    AnalyticsParams.CHECKLIST_ID to checklistId,
                    AnalyticsParams.ITEM_COUNT to itemCount,
                ),
            )
        }.onFailure { e ->
            logger.warning(TAG, "checklist_created (source=${source.wire}) not reported: ${e.message}")
        }
        notifyActivation(checklistId)
    }

    private suspend fun notifyActivation(checklistId: Long) {
        val activationEnabled = remoteConfigProvider.getBoolean(
            RemoteConfigKeys.ACTIVATION_BUNDLE_V1,
            RemoteConfigDefaults.ACTIVATION_BUNDLE_V1,
        )
        activationCoordinator.onAiChecklistCreated(checklistId, activationEnabled)
    }

    // ─── Resolution helpers ───────────────────────────────────────────────────

    /**
     * Resolves a tool call's target to a (Checklist, ChecklistFill) pair.
     * Returns null on failure — call [resolveChecklistFailure] with the SAME arguments to
     * turn that null into the [DispatchOutcome] explaining it.
     *
     * Precedence is [id] → [hint] → default, and the first step is not a preference but a
     * guarantee: when [id] is set the caller has already decided WHICH list it means (the
     * user tapped its chip, or it is their remembered default, or it is the list open behind
     * the dock), so no amount of name similarity may override it.
     *
     * **A missed [id] never retries by name.** If the row is gone — deleted between building
     * the chip and tapping it — this returns null and the caller reports it. Retrying by name
     * would silently hand the write to a same-named neighbour, which is the entire failure ids
     * exist to prevent (same reasoning as [UndoHandle], which is id-only for the identical
     * reason on the rollback side).
     */
    private suspend fun resolveChecklistAndFill(id: Long?, hint: String?): Pair<Checklist, ChecklistFill>? {
        val allChecklists = checklistRepository.projects.first()
        if (allChecklists.isEmpty()) return null

        val checklist = when {
            // Exact target — resolve by id or fail; NEVER degrade to the name path below.
            id != null -> allChecklists.firstOrNull { it.id == id }
            // Server-built call (Layer 2/3 knows names, not row ids) → fuzzy name match.
            hint != null -> {
                val matches = allChecklists.filter { it.name.contains(hint, ignoreCase = true) }
                // 0 → not found, >1 → ambiguous. resolveChecklistFailure tells them apart.
                matches.singleOrNull()
            }
            // No target at all → the active/default list.
            else -> allChecklists.firstOrNull()
        } ?: return null

        val fill = checklistRepository.getDefaultFillOneShot(checklist.id) ?: return null
        return checklist to fill
    }

    /**
     * Returns the appropriate [DispatchOutcome] when [resolveChecklistAndFill] returns null.
     * Must be called with the same [id] / [hint] the resolve was attempted with.
     */
    private suspend fun resolveChecklistFailure(id: Long?, hint: String?): DispatchOutcome {
        val allChecklists = checklistRepository.projects.first()

        if (id != null) {
            val byId = allChecklists.firstOrNull { it.id == id }
            // The id resolved, so the only way to get here is a failed fill load.
            if (byId != null) {
                return DispatchOutcome.NotFound("chat_dispatch_fill_load_failed", listOf(byId.name))
            }
            // The list is gone. Report it by the name we captured with the id — deliberately NOT
            // an AmbiguousMatch over same-named survivors: re-asking "which Shopping?" after the
            // user already answered that question is how the picker loops.
            return if (hint != null) {
                DispatchOutcome.NotFound("chat_dispatch_no_checklist_match", listOf(hint))
            } else {
                DispatchOutcome.NotFound("chat_dispatch_no_checklists", emptyList())
            }
        }

        if (hint == null) {
            return DispatchOutcome.NotFound("chat_dispatch_no_checklists", emptyList())
        }

        val matches = allChecklists.filter { it.name.contains(hint, ignoreCase = true) }
        return when {
            matches.isEmpty() -> DispatchOutcome.NotFound("chat_dispatch_no_checklist_match", listOf(hint))
            matches.size > 1 -> DispatchOutcome.AmbiguousMatch(matches.take(MAX_AMBIGUOUS_CANDIDATES).map { it.name })
            else -> DispatchOutcome.NotFound("chat_dispatch_fill_load_failed", listOf(matches.first().name))
        }
    }

}
