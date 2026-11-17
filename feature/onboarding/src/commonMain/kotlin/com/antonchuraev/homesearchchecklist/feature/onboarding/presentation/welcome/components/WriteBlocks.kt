package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_input_transcribing_placeholder
import aichecklists.core.designsystem.generated.resources.chat_voice_drag_cancel_hint
import aichecklists.core.designsystem.generated.resources.chat_voice_release_cancel_hint
import aichecklists.core.designsystem.generated.resources.onboarding_try_ai_error
import aichecklists.core.designsystem.generated.resources.onboarding_try_ai_error_sample
import aichecklists.core.designsystem.generated.resources.onboarding_try_ai_error_save
import aichecklists.core.designsystem.generated.resources.onboarding_try_ai_signin
import aichecklists.core.designsystem.generated.resources.onboarding_try_type_placeholder
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiMicSendButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.MicSendStyle
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapes
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.OnboardingAiError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The Write screen's hero input (DESIGN_SPEC 2026-11-17 §3.1): a multi-line card that clearly stands
 * out from the page (card fill, 1dp outline → 2dp primary while focused) with the shared
 * [GistiMicSendButton] in its toolbar. While the mic is held the text area is replaced IN PLACE by
 * the recording status (same height: the field stays laid out underneath, invisible); while the clip
 * is transcribed the field is read-only and its hint is the status.
 *
 * On focus the WHOLE card is brought into view, not just the caret line, so the toolbar never sits
 * under the keyboard (pass-1 finding at 320×640 × 1.5).
 *
 * @param showMic false (web without Google): no mic — the toolbar holds a Send that is disabled while
 *   the field is blank.
 */
