package com.antonchuraev.homesearchchecklist.aichat

import com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher.ToolCallDispatcher
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
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
) : ToolCallDispatcher {

    companion object {
        private const val FREE_CHECKLIST_LIMIT = 4 // mirrors RemoteConfigDefaults.MAX_CHECKLISTS_FREE
        private const val MAX_FIND_RESULTS = 10
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
        }
    }.getOrElse { e ->
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
            DispatchOutcome.Success("chat_dispatch_added_to", listOf(toolCall.itemText, checklist.name))
        } else {
            DispatchOutcome.Success("chat_dispatch_added", listOf(toolCall.itemText))
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

        return DispatchOutcome.Success("chat_dispatch_deleted", listOf(matchingFillItem.text, checklist.name))
    }

    // ─── CompleteItem ─────────────────────────────────────────────────────────

    private suspend fun handleCompleteItem(toolCall: ToolCall.CompleteItem): DispatchOutcome {
        val (checklist, fill) = resolveChecklistAndFill(toolCall.checklistHint)
            ?: return resolveChecklistFailure(toolCall.checklistHint)

        val matchingItem = fill.items.firstOrNull { it.text.contains(toolCall.itemText, ignoreCase = true) }
            ?: return DispatchOutcome.NotFound("chat_dispatch_item_not_found", listOf(toolCall.itemText, checklist.name))

        if (matchingItem.checked) {
            return DispatchOutcome.Success("chat_dispatch_already_done", listOf(matchingItem.text))
        }

        val updatedFill = fill.copy(
            items = fill.items.map { if (it.id == matchingItem.id) it.withChecked(true) else it }
        )
        checklistRepository.updateFill(updatedFill)

        return DispatchOutcome.Success("chat_dispatch_completed", listOf(matchingItem.text, checklist.name))
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
        checklistRepository.addChecklist(newChecklist)

        return when (toolCall.initialItems.size) {
            0 -> DispatchOutcome.Success("chat_dispatch_created_empty", listOf(toolCall.name))
            1 -> DispatchOutcome.Success("chat_dispatch_created_with_one", listOf(toolCall.name))
            else -> DispatchOutcome.Success("chat_dispatch_created_with_many", listOf(toolCall.name, toolCall.initialItems.size.toString()))
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

        return DispatchOutcome.Success("chat_dispatch_reminder_set", listOf(matchingItem.text, formatTimestamp(toolCall.at)))
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
