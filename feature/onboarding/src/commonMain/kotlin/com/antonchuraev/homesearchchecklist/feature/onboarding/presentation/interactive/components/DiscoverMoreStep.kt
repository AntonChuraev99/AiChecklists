package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCardDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderDateTimePicker
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheet
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheetCallbacks
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheetState
import com.antonchuraev.homesearchchecklist.feature.onboarding.isWidgetSupported
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.DiscoverMoreState
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.InteractiveOnboardingIntent
import com.antonchuraev.homesearchchecklist.feature.onboarding.rememberNotificationPermissionRequester
import com.antonchuraev.homesearchchecklist.feature.sharing.presentation.share.ShareLauncher
import org.jetbrains.compose.resources.stringResource

private val Blue50 = Color(0xFFE3F2FD)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverMoreStep(
    state: DiscoverMoreState,
    onIntent: (InteractiveOnboardingIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    var showWidgetSheet by remember { mutableStateOf(false) }
    var triggerShare by remember { mutableStateOf(false) }

    // Notification permission requester — opens reminder sheet on grant
    val requestNotificationPermission = rememberNotificationPermissionRequester { granted ->
        if (granted) {
            onIntent(InteractiveOnboardingIntent.OnReminderClick)
        }
    }

    // Share launcher — composable, fires when triggerShare is true
    if (triggerShare && state.shareText != null) {
        ShareLauncher(
            textContent = state.shareText,
            pdfFilePath = null,
            onShareComplete = {
                triggerShare = false
                onIntent(InteractiveOnboardingIntent.OnShareCompleted)
            }
        )
    }

    val completedCount = listOf(
        state.reminderCompleted,
        if (isWidgetSupported()) state.widgetCompleted else false,
        state.shareCompleted
    ).count { it }
    val totalCount = if (isWidgetSupported()) 3 else 2

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(AppDimens.SpacingXxl))

        Text(
            text = stringResource(Res.string.onboarding_discover_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

        Text(
            text = stringResource(Res.string.onboarding_discover_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (completedCount > 0) {
            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
            Text(
                text = stringResource(
                    Res.string.onboarding_discover_completed,
                    completedCount,
                    totalCount
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

        // Reminder card
        ActionCard(
            icon = Icons.Outlined.Notifications,
            title = stringResource(Res.string.onboarding_discover_reminder_title),
            subtitle = stringResource(Res.string.onboarding_discover_reminder_subtitle),
            isCompleted = state.reminderCompleted,
            onClick = {
                if (!state.reminderCompleted) {
                    requestNotificationPermission()
                }
            }
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

        // Widget card (Android only)
        if (isWidgetSupported()) {
            ActionCard(
                icon = Icons.Outlined.Widgets,
                title = stringResource(Res.string.onboarding_discover_widget_title),
                subtitle = stringResource(Res.string.onboarding_discover_widget_subtitle),
                isCompleted = state.widgetCompleted,
                onClick = {
                    if (!state.widgetCompleted) {
                        showWidgetSheet = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
        }

        // Share card
        ActionCard(
            icon = Icons.Outlined.Share,
            title = stringResource(Res.string.onboarding_discover_share_title),
            subtitle = stringResource(Res.string.onboarding_discover_share_subtitle),
            isCompleted = state.shareCompleted,
            onClick = {
                if (!state.shareCompleted) {
                    triggerShare = true
                }
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        AppButton(
            text = stringResource(Res.string.onboarding_continue),
            onClick = { onIntent(InteractiveOnboardingIntent.OnDiscoverMoreContinue) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingLg))
    }

    // Shared Reminder bottom sheet
    if (state.showReminderSheet) {
        ReminderSheet(
            state = ReminderSheetState(
                activeTab = state.activeReminderTab,
                currentReminder = state.currentReminder,
                currentRepeatRule = state.currentRepeatRule,
                repeatRuleSummary = state.repeatRuleSummary,
                pendingRepeatConfig = state.pendingRepeatConfig,
                showEndConditionPicker = state.showEndConditionPicker
            ),
            callbacks = ReminderSheetCallbacks(
                onTabSelected = { onIntent(InteractiveOnboardingIntent.OnReminderTabSelected(it)) },
                onPresetSelected = { onIntent(InteractiveOnboardingIntent.OnReminderPresetSelected(it)) },
                onCustomDateRequested = { onIntent(InteractiveOnboardingIntent.OnCustomDateRequested) },
                onRemoveReminder = { onIntent(InteractiveOnboardingIntent.OnRemoveReminder) },
                onRepeatTypeSelected = { onIntent(InteractiveOnboardingIntent.OnRepeatTypeSelected(it)) },
                onSmartPresetSelected = { onIntent(InteractiveOnboardingIntent.OnSmartPresetSelected(it)) },
                onRepeatIntervalChanged = { onIntent(InteractiveOnboardingIntent.OnRepeatIntervalChanged(it)) },
                onWeekDayToggled = { onIntent(InteractiveOnboardingIntent.OnWeekDayToggled(it)) },
                onResetChecksToggled = { onIntent(InteractiveOnboardingIntent.OnResetChecksToggled(it)) },
                onRepeatTimeChanged = { h, m -> onIntent(InteractiveOnboardingIntent.OnRepeatTimeChanged(h, m)) },
                onEndConditionClick = { onIntent(InteractiveOnboardingIntent.OnEndConditionClick) },
                onEndConditionSelected = { onIntent(InteractiveOnboardingIntent.OnEndConditionSelected(it)) },
                onDismissEndCondition = { onIntent(InteractiveOnboardingIntent.OnDismissEndConditionPicker) },
                onSaveRepeat = { onIntent(InteractiveOnboardingIntent.OnSaveRepeatSchedule) },
                onRemoveRepeat = { onIntent(InteractiveOnboardingIntent.OnRemoveRepeatSchedule) },
                onDismiss = { onIntent(InteractiveOnboardingIntent.OnDismissReminderUI) }
            )
        )
    }

    // Custom date/time picker
    if (state.showCustomPicker) {
        ReminderDateTimePicker(
            selectedDateMillis = state.customPickerDateMillis,
            minDateMillis = state.customPickerMinDateMillis,
            initialHour = state.customPickerInitialHour,
            isTimeInPast = state.isCustomTimeInPast,
            onDateSelected = { onIntent(InteractiveOnboardingIntent.OnDateSelected(it)) },
            onTimeChanged = { h, m -> onIntent(InteractiveOnboardingIntent.OnCustomTimeChanged(h, m)) },
            onTimeSelected = { h, m -> onIntent(InteractiveOnboardingIntent.OnTimeSelected(h, m)) },
            onDismiss = { onIntent(InteractiveOnboardingIntent.OnDismissReminderUI) }
        )
    }

    // Widget instruction bottom sheet
    if (showWidgetSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWidgetSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            WidgetInstructionSheetContent(
                onDone = {
                    showWidgetSheet = false
                    onIntent(InteractiveOnboardingIntent.OnWidgetInstructionDone)
                }
            )
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isCompleted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isCompleted) 1f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "action_card_scale"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isCompleted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "icon_tint"
    )

    val cardShape = RoundedCornerShape(AppDimens.SpacingMd)
    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.SpacingLg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon in Blue50 container
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Blue50, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(AppDimens.SpacingMd))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(AppDimens.SpacingSm))

            if (isCompleted) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = stringResource(Res.string.completed_label),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(cardShape)
            .clickable(enabled = !isCompleted, onClick = onClick),
        shape = cardShape,
        colors = AppCardDefaults.colors(),
        border = AppCardDefaults.border(),
        elevation = AppCardDefaults.flatElevation()
    ) { cardContent() }
}

@Composable
private fun WidgetInstructionSheetContent(
    onDone: () -> Unit
) {
    com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation.components.WidgetInstructionOverlay(
        onDone = onDone
    )
}
