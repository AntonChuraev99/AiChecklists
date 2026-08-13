package com.antonchuraev.homesearchchecklist.feature.home.presentation.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_add_task_row
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Height of the [prominent][AddTaskRow] variant. Above the 48dp minimum on purpose: on an EMPTY page
 * this row is the only action on screen, and at minimum height under a full-height empty state it
 * read as a caption rather than as the one thing to press.
 */
private val ProminentRowHeight = 56.dp

/**
 * The "+ Add task" affordance — a tonal pill that opens the shared `QuickCaptureDock`.
 *
 * This replaced the shell's floating "+" FAB. The owner picked Todoist's anatomy after seeing both
 * variants rendered: on the Inbox the row is the LAST element of the task list and scrolls with it,
 * so capture reads as "one more line at the end of what I already have" instead of as a button
 * hovering over the content.
 *
 * ## Why it must not look like a task
 * A first draft of this row carried the same container as [InboxTaskRow][com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox]
 * neighbours and blended straight into the list — a mockup review caught it. Two things keep it
 * legible as an ACTION:
 *  - **no checkbox** — the leading slot is a plus, which is the only glyph in this list that promises
 *    to create rather than to complete;
 *  - **muted colour on BOTH the icon and the label** (`onSurfaceVariant`) — an accent-coloured plus
 *    next to primary-coloured text would compete with the checked/starred states the list already
 *    uses colour for.
 *
 * ## Why it now HAS a container (owner review, 2026-08-13)
 * It shipped containerless, on the theory that dropping the container is what makes a row read as
 * chrome rather than as data. On a device that lost the wrong half of the trade: against
 * `background` a muted label with no fill is the lowest-contrast element on the screen, and the
 * owner's verdict on both states was that it blends into the page ("сливается с фоном"). Todoist —
 * the reference this whole anatomy comes from — fills the same row with a neutral container for
 * exactly that reason.
 *
 * `surfaceContainerHigh`, deliberately, and NOT the surface the task rows use: the cards are
 * `surfaceContainerLowest` (white on light), so a *higher* container separates this row from both
 * the page AND its neighbours, and keeps the plus/label muted instead of spending brand colour on a
 * secondary action (the shell's AI FAB is the one primary-coloured circle on this screen).
 *
 * The caller adds the vertical gap that separates it from the last row (the list's own arrangement
 * spacing is not enough on its own in the compact layout, which uses zero).
 *
 * Ripple is deliberately KEPT here, unlike on the task rows: a task row's feedback is the state
 * change the user can see (the checkbox flips, the sheet appears), while this row's outcome — a dock
 * sliding up from the bottom edge — starts far away from the finger, so the press needs its own
 * acknowledgement. It is clipped to `shapes.small` so the ripple cannot bleed past the row.
 *
 * Lives in a shared package rather than inside `inbox/` because the Calendar tab mounts the very same
 * row: it lost the same "+" FAB, and that FAB was the ONLY way to reach the capture dock there.
 * Copying the composable instead would let the two drift.
 *
 * @param onClick report the tap to the host — this composable never opens the dock itself. The dock's
 *   open flag is shell state (the shell hides its own chrome while the dock is up), so a screen-local
 *   flag would leave chrome floating over the dock it raised.
 * @param prominent the EMPTY-page variant: taller and set in `titleMedium` with a full-strength
 *   label. A parameter rather than a second composable — a twin would inherit none of the three
 *   defects already fixed inside this one (ripple clipped to the shape, a tap target above the label's
 *   own height, `fillMaxWidth` on a Text mounted inside a HorizontalPager). Same fill in both
 *   variants on purpose: it is one control that grows, not two different buttons, so a page that
 *   fills up must not appear to swap its add affordance for another.
 */
@Composable
internal fun AddTaskRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // clip BEFORE the fill: background() paints the node's whole rect, so an unclipped fill
            // draws a square behind the rounded ripple.
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            // A tap target, not a text line: the label alone is ~24dp tall, which is half the
            // project's MinTouchTarget.
            .heightIn(min = if (prominent) ProminentRowHeight else AppDimens.MinTouchTarget)
            .padding(
                // Wider than the old containerless row: with a fill, the glyph sitting 8dp from the
                // pill's edge reads as cramped against the same 16dp the cards inset their text by.
                horizontal = AppDimens.SpacingLg,
                vertical = AppDimens.SpacingSm,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            // The label below is the accessible name; a description here would make a screen reader
            // announce "Add task, Add task".
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(AppDimens.IconSizeMd),
        )
        Text(
            text = stringResource(Res.string.inbox_add_task_row),
            style = if (prominent) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            // Full-strength on an empty page: there is nothing else to read there, so the muted
            // variant that keeps this row subordinate to a list of tasks has no list to defer to.
            color = if (prominent) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            // fillMaxWidth is mandatory for any Text inside a HorizontalPager — the Inbox mounts this
            // row inside one, and without it the label overflows the page instead of wrapping
            // (rule ui-card-patterns).
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}
