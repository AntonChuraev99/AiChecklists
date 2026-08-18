package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_display_group_by_date
import aichecklists.core.designsystem.generated.resources.inbox_display_layout
import aichecklists.core.designsystem.generated.resources.inbox_display_layout_cards
import aichecklists.core.designsystem.generated.resources.inbox_display_layout_compact
import aichecklists.core.designsystem.generated.resources.inbox_display_show_completed
import aichecklists.core.designsystem.generated.resources.inbox_display_sort
import aichecklists.core.designsystem.generated.resources.inbox_display_sort_manual
import aichecklists.core.designsystem.generated.resources.inbox_display_sort_name
import aichecklists.core.designsystem.generated.resources.inbox_display_sort_priority
import aichecklists.core.designsystem.generated.resources.inbox_display_title
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxDisplayOptions
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxLayout
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxSort
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppSwitch
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AdaptiveSheetOrDialog
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * The "Display" sheet, modelled on Todoist's (`docs/reference/todoist-ui-reference/`): layout picker
 * with visual previews first, then the completed-tasks toggle, then sorting.
 *
 * Two deliberate departures from the reference:
 *
 * 1. **No "Done" button.** Todoist commits its sheet; every option here is one independent persisted
 *    value, so each tap applies immediately and the list re-renders behind the sheet. Seeing the
 *    change IS the confirmation, and the other option is always one tap away — a commit step would
 *    hide the effect until after the sheet closes.
 * 2. **No Board layout and no grouping/filter sections.** Both need something a flat checklist page
 *    does not have (columns come from sections; grouping by date/label needs those fields on a task).
 *    Offering rows that cannot change anything is worse than a shorter sheet.
 *
 * Presented through [AdaptiveSheetOrDialog] like [InboxTaskSheet] — a raw `ModalBottomSheet` slid a
 * phone-shaped sheet across the whole window on a tablet (and over the permanent drawer), while the
 * sibling sheets reachable from the SAME toolbar rendered as centred dialogs.
 */
