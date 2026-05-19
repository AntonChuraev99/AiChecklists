package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.mimeTypeToAttachmentSource

/**
 * Horizontally-scrollable row of 80×80dp attachment thumbnails inside a user
 * chat bubble. Displayed ABOVE the message text when [attachments] is non-empty.
 *
 * Module-boundary rule (spec §10 point 10): this composable is private to
 * `feature/aichat` and typed to [ChatAttachment], NOT importing from `feature/home`.
 * The visual design is a port of [AttachmentThumbnail] in item-attachments — same
 * sizes and tokens — but with zero cross-feature dependency.
 *
 * Tap-to-fullscreen is out-of-scope for Phase 3 (thumbnails are non-clickable).
 */
@Composable
internal fun MessageAttachmentRow(
    attachments: List<ChatAttachment>,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
    ) {
        items(attachments, key = { it.sourcePath }) { attachment ->
            MessageAttachmentThumbnail(attachment)
        }
    }
}

@Composable
private fun MessageAttachmentThumbnail(attachment: ChatAttachment) {
    val thumbnailShape = RoundedCornerShape(8.dp)
    val source = mimeTypeToAttachmentSource(attachment.mimeType) ?: AttachmentSource.Image

    Surface(
        shape = thumbnailShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.size(80.dp),
    ) {
        when (source) {
            AttachmentSource.Image -> {
                AsyncImage(
                    model = attachment.sourcePath,
                    contentDescription = attachment.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(thumbnailShape),
                )
            }

            AttachmentSource.Pdf -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PictureAsPdf,
                        contentDescription = attachment.fileName,
                        modifier = Modifier.size(AppDimens.IconSizeMd),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            AttachmentSource.Text -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = attachment.fileName,
                        modifier = Modifier.size(AppDimens.IconSizeMd),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            AttachmentSource.Audio -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = attachment.fileName,
                        modifier = Modifier.size(AppDimens.IconSizeMd),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    // Duration from filename convention (see ChatAttachmentChip)
                    val durationMs = parseDurationFromFileName(attachment.fileName)
                    if (durationMs != null) {
                        val minutes = durationMs / 60000L
                        val seconds = (durationMs / 1000L) % 60L
                        val formatted = "$minutes:${seconds.toString().padStart(2, '0')}"
                        Text(
                            text = formatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun parseDurationFromFileName(fileName: String): Long? {
    return try {
        val match = Regex("audio_(\\d+)ms_").find(fileName)
        match?.groupValues?.getOrNull(1)?.toLongOrNull()
    } catch (_: Exception) {
        null
    }
}
