package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.SideEffect
import com.antonchuraev.homesearchchecklist.core.common.api.State

/**
 * One task row of the v2 Inbox pager.
 *
 * A flattened projection of [com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem]
 * rather than the model itself: the Inbox only ever renders text / checkbox / star, and exposing the
 * full fill item would invite screens to mutate it directly and re-open the template↔fill desync bug
 * this project keeps hitting. Every mutation goes back through an [InboxIntent].
 *
 * [templateItemId] is carried even though nothing renders it — it is the stable link the
 * move/delete paths need to find the matching row in the checklist TEMPLATE. Matching by text
 * instead would hit a same-text sibling.
 */
data class InboxTask(
    val fillItemId: String,
    val templateItemId: String?,
    val text: String,
    val checked: Boolean,
    val priority: Int,
)

/**
 * One page of the Inbox pager: either the system Inbox itself or one project.
 *
 * The pager exists so quick-capture and "quick-add straight into a project" are the SAME gesture —
 * swipe to the project, type, send — instead of two separate flows.
 */
data class InboxPage(
    val checklistId: Long,
    val title: String,
    val isInbox: Boolean,
    val tasks: List<InboxTask>,
)

sealed interface InboxScreenState : State {

    data object Loading : InboxScreenState

    /**
     * @param pages never empty, and index 0 is ALWAYS the system Inbox page — the screen's tab row,
     *   quick-add analytics source and move-target list all rely on that invariant, and the "capture
     *   lands in the Inbox" promise breaks the moment a project can occupy the first slot. ENFORCED
     *   by `InboxViewModel.observePages`, which keeps the state at [Loading] while the Inbox row is
     *   absent rather than letting a project slide into slot 0.
     * @param moveTargets projects the currently-open task may be moved into: every project page
     *   except the one it already lives on, and never the Inbox (moving INTO the inbox is not a
     *   triage action).
     */
    data class Content(
        val pages: List<InboxPage>,
        val selectedPage: Int = 0,
        val quickAddText: String = "",
        val sheetForTaskId: String? = null,
        val movePickerOpen: Boolean = false,
        val moveTargets: List<InboxPage> = emptyList(),
    ) : InboxScreenState
}

sealed interface InboxIntent : Intent {
    /** Pager settled on [index] (swipe or tab tap) — retargets quick-add and the move-target list. */
    data class OnPageSelected(val index: Int) : InboxIntent
    data class OnQuickAddTextChanged(val text: String) : InboxIntent

    /** Appends the trimmed quick-add text to the CURRENT page's checklist (template + fill pair). */
    data object OnQuickAddSubmit : InboxIntent

    data class OnTaskCheckedChanged(val taskId: String, val checked: Boolean) : InboxIntent

    /** Right 70% of a task row — opens the triage sheet. Per-item actions never live on the row. */
    data class OnTaskDetailsClick(val taskId: String) : InboxIntent
    data object OnTaskSheetDismiss : InboxIntent

    data object OnMovePickerOpen : InboxIntent
    data object OnMovePickerDismiss : InboxIntent
    data class OnMoveToProject(val targetChecklistId: Long) : InboxIntent

    data object OnToggleImportant : InboxIntent
    data object OnDeleteTask : InboxIntent

    /** Opens the full checklist detail screen for [checklistId] (offered on project pages only). */
    data class OnOpenProject(val checklistId: Long) : InboxIntent
}

/**
 * Single side effect on purpose: every Inbox mutation is either visible in the list (add, check) or
 * needs one line of confirmation (move, delete) — and every failure path needs the same channel, so
 * a failed action can never be a silent no-op.
 *
 * [text] is already resolved from Compose Resources by the ViewModel (`getString`), so the screen
 * shows it verbatim; a resource KEY here would push string resolution into the collector and make
 * it easy to leak a literal.
 */
sealed interface InboxSideEffect : SideEffect {
    data class ShowMessage(val text: String) : InboxSideEffect
}
