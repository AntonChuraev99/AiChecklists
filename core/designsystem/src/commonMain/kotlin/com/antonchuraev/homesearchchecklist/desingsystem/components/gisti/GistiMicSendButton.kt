package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_record_voice
import aichecklists.core.designsystem.generated.resources.chat_send_action
import aichecklists.core.designsystem.generated.resources.chat_voice_a11y_cancel
import aichecklists.core.designsystem.generated.resources.chat_voice_a11y_stop
import aichecklists.core.designsystem.generated.resources.chat_voice_press_hold_hint
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import org.jetbrains.compose.resources.stringResource

/** The look of a [GistiMicSendButton]. */
enum class MicSendStyle {
    /** A bare 24dp glyph — the chat composer ([ChatInputRow]) as it has always looked. */
    Bare,

    /**
     * A 36dp disc behind the glyph (onboarding Write composer, DESIGN_SPEC onboarding-steps §3.1):
     * recessed disc + mic, `primary` disc + arrow to send, `error` disc while recording. The disc
     * keeps ONE size in every state, so the composer never jumps when recording starts.
     */
    Disc,
}

/**
 * The trailing Mic ⇄ Send control of the Gisti composers, lifted out of [ChatInputRow] so the chat
 * and the onboarding Write composer share ONE press-and-hold gesture, one recording look and one set
 * of strings (DESIGN_SPEC onboarding-steps-2026-11-17 §3.2).
 *
 * - Mic (nothing to send): finger down → [onVoiceRecordingStarted]; a ≥80dp upward slide arms the
 *   cancel ([onDragCancelChanged]); release → [onVoiceRecordingStopped], or
 *   [onVoiceRecordingCancelled] when armed. A tap while already recording stops and keeps it.
 * - Send ([canSend]): a tap → [onSend].
 * - [isTranscribing]: a spinner, no interaction.
 * - [sendOnly]: never the mic — always Send, disabled while there is nothing to send (the chat's
 *   item-create dock, a composer without voice).
 *
 * @param enabled [MicSendStyle.Bare] keeps the chat's exact behaviour (the gesture is not gated, the
 *   Send tap is). [MicSendStyle.Disc] also gates the gesture, and keeps it alive while [isRecording]
 *   so the press that disabled the screen can still end.
 */
