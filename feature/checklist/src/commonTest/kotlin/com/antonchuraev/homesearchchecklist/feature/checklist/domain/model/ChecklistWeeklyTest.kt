package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChecklistWeeklyTest {

    // ── ChecklistViewMode enum ──

    @Test
    fun viewMode_default_isStandard() {
        val checklist = Checklist(name = "Test", items = emptyList())
        assertEquals(ChecklistViewMode.Standard, checklist.viewMode)
    }

    @Test
    fun viewMode_canBeWeekly() {
        val checklist = Checklist(
            name = "My Week",
            items = emptyList(),
            viewMode = ChecklistViewMode.Weekly
        )
        assertEquals(ChecklistViewMode.Weekly, checklist.viewMode)
    }

    @Test
    fun viewMode_enumValues_areExactlyTwo() {
        assertEquals(2, ChecklistViewMode.entries.size)
    }

    // ── ChecklistItem.weekday ──

    @Test
    fun checklistItem_weekday_defaultNull() {
        val item = ChecklistItem(text = "Buy milk")
        assertNull(item.weekday)
    }

    @Test
    fun checklistItem_weekday_canBeSet() {
        val item = ChecklistItem(text = "Workout", weekday = 1)
        assertEquals(1, item.weekday)
    }

    @Test
    fun checklistItem_withText_preservesWeekday() {
        val original = ChecklistItem(text = "Workout", weekday = 3)
        val updated = original.withText("Stretching")
        assertEquals("Stretching", updated.text)
        assertEquals(3, updated.weekday)
    }

    @Test
    fun checklistItem_withText_preservesId() {
        val original = ChecklistItem(text = "Workout", weekday = 3)
        val updated = original.withText("Stretching")
        assertEquals(original.id, updated.id)
    }

    @Test
    fun checklistItem_withWeekday_preservesIdAndText() {
        val original = ChecklistItem(text = "Workout", weekday = 1)
        val updated = original.withWeekday(5)

        assertEquals(original.id, updated.id, "id must be preserved when moving items between days")
        assertEquals("Workout", updated.text)
        assertEquals(5, updated.weekday)
    }

    @Test
    fun checklistItem_withWeekday_canSetNull() {
        val original = ChecklistItem(text = "Unscheduled", weekday = 4)
        val updated = original.withWeekday(null)
        assertNull(updated.weekday)
        assertEquals(original.id, updated.id)
    }

    @Test
    fun checklistItem_directConstruction_generatesNewId() {
        val first = ChecklistItem(text = "X", weekday = 1)
        val second = ChecklistItem(text = "X", weekday = 1)
        // Different items must have different ids (regression: check that ids are generated)
        assertNotEquals(first.id, second.id)
    }

    // ── ChecklistFillItem.weekday ──

    @Test
    fun fillItem_weekday_defaultNull() {
        val item = ChecklistFillItem(text = "Task", checked = false)
        assertNull(item.weekday)
    }

    @Test
    fun fillItem_weekday_canBeSet() {
        val item = ChecklistFillItem(text = "Task", checked = false, weekday = 7)
        assertEquals(7, item.weekday)
    }

    @Test
    fun fillItem_withChecked_preservesWeekday() {
        val original = ChecklistFillItem(text = "Task", checked = false, weekday = 2)
        val updated = original.withChecked(true)
        assertTrue(updated.checked)
        assertEquals(2, updated.weekday)
        assertEquals(original.id, updated.id)
    }

    @Test
    fun fillItem_withNote_preservesWeekday() {
        val original = ChecklistFillItem(text = "Task", checked = false, weekday = 4)
        val updated = original.withNote("important")
        assertEquals("important", updated.note)
        assertEquals(4, updated.weekday)
        assertEquals(original.id, updated.id)
    }

    @Test
    fun fillItem_withWeekday_preservesIdTextCheckedNote() {
        val original = ChecklistFillItem(
            text = "Run",
            checked = true,
            note = "5km",
            weekday = 1
        )
        val updated = original.withWeekday(6)

        assertEquals(original.id, updated.id, "id must be preserved")
        assertEquals("Run", updated.text)
        assertTrue(updated.checked)
        assertEquals("5km", updated.note)
        assertEquals(6, updated.weekday)
    }

    @Test
    fun fillItem_withWeekday_canSetNull() {
        val original = ChecklistFillItem(text = "X", checked = false, weekday = 3)
        val updated = original.withWeekday(null)
        assertNull(updated.weekday)
        assertEquals(original.id, updated.id)
    }

    // ── ISO weekday range invariants ──

    @Test
    fun weekday_isoRange_acceptsOneThroughSeven() {
        for (day in 1..7) {
            val item = ChecklistItem(text = "X", weekday = day)
            assertEquals(day, item.weekday)
        }
    }
}
