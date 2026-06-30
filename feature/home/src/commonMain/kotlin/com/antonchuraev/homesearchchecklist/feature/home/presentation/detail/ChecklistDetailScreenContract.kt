package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.ParsedDateToken
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.UserLimits

/**
 * Holds a recently deleted item so it can be restored via undo snackbar.
 */
data class UndoableDeleteItem(
    val fillItem: ChecklistFillItem,
    val checklistItemText: String,
    val originalFillIndex: Int,
    val originalChecklistIndex: Int,
)

/**
 * Lightweight UI model for a folder row at the current drill-down level.
 *
 * [id]/[name] mirror the underlying template [com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem].
 * [checked]/[total] are the aggregate of the folder's descendant leaves (from
 * [com.antonchuraev.homesearchchecklist.feature.checklist.domain.tree.ChecklistTree.folderProgress]).
 * Computed in the ViewModel so the Composable stays a pure renderer.
 */
data class FolderUiModel(
    val id: String,
    val name: String,
    val checked: Int,
    val total: Int,
    // True when the folder's linked fill row carries an active reminder (one-shot or recurring).
    // Drives the read-only reminder indicator on [FolderCard] (no hit-zone — the action lives in
    // the folder actions sheet). Computed in the ViewModel from the folder's fill row.
    val hasReminder: Boolean = false,
)

/**
 * One reorderable node at the current drill-down level: a folder OR a leaf item, intermixed in
 * template order. The detail screen renders a SINGLE reorderable list of these so a folder can be
 * dragged to any slot between items (the user-chosen "folders and items mixed" model), instead of
 * folders sitting in a separate non-draggable section above the leaves.
 *
 * [reorderId] is the stable key used both as the LazyColumn / `ReorderableItem` key AND the id
 * passed back in [ChecklistDetailIntent.OnFinalizeReorder]:
 *  - [Folder.reorderId] = the TEMPLATE folder id (folders live on the template; the ViewModel
 *    repositions the template node by this id directly and the folder's placeholder fill row via
 *    its `templateItemId` link).
 *  - [Leaf.reorderId] = the FILL-item id (leaves are repositioned on the fill, template mirrored
 *    via the fill→template link — exactly as before folders existed).
 *
 * Template folder ids and leaf fill ids never collide (distinct UUID spaces), so a mixed list of
 * [reorderId]s resolves unambiguously in the ViewModel.
 */
sealed interface LevelNode {
    val reorderId: String

    data class Folder(val model: FolderUiModel) : LevelNode {
        override val reorderId: String get() = model.id
    }

    data class Leaf(val fillItemId: String) : LevelNode {
        override val reorderId: String get() = fillItemId
    }
}

/**
 * A candidate destination folder for the "Move to…" sheet.
 *
 * The list is a flattened, depth-indented view of the whole folder tree of the checklist
 * (plus a synthetic root row, modelled with [id] == null). [enabled] is precomputed in the
 * ViewModel via
 * [com.antonchuraev.homesearchchecklist.feature.checklist.domain.tree.ChecklistTree.canMove]:
 * the node being moved and all of its descendants are disabled so a move can never create a
 * cycle. [isCurrentParent] marks the node's current parent so the row can read "Current
 * location" and stay non-actionable (moving somewhere it already is would be a no-op).
 *
 * @param id     Target folder id; null = checklist root ("Home").
 * @param name   Display label (folder text, or the root label for the synthetic root row).
 * @param depth  Indentation level (0 = root / top-level folders) for the leading inset.
 * @param enabled Whether this target is a legal destination for the node being moved.
 * @param isCurrentParent True when this target is where the node already lives.
 */
data class MoveTargetUiModel(
    val id: String?,
    val name: String,
    val depth: Int,
    val enabled: Boolean,
    val isCurrentParent: Boolean,
)

/**
 * Which item-create reminder preset chip is currently active. The four reminder chips are
 * single-select among themselves; [CUSTOM] is set when the user resolves a time via the
 * "Pick time…" date/time picker (the chip then shows the absolute datetime).
 */
enum class ItemCreateReminderPreset { ONE_HOUR, TOMORROW_MORNING, TONIGHT, CUSTOM }

