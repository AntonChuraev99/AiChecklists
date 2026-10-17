package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_ask_ai_fallback
import aichecklists.core.designsystem.generated.resources.chat_message_copy
import aichecklists.core.designsystem.generated.resources.chat_message_show_actions
import aichecklists.core.designsystem.generated.resources.chat_message_thumb_down
import aichecklists.core.designsystem.generated.resources.chat_message_thumb_up
import aichecklists.core.designsystem.generated.resources.chat_open_checklist
import aichecklists.core.designsystem.generated.resources.chat_paywall_cta_credits
import aichecklists.core.designsystem.generated.resources.chat_retry
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.SparkleTile
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceRole
import org.jetbrains.compose.resources.stringResource

/**
 * Geometry of the assistant "avatar beside the bubble" layout (redesign B §2.9), shared by every
 * assistant-side turn — [ChatMessageBubble], [AiChoiceResponse], [ChatTypingIndicator] — so the
 * bubbles of one run line up on one start edge whether or not they carry the avatar.
 */
internal object ChatAvatarLayout {
    /** The sparkle avatar's diameter. */
    val AvatarSize: Dp = 28.dp

    /** Avatar → bubble gap. */
    val Gap: Dp = 8.dp

    /** Start inset of every assistant bubble: avatar slot + gap (36dp). */
    val Slot: Dp = AvatarSize + Gap
}

/** Assistant bubble: 20/20/20 with the [AppShapeTokens.ChatBubbleTail] at the bottom-start (by the avatar). */
internal val AssistantBubbleShape: Shape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomEnd = 20.dp,
    bottomStart = AppShapeTokens.ChatBubbleTail,
)

/** User bubble: 20/20/20 with the tail at the bottom-end. */
private val UserBubbleShape: Shape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomEnd = AppShapeTokens.ChatBubbleTail,
    bottomStart = 20.dp,
)

/**
 * The 28dp gradient sparkle avatar beside an assistant turn, or an equal-width spacer when this turn
 * is not the last of its run (so the bubbles of one run share a start edge). Decorative — the bubble
 * text is what the turn says.
 */
@Composable
internal fun AssistantAvatarSlot(showAvatar: Boolean, modifier: Modifier = Modifier) {
    if (showAvatar) {
        SparkleTile(
            size = ChatAvatarLayout.AvatarSize,
            cornerRadius = ChatAvatarLayout.AvatarSize / 2,
            modifier = modifier,
        )
    } else {
        Spacer(modifier = modifier.width(ChatAvatarLayout.AvatarSize))
    }
}

