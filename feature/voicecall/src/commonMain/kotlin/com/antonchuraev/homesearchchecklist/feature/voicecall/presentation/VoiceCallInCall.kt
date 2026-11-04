package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.voice_call_action_cancel_turn
import aichecklists.core.designsystem.generated.resources.voice_call_action_get_premium
import aichecklists.core.designsystem.generated.resources.voice_call_action_resume
import aichecklists.core.designsystem.generated.resources.voice_call_banner_limit_daily
import aichecklists.core.designsystem.generated.resources.voice_call_banner_limit_free
import aichecklists.core.designsystem.generated.resources.voice_call_banner_reconnecting
import aichecklists.core.designsystem.generated.resources.voice_call_banner_reconnecting_a11y
import aichecklists.core.designsystem.generated.resources.voice_call_control_captions
import aichecklists.core.designsystem.generated.resources.voice_call_control_captions_state_off
import aichecklists.core.designsystem.generated.resources.voice_call_control_captions_state_on
import aichecklists.core.designsystem.generated.resources.voice_call_control_end
import aichecklists.core.designsystem.generated.resources.voice_call_control_end_a11y
import aichecklists.core.designsystem.generated.resources.voice_call_control_mic
import aichecklists.core.designsystem.generated.resources.voice_call_control_mic_state_off
import aichecklists.core.designsystem.generated.resources.voice_call_control_mic_state_on
import aichecklists.core.designsystem.generated.resources.voice_call_orb_finish_a11y
import aichecklists.core.designsystem.generated.resources.voice_call_orb_finish_hint_a11y
import aichecklists.core.designsystem.generated.resources.voice_call_orb_interrupt_a11y
import aichecklists.core.designsystem.generated.resources.voice_call_speed_normal
import aichecklists.core.designsystem.generated.resources.voice_call_speed_slower
import aichecklists.core.designsystem.generated.resources.voice_call_speed_title
import aichecklists.core.designsystem.generated.resources.voice_call_timer_a11y
import aichecklists.core.designsystem.generated.resources.voice_call_timer_left
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppSoftIconButton
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppElevation
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components.CallControlDock
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components.CallDockLabels
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components.CallTimerPill
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components.GistiPresenceOrb
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components.ReplyCaptions
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components.StagePlan
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components.UserPartialBubble
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components.VoiceActionCard
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components.VoiceCallBanner
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components.VoiceCallDefaults
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components.orbDiameter
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components.planStage
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The running call (DESIGN_SPEC §1). Portrait: top row → banner → stage (layout budget) → dock.
 * Landscape with height < 480dp: orb column (30 %) · content · vertical dock rail.
 *
 * @param compactCards `fontScale ≥ 1.5` on a < 360dp window: the action card goes one line per text.
 */
