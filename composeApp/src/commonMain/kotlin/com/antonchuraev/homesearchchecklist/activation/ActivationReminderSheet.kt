package com.antonchuraev.homesearchchecklist.activation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.reminder_notification_permission_description
import aichecklists.core.designsystem.generated.resources.reminder_notification_permission_enable
import aichecklists.core.designsystem.generated.resources.reminder_notification_permission_feature1
import aichecklists.core.designsystem.generated.resources.reminder_notification_permission_feature2
import aichecklists.core.designsystem.generated.resources.reminder_notification_permission_feature3
import aichecklists.core.designsystem.generated.resources.reminder_notification_permission_skip
import aichecklists.core.designsystem.generated.resources.reminder_notification_permission_title
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AdaptiveSheetOrDialog
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.rememberNotificationPermissionRequester
import org.jetbrains.compose.resources.stringResource

/**
 * One-time reminder opt-in soft-ask shown right after a new user's FIRST AI checklist (new-user
 * activation bundle, RC flag `activation_bundle_v1`). Reuses the same copy + look as the checklist-
 * detail [com.antonchuraev.homesearchchecklist.feature.home.presentation.detail] notification sheet,
 * and the shared `rememberNotificationPermissionRequester` expect/actual so "Enable" triggers the
 * real Android POST_NOTIFICATIONS prompt (no-op grant on web/iOS).
 *
 * @param onEnableGranted Called only after the user tapped Enable AND the OS granted notifications.
 *        The host schedules the default reminder for the just-created checklist.
 * @param onSkip Called when the user taps "Not Now".
 * @param onDismiss Called on scrim/back dismissal (treated as skip by the host).
 */
@Composable
fun ActivationReminderSheet(
    onEnableGranted: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val requestNotificationPermission = rememberNotificationPermissionRequester { granted ->
        if (granted) onEnableGranted() else onSkip()
    }

    AdaptiveSheetOrDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(Res.string.reminder_notification_permission_title)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingXxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

            Text(
                text = stringResource(Res.string.reminder_notification_permission_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
            ) {
                ActivationReminderFeatureRow(
                    icon = Icons.Outlined.Schedule,
                    text = stringResource(Res.string.reminder_notification_permission_feature1),
                )
                ActivationReminderFeatureRow(
                    icon = Icons.Outlined.Notifications,
                    text = stringResource(Res.string.reminder_notification_permission_feature2),
                )
                ActivationReminderFeatureRow(
                    icon = Icons.Outlined.AutoAwesome,
                    text = stringResource(Res.string.reminder_notification_permission_feature3),
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

            AppButton(
                text = stringResource(Res.string.reminder_notification_permission_enable),
                onClick = { requestNotificationPermission() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
            AppButtonText(
                text = stringResource(Res.string.reminder_notification_permission_skip),
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ActivationReminderFeatureRow(
    icon: ImageVector,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
