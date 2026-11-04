package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiAvatarTile
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.VoiceActionKind
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.VoiceActionUi
import kotlin.math.min

/**
 * Text that shows a window of at most [maxLines] lines anchored to the BOTTOM: the tail of a partial
 * utterance, or (with [anchorChar]) the line being spoken plus the next one. Older lines leave
 * upward under a 16dp fade. When the parent gives less height than the window, the bottom stays
 * visible and the top is cut — the current phrase is never the part that disappears.
 *
 * The window is computed in the layout phase from the text layout, so a moving [anchorChar] only
 * re-lays out this node.
 */
@Composable
fun BottomWindowText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    maxLines: Int,
    modifier: Modifier = Modifier,
    anchorChar: Int? = null,
    textAlign: TextAlign = TextAlign.Start,
) {
    val layoutHolder = remember { arrayOfNulls<TextLayoutResult>(1) }
    val fadeFromTop = remember { mutableIntStateOf(0) }
    Box(
        modifier = modifier
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (fadeFromTop.intValue > 0) {
                    val fade = VoiceCallDefaults.TranscriptFade.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(0f to Color.Transparent, fade / size.height to Color.Black),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            textAlign = textAlign,
            onTextLayout = { layoutHolder[0] = it },
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
                val result = layoutHolder[0]
                if (result == null || result.lineCount == 0) {
                    val h = min(placeable.height, constraints.maxHeight)
                    return@layout layout(placeable.width, h) { placeable.place(0, h - placeable.height) }
                }
                val n = result.lineCount
                val anchorLine = anchorChar
                    ?.let { result.getLineForOffset(it.coerceIn(0, text.length)) }
                    ?: (n - 1)
                val window = transcriptWindow(n, anchorLine, maxLines)
                val start = window.first
                val top = result.getLineTop(start)
                val bottom = result.getLineBottom(window.last)
                val windowH = (bottom - top).toInt()
                val h = min(windowH, constraints.maxHeight)
                // A cut top (window start > 0, or the parent gave less than the window) gets the fade.
                fadeFromTop.intValue = if (start > 0 || h < windowH) 1 else 0
                val y = -(bottom.toInt() - h)
                layout(placeable.width, h) { placeable.place(0, y) }
            },
        )
    }
}

/** Agent reply as captions: spoken part `onSurface`, the rest `onSurfaceVariant`, centred. */
@Composable
fun ReplyCaptions(
    spoken: String,
    pending: String,
    modifier: Modifier = Modifier,
    maxLines: Int = CAPTION_MAX_LINES,
) {
    val spokenColor = MaterialTheme.colorScheme.onSurface
    val pendingColor = MaterialTheme.colorScheme.onSurfaceVariant
    val text = remember(spoken, pending, spokenColor, pendingColor) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = spokenColor)) { append(spoken) }
            withStyle(SpanStyle(color = pendingColor)) { append(pending) }
        }
    }
    BottomWindowText(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = Color.Unspecified,
        maxLines = maxLines,
        anchorChar = spoken.length,
        textAlign = TextAlign.Center,
        // Not a live region: TTS already says it, and a growing text would be re-announced.
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken + pending },
    )
}

/**
 * The user's utterance as a chat-style bubble at the end edge. Partial: its tail (max 3 lines);
 * final: the full text, same look. Shape = the chat's user bubble (tail bottom-end).
 */
@Composable
fun UserPartialBubble(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        BottomWindowText(
            text = AnnotatedString(text),
            style = MaterialTheme.typography.bodyLarge,
            color = AppChatColors.onUserBubble(),
            maxLines = PARTIAL_MAX_LINES,
            modifier = Modifier
                .maxWidthFraction(BUBBLE_MAX_WIDTH)
                .background(AppChatColors.userBubble(), UserBubbleShape)
                .padding(horizontal = AppDimens.SpacingMd + 2.dp, vertical = AppDimens.SpacingSm + 2.dp),
        )
    }
}

/** Caps the width at [fraction] of the incoming max while still hugging short content. */
private fun Modifier.maxWidthFraction(fraction: Float): Modifier = layout { measurable, constraints ->
    val cap = if (constraints.hasBoundedWidth) (constraints.maxWidth * fraction).toInt() else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = cap))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/**
 * One thing Gisti did. The whole card is the target (D1: opening the object ends the call);
 * TalkBack reads one merged label that says so.
 *
 * @param compact `fontScale ≥ 1.5` on a < 360dp window: kind and title on one line each.
 */
@Composable
fun VoiceActionCard(
    action: VoiceActionUi,
    kindLabel: String,
    a11yLabel: String,
    compact: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clearAndSetSemantics {
                contentDescription = a11yLabel
                if (onClick != null) role = Role.Button
            },
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = AppDimens.SpacingMd,
            end = AppDimens.SpacingSm,
            top = AppDimens.SpacingMd,
            bottom = AppDimens.SpacingMd,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        ) {
            ActionTile(action)
            Column(Modifier.weight(1f)) {
                Text(
                    text = kindLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onClick != null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(AppDimens.IconSizeMd),
                )
            }
        }
    }
}

@Composable
private fun ActionTile(action: VoiceActionUi) {
    if (action.kind == VoiceActionKind.TaskAdded) {
        Box(
            modifier = Modifier
                .size(VoiceCallDefaults.ActionTileSize)
                .background(GistiColors.successContainer, RoundedCornerShape(VoiceCallDefaults.ActionTileCorner)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.TaskAlt,
                contentDescription = null,
                tint = GistiColors.onSuccessContainer,
                modifier = Modifier.size(AppDimens.IconSizeSm + 4.dp),
            )
        }
    } else {
        GistiAvatarTile(
            seed = (action.checklistId ?: action.id).hashCode().toLong(),
            label = action.title,
            size = VoiceCallDefaults.ActionTileSize,
            cornerRadius = VoiceCallDefaults.ActionTileCorner,
        )
    }
}

/** The chat's user bubble: 20dp corners, the tail corner bottom-end. */
private val UserBubbleShape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomEnd = AppShapeTokens.ChatBubbleTail,
    bottomStart = 20.dp,
)

private const val CAPTION_MAX_LINES = 4
private const val PARTIAL_MAX_LINES = 3
private const val BUBBLE_MAX_WIDTH = 0.85f
