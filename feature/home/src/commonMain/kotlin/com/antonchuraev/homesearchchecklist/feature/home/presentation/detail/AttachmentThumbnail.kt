package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment

internal val AttachmentThumbnailSize = 80.dp
private val ThumbnailCorner = 8.dp

/**
 * A square 80dp thumbnail for a single [Attachment].
 *
 * All attachment types share the same [OutlinedCard] wrapper (8dp corners, outline border,
 * surfaceContainerLow background, 0dp elevation) to produce a unified visual language in the row.
 *
 * - Images: [AsyncImage] fills the card with [ContentScale.Crop]; border painted on top via MD3
 *   OutlinedCard. Placeholder and error states show a generic file icon.
 * - Non-images (PDF, doc, etc.): centre-aligned file icon (28dp) + single-line ellipsized
 *   filename in labelSmall, with MIME-derived icon tint.
 *
 * Touch target: 80×80dp — well above the MD3 48dp minimum. Clickable ripple covers the full card.
 */
@Composable
internal fun AttachmentThumbnail(
    attachment: Attachment,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(ThumbnailCorner)
    OutlinedCard(
        modifier = modifier
            .size(AttachmentThumbnailSize)
            .clickable(onClick = onClick),
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        if (attachment.isImage) {
            ImageThumbnailContent(attachment = attachment)
        } else {
            FileThumbnailContent(attachment = attachment)
        }
    }
}

@Composable
private fun ImageThumbnailContent(attachment: Attachment) {
    val context = LocalPlatformContext.current
    val request = ImageRequest.Builder(context)
        .data(attachment.path)
        .crossfade(true)
        .build()

    AsyncImage(
        model = request,
        contentDescription = attachment.fileName,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        placeholder = rememberVectorPainter(Icons.AutoMirrored.Filled.InsertDriveFile),
        error = rememberVectorPainter(Icons.Filled.BrokenImage),
    )
}

@Composable
private fun FileThumbnailContent(attachment: Attachment) {
    val iconTint = fileMimeIconTint(attachment.mimeType)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppDimens.SpacingXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = attachment.fileName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Returns a semantic color token that gives a rough MIME-category visual hint.
 * Falls back to [MaterialTheme.colorScheme.onSurfaceVariant] for unrecognised types.
 * All colours are from the active MD3 colour scheme — no hardcoded hex.
 */
@Composable
private fun fileMimeIconTint(mimeType: String?): Color = when {
    mimeType == null -> MaterialTheme.colorScheme.onSurfaceVariant
    mimeType.startsWith("application/pdf") -> MaterialTheme.colorScheme.error
    mimeType.startsWith("application/vnd.openxmlformats") ||
        mimeType.startsWith("application/msword") -> MaterialTheme.colorScheme.primary
    mimeType.startsWith("text/") -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
