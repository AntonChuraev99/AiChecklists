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

sealed interface ChecklistDetailState : State {
    data object Loading : ChecklistDetailState
    data object NotFound : ChecklistDetailState
    data class Content(
        val checklist: Checklist,
        val defaultFill: ChecklistFill?,
        val additionalFillsCount: Int = 0,
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
        val showExactAlarmSheet: Boolean = false,
        val exactAlarmDontShowAgain: Boolean = false,
        val showNotificationPermissionSheet: Boolean = false,
        val snackbarMessage: String? = null,
        val showOverflowSheet: Boolean = false,
        val separateCompleted: Boolean = false,
        val autoDeleteCompleted: Boolean = false,
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

    // Item reorder and delete
    data class OnFinalizeReorder(val orderedItemIds: List<String>) : ChecklistDetailIntent
    data class OnSwipeDeleteItem(val itemId: String) : ChecklistDetailIntent
    data object OnUndoDeleteItem : ChecklistDetailIntent

    // Analytics-only intents (UI events tracked via ViewModel for testability)
    data class OnCompletedSectionToggle(val expanded: Boolean, val completedCount: Int) : ChecklistDetailIntent
    data object OnQuickAddOpened : ChecklistDetailIntent
    data class OnQuickAddCancelled(val hadText: Boolean) : ChecklistDetailIntent

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
}
