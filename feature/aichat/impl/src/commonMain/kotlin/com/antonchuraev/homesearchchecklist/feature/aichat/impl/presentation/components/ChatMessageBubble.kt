package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_ask_ai_fallback
import aichecklists.core.designsystem.generated.resources.chat_assistant_label
import aichecklists.core.designsystem.generated.resources.chat_feedback_open
import aichecklists.core.designsystem.generated.resources.chat_message_copy
import aichecklists.core.designsystem.generated.resources.chat_message_thumb_down
import aichecklists.core.designsystem.generated.resources.chat_message_thumb_up
import aichecklists.core.designsystem.generated.resources.chat_open_checklist
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import org.jetbrains.compose.resources.stringResource

/**
 * Renders a single chat message bubble — Material 3 chat pattern.
 *
 * Layout (from AI Chat M3 design):
 * - **User (sent)** — right-aligned, `primaryContainer` background, 20-20-4-20 corners
 *   (tail on bottom-right). Max width 320dp (~78% of phone).
 * - **Assistant (received)** — left-aligned, `surfaceContainerHigh` background,
 *   20-20-20-4 corners (tail on bottom-left). Optional [AiSenderLabel] above the
 *   bubble (24dp avatar + "AI-ассистент"). Max width 340dp (~82%).
 *
 * Below assistant bubbles: 4-action row [Copy] [Refresh] [ThumbUp] [ThumbDown]
 * plus optional [Open checklist] TextButton when the message carries a
 * `linkedChecklistId`.
 *
 * Typography: bodyLarge (16sp, lineHeight 24, letterSpacing 0.5) — M3 chat spec.
 * Text inside every bubble is wrapped in [SelectionContainer] for native long-press
 * copy across platforms.
 *
 * @param showSenderLabel If true and the role is Assistant, render the avatar +
 *                        "AI-ассистент" label above the bubble. Pass `false` for
 *                        the static welcome bubble where the label adds noise.
 */
@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onFeedbackClick: ((ChatMessage) -> Unit)? = null,
    onThumbUpClick: ((ChatMessage) -> Unit)? = null,
    onOpenChecklist: (() -> Unit)? = null,
    onAskAiFallback: (() -> Unit)? = null,
    showSenderLabel: Boolean = false,
) {
    val isUser = message.role == ChatRole.User
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXxs),
        ) {
            // AI sender label — 24dp avatar + "AI-ассистент" (M3 chat pattern from design).
            // Welcome bubble passes showSenderLabel=false to keep the static greeting clean.
            if (!isUser && showSenderLabel) {
                AiSenderLabel()
            }

            Surface(
                shape = if (isUser) {
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomEnd = 4.dp,
                        bottomStart = 20.dp,
                    )
                } else {
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomEnd = 20.dp,
                        bottomStart = 4.dp,
                    )
                },
                // User (sent) keeps the primaryContainer accent fill — it reads as "my message".
                // Assistant (received) switches from grey `surfaceContainerHigh` to the clean
                // `surfaceContainerLowest` (white in light) + hairline `outlineVariant` border,
                // matching AskGistiBar. The grey tonal fill was the "страшный серый" the user
                // reported in the dock answer field.
                color = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                },
                border = if (isUser) {
                    null
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                },
                modifier = Modifier.widthIn(max = if (isUser) 320.dp else 340.dp),
            ) {
                if (isUser) {
                    // User bubble: attachments above text (M3 chat media-inset pattern).
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                    ) {
                        if (message.attachments.isNotEmpty()) {
                            MessageAttachmentRow(
                                attachments = message.attachments,
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    bottom = AppDimens.SpacingXs,
                                ),
                            )
                        }
                        if (message.content.isNotBlank()) {
                            SelectionContainer {
                                Text(
                                    text = message.content,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                } else {
                    SelectionContainer {
                        ChatMarkdownText(
                            markdown = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(
                                horizontal = 16.dp,
                                vertical = 12.dp,
                            ),
                        )
                    }
                }
            }

            // Cost badge: shown only for user messages where credits were actually deducted (> 0).
            if (isUser && message.costCredits > 0) {
                InlineCostBadge(cost = message.costCredits)
            }

            // Action row for assistant messages (M3 chat actions): Copy · ThumbUp · ThumbDown.
            // ThumbUp = analytics-only fire-and-forget (no sheet). ThumbDown = opens feedback sheet.
            // Optional "Open checklist" TextButton when the message has a linkedChecklistId.
            if (!isUser && (onFeedbackClick != null || onThumbUpClick != null || onOpenChecklist != null || onAskAiFallback != null)) {
                // offset(x = -6.dp) — Compose equivalent of CSS `marginLeft: -6` (from design).
                // Visually aligns the first action button with the bubble's text left edge,
                // compensating for the 32dp IconButton hit-area. NOTE: must use offset, not
                // padding — padding throws IllegalArgumentException on negative values.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.offset(x = (-6).dp),
                ) {
                    MessageActionButton(
                        icon = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(Res.string.chat_message_copy),
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message.content))
                        },
                    )
                    // ThumbUp = fire-and-forget analytics (handled by ViewModel via OnThumbUpClick).
                    if (onThumbUpClick != null) {
                        MessageActionButton(
                            icon = Icons.Outlined.ThumbUp,
                            contentDescription = stringResource(Res.string.chat_message_thumb_up),
                            onClick = { onThumbUpClick(message) },
                        )
                    }
                    // ThumbDown = opens the feedback sheet (optional free-form text + submit).
                    if (onFeedbackClick != null) {
                        MessageActionButton(
                            icon = Icons.Outlined.ThumbDown,
                            contentDescription = stringResource(Res.string.chat_message_thumb_down),
                            onClick = { onFeedbackClick(message) },
                        )
                    }
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
                    // "Ask AI" fallback button — shown only when the assistant message
                    // carries askAiForText (i.e. it is an Unknown-intent dead-end).
                    // Tapping it escalates the original text to Layer 3 (3 credits),
                    // which is an explicit user opt-in — credits are never auto-burned.
                    if (onAskAiFallback != null) {
                        TextButton(onClick = onAskAiFallback) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(Res.string.chat_ask_ai_fallback),
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

/**
 * AI sender label — 24dp circle avatar (`primaryContainer` background, sparkle icon
 * in `primary`) + "AI-ассистент" labelSmall. Sits directly above the assistant bubble.
 */
@Composable
private fun AiSenderLabel(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .padding(0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(24.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(Res.string.chat_assistant_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Icon-only button for the action row under assistant bubbles. 32dp tap target,
 * 18dp icon, `onSurfaceVariant` tint. Matches the M3 chat action-row spec.
 */
@Composable
private fun MessageActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(32.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
