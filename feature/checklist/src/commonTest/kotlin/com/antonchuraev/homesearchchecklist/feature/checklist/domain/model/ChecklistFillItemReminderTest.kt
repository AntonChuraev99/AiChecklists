package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the per-item reminder helpers added to [ChecklistFillItem].
 *
 * Verified properties per test:
 * - Returns a NEW instance (immutability / @ConsistentCopyVisibility contract)
 * - The targeted field has the expected new value
 * - All unrelated fields are unchanged (id, text, checked, note, weekday, other reminder fields)
 */
class ChecklistFillItemReminderTest {

    // ── Default reminder state ──

    @Test
    fun fillItem_reminderFields_defaultNull() {
        val item = ChecklistFillItem(text = "Buy milk", checked = false)

        assertNull(item.reminderAt)
        assertNull(item.repeatRule)
        assertNull(item.repeatTimeOfDayMinutes)
        assertNull(item.repeatNextAt)
        assertEquals(0, item.repeatOccurrenceCount)
    }

    @Test
    fun fillItem_hasActiveReminder_falseByDefault() {
        val item = ChecklistFillItem(text = "Task", checked = false)
        assertFalse(item.hasActiveReminder)
    }

    // ── withReminderAt ──

    @Test
    fun withReminderAt_setsReminderAt() {
        val item = ChecklistFillItem(text = "Call dentist", checked = false)
        val updated = item.withReminderAt(1_700_000_000_000L)

        assertEquals(1_700_000_000_000L, updated.reminderAt)
    }

    @Test
    fun withReminderAt_returnsNewInstance() {
        val item = ChecklistFillItem(text = "Task", checked = false)
        val updated = item.withReminderAt(12345L)

        assertNotSame(item, updated)
    }

    @Test
    fun withReminderAt_preservesId() {
        val item = ChecklistFillItem(text = "Task", checked = false)
        val updated = item.withReminderAt(12345L)

        assertEquals(item.id, updated.id)
    }

    @Test
    fun withReminderAt_preservesUnrelatedFields() {
        val item = ChecklistFillItem(text = "Run 5km", checked = true, note = "morning", weekday = 2)
        val updated = item.withReminderAt(99999L)

        assertEquals("Run 5km", updated.text)
        assertTrue(updated.checked)
        assertEquals("morning", updated.note)
        assertEquals(2, updated.weekday)
        // repeat fields untouched
        assertNull(updated.repeatRule)
        assertNull(updated.repeatNextAt)
        assertEquals(0, updated.repeatOccurrenceCount)
    }

    @Test
    fun withReminderAt_canClearWithNull() {
        val item = ChecklistFillItem(text = "Task", checked = false).withReminderAt(12345L)
        val cleared = item.withReminderAt(null)

        assertNull(cleared.reminderAt)
        assertEquals(item.id, cleared.id)
    }

    @Test
    fun withReminderAt_setsHasActiveReminder() {
        val item = ChecklistFillItem(text = "Task", checked = false).withReminderAt(100L)
        assertTrue(item.hasActiveReminder)
    }

    // ── withRepeatRule ──

    @Test
    fun withRepeatRule_setsAllRepeatFields() {
        val item = ChecklistFillItem(text = "Take pills", checked = false)
        val rule = ReminderRepeatRule(
            type = RepeatType.DAILY,
            endCondition = RepeatEndCondition.Never
        )
        val updated = item.withRepeatRule(rule = rule, timeOfDayMinutes = 480, nextAt = 1_700_000_000_000L)

        assertEquals(rule, updated.repeatRule)
        assertEquals(480, updated.repeatTimeOfDayMinutes)
        assertEquals(1_700_000_000_000L, updated.repeatNextAt)
    }

    @Test
    fun withRepeatRule_returnsNewInstance() {
        val item = ChecklistFillItem(text = "Task", checked = false)
        val rule = ReminderRepeatRule(type = RepeatType.WEEKLY, endCondition = RepeatEndCondition.Never)
        val updated = item.withRepeatRule(rule, 600, 999L)

        assertNotSame(item, updated)
    }

