package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
 * | 2 [docked] | sticky CTA, v1 `AppNavigationBar`, sticky header | 1dp `outlineVariant` on the seam only |
 * | 2′ [bottomChrome] | the v2 bar, the capture dock, the chat dock — the app's bottom EDGE | tone off the page + a painted [bottomChromeShadow] |
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

    // ─────────────────────────────────────────────────────────────────────────
    // Level 2′ — BOTTOM CHROME. One family for every surface at the window's
    // bottom edge, kept SEPARATE from [docked] / [dockedSeam].
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The single tone of the app's bottom chrome: the v2 navigation bar, the quick-capture dock, the
     * chat dock, the full-screen chat overlay, and the system-nav strip painted behind any of them.
     *
     * ## Why one token and not three surfaces that happen to agree
     * These surfaces are seen **stacked**, in one frame: the capture dock's bottom edge IS the bar's
     * top edge, and the chat dock rises from the same place. Three separate colours read as three
     * unrelated docks piled at the bottom of the screen — which is what shipped, and what the owner
     * rejected on the device ("bar, dock and CTA are three different languages"). The tone therefore
     * lives here once and every bottom surface reads it, including `gistiDockColor()`, which now
     * forwards to this accessor rather than owning a second copy of the decision.
     *
     * ⛔ Deliberately NOT [docked]. [docked] belongs to a bar that shares the page's plane and is
     * separated by a seam — the share sheet's CTA, the template-preview CTA, the analyze-preview CTA,
     * the v1 `AppNavigationBar`. Those four are approved as they are and must not move; lifting
     * [docked] to satisfy the bottom chrome would move all of them.
     *
     * ## The measurements this encodes
     * Contrast RATIO is close to useless for judging two near-black or two near-white surfaces — the
     * WCAG formula compresses the whole dark ramp into a hair's breadth. The number that matches what
     * an eye sees is **ΔL\*** (CIE lightness), so both are quoted and ΔL\* is the one that decides.
     *
     * | Light: candidate ↔ page `#FBFAF8` (L\* 98.3) | ΔL\* | ratio |
     * |---|---|---|
     * | `surfaceContainerLowest` `#FFFFFF` — the REJECTED bar | +1.7 | 1.04 : 1 |
     * | `surfaceContainerHighest` `#E6E4DF` | −7.7 | 1.22 : 1 |
     * | **`surfaceDim` `#DEDCD6` — chosen** | **−10.5** | **1.31 : 1** |
     * | `inverseSurface` `#322F35` — the REJECTED ink plinth | −78.4 | 12.63 : 1 |
     *
     * | Dark: candidate ↔ page `#121317` (L\* 5.9) | ΔL\* | ratio |
     * |---|---|---|
     * | `surfaceContainerLowest` `#0D0E11` | −2.0 | 1.04 : 1 |
     * | **`surfaceContainerLow` `#1A1C20` — chosen** | **+4.3** | **1.09 : 1** |
     * | `surfaceContainerHigh` `#26282E` | +10.2 | 1.26 : 1 |
     * | `surfaceContainerHighest` `#2D2F35` — the REJECTED pale slab | +13.5 | 1.39 : 1 |
     *
     * ## Why dark goes UP and light goes DOWN — and why "mirror the light fix" is arithmetically wrong
     * The instinct after the light fix is to mirror it: if light gets a plinth DARKER than the page,
     * dark should too. Measured, that makes dark worse. The dark page `#121317` already sits at
     * L\* 5.9, two points off the bottom of the ramp — its darkest neighbour `surfaceContainerLowest`
     * is only ΔL\* −2.0 away, and even PURE BLACK is only −5.9. Going down therefore buys **less**
     * separation than going up, and lands the chrome at roughly the same invisibility (−2.0) as the
     * white bar that was rejected in light (+1.7).
     *
     * So in dark the axis is not "separate more", it is "stop being pale": +13.5 was a slab brighter
     * than anything else on screen, which reads on a device as white showing through under the
     * navigation. +4.3 is a lift you can see without it becoming the brightest object in the frame —
     * and it is the tone the chat dock already used, so the BAR moved to meet the chat rather than
     * the other way round. This is the ladder's documented asymmetry (see this object's KDoc): in
     * light it runs both ways, in dark it only runs up.
     *
     * **Neither theme is carried by tone alone**, and that is by design, not a gap: ±10.5 and +4.3 are
     * both below the step a filled boundary needs on its own. The second channel is
     * [bottomChromeShadow] plus the 28dp top corners — see [AppShapeTokens.SheetTop][
     * com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens.SheetTop].
     *
     * ⚠️ Every accessor in this group is one **variant**. Re-tuning the chrome is an edit to these
     * BODIES only — no call site reads a colour role directly.
     */
    @Composable
    @ReadOnlyComposable
    fun bottomChrome(): Color = if (LocalIsDarkTheme.current) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceDim
    }

    /**
     * Icon and label of an INACTIVE destination on the [bottomChrome].
     *
     * ## The trap this used to encode, now removed
     * The ink plinth spelled this with `inverse*` roles, and `inverse*` means "content for a surface
     * inverted **relative to the current theme**" — so the pair flips with the theme: `inverseOnSurface`
     * is the near-white `#F4EFF4` in light and the near-black `#322F35` in dark. Spelled the same way
     * in both themes it paints dark grey labels on a dark grey bar, invisible, and invisible only in
     * dark — the half a light-only preview never shows. Now that the chrome is an ordinary surface in
     * both themes the ordinary role is correct in both, and the trap is gone rather than guarded.
     *
     * Held below full strength (82% light / 75% dark) so the active destination wins by contrast and
     * not only by its pill: at full strength four identical labels compete with the one that matters.
     * The dimming is emphasis, never legibility, and it is checked against AA rather than assumed —
     * measured on the blended result, the idle label is **4.50 : 1** in light and **6.18 : 1** in
     * dark, against 6.81 : 1 / 10.01 : 1 at full strength. Light has almost no room left: drop the
     * alpha below 0.82 and the idle destinations fall under 4.5 : 1.
     *
     * For the ACTIVE destination's label, use this role at full strength (`.copy(alpha = 1f)`); its
     * icon sits on the pill and uses [onBottomChromeAccent].
     */
    @Composable
    @ReadOnlyComposable
    // The ROLE no longer branches — only the emphasis does. Written as one expression so that stays
    // obvious: an `if` around two identical roles is how a needless branch grows a second opinion.
    fun onBottomChrome(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
        alpha = if (LocalIsDarkTheme.current) {
            BOTTOM_CHROME_IDLE_ALPHA_DARK
        } else {
            BOTTOM_CHROME_IDLE_ALPHA_LIGHT
        },
    )

    /**
     * Fill of the active destination's pill, and of the raised AI button that shares the bar.
     *
     * One role for both on purpose: they are the two accents of a single strip of chrome. The ink
     * plinth had to branch here (`primary` measured 2.6 : 1 on `#322F35`, a blue smudge, so light took
     * `inversePrimary`); on an ordinary surface in both themes the ordinary `primary` is the loud one
     * in both — 4.19 : 1 on the light chrome, 9.75 : 1 on the dark one — so the branch is gone.
     */
    @Composable
    @ReadOnlyComposable
    fun bottomChromeAccent(): Color = MaterialTheme.colorScheme.primary

    /** Content ON [bottomChromeAccent] — the active icon, and the AI button's glyph. */
    @Composable
    @ReadOnlyComposable
    fun onBottomChromeAccent(): Color = MaterialTheme.colorScheme.onPrimary

    /**
     * The band that separates the [bottomChrome] from the content above it — a PAINTED gradient, not
     * a `Modifier.shadow`.
     *
     * Two reasons it is drawn rather than cast. [AppElevation.shadowInLight] resolves to `0.dp` in
     * dark by design, so a shadow would leave dark with no separator at all; and on Skiko a real
     * shadow is a `RenderEffect` that is re-rasterised every frame (CMP-6618, Declined), which is a
     * per-frame cost for chrome that never moves. A vertical gradient is identical arithmetic on
     * both targets.
     *
     * **Both themes now ramp, and that is the change.** Dark used to be a flat 1dp `outlineVariant`
     * hairline on the reasoning that there is nothing left to darken on a near-black page. Two things
     * were wrong with it. A hairline is precisely the "`border-top: 1px solid` and nothing else"
     * separator that was condemned in light, kept alive in the theme nobody screenshotted; and at
     * `#2C2F36` on a `#1A1C20` chrome it is the BRIGHTEST thing at the bottom of a dark screen, i.e.
     * it re-creates the pale edge in miniature. A ramp reads as depth instead of as a line, and depth
     * is what "a smooth transition between the bar and the chat" actually asks for.
     *
     * Light fades transparent → 7.5% black; dark fades transparent → 35% black, both over
     * [bottomChromeShadowHeight]. The alphas differ because the surfaces do: 7.5% over cream is a
     * shadow you register without seeing, while the same 7.5% over `#121317` is arithmetically
     * invisible (ΔL\* 0.4). 35% takes the dark page down to ≈`#0C0C0F`, a trough just under the
     * chrome's own L\*.
     *
     * ⚠️ Draw it OUTSIDE the node the bar's height is measured from. The v2 shell measures the bar
     * (`onSizeChanged`) to place the raised AI button and to consume the bottom inset exactly once;
     * a 16dp band inside that node moves the button 16dp off the bar and re-opens the first-frame
     * jump the measured seed exists to prevent.
     */
    @Composable
    @ReadOnlyComposable
    fun bottomChromeShadow(): Brush = Brush.verticalGradient(
        listOf(
            Color.Transparent,
            Color.Black.copy(
                alpha = if (LocalIsDarkTheme.current) {
                    BOTTOM_CHROME_SHADOW_ALPHA_DARK
                } else {
                    BOTTOM_CHROME_SHADOW_ALPHA_LIGHT
                },
            ),
        ),
    )

    /**
     * Height [bottomChromeShadow] is drawn at. Lives with the brush so the two cannot drift.
     *
     * One value for both themes now that both ramp — the old 1dp dark branch existed only because
     * dark painted a hairline instead of a gradient.
     */
    @Composable
    @ReadOnlyComposable
    fun bottomChromeShadowHeight(): Dp = BottomChromeShadowHeight

    /**
     * How dark the light-theme ramp gets at the chrome's edge.
     *
     * There is no "shadow as a brush" token in the app — [AppElevation] describes `shadowElevation`
     * depths, which is a different mechanism — so this is named here rather than left as a number in
     * a gradient literal. 7.5% is a shadow you register without seeing; 12% reads as a grey stripe
     * drawn under the bar.
     */
    private const val BOTTOM_CHROME_SHADOW_ALPHA_LIGHT = 0.075f

    /**
     * The dark-theme ramp. Much heavier than the light one and it has to be: black at 7.5% over the
     * `#121317` page moves it by ΔL\* 0.4, i.e. nothing. 35% lands ≈`#0C0C0F`.
     */
    private const val BOTTOM_CHROME_SHADOW_ALPHA_DARK = 0.35f

    /** Idle destination content, light theme. */
    private const val BOTTOM_CHROME_IDLE_ALPHA_LIGHT = 0.82f

    /** Idle destination content, dark theme — one notch lower; dark contrast reads hotter. */
    private const val BOTTOM_CHROME_IDLE_ALPHA_DARK = 0.75f

    private val BottomChromeShadowHeight: Dp = 16.dp
}
