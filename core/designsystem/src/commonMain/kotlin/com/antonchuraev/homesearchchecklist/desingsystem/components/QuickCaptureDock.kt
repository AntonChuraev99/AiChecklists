package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.gistiDockColor
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

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
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        color = gistiDockColor(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Hairline on the TOP edge only — the dock flows into the system-nav strip below it, and
            // a full border would draw a stray divider across that seam (same rule as
            // GistiGlassChatDock). A divider as the first child traces exactly that edge.
            HorizontalDivider(
                thickness = AppDimens.DividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            // Above the input, not below it: the input must stay the bottom-most element so it is the
            // one riding the keyboard, and chips under a focused field would be pushed off-screen.
            if (aboveInput != null) {
                Box(modifier = Modifier.padding(top = AppDimens.SpacingMd)) { aboveInput() }
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
                        vertical = AppDimens.SpacingMd,
                    ),
            )
        }
    }
}