    @Test
    fun withRepeatRule_preservesId() {
        val item = ChecklistFillItem(text = "Task", checked = false)
        val rule = ReminderRepeatRule(type = RepeatType.DAILY, endCondition = RepeatEndCondition.Never)
        val updated = item.withRepeatRule(rule, 480, 100L)

        assertEquals(item.id, updated.id)
    }

    @Test
    fun withRepeatRule_preservesUnrelatedFields() {
        val item = ChecklistFillItem(text = "Meditate", checked = false, note = "10 min", weekday = 3)
        val rule = ReminderRepeatRule(type = RepeatType.DAILY, endCondition = RepeatEndCondition.Never)
        val updated = item.withRepeatRule(rule, 360, 555L)

        assertEquals("Meditate", updated.text)
        assertFalse(updated.checked)
        assertEquals("10 min", updated.note)
        assertEquals(3, updated.weekday)
        // one-shot field untouched
        assertNull(updated.reminderAt)
        // occurrence count not touched by withRepeatRule
        assertEquals(0, updated.repeatOccurrenceCount)
    }

    @Test
    fun withRepeatRule_setsHasActiveReminder() {
        val rule = ReminderRepeatRule(type = RepeatType.DAILY, endCondition = RepeatEndCondition.Never)
        val item = ChecklistFillItem(text = "Task", checked = false)
            .withRepeatRule(rule, 480, 100L)
        assertTrue(item.hasActiveReminder)
    }

    // ── withRepeatAdvanced ──

    @Test
    fun withRepeatAdvanced_updatesNextAtAndCount() {
        val rule = ReminderRepeatRule(type = RepeatType.DAILY, endCondition = RepeatEndCondition.Never)
        val item = ChecklistFillItem(text = "Task", checked = false)
            .withRepeatRule(rule, 480, 1_000L)
        val advanced = item.withRepeatAdvanced(nextAt = 2_000L, newCount = 1)

        assertEquals(2_000L, advanced.repeatNextAt)
        assertEquals(1, advanced.repeatOccurrenceCount)
        // rule and timeOfDay preserved
        assertEquals(rule, advanced.repeatRule)
        assertEquals(480, advanced.repeatTimeOfDayMinutes)
    }

    @Test
    fun withRepeatAdvanced_preservesId() {
        val rule = ReminderRepeatRule(type = RepeatType.DAILY, endCondition = RepeatEndCondition.Never)
        val item = ChecklistFillItem(text = "Task", checked = false)
            .withRepeatRule(rule, 480, 1_000L)
        val advanced = item.withRepeatAdvanced(2_000L, 1)

        assertEquals(item.id, advanced.id)
    }

    @Test
    fun withRepeatAdvanced_canNullifyNextAt() {
        val rule = ReminderRepeatRule(type = RepeatType.DAILY, endCondition = RepeatEndCondition.Never)
        val item = ChecklistFillItem(text = "Task", checked = false)
            .withRepeatRule(rule, 480, 1_000L)
            .withRepeatAdvanced(nextAt = null, newCount = 5)

        assertNull(item.repeatNextAt)
        assertEquals(5, item.repeatOccurrenceCount)
    }

    // ── withReminderCleared ──

    @Test
    fun withReminderCleared_clearsAllReminderFields() {
        val rule = ReminderRepeatRule(type = RepeatType.WEEKLY, endCondition = RepeatEndCondition.Never)
        val item = ChecklistFillItem(text = "Weekly review", checked = false)
            .withReminderAt(9999L)
            .withRepeatRule(rule, 540, 100_000L)
            .withRepeatAdvanced(200_000L, 3)

        val cleared = item.withReminderCleared()

        assertNull(cleared.reminderAt)
        assertNull(cleared.repeatRule)
        assertNull(cleared.repeatTimeOfDayMinutes)
        assertNull(cleared.repeatNextAt)
        assertEquals(0, cleared.repeatOccurrenceCount)
    }

