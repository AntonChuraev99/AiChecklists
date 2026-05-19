package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
import aichecklists.core.designsystem.generated.resources.chat_input_placeholder
import aichecklists.core.designsystem.generated.resources.chat_record_voice
import aichecklists.core.designsystem.generated.resources.chat_send_action
import aichecklists.core.designsystem.generated.resources.chat_voice_press_hold_hint
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
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
 * @param onDragCancelChanged       Reports drag-up cancel state to parent for overlay label.
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
    isEnabled: Boolean = true,
    canSend: Boolean = false,
    isRecording: Boolean = false,
    onDragCancelChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Pre-resolve strings in Composable scope (spec §10 rule 6)
    val attachFileLabel = stringResource(Res.string.chat_attach_file_action)
    val recordVoiceLabel = stringResource(Res.string.chat_record_voice)
    val sendLabel = stringResource(Res.string.chat_send_action)
    val pressHoldHint = stringResource(Res.string.chat_voice_press_hold_hint)

    val isMic = !canSend

    // Track drag-cancel locally to drive the Crossfade icon state
    var isDragCancel by remember { mutableStateOf(false) }

    // Animated mic button colors (spec §1, §2)
    val micContainerTarget: Color = when {
        isRecording -> MaterialTheme.colorScheme.errorContainer
        !isMic -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val micContainerColor by animateColorAsState(
        targetValue = micContainerTarget,
        animationSpec = tween(durationMillis = 150),
        label = "mic_container_color",
    )
    val micContentTarget: Color = when {
        isRecording -> MaterialTheme.colorScheme.onErrorContainer
        !isMic -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val micContentColor by animateColorAsState(
        targetValue = micContentTarget,
        animationSpec = tween(durationMillis = 150),
        label = "mic_content_color",
    )

    // Scale animation: 1.0f → 1.2f when recording (spec §2)
    val micIconScale by animateFloatAsState(
        targetValue = if (isRecording) 1.2f else 1.0f,
        animationSpec = spring(stiffness = 300f),
        label = "mic_icon_scale",
    )

    // Threshold in pixels — computed via density in the pointerInput block
    val cancelThresholdDp = 80.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(
                horizontal = AppDimens.ScreenPaddingHorizontal,
                vertical = AppDimens.SpacingMd,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
    ) {
        // [1] Leading attach button
        IconButton(
            onClick = onAttachFileClick,
            enabled = isEnabled && !isRecording,
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

        // [2] Center text field
        AppTextField(
            value = text,
            onValueChange = if (isRecording) { _ -> } else onTextChange,
            placeholder = stringResource(Res.string.chat_input_placeholder),
            singleLine = false,
            maxLines = 4,
            enabled = isEnabled && !isRecording,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(
                imeAction = if (canSend) ImeAction.Send else ImeAction.Default,
            ),
        )

        // [3] Trailing Mic / Send FilledIconButton
        // Press-and-hold gesture for the mic button.
        // Uses awaitEachGesture (low-level) to support drag-up cancel tracking.
        val micGestureModifier = if (isMic || isRecording) {
            Modifier.pointerInput(isMic, isRecording, cancelThresholdDp) {
                awaitEachGesture {
                    val down = awaitFirstDown()
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

        val onButtonClick: () -> Unit = if (!isMic && !isRecording) onSend else ({})
        FilledIconButton(
            onClick = onButtonClick,
            enabled = isEnabled,
            modifier = micGestureModifier
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
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = micContainerColor,
                contentColor = micContentColor,
            ),
        ) {
            val crossfadeTarget = when {
                isRecording && isDragCancel -> "cancel"
                isMic || isRecording -> "mic"
                else -> "send"
            }
            Crossfade(
                targetState = crossfadeTarget,
                animationSpec = tween(durationMillis = 200),
                label = "mic_send_crossfade",
            ) { iconState ->
                when (iconState) {
                    "cancel" -> Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = recordVoiceLabel,
                        modifier = Modifier
                            .size(AppDimens.IconSizeMd)
                            .scale(micIconScale),
                    )
                    "mic" -> Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = recordVoiceLabel,
                        modifier = Modifier
                            .size(AppDimens.IconSizeMd)
                            .scale(micIconScale),
                    )
                    else -> Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = sendLabel,
                        modifier = Modifier.size(AppDimens.IconSizeMd),
                    )
                }
            }
        }
    }
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
