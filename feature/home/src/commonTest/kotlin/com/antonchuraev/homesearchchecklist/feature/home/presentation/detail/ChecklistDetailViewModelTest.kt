package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
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
            fills = emptyList()
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
            fills = emptyList(),
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
            fills = emptyList(),
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

    @Test
    fun `Content state should calculate progress correctly`() {
        val checklist = Checklist(
            id = 1L,
            name = "Test Checklist",
            items = listOf(
                ChecklistItem("Item 1", checked = true),
                ChecklistItem("Item 2", checked = false),
                ChecklistItem("Item 3", checked = true)
            )
        )

        val state = ChecklistDetailState.Content(
            checklist = checklist,
            fills = emptyList()
        )

        val checkedCount = state.checklist.items.count { it.checked }
        val totalCount = state.checklist.items.size

        assertEquals(2, checkedCount)
        assertEquals(3, totalCount)
    }
}
