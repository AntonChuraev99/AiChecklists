package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme

/**
 * Sizes of the voice call overlay (DESIGN_SPEC §11). A component-level Defaults object like
 * `AppSoftIconButtonDefaults`: none of these has a counterpart in `AppDimens` by meaning
 * (56 equals `TextFieldHeight` by coincidence only). Spacing stays on `AppDimens.Spacing*`.
 */
object VoiceCallDefaults {
    /** Orb diameter ceiling (412×915 lands here). */
    val OrbMax: Dp = 240.dp

    /** Below this diameter the stage collapses and the orb becomes the [DotSize] dot by the title. */
    val OrbMin: Dp = 96.dp

    /** Below this diameter the status hint is dropped first (layout budget, step 5). */
    val OrbHintThreshold: Dp = 140.dp

    /** Orb size in dot-mode. */
    val DotSize: Dp = 56.dp

    /** How far the speaking halo reaches past the orb: the layout reserves `D × HaloExtent`. */
    const val HaloExtent: Float = 1.3f

    val ControlDisc: Dp = 56.dp
    val EndDisc: Dp = 72.dp

    /** Every dock control sits in a slot this tall, disc centred, so the three labels share a line. */
    val ControlSlot: Dp = 72.dp

    /** Width of the vertical dock rail in landscape. */
    val RailWidth: Dp = 96.dp

    val ContentMaxWidth: Dp = 480.dp
    val ActionTileSize: Dp = 40.dp
    val ActionTileCorner: Dp = 12.dp

    /** Height of the top row, and of the spacer that replaces it on full-screen states. */
    val TopRowHeight: Dp = 56.dp

    /** Full-screen state icon disc. */
    val IconDisc: Dp = 64.dp

    /** fontScale ceiling for the timer pill and the dock labels (RU labels break mid-word above it). */
    const val LabelFontScaleCap: Float = 1.3f

    /** Fade mask over the top of a bottom-anchored transcript text when older lines are cut off. */
    val TranscriptFade: Dp = 16.dp

    /** Dot «on air» in the timer pill. */
    val LiveDot: Dp = 8.dp
}

/**
 * Call-specific colour roles (DESIGN_SPEC §11). Every value is a scheme role — no new hex.
 *
 * - [end]: in M3 dark `error` is the pale `#FFB4AB`, which does not read as "hang up"; dark takes
 *   `errorContainer` instead.
 * - [toggleOn]: no scheme role names "a toggle in its deviating state" (mic off); an own accessor so a
 *   re-tune does not drag every `onSurface`.
 * - [liveDot] is separate from [end]: the dark `errorContainer` `#8C0009` disappears as an 8dp dot.
 */
object AppCallColors {
    @Composable
    @ReadOnlyComposable
    fun end(): Color =
        if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.error

    @Composable
    @ReadOnlyComposable
    fun onEnd(): Color =
        if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onError

    @Composable
    @ReadOnlyComposable
    fun toggleOn(): Color = MaterialTheme.colorScheme.onSurface

    @Composable
    @ReadOnlyComposable
    fun onToggleOn(): Color = MaterialTheme.colorScheme.surface

    @Composable
    @ReadOnlyComposable
    fun liveDot(): Color = MaterialTheme.colorScheme.error
}
