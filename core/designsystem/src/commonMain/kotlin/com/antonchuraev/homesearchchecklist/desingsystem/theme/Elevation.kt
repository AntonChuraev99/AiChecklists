package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
 * | 2′ [bottomChrome] | the v2 bar, the capture dock, the chat dock — the app's bottom EDGE | tone off the page + the 28dp `SheetTop` corners |
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
     * so `outlineVariant` only has to hint at the edge. On a [docked] bar there is no second channel:
     * measured on the rendered screen, the bar (`#FFFFFF`) against the warm page (`#FBFAF8`) is a
     * 1.03:1 step, i.e. no step at all, and `outlineVariant` (`#E2E0DB`) against that page is 1.26:1.
     * The entire separation rides on a line you can barely see.
     *
     * ⛔ Not for the bottom chrome — that edge has its own token, [bottomChromeSeam], for the reason
     * spelled out there.
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
     * | **[GistiColors.chrome] `#DCE2EC` — chosen 2026-09-03** | **−8.6** | **1.25 : 1** |
     * | `surfaceDim` `#DEDCD6` — the REJECTED warm grey | −10.5 | 1.31 : 1 |
     * | `inverseSurface` `#322F35` — the REJECTED ink plinth | −78.4 | 12.63 : 1 |
     *
     * | Dark: candidate ↔ page `#121317` (L\* 5.9) | ΔL\* | ratio |
     * |---|---|---|
     * | `surfaceContainerLowest` `#0D0E11` | −2.0 | 1.04 : 1 |
     * | `surfaceContainerLow` `#1A1C20` — the previous value | +4.3 | 1.09 : 1 |
     * | **[GistiColors.chrome] `#191D25` — chosen 2026-09-03** | **+4.85** | **1.10 : 1** |
     * | `surfaceContainerHigh` `#26282E` | +10.2 | 1.26 : 1 |
     * | `surfaceContainerHighest` `#2D2F35` — the REJECTED pale slab | +13.5 | 1.39 : 1 |
     *
     * ## Why a literal, and what the 2026-09-03 re-tune had to survive
     * The owner rejected the family as GREY, not as mis-levelled ("мне не нравится текущий серый
     * цвет"), so the axis of this re-tune is CHROMA and no Material role can supply it: every neutral
     * in this palette at this lightness is a warm grey of chroma ~3. [GistiColors.chrome] is therefore
     * the one hex the bottom chrome owns, and it is spent on hue while every relationship the family
     * was tuned for is kept inside a rounding error of where it was:
     *
     * | Relationship | Before | After | Verdict |
     * |---|---|---|---|
     * | raised ↔ chrome, light (`#FFFFFF`) | +12.2 | +10.3 | pills / field / cells still read as lifted |
     * | raised ↔ chrome, dark (`#26282E`) | +5.9 | +5.35 | plus the 1dp `controlOutline`, unchanged |
     * | seam [bottomChromeSeam] on chrome, light | 3.33 : 1 | 3.50 : 1 | better |
     * | idle nav label ([onBottomChrome] @0.82) light | 4.50 : 1 | ≈4.75 : 1 | AA, with more headroom |
     * | idle nav label dark @0.75 | 6.18 : 1 | ≈6.07 : 1 | AA |
     * | [bottomChromeAccent] pill / FAB on chrome, light | 4.19 : 1 | 4.42 : 1 | ≥3 : 1 (1.4.11), room |
     * | accent on chrome, dark | 9.75 : 1 | 9.58 : 1 | fine |
     *
     * **Nothing measured gets worse in light; dark moves by ≤0.2 of a ratio point.** That is the whole
     * safety argument for changing a colour four screenshot suites are pinned to.
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
     * **The second channel is the SHAPE**, not a shadow: the 28dp top corners — see
     * [AppShapeTokens.SheetTop][
     * com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens.SheetTop] — which is what
     * makes the page read as sliding behind the chrome rather than stopping on top of it.
     *
     * There WAS a third: a painted 16dp gradient ramping the page into the bar's top edge, on the
     * reasoning that ±10.5 and +4.3 are both below the step a filled boundary needs on its own. The
     * owner removed it from the device ("убери тени от нижней навигации"), so tone plus shape is now
     * the whole separator by decision, and a re-tune of this family may not quietly reintroduce a
     * shadow to buy back contrast. If the edge ever needs a third channel again the answer is
     * [bottomChromeSeam] — a hairline the owner can see and judge — not a ramp.
     *
     * ⚠️ Every accessor in this group is one **variant**. Re-tuning the chrome is an edit to these
     * BODIES only — no call site reads a colour role directly.
     */
    @Composable
    @ReadOnlyComposable
    fun bottomChrome(): Color = GistiColors.chrome

    /**
     * Colour of the 1dp line tracing the TOP edge of a [bottomChrome] surface — the chat dock's
     * stroked shoulders, the quick-capture dock's divider.
     *
     * Measured on the recorded 360dp frames, `outlineVariant` was `#E2E0DB` on the `#DEDCD6` chrome:
     * ΔL\* +1.4 (1.04 : 1), an edge-tracing line the colour of the edge it traces, leaving the whole
     * separation to the chrome's own −10.5 step off the page. Light is therefore lifted to `outline`
     * (3.33 : 1 on this surface); dark keeps `outlineVariant`, which was already ΔL\* +9.2 there and
     * needed no help.
     *
     * ## Why it is its own accessor and not [dockedSeam]
     * The two bodies are identical TODAY and that is a coincidence of the current palette, exactly as
     * [bottomChromeRaised] and [floating] are — same trap, same answer. [dockedSeam] belongs to a bar
     * that shares the PAGE's plane and is separated by the seam alone: the share sheet's CTA, the
     * template-preview CTA, the analyze-preview CTA, the v1 `AppNavigationBar`. Those four are
     * approved as they are. Re-tuning the bottom chrome is an edit to THIS group (see [bottomChrome])
     * and must leave them where they are; equally, re-tuning those four must not repaint the top edge
     * of both docks. Wiring one accessor to the other would silently drag whichever set was not being
     * worked on.
     *
     * ⚠️ Pair it with [bottomChrome], never with [docked] — a seam drawn in this role on a `docked`
     * bar is a firm line on a surface that already has a tonal step, i.e. the "decorative frame" this
     * system spends [AppChatColors][
     * com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors]'s whole KDoc avoiding.
     */
    @Composable
    @ReadOnlyComposable
    fun bottomChromeSeam(): Color = if (LocalIsDarkTheme.current) {
        MaterialTheme.colorScheme.outlineVariant
    } else {
        MaterialTheme.colorScheme.outline
    }

    /**
     * A control or a bubble raised ON the [bottomChrome] — the chat dock's chips, its input pill, its
     * answer bubble, the capture dock's AI source pills.
     *
     * ⚠️ **NOT the capture dock's text field**, which this line used to claim. That field is
     * `AddItemInputField` → `AppTextField` → an `OutlinedTextField` with a transparent container:
     * measured on `SourceRowScreenshotTest.dock_withSources_360dp_dark`, its interior reads `#1A1C20`,
     * i.e. the chrome itself at ΔL\* 0.0, separated by its `outline` ring alone (ΔL\* +49.8 dark /
     * −38.2 light). That is not the "hole punched in the dock" this accessor exists to prevent — a
     * hole is DARKER than the slab — so the field is left as it is rather than filled. What it does
     * leave open is a consistency question, not a legibility one: the chat's input pill on the same
     * chrome IS filled with this level, so the app's two bottom-chrome inputs are drawn two ways.
     * Deciding that is a design call with four other `AddItemInputField` call sites on the page plane
     * behind it (analyze preview, template preview, create-checklist), not a doc edit.
     *
     * ## Why this cannot be [card] or [floating]
     * Those two answer "how far above the PAGE is this", and the page is not what these sit on. A
     * component inside the bottom chrome has [bottomChrome] behind it, so the step that has to be
     * visible is the one off THAT surface, and it is a different arithmetic in each theme:
     *
     * | | Chrome | This | ΔL\* | ratio |
     * |---|---|---|---|---|
     * | Light | `surfaceDim` `#DEDCD6` (L\* 87.8) | `surfaceContainerLowest` `#FFFFFF` (L\* 100) | **+12.2** | 1.31 : 1 |
     * | Dark | `surfaceContainerLow` `#1A1C20` (L\* 10.2) | `surfaceContainerHigh` `#26282E` (L\* 16.1) | **+5.9** | 1.16 : 1 |
     *
     * Dark had to move: [card] resolves to the chrome's OWN tone there (both `surfaceContainerLow`),
     * i.e. ΔL\* 0.0, and `surfaceContainerLowest` — what the chat shipped — is `#0D0E11`, ΔL\* −6.2
     * BELOW the chrome. A surface darker than the slab it rests on reads as a hole punched in the
     * dock, not as a control lying on it, and it breaks [AppSurface]'s own rule that the dark ladder
     * only runs up.
     *
     * ## Why it is its own accessor and not an alias of [floating]
     * The two bodies are identical TODAY, and that is a coincidence of the current palette, not a
     * shared decision. [floating] answers "above arbitrary scrolling content, colour underneath
     * unknown" and is paired with a shadow; this one answers "on a known, measured slab" and is
     * paired with a 1dp `outline`. Re-tuning the bottom chrome (which is one variant — see
     * [bottomChrome]) must move this level and leave the FAB, the snackbar and `DropdownMenu` where
     * they are. Aliasing them would silently drag those along.
     */
    @Composable
    @ReadOnlyComposable
    fun bottomChromeRaised(): Color = if (LocalIsDarkTheme.current) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
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
     * What is visible THROUGH the two corners [AppShapeTokens.SheetTop][
     * com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens.SheetTop] clips off the
     * top of the v2 navigation bar.
     *
     * ## Why this token has to exist at all
     * Every other surface that takes `SheetTop` — the capture dock, the chat dock, the inline panel,
     * `FillOptionsSheet` — is a shaped `Surface` drawn OVER content, so its clipped shoulders reveal
     * the page that genuinely lies behind it. The navigation bar is the one exception: it is the last
     * child of the shell's `Column`, the content box above it ends exactly at its top edge, and
     * nothing in the composition is behind it. Its clipped shoulders were therefore a hole straight
     * through the app — two 28dp quarter-discs showing whatever the platform happens to paint under
     * the Compose surface.
     *
     * Measured on the recorded frames, at `x = 2`, two rows below the bar's top edge:
     *
     * | Frame | Shoulder | Chrome beside it | Page above it |
     * |---|---|---|---|
     * | `compactBar_412dp_light` | `#FAFAFA` | `#DEDCD6` | `#FBFAF8` |
     * | `compactBar_412dp_dark` | **`#FAFAFA`** | `#1A1C20` | `#121317` |
     *
     * `#FAFAFA` is not a colour this design system owns — it is Robolectric's default window backdrop.
     * That the SAME value appears in both themes is the whole point: the shoulder does not resolve
     * against the design system at all, it resolves against whatever host the composition happens to
     * be sitting on. Three hosts, three answers:
     *  - **Android, steady state** — `android:windowBackground`, so cream `#FBFAF8` beside the
     *    `#DEDCD6` chrome (the bright nick reported from a Pixel 9) and `#121317` in dark.
     *  - **Android, cold start / theme mismatch** — the `values-night` qualifier follows the SYSTEM
     *    night setting while the app's theme comes from DataStore, so a dark-theme app launched on a
     *    light-mode phone paints CREAM shoulders until `MainActivity`'s runtime setter lands.
     *  - **wasmJs** — no `windowBackground` exists behind the Skiko canvas at all.
     *
     * A hole whose colour is decided outside the composition cannot be reasoned about, cannot be
     * screenshot-tested honestly, and cannot be made consistent across the two platforms this file
     * serves. So it is painted here instead.
     *
     * ## The value: the page, unmodified
     * The shoulder shows what the plane ending at the bar's top edge would look like if it carried on
     * behind the corner, and that plane is the page. So this is [ground] — the SAME accessor, no
     * alpha over it.
     *
     * It was not always. While the chrome carried a painted 16dp shadow ramp, the shoulder took that
     * gradient's terminal colour (black at the ramp's alpha, composited over [ground]) so the ramp
     * could pool into the corner instead of stopping dead at it. With the ramp gone by the owner's
     * decision (see [bottomChrome]) that terminal value has nothing leading up to it, and leaving it
     * in place was measured on the 412dp frames as exactly the wedge it used to hide:
     *
     * | | Page | Stale shoulder | Reads as |
     * |---|---|---|---|
     * | Light | `#FBFAF8` (lum 250) | `#E8E7E6` (lum 231) | a hard 19-step grey arc at the bar's edge |
     * | Dark | `#121317` (lum 19) | `#0C0C0F` (lum 12) | a BLACK notch, darker than page and chrome both |
     *
     * The dark row is why this is not a rounding detail: a shoulder darker than both neighbours is a
     * hole again, just a dark one. Dropping the fill entirely is worse still and in the opposite
     * direction — measured `#FAFAFA` in BOTH themes, the window backdrop, i.e. the original defect.
     *
     * ## What the run down the edge looks like now
     * | | Shoulder | Chrome | Reads as |
     * |---|---|---|---|
     * | Light | `#FBFAF8` (L\* 98.3) | `#DEDCD6` (L\* 87.8) | the page continuing behind a dim slab |
     * | Dark | `#121317` (L\* 5.9) | `#1A1C20` (L\* 10.2) | the page continuing behind a lifted slab |
     *
     * page → shoulder → chrome, with the first two now EQUAL and nothing brighter than the page in
     * either theme. That equality is the assertion (`V2BarShoulderFillTest.theShoulderIsThePage_*`) and
     * it is stronger than the monotonic run it replaced: a ramp has a tolerance, an identity does not.
     *
     * ⚠️ Still a distinct accessor rather than `ground()` spelled out at the call site, and the reason
     * survives the ramp that motivated it. The call site's question is "what is behind the bar's
     * clipped corner", not "what colour is the page"; they agree today because the bar is the last
     * child of a `Column` whose content box ends at its top edge. Put anything else there — a shell
     * that lets content scroll under the chrome, a second bar — and this body changes while `ground()`
     * does not. Inlining it also deletes the only place the hole is explained.
     *
     * ⛔ Not for the docks. They have real content behind them; painting this under one would replace
     * the list they are supposed to be floating over with a flat page.
     */
    @Composable
    @ReadOnlyComposable
    fun bottomChromeShoulder(): Color = ground()

    /** Idle destination content, light theme. */
    private const val BOTTOM_CHROME_IDLE_ALPHA_LIGHT = 0.82f

    /** Idle destination content, dark theme — one notch lower; dark contrast reads hotter. */
    private const val BOTTOM_CHROME_IDLE_ALPHA_DARK = 0.75f
}
