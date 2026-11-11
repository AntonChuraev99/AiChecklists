package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** The ime inset must exceed the nav-bar inset by this much to count as "open" (closed ime == navbar). */
internal val ImeOpenMargin = 8.dp

/**
 * "Is the keyboard open" as a snapshot-backed boolean.
 *
 * The insets are read INSIDE the derivation, so (a) the value follows the live keyboard — the old
 * `remember { derivedStateOf { capturedDp > capturedDp } }` read no state and froze at the first
 * composition — and (b) a reader recomposes only when the boolean flips, not on every frame of the
 * keyboard animation. Reading `asPaddingValues().calculateBottomPadding()` in a screen body instead
 * invalidated the whole screen per keyboard frame.
 */
@Composable
internal fun rememberImeOpenState(
    ime: WindowInsets = WindowInsets.ime,
    navigationBars: WindowInsets = WindowInsets.navigationBars,
): State<Boolean> {
    val density = LocalDensity.current
    return remember(ime, navigationBars, density) {
        derivedStateOf {
            with(density) {
                ime.getBottom(density).toDp() > navigationBars.getBottom(density).toDp() + ImeOpenMargin
            }
        }
    }
}
