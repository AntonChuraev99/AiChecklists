package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.material3.minimumInteractiveComponentSize
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_attachment_remove
import coil3.compose.AsyncImage
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import org.jetbrains.compose.resources.stringResource

/**
 * 56×56dp attachment preview chip shown in the pending-attachments strip above [ChatInputRow].
 *
 * Visual content only — no filename label (font-scale safety rule from item-attachments
 * 2026-05-15). Audio chips show duration as the sole exception (≤5 chars, high-signal metadata).
 *
 * The remove ✕ button is placed at the top-right corner with a slight bleed outside the tile
 * boundary — standard Android close-chip affordance (Google Search chips, Gmail, contacts).
 * Touch target ≥ 48dp is guaranteed via [minimumInteractiveComponentSize].
 */
@Composable
fun ChatAttachmentChip(
    attachment: ChatAttachment,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val removeLabel = stringResource(Res.string.chat_attachment_remove)
    val chipShape = RoundedCornerShape(12.dp)

    Box(modifier = modifier.size(56.dp)) {
        Surface(
            shape = chipShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (attachmentSourceFor(attachment)) {
                AttachmentSource.Image -> {
                    AsyncImage(
                        model = attachment.sourcePath,
                        contentDescription = attachment.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(chipShape),
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
                        // Audio-only exception: duration is ≤5 chars and high-signal
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

        // Remove button — top-right corner, slight bleed outside tile for tap target.
        // minimumInteractiveComponentSize() ensures ≥48dp effective touch zone (AC-31).
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .size(24.dp)
                .minimumInteractiveComponentSize(),
        ) {
            Icon(
                imageVector = Icons.Filled.Cancel,
                contentDescription = removeLabel,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Derive [AttachmentSource] from mimeType, falling back to Image for unknown types.
 */
private fun attachmentSourceFor(attachment: ChatAttachment): AttachmentSource {
    val mime = attachment.mimeType
    return when {
        mime.startsWith("image/") -> AttachmentSource.Image
        mime == "application/pdf" -> AttachmentSource.Pdf
        mime.startsWith("text/") -> AttachmentSource.Text
        mime.startsWith("audio/") -> AttachmentSource.Audio
        mime in setOf("video/mp4", "video/webm") -> AttachmentSource.Audio
        else -> AttachmentSource.Image
    }
}

/**
 * Parse duration from audio file name convention "audio_<durationMs>ms_<timestamp>.m4a".
 * Returns null if the filename doesn't follow the convention (e.g. files from the picker).
 */
private fun parseDurationFromFileName(fileName: String): Long? {
    return try {
        val match = Regex("audio_(\\d+)ms_").find(fileName)
        match?.groupValues?.getOrNull(1)?.toLongOrNull()
    } catch (_: Exception) {
        null
    }
}
