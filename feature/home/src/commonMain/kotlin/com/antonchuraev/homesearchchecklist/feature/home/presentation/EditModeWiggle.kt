package com.antonchuraev.homesearchchecklist.feature.home.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private val NoWiggle: () -> Float = { 0f }

/**
 * Edit-mode wiggle angle as a PROVIDER, to be read inside `Modifier.graphicsLayer { }` (draw phase).
 *
 * The infinite transition exists only while [active]: outside edit mode no animation clock ticks and
 * the provider is a constant 0f. Inside edit mode the value changes every frame, but because rows read
 * it in the layer block, only their layers are redrawn — no row recomposes per frame. Passing the
 * Float itself made every row (and the whole list body) recompose ~60 times a second, edit mode or not.
 */
@Composable
internal fun rememberEditModeWiggle(active: Boolean): () -> Float {
    if (!active) return NoWiggle
    val transition = rememberInfiniteTransition(label = "wiggle")
    val angle = transition.animateFloat(
        initialValue = -0.8f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 150),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wiggleAngle",
    )
    return remember(angle) { { angle.value } }
}
