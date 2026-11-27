package com.antonchuraev.homesearchchecklist.appfunctions

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionException
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher.ToolCallDispatcher
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/**
 * Gisti's AppFunctions: checklist operations that system agents (Gemini on Android 16+) can call.
 *
 * KSP generates the concrete `ChecklistAppFunctionService` (registered in the manifest) and the
 * `assets/gisti_app_function_service.xml` schema from this class. Function ids are
 * `"<this class FQCN>#<method>"` — renaming this class or a method changes the ids agents use.
 *
 * Every mutation goes through the AI chat's [ToolCallDispatcher], so free-tier ceilings, sync,
 * `checklist_created` analytics and the activation funnel behave exactly as in the chat. Only
 * non-destructive operations are exposed (no delete, no bulk clear, no bulk reminder move) and none
 * spends AI credits. Koin is already running: [com.antonchuraev.homesearchchecklist.GistiAndroidApplication]
 * starts it in `onCreate`, which precedes any service binding in a cold-started process.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "ChecklistAppFunctionService",
    appFunctionXmlFileName = "gisti_app_function_service",
)
abstract class BaseChecklistAppFunctionService : AppFunctionService() {

    private val dispatcher: ToolCallDispatcher by inject()
    private val checklistRepository: ChecklistRepository by inject()
    private val reminderScheduler: ChecklistReminderScheduler by inject()
    private val logger: AppLogger by inject()
    private val mapper = ChecklistAppFunctionMapper()

    /**
     * List the user's checklists with their IDs, names and item counts.
     *
     * The user's Inbox (quick-capture list) is not listed.
     *
     * @return The user's checklists in their own order; empty when there are none.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun listChecklists(): ChecklistsResult = execute("listChecklists") {
        ChecklistsResult(checklistRepository.projects.first().map { it.toSummary() })
    }

    /**
     * Create a new checklist, optionally with its first items.
     *
     * On the free plan the number of checklists is limited.
     *
     * @param name Name of the new checklist.
     * @param items Item texts to add, in order. Omit for an empty checklist; blank texts are skipped.
     * @return The created checklist.
     * @throws androidx.appfunctions.AppFunctionLimitExceededException If the free plan's checklist
     *   limit is reached; the user can upgrade to Premium in the app.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun createChecklist(name: String, items: List<String> = emptyList()): ChecklistSummary =
        execute("createChecklist") {
            val mutation = mapper.requireMutation(dispatcher.dispatch(mapper.createChecklist(name, items)))
            loadSummary(mutation.checklistId)
        }

    /**
     * Add one or more items to an existing checklist.
     *
     * @param items Item texts to add, in order. Blank texts are skipped; at least one is required.
     * @param checklistName Name of the target checklist; a part of the name matches when only one
     *   checklist contains it. If both this and checklistId are omitted, the items go to the first
     *   checklist in the user's order.
     * @param checklistId Exact ID of the target checklist from listChecklists. Takes precedence
     *   over checklistName.
     * @return The target checklist with its updated item count.
     * @throws androidx.appfunctions.AppFunctionInvalidArgumentException If several checklists match
     *   checklistName; the message lists them.
     * @throws androidx.appfunctions.AppFunctionElementNotFoundException If no checklist matches.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun addItems(
        items: List<String>,
        checklistName: String? = null,
        checklistId: Long? = null,
    ): ChecklistSummary = execute("addItems") {
        val mutation = mapper.requireMutation(
            dispatcher.dispatch(mapper.addItems(checklistName, checklistId, items)),
        )
        loadSummary(mutation.checklistId)
    }

    /**
     * Mark an item of a checklist as done.
     *
     * @param itemText Text of the item; the first item that contains it, ignoring case, is marked.
     * @param checklistName Name of the checklist holding the item; a part of the name matches when
     *   only one checklist contains it. If both this and checklistId are omitted, the first
     *   checklist in the user's order is used.
     * @param checklistId Exact ID of the checklist from listChecklists. Takes precedence over
     *   checklistName.
     * @return The matched item, and whether it was already done.
     * @throws androidx.appfunctions.AppFunctionElementNotFoundException If no checklist or no item
     *   matches.
     * @throws androidx.appfunctions.AppFunctionInvalidArgumentException If several checklists match
     *   checklistName; the message lists them.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun completeItem(
        itemText: String,
        checklistName: String? = null,
        checklistId: Long? = null,
    ): CompletedItem = execute("completeItem") {
        val mutation = mapper.requireMutation(
            dispatcher.dispatch(mapper.completeItem(checklistName, checklistId, itemText)),
        )
        CompletedItem(
            checklistId = mutation.checklistId,
            checklistName = loadChecklist(mutation.checklistId).name,
            itemText = mutation.firstArg ?: itemText,
            wasAlreadyCompleted = mutation.noChange,
        )
    }

    /**
     * Set a one-time reminder on an item of a checklist, replacing its previous one-time reminder.
     *
     * Items with a repeating reminder are refused: their schedule can only be changed in the app.
     *
     * @param itemText Text of the item; the first item that contains it, ignoring case, gets the
     *   reminder.
     * @param remindAt Date and time of the reminder in the device's local time zone. Must be in
     *   the future.
     * @param checklistName Name of the checklist holding the item; a part of the name matches when
     *   only one checklist contains it. Required unless checklistId is given.
     * @param checklistId Exact ID of the checklist from listChecklists. Takes precedence over
     *   checklistName.
     * @return The matched item and the reminder time.
     * @throws androidx.appfunctions.AppFunctionNotSupportedException If the item has a repeating
     *   reminder.
     * @throws androidx.appfunctions.AppFunctionInvalidArgumentException If remindAt is in the past,
     *   no checklist is given, or several checklists match checklistName.
     * @throws androidx.appfunctions.AppFunctionElementNotFoundException If no checklist or no item
     *   matches.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun setItemReminder(
        itemText: String,
        remindAt: LocalDateTime,
        checklistName: String? = null,
        checklistId: Long? = null,
    ): ItemReminder = execute("setItemReminder") {
        val requested = mapper.setItemReminder(checklistName, checklistId, itemText, remindAt)
        // Resolve the checklist through the dispatcher's own resolution BEFORE writing, refuse a
        // repeating item, then pin the write to that exact checklist id so it lands on the rows checked.
        val target = mapper.requireContent(dispatcher.dispatch(mapper.reminderTargetRead(requested)))
        checklistRepository.getDefaultFillOneShot(target.id)?.let { fill ->
            mapper.requireNoRepeat(fill.items, requested.itemText)
        }
        val call = requested.copy(checklistId = target.id)
        val mutation = mapper.requireMutation(dispatcher.dispatch(call))
        val matchedText = mutation.firstArg ?: call.itemText
        scheduleItemAlarm(mutation.checklistId, matchedText, call.at)
        ItemReminder(
            checklistId = mutation.checklistId,
            checklistName = loadChecklist(mutation.checklistId).name,
            itemText = matchedText,
            remindAt = remindAt,
        )
    }

    /**
     * Read the items of one checklist, with their done state.
     *
     * @param name Name of the checklist; a part of the name matches when only one checklist
     *   contains it. Required unless checklistId is given.
     * @param checklistId Exact ID of the checklist from listChecklists. Takes precedence over name.
     * @return The checklist and its items in display order.
     * @throws androidx.appfunctions.AppFunctionElementNotFoundException If no checklist matches.
     * @throws androidx.appfunctions.AppFunctionInvalidArgumentException If several checklists match
     *   name; the message lists them.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun readChecklist(name: String? = null, checklistId: Long? = null): ChecklistContent =
        execute("readChecklist") {
            mapper.requireContent(dispatcher.dispatch(mapper.readChecklist(name, checklistId)))
        }

    /**
     * Search item texts across all checklists, ignoring case.
     *
     * @param query Text to search for.
     * @return The number of matches (at most 10) and a short list of them; zero when none match.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun findItems(query: String): FoundItems = execute("findItems") {
        mapper.requireFound(dispatcher.dispatch(mapper.findItems(query)))
    }

    /**
     * Rename an existing checklist.
     *
     * The checklist is matched by name only, there is no ID parameter: a name that several
     * checklists contain is refused, and if no longer name is unique the rename has to be done in
     * the app.
     *
     * @param currentName Current name of the checklist; a part of the name matches when only one
     *   checklist contains it.
     * @param newName New name of the checklist.
     * @return The renamed checklist.
     * @throws androidx.appfunctions.AppFunctionElementNotFoundException If no checklist matches.
     * @throws androidx.appfunctions.AppFunctionInvalidArgumentException If several checklists match
     *   currentName; the message lists them.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun renameChecklist(currentName: String, newName: String): ChecklistSummary =
        execute("renameChecklist") {
            val mutation = mapper.requireMutation(
                dispatcher.dispatch(mapper.renameChecklist(currentName, newName)),
                ChecklistAppFunctionMapper.Targeting.NAME_ONLY,
            )
            loadSummary(mutation.checklistId)
        }

    /**
     * Move one item to another checklist, keeping its text and done state.
     *
     * Items with a reminder, a repeat rule or attachments are not moved. The destination is matched
     * by name only: if several checklists contain the name and no longer name is unique, the move
     * has to be done in the app.
     *
     * @param itemText Text of the item; the first item that contains it, ignoring case, is moved.
     * @param toChecklistName Name of the destination checklist; a part of the name matches when
     *   only one checklist contains it. There is no ID parameter for the destination.
     * @param fromChecklistName Name of the checklist the item is in now. If both this and
     *   fromChecklistId are omitted, the first checklist in the user's order is searched.
     * @param fromChecklistId Exact ID of the source checklist from listChecklists. Takes precedence
     *   over fromChecklistName.
     * @return The moved item and its new checklist.
     * @throws androidx.appfunctions.AppFunctionNotSupportedException If the item has a reminder, a
     *   repeat rule or attachments; the user can move it in the app.
     * @throws androidx.appfunctions.AppFunctionElementNotFoundException If no checklist or no item
     *   matches.
     * @throws androidx.appfunctions.AppFunctionInvalidArgumentException If several checklists match
     *   a name; the message lists them.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun moveItem(
        itemText: String,
        toChecklistName: String,
        fromChecklistName: String? = null,
        fromChecklistId: Long? = null,
    ): MovedItem = execute("moveItem") {
        val call = mapper.moveItem(itemText, toChecklistName, fromChecklistName, fromChecklistId)
        val mutation = mapper.requireMutation(dispatcher.dispatch(call), mapper.moveTargeting(call))
        MovedItem(
            itemText = mutation.firstArg ?: itemText,
            toChecklistId = mutation.checklistId,
            toChecklistName = loadChecklist(mutation.checklistId).name,
            wasAlreadyThere = mutation.noChange,
        )
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Runs [block] off the main thread (platform calls arrive on it). An [AppFunctionException] is
     * the agent-facing answer and passes through; anything else is logged with its throwable and
     * surfaced as [AppFunctionAppUnknownException] instead of escaping raw.
     */
    private suspend fun <T> execute(function: String, block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            runCatching { block() }.getOrElse { e ->
                when (e) {
                    is CancellationException -> throw e
                    is AppFunctionAppUnknownException -> {
                        logger.error(TAG, "$function failed: ${e.message}", e)
                        throw e
                    }
                    is AppFunctionException -> {
                        // Expected rejection (not found, ambiguous, free-plan limit): the agent gets
                        // the reason. Not a crash, so no recordException.
                        logger.warning(TAG, "$function rejected: ${e::class.simpleName}: ${e.message}")
                        throw e
                    }
                    else -> {
                        logger.error(TAG, "$function failed: ${e.message}", e)
                        throw AppFunctionAppUnknownException("$function failed inside the app: ${e.message}")
                    }
                }
            }
        }

    private suspend fun loadChecklist(id: Long): Checklist =
        checklistRepository.getChecklistById(id)
            ?: throw AppFunctionAppUnknownException("Checklist $id disappeared right after the operation.")

    private suspend fun loadSummary(id: Long): ChecklistSummary = loadChecklist(id).toSummary()

    private fun Checklist.toSummary() = ChecklistSummary(
        id = id,
        name = name,
        itemCount = items.count { !it.isFolder },
    )

    /**
     * Arms the AlarmManager alarm for a reminder the dispatcher just wrote.
     *
     * [ToolCallDispatcher] persists `reminderAt` on the fill row but owns no
     * [ChecklistReminderScheduler], so without this the reminder would only be armed by the next
     * reboot (BootCompletedReceiver) — set, shown in the app, and silently never fired. The row is
     * found by the exact time just written plus the matched item text.
     */
    private suspend fun scheduleItemAlarm(checklistId: Long, itemText: String, atMillis: Long) {
        val fill = checklistRepository.getDefaultFillOneShot(checklistId)
        val item = fill?.items?.firstOrNull { it.reminderAt == atMillis && it.text == itemText }
            ?: fill?.items?.firstOrNull { it.reminderAt == atMillis && it.text.contains(itemText, ignoreCase = true) }
        if (fill == null || item == null) {
            logger.error(
                TAG,
                "setItemReminder: reminder saved but its row was not found in checklist $checklistId — alarm not armed",
            )
            throw AppFunctionAppUnknownException(
                "The reminder was saved but could not be scheduled. The user can open the item in the Gisti app to re-set it.",
            )
        }
        reminderScheduler.scheduleItemReminder(checklistId, fill.id, item.id, atMillis)
    }

    private companion object {
        const val TAG = "AppFunctions"
    }
}