sealed interface ChecklistDetailState : State {
    data object Loading : ChecklistDetailState
    data object NotFound : ChecklistDetailState
    data class Content(
        val checklist: Checklist,
        val defaultFill: ChecklistFill?,
        val additionalFillsCount: Int = 0,
        // ── Folders (nested checklists) ──
        // Mirror of checklist.foldersEnabled, lifted onto state so the Composable can branch
        // without reading the domain model directly.
        val foldersEnabled: Boolean = false,
        // Folder node whose children are being shown. null = checklist root.
        val currentFolderId: String? = null,
        // Title of the current folder (its template text). null at root → header shows checklist name.
        val currentFolderTitle: String? = null,
        // Folder rows to render at the current level (empty when !foldersEnabled). Each carries
        // aggregate descendant-leaf progress, precomputed in the ViewModel.
        val folders: List<FolderUiModel> = emptyList(),
        // Fill-item ids that belong to the current folder level (leaf items whose linked template
        // item has parentId == currentFolderId). null = no folder filtering (flat list / !foldersEnabled);
        // the Composable shows all fill items. Non-null = show only these ids at this level.
        val visibleFillItemIds: Set<String>? = null,
        // Ordered mixed list of folders + leaf items at the current level, in template order (empty
        // when !foldersEnabled → the screen uses the flat fill-item list instead). Drives the SINGLE
        // reorderable list so a folder can be dragged between items. Leaves here mirror
        // [visibleFillItemIds]; folders mirror [folders]. Checked leaves are still split off into the
        // completed section by the screen when separateCompleted is on.
        val levelNodes: List<LevelNode> = emptyList(),
        // ── Folder node actions (Phase 4) ──
        // Folder whose actions sheet (Rename / Move to… / Delete) is open. null = closed.
        val folderActionsSheetFor: String? = null,
        // Node (folder OR leaf item) whose "Move to…" target sheet is open. null = closed.
        // A node is identified by its TEMPLATE item id (folders are template-only; for leaves
        // the move re-parents the template item, the fill stays flat).
        val moveSheetForNodeId: String? = null,
        // Flattened, depth-indented destination folders for the open move sheet (empty when closed).
        val moveTargets: List<MoveTargetUiModel> = emptyList(),
        // Folder pending deletion confirmation. null = no confirm dialog showing.
        val pendingFolderDeleteId: String? = null,
        // How many descendant items (leaves + sub-folders) the pending folder delete will cascade.
        // Drives the "This will delete N items inside" confirm copy.
        val pendingFolderDeleteCount: Int = 0,
        // Inline rename draft for [folderActionsSheetFor]. null = rename dialog closed.
        val folderRenameForId: String? = null,
        val folderRenameDraft: String = "",
        val showDeleteConfirmation: Boolean = false,
        val showAddFillDialog: Boolean = false,
        val newFillName: String = "",
        val fillNameError: String? = null,
        val isCreatingFill: Boolean = false,
        val userLimits: UserLimits? = null,
        val showFillLimitDialog: Boolean = false,
        val noteDialogItemId: String? = null,
        val editingNote: String = "",
        val showFillTargetSheet: Boolean = false,
        val showReminderSheet: Boolean = false,
        val showCustomPicker: Boolean = false,
        val customPickerDateMillis: Long? = null,
        val customPickerMinDateMillis: Long = 0L,
        val customPickerInitialHour: Int = 9,
        val isCustomTimeInPast: Boolean = false,
        // Which item the shared custom date/time picker is scoped to.
        // null = checklist-level picker; non-null = per-item picker for that itemId.
        val customPickerItemId: String? = null,
        val showExactAlarmSheet: Boolean = false,
        val exactAlarmDontShowAgain: Boolean = false,
        val showNotificationPermissionSheet: Boolean = false,
        val snackbarMessage: String? = null,
        val showOverflowSheet: Boolean = false,
        val separateCompleted: Boolean = false,
        val autoDeleteCompleted: Boolean = false,
        // Confirm dialog before disabling folders when the checklist still has folder nodes.
        // true → show the "flatten" warning (folders removed, items kept at top level).
        // Disabling a checklist that has NO folders skips this and flips foldersEnabled straight off.
        val showFlattenFoldersConfirm: Boolean = false,
        val pendingUndoItem: UndoableDeleteItem? = null,
        // Weekly mode: non-null while MoveToDayBottomSheet is open
        val moveToDayItemId: String? = null,
        // Reminder sheet tab state
        val activeReminderTab: ReminderTab = ReminderTab.ONCE,
        // Repeat configuration (active while editing on the REPEAT tab)
        val pendingRepeatConfig: PendingRepeatConfig? = null,
        val showEndConditionPicker: Boolean = false,
        val repeatRuleSummary: String? = null,
        // Per-item reminder sheet: null = closed; non-null = open for that itemId
        val itemReminderSheetFor: String? = null,
        val activeItemReminderTab: ReminderTab = ReminderTab.ONCE,
        // Item details sheet: null = closed; non-null = open for that itemId
        val itemDetailsSheetFor: String? = null,
        // Inline text edit inside ItemDetailsSheet: null = view mode (Text title); non-null = edit mode (TextField with focus)
        val editingItemTextFor: String? = null,
        val editingItemTextDraft: String = "",
        // Paywall locked banners: shown instead of normal tab content when free user is at limit
        val reminderSheetLocked: Boolean = false,
        val itemReminderSheetLocked: Boolean = false,
        // Smart Add parser state: text input is owned by ViewModel to allow debounced parsing
        val pendingItemInput: String = "",
        val parsedToken: ParsedDateToken? = null,
        // ── Item-create dock mode (checklist detail "+") ──
        // True while the shared chat dock is in item-create mode: its input creates a checklist item
        // instead of calling the AI chat, and the dock shows the selectable item-create chips.
        val itemCreateMode: Boolean = false,
        // Selected reminder for the NEW item (chip override of any parsed-from-text reminder). null = none.
        val itemCreateReminderAt: Long? = null,
        // Which reminder preset chip is blue (drives single-select + the resolved "Pick time…" label).
        val itemCreateReminderPreset: ItemCreateReminderPreset? = null,
        // Independent property toggles for the new item.
        val itemCreateImportant: Boolean = false,
        val itemCreateRepeat: PendingRepeatConfig? = null,
        // Repeat-config sheet (reuses ReminderSheet on the REPEAT tab) for the item-create Repeat chip.
        val itemCreateRepeatSheetOpen: Boolean = false,
        val itemCreateRepeatSheetLocked: Boolean = false,
        // True while the shared custom date/time picker is scoped to the item-create reminder
        // (as opposed to a per-item or checklist-level reminder).
        val customPickerForItemCreate: Boolean = false,
        // Target weekday (ISO 1=Mon..7=Sun) for the item created via the dock. Non-null in Weekly
        // view (the toolbar "+" targets today) so the new item lands in a day column and stays
        // visible; null in Standard view (unchanged). Applied to both the template + fill item.
        val itemCreateTargetWeekday: Int? = null,
        // One-shot signal (monotonic counter) bumped after each successful add so the screen can
        // scroll the list to the freshly-added item (the removed inline path scrolled directly).
        val addedItemSignal: Int = 0,
        // Attachments: viewer state — null = closed
        val attachmentViewerState: AttachmentViewerState? = null,
        // Pending picker item: which item triggered the file picker (so the callback knows where to attach)
        val pendingAttachmentItemId: String? = null,
        // Open-externally request: non-null while waiting for the Composable to launch the ACTION_VIEW intent
        val pendingOpenExternallyPath: String? = null,
        val pendingOpenExternallyMimeType: String? = null,
        // Image picker / file picker launch triggers (cleared immediately after picker launches)
        val triggerImagePicker: Boolean = false,
        val triggerFilePicker: Boolean = false,
    ) : ChecklistDetailState
}

