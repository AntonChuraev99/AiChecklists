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
        val noteDialogItemIndex: Int? = null,
        val editingNote: String = "",
        val showFillTargetSheet: Boolean = false,
        val showReminderSheet: Boolean = false,
        val showCustomPicker: Boolean = false,
        val customPickerDateMillis: Long? = null,
        val showExactAlarmSheet: Boolean = false,
        val exactAlarmDontShowAgain: Boolean = false,
        val snackbarMessage: String? = null
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

    // Item actions (for default fill)
    data class OnItemCheckedChange(val index: Int, val checked: Boolean) : ChecklistDetailIntent
    data class OnAddNoteClick(val index: Int) : ChecklistDetailIntent
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
    data object OnRemoveReminder : ChecklistDetailIntent
    data object OnDismissReminderUI : ChecklistDetailIntent

    // Exact alarm permission
    data object OnExactAlarmOpenSettings : ChecklistDetailIntent
    data object OnExactAlarmSkip : ChecklistDetailIntent
    data class OnExactAlarmDontShowChanged(val checked: Boolean) : ChecklistDetailIntent
    data object OnDismissExactAlarmSheet : ChecklistDetailIntent
    data object OnReturnedFromSettings : ChecklistDetailIntent
    data object OnSnackbarDismissed : ChecklistDetailIntent
}
