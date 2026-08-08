package com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ChecklistRepository {
    // Checklists (templates)
    val checklists: Flow<List<Checklist>>

    /**
     * [checklists] minus the auto-created system Inbox — the user-visible "projects".
     *
     * This, not [checklists], is what every picker and every free-tier / analytics counter must read:
     * the Inbox is the v2 shell's quick-capture zone, and letting it surface there would both confuse
     * the user and consume one of the 5 free checklist slots — a monetisation difference between the
     * two shells rather than a cosmetic bug.
     *
     * The one deliberate exception is the v1 (Classic layout) home LIST, which reads [checklists]:
     * that shell has no Inbox surface of its own, so filtering there would strand every captured task
     * behind a settings toggle. See `MainScreenViewModel.checklistsWithProgress`.
     *
     * For a user who has never opened the v2 shell no row is flagged, so this is identical to
     * [checklists].
     *
     * Has a default body (like [deleteCompletedItems] / [setReminderFullScreen]) purely so the many
     * inline test fakes of this interface keep compiling; the real
     * [com.antonchuraev.homesearchchecklist.feature.checklist.data.repository.ChecklistRepositoryImpl]
     * overrides it with a dedicated SQL query instead of an in-memory filter.
     */
    val projects: Flow<List<Checklist>> get() = checklists.map { list -> list.filterNot { it.isInbox } }

    /**
     * Reactive system Inbox row, or null before [ensureInbox] has run (and always null in the
     * control arm). Default body for the same test-fake reason as [projects].
     */
    fun observeInbox(): Flow<Checklist?> = checklists.map { list -> list.firstOrNull { it.isInbox } }

    /**
     * Returns the id of the system Inbox, creating it with [name] if it does not exist yet.
     * Idempotent — safe to call on every Inbox screen entry.
     *
     * [name] is resolved by the **presentation** layer via Compose Resources
     * (`getString(Res.string.inbox_checklist_name)`) and passed in: the domain layer must never
     * touch Compose Resources, and a hardcoded literal here would ship one language to every user.
     *
     * Default body returns 0L so inline test fakes keep compiling; the real implementation creates
     * the row plus its empty default fill.
     */
    suspend fun ensureInbox(name: String): Long = 0L

    /**
     * The inverse of [ensureInbox]: clears the `isInbox` flag on the system Inbox row, if one
     * exists, so it becomes an ordinary project again. Returns true when a row was actually
     * de-flagged, false when there was nothing to do.
     *
     * ## ⚠️ Currently UNCALLED — kept as a repair tool, not as a code path
     * A flagged row is invisible to [projects], and through it to every picker, to the widget's DAO
     * query, to the free-tier count and to MCP. Two screens can still read it: the v2 Inbox tab, and
     * the v1 home list — which reads the unfiltered [checklists] flow precisely so that a flagged row
     * carrying tasks is never stranded when the user turns "Classic layout" on
     * (`MainScreenViewModel.checklistsWithProgress`).
     *
     * This used to run automatically on every launch that resolved to v1
     * (`ReconcileInboxForControlArmUseCase`, deleted 2026-08-03 together with its call site in
     * `SplashViewModel`). That was right while the shell was a permanent Remote Config assignment.
     * It is WRONG now that v1/v2 is a user setting the user can flip back: clearing the flag on
     * every visit to v1 orphans the Inbox, and the next switch to v2 auto-creates a second one, so
     * the captured tasks end up in an ordinary checklist nobody opens. Keeping the flag and listing
     * the row instead is what makes the switch reversible.
     *
     * Preserves the row's name, items, fills and cloudId — nothing is deleted, the row is only
     * demoted — and marks it dirty so the cleared flag propagates to the user's other devices.
     *
     * Never call it from a screen: for a user on v2 this dissolves their Inbox into the Projects
     * list. If a repair path is ever needed again, it must be one-shot and explicitly triggered,
     * not launch-scoped.
     *
     * Default body returns false so inline test fakes keep compiling.
     */
    suspend fun clearInboxFlag(): Boolean = false

    suspend fun addChecklist(checklist: Checklist): Long
    suspend fun updateChecklist(checklist: Checklist)
    suspend fun updateChecklistTemplate(checklist: Checklist)
    suspend fun deleteChecklist(checklist: Checklist)
    suspend fun getChecklistById(id: Long): Checklist?

    /**
     * Reactive view of a single checklist. Emits a fresh value every time the row changes
     * (e.g. name edited from the Edit screen, reminder set, repeat schedule updated).
     *
     * Use this in screens that must reflect template changes live; for one-shot snapshots
     * call [getChecklistById] instead.
     */
    fun observeChecklistById(id: Long): Flow<Checklist?>
    suspend fun reorderChecklists(orderedIds: List<Long>)

    /**
     * Removes every checked item from the checklist's default fill AND the mirrored template rows,
     * returning the number of items removed (0 when nothing was checked).
     *
     * Dual-write (fill + template) so the detail screen and the edit screen stay in sync — the
     * same pattern [ChecklistRepositoryImpl.togglePriority] and the AI-chat dispatcher use. Template
     * rows are matched by the stable `templateItemId` link, falling back to text only for legacy
     * fill rows without a link (so a same-text sibling whose fill row was NOT checked is preserved).
     *
     * Backs both the detail-screen "delete completed items" overflow action and the chat
     * `clear_completed_items` tool. Default no-op returning 0 so the many inline test fakes need not
     * override it; the real [com.antonchuraev.homesearchchecklist.feature.checklist.data.repository.ChecklistRepositoryImpl]
     * overrides it.
     */
    suspend fun deleteCompletedItems(checklistId: Long): Int = 0

    // Display preferences
    suspend fun setSeparateCompleted(checklistId: Long, value: Boolean)
    suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean)

    /**
     * Enables or disables the per-checklist folders feature.
     *
     * Unlike [setSeparateCompleted]/[setAutoDeleteCompleted] (which only flip a column and
     * rely on a later unrelated edit to push), this also marks the checklist dirty via
     * [ChecklistDao.touchForSync] so the setting propagates to other devices promptly.
     */
    suspend fun setFoldersEnabled(checklistId: Long, value: Boolean)

    // One-shot reminders (independent of repeat)
    suspend fun setReminder(checklistId: Long, reminderAt: Long?)

    /**
     * Persist the checklist-level "full-screen (alarm-style) reminder" opt-in flag.
     * Default no-op so the many inline test fakes need not override it; the real
     * [com.antonchuraev.homesearchchecklist.feature.checklist.data.repository.ChecklistRepositoryImpl] overrides it.
     */
    suspend fun setReminderFullScreen(checklistId: Long, fullScreen: Boolean) {}
    /**
     * Counts active one-shot reminders across both checklist-level and per-item scopes.
     *
     * Used by the free-tier gate for one-shot reminders. Per-item recurring schedules
     * are also included (each item with any active reminder counts as 1).
     * Checklist-level repeat schedules are counted separately via [countActiveRepeatSchedules]
     * (they have their own gate in ChecklistDetailViewModel).
     *
     * Active = checklist [reminderAt] in the future (one-shot)
     *        + items where [reminderAt] != null OR [repeatRule] != null.
     *
     * Note: item-level counting is done in-memory by scanning default fills
     * (items are serialized JSON, not queryable SQL columns).
     */
    suspend fun countActiveReminders(): Int
    suspend fun getActiveReminders(): List<ChecklistReminderInfo>
    suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill?

    // Per-item reminder persistence
    // Items are stored as JSON inside the fill row — no dedicated column.
    // All mutations go through [updateFill]; these helpers are convenience wrappers.

    /**
     * Returns all default fills that contain at least one item with an active reminder.
     * Used by [getAllItemRemindersForRescheduling] and the BootCompletedReceiver (Phase 2).
     */
    suspend fun getAllItemRemindersForRescheduling(): List<ItemReminderInfo>

    // Independent repeat schedule
    suspend fun setRepeatSchedule(checklistId: Long, rule: ReminderRepeatRule, timeOfDayMinutes: Int, firstTriggerAt: Long)
    suspend fun advanceRepeatSchedule(checklistId: Long, nextAt: Long?, newCount: Int)
    suspend fun clearRepeatSchedule(checklistId: Long)
    suspend fun resetDefaultFillChecks(checklistId: Long)
    suspend fun countActiveRepeatSchedules(): Int
    suspend fun getActiveRepeatSchedules(): List<ChecklistRepeatInfo>
    suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo>

    // Analytics
    suspend fun getTotalAdditionalFillCount(): Int

    // Weekly mode
    suspend fun getWeeklyChecklistCount(): Int
    val weeklyChecklistCount: Flow<Int>

    /**
     * Observes all reminders (checklist-level + per-item) that fall within [fromMs]..[toMs].
     *
     * Checklist-level: [Checklist.reminderAt] or [Checklist.repeatNextAt] in range.
     * Per-item: [ChecklistFillItem.reminderAt] or [ChecklistFillItem.repeatNextAt] in range.
     *
     * Emits a new list whenever the underlying checklists **or fills** change — including while a
     * screen stays subscribed. The Today and Calendar tabs capture tasks into a list they are already
     * showing, so an implementation that reads either table once per subscription silently freezes
     * both (regression test: `observeRemindersInRange_itemReminderCapturedWhileSubscribed…`).
     *
     * Scans ALL checklists, [projects] and the system Inbox alike: a reminder must surface wherever
     * the task lives, and the detail deep-link resolves for the Inbox too. See the block comment
     * above the implementation in `ChecklistRepositoryImpl` for the full rationale — this is a
     * deliberate choice, not a missed filter.
     */
    fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<TodayReminderInfo>>

    /**
     * One-shot version of [observeRemindersInRange] for use in suspend contexts.
     */
    suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<TodayReminderInfo>

    // Priority
    /**
     * Toggles the priority of a single fill item between 0 (normal) and 1 (starred).
     *
     * Performs a dual update: both the fill (for the detail screen) and the checklist template
     * (for the edit screen) are updated atomically to keep them in sync.
     *
     * Returns [Result.failure] if the fill or item is not found.
     */
    suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit>

    // Attachments
    // Items are stored as JSON inside the fill row — no dedicated SQL column.
    // Both helpers follow the dual-update pattern: fill updated via updateFill();
    // attachments do NOT propagate to the checklist template (per-fill data only).

    /**
     * Appends [attachment] to [itemId] inside fill [fillId].
     * Internally loads the fill, calls [ChecklistFillItem.withAttachmentAdded], then [updateFill].
     */
    suspend fun addAttachment(fillId: Long, itemId: String, attachment: Attachment)

    /**
     * Removes the attachment identified by [attachmentId] from [itemId] inside fill [fillId].
     * Internally loads the fill, calls [ChecklistFillItem.withAttachmentRemoved], then [updateFill].
     * No-op if [attachmentId] is not found.
     */
    suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String)

    // Fills (instances)
    fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>>
    fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?>
    fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>>
    suspend fun getFillById(id: Long): ChecklistFill?
    suspend fun getFillCountByChecklistId(checklistId: Long): Int
    suspend fun addFill(fill: ChecklistFill): Long
    suspend fun updateFill(fill: ChecklistFill)
    suspend fun deleteFill(fill: ChecklistFill)

    /**
     * Persists a drag-to-reorder atomically: the reordered [fill] and its [checklist] template
     * are written in ONE database transaction with a single shared `updatedAt`.
     *
     * This is the dedicated path for reorder (instead of [updateFill] + [updateChecklistTemplate])
     * to close a sync race: those two calls do separate writes — and [updateFill] additionally
     * dirties the parent checklist with the OLD items before the template write — so a sync push
     * triggered by the parent-table emission could read the stale half, upload it, and stamp it
     * with a fresh `updatedAt`; the real-time listener echo then merged that stale snapshot back
     * over the just-made local order (old order resurrected after leaving the screen).
     *
     * Contract:
     * - Both rows are marked PENDING_UPLOAD with the SAME, monotonic `updatedAt` (>= any value a
     *   concurrent push could have stamped), so the parent's timestamp never goes backwards.
     * - The transaction makes the parent (`checklists`) table emit exactly once, AFTER commit,
     *   already carrying the new order — a push can never observe an intermediate stale state.
     *
     * No fill-id regeneration happens (unlike [updateChecklist]); the caller passes already
     * reconciled fill + template, matching the [updateChecklistTemplate] contract.
     */
    suspend fun reorderItems(fill: ChecklistFill, checklist: Checklist)
}
