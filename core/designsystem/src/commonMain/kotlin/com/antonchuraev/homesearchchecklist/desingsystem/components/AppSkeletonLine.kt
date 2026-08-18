package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion

/** Sizing and timing for [AppSkeletonLine]. */
object AppSkeletonLineDefaults {

    /**
     * One full sweep of the shimmer highlight.
     *
     * Slow enough to read as "working", far too slow to read as "flashing". It is a `tween` and not
     * one of the [com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion] springs on
     * purpose: a spring settles, and a shimmer must not.
     */
    const val ShimmerMillis: Int = 1200

    /** Used when the type scale leaves `bodyLarge.lineHeight` unspecified or expressed in `em`. */
    val FallbackHeight: Dp = 24.dp

    /**
     * Default line height — the line height of the text this placeholder stands in for.
     *
     * Deriving it rather than hardcoding it is what makes the skeleton the *shape of the future
     * content*: when the type scale is recut, the placeholder follows it instead of drifting into a
     * generic grey bar.
     */
    @Composable
    @ReadOnlyComposable
    fun lineHeight(): Dp {
        val lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        // Unspecified, or expressed in `em`, cannot be converted to Dp — toDp() would throw.
        if (lineHeight.type != TextUnitType.Sp) return FallbackHeight
        return with(LocalDensity.current) { lineHeight.toDp() }
    }
}

/**
 * One line of a loading placeholder: a recessed bar with a highlight sweeping across it.
 *
 * ## Why a skeleton and not a spinner
 * A skeleton shows the *shape* of what is arriving, so the layout does not jump when it does, and it
 * makes the wait feel like assembly rather than like nothing happening. Vary [widthFraction] between
 * stacked lines (0.72 / 0.55 / 0.84 …) — three identical bars read as a graphic, three uneven ones
 * read as text.
 *
 * ⛔ Never pair this with a determinate progress bar: we do not know how long the wait is, so a
 * determinate bar lies.
 *
 * ## Reduced motion: the gate is before the transition, not inside it
 * When [LocalReducedMotion] is on, [rememberInfiniteTransition] is **never called** and the bar is a
 * flat fill. Gating the *output* instead would be a bug that passes review: the transition would
 * still exist and still drive a frame callback forever, burning battery on wasm and on device to
 * animate a value nobody reads. This is the only `infiniteRepeatable` in the product, which is
 * exactly why it has to be right here.
 *
 * The animated value is read inside `drawBehind`, not in composition, so the sweep invalidates the
 * draw phase alone — a composition-phase read would recompose this node on every frame.
 *
 * @param widthFraction Fraction of the available width this line occupies, `0f..1f`.
 * @param modifier Optional external modifier.
 * @param height Bar height. Defaults to [AppSkeletonLineDefaults.lineHeight].
 */
@Composable
fun AppSkeletonLine(
    widthFraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = AppSkeletonLineDefaults.lineHeight(),
) {
    val base = AppSurface.recessed()
    val highlight = MaterialTheme.colorScheme.surfaceContainerHigh

    val bar = modifier
        .fillMaxWidth(widthFraction.coerceIn(0f, 1f))
        .height(height)
        .clip(MaterialTheme.shapes.extraSmall)

    if (LocalReducedMotion.current) {
        Box(bar.background(base))
        return
    }

    val transition = rememberInfiniteTransition(label = "AppSkeletonLine")
    val sweep = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(AppSkeletonLineDefaults.ShimmerMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "AppSkeletonLineSweep",
    )

    Box(
        bar.drawBehind {
            // Read deferred to the draw phase — see the KDoc.
            val start = -size.width + sweep.value * (size.width * 2f)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(start, 0f),
                    end = Offset(start + size.width, 0f),
                ),
            )
        },
    )
}
