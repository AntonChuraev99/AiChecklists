package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/** What the presence orb shows (DESIGN_SPEC §4). One mode per call phase. */
enum class OrbMode {
    /** Connecting: dimmed gradient, slow breathing. */
    Breath,

    /** Listening, silence: thin `primary` ring breathing in alpha. */
    Listen0,

    /** Listening, speech: ring and scale follow the mic level. */
    Listen1,

    /** Thinking: the gradient angle turns. */
    Think,

    /** Speaking: two halos follow the TTS level. */
    Speak,

    /** Mic off: recessed disc + MicOff glyph. */
    Muted,

    /** Paused: recessed disc + Pause glyph. */
    Paused,

    /** Reconnecting: faded gradient, slower breathing. */
    Dim,
}

/**
 * Gisti's presence during a call — a gradient disc with mode-specific ring/halo.
 *
 * The node is `size × HaloExtent` square (the speaking halo reaches 30 % past the disc); the disc of
 * diameter [size] sits in its centre.
 *
 * **Recomposition budget.** [level] (`0..1`, ~20 Hz) is never read in composition: a frame loop,
 * alive only in [OrbMode.Listen1] / [OrbMode.Speak] and only without reduced motion, smooths it
 * (attack τ 50 ms, release τ 150 ms) into a float state that only the draw phase reads. Infinite
 * transitions are created only for animated modes and only when reduced motion is off (the gate is
 * BEFORE `rememberInfiniteTransition`, per the `LocalReducedMotion` contract).
 *
 * @param onClick `null` = decorative (no semantics at all). Otherwise a [Role.Button] named
 *   [clickLabel], no ripple; a press scales the disc to 0.96 and ticks a context-click haptic.
 */
@Composable
fun GistiPresenceOrb(
    mode: OrbMode,
    level: () -> Float,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null,
    clickHint: String? = null,
) {
    val reduced = LocalReducedMotion.current
    val live = !reduced && (mode == OrbMode.Listen1 || mode == OrbMode.Speak)
    val smoothed = rememberSmoothedLevel(live = live, mode = mode, level = level)
    val motion = rememberOrbMotion(mode = mode, reduced = reduced)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = if (reduced) tween(0) else AppMotion.spatialFast,
        label = "orbPress",
    )

    val colors = OrbColors(
        from = GistiColors.aiStart,
        to = GistiColors.aiEnd,
        ring = MaterialTheme.colorScheme.primary,
        halo = GistiColors.aiStart,
        recessed = AppSurface.recessed(),
        outline = MaterialTheme.colorScheme.outline,
        glyph = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val glyph = when (mode) {
        OrbMode.Muted -> rememberVectorPainter(Icons.Filled.MicOff)
        OrbMode.Paused -> rememberVectorPainter(Icons.Filled.Pause)
        else -> null
    }
    val haptic = LocalHapticFeedback.current

    // [size] is the default; a parent that measures the orb with fixed constraints (the call stage's
    // layout budget) wins, and the disc follows the measured box — the composition stays stable
    // while the diameter changes with the window.
    Box(modifier = modifier.size(size * VoiceCallDefaults.HaloExtent), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f / VoiceCallDefaults.HaloExtent
            val levelNow = if (live) smoothed.floatValue else 0f
            drawOrb(
                mode = mode,
                reduced = reduced,
                radius = radius,
                level = levelNow,
                scale = motion.scale() * pressScale,
                ringAlpha = motion.ringAlpha(),
                rotationDeg = motion.rotation(),
                colors = colors,
            )
            if (glyph != null) {
                val glyphPx = max(radius * 2f * GLYPH_FRACTION, GLYPH_MIN.toPx())
                translate(left = center.x - glyphPx / 2f, top = center.y - glyphPx / 2f) {
                    with(glyph) { draw(Size(glyphPx, glyphPx), colorFilter = ColorFilter.tint(colors.glyph)) }
                }
            }
        }
        val hitModifier = if (onClick != null) {
            Modifier
                .fillMaxSize(DISC_FRACTION)
                .clickable(interactionSource = interaction, indication = null) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                }
                .clearAndSetSemantics {
                    role = Role.Button
                    clickLabel?.let { contentDescription = it }
                    onClick(label = clickHint) { onClick(); true }
                }
        } else {
            Modifier.fillMaxSize(DISC_FRACTION).clearAndSetSemantics {}
        }
        Box(hitModifier)
    }
}

private class OrbColors(
    val from: Color,
    val to: Color,
    val ring: Color,
    val halo: Color,
    val recessed: Color,
    val outline: Color,
    val glyph: Color,
)

