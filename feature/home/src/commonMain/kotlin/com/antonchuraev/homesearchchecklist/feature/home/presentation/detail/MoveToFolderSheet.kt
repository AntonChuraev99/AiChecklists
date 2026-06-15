package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.move_here_current
import aichecklists.core.designsystem.generated.resources.move_to_folder_title
import aichecklists.core.designsystem.generated.resources.move_to_root
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AdaptiveSheetOrDialog
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * "Move to…" picker for a folder node OR a leaf item.
 *
 * Renders the whole folder tree of the checklist as a flattened, depth-indented list of
 * destination rows (plus a leading "Home / top level" row for the checklist root). Illegal
 * targets — the node itself and any of its descendants (a cycle) and the node's current parent
 * (a no-op) — are disabled by the ViewModel ([MoveTargetUiModel.enabled]) and rendered greyed
 * out / non-clickable. The current parent shows a trailing check + "Current location" subtitle.
 *
 * Uses [AdaptiveSheetOrDialog] (sheet on phone, dialog on tablet) like the other detail sheets,
 * so it inherits the design-system presentation and dismiss behaviour.
 *
 * @param targets    Flattened destination rows (root first), precomputed in the ViewModel.
 * @param onTargetSelected Invoked with the chosen target folder id (null = checklist root).
 * @param onDismiss  Invoked on swipe-dismiss / outside tap.
 */
@Composable
internal fun MoveToFolderSheet(
    targets: List<MoveTargetUiModel>,
    onTargetSelected: (targetFolderId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveSheetOrDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(Res.string.move_to_folder_title)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Cap the height so a deep tree scrolls inside the sheet instead of pushing the
                // dismiss affordance off-screen.
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = AppDimens.SpacingXxl),
        ) {
            targets.forEach { target ->
                MoveTargetRow(target = target, onClick = { onTargetSelected(target.id) })
            }
        }
    }
}

// internal (not private) so the Roborazzi screenshot test in androidHostTest can render the
// destination rows inline (deterministic capture without the sheet window) — see
// feature/home/src/androidHostTest/.../FolderComponentsScreenshotTest.kt.
@Composable
internal fun MoveTargetRow(
    target: MoveTargetUiModel,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // Leading inset grows with depth; the root row (depth 0) sits flush with the screen padding.
    val depthInset = AppDimens.ScreenPaddingHorizontal + (AppDimens.SpacingLg * target.depth)

    val contentColor = if (target.enabled || target.isCurrentParent) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val label = if (target.id == null) stringResource(Res.string.move_to_root) else target.name

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.MinTouchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = target.enabled,
                onClick = onClick,
            )
            .semantics { role = Role.Button }
            .padding(
                start = depthInset,
                end = AppDimens.ScreenPaddingHorizontal,
                top = AppDimens.SpacingSm,
                bottom = AppDimens.SpacingSm,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    ) {
        Icon(
            imageVector = if (target.id == null) Icons.Outlined.Home else Icons.Outlined.Folder,
            contentDescription = null,
            tint = if (target.enabled || target.isCurrentParent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (target.isCurrentParent) {
                Text(
                    text = stringResource(Res.string.move_here_current),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (target.isCurrentParent) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(AppDimens.IconSizeMd),
            )
        }
    }
}