@Composable
internal fun InCallContent(
    state: VoiceCallUiState.InCall,
    onAction: (VoiceCallAction) -> Unit,
    micLevel: () -> Float,
    voiceLevel: () -> Float,
    landscape: Boolean,
    compactCards: Boolean,
    modifier: Modifier = Modifier,
) {
    // The card that was on screen when the call UI appeared does not "appear" again (rotation).
    var seenActionId by rememberSaveable { mutableStateOf(state.transcript.lastAction?.id) }
    val transcript: @Composable () -> Unit = {
        CallTranscript(
            state = state,
            compactCards = compactCards,
            seenActionId = seenActionId,
            onSeen = { seenActionId = it },
            onAction = onAction,
        )
    }
    val orb: @Composable () -> Unit = { CallOrb(state, onAction, micLevel, voiceLevel) }
    val title = statusTitle(state.phase)
    val hint = statusHint(state)
    val actions: @Composable () -> Unit = { StatusActions(state.phase, onAction) }
    val dock: @Composable (Boolean) -> Unit = { vertical ->
        CallControlDock(
            micOn = state.micOn,
            captionsOn = state.captionsOn,
            labels = dockLabels(),
            onMic = { onAction(VoiceCallAction.MicToggled) },
            onEnd = { onAction(VoiceCallAction.EndClicked) },
            onCaptions = { onAction(VoiceCallAction.CaptionsToggled) },
            vertical = vertical,
        )
    }

    if (landscape) {
        Row(modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxWidth(LANDSCAPE_ORB_FRACTION).fillMaxHeight(), Alignment.Center) {
                val density = LocalDensity.current
                val d = with(density) {
                    orbDiameter(
                        stageHeightPx = maxHeight.roundToPx(),
                        widthPx = maxWidth.roundToPx(),
                        gapPx = AppDimens.SpacingSm.roundToPx(),
                        haloExtent = VoiceCallDefaults.HaloExtent,
                        maxPx = VoiceCallDefaults.OrbMax.roundToPx(),
                    ).toDp()
                }
                Box(Modifier.size(maxOf(d, VoiceCallDefaults.DotSize) * VoiceCallDefaults.HaloExtent)) { orb() }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                CallTopRow(state, onAction)
                BannerSlot(state.banner, onAction)
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = AppDimens.SpacingLg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StatusBlock(title = title, hint = hint, dot = false, actions = actions)
                    Box(Modifier.weight(1f, fill = false)) { transcript() }
                }
            }
            dock(true)
        }
    } else {
        Column(modifier.fillMaxSize()) {
            CallTopRow(state, onAction)
            BannerSlot(state.banner, onAction)
            CallStage(
                hasHint = hint != null,
                status = { variant ->
                    StatusBlock(
                        title = title,
                        hint = hint.takeIf { variant == StatusVariant.StageWithHint },
                        dot = variant == StatusVariant.Dot,
                        actions = actions,
                    )
                },
                transcript = transcript,
                orb = orb,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = AppDimens.SpacingLg),
            )
            dock(false)
        }
    }
}

@Composable
private fun dockLabels() = CallDockLabels(
    mic = stringResource(Res.string.voice_call_control_mic),
    micOn = stringResource(Res.string.voice_call_control_mic_state_on),
    micOff = stringResource(Res.string.voice_call_control_mic_state_off),
    end = stringResource(Res.string.voice_call_control_end),
    endA11y = stringResource(Res.string.voice_call_control_end_a11y),
    captions = stringResource(Res.string.voice_call_control_captions),
    captionsOn = stringResource(Res.string.voice_call_control_captions_state_on),
    captionsOff = stringResource(Res.string.voice_call_control_captions_state_off),
)

// ── Z1 top row ──────────────────────────────────────────────────────────────

@Composable
private fun CallTopRow(state: VoiceCallUiState.InCall, onAction: (VoiceCallAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = VoiceCallDefaults.TopRowHeight)
            .padding(start = AppDimens.SpacingLg, end = AppDimens.SpacingSm, top = AppDimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val elapsed = formatCallClock(state.timer.elapsedSec)
        val remainingClock = state.timer.remainingSec?.let { formatCallClock(it) }
        val remaining = remainingClock?.let { stringResource(Res.string.voice_call_timer_left, it) }
        // TalkBack gets words ("1 minute 48 seconds"), not the visual clock it would read digit by digit.
        val elapsedSpoken = spokenDuration(state.timer.elapsedSec)
        val a11y = state.timer.remainingSec
            ?.let { stringResource(Res.string.voice_call_timer_a11y, elapsedSpoken, spokenDuration(it)) }
            ?: elapsedSpoken
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            CallTimerPill(
                elapsed = elapsed,
                remaining = remaining,
                live = state.timer.live,
                warning = state.timer.warning,
                a11y = a11y,
            )
        }
        SpeedMenu(state.rate, onAction)
    }
}

@Composable
private fun SpeedMenu(rate: VoiceSpeechRateUi, onAction: (VoiceCallAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val title = stringResource(Res.string.voice_call_speed_title)
    val normal = stringResource(Res.string.voice_call_speed_normal)
    val slower = stringResource(Res.string.voice_call_speed_slower)
    Box {
        AppSoftIconButton(
            icon = Icons.Outlined.Speed,
            contentDescription = "$title: ${if (rate == VoiceSpeechRateUi.Normal) normal else slower}",
            onClick = { open = true },
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = AppSurface.floating(),
            shadowElevation = AppElevation.shadowInLight(AppElevation.FloatingMenu),
            border = if (LocalIsDarkTheme.current) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        ) {
            listOf(VoiceSpeechRateUi.Normal to normal, VoiceSpeechRateUi.Slower to slower).forEach { (value, label) ->
                val selected = value == rate
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = if (selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onAction(VoiceCallAction.RateSelected(value))
                    },
                    modifier = Modifier.semantics {
                        role = Role.RadioButton
                        this.selected = selected
                    },
                )
            }
        }
    }
}

