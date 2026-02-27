package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.UserLimits

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
        val showDeleteItemConfirmation: Boolean = false,
        val itemPendingDeleteId: String? = null
    ) : ChecklistDetailState
}

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
    data class OnAddItem(val text: String) : ChecklistDetailIntent
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

    // Notification permission
    data class OnNotificationPermissionResult(val granted: Boolean) : ChecklistDetailIntent
    data object OnNotificationPermissionSkip : ChecklistDetailIntent
    data object OnDismissNotificationPermissionSheet : ChecklistDetailIntent

    // Overflow menu
    data object OnOverflowMenuClick : ChecklistDetailIntent
    data object OnDismissOverflowSheet : ChecklistDetailIntent
    data object OnToggleSeparateCompleted : ChecklistDetailIntent

    // Item reorder and delete
    data class OnFinalizeReorder(val orderedItemIds: List<String>) : ChecklistDetailIntent
    data class OnDeleteItemClick(val itemId: String) : ChecklistDetailIntent
    data object OnConfirmDeleteItem : ChecklistDetailIntent
    data object OnDismissDeleteItemDialog : ChecklistDetailIntent

    // Analytics-only intents (UI events tracked via ViewModel for testability)
    data class OnCompletedSectionToggle(val expanded: Boolean, val completedCount: Int) : ChecklistDetailIntent
    data object OnQuickAddOpened : ChecklistDetailIntent
    data class OnQuickAddCancelled(val hadText: Boolean) : ChecklistDetailIntent

    // Exact alarm permission
    data object OnExactAlarmOpenSettings : ChecklistDetailIntent
    data object OnExactAlarmSkip : ChecklistDetailIntent
    data class OnExactAlarmDontShowChanged(val checked: Boolean) : ChecklistDetailIntent
    data object OnDismissExactAlarmSheet : ChecklistDetailIntent
    data object OnReturnedFromSettings : ChecklistDetailIntent
    data object OnSnackbarDismissed : ChecklistDetailIntent
}
