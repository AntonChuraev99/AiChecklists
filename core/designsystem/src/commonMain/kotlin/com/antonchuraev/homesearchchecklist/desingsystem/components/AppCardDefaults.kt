package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme

/**
 * Shared tokens for the app's single card style: **Material 3 "filled + hairline"** — a flat tonal
 * container separated from the page by a 1dp [outlineVariant] outline, with **no shadow elevation**
 * in any interaction state, in BOTH light and dark themes.
 *
 * This deliberately drops the old elevated look. The previous cards painted a 2dp shadow in light
 * mode on a container whose color matched the page background, so the card read as pure shadow; and
 * in a dense list the top/bottom shadow was overdrawn by the next card (z-order paints neighbours on
 * top), leaving only the side "ears" users reported. A bordered flat container reads its edge on
 * every side, identically on Android and Web (no shadow → no overdraw artifact).
 *
 * Use these helpers instead of copying the five magic values (two container colors, the border, and
 * the all-zero elevation) into every card site. [AppCard] uses them; so do the feature cards that
 * carry their own click / drag / selection logic and therefore can't delegate to [AppCard].
 *
 * Selectable cards: pair [selectedBorder] (accent ring) with [selectedContainerColor] (filled
 * `primaryContainer`) when selected, and [border] / [containerColor] otherwise. The accent ring —
 * not a shadow — is the M3 way to show "lifted"/"selected".
 */
object AppCardDefaults {

    /**
     * Resting container color. Sits one tonal step off the page background so the card stays distinct
     * without the border doing all the work:
     * - light → `surfaceContainerLowest` (just brighter than the warm cream surface)
     * - dark → `surfaceContainerLow` (one step LIGHTER than the dark surface — the M3 tonal-lift
     *   direction; `Lowest` would sit darker/recessed in dark mode, so it is NOT used in dark).
     */
    @Composable
    fun containerColor(): Color {
        val isDark = LocalIsDarkTheme.current
        return if (isDark) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        }
    }

    /** Container color for the SELECTED state of a selectable card. Filled accent for clear emphasis. */
    @Composable
    fun selectedContainerColor(): Color = MaterialTheme.colorScheme.primaryContainer

    /** Resting 1dp hairline, both themes. */
    @Composable
    fun border(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

    /**
     * Accent ring for a selected / actively-dragged card. [width] defaults to 2dp; pass an animated
     * value at the call site for a smooth select/drag transition.
     */
    @Composable
    fun selectedBorder(width: Dp = 2.dp): BorderStroke =
        BorderStroke(width, MaterialTheme.colorScheme.primary)

    /**
     * [CardColors] for a resting card, with [container] defaulting to [containerColor]. Pass a custom
     * [container] only when a card intentionally tints its surface (e.g. an AI "summary" card).
     */
    @Composable
    fun colors(container: Color = containerColor()): CardColors =
        CardDefaults.cardColors(containerColor = container)

    /**
     * Zero elevation in EVERY interaction state. A non-zero hovered/pressed/dragged elevation would
     * re-introduce a shadow (and the side-ear artifact) on web hover and on touch-down.
     */
    @Composable
    fun flatElevation(): CardElevation = CardDefaults.cardElevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        focusedElevation = 0.dp,
        hoveredElevation = 0.dp,
        draggedElevation = 0.dp,
    )
}
