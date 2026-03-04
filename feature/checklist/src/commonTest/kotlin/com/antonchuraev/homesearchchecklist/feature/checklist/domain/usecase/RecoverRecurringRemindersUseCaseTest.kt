package com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase

import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistRecurringInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
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
import kotlin.test.assertTrue

class RecoverRecurringRemindersUseCaseTest {

    // ─── Fakes ────────────────────────────────────────────────────────

    private class FakeScheduler : ChecklistReminderScheduler {
        val scheduled = mutableListOf<Pair<Long, Long>>() // checklistId to triggerAt

        override fun schedule(checklistId: Long, triggerAtMillis: Long) {
            scheduled.add(checklistId to triggerAtMillis)
        }

        override fun cancel(checklistId: Long) {}
        override suspend fun rescheduleAllActive() {}
    }

    private class FakeRepository : ChecklistRepository {
        var pastDueReminders: List<ChecklistRecurringInfo> = emptyList()
        val advancedReminders = mutableListOf<Triple<Long, Long?, Int>>() // id, nextAt, newCount
        val clearedReminders = mutableListOf<Long>()

        override suspend fun getPastDueRecurringReminders(nowMillis: Long) = pastDueReminders
        override suspend fun advanceRecurringReminder(checklistId: Long, nextReminderAt: Long?, newCount: Int) {
            advancedReminders.add(Triple(checklistId, nextReminderAt, newCount))
        }
        override suspend fun clearRecurringReminder(checklistId: Long) {
            clearedReminders.add(checklistId)
        }

        // Unused stubs
        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())
        override suspend fun addChecklist(checklist: Checklist): Long = 0L
        override suspend fun updateChecklist(checklist: Checklist) {}
        override suspend fun updateChecklistTemplate(checklist: Checklist) {}
        override suspend fun deleteChecklist(checklist: Checklist) {}
        override suspend fun getChecklistById(id: Long): Checklist? = null
        override suspend fun reorderChecklists(orderedIds: List<Long>) {}
        override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setReminder(checklistId: Long, reminderAt: Long?) {}
        override suspend fun countActiveReminders(): Int = 0
        override suspend fun getActiveReminders(): List<ChecklistReminderInfo> = emptyList()
        override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? = null
        override suspend fun setReminderWithRule(checklistId: Long, reminderAt: Long?, repeatRule: ReminderRepeatRule?) {}
        override suspend fun setRepeatRule(checklistId: Long, rule: ReminderRepeatRule?) {}
        override suspend fun resetDefaultFillChecks(checklistId: Long) {}
        override suspend fun countRecurringReminders(): Int = 0
        override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = flowOf(null)
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override suspend fun getFillById(id: Long): ChecklistFill? = null
        override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun addFill(fill: ChecklistFill): Long = 0L
        override suspend fun updateFill(fill: ChecklistFill) {}
        override suspend fun deleteFill(fill: ChecklistFill) {}
    }

    // ─── Tests ────────────────────────────────────────────────────────

    @Test
    fun noPastDue_doesNothing() = runTest {
        val repo = FakeRepository()
        val scheduler = FakeScheduler()
        val useCase = RecoverRecurringRemindersUseCase(repo, scheduler)

        useCase(nowMillis = 1000L)

        assertTrue(repo.advancedReminders.isEmpty())
        assertTrue(repo.clearedReminders.isEmpty())
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

        repo.pastDueReminders = listOf(
            ChecklistRecurringInfo(
                id = 42L,
                name = "Test",
                reminderAt = pastDueTime,
                repeatRule = rule,
                repeatOccurrenceCount = 5
            )
        )

        useCase(nowMillis = now)

        // Should advance with next occurrence
        assertEquals(1, repo.advancedReminders.size)
        val (id, nextAt, newCount) = repo.advancedReminders.first()
        assertEquals(42L, id)
        assertEquals(6, newCount) // 5 + 1
        assertTrue(nextAt != null && nextAt > now, "Next occurrence should be in the future")

        // Should schedule alarm
        assertEquals(1, scheduler.scheduled.size)
        assertEquals(42L, scheduler.scheduled.first().first)
        assertEquals(nextAt, scheduler.scheduled.first().second)
    }

    @Test
    fun pastDueWithEndConditionReached_clearsReminder() = runTest {
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

        repo.pastDueReminders = listOf(
            ChecklistRecurringInfo(
                id = 99L,
                name = "Limited",
                reminderAt = pastDueTime,
                repeatRule = rule,
                repeatOccurrenceCount = 2 // already at 2 of 3, next would be 3 >= maxCount
            )
        )

        useCase(nowMillis = now)

        // Should clear, not advance
        assertTrue(repo.advancedReminders.isEmpty())
        assertEquals(listOf(99L), repo.clearedReminders)
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

        repo.pastDueReminders = listOf(
            ChecklistRecurringInfo(id = 1L, name = "A", reminderAt = now - 86_400_000, repeatRule = rule1, repeatOccurrenceCount = 0),
            ChecklistRecurringInfo(id = 2L, name = "B", reminderAt = now - 7 * 86_400_000, repeatRule = rule2, repeatOccurrenceCount = 3)
        )

        useCase(nowMillis = now)

        assertEquals(2, repo.advancedReminders.size)
        assertEquals(2, scheduler.scheduled.size)
        assertEquals(setOf(1L, 2L), repo.advancedReminders.map { it.first }.toSet())
    }

    @Test
    fun nullRepeatRule_skipped() = runTest {
        val repo = FakeRepository()
        val scheduler = FakeScheduler()
        val useCase = RecoverRecurringRemindersUseCase(repo, scheduler)

        repo.pastDueReminders = listOf(
            ChecklistRecurringInfo(id = 7L, name = "Broken", reminderAt = 500L, repeatRule = null, repeatOccurrenceCount = 0)
        )

        useCase(nowMillis = 1000L)

        assertTrue(repo.advancedReminders.isEmpty())
        assertTrue(repo.clearedReminders.isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
    }
}