/**
 * Holds the currently-viewed attachment and its parent item.
 * [initialAttachmentId] is the attachment that should be shown first in the pager.
 */
data class AttachmentViewerState(
    val itemId: String,
    val initialAttachmentId: String,
)

sealed interface ChecklistDetailIntent : Intent {
    data object OnBackClick : ChecklistDetailIntent

    // Checklist actions
    data object OnEditChecklistClick : ChecklistDetailIntent
    data object OnShareClick : ChecklistDetailIntent
    data object OnDeleteChecklistClick : ChecklistDetailIntent
    data object OnConfirmDeleteChecklist : ChecklistDetailIntent
    data object OnDismissDeleteConfirmation : ChecklistDetailIntent

    /**
     * Deletes the checklist directly from the [ChecklistDetailState.NotFound] error screen.
     * Used to recover from a broken/partially-restored checklist that can't be opened (no
     * default fill), so the normal in-screen delete is unreachable. Resolves by [checklistId]
     * (the VM constructor param) rather than [ChecklistDetailState.Content.checklist], which
     * doesn't exist in the NotFound state.
     */
    data object OnDeleteCorruptedChecklist : ChecklistDetailIntent

    // Item actions (for default fill) — id-based for safe partition support
    data class OnItemCheckedChange(val itemId: String, val checked: Boolean) : ChecklistDetailIntent
    data class OnAddNoteClick(val itemId: String) : ChecklistDetailIntent
    /** Fires on every keystroke in the inline add-item field (debounced 200ms in VM). */
    data class OnItemInputChanged(val text: String) : ChecklistDetailIntent
    /**
     * Submits the current [ChecklistDetailState.Content.pendingItemInput].
     * If [ChecklistDetailState.Content.parsedToken] is non-null the reminder fields are
     * applied and the matched substring is stripped from the text. Replaces the former
     * [OnAddItem] in all screen call-sites (Option B — single intent, branching inside VM).
     */
    data object OnAddItemWithParse : ChecklistDetailIntent

