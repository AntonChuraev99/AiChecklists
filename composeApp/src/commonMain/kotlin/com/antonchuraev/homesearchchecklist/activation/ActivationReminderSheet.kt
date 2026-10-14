package com.antonchuraev.homesearchchecklist.activation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.activation_sheet_body
import aichecklists.core.designsystem.generated.resources.activation_sheet_caption_permission
import aichecklists.core.designsystem.generated.resources.activation_sheet_cta
import aichecklists.core.designsystem.generated.resources.activation_sheet_title
import aichecklists.core.designsystem.generated.resources.activation_sheet_title_task
import aichecklists.core.designsystem.generated.resources.activation_sheet_today
import aichecklists.core.designsystem.generated.resources.activation_sheet_tomorrow
import aichecklists.core.designsystem.generated.resources.notif_banner_body_settings
import aichecklists.core.designsystem.generated.resources.notif_cta_settings
import aichecklists.core.designsystem.generated.resources.reminder_notification_permission_skip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AdaptiveSheetOrDialog
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/** What the sheet's primary button does — decides CTA + caption copy. */
enum class ActivationSheetMode {
    /** Notifications already reach the user (or API < 33 with them on): CTA just schedules. */
    NotificationsOn,

    /** System dialog can be shown: CTA asks, then schedules on grant. Caption explains the ask. */
    CanAsk,

    /** Permanently denied / app toggle off: CTA opens settings; schedules on return if enabled. */
    SettingsRequired,
}

/** From this font scale on, the decorative tile goes so the checklist name keeps its width. */
private const val TILE_HIDE_FONT_SCALE = 1.5f

/**
 * One-time "remind me about this list" sheet after a new user's first checklist.
 * Stateless: [ActivationReminderHost] owns the selection, the permission flow and scheduling.
 *
 * Layout: title + body + a preview card of the checklist just made (name header, two radio rows)
 * scroll; the CTA, its caption and "Not now" are a pinned footer, so at 2.0 font scale on a short
 * phone the action never scrolls off. The title is drawn here (not via the container's `title`
 * slot) because the bottom-sheet branch of [AdaptiveSheetOrDialog] has no title slot.
 *
 * @param checklistName preview header: the checklist name, or the task text when [isTask].
 * @param options the two reminder times; index 0 is the default.
 * @param selectedIndex currently selected option.
 * @param mode drives CTA / caption copy.
 * @param isRequesting true while the system dialog is open (CTA shows loading, taps ignored).
 * @param onSelect radio row tapped.
 * @param onConfirm primary CTA.
 * @param onSkip "Not now".
 * @param onDismiss scrim / back — treated as skip by the host.
 */
@Composable
fun ActivationReminderSheet(
    checklistName: String,
    /** The target is a task (first Inbox capture): title asks about "this task", not "this list". */
    isTask: Boolean = false,
    options: List<ActivationReminderOption>,
    selectedIndex: Int,
    mode: ActivationSheetMode,
    isRequesting: Boolean,
    onSelect: (Int) -> Unit,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveSheetOrDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingXl),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(if (isTask) Res.string.activation_sheet_title_task else Res.string.activation_sheet_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(AppDimens.SpacingSm))
                Text(
                    text = stringResource(Res.string.activation_sheet_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(AppDimens.SpacingLg))
                ReminderPreviewCard(
                    checklistName = checklistName,
                    options = options,
                    selectedIndex = selectedIndex,
                    onSelect = onSelect,
                )
            }
            Spacer(Modifier.height(AppDimens.SpacingLg))
            AppButton(
                text = stringResource(
                    if (mode == ActivationSheetMode.SettingsRequired) Res.string.notif_cta_settings else Res.string.activation_sheet_cta,
                ),
                onClick = onConfirm,
                loading = isRequesting,
                modifier = Modifier.fillMaxWidth(),
            )
            val caption = when (mode) {
                ActivationSheetMode.NotificationsOn -> null
                ActivationSheetMode.CanAsk -> Res.string.activation_sheet_caption_permission
                ActivationSheetMode.SettingsRequired -> Res.string.notif_banner_body_settings
            }
            if (caption != null) {
                Text(
                    text = stringResource(caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = AppDimens.SpacingSm),
                )
            }
            AppButtonText(
                text = stringResource(Res.string.reminder_notification_permission_skip),
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth().padding(top = AppDimens.SpacingXs),
            )
        }
    }
}

/** The checklist just made, with the two reminder times as a radio group inside it. */
@Composable
private fun ReminderPreviewCard(
    checklistName: String,
    options: List<ActivationReminderOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val showTile = LocalDensity.current.fontScale < TILE_HIDE_FONT_SCALE
    AppCard(contentPadding = PaddingValues(AppDimens.SpacingMd)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
            ) {
                if (showTile) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = checklistName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = AppDimens.SpacingMd),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Column(Modifier.selectableGroup()) {
                options.forEachIndexed { index, option ->
                    val selected = index == selectedIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppDimens.MinTouchTarget)
                            .selectable(
                                selected = selected,
                                onClick = { onSelect(index) },
                                role = Role.RadioButton,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(
                            text = option.label(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivationReminderOption.label(): String {
    val time = formatReminderClock(hour, minute)
    return when (day) {
        ReminderOptionDay.Today -> stringResource(Res.string.activation_sheet_today, time)
        ReminderOptionDay.Tomorrow -> stringResource(Res.string.activation_sheet_tomorrow, time)
    }
}
