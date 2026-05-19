package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_feedback_open
import aichecklists.core.designsystem.generated.resources.chat_open_checklist
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import org.jetbrains.compose.resources.stringResource

/**
 * Renders a single chat message bubble.
 *
 * - [ChatRole.User]      → right-aligned, primaryContainer bubble, with [InlineCostBadge]
 * - [ChatRole.Assistant] → left-aligned, surfaceContainerHigh bubble, no cost badge
 *
 * Bubble shapes use an asymmetric corner strategy (the "tail" corner is 4dp):
 *  User:      top-start=16, top-end=16, bottom-end=4, bottom-start=16
 *  Assistant: top-start=16, top-end=16, bottom-end=16, bottom-start=4
 *
 * Text inside every bubble is wrapped in [SelectionContainer] so the user can
 * long-press to select + copy via the platform's native selection toolbar
 * (Android: long-press → handles + Copy in the contextual action bar;
 *  iOS: long-press → magnifier + Copy menu; wasmJs: drag-select like normal web text).
 *
 * Max bubble width is capped at 75% of the parent width so long messages don't
 * take up the full screen and the left/right alignment remains visually distinct.
 */
@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onFeedbackClick: ((ChatMessage) -> Unit)? = null,
    onOpenChecklist: (() -> Unit)? = null,
) {
    val isUser = message.role == ChatRole.User

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXxs),
        ) {
            Surface(
                shape = if (isUser) {
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomEnd = 4.dp,
                        bottomStart = 16.dp,
                    )
                } else {
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomEnd = 16.dp,
                        bottomStart = 4.dp,
                    )
                },
                color = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer  // md.sys.color.primary-container
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh  // md.sys.color.surface-container-high (M3 Expressive)
                },
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                if (isUser) {
                    // User bubble: attachments (if any) rendered ABOVE the text
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(vertical = AppDimens.SpacingSm),
                    ) {
                        if (message.attachments.isNotEmpty()) {
                            MessageAttachmentRow(
                                attachments = message.attachments,
                                modifier = Modifier.padding(
                                    start = AppDimens.SpacingMd,
                                    end = AppDimens.SpacingMd,
                                    bottom = AppDimens.SpacingXs,
                                ),
                            )
                        }
                        if (message.content.isNotBlank()) {
                            SelectionContainer {
                                Text(
                                    text = message.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(
                                        start = AppDimens.SpacingMd,
                                        end = AppDimens.SpacingMd,
                                        bottom = AppDimens.SpacingXs,
                                    ),
                                )
                            }
                        }
                    }
                } else {
                    SelectionContainer {
                        ChatMarkdownText(
                            markdown = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            // surfaceContainerHigh pairs with onSurface per MD3 tonal pairing rules
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(
                                horizontal = AppDimens.SpacingMd,
                                vertical = AppDimens.SpacingSm,
                            ),
                        )
                    }
                }
            }

            // Cost badge: shown only for user messages where credits were actually deducted (> 0).
            // Layer 1 (local router) is always free → badge stays hidden for most messages.
            if (isUser && message.costCredits > 0) {
                InlineCostBadge(cost = message.costCredits)
            }

            // Meta-action row: feedback + open-checklist deeplink, shown below assistant bubbles.
            // Both are optional; if neither is set the row takes no space.
            if (!isUser && (onFeedbackClick != null || onOpenChecklist != null)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Feedback icon — 20dp inline-decorative exception to the 24dp default rule
                    // (small affordance tucked below the bubble, similar to InlineCostBadge at 16dp).
                    if (onFeedbackClick != null) {
                        IconButton(
                            onClick = { onFeedbackClick(message) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RateReview,
                                contentDescription = stringResource(Res.string.chat_feedback_open),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // "Open checklist" deeplink button — only when the message carries a
                    // linkedChecklistId (successful write-intent outcomes: AddItem, DeleteItem,
                    // CompleteItem, SetItemReminder, CreateChecklist).
                    if (onOpenChecklist != null) {
                        TextButton(onClick = onOpenChecklist) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(Res.string.chat_open_checklist),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = AppDimens.SpacingXxs),
                            )
                        }
                    }
                }
            }
        }
    }
}
