package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.gistiDockColor
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.ChatSurfaceTone
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalChatSurfaceTone
import kotlin.math.roundToInt

/**
 * Opacity of the scrim a host paints over its CONTENT while [QuickCaptureDock] is up.
 *
 * Lives next to the dock so the Inbox and the Calendar cannot drift into two different depths for
 * the same interruption — they paint it in two different ways (an overlay Box vs. a draw layer on
 * the pager, because those two screens dismiss the dock through different modifier chains), and the
 * shared number is the only thing keeping the RESULT identical.
 *
 * Above M3's 32% default: this scrim sits under a dock that is itself a light surface, and at 32%
 * the two read as one page — which is the defect it was introduced to fix.
 *
 * The host applies it to the CONTENT and to the strip BEHIND the dock, never over the dock: the dock,
 * the snackbar and the system-nav strip stay bright (rule `designsystem` — a dimmed nav strip breaks
 * the continuous surface the strip and the dock form). See [captureDockScrimColor] for why the second
 * of those is mandatory rather than a refinement.
 */
const val CaptureDockScrimAlpha: Float = 0.45f

/**
 * The scrim colour itself, resolved — [CaptureDockScrimAlpha] on the theme's `scrim` role.
 *
 * Exists so the two hosts, and the shell's screenshot fixture, cannot spell the same dim three ways.
 * Each host paints it in TWO places and both are mandatory:
 *
 *  1. **over the content**, where it is the dim itself (an overlay `Box` on the Inbox, a
 *     `drawWithContent` layer on the Calendar — those two screens dismiss the dock through different
 *     modifier chains, so the mechanism differs and only the colour is shared);
 *  2. **behind the dock**, as [QuickCaptureDock]'s own `modifier`.
 *
 * The second one is not a nicety. The dock is a `SheetTop` `Surface`, so its two top corners are
 * clipped away and whatever lies behind them shows through — and what lies behind them is the
 * scaffold's container, i.e. the page, at full brightness, because the content scrim stops at the
 * dock's top edge (that boundary is deliberate: it is what keeps the dock, the snackbar and the
 * system-nav strip out of the dim at any keyboard height). Undimmed page against a 45%-dimmed page
 * is ΔL\* ≈ 33 in light: the "two light corners beside the dock" reported from a Pixel 9. Painting
 * the same scrim behind the dock makes the shoulders the dimmed page they are supposed to be, and it
 * costs nothing anywhere else — the dock's own surface is opaque and drawn over it.
 *
 * ⚠️ A host that mounts [QuickCaptureDock] WITHOUT dimming its page must not pass this: there the
 * shoulders should reveal the undimmed page, and a dim behind them would be the same defect
 * inverted. That is why this is the host's call and not something the dock does to itself.
 *
 * ⚠️ The two are exclusive by ZONE, and [captureScrimBottomPx] is what keeps them so: tile 1 owns
 * every pixel ABOVE the dock's top edge, tile 2 owns every pixel at or below it. Where they overlap
 * the alpha composites twice — 45% over 45% is 70% — and that is a visibly different colour, not a
 * rounding difference.
 */
@Composable
@ReadOnlyComposable
fun captureDockScrimColor(): Color =
    MaterialTheme.colorScheme.scrim.copy(alpha = CaptureDockScrimAlpha)

/**
 * Sentinel for [captureScrimBottomPx]: the dock has not reported its position yet.
 *
 * "No ceiling" rather than "ceiling at 0", so the first frame of an opening dock dims its page
 * normally instead of flashing undimmed for one layout pass.
 */
const val CaptureDockTopUnmeasured: Float = Float.POSITIVE_INFINITY

