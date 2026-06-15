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
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.automirrored.outlined.PlaylistAddCheck
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_features_case_add
import aichecklists.core.designsystem.generated.resources.chat_features_case_ask
import aichecklists.core.designsystem.generated.resources.chat_features_case_complete
import aichecklists.core.designsystem.generated.resources.chat_features_case_create
import aichecklists.core.designsystem.generated.resources.chat_features_case_delete
import aichecklists.core.designsystem.generated.resources.chat_features_case_find
import aichecklists.core.designsystem.generated.resources.chat_features_case_from_attachment
import aichecklists.core.designsystem.generated.resources.chat_features_case_reminder
import aichecklists.core.designsystem.generated.resources.chat_features_example_add
import aichecklists.core.designsystem.generated.resources.chat_features_example_ask
import aichecklists.core.designsystem.generated.resources.chat_features_example_complete
import aichecklists.core.designsystem.generated.resources.chat_features_example_create
import aichecklists.core.designsystem.generated.resources.chat_features_example_delete
import aichecklists.core.designsystem.generated.resources.chat_features_example_find
import aichecklists.core.designsystem.generated.resources.chat_features_example_from_attachment
import aichecklists.core.designsystem.generated.resources.chat_features_example_reminder
import aichecklists.core.designsystem.generated.resources.chat_features_got_it
import aichecklists.core.designsystem.generated.resources.chat_features_intro
import aichecklists.core.designsystem.generated.resources.chat_features_section_ask
import aichecklists.core.designsystem.generated.resources.chat_features_section_create
import aichecklists.core.designsystem.generated.resources.chat_features_section_items
import aichecklists.core.designsystem.generated.resources.chat_features_section_reminders
import aichecklists.core.designsystem.generated.resources.chat_features_sheet_title
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Features help bottom sheet — explains what AI Chat can do, grouped by category.
 *
 * Layout:
 * - **Title + intro** — headline plus a one-line reassurance that plain language is enough.
 * - **Four sections**, each opened by a [FeatureSection] header (primary-tinted label):
 *     1. *Manage items* — add / complete / remove.
 *     2. *Create checklists* — from scratch / from a photo or PDF.
 *     3. *Reminders* — set a reminder.
 *     4. *Ask & find* — find an item / ask anything.
 * - **Cases** — each [FeatureCase] is `icon + title (what it does) + example command` on a
 *   dimmed second line. Read-only: examples are prompts the user can mentally copy, not buttons.
 *   Every case mirrors a capability the chat actually supports (kept in sync with the server
 *   FEATURE_CATALOG) so the assistant never replies "I can't help with that".
 *
 * Opens when the user taps the "?" icon in the input row leading position.
 * Dismissed via drag, scrim tap, or the "Got it" button.
 *
 * Pattern mirrors [AiChatPricingHelpSheet]'s row shape; reuse rather than abstract to keep each
 * sheet free to evolve its own structure.
 *
 * @param onDismiss  Called when the sheet should be hidden (set showFeaturesSheet = false).
 * @param sheetState Optional [SheetState]; callers can pass a custom state for animation control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatFeaturesHelpSheet(
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
        ) {
            Text(
                text = stringResource(Res.string.chat_features_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

            Text(
                text = stringResource(Res.string.chat_features_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Section 1 — Manage items
            FeatureSection(Res.string.chat_features_section_items)
            FeatureCase(
                icon = Icons.Outlined.AddTask,
                titleRes = Res.string.chat_features_case_add,
                exampleRes = Res.string.chat_features_example_add,
            )
            FeatureCase(
                icon = Icons.Outlined.CheckCircle,
                titleRes = Res.string.chat_features_case_complete,
                exampleRes = Res.string.chat_features_example_complete,
            )
            FeatureCase(
                icon = Icons.Outlined.DeleteOutline,
                titleRes = Res.string.chat_features_case_delete,
                exampleRes = Res.string.chat_features_example_delete,
            )

            // Section 2 — Create checklists
            FeatureSection(Res.string.chat_features_section_create)
            FeatureCase(
                icon = Icons.AutoMirrored.Outlined.PlaylistAddCheck,
                titleRes = Res.string.chat_features_case_create,
                exampleRes = Res.string.chat_features_example_create,
            )
            FeatureCase(
                icon = Icons.Outlined.PhotoCamera,
                titleRes = Res.string.chat_features_case_from_attachment,
                exampleRes = Res.string.chat_features_example_from_attachment,
            )

            // Section 3 — Reminders
            FeatureSection(Res.string.chat_features_section_reminders)
            FeatureCase(
                icon = Icons.Outlined.NotificationsNone,
                titleRes = Res.string.chat_features_case_reminder,
                exampleRes = Res.string.chat_features_example_reminder,
            )

            // Section 4 — Ask & find
            FeatureSection(Res.string.chat_features_section_ask)
            FeatureCase(
                icon = Icons.Outlined.Search,
                titleRes = Res.string.chat_features_case_find,
                exampleRes = Res.string.chat_features_example_find,
            )
            FeatureCase(
                icon = Icons.Outlined.AutoAwesome,
                titleRes = Res.string.chat_features_case_ask,
                exampleRes = Res.string.chat_features_example_ask,
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

            AppButton(
                text = stringResource(Res.string.chat_features_got_it),
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
 * Category header that opens a group of [FeatureCase]s. Primary-tinted label that sits one
 * level below the sheet title and one above each case title, giving a clear 3-level hierarchy.
 */
@Composable
private fun FeatureSection(
    titleRes: StringResource,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = AppDimens.SpacingLg, bottom = AppDimens.SpacingSm),
    )
}

/**
 * Single capability row: icon + title (what it does) + an example command on a dimmed
 * second line. Alignment is Top so a multi-line example starts beside the icon top edge.
 */
@Composable
private fun FeatureCase(
    icon: ImageVector,
    titleRes: StringResource,
    exampleRes: StringResource,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.SpacingSm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(AppDimens.SpacingLg))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXxs),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(exampleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
