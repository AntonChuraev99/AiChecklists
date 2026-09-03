package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import com.antonchuraev.homesearchchecklist.core.common.api.AiEntrySource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.SideEffect
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxDisplayOptions
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxLayout
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxSort
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiItemCreateAction
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.DraftDueIntent
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.DraftDueUiState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.TaskDraft

/**
 * One task row of the v2 Inbox pager.
 *
 * Carries the whole [ChecklistFillItem] instead of the five-field projection it used to be.
 *
 * The projection existed to stop screens mutating the fill item directly and re-opening the
 * template↔fill desync bug this repo keeps hitting — and that rule still holds: every mutation below
 * still goes out as an [InboxIntent] and is applied by the ViewModel through the template+fill pair.
 * What changed is the READ side. The Inbox now shows the detail screen's own `ItemDetailsSheet`
 * (reminder / note / links / attachments / priority / move / delete), and that composable takes a
 * `ChecklistFillItem` because half its rows describe fields the projection threw away. Rebuilding a
 * wider projection would have been a second, drifting copy of the domain model — the thing the
 * projection was meant to prevent, one layer up.
 *
 * The convenience accessors keep every existing read site (`task.text`, `task.checked`,
 * `task.priority`) compiling and reading the same as before.
 */
data class InboxTask(val item: ChecklistFillItem) {
    val fillItemId: String get() = item.id

    /**
     * The stable link the move/delete paths need to find the matching row in the checklist TEMPLATE.
     * Matching by text instead would hit a same-text sibling.
     */
    val templateItemId: String? get() = item.templateItemId
    val text: String get() = item.text
    val checked: Boolean get() = item.checked
    val priority: Int get() = item.priority
}

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

/**
 * Everything the shared reminder/repeat sheet needs while it is open over an Inbox task.
 *
 * Grouped rather than spread across [InboxScreenState.Content] for the same reason the detail screen
 * keeps them together: they are one interaction, they are all meaningless while the sheet is shut,
 * and a null object ("closed") makes the illegal "tab selected but no sheet" state unrepresentable.
 */
data class InboxItemReminderUi(
    val tab: ReminderTab = ReminderTab.ONCE,
    /** Free-tier gate: renders the locked upgrade banner instead of the tab content. */
    val locked: Boolean = false,
    val fullScreen: Boolean = false,
    val pendingRepeatConfig: PendingRepeatConfig? = null,
    val repeatRuleSummary: String? = null,
    val showEndConditionPicker: Boolean = false,
)

/**
 * Non-null while the custom date+time picker is up (opened from the reminder sheet's "Pick date").
 *
 * [minDateMillis] has no default: it is UTC midnight of today, which only the ViewModel can compute,
 * and a wrong floor here makes yesterday selectable — i.e. a reminder that fires the moment it is
 * saved. [dateMillis] stays nullable because the picker legitimately opens with no day chosen yet.
 */
data class InboxCustomPickerUi(
    val minDateMillis: Long,
    val dateMillis: Long? = null,
    val initialHour: Int = 9,
    val timeInPast: Boolean = false,
)

sealed interface InboxScreenState : State {

    data object Loading : InboxScreenState

