package com.antonchuraev.homesearchchecklist.appfunctions

import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionLimitExceededException
import androidx.appfunctions.AppFunctionNotSupportedException
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Pure translation layer between AppFunction arguments and the AI chat's [ToolCall] /
 * [DispatchOutcome] contract. No Android, no I/O: the service does the dispatching and the
 * repository reads, this class decides what to send and what an outcome means for an agent.
 *
 * **Inputs come from a model, not from UI controls.** The AppFunctions library passes `null` / `""`
 * / empty list for an omitted parameter and ignores Kotlin defaults, so every argument is
 * normalised here. Blank item text is rejected outright: the dispatcher matches items with
 * `contains(text, ignoreCase = true)`, and `contains("")` is true for EVERY item — a blank
 * `completeItem` would tick off whatever item happens to be first.
 *
 * **Outcome mapping** couples to the dispatcher's `chat_dispatch_*` message keys (the only typed
 * signal [DispatchOutcome.Success] / [DispatchOutcome.NotFound] carry). The keys below are copies of
 * the dispatcher's literals and nothing checks them against it: renaming a key in
 * `ToolCallDispatcherImpl` turns that mapping into [AppFunctionAppUnknownException] (key in the
 * message) and an "already done" flag into `false` — degraded, never a silent success.
 */