@Composable
fun GistiMicSendButton(
    canSend: Boolean,
    onSend: () -> Unit,
    onVoiceRecordingStarted: () -> Unit,
    onVoiceRecordingStopped: () -> Unit,
    onVoiceRecordingCancelled: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isRecording: Boolean = false,
    isTranscribing: Boolean = false,
    onDragCancelChanged: ((Boolean) -> Unit)? = null,
    sendOnly: Boolean = false,
    style: MicSendStyle = MicSendStyle.Bare,
) {
    val recordVoiceLabel = stringResource(Res.string.chat_record_voice)
    val sendLabel = stringResource(Res.string.chat_send_action)
    val pressHoldHint = stringResource(Res.string.chat_voice_press_hold_hint)
    val stopLabel = stringResource(Res.string.chat_voice_a11y_stop)
    val cancelLabel = stringResource(Res.string.chat_voice_a11y_cancel)

    val isMic = !canSend && !sendOnly
    val disc = style == MicSendStyle.Disc

    // Track drag-cancel locally to drive the Crossfade icon state
    var isDragCancel by remember { mutableStateOf(false) }

    // Scale animation: 1.0f → 1.2f when recording (chat spec §2). The disc look keeps one size.
    val micIconScale by animateFloatAsState(
        targetValue = if (isRecording && !disc) 1.2f else 1.0f,
        animationSpec = spring(stiffness = 300f),
        label = "mic_icon_scale",
    )

    // Threshold in pixels — computed via density in the pointerInput block
    val cancelThresholdDp = 80.dp

    // CRITICAL: pointerInput keys MUST be stable across `isRecording` flips.
    // Earlier we had `pointerInput(isMic, isRecording, cancelThresholdDp)` which
    // recreated the modifier the moment recording started — the in-flight gesture
    // coroutine was cancelled BEFORE the release event arrived, so onStop never
    // fired. Recovery: read `isRecording` via `rememberUpdatedState` so the
    // coroutine sees the latest value without recomposition tearing down the modifier.
    val isRecordingLatest by rememberUpdatedState(isRecording)
    val onStartedLatest by rememberUpdatedState(onVoiceRecordingStarted)
    val onStoppedLatest by rememberUpdatedState(onVoiceRecordingStopped)
    val onCancelledLatest by rememberUpdatedState(onVoiceRecordingCancelled)
    val onDragCancelLatest by rememberUpdatedState(onDragCancelChanged)
    // Live layout coordinates of the mic Box, kept fresh as the node moves. On record-start the
    // field is disabled → the keyboard closes and, on the home dock, the dock auto-collapses
    // Expanded→Peek — both slide this node DOWN under a stationary finger. Read ONLY inside the
    // gesture coroutine (never in composition), so onGloballyPositioned updating it never recomposes.
    var micLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val gestureActive = if (disc) (isMic && enabled) || isRecording else isMic || isRecording
    val micGestureModifier = if (gestureActive) {
        Modifier
            .onGloballyPositioned { micLayoutCoordinates = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()

                    // Tap while already recording → finish & save (NOT cancel).
                    // Previously this path landed in audioRecorder.start() which
                    // delete()'d the existing file — entire recording lost on second tap.
                    if (isRecordingLatest) {
                        down.consume()
                        // Wait for release so we don't process another down in the same gesture
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            change.consume()
                        } while (true)
                        onStoppedLatest()
                        return@awaitEachGesture
                    }

                    // Cancel-vs-commit is measured in WINDOW (root) coordinates, NOT the mic Box's
                    // LOCAL frame. When the node slides down under a STILL finger (keyboard close /
                    // dock Expanded→Peek), local coords read that shift as a phantom upward drag, so
                    // a stationary release wrongly CANCELS — the "release discards the recording" bug.
                    // localToWindow maps each pointer sample to the finger's absolute screen position,
                    // which does not move, so node movement contributes 0 to dragY: a still finger
                    // SAVES; only a genuine ≥80dp upward slide cancels. This makes the fix independent
                    // of node movement, so it holds on every host (dock peek, full screen, full overlay)
                    // with no inset-freeze needed.
                    fun windowY(local: Offset): Float =
                        micLayoutCoordinates?.localToWindow(local)?.y ?: local.y

                    val downWindowY = windowY(down.position)
                    onStartedLatest()
                    isDragCancel = false
                    onDragCancelLatest?.invoke(false)

                    val cancelThresholdPx = cancelThresholdDp.toPx()

                    // Track pointer movement until released
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        val dragY = windowY(change.position) - downWindowY
                        // Negative Y = upward finger movement in window space
                        val newIsDragCancel = dragY < -cancelThresholdPx
                        if (newIsDragCancel != isDragCancel) {
                            isDragCancel = newIsDragCancel
                            onDragCancelLatest?.invoke(newIsDragCancel)
                        }
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()
                    } while (true)

                    // On release: cancel or normal stop
                    val wasDragCancel = isDragCancel
                    isDragCancel = false
                    onDragCancelLatest?.invoke(false)
                    if (wasDragCancel) {
                        onCancelledLatest()
                    } else {
                        onStoppedLatest()
                    }
                }
            }
    } else {
        Modifier
    }

    // Plain icon container, NOT IconButton — IconButton internally applies `clickable {}` which
    // consumes down events before `pointerInput { awaitEachGesture }` can see them. That collision is
    // why press-and-hold mic was a silent no-op. `clickable` for Send-tap, `pointerInput` for the mic
    // press-and-hold; only one active at a time → no conflict.
    val interactionModifier = when {
        isTranscribing -> Modifier // no interaction while transcription is in progress
        // Send-only: always a Send tap, enabled only when there is something to send.
        sendOnly -> Modifier.clickable(enabled = enabled && canSend, onClick = onSend)
        !isMic && !isRecording -> Modifier.clickable(enabled = enabled, onClick = onSend)
        else -> micGestureModifier // Mic mode: press-and-hold gesture
    }
    Box(
        modifier = modifier
            .then(interactionModifier)
            .size(48.dp)
            .clip(CircleShape)
            .semantics {
                role = Role.Button
                // No actions while transcribing (the slot shows the progress spinner): a new
                // start() would delete the recording that is still being uploaded.
                customActions = when {
                    isTranscribing -> emptyList()
                    // Disc: a hold is not reachable by TalkBack, so the recording gets explicit
                    // stop / cancel actions instead of a second "start" (DESIGN_SPEC §8).
                    disc && isRecording -> listOf(
                        CustomAccessibilityAction(stopLabel) { onVoiceRecordingStopped(); true },
                        CustomAccessibilityAction(cancelLabel) { onVoiceRecordingCancelled(); true },
                    )
                    disc && isMic && !enabled -> emptyList()
                    else -> listOf(
                        CustomAccessibilityAction(
                            label = pressHoldHint,
                            action = {
                                if (isMic || isRecording) {
                                    onVoiceRecordingStarted()
                                } else {
                                    onSend()
                                }
                                true
                            },
                        ),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val crossfadeTarget = when {
            isTranscribing -> "transcribing"
            isRecording && isDragCancel -> "cancel"
            isMic || isRecording -> "mic"
            else -> "send"
        }
        if (disc) {
            DiscFace(
                target = crossfadeTarget,
                canSend = canSend,
                enabled = enabled,
                isRecording = isRecording,
                recordVoiceLabel = recordVoiceLabel,
                sendLabel = sendLabel,
            )
        } else {
            // Icon tint: recording → error (red); idle mic → onSurfaceVariant; send → primary.
            // Send-only greys out while blank (disabled) so the gating is visible.
            val iconTint = when {
                isRecording -> MaterialTheme.colorScheme.error
                isMic -> MaterialTheme.colorScheme.onSurfaceVariant
                sendOnly && !canSend -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                else -> MaterialTheme.colorScheme.primary
            }
            Crossfade(
                targetState = crossfadeTarget,
                animationSpec = tween(durationMillis = 200),
                label = "mic_send_crossfade",
            ) { iconState ->
                when (iconState) {
                    "transcribing" -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    "cancel" -> Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = recordVoiceLabel,
                        tint = iconTint,
                        modifier = Modifier
                            .size(AppDimens.IconSizeMd)
                            .scale(micIconScale),
                    )
                    "mic" -> Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = recordVoiceLabel,
                        tint = iconTint,
                        modifier = Modifier
                            .size(AppDimens.IconSizeMd)
                            .scale(micIconScale),
                    )
                    else -> Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = sendLabel,
                        tint = iconTint,
                        modifier = Modifier.size(AppDimens.IconSizeMd),
                    )
                }
            }
        }
    }
}

