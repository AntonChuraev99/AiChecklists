package com.antonchuraev.homesearchchecklist.desingsystem.components

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
