package com.antonchuraev.homesearchchecklist.feature.home.presentation.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_add_task_row
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
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * The "+ Add task" affordance — a plain, muted row that opens the shared `QuickCaptureDock`.
 *
 * This replaced the shell's floating "+" FAB. The owner picked Todoist's anatomy after seeing both
 * variants rendered: on the Inbox the row is the LAST element of the task list and scrolls with it,
 * so capture reads as "one more line at the end of what I already have" instead of as a button
 * hovering over the content.
 *
 * ## Why it must not look like a task
 * A first draft of this row carried the same container as [InboxTaskRow][com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox]
 * neighbours and blended straight into the list — a mockup review caught it. Three things keep it
 * legible as an ACTION:
 *  - **no checkbox** — the leading slot is a plus, which is the only glyph in this list that promises
 *    to create rather than to complete;
 *  - **no card, no border** — the surrounding rows are cards (or divider-separated lines); dropping
 *    the container is what makes this one read as chrome rather than as data;
 *  - **muted colour on BOTH the icon and the label** (`onSurfaceVariant`) — an accent-coloured plus
 *    next to primary-coloured text would compete with the checked/starred states the list already
 *    uses colour for.
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
 */
@Composable
internal fun AddTaskRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            // A tap target, not a text line: the label alone is ~24dp tall, which is half the
            // project's MinTouchTarget.
            .heightIn(min = AppDimens.MinTouchTarget)
            .padding(
                horizontal = AppDimens.SpacingSm,
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
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // fillMaxWidth is mandatory for any Text inside a HorizontalPager — the Inbox mounts this
            // row inside one, and without it the label overflows the page instead of wrapping
            // (rule ui-card-patterns).
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}