    @Test
    fun withReminderCleared_returnsNewInstance() {
        val item = ChecklistFillItem(text = "Task", checked = false).withReminderAt(100L)
        val cleared = item.withReminderCleared()

        assertNotSame(item, cleared)
    }

    @Test
    fun withReminderCleared_preservesId() {
        val item = ChecklistFillItem(text = "Task", checked = false).withReminderAt(100L)
        val cleared = item.withReminderCleared()

        assertEquals(item.id, cleared.id)
    }

    @Test
    fun withReminderCleared_preservesUnrelatedFields() {
        val item = ChecklistFillItem(text = "Stretch", checked = true, note = "5 min", weekday = 1)
            .withReminderAt(100L)
        val cleared = item.withReminderCleared()

        assertEquals("Stretch", cleared.text)
        assertTrue(cleared.checked)
        assertEquals("5 min", cleared.note)
        assertEquals(1, cleared.weekday)
        assertEquals(item.id, cleared.id)
    }

    @Test
    fun withReminderCleared_hasActiveReminderIsFalse() {
        val rule = ReminderRepeatRule(type = RepeatType.DAILY, endCondition = RepeatEndCondition.Never)
        val item = ChecklistFillItem(text = "Task", checked = false)
            .withRepeatRule(rule, 480, 100L)
            .withReminderCleared()

        assertFalse(item.hasActiveReminder)
    }

    // ── hasActiveReminder corner cases ──

    @Test
    fun hasActiveReminder_trueWhenOnlyShotSet() {
        val item = ChecklistFillItem(text = "X", checked = false).withReminderAt(1L)
        assertTrue(item.hasActiveReminder)
    }

    @Test
    fun hasActiveReminder_trueWhenOnlyRepeatSet() {
        val rule = ReminderRepeatRule(type = RepeatType.DAILY, endCondition = RepeatEndCondition.Never)
        val item = ChecklistFillItem(text = "X", checked = false)
            .withRepeatRule(rule, 480, 100L)
        assertTrue(item.hasActiveReminder)
    }

    @Test
    fun hasActiveReminder_trueWhenBothSet() {
        val rule = ReminderRepeatRule(type = RepeatType.DAILY, endCondition = RepeatEndCondition.Never)
        val item = ChecklistFillItem(text = "X", checked = false)
            .withReminderAt(1L)
            .withRepeatRule(rule, 480, 100L)
        assertTrue(item.hasActiveReminder)
    }

    // ── Existing helpers still preserve reminder fields ──

    @Test
    fun withChecked_preservesReminderFields() {
        val rule = ReminderRepeatRule(type = RepeatType.DAILY, endCondition = RepeatEndCondition.Never)
        val item = ChecklistFillItem(text = "Task", checked = false)
            .withReminderAt(12345L)
            .withRepeatRule(rule, 480, 99L)
        val toggled = item.withChecked(true)

        assertEquals(12345L, toggled.reminderAt)
        assertEquals(rule, toggled.repeatRule)
        assertEquals(480, toggled.repeatTimeOfDayMinutes)
        assertEquals(99L, toggled.repeatNextAt)
        assertEquals(item.id, toggled.id)
    }

    @Test
    fun withNote_preservesReminderFields() {
        val item = ChecklistFillItem(text = "Task", checked = false).withReminderAt(77L)
        val noted = item.withNote("important")

        assertEquals(77L, noted.reminderAt)
        assertEquals(item.id, noted.id)
    }

    @Test
    fun withWeekday_preservesReminderFields() {
        val rule = ReminderRepeatRule(type = RepeatType.WEEKLY, endCondition = RepeatEndCondition.Never)
        val item = ChecklistFillItem(text = "Workout", checked = false)
            .withRepeatRule(rule, 420, 500L)
        val moved = item.withWeekday(5)

        assertEquals(rule, moved.repeatRule)
        assertEquals(420, moved.repeatTimeOfDayMinutes)
        assertEquals(500L, moved.repeatNextAt)
        assertEquals(item.id, moved.id)
        assertEquals(5, moved.weekday)
    }
}