// ── Z2 banner ───────────────────────────────────────────────────────────────

@Composable
private fun BannerSlot(banner: VoiceCallBannerUi?, onAction: (VoiceCallAction) -> Unit) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = banner,
        contentKey = { it?.let { b -> b::class } },
        transitionSpec = {
            val fade = if (reduced) tween<Float>(AppMotion.ReducedMotionMillis) else AppMotion.effectsDefaultTween()
            (fadeIn(fade) togetherWith fadeOut(fade)).using(
                // Layout animation → tween, not spring (Motion.kt KDoc); reduced → no expand at all.
                SizeTransform(clip = false) { _, _ -> if (reduced) snap() else AppMotion.spatialFastTween() },
            )
        },
        label = "banner",
    ) { b ->
        if (b != null) {
            Box(Modifier.padding(start = AppDimens.SpacingLg, end = AppDimens.SpacingLg, top = AppDimens.SpacingXs)) {
                CallBanner(b, onAction)
            }
        } else {
            Box(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CallBanner(banner: VoiceCallBannerUi, onAction: (VoiceCallAction) -> Unit) {
    when (banner) {
        is VoiceCallBannerUi.Reconnecting -> VoiceCallBanner(
            icon = Icons.Outlined.WifiOff,
            text = stringResource(Res.string.voice_call_banner_reconnecting, banner.secondsLeft),
            a11yText = stringResource(Res.string.voice_call_banner_reconnecting_a11y),
        )
        is VoiceCallBannerUi.LimitWarning -> {
            val text = when (banner.plan) {
                VoicePlanUi.Free -> pluralStringResource(
                    Res.plurals.voice_call_banner_limit_free,
                    banner.minutesLeft,
                    banner.minutesLeft,
                )
                VoicePlanUi.Premium -> pluralStringResource(
                    Res.plurals.voice_call_banner_limit_daily,
                    banner.minutesLeft,
                    banner.minutesLeft,
                )
            }
            val free = banner.plan == VoicePlanUi.Free
            VoiceCallBanner(
                icon = Icons.Outlined.HourglassBottom,
                text = text,
                a11yText = text,
                actionLabel = if (free) stringResource(Res.string.voice_call_action_get_premium) else null,
                onAction = if (free) {
                    { onAction(VoiceCallAction.BannerPremiumClicked) }
                } else {
                    null
                },
            )
        }
    }
}

// ── Z3 stage ────────────────────────────────────────────────────────────────

private enum class StatusVariant { StageWithHint, StageNoHint, Dot }

private enum class StageSlot { Dot, Transcript, StatusHint, StatusNoHint, Orb }

/**
 * The layout budget of Z3 in ONE measure pass (DESIGN_SPEC §1.1): the transcript keeps its natural
 * height (capped so the dot-mode status always fits), the status keeps its title, the hint goes
 * first, and the orb takes whatever is left — or collapses into a 56dp dot by the title.
 *
 * The orb is composed once, in one slot, and only its measured box changes: a font-scale change or
 * a rotation never restarts its animations.
 */
@Composable
private fun CallStage(
    hasHint: Boolean,
    status: @Composable (StatusVariant) -> Unit,
    transcript: @Composable () -> Unit,
    orb: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dotContent: @Composable () -> Unit = remember(status) { { status(StatusVariant.Dot) } }
    val hintContent: @Composable () -> Unit = remember(status) { { status(StatusVariant.StageWithHint) } }
    val noHintContent: @Composable () -> Unit = remember(status) { { status(StatusVariant.StageNoHint) } }
    SubcomposeLayout(modifier) { constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val loose = Constraints(maxWidth = w)
        val gap = AppDimens.SpacingSm.roundToPx()

        val dot = subcompose(StageSlot.Dot, dotContent).first().measure(loose)
        val transcriptCap = (h - dot.height).coerceAtLeast(0)
        val tr = subcompose(StageSlot.Transcript, transcript).firstOrNull()
            ?.measure(Constraints(maxWidth = w, maxHeight = transcriptCap))
        val trH = tr?.height ?: 0

        val withHint = if (hasHint) subcompose(StageSlot.StatusHint, hintContent).first().measure(loose) else null
        var noHint: androidx.compose.ui.layout.Placeable? = null
        val plan = planStage(
            heightPx = h,
            widthPx = w,
            transcriptPx = trH,
            statusWithHintPx = withHint?.height,
            statusNoHintPx = {
                subcompose(StageSlot.StatusNoHint, noHintContent).first().measure(loose).also { noHint = it }.height
            },
            gapPx = gap,
            haloExtent = VoiceCallDefaults.HaloExtent,
            maxPx = VoiceCallDefaults.OrbMax.roundToPx(),
            minPx = VoiceCallDefaults.OrbMin.roundToPx(),
            hintThresholdPx = VoiceCallDefaults.OrbHintThreshold.roundToPx(),
        )
        when (plan) {
            is StagePlan.Orb -> {
                val statusP = if (plan.showHint) withHint!! else noHint!!
                val box = (plan.diameterPx * VoiceCallDefaults.HaloExtent).toInt()
                val orbP = subcompose(StageSlot.Orb, orb).first().measure(Constraints.fixed(box, box))
                val stageH = h - trH - statusP.height
                layout(w, h) {
                    orbP.place((w - box) / 2, ((stageH - box) / 2).coerceAtLeast(0))
                    statusP.place((w - statusP.width) / 2, stageH)
                    tr?.place(0, h - trH)
                }
            }
            StagePlan.Dot -> {
                val box = (VoiceCallDefaults.DotSize * VoiceCallDefaults.HaloExtent).roundToPx()
                val orbP = subcompose(StageSlot.Orb, orb).first().measure(Constraints.fixed(box, box))
                layout(w, h) {
                    dot.place(0, 0)
                    orbP.place(0, ((dot.height - box) / 2).coerceAtLeast(0))
                    tr?.place(0, dot.height)
                }
            }
        }
    }
}

@Composable
private fun CallOrb(
    state: VoiceCallUiState.InCall,
    onAction: (VoiceCallAction) -> Unit,
    micLevel: () -> Float,
    voiceLevel: () -> Float,
) {
    val phase = state.phase
    val interrupt = phase == VoiceCallPhaseUi.Speaking && state.interruptible
    val finish = phase is VoiceCallPhaseUi.Listening && state.manualEndOfTurn
    GistiPresenceOrb(
        mode = orbMode(phase),
        level = when (phase) {
            VoiceCallPhaseUi.Speaking -> voiceLevel
            is VoiceCallPhaseUi.Listening -> micLevel
            else -> ZeroLevel
        },
        size = VoiceCallDefaults.OrbMax,
        modifier = Modifier.fillMaxSize(),
        onClick = if (interrupt || finish) {
            { onAction(VoiceCallAction.OrbClicked) }
        } else {
            null
        },
        clickLabel = when {
            interrupt -> stringResource(Res.string.voice_call_orb_interrupt_a11y)
            finish -> stringResource(Res.string.voice_call_orb_finish_a11y)
            else -> null
        },
        clickHint = if (finish) stringResource(Res.string.voice_call_orb_finish_hint_a11y) else null,
    )
}

/**
 * Status title (+ hint, + the phase's own button). In dot-mode a 73dp space on the start edge
 * holds the orb, which [CallStage] places there.
 */
@Composable
private fun StatusBlock(title: String, hint: String?, dot: Boolean, actions: @Composable () -> Unit) {
    if (dot) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        ) {
            Spacer(Modifier.size(VoiceCallDefaults.DotSize * VoiceCallDefaults.HaloExtent))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs)) {
                StatusTitle(title, TextAlign.Start)
                actions()
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
        ) {
            StatusTitle(title, TextAlign.Center)
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            actions()
        }
    }
}

