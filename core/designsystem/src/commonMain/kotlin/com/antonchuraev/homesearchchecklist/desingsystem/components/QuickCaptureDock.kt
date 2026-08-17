package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.gistiDockColor
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.ChatSurfaceTone
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalChatSurfaceTone

/**
 * Opacity of the scrim a host paints over its CONTENT while [QuickCaptureDock] is up.
 *
 * Lives next to the dock so the Inbox and the Calendar cannot drift into two different depths for
 * the same interruption — they paint it in two different ways (an overlay Box vs. a draw layer on
 * the pager, because those two screens dismiss the dock through different modifier chains), and the
 * shared number is the only thing keeping the RESULT identical.
 *
 * Above M3's 32% default: this scrim sits under a dock that is itself a light surface, and at 32%
 * the two read as one page — which is the defect it was introduced to fix. The host must apply it to
 * the content only: the dock, the snackbar and the system-nav strip stay bright (rule `designsystem`
 * — a dimmed nav strip breaks the continuous surface the strip and the dock form).
 */
const val CaptureDockScrimAlpha: Float = 0.45f

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
