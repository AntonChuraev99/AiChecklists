package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.weekly

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeeklyHelpersTest {

    // ── ISO_WEEK_DAYS — fixed Mon→Sun calendar order, regardless of "today" ──

    @Test
    fun isoWeekDays_isFixedMondayToSunday() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), ISO_WEEK_DAYS)
    }

    // ── getOverdueItems — filter unchecked items from past weekdays ──

    @Test
    fun getOverdueItems_mondayHasNoOverdue() {
        // On Monday (todayWeekday=1), all other days are future → overdue is empty
        val items = listOf(
            ChecklistFillItem("Past", checked = false, weekday = 7),  // Sunday
            ChecklistFillItem("Today", checked = false, weekday = 1), // Monday
            ChecklistFillItem("Future", checked = false, weekday = 2), // Tuesday
        )
        val overdue = getOverdueItems(items, todayWeekday = 1)
        assertTrue(overdue.isEmpty(), "Monday should have no overdue items (week-reset semantics)")
    }

    @Test
    fun getOverdueItems_wednesdayFiltersCorrectly() {
        // Today = Wednesday (3)
        // Overdue: Mon(1), Tue(2)
        // Not overdue: Wed(3), Thu(4), Fri(5), Sat(6), Sun(7)
        val items = listOf(
            ChecklistFillItem("Mon unchecked", checked = false, weekday = 1),    // Overdue
            ChecklistFillItem("Mon checked", checked = true, weekday = 1),       // Not overdue (checked)
            ChecklistFillItem("Tue unchecked", checked = false, weekday = 2),    // Overdue
            ChecklistFillItem("Wed unchecked", checked = false, weekday = 3),    // Not overdue (today)
            ChecklistFillItem("Thu unchecked", checked = false, weekday = 4),    // Not overdue (future)
        )
        val overdue = getOverdueItems(items, todayWeekday = 3)
        assertEquals(2, overdue.size, "Should have 2 overdue items (Mon and Tue unchecked)")
        assertTrue(overdue.all { it.weekday in listOf(1, 2) }, "All overdue should be Mon/Tue")
        assertTrue(overdue.all { !it.checked }, "All overdue should be unchecked")
    }

    @Test
    fun getOverdueItems_ignoresCheckedItems() {
        // Today = Friday (5)
        // Even if weekday < today, checked items are NOT overdue
        val items = listOf(
            ChecklistFillItem("Mon checked", checked = true, weekday = 1),
            ChecklistFillItem("Tue unchecked", checked = false, weekday = 2),
        )
        val overdue = getOverdueItems(items, todayWeekday = 5)
        assertEquals(1, overdue.size, "Should have 1 overdue (Tue unchecked only)")
        assertEquals(2, overdue[0].weekday, "Overdue item should be Tue")
    }

    @Test
    fun getOverdueItems_ignoresNullWeekday() {
        // Items without weekday assignment (null) are not included
        val items = listOf(
            ChecklistFillItem("No weekday", checked = false, weekday = null),
            ChecklistFillItem("Mon unchecked", checked = false, weekday = 1),
        )
        val overdue = getOverdueItems(items, todayWeekday = 3)
        assertEquals(1, overdue.size, "Should have 1 overdue (Mon unchecked only)")
        assertEquals(1, overdue[0].weekday, "Overdue item should be Mon")
    }

    @Test
    fun getOverdueItems_emptyWhenAllCheckedOrFuture() {
        // Today = Friday (5)
        // Items: all checked or weekday >= today
        val items = listOf(
            ChecklistFillItem("Mon checked", checked = true, weekday = 1),
            ChecklistFillItem("Fri unchecked", checked = false, weekday = 5),
            ChecklistFillItem("Sat unchecked", checked = false, weekday = 6),
        )
        val overdue = getOverdueItems(items, todayWeekday = 5)
        assertTrue(overdue.isEmpty(), "Should have no overdue items")
    }

    @Test
    fun getOverdueItems_emptySundayFiltersCorrectly() {
        // Today = Sunday (7)
        // Overdue: Mon(1)..Sat(6) if unchecked
        val items = listOf(
            ChecklistFillItem("Mon", checked = false, weekday = 1),
            ChecklistFillItem("Sat", checked = false, weekday = 6),
            ChecklistFillItem("Sun", checked = false, weekday = 7),
        )
        val overdue = getOverdueItems(items, todayWeekday = 7)
        assertEquals(2, overdue.size, "Sun should have 2 overdue items (Mon, Sat)")
        assertTrue(overdue.all { it.weekday in listOf(1, 6) }, "Overdue should be Mon, Sat")
    }
}
