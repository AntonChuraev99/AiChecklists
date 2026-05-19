package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.ui.graphics.vector.ImageVector
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_attachment_source_audio
import aichecklists.core.designsystem.generated.resources.chat_attachment_source_chooser_title
import aichecklists.core.designsystem.generated.resources.chat_attachment_source_image
import aichecklists.core.designsystem.generated.resources.chat_attachment_source_pdf
import aichecklists.core.designsystem.generated.resources.chat_attachment_source_text
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource
import org.jetbrains.compose.resources.stringResource

/**
 * Modal bottom sheet showing 4 attachment source choices:
 * Image / PDF document / Text file / Audio file.
 *
 * Tapping a row emits [onSourceSelected] with the corresponding [AttachmentSource]
 * and automatically dismisses the sheet via [onDismiss].
 *
 * Note: "Audio file" row = pick EXISTING audio from device (complementary to
 * press-and-hold mic = record NEW audio). Both paths are intentional.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatAttachmentSourceSheet(
    onSourceSelected: (AttachmentSource) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val imageLabel = stringResource(Res.string.chat_attachment_source_image)
    val pdfLabel = stringResource(Res.string.chat_attachment_source_pdf)
    val textLabel = stringResource(Res.string.chat_attachment_source_text)
    val audioLabel = stringResource(Res.string.chat_attachment_source_audio)
    val title = stringResource(Res.string.chat_attachment_source_chooser_title)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = AppDimens.SpacingLg),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = AppDimens.SpacingLg,
                        vertical = AppDimens.SpacingMd,
                    ),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            AttachmentSourceRow(
                icon = Icons.Filled.Image,
                label = imageLabel,
                onClick = {
                    onSourceSelected(AttachmentSource.Image)
                    onDismiss()
                },
            )
            AttachmentSourceRow(
                icon = Icons.Filled.PictureAsPdf,
                label = pdfLabel,
                onClick = {
                    onSourceSelected(AttachmentSource.Pdf)
                    onDismiss()
                },
            )
            AttachmentSourceRow(
                icon = Icons.Filled.Description,
                label = textLabel,
                onClick = {
                    onSourceSelected(AttachmentSource.Text)
                    onDismiss()
                },
            )
            AttachmentSourceRow(
                icon = Icons.Filled.AudioFile,
                label = audioLabel,
                onClick = {
                    onSourceSelected(AttachmentSource.Audio)
                    onDismiss()
                },
            )
        }
    }
}

/**
 * Single row inside [ChatAttachmentSourceSheet] — icon + label, full-width clickable.
 * Touch target: 12dp top+bottom padding + 24dp icon content = ≥ 48dp total height.
 */
@Composable
private fun AttachmentSourceRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = AppDimens.SpacingLg,
                vertical = AppDimens.SpacingMd,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingLg),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // label provides semantic context
            modifier = Modifier.size(AppDimens.IconSizeMd),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
