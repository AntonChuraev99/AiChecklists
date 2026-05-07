package com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppSwitch
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * Shared reminder bottom sheet used on both ChecklistDetail and Onboarding screens.
 * Shows two tabs: ONCE (one-shot presets + custom date) and REPEAT (recurring schedule config).
 *
 * Remove buttons are automatically hidden when no existing reminder/repeat is set.
 * Permission handling is NOT included — each caller resolves permission before opening.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSheet(
    state: ReminderSheetState,
    callbacks: ReminderSheetCallbacks,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = callbacks.onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        if (state.isLocked) {
            LockedReminderContent(
                onUpgradeClick = callbacks.onUpgradeClick
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // Tab row
                PrimaryTabRow(
                    selectedTabIndex = if (state.activeTab == ReminderTab.ONCE) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = state.activeTab == ReminderTab.ONCE,
                        onClick = { callbacks.onTabSelected(ReminderTab.ONCE) },
                        text = { Text(stringResource(Res.string.reminder_tab_once)) }
                    )
                    Tab(
                        selected = state.activeTab == ReminderTab.REPEAT,
                        onClick = { callbacks.onTabSelected(ReminderTab.REPEAT) },
                        text = { Text(stringResource(Res.string.reminder_tab_repeat)) }
                    )
                }

                // Tab content
                when (state.activeTab) {
                    ReminderTab.ONCE -> OnceTabContent(
                        currentReminder = state.currentReminder,
                        onPresetSelected = callbacks.onPresetSelected,
                        onCustomDateRequested = callbacks.onCustomDateRequested,
                        onRemoveReminder = callbacks.onRemoveReminder
                    )
                    ReminderTab.REPEAT -> RepeatTabContent(
                        config = state.pendingRepeatConfig ?: PendingRepeatConfig(),
                        currentRepeatRule = state.currentRepeatRule,
                        repeatRuleSummary = state.repeatRuleSummary,
                        showEndConditionPicker = state.showEndConditionPicker,
                        onTypeSelected = callbacks.onRepeatTypeSelected,
                        onSmartPresetSelected = callbacks.onSmartPresetSelected,
                        onIntervalChanged = callbacks.onRepeatIntervalChanged,
                        onWeekDayToggled = callbacks.onWeekDayToggled,
                        onResetChecksToggled = callbacks.onResetChecksToggled,
                        onTimeChanged = callbacks.onRepeatTimeChanged,
                        onEndConditionClick = callbacks.onEndConditionClick,
                        onEndConditionSelected = callbacks.onEndConditionSelected,
                        onDismissEndCondition = callbacks.onDismissEndCondition,
                        onSave = callbacks.onSaveRepeat,
                        onRemove = callbacks.onRemoveRepeat
                    )
                }
            }
        }
    }
}