    /**
     * The pager could not be loaded.
     *
     * ## Why this branch has to exist
     * Until it did, the sealed interface held only [Loading] and [Content], so **every** load
     * failure — the pages stream throwing, or the system Inbox row failing to be created — left
     * `pages` null and the tab on its spinner forever. A snackbar was fired, but a snackbar lasts
     * four seconds and the spinner lasts until the app is killed: the user is left staring at a
     * screen that is, as far as it says, still working. That is the project's "every action gets a
     * visible response" rule broken in the one place it matters most, the first screen of the app.
     *
     * @param message the reason, ALREADY resolved from Compose Resources by the ViewModel — same
     *   contract as [InboxSideEffect.ShowMessage], so a resource key can never leak into the screen
     *   and be rendered as a literal.
     * @param canRetry whether a retry can plausibly help. False would render the reason with no
     *   button rather than a button that re-runs a hopeless call; today every failure this state is
     *   raised for is transient, so it is always true — the parameter exists so a permanent failure
     *   does not have to invent a second state.
     */
    data class Error(
        val message: String,
        val canRetry: Boolean = true,
    ) : InboxScreenState

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
        /**
         * The task being composed in the capture dock — text AND every chip the user toggled.
         *
         * Replaced a bare `quickAddText: String`. A lone string is why a capture made here could only
         * ever be a title: the reminder / Important chips the detail screen has always offered had
         * nowhere to live, so the two v2 tabs shipped a strictly weaker create surface than the screen
         * one tap deeper.
         */
        val draft: TaskDraft = TaskDraft(),
        /**
         * The capture dock's due rail: whether its planner grid is open, and which v1 reminder
         * surface is up over it.
         *
         * Screen state rather than a `remember` inside the dock, even for the plain expand/collapse
         * flag. Two slots of the dock read it — the planner in `aboveInput`, the AI source row it
         * hides in `belowInput` — and a screenshot cannot reach the expanded frame by tapping,
         * because the dock focuses its input on mount and a blinking caret never lets the test clock
         * go idle.
         */
        val due: DraftDueUiState = DraftDueUiState(),
        val sheetForTaskId: String? = null,
        val movePickerOpen: Boolean = false,
        val moveTargets: List<InboxPage> = emptyList(),

        // ── Item sheet sub-surfaces ──────────────────────────────────────────────────────────
        /** Inline rename inside the item sheet (the sheet's headline turns into a text field). */
        val sheetTextEditing: Boolean = false,
        val sheetTextDraft: String = "",
        /** Non-null while the note dialog is up; holds the draft, seeded with the current note. */
        val noteDraft: String? = null,
        /** Non-null while the reminder/repeat sheet is up over the open task. */
        val reminderSheet: InboxItemReminderUi? = null,
        /** Non-null while the custom date+time picker is up. */
        val customPicker: InboxCustomPickerUi? = null,
        val notificationPermissionOpen: Boolean = false,
        /** Attachment id the fullscreen viewer opened on; null = viewer closed. */
        val attachmentViewerFor: String? = null,
        /**
         * One-shot flags the screen turns into a picker launch, cleared straight back through
         * [InboxIntent.OnImagePickerLaunched] / [InboxIntent.OnFilePickerLaunched] so a recomposition
         * cannot re-open the picker. Same contract as the detail screen's.
         */
        val triggerImagePicker: Boolean = false,
        val triggerFilePicker: Boolean = false,
        /**
         * The row the picker was launched FOR. Separate from [sheetForTaskId] because the picker is
         * another app: the result arrives after this process has been backgrounded, and the sheet may
         * be gone by then.
         */
        val pendingAttachmentTaskId: String? = null,
        /**
         * Drives the attachment quota shown in the sheet. Held as the resolved boolean rather than the
         * whole `UserLimits` so the screen never re-derives a limit — see the CLAUDE.md rule about
         * stale local mirrors of RC limits.
         */
        val isPremium: Boolean = false,
        val maxAttachmentsPerItem: Int = FREE_ATTACHMENTS_FALLBACK,

        /** Overflow menu of the CURRENT page's checklist. Never opened on the system Inbox page. */
        val listMenuOpen: Boolean = false,
        /**
         * Non-null while the rename dialog is up; holds the draft text, seeded with the current title.
         *
         * A nullable draft rather than a `renameOpen: Boolean` + `renameDraft: String` pair: two
         * fields can disagree (dialog closed with a stale draft still in state), and the next open
         * would then show the previous checklist's name.
         */
        val renameDraft: String? = null,
        val deleteConfirmationOpen: Boolean = false,
        /**
         * Already APPLIED to [pages] by the ViewModel — the screen renders the list verbatim and
         * reads these only to draw the sheet's current selection and to pick the row layout.
         */
        val displayOptions: InboxDisplayOptions = InboxDisplayOptions(),
        val displayOptionsOpen: Boolean = false,

