package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion

/**
 * Caps the font scale of [content] at [cap] (DESIGN_SPEC §7: the timer pill and the dock labels,
 * whose RU words break mid-word above 1.3 in a 90dp slot). Text inside still scales up to the cap;
 * TalkBack reads the full label from the control's semantics.
 */
@Composable
fun FontScaleCap(cap: Float = VoiceCallDefaults.LabelFontScaleCap, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    if (density.fontScale <= cap) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(density.density, cap), content = content)
    }
}

/** Text labels of the three dock controls and their TalkBack states. */
class CallDockLabels(
    val mic: String,
    val micOn: String,
    val micOff: String,
    val end: String,
    val endA11y: String,
    val captions: String,
    val captionsOn: String,
    val captionsOff: String,
)

/**
 * The call dock: Mic · End · Captions. Horizontal in portrait (each control a third of the width),
 * a vertical rail in landscape. Never shrinks: the content above gives way instead.
 */
@Composable
fun CallControlDock(
    micOn: Boolean,
    captionsOn: Boolean,
    labels: CallDockLabels,
    onMic: () -> Unit,
    onEnd: () -> Unit,
    onCaptions: () -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    val mic: @Composable (Modifier) -> Unit = { m ->
        CallToggleButton(
            checked = micOn,
            label = labels.mic,
            stateOn = labels.micOn,
            stateOff = labels.micOff,
            iconOn = Icons.Filled.Mic,
            iconOff = Icons.Filled.MicOff,
            // Mic OFF is the deviating state → ink disc; Captions stays neutral in both states.
            inkWhenOff = true,
            onToggle = onMic,
            modifier = m,
        )
    }
    val end: @Composable (Modifier) -> Unit = { m ->
        CallEndButton(label = labels.end, a11yLabel = labels.endA11y, onClick = onEnd, modifier = m)
    }
    val captions: @Composable (Modifier) -> Unit = { m ->
        CallToggleButton(
            checked = captionsOn,
            label = labels.captions,
            stateOn = labels.captionsOn,
            stateOff = labels.captionsOff,
            iconOn = Icons.Filled.ClosedCaption,
            iconOff = Icons.Filled.ClosedCaptionDisabled,
            inkWhenOff = false,
            onToggle = onCaptions,
            modifier = m,
        )
    }
    if (vertical) {
        Column(
            modifier = modifier.width(VoiceCallDefaults.RailWidth).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            mic(Modifier.fillMaxWidth())
            end(Modifier.fillMaxWidth())
            captions(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.SpacingLg, vertical = AppDimens.SpacingSm),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
            verticalAlignment = Alignment.Top,
        ) {
            mic(Modifier.weight(1f))
            end(Modifier.weight(1f))
            captions(Modifier.weight(1f))
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun CallToggleButton(
    checked: Boolean,
    label: String,
    stateOn: String,
    stateOff: String,
    iconOn: ImageVector,
    iconOff: ImageVector,
    inkWhenOff: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = inkWhenOff && !checked
    val reduced = LocalReducedMotion.current
    val discColor by animateColorAsState(
        targetValue = if (ink) AppCallColors.toggleOn() else AppSurface.card(),
        animationSpec = if (reduced) tween(AppMotion.ReducedMotionMillis) else AppMotion.effectsFastAs(),
        label = "toggleDisc",
    )
    val iconColor by animateColorAsState(
        targetValue = if (ink) AppCallColors.onToggleOn() else MaterialTheme.colorScheme.onSurface,
        animationSpec = if (reduced) tween(AppMotion.ReducedMotionMillis) else AppMotion.effectsFastAs(),
        label = "toggleIcon",
    )
    ControlSlot(
        label = label,
        modifier = modifier.semantics(mergeDescendants = true) {
            stateDescription = if (checked) stateOn else stateOff
        },
        slotModifier = Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            interactionSource = null,
            indication = ripple(bounded = false, radius = VoiceCallDefaults.ControlSlot / 2),
            onValueChange = { onToggle() },
        ),
    ) {
        Disc(
            size = VoiceCallDefaults.ControlDisc,
            color = discColor,
            border = if (ink) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
        ) {
            Icon(
                imageVector = if (checked) iconOn else iconOff,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(AppDimens.IconSizeMd),
            )
        }
    }
}

@Composable
private fun CallEndButton(label: String, a11yLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ControlSlot(
        label = label,
        modifier = modifier,
        slotModifier = Modifier.clickable(
            role = Role.Button,
            onClickLabel = null,
            interactionSource = null,
            indication = ripple(bounded = false, radius = VoiceCallDefaults.ControlSlot / 2),
            onClick = onClick,
        ).semantics { contentDescription = a11yLabel },
        mergeLabel = false,
    ) {
        Disc(size = VoiceCallDefaults.EndDisc, color = AppCallColors.end(), border = Color.Transparent) {
            Icon(
                imageVector = Icons.Filled.CallEnd,
                contentDescription = null,
                tint = AppCallColors.onEnd(),
                modifier = Modifier.size(AppDimens.IconSizeLg),
            )
        }
    }
}

/** A 72dp slot with the disc centred, and a one-line label below (font scale capped). */
@Composable
private fun ControlSlot(
    label: String,
    modifier: Modifier,
    slotModifier: Modifier,
    mergeLabel: Boolean = true,
    disc: @Composable () -> Unit,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(VoiceCallDefaults.ControlSlot).then(slotModifier),
            contentAlignment = Alignment.Center,
        ) { disc() }
        FontScaleCap {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = AppDimens.SpacingXs)
                    .then(
                        // End carries its own longer a11y label on the slot; its visible label is
                        // decoration only, so TalkBack does not read "End, End call".
                        if (mergeLabel) Modifier else Modifier.clearAndSetSemantics {},
                    ),
            )
        }
    }
}

@Composable
private fun Disc(size: Dp, color: Color, border: Color, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .background(color, CircleShape)
            .then(if (border != Color.Transparent) Modifier.border(1.dp, border, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}
