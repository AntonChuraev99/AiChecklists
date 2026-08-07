package com.antonchuraev.homesearchchecklist.feature.home.presentation.projects

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistNodeType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The counting rules behind the Projects tab's trailing mini-stat badges.
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

    // ── totalCount: the denominator must come from the same set as the numerator ─────────────

    /**
     * The ring's denominator has to be the FILL's leaf count, exactly like [ProjectRow.openCount].
     * Taking `checklist.items.size` instead would make a checklist whose fill has been edited draw
     * an arc that disagrees with the number printed next to it.
     */
    @Test
    fun totalCount_countsFillLeaves_notTemplateSize() {
        val row = toProjectRow(
            checklist = checklist(id = 10, name = "Groceries", templateItems = 5),
            fill = fill(checked = listOf(true, true)),
        )

        assertEquals(2, row.totalCount)
        assertEquals(0, row.openCount)
        assertTrue(row.isComplete)
    }

    /** No fill yet: the denominator falls back to template LEAVES, so a folder cannot inflate it. */
    @Test
    fun totalCount_noFill_countsTemplateLeavesOnly() {
        val folder = ChecklistItem(text = "Packing", type = ChecklistNodeType.FOLDER)
        val row = toProjectRow(
            checklist = Checklist(
                id = 11,
                name = "Trip",
                items = listOf(folder, ChecklistItem(text = "Passport", parentId = folder.id)),
            ),
            fill = null,
        )

        assertEquals(1, row.totalCount)
        assertEquals(1, row.openCount)
    }

    /** Folder mirror rows are excluded from the denominator too, or the ring never reaches full. */
    @Test
    fun totalCount_excludesFolderMirrorRows() {
        val folder = ChecklistItem(text = "Packing", type = ChecklistNodeType.FOLDER)
        val leaf = ChecklistItem(text = "Passport", parentId = folder.id)
        val row = toProjectRow(
            checklist = Checklist(id = 12, name = "Trip", items = listOf(folder, leaf)),
            fill = ChecklistFill(
                id = 1L,
                checklistId = 12L,
                name = "default",
                items = listOf(
                    ChecklistFillItem(text = folder.text, checked = false, templateItemId = folder.id),
                    ChecklistFillItem(text = leaf.text, checked = true, templateItemId = leaf.id),
                ),
                isDefault = true,
            ),
        )

        assertEquals(1, row.totalCount)
        assertEquals(1f, row.doneFraction, FractionTolerance)
    }

    // ── doneFraction ─────────────────────────────────────────────────────────

    /**
     * Zero of zero is 0f, never 1f. A checklist with no items has nothing done, and rendering a full
     * ring on it would congratulate the user for a list they never wrote — the same invariant
     * [ProjectRow.isComplete] already carries.
     */
    @Test
    fun doneFraction_emptyChecklist_isZeroNotOne() {
        val row = toProjectRow(
            checklist = checklist(id = 13, name = "Empty", templateItems = 0),
            fill = fill(checked = emptyList()),
        )

        assertEquals(0, row.totalCount)
        assertEquals(0f, row.doneFraction, FractionTolerance)
        assertFalse(row.isComplete)
    }

    @Test
    fun doneFraction_halfChecked() {
        val row = toProjectRow(
            checklist = checklist(id = 14, name = "Move", templateItems = 4),
            fill = fill(checked = listOf(true, true, false, false)),
        )

        assertEquals(4, row.totalCount)
        assertEquals(2, row.openCount)
        assertEquals(0.5f, row.doneFraction, FractionTolerance)
    }

    @Test
    fun doneFraction_allChecked_isOne() {
        val row = toProjectRow(
            checklist = checklist(id = 15, name = "Packed", templateItems = 2),
            fill = fill(checked = listOf(true, true)),
        )

        assertEquals(1f, row.doneFraction, FractionTolerance)
        assertTrue(row.isComplete)
    }

    // ── isEmpty: must agree with what OPENING the checklist shows ────────────

    /**
     * The regression this field exists for: a checklist holding a folder and no tasks has zero
     * LEAVES, so `totalCount == 0` — but the detail screen renders the folder row, so the Projects
     * card must not print "Empty" over it. A counter is only allowed to say "nothing here" about the
     * same node set the other screen draws.
     */
    @Test
    fun isEmpty_folderWithNoLeaves_isNotEmpty() {
        val folder = ChecklistItem(text = "Packing", type = ChecklistNodeType.FOLDER)
        val row = toProjectRow(
            checklist = Checklist(id = 20, name = "Trip", items = listOf(folder)),
            fill = ChecklistFill(
                id = 1L,
                checklistId = 20L,
                name = "default",
                items = listOf(
                    ChecklistFillItem(text = folder.text, checked = false, templateItemId = folder.id),
                ),
                isDefault = true,
            ),
        )

        assertEquals(0, row.totalCount, "folders are still excluded from the leaf counts")
        assertFalse(row.isEmpty, "the detail screen renders this folder, so the row is not empty")
    }

    @Test
    fun isEmpty_noNodesAtAll_isEmpty() {
        val row = toProjectRow(
            checklist = checklist(id = 21, name = "Blank", templateItems = 0),
            fill = fill(checked = emptyList()),
        )

        assertTrue(row.isEmpty)
    }

    @Test
    fun isEmpty_withLeaves_isNotEmpty() {
        val row = toProjectRow(
            checklist = checklist(id = 22, name = "Move", templateItems = 2),
            fill = fill(checked = listOf(true, false)),
        )

        assertFalse(row.isEmpty)
    }

    /**
     * No fill yet (freshly synced row): emptiness has to fall back to the TEMPLATE's node count for
     * the same reason the counts do — otherwise a checklist that has content reads "Empty" until its
     * fill materialises.
     */
    @Test
    fun isEmpty_noFill_readsTemplateNodesIncludingFolders() {
        val folder = ChecklistItem(text = "Packing", type = ChecklistNodeType.FOLDER)
        val row = toProjectRow(
            checklist = Checklist(id = 23, name = "Trip", items = listOf(folder)),
            fill = null,
        )

        assertEquals(0, row.totalCount)
        assertFalse(row.isEmpty)
    }

    // ── reminderCount: same leaf set as the other two badges ─────────────────

    /** A one-shot reminder makes its row count; the rows without one do not. */
    @Test
    fun reminderCount_countsLeavesWithOneShotReminder() {
        val row = toProjectRow(
            checklist = checklist(id = 30, name = "Trip", templateItems = 3),
            fill = ChecklistFill(
                id = 1L,
                checklistId = 30L,
                name = "default",
                items = listOf(
                    ChecklistFillItem(text = "Passport", checked = false)
                        .withReminderAt(1_700_000_000_000L),
                    ChecklistFillItem(text = "Tickets", checked = false),
                    ChecklistFillItem(text = "Visa", checked = true)
                        .withReminderAt(1_700_000_100_000L),
                ),
                isDefault = true,
            ),
        )

        assertEquals(2, row.reminderCount, "a checked row can still hold a pending reminder")
        assertEquals(3, row.totalCount)
    }

    /** A recurring rule with no one-shot timestamp is an active reminder too. */
    @Test
    fun reminderCount_countsRecurringRuleWithoutOneShot() {
        val row = toProjectRow(
            checklist = checklist(id = 31, name = "Chores", templateItems = 2),
            fill = ChecklistFill(
                id = 1L,
                checklistId = 31L,
                name = "default",
                items = listOf(
                    ChecklistFillItem(text = "Water plants", checked = false).withRepeatRule(
                        rule = ReminderRepeatRule(type = RepeatType.DAILY),
                        timeOfDayMinutes = 9 * 60,
                        nextAt = 1_700_000_000_000L,
                    ),
                    ChecklistFillItem(text = "Take out bins", checked = false),
                ),
                isDefault = true,
            ),
        )

        assertEquals(1, row.reminderCount)
    }

    @Test
    fun reminderCount_noReminders_isZero() {
        val row = toProjectRow(
            checklist = checklist(id = 32, name = "Move", templateItems = 2),
            fill = fill(checked = listOf(true, false)),
        )

        assertEquals(0, row.reminderCount)
    }

    /**
     * The badge row's whole contract: all three numbers describe the SAME rows. A folder mirror is
     * excluded from [ProjectRow.openCount] and [ProjectRow.totalCount], so a reminder sitting on it
     * must not appear in [ProjectRow.reminderCount] either — a bell over a card whose task counts
     * know nothing about that row is the counter-vs-rendered-set mismatch all over again.
     */
    @Test
    fun reminderCount_ignoresFolderMirrorRows() {
        val folder = ChecklistItem(text = "Packing", type = ChecklistNodeType.FOLDER)
        val leaf = ChecklistItem(text = "Passport", parentId = folder.id)
        val row = toProjectRow(
            checklist = Checklist(id = 33, name = "Trip", items = listOf(folder, leaf)),
            fill = ChecklistFill(
                id = 1L,
                checklistId = 33L,
                name = "default",
                items = listOf(
                    ChecklistFillItem(text = folder.text, checked = false, templateItemId = folder.id)
                        .withReminderAt(1_700_000_000_000L),
                    ChecklistFillItem(text = leaf.text, checked = false, templateItemId = leaf.id),
                ),
                isDefault = true,
            ),
        )

        assertEquals(1, row.totalCount)
        assertEquals(0, row.reminderCount, "a folder mirror is not one of the rows these counts describe")
    }

    /**
     * A checklist whose only content is folders: no leaves at all, so every badge is zero even
     * though the row is NOT empty (the detail screen renders those folders). The card must print no
     * numbers here rather than a zero dressed as a stat.
     */
    @Test
    fun reminderCount_folderOnlyChecklist_isZero() {
        val folderA = ChecklistItem(text = "Packing", type = ChecklistNodeType.FOLDER)
        val folderB = ChecklistItem(text = "Documents", type = ChecklistNodeType.FOLDER)
        val row = toProjectRow(
            checklist = Checklist(id = 34, name = "Trip", items = listOf(folderA, folderB)),
            fill = ChecklistFill(
                id = 1L,
                checklistId = 34L,
                name = "default",
                items = listOf(
                    ChecklistFillItem(text = folderA.text, checked = false, templateItemId = folderA.id)
                        .withReminderAt(1_700_000_000_000L),
                    ChecklistFillItem(text = folderB.text, checked = false, templateItemId = folderB.id),
                ),
                isDefault = true,
            ),
        )

        assertEquals(0, row.totalCount)
        assertEquals(0, row.openCount)
        assertEquals(0, row.reminderCount)
        assertFalse(row.isEmpty, "the detail screen renders these folders, so the row is not empty")
        assertFalse(row.isComplete)
    }

    /**
     * Reminders exist on fill items only — `ChecklistItem` has no reminder fields — so a checklist
     * whose fill has not materialised yet reports 0 rather than borrowing a number from the
     * template the way [ProjectRow.openCount] does.
     */
    @Test
    fun reminderCount_noFill_isZero() {
        val row = toProjectRow(
            checklist = checklist(id = 35, name = "Move", templateItems = 4),
            fill = null,
        )

        assertEquals(4, row.openCount)
        assertEquals(0, row.reminderCount)
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

    private companion object {
        /** doneFraction is a Float division — compare with a delta, never with `==`. */
        const val FractionTolerance = 0.0001f
    }
}
