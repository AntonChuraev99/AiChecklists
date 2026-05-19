package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_recording_in_progress
import aichecklists.core.designsystem.generated.resources.chat_voice_drag_cancel_hint
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Recording overlay shown above [ChatAttachmentChipStrip] and [ChatInputRow]
 * while [isRecording] is true.
 *
 * Uses [AnimatedVisibility] to slide in from the bottom when recording starts
 * and slide out when it stops. The overlay surface uses [errorContainer] —
 * universal "recording" signal across Android/iOS.
 *
 * Three pulsing dots are rendered inline (NOT the full [ChatTypingIndicator]
 * bubble since we need just the dot row inside a custom surface).
 *
 * The Surface carries [liveRegion = Polite] so TalkBack announces the changing
 * duration without interrupting the user.
 *
 * @param isRecording      Whether recording is in progress.
 * @param durationMs       Elapsed recording time in milliseconds (ticks every second).
 * @param isDragCancel     True when the user has dragged ≥80dp upward (cancel zone).
 */
@Composable
fun ChatRecordingOverlay(
    isRecording: Boolean,
    durationMs: Long,
    isDragCancel: Boolean,
    modifier: Modifier = Modifier,
) {
    // Format "M:SS" — e.g. "0:04", "1:23"
    val minutes = durationMs / 60000L
    val seconds = (durationMs / 1000L) % 60L
    val formattedDuration = "$minutes:${seconds.toString().padStart(2, '0')}"

    // Just "Recording…" — timer rendered separately on the right side (single source of truth).
    // Previously the format string baked in the timer ("Recording… %1$s") which caused
    // two timers to render side-by-side with the right-aligned Text below.
    val inProgressLabel = stringResource(Res.string.chat_recording_in_progress)
    val dragCancelLabel = stringResource(Res.string.chat_voice_drag_cancel_hint)

    AnimatedVisibility(
        visible = isRecording,
        enter = slideInVertically { fullHeight -> fullHeight } + fadeIn(),
        exit = slideOutVertically { fullHeight -> fullHeight } + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppDimens.ScreenPaddingHorizontal,
                    end = AppDimens.ScreenPaddingHorizontal,
                    bottom = AppDimens.SpacingSm,
                )
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = AppDimens.SpacingLg,
                    vertical = AppDimens.SpacingMd,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left: 3 pulsing dots + label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
                ) {
                    // 3 inline pulsing dots (same animation as TypingDot in ChatTypingIndicator)
                    RecordingDot(delayMs = 0)
                    RecordingDot(delayMs = 200)
                    RecordingDot(delayMs = 400)

                    Text(
                        text = if (isDragCancel) dragCancelLabel else inProgressLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(start = AppDimens.SpacingXs),
                    )
                }

                // Right: duration counter
                Text(
                    text = formattedDuration,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

/**
 * Single animated dot for [ChatRecordingOverlay].
 * Uses [errorContainer] background with [onErrorContainer] tint to fit the overlay surface.
 */
@Composable
private fun RecordingDot(delayMs: Int) {
    val transition = rememberInfiniteTransition(label = "rec_dot_alpha_$delayMs")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, delayMillis = delayMs),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rec_dot_alpha_anim_$delayMs",
    )

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .size(6.dp)
            .alpha(alpha),
    ) {}
}