    // ── Folders (nested checklists) ──
    /** Drill into folder [folderId] (pushes a new ChecklistDetail entry scoped to it). */
    data class OnOpenFolder(val folderId: String) : ChecklistDetailIntent
    /** Create a new empty folder under the current level (parentId = currentFolderId). */
    data object OnCreateFolder : ChecklistDetailIntent

    // ── Folder node actions (Phase 4): move / rename / delete ──
    /** Long-press a FolderCard → open the folder actions sheet (Rename / Move to… / Delete). */
    data class OnFolderLongPress(val folderId: String) : ChecklistDetailIntent
    data object OnDismissFolderActions : ChecklistDetailIntent

    /**
     * Enter inline-rename mode for [folderId] inside the open folder actions sheet (seeds the
     * draft with its current name). The sheet stays open and its headline swaps to a text field —
     * mirroring the leaf [ItemDetailsSheet] title edit (no separate rename dialog).
     */
    data class OnRenameFolder(val folderId: String) : ChecklistDetailIntent
    data class OnFolderRenameDraftChange(val text: String) : ChecklistDetailIntent
    /**
     * Commit the rename: writes the folder node text (template) + its linked fill row text. The
     * actions sheet stays open showing the new name (leaves inline-edit mode only).
     */
    data object OnConfirmRenameFolder : ChecklistDetailIntent
    /** Leave inline-rename mode without committing (kept for symmetry / programmatic cancel). */
    data object OnDismissRenameFolder : ChecklistDetailIntent

    /**
     * Open the "Move to…" sheet for a node (folder OR leaf item), identified by its TEMPLATE
     * item id. Builds the depth-indented [MoveTargetUiModel] list with legality from
     * [com.antonchuraev.homesearchchecklist.feature.checklist.domain.tree.ChecklistTree.canMove].
     */
    data class OnMoveNodeRequested(val nodeId: String) : ChecklistDetailIntent
    /** Re-parent [nodeId] under [targetFolderId] (null = checklist root). Guarded by canMove. */
    data class OnMoveNodeToFolder(val nodeId: String, val targetFolderId: String?) : ChecklistDetailIntent
    data object OnDismissMoveSheet : ChecklistDetailIntent

