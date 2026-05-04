package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeeklyChecklistDetailStateTest {

    // ── moveToDayItemId state ──

    @Test
    fun moveToDayItemId_defaultsToNull() {
        val state = ChecklistDetailState.Content(
            checklist = weeklyChecklist(),
            defaultFill = null
        )
        assertNull(state.moveToDayItemId)
    }

    @Test
    fun moveToDayItemId_canBeSet() {
        val state = ChecklistDetailState.Content(
            checklist = weeklyChecklist(),
            defaultFill = null,
            moveToDayItemId = "abc-123"
        )
        assertEquals("abc-123", state.moveToDayItemId)
    }

    @Test
    fun moveToDayItemId_clearedViaCopy() {
        val state = ChecklistDetailState.Content(
            checklist = weeklyChecklist(),
            defaultFill = null,
            moveToDayItemId = "abc-123"
        )
        val cleared = state.copy(moveToDayItemId = null)
        assertNull(cleared.moveToDayItemId)
    }

    // ── viewMode flag in state ──

    @Test
    fun checklist_viewMode_isWeekly_whenSet() {
        val state = ChecklistDetailState.Content(
            checklist = weeklyChecklist(),
            defaultFill = null
        )
        assertEquals(ChecklistViewMode.Weekly, state.checklist.viewMode)
    }

    @Test
    fun checklist_viewMode_isStandard_byDefault() {
        val state = ChecklistDetailState.Content(
            checklist = Checklist(name = "Standard", items = emptyList()),
            defaultFill = null
        )
        assertEquals(ChecklistViewMode.Standard, state.checklist.viewMode)
    }

    // ── Items grouping by weekday (logic mirroring WeeklyChecklistDetailContent) ──

    @Test
    fun items_groupByWeekday_correctlyDistributesAcrossDays() {
        val items = listOf(
            ChecklistFillItem("Mon-1", checked = false, weekday = 1),
            ChecklistFillItem("Mon-2", checked = false, weekday = 1),
            ChecklistFillItem("Wed-1", checked = false, weekday = 3),
            ChecklistFillItem("Sun-1", checked = true, weekday = 7),
        )
        val grouped: Map<Int, List<ChecklistFillItem>> =
            items.filter { it.weekday != null }.groupBy { it.weekday!! }

        assertEquals(2, grouped[1]?.size)
        assertEquals(1, grouped[3]?.size)
        assertEquals(1, grouped[7]?.size)
        assertNull(grouped[2], "Tuesday slot should not exist if no items have weekday=2")
    }

    @Test
    fun items_withNullWeekday_excludedFromGrouping() {
        val items = listOf(
            ChecklistFillItem("Unscheduled", checked = false, weekday = null),
            ChecklistFillItem("Mon", checked = false, weekday = 1),
        )
        val grouped = items.filter { it.weekday != null }.groupBy { it.weekday!! }
        assertEquals(1, grouped.size)
        assertEquals("Mon", grouped[1]?.first()?.text)
    }

    @Test
    fun items_emptyList_yieldsEmptyGrouping() {
        val items = emptyList<ChecklistFillItem>()
        val grouped = items.filter { it.weekday != null }.groupBy { it.weekday!! }
        assertTrue(grouped.isEmpty())
    }

    // ── Move-item-by-id logic (mirrors moveItemToDay in ViewModel) ──

    @Test
    fun moveItem_byId_preservesIdAndUpdatesWeekday() {
        val target = ChecklistFillItem("Run", checked = true, note = "5km", weekday = 1)
        val items = listOf(
            ChecklistFillItem("Other", checked = false, weekday = 2),
            target,
            ChecklistFillItem("Yet another", checked = false, weekday = 3),
        )
        val targetId = target.id

        val movedToWeekday = 5
        val updated = items.map { item ->
            if (item.id == targetId) item.withWeekday(movedToWeekday) else item
        }

        val movedItem = updated.first { it.id == targetId }
        assertEquals(5, movedItem.weekday)
        assertEquals(target.id, movedItem.id, "id must be preserved by withWeekday()")
        assertEquals("Run", movedItem.text)
        assertTrue(movedItem.checked)
        assertEquals("5km", movedItem.note)

        // Other items untouched
        assertEquals(2, updated[0].weekday)
        assertEquals(3, updated[2].weekday)
    }

    @Test
    fun moveItem_byNonexistentId_yieldsNoChange() {
        val items = listOf(
            ChecklistFillItem("A", checked = false, weekday = 1),
            ChecklistFillItem("B", checked = false, weekday = 2),
        )
        val updated = items.map { item ->
            if (item.id == "nonexistent-id") item.withWeekday(7) else item
        }
        assertEquals(items.map { it.weekday }, updated.map { it.weekday })
        assertEquals(items.map { it.id }, updated.map { it.id })
    }

    // ── Add item to day (mirrors addItemToDay logic) ──

    @Test
    fun addItem_toSpecificDay_appendsWithWeekday() {
        val originalItems = listOf(
            ChecklistFillItem("A", checked = false, weekday = 1)
        )
        val newItem = ChecklistFillItem(text = "B", checked = false, weekday = 3)
        val updated = originalItems + newItem

        assertEquals(2, updated.size)
        assertEquals(3, updated.last().weekday)
        assertEquals("B", updated.last().text)
        assertTrue(updated.last().id.isNotBlank(), "new item must have generated id")
    }

    @Test
    fun addItem_template_alsoGetsWeekday() {
        val checklist = weeklyChecklist()
        val newTemplateItem = ChecklistItem(text = "Plan trip", weekday = 6)
        val updated = checklist.copy(items = checklist.items + newTemplateItem)

        assertEquals(1, updated.items.size)
        assertEquals(6, updated.items.first().weekday)
        assertEquals(ChecklistViewMode.Weekly, updated.viewMode, "viewMode must persist on copy()")
    }

    // ── helpers ──

    private fun weeklyChecklist() = Checklist(
        id = 42L,
        name = "Моя неделя",
        items = emptyList(),
        viewMode = ChecklistViewMode.Weekly
    )

    @Suppress("unused") // sample for future integration tests
    private fun weeklyFill(items: List<ChecklistFillItem>) = ChecklistFill(
        id = 1L,
        checklistId = 42L,
        name = "",
        items = items,
        createdAt = 0L,
        isDefault = true
    )
}
