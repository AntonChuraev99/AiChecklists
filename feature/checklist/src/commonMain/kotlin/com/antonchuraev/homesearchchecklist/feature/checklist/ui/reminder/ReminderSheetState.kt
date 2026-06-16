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
 *
 * When [isLocked] is true the sheet renders a locked-paywall banner instead of the normal
 * tab content. The caller is responsible for setting this flag; the sheet itself only
 * displays UI and calls back through [ReminderSheetCallbacks.onUpgradeClick] /
 * [ReminderSheetCallbacks.onDismiss].
 */
data class ReminderSheetState(
    val activeTab: ReminderTab = ReminderTab.ONCE,
    val currentReminder: Long? = null,
    val currentRepeatRule: ReminderRepeatRule? = null,
    val repeatRuleSummary: String? = null,
    val pendingRepeatConfig: PendingRepeatConfig? = null,
    val showEndConditionPicker: Boolean = false,
    /** When true, hides tab content and shows the premium-upgrade locked banner. */
    val isLocked: Boolean = false,
)

/**
 * Callbacks for the shared [ReminderSheet] composable.
 *
 * [onUpgradeClick] is called when the user taps "Become Pro" inside the locked banner.
 * Defaults to no-op so existing call-sites (e.g. onboarding) don't need to change.
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
    // Export the current reminder/repeat to the device calendar (one-way). Defaults to no-op so
    // existing call-sites (e.g. onboarding) don't need to change. The row is only shown when a
    // reminder/repeat is already configured (there is something to export).
    val onAddToCalendar: () -> Unit = {},
    // Sheet
    val onDismiss: () -> Unit,
    // Locked paywall banner
    val onUpgradeClick: () -> Unit = {},
)
