package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.UserLimits

enum class ReminderTab { ONCE, REPEAT }

/**
 * Groups all mutable repeat configuration fields into a single object.
 * Null when repeat tab is not active; non-null with defaults when open.
 */
data class PendingRepeatConfig(
    val type: RepeatType = RepeatType.DAILY,
    val interval: Int = 1,
    val weekDays: Set<Int> = emptySet(),
    val endCondition: RepeatEndCondition = RepeatEndCondition.Never,
    val resetChecks: Boolean = false,
    val isCustom: Boolean = false,
    val timeHour: Int = 9,
    val timeMinute: Int = 0
) {
    fun toRule(): ReminderRepeatRule = ReminderRepeatRule(
        type = type,
        interval = interval,
        weekDays = weekDays.takeIf { it.isNotEmpty() && type == RepeatType.WEEKLY },
        endCondition = endCondition,
        resetChecks = resetChecks
    )
}

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
        val showDeleteItemConfirmation: Boolean = false,
        val itemPendingDeleteId: String? = null,
        // Reminder sheet tab state
        val activeReminderTab: ReminderTab = ReminderTab.ONCE,
        // Repeat configuration (active while editing on the REPEAT tab)
        val pendingRepeatConfig: PendingRepeatConfig? = null,
        val showEndConditionPicker: Boolean = false,
        val repeatRuleSummary: String? = null,
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
    data class OnDeleteItemClick(val itemId: String) : ChecklistDetailIntent
    data object OnConfirmDeleteItem : ChecklistDetailIntent
    data object OnDismissDeleteItemDialog : ChecklistDetailIntent

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
}
