package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.folder_reminder
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppItemMetaChip
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDensity
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTextStyles
import com.antonchuraev.homesearchchecklist.feature.home.presentation.components.RowContainer
import org.jetbrains.compose.resources.stringResource

/**
 * A folder row at the current drill-down level of a folder-enabled checklist.
 *
 * Unlike [ChecklistItemCard] (which uses a 30/70 hit-zone split: left toggles the checkbox,
 * right opens the details sheet), a folder has no checkbox — so the WHOLE card is clickable and
 * the single action is "open the folder". The trailing chevron signals drill-down.
 *
 * Visuals follow the design system: [AppCard] container, a leading folder icon, the folder name,
 * an aggregate-progress meta chip ("checked/total" of all descendant leaves) and a chevron.
 *
 * Outside edit mode: tap drills into the folder; long-press opens the folder actions sheet
 * (Rename / Move to… / Delete) — mirroring how a leaf item exposes its actions via long-press /
 * the details sheet (Phase 4). The single-tap target stays "open the folder".
 *
 * In edit mode ([isEditMode]) the folder joins the single mixed reorderable list, so it can be
 * dragged between items. The whole card becomes the drag handle ([cardDragModifier]) and tap /
 * long-press navigation is suppressed (you're reordering, not opening) — matching how leaf items
 * behave during reorder.
 *
 * A read-only reminder bell ([hasReminder]) renders before the chevron when the folder has an
 * active reminder. Per the card hit-zone rule it is purely visual — it has no `clickable`; the
 * reminder is managed from the folder actions sheet (long-press), like every other folder action.
 *
 * @param name      Folder display name.
 * @param total     Total descendant leaves (chip hidden when 0 — an empty folder shows no count).
 * @param progressLabel Pre-formatted "checked/total" label (resolved from string resources by the caller).
 * @param hasReminder Whether to show the read-only reminder indicator.
 * @param onOpen    Invoked when the card is tapped (outside edit mode) → drill into this folder.
 * @param onLongPress Invoked on long-press (outside edit mode) → open the folder actions sheet.
 * @param isEditMode When true the card becomes a drag handle and tap/long-press are suppressed.
 * @param cardDragModifier The reorderable drag-handle modifier applied to the card in edit mode.
 */
@Composable
internal fun FolderCard(
    name: String,
    total: Int,
    progressLabel: String,
    hasReminder: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    isDragging: Boolean = false,
    cardDragModifier: Modifier = Modifier,
    wiggleAngle: Float = 0f,
    /**
     * [RowContainer.Card] (default) keeps the [AppCard] rendering byte-for-byte. [RowContainer.Grouped]
     * (v2 Standard, redesign B) draws no container — the caller paints the row's `groupSegment` — and
     * shows a 28dp folder tile, the name, the progress as plain text, the bell and a chevron. Same
     * callbacks, same edit-mode drag handle.
     */
    container: RowContainer = RowContainer.Card,
) {
    if (container == RowContainer.Grouped) {
        GroupedFolderRow(
            name = name,
            total = total,
            progressLabel = progressLabel,
            hasReminder = hasReminder,
            onOpen = onOpen,
            onLongPress = onLongPress,
            modifier = modifier,
            isEditMode = isEditMode,
            isDragging = isDragging,
            cardDragModifier = cardDragModifier,
            wiggleAngle = wiggleAngle,
        )
        return
    }
    // Tap (open) / long-press (actions) are handed to AppCard, which applies combinedClickable to
    // its INNER content — so the Card clips the ripple to its rounded [shape] and no rectangle
    // bleeds past the corners. Drag/wiggle/zIndex stay on the OUTER modifier so the elevation
    // shadow and reorder transform are not clipped.
    //   • Normal mode: the whole card surface is the tap (open) / long-press (actions) target.
    //   • Edit mode: the card surface becomes the reorder drag handle; tap/long-press are dropped.
    AppCard(
        modifier = modifier
            .then(if (isDragging) Modifier.zIndex(1f) else Modifier)
            .graphicsLayer {
                // Match the leaf wiggle in edit mode; freeze the rotation while this card is dragged.
                if (isEditMode && !isDragging) {
                    rotationZ = wiggleAngle
                }
            }
            .then(if (isEditMode) cardDragModifier else Modifier),
        onClick = if (isEditMode) null else onOpen,
        onLongClick = if (isEditMode) null else onLongPress,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null, // decorative — the name conveys meaning
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = AppDimens.SpacingSm),
            )
            if (total > 0) {
                AppItemMetaChip(
                    icon = Icons.Outlined.CheckCircle,
                    label = progressLabel,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            // Read-only reminder indicator (no clickable — see hit-zone rule). Managed from the
            // folder actions sheet (long-press).
            if (hasReminder) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = stringResource(Res.string.folder_reminder),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Grouped (redesign B) folder row. The whole row is the target (tap = open, long-press = actions);
 * in edit mode it is the reorder handle instead. The tile sits in the checkbox column of the leaf rows
 * so the name starts on the same x as their text (and the group's divider inset).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupedFolderRow(
    name: String,
    total: Int,
    progressLabel: String,
    hasReminder: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier,
    isEditMode: Boolean,
    isDragging: Boolean,
    cardDragModifier: Modifier,
    wiggleAngle: Float,
) {
    Box(
        modifier = modifier
            .then(if (isDragging) Modifier.zIndex(1f) else Modifier)
            .fillMaxWidth()
            .then(
                if (isEditMode) {
                    cardDragModifier
                } else {
                    Modifier.combinedClickable(onClick = onOpen, onLongClick = onLongPress)
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppDensity.RowMinHeightComfortable)
                .graphicsLayer { if (isEditMode && !isDragging) rotationZ = wiggleAngle }
                .padding(
                    start = AppDensity.GroupRowPaddingHorizontal - FolderTileOverhang,
                    end = AppDimens.SpacingMd,
                    top = AppDimens.SpacingSm,
                    bottom = AppDimens.SpacingSm,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(FolderTileSize)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null, // decorative — the name conveys meaning
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(AppDensity.GroupRowGap - FolderTileOverhang))
            Text(
                text = name,
                style = AppTextStyles.taskTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = AppDimens.SpacingSm),
            )
            if (total > 0) {
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            // Read-only reminder indicator (no clickable — see hit-zone rule).
            if (hasReminder) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = stringResource(Res.string.folder_reminder),
                    modifier = Modifier.padding(start = AppDimens.SpacingXs).size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 28dp folder tile; it overhangs the leaf rows' 24dp checkbox column by 2dp on each side. */
private val FolderTileSize = 28.dp
private val FolderTileOverhang = (FolderTileSize - AppDensity.RoundCheckboxBox) / 2