@Composable
fun InboxDisplayOptionsSheet(
    options: InboxDisplayOptions,
    onIntent: (InboxIntent) -> Unit,
) {
    AdaptiveSheetOrDialog(
        onDismiss = { onIntent(InboxIntent.OnDisplayOptionsDismiss) },
        // No `title` argument: the heading below has to render on Compact too (AdaptiveSheetOrDialog
        // forwards `title` only to the AlertDialog branch), and passing both shows it twice on
        // Medium/Expanded. Same call shape as InboxTaskSheet's move picker.
        //
        // No container colour either — AdaptiveSheetOrDialog exposes none, so both branches paint the
        // M3 defaults (surfaceContainerLow on the sheet, surfaceContainerHigh on the dialog) instead
        // of the `surface` this repo passes to raw ModalBottomSheets. Deliberate: the sibling
        // InboxTaskSheet, reachable from the SAME toolbar, sits on those same defaults, and two
        // sheets on one screen in two different colours is the worse defect. Moving both onto
        // `surface` means a containerColor parameter on the shared container — NOT a background under
        // this content, which the sheet's own Surface simply draws over.
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Scrollable because the sheet is taller than a short window (phone landscape, or any
                // window at a large font scale): without it the Sort section is clipped below the
                // fold with no way to reach it.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                // The sheet's own content is not inset by the scaffold — without this the last row
                // sits under the gesture bar on a gesture-nav device.
                .navigationBarsPadding()
                .padding(bottom = AppDimens.SpacingLg),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        ) {
            Text(
                text = stringResource(Res.string.inbox_display_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            SectionLabel(stringResource(Res.string.inbox_display_layout))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
            ) {
                LayoutOption(
                    label = stringResource(Res.string.inbox_display_layout_cards),
                    selected = options.layout == InboxLayout.CARDS,
                    // Preview rows are spaced and rounded — the card list's own rhythm.
                    previewRowGap = 4.dp,
                    previewRowHeight = 8.dp,
                    onClick = { onIntent(InboxIntent.OnLayoutSelected(InboxLayout.CARDS)) },
                    modifier = Modifier.weight(1f),
                )
                LayoutOption(
                    label = stringResource(Res.string.inbox_display_layout_compact),
                    selected = options.layout == InboxLayout.COMPACT,
                    // Touching, thinner rows — what compact actually looks like.
                    previewRowGap = 1.dp,
                    previewRowHeight = 5.dp,
                    onClick = { onIntent(InboxIntent.OnLayoutSelected(InboxLayout.COMPACT)) },
                    modifier = Modifier.weight(1f),
                )
            }

            ToggleRow(
                label = stringResource(Res.string.inbox_display_show_completed),
                checked = options.showCompleted,
                onCheckedChange = { onIntent(InboxIntent.OnShowCompletedChanged(it)) },
            )

            // The fourth block, and deliberately a row on THIS sheet rather than a surface of its
            // own: grouping is a projection over the list exactly like the sort below it, so it
            // belongs beside the other three view settings. A separate control somewhere else would
            // be a second place to look for "why does my list look like that".
            ToggleRow(
                label = stringResource(Res.string.inbox_display_group_by_date),
                checked = options.groupByDate,
                onCheckedChange = { onIntent(InboxIntent.OnGroupByDateChanged(it)) },
            )

            SectionLabel(stringResource(Res.string.inbox_display_sort))

            Column(modifier = Modifier.selectableGroup()) {
                SortOption(
                    label = stringResource(Res.string.inbox_display_sort_manual),
                    selected = options.sort == InboxSort.MANUAL,
                    onClick = { onIntent(InboxIntent.OnSortSelected(InboxSort.MANUAL)) },
                )
                SortOption(
                    label = stringResource(Res.string.inbox_display_sort_name),
                    selected = options.sort == InboxSort.NAME,
                    onClick = { onIntent(InboxIntent.OnSortSelected(InboxSort.NAME)) },
                )
                SortOption(
                    label = stringResource(Res.string.inbox_display_sort_priority),
                    selected = options.sort == InboxSort.PRIORITY,
                    onClick = { onIntent(InboxIntent.OnSortSelected(InboxSort.PRIORITY)) },
                )
            }
        }
    }
}

/**
 * A label plus a switch, applied on tap like every other row on this sheet.
 *
 * One composable for both toggles: the second one arrived with date grouping, and a copied Row is
 * how two controls on the same sheet end up with different label styles and different heights.
 */
@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A layout choice drawn as a miniature of the list it produces, the way Todoist draws its three.
 *
 * The preview is generated from the same two numbers that describe the difference (row height and
 * gap) rather than being an asset: an illustration would keep showing the old spacing the moment the
 * real list's rhythm changes, and nobody would notice.
 */
@Composable
private fun LayoutOption(
    label: String,
    selected: Boolean,
    previewRowGap: androidx.compose.ui.unit.Dp,
    previewRowHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Column(
        modifier = modifier
            // selectable, not clickable: this is one of a pair, and Role.RadioButton is what tells
            // TalkBack the tap CHANGES a choice rather than performing an action.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = AppDimens.SpacingXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
    ) {
        Column(
            modifier = Modifier
                // 4:3 preview card, capped in height. The cap MUST come before aspectRatio and the
                // width must stay unpinned (no fillMaxWidth): a fixed width leaves the ratio no
                // freedom, so aspectRatio discards the height bound and the two previews grow with
                // the sheet — on a wide-but-short window they took it over and pushed Sort off the
                // end. Unbounded on the width side, aspectRatio falls back to the height cap and
                // narrows instead; the parent centres what is left.
                .heightIn(max = LayoutPreviewMaxHeight)
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(AppDimens.SpacingSm))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(AppDimens.SpacingSm),
            verticalArrangement = Arrangement.spacedBy(previewRowGap),
        ) {
            repeat(LayoutPreviewRowCount) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(previewRowHeight)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        RadioButton(
            selected = selected,
            // null: the whole column above is the selectable, so a second target here would give
            // TalkBack two ways to say the same thing and split the touch area.
            onClick = null,
        )
    }
}

private const val LayoutPreviewRowCount = 4

/**
 * Ceiling on the 4:3 preview's height, and therefore on its width too (see [LayoutOption]).
 *
 * Each preview gets a weighted half of the sheet minus the page and row padding, so on a 411dp
 * window it wants ~137dp of height and the cap does NOT bind — that reference phone looks exactly as
 * drawn. It is not a phone-only escape hatch though: the cap starts binding at roughly 420dp of
 * window width, which covers large phones as well as every tablet sheet, and there the preview stops
 * growing and NARROWS inside its slot instead, centred by the parent. That is the intended
 * behaviour — uncapped previews grew with the sheet and pushed the Sort section off a wide-but-short
 * window.
 */
private val LayoutPreviewMaxHeight = 140.dp

@Composable
private fun SortOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = AppDimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
