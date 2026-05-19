package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment

/**
 * Horizontally-scrollable strip of [ChatAttachmentChip]s shown above [ChatInputRow]
 * when the user has picked one or more pending attachments.
 *
 * Animated in/out with [expandVertically] + [fadeIn]/[fadeOut] so the strip
 * grows/shrinks smoothly — no jarring layout jump when the first chip is added
 * or the last chip is removed.
 *
 * Layout: sits BETWEEN the message list and the input row in [ChatScreen].
 */
@Composable
fun ChatAttachmentChipStrip(
    attachments: List<ChatAttachment>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = attachments.isNotEmpty(),
        enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
    ) {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
            contentPadding = PaddingValues(
                horizontal = AppDimens.ScreenPaddingHorizontal,
                vertical = AppDimens.SpacingSm,
            ),
        ) {
            items(attachments, key = { it.sourcePath }) { attachment ->
                ChatAttachmentChip(
                    attachment = attachment,
                    onRemove = { onRemove(attachment.sourcePath) },
                )
            }
        }
    }
}