        /**
         * The clock the list is rendered against — due-chip colours and the date sections both.
         *
         * ONE ticking source for the whole tab rather than `currentTimeMillis()` read per row. Two
         * things depend on that: a row must not be able to disagree with the heading above it about
         * whether its date has passed, and a screenshot test (or a preview) has to be able to pin
         * the clock, which an ambient read makes impossible.
         *
         * The ViewModel advances this once a minute. WHEN the screen acts on a new value is the
         * screen's decision — see `rememberSettledNow` in `InboxScreen`: applying a regroup while
         * the user's finger is on the list would move the row out from under it.
         */
        val nowMillis: Long = currentTimeMillis(),

        /**
         * Whether the "plan your day" invitation is suppressed because it was swiped away within
         * the last 24 hours.
         *
         * Resolved here rather than in the screen because it needs BOTH the persisted timestamp and
         * the ticking clock, and the ViewModel already holds them. The screen adds the other half of
         * the condition — how many undated tasks the page it is drawing has — which the ViewModel
         * cannot know, because it does not know which page is on screen.
         */
        val planNudgeDismissed: Boolean = false,
    ) : InboxScreenState {

        /** Whether one more attachment fits on an item that already has [currentCount] of them. */
        fun canAddAttachment(currentCount: Int): Boolean =
            isPremium || currentCount < maxAttachmentsPerItem
    }

    companion object {
        /**
         * Used only until the first `UserLimits` emission arrives — the same static free-tier default
         * `ChecklistDetailViewModel.FREE_ATTACHMENT_LIMIT_PER_ITEM` falls back to, so the two sheets
         * cannot disagree during that first frame.
         */
        const val FREE_ATTACHMENTS_FALLBACK = 3
    }
}

sealed interface InboxIntent : Intent {
    /** Pager settled on [index] (swipe or tab tap) — retargets quick-add and the move-target list. */
    data class OnPageSelected(val index: Int) : InboxIntent
    data class OnQuickAddTextChanged(val text: String) : InboxIntent

    /** Appends the trimmed quick-add text to the CURRENT page's checklist (template + fill pair). */
    data object OnQuickAddSubmit : InboxIntent

    /**
     * The inline "add task" row at the end of the list was tapped — the entry point that replaced the
     * shell's floating "+" FAB.
     *
     * Raised through the normal intent channel even though the ViewModel cannot open the dock: the
     * dock's open flag is HOST state (the shell hides its own chrome while it is up), so `InboxRoute`
     * forwards this to the host AND to the ViewModel. The ViewModel's job here is the ANALYTICS —
     * `nav_create_fab_tapped` with `source = "inline_row"`. That emit deliberately moved down here
     * from the shell: the "+" FAB it used to hang off is being deleted, and an event that lives on a
     * deleted button is an event that silently stops, which would break the series it belongs to.
     */
    data object OnAddTaskRowClick : InboxIntent

    /**
     * A capture-dock chip was tapped (reminder preset or Important).
     *
     * Carries the design-system action verbatim instead of one intent per chip: the chip row is
     * shared with the detail screen, and translating its enum into a private mirror here is the kind
     * of parallel vocabulary that drifts the moment a seventh chip is added.
     */
    data class OnCreateChipAction(val action: GistiItemCreateAction) : InboxIntent

    /**
     * Anything the capture dock's due rail, its planner grid or the v1 reminder sheet behind them
     * reported.
     *
     * ONE case wrapping a shared vocabulary, rather than twenty intents mirrored here and again on
     * `TodayIntent`: both tabs mount the same dock and must answer "when" identically, and the rules
     * live in exactly one place ([DraftDueController]). Mirroring the cases per screen is how the two
     * capture surfaces would drift — which they already did once, when only one of them pre-selected
     * a reminder.
     */
    data class OnDue(val due: DraftDueIntent) : InboxIntent

    /**
     * One of the AI source pills (Photo / PDF / Web Link / Voice) was tapped.
     *
     * Routed through the ViewModel rather than through a host callback because BOTH halves of the
     * response belong together and neither belongs to the shell: the emit of `ai_entry_tapped` and
     * the navigation into Analyze with that material pre-selected. Splitting them across layers is
     * how the v2 shell ended up with a credits chip that navigated but reported nothing, and an
     * Analyze entry that reported nothing because it did not exist at all.
     *
     * @param source WHICH of this screen's two doors was tapped — the capture dock, the empty
     *   state, or the sparse-inbox row. Passed in by the composable rather than inferred here:
     *   the ViewModel cannot see which of its own surfaces the user was looking at.
     */
    data class OnAiSourceTapped(
        val kind: AnalyzeInputKind,
        val source: AiEntrySource,
    ) : InboxIntent

