package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_typing_indicator_a11y
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Animated typing indicator — three pulsing dots inside an assistant-style bubble.
 *
 * Rendered as the first item (visually bottom-most) in the chat [LazyColumn] when
 * `state.isProcessing == true && state.pendingPreview == null`, to signal the AI is
 * working during Layer 1→2→3 round-trip.
 *
 * Design:
 * - Left-aligned row (mirrors assistant bubble alignment)
 * - Bubble uses [AppChatColors.raised] + a soft [AppChatColors.contentOutline] hairline — the SAME
 *   pair as [ChatMessageBubble]'s assistant side, because the real answer replaces this block in
 *   place and any drift would show as the bubble changing colour the moment the text arrives
 * - Asymmetric tail corner: bottomStart=4dp (matches assistant bubble shape)
 * - 3 dots, 6dp diameter, 4dp gap between them
 * - Alpha animation: 0.3f → 1.0f, staggered 200ms between dots
 * - Animation period: 900ms total, repeating with RESTART
 *
 * Accessibility:
 * - Outer [Row] carries [contentDescription] for screen readers ("AI is thinking…")
 * - Decorative dots have no individual descriptions
 */
@Composable
fun ChatTypingIndicator(
    modifier: Modifier = Modifier,
) {
    val a11yLabel = stringResource(Res.string.chat_typing_indicator_a11y)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = a11yLabel },
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomEnd = 16.dp,
                bottomStart = 4.dp,
            ),
            // Same pair as ChatMessageBubble's received bubble, and it has to STAY the same pair:
            // this block is the placeholder that the real answer replaces in place, so any drift
            // shows up as the bubble changing colour the moment the text arrives. Content, not a
            // target → the soft hairline. See AppChatColors.
            color = AppChatColors.raised(),
            border = BorderStroke(1.dp, AppChatColors.contentOutline()),
            modifier = Modifier.widthIn(max = 80.dp),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = AppDimens.SpacingMd,
                    vertical = AppDimens.SpacingMd,
                ),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TypingDot(delayMs = 0)
                TypingDot(delayMs = 200)
                TypingDot(delayMs = 400)
            }
        }
    }
}

/**
 * Single animated dot for [ChatTypingIndicator].
 *
 * @param delayMs Phase offset in milliseconds for staggered animation.
 *                Dot 0 = 0ms, Dot 1 = 200ms, Dot 2 = 400ms.
 */
@Composable
private fun TypingDot(delayMs: Int) {
    val transition = rememberInfiniteTransition(label = "dot_alpha_$delayMs")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, delayMillis = delayMs),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot_alpha_anim_$delayMs",
    )

    Surface(
        shape = CircleShape,
        // The dot is ink on the bubble, so it takes a CONTENT role for whatever the bubble resolved
        // to. (The old comment claimed a `surfaceContainerHigh` pairing; that fill has not been the
        // bubble's since the clean-bubble change, and the role below never matched it.)
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .size(6.dp)
            .alpha(alpha),
    ) {}
}
