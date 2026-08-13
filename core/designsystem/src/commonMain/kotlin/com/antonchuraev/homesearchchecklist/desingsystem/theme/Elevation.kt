package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shadow depths for the ONE layer that still casts a shadow: **L3 Floating** (see [AppSurface]).
 *
 * A floating element sits above arbitrary scrolling content, so the color underneath it is unknown
 * and the tonal step that separates every other layer cannot work: a white pill on a white card is
 * invisible, and a hairline around it reads as "one more card" rather than "above". Everywhere else
 * the ladder separates by tone, which is the Material 3 default mechanism.
 *
 * ⛔ Never re-introduce a shadow inside a `LazyColumn` row: the next item paints over the previous
 * one by z-order, so the top/bottom shadow is overdrawn and only the side "ears" survive. That is the
 * defect behind the flat-card decision (2026-06-16) — see [AppCardDefaults][
 * com.antonchuraev.homesearchchecklist.desingsystem.components.AppCardDefaults].
 */
object AppElevation {

    /** Small floating pill — the collapsed chat dock. */
    val FloatingPill: Dp = 2.dp

    /** Floating menu surface — `DropdownMenu`, tooltip. */
    val FloatingMenu: Dp = 3.dp

    /** Large floating panel — the inline chat panel, snackbar, FAB-adjacent surfaces. */
    val FloatingPanel: Dp = 8.dp

    /**
     * The shadow rule of the ladder, in one place: **dark theme paints no shadow at all**.
     *
     * It is not "a shadow with lower alpha" — it is a different separator. On a near-black surface a
     * drop shadow has nothing to darken, so L3 in dark is carried by a 1dp `outlineVariant` ring
     * instead, and the shadow is switched off entirely.
     *
     * ```kotlin
     * Surface(shadowElevation = AppElevation.shadowInLight(AppElevation.FloatingPanel)) { … }
     * ```
     *
     * @param level the light-theme depth, one of [FloatingPill] / [FloatingMenu] / [FloatingPanel].
     * @return [level] in light theme, `0.dp` in dark.
     */
    @Composable
    @ReadOnlyComposable
    fun shadowInLight(level: Dp): Dp = if (LocalIsDarkTheme.current) 0.dp else level
}

/**
 * The app's depth ladder: six levels expressed as `surfaceContainer*` **tone**, not as shadow.
 *
 * Material 3 separates surfaces by tonal difference by default; shadow is only one of the ways to
 * depict elevation, and this app uses it on a single level ([AppElevation]). Every accessor reads
 * [LocalIsDarkTheme] — the *user-selected* theme, which may differ from the system one — exactly like
 * [GistiColors] does.
 *
 * | Level | Lives there | Separator |
 * |---|---|---|
 * | −1 [recessed] | progress track, text-field fill, chat bubble, chip inside a card, skeleton | tone only |
 * | 0 [ground] | page background, `AppScaffold` container | — |
 * | 1 [card] | list row, card, section | 1dp `outlineVariant` all round |
 * | 2 [docked] | sticky CTA, capture dock, `NavigationBar`, sticky header | 1dp `outlineVariant` on the seam only |
 * | 3 [floating] | FAB, chat dock/panel, snackbar, `DropdownMenu`, tooltip, drag ghost | light → shadow; dark → 1dp ring |
 * | 4 [modal] | `ModalBottomSheet`, `AlertDialog`, full-screen overlay | `scrim` @ 32%, no shadow |
 *
 * **In light the ladder runs both ways** — raised surfaces are brighter than the warm cream page,
 * recessed ones are darker. In dark it only runs up in lightness. Flattening either direction to
 * "one neutral grey" collapses the ladder.
 *
 * **Nesting steps one container up from the host.** Light: sheet `#F6F5F2` → card `#FFFFFF`.
 * Dark: sheet `#1E2025` → card `#26282E` → well `#2D2F35`.
 */
object AppSurface {

    /** Level 0 — the page itself. */
    @Composable
    @ReadOnlyComposable
    fun ground(): Color = MaterialTheme.colorScheme.surface

    /** Level −1 — a well *inside* a card: progress track, input fill, bubble, skeleton. */
    @Composable
    @ReadOnlyComposable
    fun recessed(): Color = if (LocalIsDarkTheme.current) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    /**
     * Level 1 — the resting card / list row.
     *
     * Dark uses `surfaceContainerLow`, one step *lighter* than the dark page, because tonal lift in
     * dark only runs upward; `surfaceContainerLowest` would sit darker and read as recessed.
     */
    @Composable
    @ReadOnlyComposable
    fun card(): Color = if (LocalIsDarkTheme.current) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    /**
     * Level 2 — a bar pinned to an edge of the window.
     *
     * Same tone as [card] on purpose: a dock is not "more raised" than a card, it is *anchored*. What
     * separates it is the 1dp divider on the seam it shares with the content — see [dockedSeam] —
     * not a tonal step and never a shadow.
     */
    @Composable
    @ReadOnlyComposable
    fun docked(): Color = card()

    /**
     * Colour of the 1dp line where a [docked] bar meets the content. **Not `outlineVariant`.**
     *
     * On a card the hairline is one of two separators — the card also sits a tonal step off the page,
     * so `outlineVariant` only has to hint at the edge. On the bottom bar there is no second channel:
     * measured on the rendered screen, the bar (`#FFFFFF`) against the warm page (`#FBFAF8`) is a
     * 1.03:1 step, i.e. no step at all, and `outlineVariant` (`#E2E0DB`) against that page is 1.26:1.
     * The entire separation rides on a line you can barely see.
     *
     * Dark does not have this problem — the same token resolves to `#2C2F36`, which is 3.17:1 against
     * `#121317` — so only light is lifted, to `outline`. Same role family, one step firmer, and the
     * bar stops floating over the list with nothing between them.
     */
    @Composable
    @ReadOnlyComposable
    fun dockedSeam(): Color = if (LocalIsDarkTheme.current) {
        MaterialTheme.colorScheme.outlineVariant
    } else {
        MaterialTheme.colorScheme.outline
    }

    /**
     * Level 3 — floating above arbitrary content.
     *
     * Pair with [AppElevation.shadowInLight] in light and a 1dp `outlineVariant` ring in dark.
     */
    @Composable
    @ReadOnlyComposable
    fun floating(): Color = if (LocalIsDarkTheme.current) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    /** Level 4 — sheet / dialog / full-screen overlay. Separated by the scrim, so it carries no shadow. */
    @Composable
    @ReadOnlyComposable
    fun modal(): Color = if (LocalIsDarkTheme.current) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
}
