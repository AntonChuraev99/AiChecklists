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
 * A flattened projection rather than the `Checklist` model: this row renders a name and a count, and
 * handing screens the full model is how the template↔fill desync bugs keep starting.
 */
data class ProjectRow(
    val checklistId: Long,
    val title: String,
    val openCount: Int,
    /** All items are done AND there is at least one — drives the "done" tick instead of a zero. */
    val isComplete: Boolean,
)

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
