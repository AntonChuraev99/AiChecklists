package com.antonchuraev.homesearchchecklist.feature.home.presentation.projects

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistNodeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The counting rules behind the Projects tab's trailing number.
 *
 * Worth its own test because every one of these cases renders a plausible-looking row when it is
 * wrong: a stale count, a "0 left" that should be a tick, or a tick on a checklist the user never
 * filled in. None of them crashes, so nothing else would catch them.
 */
class ProjectRowMappingTest {

    @Test
    fun openCount_countsUncheckedFillItems() {
        val row = toProjectRow(
            checklist = checklist(id = 7, name = "Trip", templateItems = 3),
            fill = fill(checked = listOf(true, false, false)),
        )

        assertEquals(7L, row.checklistId)
        assertEquals("Trip", row.title)
        assertEquals(2, row.openCount)
        assertFalse(row.isComplete)
    }

    /**
     * The template must NOT be the source of the count: it has no checked state that moves, so a
     * checklist would show the same number forever.
     */
    @Test
    fun openCount_ignoresTemplateSizeWhenFillExists() {
        val row = toProjectRow(
            // Template says 5, the fill is what the user actually works with.
            checklist = checklist(id = 1, name = "Groceries", templateItems = 5),
            fill = fill(checked = listOf(true, true)),
        )

        assertEquals(0, row.openCount)
        assertTrue(row.isComplete)
    }

    /**
     * A freshly synced checklist can exist before its default fill does. "No fill" means nothing has
     * been checked yet — so every template item is open, NOT zero.
     */
    @Test
    fun openCount_noFill_fallsBackToTemplateAsAllOpen() {
        val row = toProjectRow(
            checklist = checklist(id = 2, name = "Move", templateItems = 4),
            fill = null,
        )

        assertEquals(4, row.openCount)
        assertFalse(row.isComplete, "an unfilled checklist is not a finished one")
    }

    /** An empty checklist has zero open items too — but it is not an achievement. */
    @Test
    fun isComplete_emptyChecklist_isNotComplete() {
        val row = toProjectRow(
            checklist = checklist(id = 3, name = "Empty", templateItems = 0),
            fill = fill(checked = emptyList()),
        )

        assertEquals(0, row.openCount)
        assertFalse(row.isComplete)
    }

    @Test
    fun isComplete_allItemsChecked_isComplete() {
        val row = toProjectRow(
            checklist = checklist(id = 4, name = "Packed", templateItems = 2),
            fill = fill(checked = listOf(true, true)),
        )

        assertEquals(0, row.openCount)
        assertTrue(row.isComplete)
    }

    /**
     * Folders are mirrored into the fill like any other template node and are never checked, so
     * counting them adds a permanent +1 per folder — the number the Inbox toolbar shows for the same
     * checklist (which filters them) would then be smaller for no reason the user can see.
     */
    @Test
    fun openCount_ignoresFolderMirrorRows() {
        val folder = ChecklistItem(text = "Packing", type = ChecklistNodeType.FOLDER)
        val leaf = ChecklistItem(text = "Passport", parentId = folder.id)
        val row = toProjectRow(
            checklist = Checklist(id = 5, name = "Trip", items = listOf(folder, leaf)),
            fill = ChecklistFill(
                id = 1L,
                checklistId = 5L,
                name = "default",
                items = listOf(
                    ChecklistFillItem(text = folder.text, checked = false, templateItemId = folder.id),
                    ChecklistFillItem(text = leaf.text, checked = false, templateItemId = leaf.id),
                ),
                isDefault = true,
            ),
        )

        assertEquals(1, row.openCount)
        assertFalse(row.isComplete)
    }

    /** …and a checklist whose only OPEN row is a folder mirror is finished, not "1 left". */
    @Test
    fun isComplete_allLeavesChecked_folderMirrorDoesNotHoldItOpen() {
        val folder = ChecklistItem(text = "Packing", type = ChecklistNodeType.FOLDER)
        val leaf = ChecklistItem(text = "Passport", parentId = folder.id)
        val row = toProjectRow(
            checklist = Checklist(id = 6, name = "Trip", items = listOf(folder, leaf)),
            fill = ChecklistFill(
                id = 1L,
                checklistId = 6L,
                name = "default",
                items = listOf(
                    ChecklistFillItem(text = folder.text, checked = false, templateItemId = folder.id),
                    ChecklistFillItem(text = leaf.text, checked = true, templateItemId = leaf.id),
                ),
                isDefault = true,
            ),
        )

        assertEquals(0, row.openCount)
        assertTrue(row.isComplete)
    }

    /** The no-fill fallback counts template LEAVES, so a folder cannot inflate it either. */
    @Test
    fun openCount_noFill_countsTemplateLeavesOnly() {
        val folder = ChecklistItem(text = "Packing", type = ChecklistNodeType.FOLDER)
        val row = toProjectRow(
            checklist = Checklist(
                id = 7,
                name = "Trip",
                items = listOf(folder, ChecklistItem(text = "Passport", parentId = folder.id)),
            ),
            fill = null,
        )

        assertEquals(1, row.openCount)
        assertFalse(row.isComplete, "an unfilled checklist is not a finished one")
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun checklist(id: Long, name: String, templateItems: Int) = Checklist(
        id = id,
        name = name,
        items = List(templateItems) { ChecklistItem(text = "item $it") },
    )

    private fun fill(checked: List<Boolean>) = ChecklistFill(
        id = 1L,
        checklistId = 1L,
        name = "default",
        items = checked.mapIndexed { index, isChecked ->
            ChecklistFillItem(text = "item $index", checked = isChecked)
        },
        isDefault = true,
    )
}
