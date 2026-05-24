package com.antonchuraev.homesearchchecklist.aichat

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher.ToolCallDispatcher
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.mimeTypeToAttachmentSource
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
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * App-level implementation of [ToolCallDispatcher].
 *
 * Lives in composeApp to access the full dependency graph:
 * [ChecklistRepository] for all data mutations,
 * [UserDataRepository] for premium gate on [ToolCall.CreateChecklist].
 *
 * Checklist resolution strategy ([resolveChecklist]):
 *   1. hint != null → fuzzy name match (substring, case-insensitive) in all checklists.
 *      - 0 matches → [DispatchOutcome.NotFound]
 *      - 1 match → proceed
 *      - >1 matches → [DispatchOutcome.AmbiguousMatch] with top-3 names
 *   2. hint == null → use first checklist in the list (most recently positioned).
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
) : ToolCallDispatcher {

    companion object {
        private const val TAG = "ToolCallDispatcher"
        private const val FREE_CHECKLIST_LIMIT = 4 // mirrors RemoteConfigDefaults.MAX_CHECKLISTS_FREE
        private const val MAX_FIND_RESULTS = 10
        /** Free-tier max attachments per item (mirrors item-attachments FREE_ATTACHMENT_LIMIT_PER_ITEM). */
        private const val FREE_ATTACH_LIMIT_PER_ITEM = 3
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
        }
    }.getOrElse { e ->
        logger.error(TAG, "dispatch failed for ${toolCall::class.simpleName}", e)
        DispatchOutcome.NotFound("chat_dispatch_operation_failed", listOf(e.message ?: "unknown error"))
    }

    // ─── AddItem ──────────────────────────────────────────────────────────────

    private suspend fun handleAddItem(toolCall: ToolCall.AddItem): DispatchOutcome {
        val (checklist, fill) = resolveChecklistAndFill(toolCall.checklistHint)
            ?: return resolveChecklistFailure(toolCall.checklistHint)

        // Add item to fill
        val newFillItem = ChecklistFillItem(text = toolCall.itemText, checked = false)
        val updatedFill = fill.copy(items = fill.items + newFillItem)
        checklistRepository.updateFill(updatedFill)

        // Add item to template (dual-update as per CLAUDE.md)
        val newTemplateItem = ChecklistItem(text = toolCall.itemText, checked = false)
        val updatedChecklist = checklist.copy(items = checklist.items + newTemplateItem)
        checklistRepository.updateChecklistTemplate(updatedChecklist)

        return if (toolCall.checklistHint != null) {
            DispatchOutcome.Success("chat_dispatch_added_to", listOf(toolCall.itemText, checklist.name), linkedChecklistId = checklist.id)
        } else {
            DispatchOutcome.Success("chat_dispatch_added", listOf(toolCall.itemText), linkedChecklistId = checklist.id)
        }
    }

    // ─── DeleteItem ───────────────────────────────────────────────────────────

    private suspend fun handleDeleteItem(toolCall: ToolCall.DeleteItem): DispatchOutcome {
        val (checklist, fill) = resolveChecklistAndFill(toolCall.checklistHint)
            ?: return resolveChecklistFailure(toolCall.checklistHint)

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
        val (checklist, fill) = resolveChecklistAndFill(toolCall.checklistHint)
            ?: return resolveChecklistFailure(toolCall.checklistHint)

        val matchingItem = fill.items.firstOrNull { it.text.contains(toolCall.itemText, ignoreCase = true) }
            ?: return DispatchOutcome.NotFound("chat_dispatch_item_not_found", listOf(toolCall.itemText, checklist.name))

        if (matchingItem.checked) {
            return DispatchOutcome.Success("chat_dispatch_already_done", listOf(matchingItem.text), linkedChecklistId = checklist.id)
        }

        val updatedFill = fill.copy(
            items = fill.items.map { if (it.id == matchingItem.id) it.withChecked(true) else it }
        )
        checklistRepository.updateFill(updatedFill)

        return DispatchOutcome.Success("chat_dispatch_completed", listOf(matchingItem.text, checklist.name), linkedChecklistId = checklist.id)
    }

    // ─── CreateChecklist ──────────────────────────────────────────────────────

    private suspend fun handleCreateChecklist(toolCall: ToolCall.CreateChecklist): DispatchOutcome {
        // Premium gate: check if free user is at checklist limit
        val userData = userDataRepository.getUserData()
        if (!userData.isPremium) {
            val allChecklists = checklistRepository.checklists.first()
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

        return when (toolCall.initialItems.size) {
            0 -> DispatchOutcome.Success("chat_dispatch_created_empty", listOf(toolCall.name), linkedChecklistId = newChecklistId)
            1 -> DispatchOutcome.Success("chat_dispatch_created_with_one", listOf(toolCall.name), linkedChecklistId = newChecklistId)
            else -> DispatchOutcome.Success("chat_dispatch_created_with_many", listOf(toolCall.name, toolCall.initialItems.size.toString()), linkedChecklistId = newChecklistId)
        }
    }

    // ─── SetItemReminder ──────────────────────────────────────────────────────

    private suspend fun handleSetItemReminder(toolCall: ToolCall.SetItemReminder): DispatchOutcome {
        val (checklist, fill) = resolveChecklistAndFill(toolCall.checklistHint)
            ?: return resolveChecklistFailure(toolCall.checklistHint)

        val matchingItem = fill.items.firstOrNull { it.text.contains(toolCall.itemText, ignoreCase = true) }
            ?: return DispatchOutcome.NotFound("chat_dispatch_item_not_found", listOf(toolCall.itemText, checklist.name))

        val updatedFill = fill.copy(
            items = fill.items.map {
                if (it.id == matchingItem.id) it.withReminderAt(toolCall.at) else it
            }
        )
        checklistRepository.updateFill(updatedFill)

        return DispatchOutcome.Success("chat_dispatch_reminder_set", listOf(matchingItem.text, formatTimestamp(toolCall.at)), linkedChecklistId = checklist.id)
    }

    // ─── MoveAllReminders ─────────────────────────────────────────────────────

    private suspend fun handleMoveAllReminders(toolCall: ToolCall.MoveAllReminders): DispatchOutcome {
        val reminders = checklistRepository.getRemindersInRange(
            toolCall.fromDayStartMs,
            toolCall.fromDayEndMs,
        )

        if (reminders.isEmpty()) {
            return DispatchOutcome.NotFound("chat_dispatch_no_reminders_on_day", listOf(formatDay(toolCall.fromDayStartMs)))
        }

        val offsetMs = toolCall.toDayStartMs - toolCall.fromDayStartMs

        // Move checklist-level reminders
        val allChecklists = checklistRepository.checklists.first()
        var movedCount = 0

        for (checklist in allChecklists) {
            val reminderAt = checklist.reminderAt ?: continue
            if (reminderAt in toolCall.fromDayStartMs..toolCall.fromDayEndMs) {
                val newChecklist = checklist.copy(reminderAt = reminderAt + offsetMs)
                checklistRepository.updateChecklistTemplate(newChecklist)
                movedCount++
            }
        }

        return when (movedCount) {
            1 -> DispatchOutcome.Success("chat_dispatch_moved_one", listOf(formatDay(toolCall.toDayStartMs)))
            else -> DispatchOutcome.Success("chat_dispatch_moved_many", listOf(movedCount.toString(), formatDay(toolCall.toDayStartMs)))
        }
    }

    // ─── FindItemsQuery ───────────────────────────────────────────────────────

    private suspend fun handleFind(toolCall: ToolCall.FindItemsQuery): DispatchOutcome {
        if (toolCall.query.isBlank()) {
            return DispatchOutcome.NotFound("chat_dispatch_find_blank", emptyList())
        }

        val allChecklists = checklistRepository.checklists.first()
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
            val allChecklists = checklistRepository.checklists.first()
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

        val (checklist, fill) = resolveChecklistAndFill(toolCall.checklistHint)
            ?: return resolveChecklistFailure(toolCall.checklistHint)

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
                matches.take(3).map { it.text },
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

    // ─── Resolution helpers ───────────────────────────────────────────────────

    /**
     * Resolves [hint] to a (Checklist, ChecklistFill) pair.
     * Returns null on failure — call [resolveChecklistFailure] to get the [DispatchOutcome].
     */
    private suspend fun resolveChecklistAndFill(hint: String?): Pair<Checklist, ChecklistFill>? {
        val allChecklists = checklistRepository.checklists.first()
        if (allChecklists.isEmpty()) return null

        val checklist = if (hint == null) {
            allChecklists.firstOrNull()
        } else {
            val matches = allChecklists.filter { it.name.contains(hint, ignoreCase = true) }
            when {
                matches.isEmpty() -> null
                matches.size == 1 -> matches.first()
                else -> null // ambiguous — caller must use resolveChecklistFailure
            }
        } ?: return null

        val fill = checklistRepository.getDefaultFillOneShot(checklist.id) ?: return null
        return checklist to fill
    }

    /**
     * Returns the appropriate [DispatchOutcome] when [resolveChecklistAndFill] returns null.
     */
    private suspend fun resolveChecklistFailure(hint: String?): DispatchOutcome {
        if (hint == null) {
            return DispatchOutcome.NotFound("chat_dispatch_no_checklists", emptyList())
        }

        val allChecklists = checklistRepository.checklists.first()
        val matches = allChecklists.filter { it.name.contains(hint, ignoreCase = true) }
        return when {
            matches.isEmpty() -> DispatchOutcome.NotFound("chat_dispatch_no_checklist_match", listOf(hint))
            matches.size > 1 -> DispatchOutcome.AmbiguousMatch(matches.take(3).map { it.name })
            else -> DispatchOutcome.NotFound("chat_dispatch_fill_load_failed", listOf(matches.first().name))
        }
    }

    // ─── Time formatting ──────────────────────────────────────────────────────

    private fun formatTimestamp(epochMs: Long): String {
        val tz = TimeZone.currentSystemDefault()
        val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz)
        val h = dt.hour.toString().padStart(2, '0')
        val m = dt.minute.toString().padStart(2, '0')
        return "$h:$m"
    }

    private fun formatDay(epochMs: Long): String {
        val tz = TimeZone.currentSystemDefault()
        val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz)
        val dayName = dt.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercaseChar() }
        val monthName = dt.month.name.lowercase().replaceFirstChar { it.uppercaseChar() }
        return "$dayName, $monthName ${dt.dayOfMonth}"
    }
}
