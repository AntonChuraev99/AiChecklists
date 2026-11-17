package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.onboarding_talk_greeting
import aichecklists.core.designsystem.generated.resources.onboarding_talk_greeting_hint_call
import aichecklists.core.designsystem.generated.resources.onboarding_talk_greeting_hint_chat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.ChatInputLeading
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.ChatInputRow
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPresenceOrb
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.OrbMode
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.ChatSurfaceTone
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalChatSurfaceTone
import org.jetbrains.compose.resources.stringResource

/**
 * Gisti's opening line on Talk (DESIGN_SPEC 2026-11-17 §2.3 / §3.5): the presence orb beside an AI
 * bubble with the greeting and — at fontScale ≤ 1.3 — the hint of what the composer below can do.
 */
@Composable
internal fun GreetingRow(isCallAvailable: Boolean, showHint: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd), verticalAlignment = Alignment.Top) {
        // The orb node is 52dp (halo); a 40dp layout box with the node overflowing it keeps the DISC
        // aligned to the bubble's top edge.
        Box(Modifier.size(TalkOrb).wrapContentSize(unbounded = true)) {
            GistiPresenceOrb(mode = OrbMode.Speak, level = { 0f }, size = TalkOrb, static = true)
        }
        Column(
            Modifier
                .widthIn(max = BubbleMaxWidth)
                .clip(AiBubbleShape)
                .background(AppChatColors.raised())
                .border(BorderStroke(1.dp, AppChatColors.contentOutline()), AiBubbleShape)
                .padding(AppDimens.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
        ) {
            Text(
                text = stringResource(Res.string.onboarding_talk_greeting),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (showHint) {
                Text(
                    text = stringResource(
                        if (isCallAvailable) {
                            Res.string.onboarding_talk_greeting_hint_call
                        } else {
                            Res.string.onboarding_talk_greeting_hint_chat
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A "Try asking" starter: an OUTLINED user-bubble (a filled one would read as "already sent"),
 * End-aligned by the host, ≥ 48dp tall, wrapping rather than clipping at large font scales.
 */
@Composable
internal fun StarterBubble(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(UserBubbleShape)
            .border(BorderStroke(1.dp, AppChatColors.controlOutline()), UserBubbleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = AppDimens.SpacingMd, vertical = AppDimens.SpacingSm),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

/**
 * The chat's own composer docked at the bottom of Talk (DESIGN_SPEC §3.2): [ChatInputRow] itself on
 * the bottom-chrome band — `[field · Call · Mic⇄Send]`, no help, no credits, no "+" — so the next
 * screen the user sees (the chat dock) has the same control in the same place. While the mic is held
 * the recording status sits above the pill.
 */
@Composable
internal fun TalkComposerPlate(
    text: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    isRecording: Boolean,
    isTranscribing: Boolean,
    showMic: Boolean,
    onSend: () -> Unit,
    onCall: (() -> Unit)?,
    onVoicePressStart: () -> Unit,
    onVoicePressEnd: (cancel: Boolean) -> Unit,
    maxPillWidth: Dp,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    var dragCancel by remember { mutableStateOf(false) }
    Box(
        // The plate owns the bottom nav-bar inset INSIDE its band (the host leaves it unconsumed), so
        // the system-nav zone is the same sand as the composer — one surface, like the chat dock.
        // With the keyboard up the host's imePadding() has consumed it and this collapses to 0.
        modifier = modifier.fillMaxWidth().background(AppSurface.bottomChrome()).navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        CompositionLocalProvider(LocalChatSurfaceTone provides ChatSurfaceTone.BottomChrome) {
            Column(Modifier.widthIn(max = maxPillWidth).fillMaxWidth()) {
                if (isRecording) {
                    RecordingStatus(
                        dragCancel = dragCancel,
                        modifier = Modifier.padding(
                            start = AppDimens.ScreenPaddingHorizontal + AppDimens.SpacingLg,
                            end = AppDimens.ScreenPaddingHorizontal,
                            top = AppDimens.SpacingMd,
                        ),
                    )
                }
                ChatInputRow(
                    text = text,
                    onTextChange = onTextChange,
                    onSend = onSend,
                    onAttachFileClick = {},
                    onVoiceRecordingStarted = onVoicePressStart,
                    onVoiceRecordingStopped = { onVoicePressEnd(false) },
                    onVoiceRecordingCancelled = { onVoicePressEnd(true) },
                    isEnabled = enabled,
                    canSend = text.isNotBlank(),
                    isRecording = isRecording,
                    isTranscribing = isTranscribing,
                    onDragCancelChanged = { dragCancel = it },
                    focusRequester = focusRequester,
                    onFocusChanged = onFocusChanged,
                    leading = ChatInputLeading.None,
                    onCallClick = onCall,
                    attachVisible = false,
                    micVisible = showMic,
                    modifier = Modifier.testTag("onboarding_talk_composer"),
                )
            }
        }
    }
}

/** The chat's AI bubble: 20dp corners with the tail corner at the top-start, by the orb. */
private val AiBubbleShape: Shape = RoundedCornerShape(
    topStart = AppShapeTokens.ChatBubbleTail,
    topEnd = 20.dp,
    bottomEnd = 20.dp,
    bottomStart = 20.dp,
)

/** The chat's user bubble: 20dp corners with the tail at the bottom-end. */
private val UserBubbleShape: Shape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomEnd = AppShapeTokens.ChatBubbleTail,
    bottomStart = 20.dp,
)

private val TalkOrb = 40.dp
private val BubbleMaxWidth = 520.dp