    /**
     * Open the reminder sheet for a FOLDER node (from its actions sheet). [folderId] is the
     * TEMPLATE node id; the VM resolves the folder's linked fill-item id and reuses the per-item
     * reminder flow ([OnItemReminderClick] / [OnSaveItemReminder] / [OnRemoveItemReminder]) — a
     * folder's reminder lives on its flat fill row exactly like a leaf's. Lazily creates the fill
     * row for legacy folders that lack one.
     */
    data class OnFolderReminderClick(val folderId: String) : ChecklistDetailIntent

    /** Request folder deletion: opens the confirm dialog (computes cascade count). */
    data class OnDeleteFolder(val folderId: String) : ChecklistDetailIntent
    /** Confirm the cascade delete: removes the folder + all descendants, cancels leaf alarms. */
    data object OnConfirmDeleteFolder : ChecklistDetailIntent
    data object OnDismissDeleteFolder : ChecklistDetailIntent

    data class OnNoteChanged(val note: String) : ChecklistDetailIntent
    data object OnSaveNote : ChecklistDetailIntent
    data object OnDismissNoteDialog : ChecklistDetailIntent

    // View all fills
    data object OnViewAllFillsClick : ChecklistDetailIntent

    // Add new fill
    data object OnAddFillClick : ChecklistDetailIntent
    data object OnAddFillViaAiClick : ChecklistDetailIntent
    data object OnFillTargetSheetDismiss : ChecklistDetailIntent
    data object OnFillMainChecklistSelected : ChecklistDetailIntent
    data object OnCreateNewFillSelected : ChecklistDetailIntent
    data object OnDismissAddFillDialog : ChecklistDetailIntent
    data class OnNewFillNameChanged(val name: String) : ChecklistDetailIntent
    data object OnConfirmAddFill : ChecklistDetailIntent

    // Limits
    data object OnDismissFillLimitDialog : ChecklistDetailIntent
    data object OnUpgradeToPremiumClick : ChecklistDetailIntent

    // Reminders
    data object OnReminderClick : ChecklistDetailIntent
    data class OnReminderPresetSelected(val triggerAtMillis: Long) : ChecklistDetailIntent
    data object OnCustomDateRequested : ChecklistDetailIntent
    data class OnDateSelected(val dateMillis: Long) : ChecklistDetailIntent
    data class OnTimeSelected(val hour: Int, val minute: Int) : ChecklistDetailIntent
    data class OnCustomTimeChanged(val hour: Int, val minute: Int) : ChecklistDetailIntent
    data object OnRemoveReminder : ChecklistDetailIntent
    data object OnDismissReminderUI : ChecklistDetailIntent

    // Reminder sheet tab
    data class OnReminderTabSelected(val tab: ReminderTab) : ChecklistDetailIntent

    // Notification permission
    data class OnNotificationPermissionResult(val granted: Boolean) : ChecklistDetailIntent
    data object OnNotificationPermissionSkip : ChecklistDetailIntent
    data object OnDismissNotificationPermissionSheet : ChecklistDetailIntent

    // Overflow menu
    data object OnOverflowMenuClick : ChecklistDetailIntent
    data object OnDismissOverflowSheet : ChecklistDetailIntent
    data object OnToggleSeparateCompleted : ChecklistDetailIntent
    data object OnToggleAutoDeleteCompleted : ChecklistDetailIntent
    data object OnDeleteCompletedItems : ChecklistDetailIntent

    /**
     * Toggle the per-checklist folders feature on/off.
     *
     * Turning ON simply enables folders (no-op when the checklist is in Weekly view mode —
     * folders and Weekly are mutually exclusive groupings of the same flat list).
     * Turning OFF: if the checklist still has folder nodes, opens the flatten-confirm dialog
     * ([ChecklistDetailState.Content.showFlattenFoldersConfirm]); otherwise disables straight away.
     */
    data object OnToggleFoldersEnabled : ChecklistDetailIntent
    /** Confirm disabling folders: flattens the tree (items kept at root) then sets foldersEnabled=false. */
    data object OnConfirmDisableFolders : ChecklistDetailIntent
    data object OnDismissDisableFolders : ChecklistDetailIntent