/**
 * How far down the window a host may paint the dim it lays OVER its page, in root pixels.
 *
 * [contentTopPx] is where the host's own content scrim begins (the scaffold's content slot), so it
 * is the height of the chrome tile above it. [dockTopPx] is where [QuickCaptureDock]'s slot begins,
 * measured FROM THE DOCK — [CaptureDockTopUnmeasured] until it has been.
 *
 * ## Why the dock's own position has to be measured, when the slot boundary looks like enough
 * It normally is: `AppScaffold` pads its content by the bottom bar's height, so the content slot's
 * bottom edge already IS the dock's top edge, and the chrome tile stops where the content slot
 * starts. That holds while the scaffold has room for all three.
 *
 * It stops holding the moment `topBar + bottomBar > windowHeight`, which a tall dock reaches as soon
 * as the keyboard is up: `Scaffold` places the top bar at 0 and the bottom bar at
 * `height - bottomBarHeight` — OVERLAPPING, with the dock drawn last and therefore on top — and
 * `Modifier.padding` collapses the content slot to zero height at the top bar's offset, which is now
 * BELOW the dock's top edge. The chrome tile, sized from that offset, then runs past the dock: on a
 * Pixel 9 with a Russian keyboard it reached 65px into it, dimming the dock's first 24dp and laying a
 * SECOND scrim over the shoulders behind it (`#FBFAF8` → `#8A8988` → `#4C4B4B`). Reported as "a black
 * bar covering the top of the sheet", 2026-08-17.
 *
 * So the two numbers answer two different questions — "where does the chrome end" and "where does the
 * dock begin" — and only the second one is the boundary the dim must respect. They coincide right up
 * until the layout is over-constrained, which is exactly when the defect appears.
 *
 * The host's CONTENT scrim needs no such cap: it is bounded by the content slot's own size, which is
 * either inside `[contentTop, dockTop]` or zero-height, never past the dock.
 */
fun captureScrimBottomPx(contentTopPx: Float, dockTopPx: Float): Float =
    minOf(contentTopPx, dockTopPx)

/**
 * The chrome tile of the capture scrim: the dim over the status-bar zone and the app bar, which belong
 * to the scaffold and are out of reach from inside its content slot.
 *
 * Mount it as a sibling ABOVE the scaffold, inside the host's root `Box`. Both hosts had this as a
 * hand-written `Box` with the same three modifiers and the same height arithmetic; one component is
 * what keeps them one depth of dim, and it is where the deferred read below lives.
 *
 * ## Why the two positions arrive as lambdas
 * [dockTopPx] changes on EVERY FRAME of the keyboard animation — the dock rides the ime inset, so its
 * `positionInRoot().y` is a new number each pass — and [contentTopPx] moves with it. Read in the
 * host's composable body, as `captureScrimBottomPx(contentTopPx, dockTopPx)` was, those two
 * subscribed the whole screen: the scaffold, the toolbar, the pager and the list were invalidated once
 * per frame for the entire keyboard animation, which is the documented
 * `windowinsets-hoist-widens-recomposition-scope` trap in this repo's own memory.
 *
 * As lambdas the values are read inside [Modifier.layout], i.e. in the LAYOUT phase of this one node.
 * Nothing recomposes; the tile re-measures. Same pixels, one node instead of a screen.
 *
 * ## The gate is inside the layout too
 * "Nothing measured yet / nothing to dim" resolves to a zero-height tile rather than to `if (…)`
 * around the call, because an `if` in the host's body is a composition read of the same frame-varying
 * state and would put the whole cost straight back. A zero-height, background-only node draws nothing
 * and, having no pointer input, takes no part in hit-testing either — which is the other reason this
 * tile can sit over the toolbar at all: its actions stay pressable through the dim.
 *
 * @param visible the host's own dock flag. A plain Boolean, and the ONE thing here that is allowed to
 *   be read in composition: it flips once per open and once per close, not once per frame.
 */
@Composable
fun CaptureChromeScrim(
    visible: Boolean,
    color: Color,
    contentTopPx: () -> Float,
    dockTopPx: () -> Float,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val height = captureScrimBottomPx(contentTopPx(), dockTopPx())
                    .coerceIn(0f, constraints.maxHeight.toFloat())
                    .roundToInt()
                val placeable = measurable.measure(
                    constraints.copy(minHeight = height, maxHeight = height),
                )
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
            .background(color),
    )
}