/** The [MicSendStyle.Disc] face: one 36dp disc whose fill and glyph follow the state. */
@Composable
private fun DiscFace(
    target: String,
    canSend: Boolean,
    enabled: Boolean,
    isRecording: Boolean,
    recordVoiceLabel: String,
    sendLabel: String,
) {
    val scheme = MaterialTheme.colorScheme
    val active = enabled || isRecording
    val fillTarget = when {
        !active -> scheme.onSurface.copy(alpha = DISABLED_CONTAINER_ALPHA)
        isRecording -> scheme.error
        target == "send" && canSend -> scheme.primary
        else -> AppSurface.recessed()
    }
    val glyphTarget = when {
        !active -> scheme.onSurface.copy(alpha = DISABLED_GLYPH_ALPHA)
        isRecording -> scheme.onError
        target == "send" && canSend -> scheme.onPrimary
        target == "send" -> scheme.onSurface.copy(alpha = DISABLED_GLYPH_ALPHA)
        else -> scheme.onSurface
    }
    val fill by animateColorAsState(fillTarget, AppMotion.effectsFastTween(), label = "mic_disc_fill")
    val glyph by animateColorAsState(glyphTarget, AppMotion.effectsFastTween(), label = "mic_disc_glyph")
    Box(
        modifier = Modifier.size(DiscSize).background(fill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = target, animationSpec = tween(durationMillis = 200), label = "mic_disc_crossfade") { state ->
            when (state) {
                "transcribing" -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = scheme.primary,
                )
                "cancel" -> Icon(Icons.Filled.Close, recordVoiceLabel, tint = glyph, modifier = Modifier.size(DiscGlyph))
                "mic" -> Icon(
                    imageVector = if (isRecording) Icons.Filled.Mic else Icons.Outlined.Mic,
                    contentDescription = recordVoiceLabel,
                    tint = glyph,
                    modifier = Modifier.size(DiscGlyph),
                )
                else -> Icon(Icons.Filled.ArrowUpward, sendLabel, tint = glyph, modifier = Modifier.size(DiscGlyph))
            }
        }
    }
}

private val DiscSize = AskPillDefaults.DiscSize
private val DiscGlyph = 20.dp
private const val DISABLED_GLYPH_ALPHA = 0.38f
private const val DISABLED_CONTAINER_ALPHA = 0.12f
