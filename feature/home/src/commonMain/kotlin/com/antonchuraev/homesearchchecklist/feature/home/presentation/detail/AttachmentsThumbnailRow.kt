package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.attachment_add_file_button
import aichecklists.core.designsystem.generated.resources.attachment_add_image_button
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import org.jetbrains.compose.resources.stringResource

private val AddButtonCorner = 8.dp

/**
 * Horizontal scrollable row displaying [attachments] as [AttachmentThumbnail] tiles,
 * followed by "+ Image" and "+ File" add-button cards.
 *
 * Rules:
 * - If [attachments] is empty and [canAddMore] is true, only the two add-buttons are shown.
 * - When [canAddMore] is false, the add-buttons are rendered in a disabled visual state and
 *   suppress their own click callbacks. The premium-limit snackbar must be triggered via a
 *   ViewModel SideEffect wired to [onAddImageClick]/[onAddFileClick] at the callsite.
 * - No "empty" message is rendered here — the subtitle in ItemDetailsSheetRow above handles it.
 *
 * Spacing note: the [LazyRow] uses zero horizontal [contentPadding] so that thumbnails align
 * with the left edge of the parent container. When embedded inside [ItemDetailsSheet] the
 * parent Column already provides [AppDimens.ScreenPaddingHorizontal] (16dp) of horizontal
 * padding — adding more here would double-pad. If reused elsewhere without a padded parent,
 * pass `modifier = Modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal)`.
 */
@Composable
internal fun AttachmentsThumbnailRow(
    attachments: List<Attachment>,
    onAttachmentClick: (attachmentId: String) -> Unit,
    onAddImageClick: () -> Unit,
    onAddFileClick: () -> Unit,
    canAddMore: Boolean = true,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AttachmentThumbnailSize),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        contentPadding = PaddingValues(0.dp),
    ) {
        items(attachments, key = { it.id }) { attachment ->
            AttachmentThumbnail(
                attachment = attachment,
                onClick = { onAttachmentClick(attachment.id) },
            )
        }

        // Visual separator between existing attachments and add-action buttons.
        // Only shown when there is at least one attachment to avoid orphaned divider.
        if (attachments.isNotEmpty()) {
            item(key = "divider") {
                Box(
                    modifier = Modifier
                        .height(AttachmentThumbnailSize)
                        .padding(horizontal = AppDimens.SpacingXs),
                    contentAlignment = Alignment.Center,
                ) {
                    VerticalDivider(
                        modifier = Modifier.height(AttachmentThumbnailSize * 0.6f),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }

        item(key = "add_image") {
            AddAttachmentButton(
                icon = Icons.Outlined.PhotoLibrary,
                contentDescription = stringResource(Res.string.attachment_add_image_button),
                enabled = canAddMore,
                onClick = onAddImageClick,
            )
        }

        item(key = "add_file") {
            AddAttachmentButton(
                icon = Icons.Outlined.Description,
                contentDescription = stringResource(Res.string.attachment_add_file_button),
                enabled = canAddMore,
                onClick = onAddFileClick,
            )
        }
    }
}

/**
 * An 80×80dp outlined add-button tile with a single centred icon (Option A: icon-only).
 *
 * Two distinct icons disambiguate image vs file without text that can wrap on high font scales:
 * - [Icons.Outlined.PhotoLibrary] for "Add image"
 * - [Icons.Outlined.Description] for "Add file"
 *
 * [contentDescription] is set on the Icon for accessibility (TalkBack announces it).
 *
 * When [enabled] is false the tile uses muted colours and ignores taps.
 * The parent is responsible for showing the premium limit snackbar.
 *
 * Touch target: 80×80dp — well above MD3 48dp minimum.
 */
@Composable
private fun AddAttachmentButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(AddButtonCorner)
    val iconTint = if (enabled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    val borderColor = if (enabled) MaterialTheme.colorScheme.outline
    else MaterialTheme.colorScheme.outlineVariant

    OutlinedCard(
        modifier = Modifier
            .size(AttachmentThumbnailSize)
            .then(
                if (enabled) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = shape,
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier.size(AttachmentThumbnailSize),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