    data class OnTaskCheckedChanged(val taskId: String, val checked: Boolean) : InboxIntent

    /** Right 70% of a task row — opens the item sheet. Per-item actions never live on the row. */
    data class OnTaskDetailsClick(val taskId: String) : InboxIntent
    data object OnTaskSheetDismiss : InboxIntent

    data object OnMovePickerOpen : InboxIntent
    data object OnMovePickerDismiss : InboxIntent
    data class OnMoveToProject(val targetChecklistId: Long) : InboxIntent

    data object OnToggleImportant : InboxIntent
    data object OnDeleteTask : InboxIntent

    /** Opens the full checklist detail screen for [checklistId] (offered on project pages only). */
    data class OnOpenProject(val checklistId: Long) : InboxIntent

    // ─── Item sheet: inline rename ────────────────────────────────────────────
    /**
     * Tap on the sheet headline. No item id: the sheet can only ever be open over
     * [InboxScreenState.Content.sheetForTaskId], and an id captured at tap time would let a sync that
     * shifted the pager commit the rename onto a different row.
     */
    data object OnTaskTextEditStart : InboxIntent
    data class OnTaskTextDraftChanged(val text: String) : InboxIntent
    data object OnTaskTextEditConfirm : InboxIntent

    // ─── Item sheet: note ─────────────────────────────────────────────────────
    data object OnTaskNoteClick : InboxIntent
    data class OnTaskNoteDraftChanged(val text: String) : InboxIntent
    data object OnTaskNoteSave : InboxIntent
    data object OnTaskNoteDismiss : InboxIntent

    // ─── Item sheet: reminder / repeat ────────────────────────────────────────
    data object OnTaskReminderClick : InboxIntent
    data class OnReminderTabSelected(val tab: ReminderTab) : InboxIntent
    data class OnReminderPresetSelected(val triggerAtMillis: Long) : InboxIntent
    data object OnReminderRemove : InboxIntent
    data object OnReminderSheetDismiss : InboxIntent
    data class OnReminderFullScreenToggled(val enabled: Boolean) : InboxIntent
    data object OnReminderAddToCalendar : InboxIntent
    data object OnReminderUpgradeClick : InboxIntent

    data class OnRepeatTypeSelected(val type: RepeatType) : InboxIntent
    data class OnSmartPresetSelected(val config: PendingRepeatConfig) : InboxIntent
    data class OnRepeatIntervalChanged(val interval: Int) : InboxIntent
    data class OnWeekDayToggled(val dayNumber: Int) : InboxIntent
    data class OnResetChecksToggled(val enabled: Boolean) : InboxIntent
    data class OnRepeatTimeChanged(val hour: Int, val minute: Int) : InboxIntent
    data object OnEndConditionClick : InboxIntent
    data class OnEndConditionSelected(val condition: RepeatEndCondition) : InboxIntent
    data object OnEndConditionDismiss : InboxIntent
    data object OnRepeatSave : InboxIntent
    data object OnRepeatRemove : InboxIntent

    // ─── Item sheet: custom date/time picker ──────────────────────────────────
    data object OnCustomDateRequested : InboxIntent
    data class OnCustomDateSelected(val dateMillis: Long) : InboxIntent
    data class OnCustomTimeChanged(val hour: Int, val minute: Int) : InboxIntent
    data class OnCustomTimeSelected(val hour: Int, val minute: Int) : InboxIntent
    data object OnCustomPickerDismiss : InboxIntent

    // ─── Item sheet: notification permission ──────────────────────────────────
    data class OnNotificationPermissionResult(val granted: Boolean) : InboxIntent
    data object OnNotificationPermissionSkip : InboxIntent

