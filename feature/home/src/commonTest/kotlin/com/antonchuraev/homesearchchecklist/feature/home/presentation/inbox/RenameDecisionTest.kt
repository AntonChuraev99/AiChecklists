package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The four outcomes of confirming the Inbox rename dialog.
 *
 * Worth its own test because the failure this guards against is invisible: every wrong outcome still
 * closes the dialog and still shows *a* message, so nothing crashes and nothing looks broken — the
 * user is simply told the wrong reason and the log says nothing. The shipped version collapsed
 * "blank input" and "the target is gone" into one branch and reported "Enter a name" for both.
 *
 * Tested at this level and not through [InboxViewModel] because the ViewModel resolves the Inbox
 * title through Compose Resources in its `init`, which has no loader on a JVM host — constructing it
 * throws before any intent can be sent (same constraint documented in `TemplatePreviewViewModelTest`).
 */
class RenameDecisionTest {

    @Test
    fun nullDraft_isBlankName() {
        assertEquals(RenameDecision.BlankName, decideRename(draft = null, page = project()))
    }

    @Test
    fun emptyDraft_isBlankName() {
        assertEquals(RenameDecision.BlankName, decideRename(draft = "", page = project()))
    }

    /** The dialog's own Save button uses `isNotBlank`, so the two agree on whitespace-only input. */
    @Test
    fun whitespaceOnlyDraft_isBlankName() {
        assertEquals(RenameDecision.BlankName, decideRename(draft = "   ", page = project()))
    }

    /**
     * THE regression: the user typed a valid name, a sync deleted that project, the pager shifted,
     * and the confirm found no page. "Enter a name" would blame input that was never the problem.
     */
    @Test
    fun validDraft_missingPage_isInvalidTarget_notBlankName() {
        assertEquals(RenameDecision.InvalidTarget, decideRename(draft = "Groceries", page = null))
    }

    /** Same shape with the Inbox in the slot: the system row has no rename. */
    @Test
    fun validDraft_inboxPage_isInvalidTarget() {
        assertEquals(RenameDecision.InvalidTarget, decideRename(draft = "Groceries", page = inbox()))
    }

    /** Blank input wins over a missing target: with no name, which page is selected cannot matter. */
    @Test
    fun blankDraft_missingPage_isBlankName() {
        assertEquals(RenameDecision.BlankName, decideRename(draft = " ", page = null))
    }

    @Test
    fun draftEqualToCurrentTitle_isUnchanged() {
        assertEquals(RenameDecision.Unchanged, decideRename(draft = "Trip", page = project(title = "Trip")))
    }

    /** Trimmed before comparing, or re-saving "Trip " would count as an edit and dirty the row. */
    @Test
    fun draftEqualToCurrentTitleAfterTrim_isUnchanged() {
        assertEquals(RenameDecision.Unchanged, decideRename(draft = "  Trip  ", page = project(title = "Trip")))
    }

    @Test
    fun newNameOnProjectPage_isRename_withTrimmedName() {
        assertEquals(
            RenameDecision.Rename(checklistId = 7L, name = "Trip 2026"),
            decideRename(draft = "  Trip 2026  ", page = project(id = 7L, title = "Trip")),
        )
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun project(id: Long = 1L, title: String = "Trip") =
        InboxPage(checklistId = id, title = title, isInbox = false, tasks = emptyList())

    private fun inbox() =
        InboxPage(checklistId = 99L, title = "Inbox", isInbox = true, tasks = emptyList())
}
