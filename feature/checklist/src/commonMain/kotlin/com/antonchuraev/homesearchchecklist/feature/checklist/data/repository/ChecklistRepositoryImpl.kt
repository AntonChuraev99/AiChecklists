package com.antonchuraev.homesearchchecklist.feature.checklist.data.repository

import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistEntity
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillEntity
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ReminderConverters
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.toDomain
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.toEntity
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class ChecklistRepositoryImpl(
    private val checklistDao: ChecklistDao,
    private val fillDao: ChecklistFillDao
) : ChecklistRepository {

    // Checklists (templates)
    override val checklists: Flow<List<Checklist>> = checklistDao.observeChecklists().map { list ->
        list.map { it.toDomain() }
    }

    override suspend fun addChecklist(checklist: Checklist): Long {
        // Shift all existing checklists down so the new one appears at the top
        checklistDao.incrementAllPositions()
        val checklistId = checklistDao.insert(checklist.toEntity().copy(position = 0))

        // Create default fill automatically
        val defaultFill = ChecklistFill(
            checklistId = checklistId,
            name = "",
            items = checklist.items.map { item ->
                ChecklistFillItem(
                    text = item.text,
                    checked = false,
                    note = null
                )
            },
            createdAt = currentTimeMillis(),
            isDefault = true
        )
        fillDao.insert(defaultFill.toEntity())

        return checklistId
    }

    override suspend fun updateChecklist(checklist: Checklist) {
        checklistDao.update(checklist.toEntity())

        // Sync default fill items with updated checklist items
        val defaultFillEntity = fillDao.getDefaultFillByChecklistId(checklist.id)
        if (defaultFillEntity != null) {
            val existingItemsMap = defaultFillEntity.items.associateBy { it.text }
            val updatedItems = checklist.items.map { checklistItem ->
                val existingItem = existingItemsMap[checklistItem.text]
                ChecklistFillItem(
                    text = checklistItem.text,
                    checked = existingItem?.checked ?: false,
                    note = existingItem?.note
                )
            }
            fillDao.insert(defaultFillEntity.copy(items = updatedItems))
        }
    }

    override suspend fun updateChecklistTemplate(checklist: Checklist) {
        checklistDao.update(checklist.toEntity())
    }

    override suspend fun deleteChecklist(checklist: Checklist) {
        checklistDao.deleteById(checklist.id)
        reindexPositions()
    }

    override suspend fun getChecklistById(id: Long): Checklist? {
        return checklistDao.getById(id)?.toDomain()
    }

    // Display preferences
    override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {
        checklistDao.setSeparateCompleted(checklistId, value)
    }

    override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) {
        checklistDao.setAutoDeleteCompleted(checklistId, value)
    }

    // One-shot reminders
    override suspend fun setReminder(checklistId: Long, reminderAt: Long?) {
        checklistDao.updateReminder(checklistId, reminderAt)
    }

    override suspend fun countActiveReminders(): Int {
        val now = currentTimeMillis()
        // Checklist-level one-shot reminders still in the future (original behavior preserved)
        val checklistOneShot = checklistDao.countActiveReminders(now)
        // Per-item reminders: scan all default fills in-memory.
        // Items are serialized JSON — not SQL-queryable columns.
        // Counts each item that has any active reminder (one-shot OR recurring).
        // Note: checklist-level repeat schedules are NOT included here — they have a
        // separate gate via countActiveRepeatSchedules() in ChecklistDetailViewModel.
        val itemReminderCount = fillDao.getAllDefaultFills()
            .sumOf { entity ->
                entity.items.count { item -> item.hasActiveReminder }
            }
        return checklistOneShot + itemReminderCount
    }

    override suspend fun getActiveReminders(): List<ChecklistReminderInfo> {
        return checklistDao.getActiveReminders(currentTimeMillis())
    }

    override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? {
        return fillDao.getDefaultFillByChecklistId(checklistId)?.toDomain()
    }

    // Independent repeat schedule

    private val reminderConverters = ReminderConverters()

    override suspend fun setRepeatSchedule(
        checklistId: Long,
        rule: ReminderRepeatRule,
        timeOfDayMinutes: Int,
        firstTriggerAt: Long
    ) {
        val ruleJson = reminderConverters.repeatRuleToString(rule)
        checklistDao.setRepeatSchedule(checklistId, ruleJson, timeOfDayMinutes, firstTriggerAt)
    }

    override suspend fun advanceRepeatSchedule(
        checklistId: Long,
        nextAt: Long?,
        newCount: Int
    ) {
        checklistDao.advanceRepeatSchedule(checklistId, nextAt, newCount)
    }

    override suspend fun clearRepeatSchedule(checklistId: Long) {
        checklistDao.clearRepeatSchedule(checklistId)
    }

    override suspend fun resetDefaultFillChecks(checklistId: Long) {
        val defaultFillEntity = fillDao.getDefaultFillByChecklistId(checklistId)
        if (defaultFillEntity != null) {
            val resetItems = defaultFillEntity.items.map {
                ChecklistFillItem(text = it.text, checked = false, note = it.note)
            }
            fillDao.insert(defaultFillEntity.copy(items = resetItems))
        }
    }

    override suspend fun countActiveRepeatSchedules(): Int {
        return checklistDao.countActiveRepeatSchedules()
    }

    override suspend fun getActiveRepeatSchedules(): List<ChecklistRepeatInfo> {
        return checklistDao.getActiveRepeatSchedules(currentTimeMillis())
    }

    override suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> {
        return checklistDao.getPastDueRepeatSchedules(nowMillis)
    }

    // Analytics
    override suspend fun getTotalAdditionalFillCount(): Int {
        return fillDao.getTotalAdditionalFillCount()
    }

    override suspend fun getAllItemRemindersForRescheduling(): List<ItemReminderInfo> {
        return fillDao.getAllDefaultFills().flatMap { entity ->
            entity.items
                .filter { item -> item.hasActiveReminder }
                .map { item ->
                    ItemReminderInfo(
                        checklistId = entity.checklistId,
                        fillId = entity.id,
                        itemId = item.id,
                        reminderAt = item.reminderAt,
                        repeatRule = item.repeatRule,
                        repeatNextAt = item.repeatNextAt,
                        repeatOccurrenceCount = item.repeatOccurrenceCount
                    )
                }
        }
    }

    // Priority

    override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> {
        // 1. Load fill
        val fillEntity = fillDao.getById(fillId)
            ?: return Result.failure(IllegalStateException("Fill $fillId not found"))

        // 2. Find and toggle the item in the fill
        val itemIndex = fillEntity.items.indexOfFirst { it.id == itemId }
        if (itemIndex == -1) {
            return Result.failure(IllegalStateException("Item $itemId not found in fill $fillId"))
        }

        return runCatching {
            val item = fillEntity.items[itemIndex]
            val newPriority = if (item.priority == 0) 1 else 0
            val updatedFillItems = fillEntity.items.toMutableList().also {
                it[itemIndex] = item.withPriority(newPriority)
            }

            // 3. Persist updated fill
            fillDao.insert(fillEntity.copy(items = updatedFillItems))

            // 4. Dual update: sync priority to template items (matched by text)
            // Template items use text as the stable key (same pattern as updateChecklist sync)
            val checklistEntity = checklistDao.getById(fillEntity.checklistId)
            if (checklistEntity != null) {
                val updatedTemplateItems = checklistEntity.items.map { templateItem ->
                    if (templateItem.text == item.text) {
                        templateItem.withPriority(newPriority)
                    } else {
                        templateItem
                    }
                }
                checklistDao.update(checklistEntity.copy(items = updatedTemplateItems))
            }
        }
    }

    // Weekly mode
    override suspend fun getWeeklyChecklistCount(): Int {
        return checklistDao.getWeeklyChecklistCount()
    }

    override val weeklyChecklistCount: Flow<Int> = checklistDao.observeWeeklyChecklistCount()

    // Fills (instances)
    override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> {
        return fillDao.observeFillsByChecklistId(checklistId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> {
        return fillDao.observeDefaultFillByChecklistId(checklistId).map { it?.toDomain() }
    }

    override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> {
        return fillDao.observeAdditionalFillsByChecklistId(checklistId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getFillById(id: Long): ChecklistFill? {
        return fillDao.getById(id)?.toDomain()
    }

    override suspend fun getFillCountByChecklistId(checklistId: Long): Int {
        return fillDao.getCountByChecklistId(checklistId)
    }

    override suspend fun addFill(fill: ChecklistFill): Long {
        return fillDao.insert(fill.toEntity())
    }

    override suspend fun updateFill(fill: ChecklistFill) {
        fillDao.insert(fill.toEntity())
    }

    override suspend fun deleteFill(fill: ChecklistFill) {
        fillDao.deleteById(fill.id)
    }

    override suspend fun reorderChecklists(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            checklistDao.updatePosition(id, index)
        }
    }

    private suspend fun reindexPositions() {
        val all = checklistDao.getAllOrderedByPosition()
        all.forEachIndexed { index, entity ->
            if (entity.position != index) {
                checklistDao.updatePosition(entity.id, index)
            }
        }
    }

    // ─── Today reminders ───

    override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<TodayReminderInfo>> {
        // Combine the checklists flow with all default fills flow to react to any change.
        // We cannot do a simple SQL query for per-item reminders because items are stored
        // as JSON (not individual columns), so we scan in-memory.
        return combine(
            checklistDao.observeChecklists(),
            flow { emit(fillDao.getAllDefaultFills()) }
        ) { checklistEntities, fillEntities ->
            buildRemindersInRange(checklistEntities, fillEntities, fromMs, toMs)
        }
    }

    override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<TodayReminderInfo> {
        val checklistEntities = checklistDao.observeChecklists().first()
        val fillEntities = fillDao.getAllDefaultFills()
        return buildRemindersInRange(checklistEntities, fillEntities, fromMs, toMs)
    }

    /**
     * Scans checklists and default fills to collect all reminders in [fromMs]..[toMs].
     *
     * Checklist-level: [ChecklistEntity.reminderAt] (one-shot) or [ChecklistEntity.repeatNextAt] (recurring).
     * Item-level: [ChecklistFillItem.reminderAt] (one-shot) or [ChecklistFillItem.repeatNextAt] (recurring).
     *
     * A fill entity is keyed by checklistId — we resolve the checklist name via the entities list
     * rather than a separate DB query.
     */
    private fun buildRemindersInRange(
        checklistEntities: List<ChecklistEntity>,
        fillEntities: List<ChecklistFillEntity>,
        fromMs: Long,
        toMs: Long,
    ): List<TodayReminderInfo> {
        val result = mutableListOf<TodayReminderInfo>()

        // Build name-lookup map once
        val nameById = checklistEntities.associate { it.id to it.name }

        // ── Checklist-level reminders ──
        for (entity in checklistEntities) {
            // One-shot reminder
            val reminderAt = entity.reminderAt
            if (reminderAt != null && reminderAt in fromMs..toMs) {
                result += TodayReminderInfo.ChecklistLevel(
                    checklistId = entity.id,
                    checklistName = entity.name,
                    reminderAt = reminderAt,
                    isRecurring = false,
                )
            }
            // Recurring next occurrence (independent of one-shot)
            val repeatNextAt = entity.repeatNextAt
            if (repeatNextAt != null && repeatNextAt in fromMs..toMs) {
                result += TodayReminderInfo.ChecklistLevel(
                    checklistId = entity.id,
                    checklistName = entity.name,
                    reminderAt = repeatNextAt,
                    isRecurring = true,
                )
            }
        }

        // ── Per-item reminders (from default fills) ──
        for (fillEntity in fillEntities) {
            val checklistName = nameById[fillEntity.checklistId] ?: continue
            for (item in fillEntity.items) {
                // One-shot
                val reminderAt = item.reminderAt
                if (reminderAt != null && reminderAt in fromMs..toMs) {
                    result += TodayReminderInfo.ItemLevel(
                        checklistId = fillEntity.checklistId,
                        checklistName = checklistName,
                        fillId = fillEntity.id,
                        itemId = item.id,
                        itemText = item.text,
                        reminderAt = reminderAt,
                        isRecurring = false,
                        priority = item.priority,
                    )
                }
                // Recurring
                val repeatNextAt = item.repeatNextAt
                if (repeatNextAt != null && repeatNextAt in fromMs..toMs) {
                    result += TodayReminderInfo.ItemLevel(
                        checklistId = fillEntity.checklistId,
                        checklistName = checklistName,
                        fillId = fillEntity.id,
                        itemId = item.id,
                        itemText = item.text,
                        reminderAt = repeatNextAt,
                        isRecurring = true,
                        priority = item.priority,
                    )
                }
            }
        }

        return result
    }
}