    // ─── Item sheet: attachments ──────────────────────────────────────────────
    data object OnAddImageAttachment : InboxIntent
    data object OnAddFileAttachment : InboxIntent
    data object OnImagePickerLaunched : InboxIntent
    data object OnFilePickerLaunched : InboxIntent

    /**
     * Dispatched by the Composable once the platform picker returns.
     *
     * [taskId] is carried (unlike the other sheet intents) because the picker leaves and re-enters the
     * app: by the time the result lands the sheet may have been dismissed, and the write must still
     * target the row the user picked FOR.
     */
    data class OnAttachmentPicked(
        val taskId: String,
        val sourcePath: String,
        val fileName: String,
        val mimeType: String?,
    ) : InboxIntent

    data class OnAttachmentClick(val attachmentId: String) : InboxIntent
    data class OnAttachmentDelete(val attachmentId: String) : InboxIntent
    data class OnAttachmentOpenExternally(val attachmentId: String) : InboxIntent
    data object OnAttachmentViewerClose : InboxIntent

    // ─── Toolbar ──────────────────────────────────────────────────────────────
    /** Leading toolbar icon: opens the display-options sheet. */
    data object OnDisplayOptionsClick : InboxIntent
    data object OnDisplayOptionsDismiss : InboxIntent

    /**
     * Applied immediately, not on a "Done" tap.
     *
     * Todoist confirms its sheet with a button; here every option is a single persisted value with
     * no interdependencies, so applying on tap lets the user SEE the list re-render under the sheet
     * and undo by tapping the other option. A confirm step would hide that feedback behind a commit.
     */
    data class OnLayoutSelected(val layout: InboxLayout) : InboxIntent
    data class OnSortSelected(val sort: InboxSort) : InboxIntent
    data class OnShowCompletedChanged(val show: Boolean) : InboxIntent

    /**
     * Date grouping switched on or off. Persisted like the other three; the list is regrouped from
     * the stored value coming back, so there is no local copy to fall out of step with.
     */
    data class OnGroupByDateChanged(val group: Boolean) : InboxIntent

    /**
     * The plan-your-day nudge was swiped away. Hides it for 24 hours.
     *
     * A snooze rather than a permanent dismissal: the invitation is only useful while there is
     * something to plan, and "never again" would delete the entry point to the daily review for a
     * user who swiped once out of curiosity.
     */
    data object OnPlanNudgeDismissed : InboxIntent

    /**
     * "Try again" on [InboxScreenState.Error].
     *
     * Re-runs BOTH halves of the load — the Inbox-row guarantee and the pages subscription — since
     * either can be what failed and the state does not distinguish them for the user.
     */
    data object OnRetryLoad : InboxIntent

    data object OnListMenuOpen : InboxIntent
    data object OnListMenuDismiss : InboxIntent

    /**
     * Opens the current page's checklist in the detail screen.
     *
     * Distinct from [OnOpenProject], which carries an id: this one is raised from the toolbar menu,
     * where the only meaningful target is whatever page the pager is settled on. Carrying an id
     * captured when the menu OPENED would let a swipe underneath it navigate to the wrong checklist.
     */
    data object OnOpenCurrentChecklist : InboxIntent

    /** Opens the rename dialog seeded with the current page's title. */
    data object OnRenameChecklistClick : InboxIntent
    data class OnRenameDraftChanged(val text: String) : InboxIntent
    data object OnConfirmRenameChecklist : InboxIntent
    data object OnDismissRenameChecklist : InboxIntent

    data object OnDeleteChecklistClick : InboxIntent
    data object OnConfirmDeleteChecklist : InboxIntent
    data object OnDismissDeleteChecklist : InboxIntent
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

    /**
     * Hand a stored attachment to the platform's own viewer.
     *
     * A side effect rather than a state flag: opening an external app is a one-shot ACTION, and a
     * flag that must be cleared afterwards is how the detail screen ended up with a
     * `pendingOpenExternallyPath` + `OnOpenExternallyDispatched` round trip just to avoid re-opening
     * the file on every recomposition.
     */
    data class OpenAttachmentExternally(val path: String, val mimeType: String?) : InboxSideEffect
}
