package com.antonchuraev.homesearchchecklist.feature.home.presentation.create

import androidx.compose.runtime.Composable
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderDateTimePicker
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheet
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheetCallbacks
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheetState
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.resolveRepeatSummaryLabel

/**
 * Mounts the v1 reminder surfaces for a task that does not exist yet.
 *
 * The planner panel's three exits — "Pick date", "Time" and "Repeat" — all land here. It is the SAME
 * [ReminderSheet] the checklist detail screen and the Inbox item sheet open, not a dock-sized
 * re-implementation of it: the owner rule is to lift the v1 surface and extend it, and a reduced twin
 * would re-open the defects the original already closed (the in-past guard, the repeat editor seeding
 * from the stored rule, the locked-tier banner).
 *
 * ## Where this must be mounted
 * At the HOST screen's level — beside `AppScaffold`, never inside [QuickCaptureDock]. The dock lives
 * in `core:designsystem`, which sits UNDER `feature:checklist` in the module graph, so it cannot even
 * name these types. That is also why the panel's exits are callbacks rather than a sheet of its own.
 *
 * Both surfaces are `ModalBottomSheet`-shaped and mutually exclusive by construction: the controller
 * nulls the sheet as it opens the picker, so the two can never fight over the scrim or over
 * predictive back.
 *
 * No `showFullScreenOption`: full-screen delivery is a field on a stored fill row, and there is no
 * row yet. Offering the toggle here would be a control whose value has nowhere to go.
 */
@Composable
fun DraftDueSheetHost(
    draft: TaskDraft,
    due: DraftDueUiState,
    onIntent: (DraftDueIntent) -> Unit,
) {
    due.sheet?.let { sheet ->
        ReminderSheet(
            state = ReminderSheetState(
                activeTab = sheet.tab,
                currentReminder = draft.reminderAt,
                currentRepeatRule = draft.repeat?.toRule(),
                // A visibility flag for the "current repeat" card, not its text — the sheet derives
                // the words from `currentRepeatRule` so they follow the app locale. Resolved
                // localised here anyway, so this can never leak an English literal onto the screen.
                repeatRuleSummary = draft.repeat?.let { resolveRepeatSummaryLabel(it) },
                pendingRepeatConfig = sheet.repeatConfig,
                showEndConditionPicker = sheet.endConditionPickerOpen,
                isLocked = sheet.locked,
            ),
            callbacks = ReminderSheetCallbacks(
                onTabSelected = { onIntent(DraftDueIntent.OnSheetTabSelected(it)) },
                onPresetSelected = { onIntent(DraftDueIntent.OnSheetPresetSelected(it)) },
                onCustomDateRequested = { onIntent(DraftDueIntent.OnSheetCustomDateRequested) },
                onRemoveReminder = { onIntent(DraftDueIntent.OnSheetRemoveReminder) },
                onRepeatTypeSelected = { onIntent(DraftDueIntent.OnRepeatTypeSelected(it)) },
                onSmartPresetSelected = { onIntent(DraftDueIntent.OnSmartPresetSelected(it)) },
                onRepeatIntervalChanged = { onIntent(DraftDueIntent.OnRepeatIntervalChanged(it)) },
                onWeekDayToggled = { onIntent(DraftDueIntent.OnWeekDayToggled(it)) },
                onResetChecksToggled = { onIntent(DraftDueIntent.OnResetChecksToggled(it)) },
                onRepeatTimeChanged = { h, m -> onIntent(DraftDueIntent.OnRepeatTimeChanged(h, m)) },
                onEndConditionClick = { onIntent(DraftDueIntent.OnEndConditionClick) },
                onEndConditionSelected = { onIntent(DraftDueIntent.OnEndConditionSelected(it)) },
                onDismissEndCondition = { onIntent(DraftDueIntent.OnEndConditionDismiss) },
                onSaveRepeat = { onIntent(DraftDueIntent.OnRepeatSave) },
                onRemoveRepeat = { onIntent(DraftDueIntent.OnRepeatRemove) },
                onDismiss = { onIntent(DraftDueIntent.OnSheetDismiss) },
                onUpgradeClick = { onIntent(DraftDueIntent.OnSheetUpgradeClick) },
            ),
        )
    }

    due.picker?.let { picker ->
        ReminderDateTimePicker(
            selectedDateMillis = picker.dateMillis,
            minDateMillis = picker.minDateMillis,
            initialHour = picker.initialHour,
            isTimeInPast = picker.timeInPast,
            onDateSelected = { onIntent(DraftDueIntent.OnPickerDateSelected(it)) },
            onTimeChanged = { h, m -> onIntent(DraftDueIntent.OnPickerTimeChanged(h, m)) },
            onTimeSelected = { h, m -> onIntent(DraftDueIntent.OnPickerTimeSelected(h, m)) },
            onDismiss = { onIntent(DraftDueIntent.OnPickerDismiss) },
        )
    }
}
