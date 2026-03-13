package com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType

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

/**
 * State for the shared [ReminderSheet] composable.
 */
data class ReminderSheetState(
    val activeTab: ReminderTab = ReminderTab.ONCE,
    val currentReminder: Long? = null,
    val currentRepeatRule: ReminderRepeatRule? = null,
    val repeatRuleSummary: String? = null,
    val pendingRepeatConfig: PendingRepeatConfig? = null,
    val showEndConditionPicker: Boolean = false
)

/**
 * Callbacks for the shared [ReminderSheet] composable.
 */
data class ReminderSheetCallbacks(
    // Tab
    val onTabSelected: (ReminderTab) -> Unit,
    // Once tab
    val onPresetSelected: (Long) -> Unit,
    val onCustomDateRequested: () -> Unit,
    val onRemoveReminder: () -> Unit,
    // Repeat tab
    val onRepeatTypeSelected: (RepeatType) -> Unit,
    val onSmartPresetSelected: (PendingRepeatConfig) -> Unit,
    val onRepeatIntervalChanged: (Int) -> Unit,
    val onWeekDayToggled: (Int) -> Unit,
    val onResetChecksToggled: (Boolean) -> Unit,
    val onRepeatTimeChanged: (Int, Int) -> Unit,
    val onEndConditionClick: () -> Unit,
    val onEndConditionSelected: (RepeatEndCondition) -> Unit,
    val onDismissEndCondition: () -> Unit,
    val onSaveRepeat: () -> Unit,
    val onRemoveRepeat: () -> Unit,
    // Sheet
    val onDismiss: () -> Unit
)
