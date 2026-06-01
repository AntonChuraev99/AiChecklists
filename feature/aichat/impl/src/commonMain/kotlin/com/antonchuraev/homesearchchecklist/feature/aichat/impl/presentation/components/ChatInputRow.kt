package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_attach_file_action
import aichecklists.core.designsystem.generated.resources.chat_features_help_action
import aichecklists.core.designsystem.generated.resources.chat_input_placeholder
import aichecklists.core.designsystem.generated.resources.chat_input_placeholder_with_attachment
import aichecklists.core.designsystem.generated.resources.chat_record_voice
import aichecklists.core.designsystem.generated.resources.chat_send_action
import aichecklists.core.designsystem.generated.resources.chat_voice_press_hold_hint
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom input row for the AI Chat screen — Phase 3 redesign.
 *
 * Layout: `[AttachFile IconButton] · [AppTextField weight=1f] · [Mic OR Send FilledIconButton]`
 *
 * Icon-swap rule: trailing button morphs between [Icons.Filled.Mic] (canSend=false)
 * and [Icons.AutoMirrored.Filled.Send] (canSend=true) via [Crossfade] (200ms).
 * Single trailing button = primary action (WhatsApp / Telegram / iMessage pattern).
 *
 * Mic press-and-hold via [awaitEachGesture]:
 * - Finger down → [onVoiceRecordingStarted]
 * - Drag-up > 80dp → [onDragCancelChanged](true) → overlay shows "Slide up to cancel"
 * - Release without significant drag → [onVoiceRecordingStopped] (normal stop)
 * - Release after drag > 80dp → [onVoiceRecordingCancelled] (cancel)
 *
 * @param text                      Current input text.
 * @param onTextChange              Called on every keystroke.
 * @param onSend                    Called when Send button or IME Send action fires.
 * @param onAttachFileClick         Called when the leading AttachFile button is tapped.
 * @param onVoiceRecordingStarted   Called when the user presses-and-holds the mic.
 * @param onVoiceRecordingStopped   Called when the user releases the mic without cancel.
 * @param onVoiceRecordingCancelled Called when the user releases after drag-up > 80dp.
 * @param isEnabled                 When false, all interactive elements are disabled.
 * @param canSend                   True when text is non-blank OR pendingAttachments is non-empty.
 * @param isRecording               True while voice recording is in progress.
 * @param isTranscribing            True while audio is being sent to the transcription Cloud Function.
 *                                  Hides the mic/send button and shows a [CircularProgressIndicator]
 *                                  with a «Transcribing…» label. Input field is read-only in this state.
 * @param onDragCancelChanged       Reports drag-up cancel state to parent for overlay label.
 * @param focusRequester            When non-null, attached to the center [TextField] so a
 *                                  caller can programmatically request focus (e.g. auto-focus
 *                                  the input when the inline chat dock expands). The caller
 *                                  owns the [androidx.compose.runtime.LaunchedEffect] that calls
 *                                  `focusRequester.requestFocus()`; this component only wires
 *                                  the modifier.
 */