/**
 * The v2 quick-capture affordance: a raised bottom dock with one input, raised by the shell's "+" FAB.
 *
 * Shared by the Inbox tab and the Calendar/Today tab. Deliberately ONE component rather than a copy
 * per screen: three separate defects were fixed inside this dock during the v2 work (the keyboard
 * inset, the `aspectRatio` blow-up of the Add button, and the scrim swallowing the list's scroll),
 * and a second copy would have kept none of those fixes.
 *
 * ## Host contract — read before mounting it
 * - MUST be placed in `AppScaffold`'s `bottomBar` slot. That slot is the only one that applies
 *   `ime ∪ navigationBars`, i.e. the only place automatically lifted above the keyboard. Inside the
 *   content it needs a manual `imePadding()`, and because a bottom inset counts toward a composable's
 *   measured HEIGHT, the row then claims the keyboard's height out of its parent Column and slides
 *   off-screen — the exact defect this dock shipped with once.
 * - It adds NO inset of its own, and reserves no FAB band: the host hides its FABs while the dock is
 *   up, so reserving space for a stack that is not on screen would float the input above the edge.
 * - Pass [captureDockScrimColor] as this component's `modifier` background whenever the host dims its
 *   page. The dock's `SheetTop` corners are clipped away, and the strip behind them is OUTSIDE the
 *   content scrim, so without it those two corners show the page at full brightness beside a dimmed
 *   one — measured ΔL\* +41 in light, reported from a device as two bright nicks.
 * - In the SAME chain, report this node's `positionInRoot().y` and cap every tile the host paints over
 *   its page at that y — see [captureScrimBottomPx]. The dock's top edge is not derivable from the
 *   scaffold's slots: once the keyboard makes `topBar + bottomBar` taller than the window, the two
 *   bars overlap and the content slot collapses BELOW the dock, so a tile sized from it paints over
 *   the dock and doubles up on the shoulder this same modifier is dimming.
 * - Compose it only while it is open. The keyboard is raised via a `LaunchedEffect(Unit)`, which is
 *   correct exactly once per appearance; keeping it mounted-but-hidden fires it at the wrong time.
 *
 * The dock STAYS open after each add — capture is usually more than one item, and closing after every
 * send would cost a FAB tap per task. Dismissal is explicit: BACK, or a tap outside.
 *
 * Styled as a raised dock (surface + top hairline + rounded top corners) rather than as a bare row,
 * because it APPEARS over a list: with no edge of its own it reads as a list row that grew an input.
 */
