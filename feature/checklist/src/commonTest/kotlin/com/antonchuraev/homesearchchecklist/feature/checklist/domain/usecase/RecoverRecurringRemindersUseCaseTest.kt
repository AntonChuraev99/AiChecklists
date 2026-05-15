package com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecoverRecurringRemindersUseCaseTest {

    // ─── Fakes ────────────────────────────────────────────────────────

    private class FakeScheduler : ChecklistReminderScheduler {
        val scheduled = mutableListOf<Pair<Long, Long>>() // checklistId to triggerAt

        override fun scheduleReminder(checklistId: Long, triggerAtMillis: Long) {}
        override fun cancelReminder(checklistId: Long) {}
        override suspend fun rescheduleAllActiveReminders() {}
        override fun scheduleRepeat(checklistId: Long, triggerAtMillis: Long) {
            scheduled.add(checklistId to triggerAtMillis)
        }
        override fun cancelRepeat(checklistId: Long) {}
        override suspend fun rescheduleAllActiveRepeats() {}
        override fun scheduleItemReminder(checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long) {}
        override fun cancelItemReminder(checklistId: Long, fillId: Long, itemId: String) {}
        override fun scheduleItemRepeat(checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long) {}
        override fun cancelItemRepeat(checklistId: Long, fillId: Long, itemId: String) {}
    }

    private class FakeRepository : ChecklistRepository {
        var pastDueSchedules: List<ChecklistRepeatInfo> = emptyList()
        val advancedSchedules = mutableListOf<Triple<Long, Long?, Int>>() // id, nextAt, newCount
        val clearedSchedules = mutableListOf<Long>()

        override suspend fun getPastDueRepeatSchedules(nowMillis: Long) = pastDueSchedules
        override suspend fun advanceRepeatSchedule(checklistId: Long, nextAt: Long?, newCount: Int) {
            advancedSchedules.add(Triple(checklistId, nextAt, newCount))
        }
        override suspend fun clearRepeatSchedule(checklistId: Long) {
            clearedSchedules.add(checklistId)
        }

        // Unused stubs
        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())
        override suspend fun addChecklist(checklist: Checklist): Long = 0L
        override suspend fun updateChecklist(checklist: Checklist) {}
        override suspend fun updateChecklistTemplate(checklist: Checklist) {}
        override suspend fun deleteChecklist(checklist: Checklist) {}
        override suspend fun getChecklistById(id: Long): Checklist? = null
        override fun observeChecklistById(id: Long): Flow<Checklist?> = flowOf(null)
        override suspend fun reorderChecklists(orderedIds: List<Long>) {}
        override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setReminder(checklistId: Long, reminderAt: Long?) {}
        override suspend fun countActiveReminders(): Int = 0
        override suspend fun getActiveReminders(): List<ChecklistReminderInfo> = emptyList()
        override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? = null
        override suspend fun setRepeatSchedule(checklistId: Long, rule: ReminderRepeatRule, timeOfDayMinutes: Int, firstTriggerAt: Long) {}
        override suspend fun resetDefaultFillChecks(checklistId: Long) {}
        override suspend fun countActiveRepeatSchedules(): Int = 0
        override suspend fun getActiveRepeatSchedules(): List<ChecklistRepeatInfo> = emptyList()
        override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = flowOf(null)
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override suspend fun getFillById(id: Long): ChecklistFill? = null
        override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun addFill(fill: ChecklistFill): Long = 0L
        override suspend fun updateFill(fill: ChecklistFill) {}
        override suspend fun deleteFill(fill: ChecklistFill) {}
        override suspend fun getTotalAdditionalFillCount(): Int = 0
        override suspend fun getWeeklyChecklistCount(): Int = 0
        override val weeklyChecklistCount: Flow<Int> = flowOf(0)
        override suspend fun getAllItemRemindersForRescheduling(): List<ItemReminderInfo> = emptyList()
        override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo>> = flowOf(emptyList())
        override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo> = emptyList()
        override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addAttachment(fillId: Long, itemId: String, attachment: com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment) = Unit
        override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) = Unit
    }

    // ─── Tests ────────────────────────────────────────────────────────

    @Test
    fun noPastDue_doesNothing() = runTest {
        val repo = FakeRepository()
        val scheduler = FakeScheduler()
        val useCase = RecoverRecurringRemindersUseCase(repo, scheduler)

        useCase(nowMillis = 1000L)

        assertTrue(repo.advancedSchedules.isEmpty())
        assertTrue(repo.clearedSchedules.isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun pastDueRecurring_advancesAndReschedules() = runTest {
        val repo = FakeRepository()
        val scheduler = FakeScheduler()
        val useCase = RecoverRecurringRemindersUseCase(repo, scheduler)

        val rule = ReminderRepeatRule(type = RepeatType.DAILY, interval = 1)
        val pastDueTime = 1_000_000_000L // some time in the past
        val now = pastDueTime + 2 * 86_400_000L // 2 days later

        repo.pastDueSchedules = listOf(
            ChecklistRepeatInfo(
                id = 42L,
                name = "Test",
                repeatNextAt = pastDueTime,
                repeatRule = rule,
                repeatOccurrenceCount = 5
            )
        )

        useCase(nowMillis = now)

        // Should advance with next occurrence
        assertEquals(1, repo.advancedSchedules.size)
        val (id, nextAt, newCount) = repo.advancedSchedules.first()
        assertEquals(42L, id)
        assertEquals(6, newCount) // 5 + 1
        assertTrue(nextAt != null && nextAt > now, "Next occurrence should be in the future")

        // Should schedule alarm via scheduleRepeat
        assertEquals(1, scheduler.scheduled.size)
        assertEquals(42L, scheduler.scheduled.first().first)
        assertEquals(nextAt, scheduler.scheduled.first().second)
    }

    @Test
    fun pastDueWithEndConditionReached_clearsRepeatSchedule() = runTest {
        val repo = FakeRepository()
        val scheduler = FakeScheduler()
        val useCase = RecoverRecurringRemindersUseCase(repo, scheduler)

        val rule = ReminderRepeatRule(
            type = RepeatType.DAILY,
            interval = 1,
            endCondition = RepeatEndCondition.AfterCount(maxCount = 3)
        )
        val pastDueTime = 1_000_000_000L
        val now = pastDueTime + 86_400_000L

        repo.pastDueSchedules = listOf(
            ChecklistRepeatInfo(
                id = 99L,
                name = "Limited",
                repeatNextAt = pastDueTime,
                repeatRule = rule,
                repeatOccurrenceCount = 2 // already at 2 of 3, next would be 3 >= maxCount
            )
        )

        useCase(nowMillis = now)

        // Should clear, not advance
        assertTrue(repo.advancedSchedules.isEmpty())
        assertEquals(listOf(99L), repo.clearedSchedules)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun multiplePastDue_processesAll() = runTest {
        val repo = FakeRepository()
        val scheduler = FakeScheduler()
        val useCase = RecoverRecurringRemindersUseCase(repo, scheduler)

        val rule1 = ReminderRepeatRule(type = RepeatType.DAILY, interval = 1)
        val rule2 = ReminderRepeatRule(type = RepeatType.WEEKLY, interval = 1)
        val now = 2_000_000_000L

        repo.pastDueSchedules = listOf(
            ChecklistRepeatInfo(id = 1L, name = "A", repeatNextAt = now - 86_400_000, repeatRule = rule1, repeatOccurrenceCount = 0),
            ChecklistRepeatInfo(id = 2L, name = "B", repeatNextAt = now - 7 * 86_400_000, repeatRule = rule2, repeatOccurrenceCount = 3)
        )

        useCase(nowMillis = now)

        assertEquals(2, repo.advancedSchedules.size)
        assertEquals(2, scheduler.scheduled.size)
        assertEquals(setOf(1L, 2L), repo.advancedSchedules.map { it.first }.toSet())
    }

    // ─── Analytics tests ─────────────────────────────────────────────

    @Test
    fun pastDueRecovery_tracksRecoveredEvent() = runTest {
        val repo = FakeRepository()
        val scheduler = FakeScheduler()
        val tracker = FakeAnalyticsTracker()
        val useCase = RecoverRecurringRemindersUseCase(repo, scheduler, tracker)

        val rule = ReminderRepeatRule(type = RepeatType.DAILY, interval = 1)
        val pastDueTime = 1_000_000_000L
        val now = pastDueTime + 2 * 86_400_000L

        repo.pastDueSchedules = listOf(
            ChecklistRepeatInfo(
                id = 42L, name = "Test",
                repeatNextAt = pastDueTime, repeatRule = rule,
                repeatOccurrenceCount = 5
            )
        )

        useCase(nowMillis = now)

        val event = tracker.events.find { it.first == "recurring_reminder_recovered" }
        assertNotNull(event)
        assertEquals("42", event.second["checklist_id"])
        assertEquals("1", event.second["skipped_occurrences"])
    }

    @Test
    fun endConditionReachedDuringRecovery_tracksEndedEvent() = runTest {
        val repo = FakeRepository()
        val scheduler = FakeScheduler()
        val tracker = FakeAnalyticsTracker()
        val useCase = RecoverRecurringRemindersUseCase(repo, scheduler, tracker)

        val rule = ReminderRepeatRule(
            type = RepeatType.DAILY, interval = 1,
            endCondition = RepeatEndCondition.AfterCount(maxCount = 3)
        )

        repo.pastDueSchedules = listOf(
            ChecklistRepeatInfo(
                id = 99L, name = "Limited",
                repeatNextAt = 1_000_000_000L, repeatRule = rule,
                repeatOccurrenceCount = 2
            )
        )

        useCase(nowMillis = 1_000_000_000L + 86_400_000L)

        val event = tracker.events.find { it.first == "recurring_reminder_ended" }
        assertNotNull(event)
        assertEquals("99", event.second["checklist_id"])
        assertEquals("AfterCount", event.second["end_reason"])
        assertEquals("2", event.second["total_occurrences"])
    }

    @Test
    fun nullAnalyticsTracker_doesNotCrash() = runTest {
        val repo = FakeRepository()
        val scheduler = FakeScheduler()
        val useCase = RecoverRecurringRemindersUseCase(repo, scheduler, null)

        val rule = ReminderRepeatRule(type = RepeatType.DAILY, interval = 1)
        repo.pastDueSchedules = listOf(
            ChecklistRepeatInfo(
                id = 1L, name = "Test",
                repeatNextAt = 500L, repeatRule = rule,
                repeatOccurrenceCount = 0
            )
        )

        useCase(nowMillis = 1000L)

        assertEquals(1, repo.advancedSchedules.size)
    }

    // ─── Original tests (continued) ─────────────────────────────────

    @Test
    fun nullRepeatRule_skipped() = runTest {
        val repo = FakeRepository()
        val scheduler = FakeScheduler()
        val useCase = RecoverRecurringRemindersUseCase(repo, scheduler)

        repo.pastDueSchedules = listOf(
            ChecklistRepeatInfo(id = 7L, name = "Broken", repeatNextAt = 500L, repeatRule = null, repeatOccurrenceCount = 0)
        )

        useCase(nowMillis = 1000L)

        assertTrue(repo.advancedSchedules.isEmpty())
        assertTrue(repo.clearedSchedules.isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
    }

    // ─── Fakes (analytics) ─────────────────────────────────────────

    private class FakeAnalyticsTracker : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {
            events.add(name to params)
        }
    }
}