    // Item reorder and delete
    data class OnFinalizeReorder(val orderedItemIds: List<String>) : ChecklistDetailIntent
    data class OnSwipeDeleteItem(val itemId: String) : ChecklistDetailIntent
    data object OnUndoDeleteItem : ChecklistDetailIntent

    // Analytics-only intents (UI events tracked via ViewModel for testability)
    data class OnCompletedSectionToggle(val expanded: Boolean, val completedCount: Int) : ChecklistDetailIntent
    data object OnQuickAddOpened : ChecklistDetailIntent
    data class OnQuickAddCancelled(val hadText: Boolean) : ChecklistDetailIntent

    // ── Item-create dock mode (checklist detail "+") ──
    /**
     * Enter item-create mode: the shared chat dock's input now adds checklist items. Resets chips.
     * [targetWeekday] (ISO 1=Mon..7=Sun) pins the new item to a Weekly day column (today for the
     * toolbar "+" in Weekly view); null in Standard view.
     */
    data class OnDockItemCreateOpened(val targetWeekday: Int? = null) : ChecklistDetailIntent
    /** Exit item-create mode (dock collapsed/dismissed): clears the input + all chip selections. */
    data object OnDockItemCreateClosed : ChecklistDetailIntent
    /**
     * Toggle a reminder preset chip for the new item (single-select among reminder chips). Tapping the
     * already-active preset again clears the reminder. [ItemCreateReminderPreset.CUSTOM] is never sent
     * here — it is set by the date/time picker; only ONE_HOUR / TOMORROW_MORNING / TONIGHT come through.
     */
    data class OnItemCreatePresetSelected(val preset: ItemCreateReminderPreset) : ChecklistDetailIntent
    /** "Pick time…" chip: open the shared date/time picker scoped to the item-create reminder. */
    data object OnItemCreateReminderPickRequested : ChecklistDetailIntent
    /** Toggle the "⭐ Important" chip (priority = 1 on the new item). */
    data object OnItemCreateImportantToggled : ChecklistDetailIntent
    /** "🔁 Repeat" chip: open the repeat-config sheet (free-user limit gated). */
    data object OnItemCreateRepeatRequested : ChecklistDetailIntent
    /** Switch the item-create reminder/repeat sheet tab (kept item-create-scoped, no checklist seeding). */
    data class OnItemCreateRepeatTabSelected(val tab: ReminderTab) : ChecklistDetailIntent
    /** Set (or clear, when null) a one-shot reminder for the new item from the sheet's ONCE tab. */
    data class OnItemCreateReminderSet(val at: Long?) : ChecklistDetailIntent
    /** Commit the repeat config from the open sheet into the item-create chip state. */
    data object OnItemCreateRepeatSaved : ChecklistDetailIntent
    /** Clear the configured item-create repeat. */
    data object OnItemCreateRepeatRemoved : ChecklistDetailIntent
    /** Dismiss the item-create repeat-config sheet without changing the configured repeat. */
    data object OnDismissItemCreateRepeatSheet : ChecklistDetailIntent

    // Repeat schedule (independent of reminder)
    data class OnRepeatTypeSelected(val type: RepeatType) : ChecklistDetailIntent
    data class OnSmartPresetSelected(val config: PendingRepeatConfig) : ChecklistDetailIntent
    data class OnRepeatIntervalChanged(val interval: Int) : ChecklistDetailIntent
    data class OnWeekDayToggled(val dayNumber: Int) : ChecklistDetailIntent
    data class OnResetChecksToggled(val enabled: Boolean) : ChecklistDetailIntent
    data class OnRepeatTimeChanged(val hour: Int, val minute: Int) : ChecklistDetailIntent
    data object OnSaveRepeatSchedule : ChecklistDetailIntent
    data object OnRemoveRepeatSchedule : ChecklistDetailIntent

    // End condition
    data object OnEndConditionClick : ChecklistDetailIntent
    data class OnEndConditionSelected(val condition: RepeatEndCondition) : ChecklistDetailIntent
    data object OnDismissEndConditionPicker : ChecklistDetailIntent