class ChecklistAppFunctionMapper(
    private val zoneId: () -> ZoneId = { ZoneId.systemDefault() },
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {

    // ─── Arguments → ToolCall ────────────────────────────────────────────────

    fun createChecklist(name: String?, items: List<String>?): ToolCall.CreateChecklist =
        ToolCall.CreateChecklist(
            name = requireText(name, "name", "the checklist name"),
            initialItems = cleanItems(items),
        )

    fun addItems(checklistName: String?, checklistId: Long?, items: List<String>?): ToolCall.AddItems {
        val texts = cleanItems(items)
        if (texts.isEmpty()) {
            throw AppFunctionInvalidArgumentException("items is empty: pass at least one non-blank item text.")
        }
        return ToolCall.AddItems(
            checklistHint = optionalText(checklistName),
            itemTexts = texts,
            checklistId = optionalId(checklistId),
        )
    }

    fun completeItem(checklistName: String?, checklistId: Long?, itemText: String?): ToolCall.CompleteItem =
        ToolCall.CompleteItem(
            checklistHint = optionalText(checklistName),
            itemText = requireText(itemText, "itemText", "the item text"),
            checklistId = optionalId(checklistId),
        )

    fun setItemReminder(
        checklistName: String?,
        checklistId: Long?,
        itemText: String?,
        remindAt: LocalDateTime?,
    ): ToolCall.SetItemReminder {
        val text = requireText(itemText, "itemText", "the item text")
        if (remindAt == null) {
            throw AppFunctionInvalidArgumentException("remindAt is missing: pass the local date and time of the reminder.")
        }
        val atMillis = toEpochMillis(remindAt)
        if (atMillis <= nowMillis()) {
            throw AppFunctionInvalidArgumentException(
                "remindAt $remindAt is in the past for the device's time zone (${zoneId().id}). Pass a future time.",
            )
        }
        val hint = optionalText(checklistName)
        val id = optionalId(checklistId)
        // A target is required here (unlike addItems / completeItem): the service resolves it through
        // reminderTargetRead BEFORE writing, and ReadChecklist cannot express the dispatcher's
        // "no target → first list" default.
        if (hint == null && id == null) {
            throw AppFunctionInvalidArgumentException(
                "Pass checklistName or checklistId for the checklist that holds the item. listChecklists returns both.",
            )
        }
        return ToolCall.SetItemReminder(checklistHint = hint, itemText = text, at = atMillis, checklistId = id)
    }

    /**
     * The read that resolves [call]'s checklist through the dispatcher's OWN resolution, before the
     * write. ReadChecklist and SetItemReminder both go through `resolveChecklistAndFill(id, hint)` with
     * the same arguments, so this answers "which list will the reminder land in" without a second
     * copy of the name-matching rules.
     */
    fun reminderTargetRead(call: ToolCall.SetItemReminder): ToolCall.ReadChecklist =
        ToolCall.ReadChecklist(name = call.checklistHint.orEmpty(), checklistId = call.checklistId)

    /**
     * Refuses a reminder on an item that repeats.
     *
     * The dispatcher's SetItemReminder only sets `reminderAt`; it leaves `repeatRule` and the repeat
     * alarm in place, so "set a reminder at 9" on a daily item would add a one-shot on top of a
     * schedule the agent believes it replaced. Changing a repeating schedule stays an in-app action.
     *
     * The item is picked with the dispatcher's item rule — the FIRST fill row whose text contains
     * [itemText], ignoring case (`ToolCallDispatcherImpl.handleSetItemReminder`). The service then
     * pins the write to the same checklist id, so both look at the same rows.
     */
    fun requireNoRepeat(items: List<ChecklistFillItem>, itemText: String) {
        // No match: nothing to refuse here — the dispatch that follows reports item_not_found.
        val item = items.firstOrNull { it.text.contains(itemText, ignoreCase = true) } ?: return
        if (item.repeatRule != null) {
            throw AppFunctionNotSupportedException(
                "The item \"${item.text}\" has a repeating reminder. Its schedule can only be changed in the Gisti app.",
            )
        }
    }

    fun readChecklist(name: String?, checklistId: Long?): ToolCall.ReadChecklist {
        val id = optionalId(checklistId)
        val hint = optionalText(name)
        if (id == null && hint == null) {
            throw AppFunctionInvalidArgumentException(
                "Pass the checklist name or checklistId. listChecklists returns both.",
            )
        }
        // The dispatcher resolves by id first and only uses the name for the error message then.
        return ToolCall.ReadChecklist(name = hint.orEmpty(), checklistId = id)
    }

    fun findItems(query: String?): ToolCall.FindItemsQuery =
        ToolCall.FindItemsQuery(query = requireText(query, "query", "the text to search for"))

    fun renameChecklist(currentName: String?, newName: String?): ToolCall.RenameChecklist =
        ToolCall.RenameChecklist(
            checklistHint = requireText(currentName, "currentName", "the current checklist name"),
            newName = requireText(newName, "newName", "the new checklist name"),
        )

    fun moveItem(
        itemText: String?,
        toChecklistName: String?,
        fromChecklistName: String?,
        fromChecklistId: Long?,
    ): ToolCall.MoveItem =
        ToolCall.MoveItem(
            itemText = requireText(itemText, "itemText", "the item text"),
            toChecklistHint = requireText(toChecklistName, "toChecklistName", "the destination checklist name"),
            fromChecklistHint = optionalText(fromChecklistName),
            fromChecklistId = optionalId(fromChecklistId),
        )

    /**
     * With an exact source id only the name-matched destination can be ambiguous; without one the
     * dispatcher's AmbiguousMatch does not say which side matched several lists.
     */
    fun moveTargeting(call: ToolCall.MoveItem): Targeting =
        if (call.fromChecklistId != null) Targeting.NAME_ONLY else Targeting.MOVE_SOURCE_OR_DESTINATION

    /** Epoch millis of [time] interpreted in the device's current time zone. */
    fun toEpochMillis(time: LocalDateTime): Long = time.atZone(zoneId()).toInstant().toEpochMilli()

    // ─── DispatchOutcome → result / exception ────────────────────────────────

    /**
     * A successful mutation, reduced to what the service needs to build a result: the affected
     * checklist, the first message argument (the matched item text for item operations) and
     * whether the call changed nothing (item already done / already in the destination list).
     */
    data class Mutation(
        val checklistId: Long,
        val firstArg: String?,
        val noChange: Boolean,
    )

    /**
     * How an agent can get past an [DispatchOutcome.AmbiguousMatch]. It depends on the function: the
     * recovery hint must only offer an argument the function actually takes, otherwise the agent
     * retries with an id that is silently ignored and loops on the same ambiguity.
     */
    enum class Targeting {
        /** The function takes a `checklistId` that wins over the name. */
        BY_ID,
        /** The target is matched by name only (renameChecklist; moveItem's destination). */
        NAME_ONLY,
        /** moveItem without fromChecklistId: either the source (id-capable) or the destination (name-only). */
        MOVE_SOURCE_OR_DESTINATION,
    }

    /** Returns the [Mutation] for a Success, throws the matching [AppFunctionException] otherwise. */
    fun requireMutation(outcome: DispatchOutcome, targeting: Targeting = Targeting.BY_ID): Mutation {
        val success = outcome as? DispatchOutcome.Success ?: throw toException(outcome, targeting)
        val checklistId = success.linkedChecklistId
            ?: throw AppFunctionAppUnknownException(
                "The operation reported success without naming the affected checklist (${success.messageKey}).",
            )
        return Mutation(
            checklistId = checklistId,
            firstArg = success.args.firstOrNull(),
            noChange = success.messageKey in NO_CHANGE_KEYS,
        )
    }

    fun requireContent(outcome: DispatchOutcome): ChecklistContent {
        val content = outcome as? DispatchOutcome.ChecklistContent ?: throw toException(outcome)
        val id = content.checklistId
            ?: throw AppFunctionAppUnknownException("Checklist '${content.checklistName}' was read without an ID.")
        return ChecklistContent(
            id = id,
            name = content.checklistName,
            items = content.items.map { ChecklistEntry(text = it.text, checked = it.checked) },
        )
    }

    /** A search with no match is an empty result, not an error: the request itself was valid. */
    fun requireFound(outcome: DispatchOutcome): FoundItems {
        if (outcome is DispatchOutcome.NotFound && outcome.messageKey == KEY_FIND_NO_MATCH) {
            return FoundItems(matchCount = 0, summary = "")
        }
        val success = outcome as? DispatchOutcome.Success ?: throw toException(outcome)
        return FoundItems(
            matchCount = success.args.getOrNull(0)?.toIntOrNull() ?: 0,
            summary = success.args.getOrNull(1).orEmpty(),
        )
    }

    /** The agent-facing exception for any outcome that is not the success the caller expected. */
    fun toException(outcome: DispatchOutcome, targeting: Targeting = Targeting.BY_ID): AppFunctionException = when (outcome) {
        is DispatchOutcome.AmbiguousMatch -> ambiguousException(outcome.candidates, targeting)
        DispatchOutcome.RequiresPremium -> AppFunctionLimitExceededException(
            "The user's free plan limit for checklists is reached. This is a plan limit, not a rate " +
                "limit: retrying will not help. The user can upgrade to Premium in the Gisti app.",
        )
        is DispatchOutcome.NotFound -> notFoundException(outcome)
        is DispatchOutcome.Success,
        is DispatchOutcome.ChecklistContent -> AppFunctionAppUnknownException(
            "Unexpected result type ${outcome::class.simpleName} for this operation.",
        )
    }

    private fun ambiguousException(candidates: List<String>, targeting: Targeting): AppFunctionException {
        val names = candidates.joinToString(", ") { "\"$it\"" }
        val recovery = when (targeting) {
            Targeting.BY_ID ->
                "Ask the user which one, then call again with its checklistId from listChecklists " +
                    "(or a name that matches only one checklist)."
            Targeting.NAME_ONLY ->
                "This function matches checklists by name only (a part of the name matches). Call again " +
                    "with a longer name that only one checklist contains; if no such name exists, the user " +
                    "has to do this in the Gisti app."
            Targeting.MOVE_SOURCE_OR_DESTINATION ->
                "The source checklist can be given exactly with fromChecklistId from listChecklists. The " +
                    "destination (toChecklistName) is matched by name only: if it is the destination that " +
                    "matches several checklists and no longer name is unique, the user has to move the item " +
                    "in the Gisti app."
        }
        return AppFunctionInvalidArgumentException("Several checklists match that name: $names. $recovery")
    }

    private fun notFoundException(outcome: DispatchOutcome.NotFound): AppFunctionException {
        val a0 = outcome.args.getOrNull(0).orEmpty()
        val a1 = outcome.args.getOrNull(1).orEmpty()
        return when (outcome.messageKey) {
            KEY_ITEM_NOT_FOUND -> AppFunctionElementNotFoundException(
                "No item containing \"$a0\" in checklist \"$a1\". Call readChecklist to see its items.",
            )
            KEY_NO_CHECKLIST_MATCH -> AppFunctionElementNotFoundException(
                "No checklist matches \"$a0\". Call listChecklists to get the exact names.",
            )
            KEY_NO_CHECKLISTS -> AppFunctionElementNotFoundException(
                "The user has no checklists yet. Create one with createChecklist.",
            )
            KEY_FIND_NO_MATCH -> AppFunctionElementNotFoundException("No item contains \"$a0\".")
            KEY_FIND_BLANK -> AppFunctionInvalidArgumentException("query is blank: pass the text to search for.")
            KEY_ADD_EMPTY -> AppFunctionInvalidArgumentException("items is empty: pass at least one item text.")
            KEY_MOVE_BLOCKED -> AppFunctionNotSupportedException(
                "The item \"$a0\" has a reminder, a repeat rule or attachments, which a move would lose. " +
                    "The user can move it in the Gisti app.",
            )
            KEY_FILL_LOAD_FAILED -> AppFunctionAppUnknownException(
                "The items of checklist \"$a0\" could not be loaded. The user can open it in the Gisti app.",
            )
            KEY_OPERATION_FAILED -> AppFunctionAppUnknownException("The operation failed inside the app: $a0")
            else -> AppFunctionAppUnknownException("The operation failed (${outcome.messageKey}).")
        }
    }

    // ─── Argument normalisation ──────────────────────────────────────────────

    private fun requireText(value: String?, param: String, what: String): String =
        value?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw AppFunctionInvalidArgumentException("$param is blank: pass $what.")

    private fun optionalText(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    /** Omitted numeric arguments can arrive as 0; checklist ids start at 1. */
    private fun optionalId(value: Long?): Long? = value?.takeIf { it > 0L }

    private fun cleanItems(items: List<String>?): List<String> =
        items.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        // Copies of ToolCallDispatcherImpl message keys — not compiler-checked against it (see class KDoc).
        const val KEY_ALREADY_DONE = "chat_dispatch_already_done"
        const val KEY_MOVE_SAME_LIST = "chat_dispatch_move_same_list"
        const val KEY_ITEM_NOT_FOUND = "chat_dispatch_item_not_found"
        const val KEY_NO_CHECKLIST_MATCH = "chat_dispatch_no_checklist_match"
        const val KEY_NO_CHECKLISTS = "chat_dispatch_no_checklists"
        const val KEY_FIND_NO_MATCH = "chat_dispatch_find_no_match"
        const val KEY_FIND_BLANK = "chat_dispatch_find_blank"
        const val KEY_ADD_EMPTY = "chat_dispatch_add_empty"
        const val KEY_MOVE_BLOCKED = "chat_dispatch_move_blocked"
        const val KEY_FILL_LOAD_FAILED = "chat_dispatch_fill_load_failed"
        const val KEY_OPERATION_FAILED = "chat_dispatch_operation_failed"

        private val NO_CHANGE_KEYS = setOf(KEY_ALREADY_DONE, KEY_MOVE_SAME_LIST)
    }
}