@Composable
fun ChatInputRow(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachFileClick: () -> Unit,
    onVoiceRecordingStarted: () -> Unit,
    onVoiceRecordingStopped: () -> Unit,
    onVoiceRecordingCancelled: () -> Unit,
    onHelpClick: () -> Unit = {},
    hasAttachments: Boolean = false,
    isEnabled: Boolean = true,
    canSend: Boolean = false,
    isRecording: Boolean = false,
    isTranscribing: Boolean = false,
    onDragCancelChanged: ((Boolean) -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    // Pre-resolve strings in Composable scope (spec §10 rule 6)
    val attachFileLabel = stringResource(Res.string.chat_attach_file_action)
    val helpLabel = stringResource(Res.string.chat_features_help_action)
    val recordVoiceLabel = stringResource(Res.string.chat_record_voice)
    val sendLabel = stringResource(Res.string.chat_send_action)
    val pressHoldHint = stringResource(Res.string.chat_voice_press_hold_hint)
    val placeholder = stringResource(
        if (hasAttachments) Res.string.chat_input_placeholder_with_attachment
        else Res.string.chat_input_placeholder
    )

    val isMic = !canSend

    // Track drag-cancel locally to drive the Crossfade icon state
    var isDragCancel by remember { mutableStateOf(false) }

    // Scale animation: 1.0f → 1.2f when recording (spec §2)
    val micIconScale by animateFloatAsState(
        targetValue = if (isRecording) 1.2f else 1.0f,
        animationSpec = spring(stiffness = 300f),
        label = "mic_icon_scale",
    )

    // Threshold in pixels — computed via density in the pointerInput block
    val cancelThresholdDp = 80.dp

    // Outer column applies the surface padding (M3 chat bar — `padding: 8px 16px 12px`).
    // Inner pill follows the AskGistiBar clean pattern: `surfaceContainerLowest` (white card
    // in light, near-black in dark) + a hairline `outlineVariant` border so the pill stays
    // visible on the white dock surface. Previously it was `surfaceContainerHigh` (grey tonal),
    // which is the "страшный серый" the user reported. 28dp radius, all 4 controls live inside.
    // minHeight 56dp lets the pill grow when text wraps to multiple lines.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(
                start = AppDimens.ScreenPaddingHorizontal,
                end = AppDimens.ScreenPaddingHorizontal,
                top = AppDimens.SpacingSm,
                bottom = AppDimens.SpacingMd,
            ),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
        // [1] Leading help button (Telegram-style — replaces emoji slot)
        IconButton(
            onClick = onHelpClick,
            enabled = isEnabled && !isRecording && !isTranscribing,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = helpLabel,
                modifier = Modifier.size(AppDimens.IconSizeMd),
                tint = if (isEnabled && !isRecording)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }

        // [2] Center text field — borderless Telegram-style.
        // Indicator + container colors are transparent so the field blends into the row;
        // placeholder swaps when attachments are pending.
        TextField(
            value = text,
            onValueChange = if (isRecording || isTranscribing) { _ -> } else onTextChange,
            placeholder = {
                androidx.compose.material3.Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            singleLine = false,
            maxLines = 4,
            enabled = isEnabled && !isRecording && !isTranscribing,
            modifier = Modifier
                .weight(1f)
                .let { base -> focusRequester?.let { base.focusRequester(it) } ?: base },
            keyboardOptions = KeyboardOptions(
                imeAction = if (canSend) ImeAction.Send else ImeAction.Default,
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
        )

        // [3] Attach file button (moved to trailing side, next to Mic/Send — Telegram pattern)
        IconButton(
            onClick = onAttachFileClick,
            enabled = isEnabled && !isRecording && !isTranscribing,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.AttachFile,
                contentDescription = attachFileLabel,
                modifier = Modifier.size(AppDimens.IconSizeMd),
                tint = if (isEnabled && !isRecording)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }

        // [4] Trailing Mic / Send / Transcribing indicator
        //
        // When isTranscribing=true, the entire trailing slot is replaced by a
        // CircularProgressIndicator + label row (Crossfade "transcribing" branch).
        // Mic / Send remain hidden until transcription completes.
        //
        // Press-and-hold gesture for the mic button.
        // Uses awaitEachGesture (low-level) to support drag-up cancel tracking.
        //
        // CRITICAL: pointerInput keys MUST be stable across `isRecording` flips.
        // Earlier we had `pointerInput(isMic, isRecording, cancelThresholdDp)` which
        // recreated the modifier the moment recording started — the in-flight gesture
        // coroutine was cancelled BEFORE the release event arrived, so onStop never
        // fired. Recovery: read `isRecording` via `rememberUpdatedState` so the
        // coroutine sees the latest value without recomposition tearing down the modifier.
        val isRecordingLatest by rememberUpdatedState(isRecording)
        val micGestureModifier = if (isMic || isRecording) {
            Modifier.pointerInput(Unit) {
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
                        onVoiceRecordingStopped()
                        return@awaitEachGesture
                    }

                    val downY = down.position.y
                    onVoiceRecordingStarted()
                    isDragCancel = false
                    onDragCancelChanged?.invoke(false)

                    val cancelThresholdPx = cancelThresholdDp.toPx()

                    // Track pointer movement until released
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        val dragY = change.position.y - downY
                        // Negative Y = upward drag
                        val newIsDragCancel = dragY < -cancelThresholdPx
                        if (newIsDragCancel != isDragCancel) {
                            isDragCancel = newIsDragCancel
                            onDragCancelChanged?.invoke(newIsDragCancel)
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
                    onDragCancelChanged?.invoke(false)
                    if (wasDragCancel) {
                        onVoiceRecordingCancelled()
                    } else {
                        onVoiceRecordingStopped()
                    }
                }
            }
        } else Modifier

        // Telegram-style trailing button — plain icon container.
        // CRITICAL: We use Box, NOT IconButton — IconButton internally applies
        // `clickable {}` which consumes down events before `pointerInput { awaitEachGesture }`
        // can see them. That collision is why press-and-hold mic was a silent no-op.
        // Solution: switch modifier based on mode — `clickable` for Send-tap,
        // `pointerInput` for Mic press-and-hold. Only one active at a time → no conflict.
        val interactionModifier = when {
            isTranscribing -> Modifier  // no interaction while transcription is in progress
            !isMic && !isRecording -> Modifier.clickable(enabled = isEnabled, onClick = onSend)
            else -> micGestureModifier  // Mic mode: press-and-hold gesture
        }
        Box(
            modifier = interactionModifier
                .size(48.dp)
                .clip(CircleShape)
                .semantics {
                    role = Role.Button
                    customActions = listOf(
                        CustomAccessibilityAction(
                            label = pressHoldHint,
                            action = {
                                if (isMic || isRecording) {
                                    onVoiceRecordingStarted()
                                } else {
                                    onSend()
                                }
                                true
                            }
                        )
                    )
                },
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            val crossfadeTarget = when {
                isTranscribing -> "transcribing"
                isRecording && isDragCancel -> "cancel"
                isMic || isRecording -> "mic"
                else -> "send"
            }
            // Icon tint: recording → error (red); idle mic → onSurfaceVariant; send → primary.
            val iconTint = when {
                isRecording -> MaterialTheme.colorScheme.error
                isMic -> MaterialTheme.colorScheme.onSurfaceVariant
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
        }  // end Row
        }  // end Surface
    }  // end Column
}

/**
 * Legacy overload for existing call sites that don't pass attachment/voice params.
 * Delegates to the full signature with no-op handlers.
 */
@Composable
fun ChatInputRow(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    ChatInputRow(
        text = text,
        onTextChange = onTextChange,
        onSend = onSend,
        onAttachFileClick = {},
        onVoiceRecordingStarted = {},
        onVoiceRecordingStopped = {},
        onVoiceRecordingCancelled = {},
        isEnabled = isEnabled,
        canSend = isEnabled && text.isNotBlank(),
        isRecording = false,
        modifier = modifier,
    )
}