/** The only status live region; a cross-fade on text change, no flicker when the text is the same. */
@Composable
private fun StatusTitle(title: String, align: TextAlign) {
    val reduced = LocalReducedMotion.current
    Crossfade(
        targetState = title,
        animationSpec = if (reduced) tween(AppMotion.ReducedMotionMillis) else AppMotion.effectsDefaultTween(),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = title
                liveRegion = LiveRegionMode.Polite
            },
        label = "statusTitle",
    ) { text ->
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatusActions(phase: VoiceCallPhaseUi, onAction: (VoiceCallAction) -> Unit) {
    when {
        phase is VoiceCallPhaseUi.Thinking && phase.delay == VoiceThinkingDelayUi.VerySlow ->
            AppButtonSecondary(
                text = stringResource(Res.string.voice_call_action_cancel_turn),
                onClick = { onAction(VoiceCallAction.CancelTurnClicked) },
                modifier = Modifier.padding(top = AppDimens.SpacingXs),
            )
        phase is VoiceCallPhaseUi.Paused ->
            AppButton(
                text = stringResource(Res.string.voice_call_action_resume),
                onClick = { onAction(VoiceCallAction.ResumeClicked) },
                icon = Icons.Filled.PlayArrow,
                modifier = Modifier.padding(top = AppDimens.SpacingXs),
            )
    }
}

// ── Z3c transcript ──────────────────────────────────────────────────────────

@Composable
private fun CallTranscript(
    state: VoiceCallUiState.InCall,
    compactCards: Boolean,
    seenActionId: String?,
    onSeen: (String) -> Unit,
    onAction: (VoiceCallAction) -> Unit,
) {
    val t = state.transcript
    val phase = state.phase
    val showBubble = t.userText != null &&
        ((phase is VoiceCallPhaseUi.Listening && phase.hearing) || phase is VoiceCallPhaseUi.Thinking)
    val reply = t.reply
    val captions = reply.takeIf { state.captionsOn && phase == VoiceCallPhaseUi.Speaking }
    if (t.lastAction == null && !showBubble && captions == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppDimens.SpacingMd, bottom = AppDimens.SpacingSm),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm, Alignment.Bottom),
    ) {
        t.lastAction?.let { action ->
            AppearingActionCard(action, compactCards, animate = action.id != seenActionId, onSeen, onAction)
        }
        if (showBubble) UserPartialBubble(text = t.userText.orEmpty(), modifier = Modifier.weight(1f, fill = false))
        if (captions != null) {
            ReplyCaptions(
                spoken = captions.spokenPart,
                pending = captions.pendingPart,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** The newest action card: fade + scale 0.96→1 and one Confirm haptic when it is new. */
@Composable
private fun AppearingActionCard(
    action: VoiceActionUi,
    compact: Boolean,
    animate: Boolean,
    onSeen: (String) -> Unit,
    onAction: (VoiceCallAction) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val haptic = LocalHapticFeedback.current
    val appear = remember(action.id) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(action.id) {
        if (appear.value < 1f) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            appear.animateTo(1f, if (reduced) tween(AppMotion.ReducedMotionMillis) else AppMotion.spatialDefault)
        }
        onSeen(action.id)
    }
    ActionCard(
        action = action,
        compact = compact,
        onAction = onAction,
        modifier = Modifier.graphicsLayer {
            val p = appear.value
            alpha = p.coerceIn(0f, 1f)
            if (!reduced) {
                val s = CARD_START_SCALE + (1f - CARD_START_SCALE) * p
                scaleX = s
                scaleY = s
            }
        },
    )
}

/** An action card wired to [VoiceCallAction.ActionCardClicked]; shared by the call and the summary. */
@Composable
internal fun ActionCard(
    action: VoiceActionUi,
    compact: Boolean,
    onAction: (VoiceCallAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = actionKindLabel(action)
    VoiceActionCard(
        action = action,
        kindLabel = kind,
        a11yLabel = actionA11yLabel(action, kind),
        compact = compact,
        onClick = action.checklistId?.let { { onAction(VoiceCallAction.ActionCardClicked(action.id)) } },
        modifier = modifier,
    )
}

private val ZeroLevel: () -> Float = { 0f }
private const val CARD_START_SCALE = 0.96f
private const val LANDSCAPE_ORB_FRACTION = 0.3f