/** Values of the mode's idle motion; constant providers when the mode is static or motion is reduced. */
private class OrbMotion(val scale: () -> Float, val ringAlpha: () -> Float, val rotation: () -> Float)

@Composable
private fun rememberOrbMotion(mode: OrbMode, reduced: Boolean): OrbMotion {
    // Static values first: reduced motion and every mode without an idle loop.
    val staticMotion = when (mode) {
        OrbMode.Breath, OrbMode.Dim -> OrbMotion({ BREATH_STATIC }, { 0f }, { 0f })
        OrbMode.Listen0 -> OrbMotion({ 1f }, { RING_IDLE_STATIC }, { 0f })
        OrbMode.Think -> OrbMotion({ THINK_STATIC }, { 0f }, { 0f })
        else -> OrbMotion({ 1f }, { 0f }, { 0f })
    }
    if (reduced) return staticMotion
    return when (mode) {
        OrbMode.Breath, OrbMode.Dim -> {
            val half = if (mode == OrbMode.Dim) DIM_PERIOD_MS / 2 else BREATH_PERIOD_MS / 2
            val transition = rememberInfiniteTransition(label = "orbBreath")
            val scale = transition.animateFloat(
                initialValue = BREATH_MIN,
                targetValue = BREATH_MAX,
                animationSpec = infiniteRepeatable(tween(half, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "breathScale",
            )
            remember(scale) { OrbMotion({ scale.value }, { 0f }, { 0f }) }
        }
        OrbMode.Listen0 -> {
            val transition = rememberInfiniteTransition(label = "orbListen")
            val alpha = transition.animateFloat(
                initialValue = RING_IDLE_MIN,
                targetValue = RING_IDLE_MAX,
                animationSpec = infiniteRepeatable(
                    tween(LISTEN_PERIOD_MS / 2, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                ),
                label = "ringAlpha",
            )
            remember(alpha) { OrbMotion({ 1f }, { alpha.value }, { 0f }) }
        }
        OrbMode.Think -> {
            val transition = rememberInfiniteTransition(label = "orbThink")
            val rotation = transition.animateFloat(
                initialValue = 0f,
                targetValue = FULL_TURN,
                animationSpec = infiniteRepeatable(tween(THINK_TURN_MS, easing = LinearEasing)),
                label = "thinkRotation",
            )
            val scale = transition.animateFloat(
                initialValue = THINK_MIN,
                targetValue = THINK_MAX,
                animationSpec = infiniteRepeatable(
                    tween(THINK_PULSE_MS / 2, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                ),
                label = "thinkScale",
            )
            remember(rotation, scale) { OrbMotion({ scale.value }, { 0f }, { rotation.value }) }
        }
        else -> staticMotion
    }
}

/**
 * Exponentially smoothed [level], written once per frame. The loop exists only while [live]; the
 * initial value is read without snapshot observation so the remember block never subscribes the
 * caller's scope to a 20 Hz state.
 */
@Composable
private fun rememberSmoothedLevel(live: Boolean, mode: OrbMode, level: () -> Float) =
    remember { mutableFloatStateOf(Snapshot.withoutReadObservation { level() }.coerceIn(0f, 1f)) }.also { state ->
        val currentLevel by rememberUpdatedState(level)
        if (live) {
            LaunchedEffect(mode) {
                var last = 0L
                while (true) {
                    withFrameNanos { now ->
                        val dt = if (last == 0L) FRAME_SEC else (now - last) / NANOS_PER_SEC
                        last = now
                        val target = currentLevel().coerceIn(0f, 1f)
                        val s = state.floatValue
                        val tau = if (target > s) ATTACK_TAU_SEC else RELEASE_TAU_SEC
                        state.floatValue = s + (target - s) * (1f - exp(-dt / tau))
                    }
                }
            }
        }
    }

@Suppress("LongParameterList", "CyclomaticComplexMethod")
private fun DrawScope.drawOrb(
    mode: OrbMode,
    reduced: Boolean,
    radius: Float,
    level: Float,
    scale: Float,
    ringAlpha: Float,
    rotationDeg: Float,
    colors: OrbColors,
) {
    when (mode) {
        OrbMode.Breath -> drawGradientDisc(radius * scale, colors, alpha = BREATH_ALPHA, rotationDeg = 0f)
        OrbMode.Dim -> drawGradientDisc(radius * scale, colors, alpha = DIM_ALPHA, rotationDeg = 0f)
        OrbMode.Listen0 -> {
            drawCircle(
                color = colors.ring,
                radius = radius * RING_IDLE_RADIUS,
                alpha = ringAlpha,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawGradientDisc(radius, colors, alpha = 1f, rotationDeg = 0f)
        }
        OrbMode.Listen1 -> {
            val ringR = if (reduced) RING_HEARD_STATIC_R else RING_HEARD_BASE + RING_HEARD_GAIN * level
            val ringA = if (reduced) RING_HEARD_STATIC_A else RING_HEARD_ALPHA + RING_HEARD_ALPHA_GAIN * level
            drawCircle(color = colors.ring, radius = radius * ringR, alpha = ringA, style = Stroke(3.dp.toPx()))
            drawGradientDisc(radius * (1f + LISTEN_SCALE_GAIN * level), colors, alpha = 1f, rotationDeg = 0f)
        }
        OrbMode.Think -> drawGradientDisc(radius * scale, colors, alpha = 1f, rotationDeg = rotationDeg)
        OrbMode.Speak -> {
            val outer = if (reduced) HALO_OUTER_STATIC else HALO_OUTER_BASE + HALO_OUTER_GAIN * level
            val inner = if (reduced) HALO_INNER_STATIC else HALO_INNER_BASE + HALO_INNER_GAIN * level
            drawCircle(color = colors.halo, radius = radius * outer, alpha = HALO_OUTER_ALPHA)
            drawCircle(color = colors.halo, radius = radius * inner, alpha = HALO_INNER_ALPHA)
            drawGradientDisc(radius * (1f + SPEAK_SCALE_GAIN * level) * scale, colors, alpha = 1f, rotationDeg = 0f)
        }
        OrbMode.Muted, OrbMode.Paused -> {
            drawCircle(color = colors.recessed, radius = radius)
            drawCircle(color = colors.outline, radius = radius - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
        }
    }
}

/** The gradient disc; the gradient runs top-left → bottom-right, turned by [rotationDeg]. */
private fun DrawScope.drawGradientDisc(radius: Float, colors: OrbColors, alpha: Float, rotationDeg: Float) {
    val angle = (BASE_ANGLE_DEG + rotationDeg) * PI.toFloat() / HALF_TURN
    val dir = Offset(cos(angle), sin(angle)) * radius
    val brush = Brush.linearGradient(listOf(colors.from, colors.to), start = center - dir, end = center + dir)
    drawCircle(brush = brush, radius = radius, alpha = alpha)
}

private const val PRESS_SCALE = 0.96f
private const val DISC_FRACTION = 1f / VoiceCallDefaults.HaloExtent
private const val GLYPH_FRACTION = 0.3f
private val GLYPH_MIN = 24.dp

private const val BREATH_MIN = 0.90f
private const val BREATH_MAX = 0.96f
private const val BREATH_STATIC = 0.93f
private const val BREATH_ALPHA = 0.6f
private const val DIM_ALPHA = 0.45f
private const val BREATH_PERIOD_MS = 1600
private const val DIM_PERIOD_MS = 2400

private const val RING_IDLE_RADIUS = 1.08f
private const val RING_IDLE_MIN = 0.15f
private const val RING_IDLE_MAX = 0.30f
private const val RING_IDLE_STATIC = 0.25f
private const val LISTEN_PERIOD_MS = 2400

private const val RING_HEARD_BASE = 1.06f
private const val RING_HEARD_GAIN = 0.16f
private const val RING_HEARD_ALPHA = 0.35f
private const val RING_HEARD_ALPHA_GAIN = 0.4f
private const val RING_HEARD_STATIC_R = 1.12f
private const val RING_HEARD_STATIC_A = 0.55f
private const val LISTEN_SCALE_GAIN = 0.03f

private const val THINK_MIN = 0.94f
private const val THINK_MAX = 0.98f
private const val THINK_STATIC = 0.96f
private const val THINK_TURN_MS = 2400
private const val THINK_PULSE_MS = 1200

private const val HALO_INNER_BASE = 1.06f
private const val HALO_INNER_GAIN = 0.08f
private const val HALO_OUTER_BASE = 1.14f
private const val HALO_OUTER_GAIN = 0.16f
private const val HALO_INNER_STATIC = 1.14f
private const val HALO_OUTER_STATIC = 1.3f
private const val HALO_INNER_ALPHA = 0.18f
private const val HALO_OUTER_ALPHA = 0.12f
private const val SPEAK_SCALE_GAIN = 0.06f

private const val ATTACK_TAU_SEC = 0.05f
private const val RELEASE_TAU_SEC = 0.15f
private const val FRAME_SEC = 0.016f
private const val NANOS_PER_SEC = 1_000_000_000f

private const val FULL_TURN = 360f
private const val HALF_TURN = 180f
private const val BASE_ANGLE_DEG = 45f