@Composable
internal fun GoalComposer(
    text: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    isRecording: Boolean,
    isTranscribing: Boolean,
    showMic: Boolean,
    focusRequester: FocusRequester,
    onSend: () -> Unit,
    onVoicePressStart: () -> Unit,
    onVoicePressEnd: (cancel: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    var dragCancel by remember { mutableStateOf(false) }
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val editable = enabled && !isRecording && !isTranscribing

    // Same caret rule as ChatInputRow: an external change (chip prefill, transcript) lands the caret at
    // the END; plain typing round-trips the same string and never fights the cursor.
    var value by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    LaunchedEffect(text) {
        if (text != value.text) value = TextFieldValue(text, TextRange(text.length))
    }

    Surface(
        shape = AppShapeTokens.Group,
        color = AppSurface.card(),
        border = if (focused && editable) {
            BorderStroke(2.dp, scheme.primary)
        } else {
            BorderStroke(1.dp, AppChatColors.controlOutline())
        },
        modifier = modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView),
    ) {
        Column(
            Modifier.padding(
                start = AppDimens.SpacingLg,
                top = AppDimens.SpacingMd,
                end = AppDimens.SpacingXs,
                bottom = AppDimens.SpacingXs,
            ),
        ) {
            Box(Modifier.padding(end = AppDimens.SpacingMd)) {
                val textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (enabled || isTranscribing) scheme.onSurface else scheme.onSurface.copy(alpha = DISABLED_ALPHA),
                )
                BasicTextField(
                    value = value,
                    onValueChange = { next ->
                        if (!editable) return@BasicTextField
                        value = next
                        if (next.text != text) onTextChange(next.text)
                    },
                    enabled = enabled && !isRecording,
                    readOnly = isTranscribing,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(scheme.primary),
                    minLines = FIELD_MIN_LINES,
                    maxLines = FIELD_MAX_LINES,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    decorationBox = { inner ->
                        Box {
                            if (value.text.isEmpty()) {
                                Text(
                                    text = stringResource(
                                        if (isTranscribing) {
                                            Res.string.chat_input_transcribing_placeholder
                                        } else {
                                            Res.string.onboarding_try_type_placeholder
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isTranscribing) scheme.onSurface else scheme.onSurfaceVariant,
                                    modifier = if (isTranscribing) {
                                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                    } else {
                                        Modifier
                                    },
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isRecording) 0f else 1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            focused = it.isFocused
                            onFocusChanged(it.isFocused)
                            if (it.isFocused) scope.launch { bringIntoView.bringIntoView() }
                        }
                        // Web / desktop: Enter sends, Shift+Enter is a new line (DESIGN_SPEC §7).
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                                onSend()
                                true
                            } else {
                                false
                            }
                        }
                        .testTag("onboarding_write_field"),
                )
                if (isRecording) {
                    RecordingStatus(
                        dragCancel = dragCancel,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = AppDimens.MinTouchTarget),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A transcript can land on top of typed text: then the hint is hidden, so the status
                // sits in the toolbar beside the spinner instead.
                if (isTranscribing && value.text.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.chat_input_transcribing_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                GistiMicSendButton(
                    canSend = text.isNotBlank(),
                    onSend = onSend,
                    onVoiceRecordingStarted = onVoicePressStart,
                    onVoiceRecordingStopped = { onVoicePressEnd(false) },
                    onVoiceRecordingCancelled = { onVoicePressEnd(true) },
                    enabled = enabled,
                    isRecording = isRecording,
                    isTranscribing = isTranscribing,
                    onDragCancelChanged = { dragCancel = it },
                    sendOnly = !showMic,
                    style = MicSendStyle.Disc,
                    modifier = Modifier.testTag("onboarding_write_mic_send"),
                )
            }
        }
    }
}

/**
 * The recording state of a composer (DESIGN_SPEC §3.1): a pulsing error dot, the elapsed time and
 * the "slide up to cancel" hint (→ "release to cancel" once armed). The timer is wall-clock seconds
 * (`delay`) — it measures the recording, not an animation.
 */
@Composable
internal fun RecordingStatus(dragCancel: Boolean, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            seconds++
        }
    }
    val dotAlpha = if (reduced) {
        1f
    } else {
        val pulse = rememberInfiniteTransition(label = "rec_dot")
        pulse.animateFloat(
            initialValue = 1f,
            targetValue = DOT_PULSE_MIN,
            animationSpec = infiniteRepeatable(tween(DOT_PULSE_MS), RepeatMode.Reverse),
            label = "rec_dot_alpha",
        ).value
    }
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
    ) {
        Box(
            Modifier
                .size(RecordingDot)
                .graphicsLayer { alpha = dotAlpha }
                .clip(CircleShape)
                .background(scheme.error)
                .clearAndSetSemantics {},
        )
        Text(
            text = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Row(
            Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!dragCancel) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = stringResource(
                    if (dragCancel) Res.string.chat_voice_release_cancel_hint else Res.string.chat_voice_drag_cancel_hint,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (dragCancel) scheme.error else scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The AI-failure box under the Write composer: recessed fill (not `errorContainer` — blue text buttons
 * on pink make three palettes in one box), an error-coloured icon and two AI-free ways forward.
 * liveRegion polite.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiErrorBox(
    error: OnboardingAiError,
    enabled: Boolean,
    onSample: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.medium)
            .background(AppSurface.recessed())
            .padding(start = AppDimens.SpacingMd, top = AppDimens.SpacingMd, end = AppDimens.SpacingMd, bottom = AppDimens.SpacingXs)
            .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    ) {
        Icon(
            Icons.Outlined.SyncProblem,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    when (error) {
                        OnboardingAiError.Unavailable -> Res.string.onboarding_try_ai_error
                        OnboardingAiError.SignInRequired -> Res.string.onboarding_try_ai_signin
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // The text buttons carry 16dp of their own inset; pull the row back so their LABELS line
            // up with the message above. `offset` follows the layout direction (RTL-safe).
            FlowRow(Modifier.padding(top = AppDimens.SpacingXxs).offset(x = -ErrorActionInset)) {
                AppButtonText(
                    stringResource(Res.string.onboarding_try_ai_error_sample),
                    onSample,
                    enabled = enabled,
                    modifier = Modifier.testTag("onboarding_write_error_sample"),
                )
                AppButtonText(
                    stringResource(Res.string.onboarding_try_ai_error_save),
                    onSave,
                    enabled = enabled,
                    modifier = Modifier.testTag("onboarding_write_error_save"),
                )
            }
        }
    }
}

private const val FIELD_MIN_LINES = 3
private const val FIELD_MAX_LINES = 6
private const val DISABLED_ALPHA = 0.38f
private const val DOT_PULSE_MIN = 0.4f
private const val DOT_PULSE_MS = 800
private val RecordingDot = 10.dp
private val ErrorActionInset = 16.dp