    // Exact alarm permission
    data object OnExactAlarmOpenSettings : ChecklistDetailIntent
    data object OnExactAlarmSkip : ChecklistDetailIntent
    data class OnExactAlarmDontShowChanged(val checked: Boolean) : ChecklistDetailIntent
    data object OnDismissExactAlarmSheet : ChecklistDetailIntent
    data object OnReturnedFromSettings : ChecklistDetailIntent
    data object OnSnackbarDismissed : ChecklistDetailIntent

    // Weekly mode intents
    data class OnAddItemToDay(val weekday: Int, val text: String) : ChecklistDetailIntent
    data class OnItemLongPressForMove(val itemId: String) : ChecklistDetailIntent
    data class OnMoveItemToDay(val itemId: String, val targetWeekday: Int) : ChecklistDetailIntent
    data object OnDismissMoveToDaySheet : ChecklistDetailIntent

    // Item details sheet
    data class OnItemTapForDetails(val itemId: String) : ChecklistDetailIntent
    data object OnDismissItemDetailsSheet : ChecklistDetailIntent
    data class OnDeleteItemFromSheet(val itemId: String) : ChecklistDetailIntent

    // Inline text edit inside ItemDetailsSheet
    data class OnStartItemTextEdit(val itemId: String) : ChecklistDetailIntent
    data class OnItemTextDraftChange(val text: String) : ChecklistDetailIntent
    data object OnConfirmItemTextEdit : ChecklistDetailIntent
    data object OnCancelItemTextEdit : ChecklistDetailIntent

    // Reminder paywall upgrade (called from locked banner inside ReminderSheet)
    data object OnReminderUpgradeClick : ChecklistDetailIntent
    data object OnItemReminderUpgradeClick : ChecklistDetailIntent

    // Attachments
    data class OnAttachmentClick(val attachmentId: String) : ChecklistDetailIntent
    data class OnAddImageAttachment(val itemId: String) : ChecklistDetailIntent
    data class OnAddFileAttachment(val itemId: String) : ChecklistDetailIntent
    data class OnDeleteAttachment(val itemId: String, val attachmentId: String) : ChecklistDetailIntent
    data class OnOpenAttachmentExternally(val attachmentId: String) : ChecklistDetailIntent
    data object OnCloseAttachmentViewer : ChecklistDetailIntent
    data object OnImagePickerLaunched : ChecklistDetailIntent
    data object OnFilePickerLaunched : ChecklistDetailIntent
    /**
     * Internal intent: dispatched from the Composable after FilePicker returns a result.
     * [itemId] comes from [ChecklistDetailState.Content.pendingAttachmentItemId].
     */
    data class OnAttachmentPicked(
        val itemId: String,
        val sourcePath: String,
        val fileName: String,
        val mimeType: String?,
    ) : ChecklistDetailIntent
    /** Dispatched when the open-externally ACTION_VIEW intent has been sent by the Composable. */
    data object OnOpenExternallyDispatched : ChecklistDetailIntent

    // Priority
    data class OnToggleItemPriority(val itemId: String) : ChecklistDetailIntent

    // Per-item reminder intents
    data class OnItemReminderClick(val itemId: String) : ChecklistDetailIntent
    data class OnSaveItemReminder(
        val itemId: String,
        val reminderAt: Long?,
        val repeatRule: ReminderRepeatRule?,
        val repeatTimeOfDayMinutes: Int?
    ) : ChecklistDetailIntent
    data class OnRemoveItemReminder(val itemId: String) : ChecklistDetailIntent
    data object OnDismissItemReminderSheet : ChecklistDetailIntent
    data class OnItemReminderTabSelected(val tab: ReminderTab) : ChecklistDetailIntent

    // Calendar export (one-way) — fired from the "Add to Google Calendar" row in the reminder sheet.
    // Synchronous handler (Web popup-safety): the calendar launch must happen inside the click
    // call stack, so the VM reads already-loaded state and calls the launcher without suspending.
    /** Export the checklist-level reminder/repeat to the device calendar. */
    data object OnAddToCalendar : ChecklistDetailIntent
    /** Export a single item's reminder/repeat to the device calendar. */
    data class OnAddItemToCalendar(val itemId: String) : ChecklistDetailIntent
}