@Composable
private fun LockedReminderContent(
    onUpgradeClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
            .padding(top = AppDimens.SpacingXl, bottom = AppDimens.SpacingXxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(AppDimens.SpacingLg))
        Text(
            text = stringResource(Res.string.reminder_paywall_locked_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
        Text(
            text = stringResource(Res.string.reminder_paywall_locked_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(AppDimens.SpacingLg))
        AppButton(
            text = stringResource(Res.string.reminder_paywall_locked_cta),
            onClick = onUpgradeClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun OnceTabContent(
    currentReminder: Long?,
    onPresetSelected: (Long) -> Unit,
    onCustomDateRequested: () -> Unit,
    onRemoveReminder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
            .padding(top = AppDimens.SpacingLg, bottom = AppDimens.SpacingXxl),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs)
    ) {
        if (currentReminder != null) {
            CurrentReminderCard(reminderAtMillis = currentReminder)
            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
        }

        ReminderPresetRow(
            icon = Icons.Outlined.Schedule,
            text = stringResource(Res.string.reminder_in_one_hour),
            onClick = {
                onPresetSelected(currentTimeMillis() + 3_600_000L)
            }
        )
        ReminderPresetRow(
            icon = Icons.Outlined.WbSunny,
            text = stringResource(Res.string.reminder_tomorrow_morning),
            onClick = { onPresetSelected(tomorrowAt(hour = 9, minute = 0)) }
        )
        ReminderPresetRow(
            icon = Icons.Outlined.WbTwilight,
            text = stringResource(Res.string.reminder_tomorrow_evening),
            onClick = { onPresetSelected(tomorrowAt(hour = 18, minute = 0)) }
        )
        ReminderPresetRow(
            icon = Icons.Outlined.CalendarMonth,
            text = stringResource(Res.string.reminder_pick_date_time),
            onClick = onCustomDateRequested
        )

        if (currentReminder != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = AppDimens.SpacingSm))
            AppButtonText(
                text = stringResource(Res.string.reminder_remove),
                onClick = onRemoveReminder
            )
        }
    }
}

@Composable
private fun RepeatTabContent(
    config: PendingRepeatConfig,
    currentRepeatRule: ReminderRepeatRule?,
    repeatRuleSummary: String?,
    showEndConditionPicker: Boolean,
    onTypeSelected: (RepeatType) -> Unit,
    onSmartPresetSelected: (PendingRepeatConfig) -> Unit,
    onIntervalChanged: (Int) -> Unit,
    onWeekDayToggled: (Int) -> Unit,
    onResetChecksToggled: (Boolean) -> Unit,
    onTimeChanged: (Int, Int) -> Unit,
    onEndConditionClick: () -> Unit,
    onEndConditionSelected: (RepeatEndCondition) -> Unit,
    onDismissEndCondition: () -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
            .padding(top = AppDimens.SpacingLg, bottom = AppDimens.SpacingXxl),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
    ) {
        // Current repeat schedule card
        if (currentRepeatRule != null && repeatRuleSummary != null) {
            CurrentRepeatCard(
                summary = repeatRuleSummary,
                timeHour = config.timeHour,
                timeMinute = config.timeMinute
            )
            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
        }

        // Custom state for toggling custom repeat section
        var customExpanded by rememberSaveable { mutableStateOf(config.isCustom) }
        val isCustomActive = config.isCustom || customExpanded

        // Preset type options
        RepeatTypeOption(
            text = stringResource(Res.string.reminder_repeat_daily),
            selected = !isCustomActive && config.type == RepeatType.DAILY && config.interval == 1,
            onClick = { customExpanded = false; onTypeSelected(RepeatType.DAILY) }
        )
        RepeatTypeOption(
            text = stringResource(Res.string.reminder_repeat_weekdays),
            selected = !isCustomActive && config.type == RepeatType.WEEKLY && config.interval == 1
                    && config.weekDays == setOf(1, 2, 3, 4, 5),
            onClick = {
                customExpanded = false
                onSmartPresetSelected(PendingRepeatConfig(
                    type = RepeatType.WEEKLY, interval = 1,
                    weekDays = setOf(1, 2, 3, 4, 5),
                    timeHour = config.timeHour, timeMinute = config.timeMinute
                ))
            }
        )
        RepeatTypeOption(
            text = stringResource(Res.string.reminder_repeat_weekly),
            selected = !isCustomActive && config.type == RepeatType.WEEKLY && config.interval == 1 && config.weekDays.isEmpty(),
            onClick = { customExpanded = false; onTypeSelected(RepeatType.WEEKLY) }
        )
        RepeatTypeOption(
            text = stringResource(Res.string.reminder_repeat_biweekly),
            selected = !isCustomActive && config.type == RepeatType.WEEKLY && config.interval == 2 && config.weekDays.isEmpty(),
            onClick = {
                customExpanded = false
                onSmartPresetSelected(PendingRepeatConfig(
                    type = RepeatType.WEEKLY, interval = 2,
                    timeHour = config.timeHour, timeMinute = config.timeMinute
                ))
            }
        )
        RepeatTypeOption(
            text = stringResource(Res.string.reminder_repeat_monthly),
            selected = !isCustomActive && config.type == RepeatType.MONTHLY && config.interval == 1,
            onClick = { customExpanded = false; onTypeSelected(RepeatType.MONTHLY) }
        )
        RepeatTypeOption(
            text = stringResource(Res.string.reminder_repeat_quarterly),
            selected = !isCustomActive && config.type == RepeatType.MONTHLY && config.interval == 3,
            onClick = {
                customExpanded = false
                onSmartPresetSelected(PendingRepeatConfig(
                    type = RepeatType.MONTHLY, interval = 3,
                    timeHour = config.timeHour, timeMinute = config.timeMinute
                ))
            }
        )
        RepeatTypeOption(
            text = stringResource(Res.string.reminder_repeat_yearly),
            selected = !isCustomActive && config.type == RepeatType.YEARLY && config.interval == 1,
            onClick = { customExpanded = false; onTypeSelected(RepeatType.YEARLY) }
        )

        // Custom option
        RepeatTypeOption(
            text = stringResource(Res.string.reminder_repeat_custom),
            selected = isCustomActive,
            onClick = { customExpanded = !customExpanded }
        )

        // Custom interval section
        AnimatedVisibility(visible = isCustomActive) {
            CustomRepeatSection(
                config = config,
                onTypeChanged = onTypeSelected,
                onIntervalChanged = onIntervalChanged,
                onWeekDayToggled = onWeekDayToggled
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = AppDimens.SpacingSm))

        // Time of day picker
        RepeatTimePicker(
            hour = config.timeHour,
            minute = config.timeMinute,
            onTimeChanged = onTimeChanged
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = AppDimens.SpacingSm))

        // Reset checkboxes toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onResetChecksToggled(!config.resetChecks) }
                .padding(vertical = AppDimens.SpacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.reminder_reset_checks),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(Res.string.reminder_reset_checks_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppSwitch(
                checked = config.resetChecks,
                onCheckedChange = null
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = AppDimens.SpacingSm))

        // End condition row
        val endConditionText = when (config.endCondition) {
            is RepeatEndCondition.Never -> stringResource(Res.string.reminder_ends_never)
            is RepeatEndCondition.UntilDate -> {
                val dt = Instant.fromEpochMilliseconds(config.endCondition.dateMillis)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                stringResource(Res.string.reminder_ends_on_date) + " ${formatReminderDateTime(dt)}"
            }
            is RepeatEndCondition.AfterCount -> "${config.endCondition.maxCount} ${stringResource(Res.string.reminder_ends_occurrences)}"
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onEndConditionClick)
                .padding(vertical = AppDimens.SpacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.reminder_ends),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = endConditionText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

        // Save button
        AppButton(
            text = stringResource(Res.string.reminder_repeat_save),
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        )

        // Remove repeat button (only if an active repeat schedule exists)
        if (currentRepeatRule != null) {
            AppButtonText(
                text = stringResource(Res.string.reminder_remove_repeat),
                onClick = onRemove,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // End condition picker dialog
    if (showEndConditionPicker) {
        EndConditionDialog(
            currentCondition = config.endCondition,
            onConditionSelected = onEndConditionSelected,
            onDismiss = onDismissEndCondition
        )
    }
}

// ─── Helper composables ──────────────────────────────────────────

@Composable
private fun ReminderPresetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CurrentReminderCard(reminderAtMillis: Long) {
    val tz = TimeZone.currentSystemDefault()
    val reminderDateTime = Instant.fromEpochMilliseconds(reminderAtMillis)
        .toLocalDateTime(tz)
    val formattedDate = formatReminderDateTime(reminderDateTime)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.SpacingLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CurrentRepeatCard(summary: String, timeHour: Int, timeMinute: Int) {
    val timeFormatted = "${timeHour.toString().padStart(2, '0')}:${timeMinute.toString().padStart(2, '0')}"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.SpacingLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {
            Icon(
                imageVector = Icons.Outlined.Repeat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(Res.string.reminder_repeat_active, summary, timeFormatted),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RepeatTypeOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = AppDimens.SpacingSm, horizontal = AppDimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun CustomRepeatSection(
    config: PendingRepeatConfig,
    onTypeChanged: (RepeatType) -> Unit,
    onIntervalChanged: (Int) -> Unit,
    onWeekDayToggled: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.SpacingLg),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {
            // "Every [N]" row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
            ) {
                Text(
                    text = stringResource(Res.string.reminder_repeat_every),
                    style = MaterialTheme.typography.bodyLarge
                )

                // Interval input
                var intervalText by rememberSaveable(config.interval) {
                    mutableStateOf(config.interval.toString())
                }
                AppTextField(
                    value = intervalText,
                    onValueChange = { text ->
                        intervalText = text.filter { it.isDigit() }.take(2)
                        intervalText.toIntOrNull()?.let { onIntervalChanged(it) }
                    },
                    modifier = Modifier.width(56.dp),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    )
                )
            }

            // Type selector chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
            ) {
                val types = listOf(
                    RepeatType.DAILY to stringResource(Res.string.reminder_repeat_days),
                    RepeatType.WEEKLY to stringResource(Res.string.reminder_repeat_weeks),
                    RepeatType.MONTHLY to stringResource(Res.string.reminder_repeat_months),
                    RepeatType.YEARLY to stringResource(Res.string.reminder_repeat_years)
                )
                types.forEach { (type, label) ->
                    val selected = config.type == type
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onTypeChanged(type) },
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(
                                horizontal = AppDimens.SpacingMd,
                                vertical = AppDimens.SpacingXs
                            )
                        )
                    }
                }
            }

            // Weekday chips (only for WEEKLY type)
            if (config.type == RepeatType.WEEKLY) {
                Text(
                    text = stringResource(Res.string.reminder_repeat_on),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val dayLabels = listOf(
                    1 to stringResource(Res.string.reminder_weekday_mon),
                    2 to stringResource(Res.string.reminder_weekday_tue),
                    3 to stringResource(Res.string.reminder_weekday_wed),
                    4 to stringResource(Res.string.reminder_weekday_thu),
                    5 to stringResource(Res.string.reminder_weekday_fri),
                    6 to stringResource(Res.string.reminder_weekday_sat),
                    7 to stringResource(Res.string.reminder_weekday_sun)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    dayLabels.forEach { (dayNumber, label) ->
                        val isSelected = dayNumber in config.weekDays
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { onWeekDayToggled(dayNumber) },
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatTimePicker(
    hour: Int,
    minute: Int,
    onTimeChanged: (Int, Int) -> Unit
) {
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { showTimePicker = true }
            .padding(vertical = AppDimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = stringResource(Res.string.reminder_repeat_time_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute)

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(Res.string.reminder_select_time)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                AppButtonText(
                    text = stringResource(Res.string.ok),
                    onClick = {
                        onTimeChanged(timeState.hour, timeState.minute)
                        showTimePicker = false
                    }
                )
            },
            dismissButton = {
                AppButtonText(
                    text = stringResource(Res.string.cancel),
                    onClick = { showTimePicker = false }
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large
        )
    }
}

@Composable
private fun EndConditionDialog(
    currentCondition: RepeatEndCondition,
    onConditionSelected: (RepeatEndCondition) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by rememberSaveable {
        mutableStateOf(
            when (currentCondition) {
                is RepeatEndCondition.Never -> "never"
                is RepeatEndCondition.UntilDate -> "date"
                is RepeatEndCondition.AfterCount -> "count"
            }
        )
    }
    var countText by rememberSaveable {
        mutableStateOf(
            (currentCondition as? RepeatEndCondition.AfterCount)?.maxCount?.toString() ?: "10"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.reminder_ends)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
                // Never
                RepeatTypeOption(
                    text = stringResource(Res.string.reminder_ends_never),
                    selected = selectedType == "never",
                    onClick = { selectedType = "never" }
                )
                // After N occurrences
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RepeatTypeOption(
                        text = stringResource(Res.string.reminder_ends_after_count),
                        selected = selectedType == "count",
                        onClick = { selectedType = "count" }
                    )
                }
                if (selectedType == "count") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                        modifier = Modifier.padding(start = AppDimens.SpacingXxl)
                    ) {
                        AppTextField(
                            value = countText,
                            onValueChange = { text ->
                                countText = text.filter { it.isDigit() }.take(3)
                            },
                            modifier = Modifier.width(64.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                        )
                        Text(
                            text = stringResource(Res.string.reminder_ends_occurrences),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppButtonText(
                text = stringResource(Res.string.reminder_ends_done),
                onClick = {
                    val condition = when (selectedType) {
                        "count" -> RepeatEndCondition.AfterCount(countText.toIntOrNull()?.coerceIn(1, 999) ?: 10)
                        else -> RepeatEndCondition.Never
                    }
                    onConditionSelected(condition)
                }
            )
        },
        dismissButton = {
            AppButtonText(text = stringResource(Res.string.cancel), onClick = onDismiss)
        }
    )
}

// ─── Utility functions ──────────────────────────────────────────

fun formatReminderDateTime(dateTime: LocalDateTime): String {
    val month = dateTime.month.name.take(3).lowercase()
        .replaceFirstChar { it.uppercase() }
    val day = dateTime.dayOfMonth
    val hour = dateTime.hour.toString().padStart(2, '0')
    val minute = dateTime.minute.toString().padStart(2, '0')
    return "$month $day, $hour:$minute"
}

internal fun tomorrowAt(hour: Int, minute: Int): Long {
    val tz = TimeZone.currentSystemDefault()
    val now = Instant.fromEpochMilliseconds(currentTimeMillis())
    val tomorrowDate = now.plus(1, DateTimeUnit.DAY, tz).toLocalDateTime(tz).date
    val targetDateTime = LocalDateTime(tomorrowDate, LocalTime(hour, minute))
    return targetDateTime.toInstant(tz).toEpochMilliseconds()
}

fun combinePickerResults(datePickerMillis: Long, hour: Int, minute: Int): Long {
    val utcMidnight = Instant.fromEpochMilliseconds(datePickerMillis)
    val localDate = utcMidnight.toLocalDateTime(TimeZone.UTC).date
    val localDateTime = LocalDateTime(localDate, LocalTime(hour, minute))
    return localDateTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}

// ─── Date/Time Picker ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDateTimePicker(
    selectedDateMillis: Long?,
    minDateMillis: Long,
    initialHour: Int,
    isTimeInPast: Boolean,
    onDateSelected: (Long) -> Unit,
    onTimeChanged: (Int, Int) -> Unit,
    onTimeSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (selectedDateMillis == null) {
        val currentYear = Instant.fromEpochMilliseconds(currentTimeMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).year
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = minDateMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis >= minDateMillis
                }

                override fun isSelectableYear(year: Int): Boolean {
                    return year >= currentYear
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                AppButtonText(
                    text = stringResource(Res.string.reminder_next),
                    onClick = { dateState.selectedDateMillis?.let(onDateSelected) }
                )
            },
            dismissButton = {
                AppButtonText(text = stringResource(Res.string.cancel), onClick = onDismiss)
            }
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val timeState = rememberTimePickerState(initialHour = initialHour, initialMinute = 0)

        LaunchedEffect(timeState.hour, timeState.minute) {
            onTimeChanged(timeState.hour, timeState.minute)
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.reminder_select_time)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                AppButtonText(
                    text = stringResource(Res.string.ok),
                    onClick = { onTimeSelected(timeState.hour, timeState.minute) },
                    enabled = !isTimeInPast
                )
            },
            dismissButton = {
                AppButtonText(text = stringResource(Res.string.cancel), onClick = onDismiss)
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large
        )
    }
}
