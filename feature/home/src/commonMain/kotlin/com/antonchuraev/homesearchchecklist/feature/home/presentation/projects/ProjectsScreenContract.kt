package com.antonchuraev.homesearchchecklist.feature.home.presentation.projects

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State

/**
 * One row of the Projects tab.
 *
 * [openCount] counts UNCHECKED LEAF items, not total — the question the list answers is "how much
 * is left here", and a total would read as busy even for a finished checklist. Folder mirror rows
 * are excluded for the same reason the Inbox toolbar excludes them (see [toProjectRow]), so the two
 * screens cannot disagree about a checklist's size.
 *
 * A flattened projection rather than the `Checklist` model: this row renders a name, a progress ring
 * and a mini-stat of number badges derived from these counts, and handing screens the full model is
 * how the template↔fill desync bugs keep starting.
 */
data class ProjectRow(
    val checklistId: Long,
    val title: String,
    val openCount: Int,
    /**
     * Size of the SAME filtered leaf set [openCount] is counted from — never `checklist.items.size`.
     * Computed once in [toProjectRow] so the numerator and the denominator of [doneFraction] can not
     * come from two different sources (that asymmetry is how the template↔fill desync bugs start).
     */
    val totalCount: Int,
    /**
     * How many of the SAME leaf rows [openCount] counts carry an active reminder (one-shot OR
     * recurring) — `ChecklistFillItem.hasActiveReminder`.
     *
     * Counted over the identical filtered set on purpose: the three numbers the card prints side by
     * side have to describe one set of rows, or the card becomes three opinions about one checklist
     * (the counter-vs-rendered-node-set defect this project already shipped once on the detail
     * screen). Two consequences follow from that and are deliberate, not oversights:
     * - a reminder living on a FOLDER mirror row is not counted, exactly as that row is absent from
     *   [openCount] / [totalCount];
     * - the CHECKLIST-level reminder (`Checklist.reminderAt` / `repeatRule`) is not counted either —
     *   it is a property of the checklist, not one of the rows this stat describes.
     *
     * Reminders live on the FILL only (the template's `ChecklistItem` has no reminder fields at
     * all), so a checklist whose fill has not been created yet reports 0 — the honest answer, not a
     * fallback.
     */
    val reminderCount: Int,
    /** All items are done AND there is at least one — drives the "done" tick instead of a zero. */
    val isComplete: Boolean,
    /**
     * Opening this checklist would show NOTHING — no leaf rows and no folder rows. The only condition
     * under which the row may print "Empty".
     *
     * Deliberately not `totalCount == 0`. [totalCount] counts leaves only (a folder mirror is never
     * checked, so counting it would make [isComplete] unreachable — see [toProjectRow]), which means
     * a checklist whose only content is a folder has `totalCount == 0` while the detail screen
     * visibly renders that folder. Deriving "Empty" from the leaf count therefore contradicts the
     * screen one tap away: the same class of defect as the detail screen's `visibleFillItemIds`
     * (leaves) disagreeing with the `levelNodes` it actually draws (leaves + folders). A counter must
     * match the set that gets rendered.
     */
    val isEmpty: Boolean,
) {
    /**
     * Share of leaf items already checked, 0f..1f — the only definition of this screen's progress.
     *
     * Derived rather than stored so it can never disagree with the number printed beside it. An empty
     * checklist is 0f, never 1f: it has nothing done, and [isComplete] deliberately says the same.
     * The row must switch to the "done" tick on [isComplete], NOT on `doneFraction == 1f` — the two
     * agree today only because both read these two fields, and re-deriving completeness in the UI is
     * exactly how "congratulations on your empty list" comes back.
     */
    val doneFraction: Float
        get() = if (totalCount > 0) (totalCount - openCount).toFloat() / totalCount else 0f
}

sealed interface ProjectsScreenState : State {

    data object Loading : ProjectsScreenState

    /**
     * The checklist stream failed (repository/DB error) — recoverable via [ProjectsIntent.OnRetry].
     *
     * A state of its own rather than an empty [Content]: an empty list is a CLAIM about the user's
     * data ("you have no checklists yet, create one"), and nothing on this screen can tell that
     * apart from "we could not read them". Carries no payload by design — the exception goes to
     * `AppLogger`, the UI renders a localized message from `strings.xml`, never a raw exception
     * message. Same shape as `MainScreenState.Error` / `TodayScreenState.Error`.
     */
    data object Error : ProjectsScreenState

    /**
     * @param projects never contains the system Inbox — it is the capture bucket, reachable from its
     *   own tab, and listing it here would offer two doors to one place while making it look like an
     *   ordinary project. Enforced upstream by `ChecklistRepository.projects`.
     */
    data class Content(val projects: List<ProjectRow>) : ProjectsScreenState
}

sealed interface ProjectsIntent : Intent {
    /** Opens the checklist detail screen. */
    data class OnProjectClick(val checklistId: Long) : ProjectsIntent

    /** Empty-state CTA and toolbar action — both go to the create flow. */
    data object OnCreateChecklistClick : ProjectsIntent

    /** Re-subscribes the checklist stream after [ProjectsScreenState.Error]. */
    data object OnRetry : ProjectsIntent
}
