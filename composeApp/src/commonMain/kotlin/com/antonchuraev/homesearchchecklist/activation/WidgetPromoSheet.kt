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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Widgets
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
import aichecklists.core.designsystem.generated.resources.widget_promo_add
import aichecklists.core.designsystem.generated.resources.widget_promo_description
import aichecklists.core.designsystem.generated.resources.widget_promo_feature1
import aichecklists.core.designsystem.generated.resources.widget_promo_feature2
import aichecklists.core.designsystem.generated.resources.widget_promo_feature3
import aichecklists.core.designsystem.generated.resources.widget_promo_skip
import aichecklists.core.designsystem.generated.resources.widget_promo_title
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AdaptiveSheetOrDialog
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * One-time promo for the home-screen widget, shown a distinct beat AFTER the user's second checklist
 * (see App.kt — evaluated on app open, gated on `count >= 2` + a device show-once flag, and never
 * rendered while the [ActivationReminderSheet] is up). Deliberately sequenced away from the
 * post-first-checklist reminder opt-in + the notification-permission ask so the two never stack.
 *
 * Mirrors [ActivationReminderSheet]'s look for consistency. "Add widget" does NOT pin a widget
 * programmatically (no such platform API exists in this app) — it hands off to the established
 * widget-instruction flow ([com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation.components.WidgetInstructionOverlay],
 * reused as the App-level `showWidgetInstruction` sheet), which is how every other "Add widget"
 * affordance (onboarding DiscoverMoreStep, updates-feed deep link) already works.
 *
 * @param onAddWidget Called when the user taps "Add widget" — the host opens the widget instructions.
 * @param onSkip Called when the user taps "Not now".
 * @param onDismiss Called on scrim/back dismissal (treated as skip by the host).
 */
@Composable
fun WidgetPromoSheet(
    onAddWidget: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveSheetOrDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(Res.string.widget_promo_title)) },
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
                    imageVector = Icons.Outlined.Widgets,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

            Text(
                text = stringResource(Res.string.widget_promo_description),
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
                WidgetPromoFeatureRow(
                    icon = Icons.Outlined.Home,
                    text = stringResource(Res.string.widget_promo_feature1),
                )
                WidgetPromoFeatureRow(
                    icon = Icons.Outlined.CheckCircle,
                    text = stringResource(Res.string.widget_promo_feature2),
                )
                WidgetPromoFeatureRow(
                    icon = Icons.Outlined.Schedule,
                    text = stringResource(Res.string.widget_promo_feature3),
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

            AppButton(
                text = stringResource(Res.string.widget_promo_add),
                onClick = onAddWidget,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
            AppButtonText(
                text = stringResource(Res.string.widget_promo_skip),
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WidgetPromoFeatureRow(
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