@Composable
fun QuickCaptureDock(
    text: String,
    onTextChange: (String) -> Unit,
    onAdd: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    /**
     * Character range of [text] to tint — the phrase Smart-Add recognised in what the user typed.
     *
     * A plain range and not the parse result itself, for the same reason the two slots below are
     * slots: the token is a feature-layer domain type with no business in the design system. It is
     * also the ONLY shape that lets both hosts stay identical here — the Inbox tab and the
     * Calendar tab each derive it from their own draft with the same extension.
     */
    highlightRange: IntRange? = null,
    /**
     * Content rendered between the top hairline and the input — in practice the task-create chip row
     * (reminder presets, Important, Repeat).
     *
     * A slot rather than a typed parameter because those chips are driven by feature-layer domain
     * types (draft state, repeat config, the Smart-Add parse) that have no business in the design
     * system. NULL by default rather than an empty lambda: an empty lambda would still occupy a
     * padded slot and silently retune the spacing of a host that asked for nothing. A host that only
     * wants a one-line capture field is therefore unchanged — and this component stays the SINGLE
     * owner of the dock's keyboard-inset behaviour (three separate inset defects were fixed inside
     * it; a second dock surface would inherit none of those fixes).
     */
    aboveInput: (@Composable () -> Unit)? = null,
    /**
     * Content rendered BELOW the input — in practice the [SourceRow] of AI entry points
     * (Photo / PDF / Web Link / Voice).
     *
     * ## Why below, when the comment two screens down says "above"
     * That rule was written when this dock lived INSIDE the scaffold content and carried its own
     * `imePadding()`; there, anything under a focused field really was pushed off-screen. Today the
     * dock sits in `AppScaffold`'s `bottomBar`, which applies `ime ∪ navigationBars` OUTSIDE this
     * node — the whole dock is lifted above the keyboard as one unit, so a row under the input
     * rides up with it rather than sliding beneath it.
     *
     * It is below rather than above deliberately: the input is the primary action and must stay
     * adjacent to the send button, while these four are a parallel offer ("or hand me this
     * instead"). Putting them above would wedge them between the reminder chips and the field.
     *
     * ⚠️ Real-IME behaviour is the one thing a JVM screenshot cannot prove. If a device run ever
     * shows the field failing to track the keyboard, the fix is to pass this same content as
     * [aboveInput] instead — the product requirement is only that the row is ALWAYS VISIBLE while
     * the dock is up, which either slot satisfies.
     */
    belowInput: (@Composable () -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // ── Both rows, always ────────────────────────────────────────────────────────────────────────
    // This dock briefly dropped the DATE PRESETS whenever the window was under 590dp tall or
    // `fontScale >= 1.3`, to buy list space back on a short viewport. That was wrong twice over:
    //
    //  - `fontScale >= 1.3` is a stock accessibility setting and 320×568 is an ordinary small phone,
    //    so the "degradation" fired on everyday devices — the recorded golden of a 320dp phone at the
    //    DEFAULT text scale had no presets in it. Setting a due date in one tap is not a large-screen
    //    luxury, and "it is still reachable in the detail sheet" is the argument for removing any
    //    shortcut anywhere.
    //  - The presets are a `LazyRow` that already scrolls sideways, so what they needed was never
    //    width. It was ~48dp of HEIGHT, and part of that is available from the dock's own padding.
    //
    // The compression below pays for part of it and no more: both slot gaps tighten from SpacingMd
    // to SpacingSm whenever both rows are mounted, which returns 8dp of the ~48dp the row costs. So
    // the dock IS taller than the evicting version by roughly 40dp, deliberately. Measured totals
    // with both rows: 201dp at 320×568 / fontScale 1.0, 213dp at 360×640 / fontScale 1.3 — against a
    // ~250dp keyboard that leaves ~105–177dp of list, which is one to two rows. That is the right
    // way round: while the dock is up the user is typing into it, not reading the list behind a 45%
    // scrim, and losing a row of a list you are not reading costs less than losing a due date you
    // came to set.
    val bothRows = aboveInput != null && belowInput != null
    val slotGap = if (bothRows) AppDimens.SpacingSm else AppDimens.SpacingMd

    Surface(
        // Tone AND radius come from the shared bottom-chrome tokens. This dock's bottom edge IS the
        // navigation bar's top edge, so a local 20dp against the bar's 24dp drew a visible step
        // between two surfaces that are meant to read as one slab — see AppShapeTokens.SheetTop.
        color = gistiDockColor(),
        shape = AppShapeTokens.SheetTop,
        modifier = modifier.fillMaxWidth(),
    ) {
        // Both slots and the field are drawn on the bottom chrome, not on the page — the chips the
        // host passes into `aboveInput` are the same ones the chat dock renders. See AppChatColors.
        CompositionLocalProvider(LocalChatSurfaceTone provides ChatSurfaceTone.BottomChrome) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Hairline on the TOP edge only — the dock flows into the system-nav strip below it, and
                // a full border would draw a stray divider across that seam (same rule as
                // GistiGlassChatDock). A divider as the first child traces exactly that edge.
                //
                // `bottomChromeSeam()`, not `outlineVariant`: measured on the recorded 360dp frames,
                // the line was `#E2E0DB` on a `#DEDCD6` dock — ΔL* +1.4 (1.04 : 1), i.e. the
                // edge-tracing line was the same colour as the edge it traced, and the whole
                // separation fell back on the dock's own −10.5 step off the page. The seam resolves to
                // `outline` in light (3.33 : 1 on this surface) and keeps `outlineVariant` in dark,
                // where the old value was already ΔL* +9.2 and needed no help.
                //
                // The 2′ group's token, NOT `dockedSeam()`: the two bodies agree today, but re-tuning
                // the bottom chrome must not repaint the share-sheet / preview CTAs, and re-tuning
                // those must not repaint this edge. See AppSurface.bottomChromeSeam.
                HorizontalDivider(
                    thickness = AppDimens.DividerThickness,
                    color = AppSurface.bottomChromeSeam(),
                )
                // The reminder/priority chips sit above the input; the AI source row sits below it.
                // See [belowInput]'s KDoc for why "below" is safe here and was not in the dock's
                // previous, content-hosted incarnation.
                if (aboveInput != null) {
                    Box(modifier = Modifier.padding(top = slotGap)) { aboveInput() }
                }
                AddItemInputField(
                    text = text,
                    onTextChange = onTextChange,
                    onAdd = onAdd,
                    placeholder = placeholder,
                    highlightRange = highlightRange,
                    focusRequester = focusRequester,
                    modifier = Modifier
                        .adaptiveContentWidth()
                        .padding(
                            horizontal = AppDimens.ScreenPaddingHorizontal,
                            // 4dp tighter than the input's own SpacingMd when a second row follows —
                            // the 4dp the source pills took to reach a 48dp touch target comes from
                            // here rather than from the pill, which is what keeps the pills at full size.
                            vertical = if (belowInput != null) AppDimens.SpacingSm else AppDimens.SpacingMd,
                        ),
                )
                if (belowInput != null) {
                    Box(
                        modifier = Modifier
                            .adaptiveContentWidth()
                            .padding(
                                start = AppDimens.ScreenPaddingHorizontal,
                                end = AppDimens.ScreenPaddingHorizontal,
                                bottom = slotGap,
                            ),
                    ) { belowInput() }
                }
            }
        }
    }
}
