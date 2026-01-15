package com.antonchuraev.homesearchchecklist.feature.home.presentation.fill

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FillDetailViewModelTest {

    private fun createTestFill(
        id: Long = 1L,
        checklistId: Long = 1L,
        name: String = "Test Fill",
        items: List<ChecklistFillItem> = listOf(
            ChecklistFillItem("Item 1", checked = false),
            ChecklistFillItem("Item 2", checked = true)
        )
    ) = ChecklistFill(
        id = id,
        checklistId = checklistId,
        name = name,
        items = items,
        createdAt = System.currentTimeMillis()
    )

    @Test
    fun `Content state should have correct initial values`() {
        val fill = createTestFill()
        val state = FillDetailState.Content(
            fill = fill,
            editingName = fill.name
        )

        assertFalse(state.isEditing)
        assertNull(state.editingNameError)
        assertFalse(state.showDeleteConfirmation)
        assertNull(state.noteDialogItemIndex)
        assertEquals("", state.editingNote)
    }

    @Test
    fun `Content state should track editing mode`() {
        val fill = createTestFill()
        val editingState = FillDetailState.Content(
            fill = fill,
            isEditing = true,
            editingName = "Updated Name"
        )

        assertTrue(editingState.isEditing)
        assertEquals("Updated Name", editingState.editingName)
    }

    @Test
    fun `Content state should preserve validation error`() {
        val fill = createTestFill()
        val stateWithError = FillDetailState.Content(
            fill = fill,
            editingName = "",
            editingNameError = "Name is required"
        )

        assertEquals("Name is required", stateWithError.editingNameError)
    }

    @Test
    fun `Content state should track delete confirmation`() {
        val fill = createTestFill()
        val deleteConfirmState = FillDetailState.Content(
            fill = fill,
            showDeleteConfirmation = true
        )

        assertTrue(deleteConfirmState.showDeleteConfirmation)
    }

    @Test
    fun `Content state should track note dialog`() {
        val fill = createTestFill()
        val noteDialogState = FillDetailState.Content(
            fill = fill,
            noteDialogItemIndex = 0,
            editingNote = "Test note"
        )

        assertEquals(0, noteDialogState.noteDialogItemIndex)
        assertEquals("Test note", noteDialogState.editingNote)
    }

    @Test
    fun `Loading state should be correct type`() {
        val state: FillDetailState = FillDetailState.Loading
        assertTrue(state is FillDetailState.Loading)
    }

    @Test
    fun `NotFound state should be correct type`() {
        val state: FillDetailState = FillDetailState.NotFound
        assertTrue(state is FillDetailState.NotFound)
    }

    @Test
    fun `Content state should calculate checked items count`() {
        val items = listOf(
            ChecklistFillItem("Item 1", checked = true),
            ChecklistFillItem("Item 2", checked = false),
            ChecklistFillItem("Item 3", checked = true),
            ChecklistFillItem("Item 4", checked = false)
        )
        val fill = createTestFill(items = items)
        val state = FillDetailState.Content(fill = fill, editingName = fill.name)

        val checkedCount = state.fill.items.count { it.checked }
        val uncheckedCount = state.fill.items.count { !it.checked }

        assertEquals(2, checkedCount)
        assertEquals(2, uncheckedCount)
        assertEquals(4, state.fill.items.size)
    }

    @Test
    fun `FillDetailIntent should have all required intents`() {
        // Verify intent types exist (compile-time check)
        val intents: List<FillDetailIntent> = listOf(
            FillDetailIntent.OnBackClick,
            FillDetailIntent.OnEditClick,
            FillDetailIntent.OnSaveClick,
            FillDetailIntent.OnCancelEditClick,
            FillDetailIntent.OnNameChanged("test"),
            FillDetailIntent.OnDeleteClick,
            FillDetailIntent.OnConfirmDelete,
            FillDetailIntent.OnDismissDeleteConfirmation,
            FillDetailIntent.OnItemCheckedChange(0, true),
            FillDetailIntent.OnAddNoteClick(0),
            FillDetailIntent.OnNoteChanged("note"),
            FillDetailIntent.OnSaveNote,
            FillDetailIntent.OnDismissNoteDialog,
            FillDetailIntent.OnChangeCoverClick,
            FillDetailIntent.OnRemoveCoverClick
        )

        assertEquals(15, intents.size)
    }
}
