package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.OfflineBolt
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_pricing_apply_note
import aichecklists.core.designsystem.generated.resources.chat_pricing_got_it
import aichecklists.core.designsystem.generated.resources.chat_pricing_layer1_detail
import aichecklists.core.designsystem.generated.resources.chat_pricing_layer1_title
import aichecklists.core.designsystem.generated.resources.chat_pricing_layer2_detail
import aichecklists.core.designsystem.generated.resources.chat_pricing_layer2_title
import aichecklists.core.designsystem.generated.resources.chat_pricing_layer3_detail
import aichecklists.core.designsystem.generated.resources.chat_pricing_layer3_title
import aichecklists.core.designsystem.generated.resources.chat_pricing_sheet_title
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Pricing help bottom sheet explaining the 3-tier credit system.
 *
 * - Layer 1 (Local): free — device-side intent routing
 * - Layer 2 (Classifier): 1 credit — gemini-flash-lite for ambiguous commands
 * - Layer 3 (FullChat): 3 credits — full AI reasoning for open questions
 *
 * Opens when the user taps the "?" icon in [ChatHeader].
 * Dismissed via drag, scrim tap, or the "Got it" button.
 *
 * @param onDismiss  Called when the sheet should be hidden (update state to showPricingSheet=false).
 * @param sheetState Optional [SheetState]; callers can pass a custom state for animation control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatPricingHelpSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.SpacingXl, vertical = AppDimens.SpacingXl),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
        ) {
            Text(
                text = stringResource(Res.string.chat_pricing_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

            PricingRow(
                icon = Icons.Outlined.OfflineBolt,
                iconTint = MaterialTheme.colorScheme.tertiary,
                titleRes = Res.string.chat_pricing_layer1_title,
                detailRes = Res.string.chat_pricing_layer1_detail,
            )

            PricingRow(
                icon = Icons.Outlined.AutoAwesome,
                iconTint = MaterialTheme.colorScheme.primary,
                titleRes = Res.string.chat_pricing_layer2_title,
                detailRes = Res.string.chat_pricing_layer2_detail,
            )

            PricingRow(
                icon = Icons.Outlined.Psychology,
                iconTint = MaterialTheme.colorScheme.secondary,
                titleRes = Res.string.chat_pricing_layer3_title,
                detailRes = Res.string.chat_pricing_layer3_detail,
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = AppDimens.SpacingLg),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Text(
                text = stringResource(Res.string.chat_pricing_apply_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

            AppButton(
                text = stringResource(Res.string.chat_pricing_got_it),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )

            // Bottom spacing so the button isn't flush with the navigation bar
            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
        }
    }
}

// ---------------------------------------------------------------------------
// Internal composables
// ---------------------------------------------------------------------------

/**
 * Single row inside the pricing sheet: icon + title + supporting detail.
 * Alignment is Top so multi-line detail text starts beside the icon top edge.
 */
@Composable
private fun PricingRow(
    icon: ImageVector,
    iconTint: Color,
    titleRes: StringResource,
    detailRes: StringResource,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.SpacingMd),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(28.dp),
        )

        Spacer(modifier = Modifier.width(AppDimens.SpacingLg))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXxs),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(detailRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
