package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.UndoableDeleteItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChecklistDetailViewModelTest {

    @Test
    fun `Content state should track dialog visibility correctly`() {
        val checklist = Checklist(
            id = 1L,
            name = "Test Checklist",
            items = listOf(ChecklistItem("Item 1"), ChecklistItem("Item 2"))
        )

        val initialState = ChecklistDetailState.Content(
            checklist = checklist,
            defaultFill = null
        )

        // Dialog should be closed initially
        assertEquals(false, initialState.showAddFillDialog)
        assertEquals("", initialState.newFillName)
        assertNull(initialState.fillNameError)
        assertEquals(false, initialState.isCreatingFill)
    }

    @Test
    fun `Content state should update dialog open state`() {
        val checklist = Checklist(
            id = 1L,
            name = "Test Checklist",
            items = listOf(ChecklistItem("Item 1"))
        )

        val openState = ChecklistDetailState.Content(
            checklist = checklist,
            defaultFill = null,
            showAddFillDialog = true,
            newFillName = ""
        )

        assertTrue(openState.showAddFillDialog)
    }

    @Test
    fun `Content state should preserve error message`() {
        val checklist = Checklist(
            id = 1L,
            name = "Test Checklist",
            items = emptyList()
        )

        val stateWithError = ChecklistDetailState.Content(
            checklist = checklist,
            defaultFill = null,
            showAddFillDialog = true,
            fillNameError = "Name cannot be empty"
        )

        assertEquals("Name cannot be empty", stateWithError.fillNameError)
    }

    @Test
    fun `Loading state should be correct type`() {
        val state: ChecklistDetailState = ChecklistDetailState.Loading
        assertTrue(state is ChecklistDetailState.Loading)
    }

    @Test
    fun `NotFound state should be correct type`() {
        val state: ChecklistDetailState = ChecklistDetailState.NotFound
        assertTrue(state is ChecklistDetailState.NotFound)
    }

    // ── Swipe-to-delete state tests ──────────────────────────────────────

    @Test
    fun `UndoableDeleteItem should store fill item and indices`() {
        val fillItem = ChecklistFillItem("Buy milk", checked = false, note = "2L")
        val undo = UndoableDeleteItem(
            fillItem = fillItem,
            checklistItemText = "Buy milk",
            originalFillIndex = 2,
            originalChecklistIndex = 1,
        )
        assertEquals("Buy milk", undo.fillItem.text)
        assertEquals("2L", undo.fillItem.note)
        assertEquals(2, undo.originalFillIndex)
        assertEquals(1, undo.originalChecklistIndex)
    }

    @Test
    fun `Content state pendingUndoItem should be null by default`() {
        val state = ChecklistDetailState.Content(
            checklist = Checklist(id = 1L, name = "Test", items = emptyList()),
            defaultFill = null
        )
        assertNull(state.pendingUndoItem)
    }

    @Test
    fun `Content state should store pendingUndoItem`() {
        val fillItem = ChecklistFillItem("Task 1", checked = false)
        val undo = UndoableDeleteItem(
            fillItem = fillItem,
            checklistItemText = "Task 1",
            originalFillIndex = 0,
            originalChecklistIndex = 0,
        )
        val state = ChecklistDetailState.Content(
            checklist = Checklist(id = 1L, name = "Test", items = emptyList()),
            defaultFill = null,
            pendingUndoItem = undo
        )
        assertEquals(fillItem, state.pendingUndoItem?.fillItem)
        assertEquals(0, state.pendingUndoItem?.originalFillIndex)
    }

    @Test
    fun `Clearing pendingUndoItem via copy should work`() {
        val fillItem = ChecklistFillItem("Task", checked = false)
        val undo = UndoableDeleteItem(fillItem, "Task", 0, 0)
        val state = ChecklistDetailState.Content(
            checklist = Checklist(id = 1L, name = "Test", items = emptyList()),
            defaultFill = null,
            pendingUndoItem = undo
        )
        val cleared = state.copy(pendingUndoItem = null)
        assertNull(cleared.pendingUndoItem)
    }

    @Test
    fun `UndoableDeleteItem preserves note in fill item`() {
        val fillItem = ChecklistFillItem("Item with note", checked = true, note = "Important note")
        val undo = UndoableDeleteItem(fillItem, "Item with note", 3, 2)
        assertEquals("Important note", undo.fillItem.note)
        assertTrue(undo.fillItem.checked)
    }

    @Test
    fun `UndoableDeleteItem handles negative checklist index`() {
        val fillItem = ChecklistFillItem("Orphan", checked = false)
        val undo = UndoableDeleteItem(fillItem, "Orphan", 0, -1)
        assertEquals(-1, undo.originalChecklistIndex)
    }

    @Test
    fun `Content state should calculate progress correctly from default fill`() {
        val checklist = Checklist(
            id = 1L,
            name = "Test Checklist",
            items = listOf(
                ChecklistItem("Item 1"),
                ChecklistItem("Item 2"),
                ChecklistItem("Item 3")
            )
        )

        val defaultFill = ChecklistFill(
            id = 1L,
            checklistId = 1L,
            name = "",
            items = listOf(
                ChecklistFillItem("Item 1", checked = true),
                ChecklistFillItem("Item 2", checked = false),
                ChecklistFillItem("Item 3", checked = true)
            ),
            createdAt = 0L,
            isDefault = true
        )

        val state = ChecklistDetailState.Content(
            checklist = checklist,
            defaultFill = defaultFill
        )

        // Progress should be calculated from defaultFill, not from checklist
        val checkedCount = state.defaultFill?.items?.count { it.checked } ?: 0
        val totalCount = state.defaultFill?.items?.size ?: 0

        assertEquals(2, checkedCount)
        assertEquals(3, totalCount)
    }
}