/**
 * Renders a single chat message bubble — redesign B "Soft & tactile" (§2.9).
 *
 * - **User (sent)** — right-aligned, `primaryContainer` fill, corners 20/20/8/20 (tail bottom-end).
 *   Max width 320dp. The cost badge sits under it.
 * - **Assistant (received)** — no sender label. `Row(Bottom)`: a 28dp sparkle avatar (only on the
 *   LAST message of a consecutive assistant run — [showAvatar]; otherwise a 28dp spacer keeps the
 *   column aligned) + 8dp + the bubble. Bubble = [AppChatColors.raised] + a 1dp
 *   [AppChatColors.contentOutline] hairline (plane-relative — the same bubble renders on the page and
 *   inside the bottom chrome), corners 20/20/20/8 (tail bottom-start). Max width 300dp.
 *   The action chips — Open checklist · Ask AI · Retry — and the Become Pro CTA render INSIDE the
 *   bubble under the text.
 *
 * Feedback row (Copy · 👍 · 👎, 44dp targets) under an assistant bubble is shown only for the
 * latest assistant reply ([isLatestAssistant]) or when the user revealed it on an older reply by
 * tapping the bubble ([actionsRevealed], toggled through [onToggleActions]). Long-press stays text
 * selection ([SelectionContainer]).
 *
 * @param showAvatar        Assistant only: draw the avatar (last message of a run). False → spacer.
 * @param isLatestAssistant Assistant only: this is the newest assistant reply → feedback row always.
 *                          Defaults to true so single-message hosts (the dock peek) keep the row.
 * @param actionsRevealed   Assistant only: the user tapped this older reply to show its actions.
 * @param onToggleActions   Assistant only: tap on an older bubble toggles [actionsRevealed]. Null →
 *                          the bubble is not clickable.
 * @param onPaywallCta Invoked by the "Become Pro" CTA shown inside an out-of-credits reply.
 * @param onRetry Invoked by the "Retry" chip on a recoverable-error reply (re-sends `retryText`).
 * @param attachedActions Assistant only: the actions of a blank-prompt pending choice that belongs to
 *                  THIS reply (D1 Undo / Move, AI answer options), rendered inside the bubble under
 *                  the divider — build it with [AttachedChoiceActions]. Null → the reply's own chips only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onFeedbackClick: ((ChatMessage) -> Unit)? = null,
    onThumbUpClick: ((ChatMessage) -> Unit)? = null,
    onOpenChecklist: (() -> Unit)? = null,
    onAskAiFallback: (() -> Unit)? = null,
    onPaywallCta: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    showAvatar: Boolean = false,
    isLatestAssistant: Boolean = true,
    actionsRevealed: Boolean = false,
    onToggleActions: (() -> Unit)? = null,
    attachedActions: (@Composable () -> Unit)? = null,
) {
    if (message.role == ChatRole.User) {
        UserBubble(message = message, modifier = modifier)
    } else {
        AssistantBubble(
            message = message,
            modifier = modifier,
            onFeedbackClick = onFeedbackClick,
            onThumbUpClick = onThumbUpClick,
            onOpenChecklist = onOpenChecklist,
            onAskAiFallback = onAskAiFallback,
            onPaywallCta = onPaywallCta,
            onRetry = onRetry,
            showAvatar = showAvatar,
            showActions = isLatestAssistant || actionsRevealed,
            onToggleActions = if (isLatestAssistant) null else onToggleActions,
            attachedActions = attachedActions,
        )
    }
}

@Composable
private fun UserBubble(message: ChatMessage, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXxs),
        ) {
            // User (sent) keeps the `primaryContainer` accent fill — it reads as "my message", and it
            // is deliberately NOT plane-relative: it differs from the assistant bubble by hue and
            // alignment as well as by tone, so it is legible on either plane as it is.
            Surface(
                shape = UserBubbleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                // Attachments above text (M3 chat media-inset pattern).
                Column(modifier = Modifier.padding(vertical = 10.dp)) {
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
            }

            // Cost badge: shown only for user messages where credits were actually deducted (> 0).
            if (message.costCredits > 0) {
                InlineCostBadge(cost = message.costCredits)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantBubble(
    message: ChatMessage,
    modifier: Modifier,
    onFeedbackClick: ((ChatMessage) -> Unit)?,
    onThumbUpClick: ((ChatMessage) -> Unit)?,
    onOpenChecklist: (() -> Unit)?,
    onAskAiFallback: (() -> Unit)?,
    onPaywallCta: (() -> Unit)?,
    onRetry: (() -> Unit)?,
    showAvatar: Boolean,
    showActions: Boolean,
    onToggleActions: (() -> Unit)?,
    attachedActions: (@Composable () -> Unit)?,
) {
    val clipboardManager = LocalClipboardManager.current
    val showRetry = message.retryText != null && onRetry != null
    val ctaCredits = message.paywallCtaCredits
    val showCta = ctaCredits != null && onPaywallCta != null
    val hasChips = onOpenChecklist != null || onAskAiFallback != null || showRetry || showCta
    val showActionsLabel = stringResource(Res.string.chat_message_show_actions)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            AssistantAvatarSlot(showAvatar = showAvatar)
            Spacer(modifier = Modifier.width(ChatAvatarLayout.Gap))
            // Plane-relative fill + SOFT hairline: a bubble is content, not a target, and the same
            // bubble renders on the page (full ChatScreen) and inside the bottom chrome (dock /
            // overlay). See AppChatColors.
            Surface(
                shape = AssistantBubbleShape,
                color = AppChatColors.raised(),
                border = BorderStroke(1.dp, AppChatColors.contentOutline()),
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .then(
                        if (onToggleActions != null) {
                            Modifier.clickable(
                                onClickLabel = showActionsLabel,
                                onClick = onToggleActions,
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                // Hug the content (B mock): the markdown paragraphs fill their max width, so without
                // an intrinsic cap a one-line reply with a chip stretched the bubble to 300dp.
                Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                    SelectionContainer {
                        ChatMarkdownText(
                            markdown = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                    // Action chips INSIDE the bubble (B §2.9), under an inner divider (B pass 2,
                    // "actions inside the turn"): real AiChoiceChip buttons that hug their content,
                    // in a wrapping FlowRow. The CTA label is a full sentence, so maxLines = 2 lets
                    // a long localisation wrap instead of clipping. A blank-prompt choice that
                    // belongs to this reply (Undo / Move, AI answer options) renders right after,
                    // in the same actions area ([attachedActions]).
                    if (hasChips || attachedActions != null) {
                        HorizontalDivider(thickness = 1.dp, color = AppChatColors.contentOutline())
                    }
                    if (hasChips) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
                            modifier = Modifier.padding(
                                start = AppDimens.SpacingMd,
                                end = AppDimens.SpacingMd,
                                top = 10.dp,
                                bottom = if (attachedActions != null) 0.dp else AppDimens.SpacingMd,
                            ),
                        ) {
                            if (onOpenChecklist != null) {
                                AiChoiceChip(
                                    label = stringResource(Res.string.chat_open_checklist),
                                    role = ChoiceRole.Default,
                                    onClick = onOpenChecklist,
                                    leadingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                                    maxLines = 1,
                                )
                            }
                            // "Ask AI" — Unknown-intent dead-end. Tapping escalates the original text
                            // to Layer 3 (3 credits), an explicit opt-in — credits are never auto-burned.
                            if (onAskAiFallback != null) {
                                AiChoiceChip(
                                    label = stringResource(Res.string.chat_ask_ai_fallback),
                                    role = ChoiceRole.Default,
                                    onClick = onAskAiFallback,
                                    leadingIcon = Icons.Outlined.AutoAwesome,
                                    maxLines = 1,
                                )
                            }
                            // "Retry" — recoverable-error reply (offline / service / timeout, F1).
                            if (showRetry) {
                                AiChoiceChip(
                                    label = stringResource(Res.string.chat_retry),
                                    role = ChoiceRole.Default,
                                    onClick = onRetry,
                                    leadingIcon = Icons.Outlined.Refresh,
                                    maxLines = 1,
                                )
                            }
                            // "Become Pro" — out-of-credits reply (Premium daily allowance from RC).
                            if (showCta) {
                                AiChoiceChip(
                                    label = stringResource(
                                        Res.string.chat_paywall_cta_credits,
                                        ctaCredits.toString(),
                                    ),
                                    role = ChoiceRole.Primary,
                                    onClick = onPaywallCta,
                                    leadingIcon = Icons.Outlined.WorkspacePremium,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                    attachedActions?.invoke()
                }
            }
        }

        // Feedback row: Copy · ThumbUp · ThumbDown. ThumbUp = fire-and-forget analytics; ThumbDown
        // opens the feedback sheet. Start-aligned with the bubble text: the avatar slot (36) minus
        // 6dp — offset, not padding, keeps the 44dp targets' own inset doing the rest.
        if (showActions && (onFeedbackClick != null || onThumbUpClick != null)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = ChatAvatarLayout.Slot - 6.dp),
            ) {
                MessageActionButton(
                    icon = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(Res.string.chat_message_copy),
                    onClick = { clipboardManager.setText(AnnotatedString(message.content)) },
                )
                if (onThumbUpClick != null) {
                    MessageActionButton(
                        icon = Icons.Outlined.ThumbUp,
                        contentDescription = stringResource(Res.string.chat_message_thumb_up),
                        onClick = { onThumbUpClick(message) },
                    )
                }
                if (onFeedbackClick != null) {
                    MessageActionButton(
                        icon = Icons.Outlined.ThumbDown,
                        contentDescription = stringResource(Res.string.chat_message_thumb_down),
                        onClick = { onFeedbackClick(message) },
                    )
                }
            }
        }
    }
}

/**
 * Icon-only button for the action row under assistant bubbles. 44dp tap target (B §2.9, was 32 —
 * a11y), 18dp icon, `onSurfaceVariant` tint.
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
        modifier = modifier.size(44.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
