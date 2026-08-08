package com.antonchuraev.homesearchchecklist.feature.home.presentation.projects

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.main_create_checklist
import aichecklists.core.designsystem.generated.resources.main_error_description
import aichecklists.core.designsystem.generated.resources.main_error_retry
import aichecklists.core.designsystem.generated.resources.main_error_title
import aichecklists.core.designsystem.generated.resources.projects_add_checklist
import aichecklists.core.designsystem.generated.resources.projects_complete
import aichecklists.core.designsystem.generated.resources.projects_empty_description
import aichecklists.core.designsystem.generated.resources.projects_empty_title
import aichecklists.core.designsystem.generated.resources.projects_open_count
import aichecklists.core.designsystem.generated.resources.projects_title
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The v2 "Projects" tab: a flat list of checklists, each with the number of tasks left in it.
 *
 * ## Why a list and not the v1 cards
 * The v1 home screen shows cards with cover images and progress bars — a gallery, which is the right
 * shape when that screen IS the app. In v2 it is one tab of four, and the question it has to answer
 * in one glance is "which list still has work in it". Todoist answers exactly that in its Overview
 * (`docs/reference/todoist-ui-reference/06-overview-settings-and-projects.png`): name on the left,
 * count on the right, hairlines between rows, nothing else. The card gallery stays available to
 * anyone on the classic layout — this screen deliberately does not replace it, it is a different
 * route ([com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute.Projects]).
 *
 * ## Why the whole row is one click target
 * Unlike a checklist ITEM (which splits 30/70 between toggling and opening — see
 * `.claude/rules/ui-card-patterns.md`), a project row has exactly one action: open it. A split here
 * would invent a second gesture with nothing to bind it to.
 *
 * @param contentBottomPadding inset the v2 shell reserves for the part of its raised AI button that
 *   overhangs the bottom bar and is drawn over this screen. Defaults to 0.dp so previews and tests
 *   get the plain layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    state: ProjectsScreenState,
    onIntent: (ProjectsIntent) -> Unit,
    contentBottomPadding: Dp = 0.dp,
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView(AnalyticsScreens.PROJECTS) }

    val content = state as? ProjectsScreenState.Content

    AppScaffold(
        title = stringResource(Res.string.projects_title),
        // Matches the Inbox tab: start-aligned, so the two tabs do not read as two different apps.
        startAlignedTitle = true,
        actions = {
            // Only when there is already a list. On the empty state the EmptyState's own CTA is the
            // create affordance, and two buttons for one action on one screen is noise.
            if (content?.projects?.isNotEmpty() == true) {
                IconButton(onClick = { onIntent(ProjectsIntent.OnCreateChecklistClick) }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(Res.string.projects_add_checklist),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        when {
            // The read failed — never an empty list, which would claim the user has no checklists.
            state is ProjectsScreenState.Error -> EmptyState(
                icon = Icons.Outlined.ErrorOutline,
                title = stringResource(Res.string.main_error_title),
                description = stringResource(Res.string.main_error_description),
                action = {
                    AppButton(
                        text = stringResource(Res.string.main_error_retry),
                        onClick = { onIntent(ProjectsIntent.OnRetry) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )

            content == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            content.projects.isEmpty() -> EmptyState(
                icon = Icons.Outlined.ChecklistRtl,
                title = stringResource(Res.string.projects_empty_title),
                description = stringResource(Res.string.projects_empty_description),
                action = {
                    AppButton(
                        text = stringResource(Res.string.main_create_checklist),
                        onClick = { onIntent(ProjectsIntent.OnCreateChecklistClick) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    // fill → wrapContentWidth → cap, the same order and for the same reason as the
                    // Inbox tab's list (see InboxScreen for the full write-up): fillMaxSize pins
                    // minWidth == maxWidth to the pane, so a widthIn(max) alone is coerced away and
                    // the cap silently does nothing. wrapContentWidth relaxes the minimum back to 0
                    // and centres the capped column in the pane it still holds.
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .adaptiveContentWidth(),
                contentPadding = PaddingValues(bottom = AppDimens.SpacingXl + contentBottomPadding),
            ) {
                items(content.projects, key = { it.checklistId }) { project ->
                    ProjectRowItem(
                        project = project,
                        onClick = { onIntent(ProjectsIntent.OnProjectClick(project.checklistId)) },
                    )
                    // Hairlines, not gaps: the rows are one continuous list, which is what makes
                    // a long list scannable. A trailing rule under the last row would read as
                    // "the list continues below".
                    if (project != content.projects.last()) {
                        HorizontalDivider(
                            thickness = AppDimens.DividerThickness,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(start = ProjectRowLeadingInset),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One project row: leading icon, name, trailing count.
 *
 * The count is the only piece of state here, so it carries the emphasis — `titleMedium` on the
 * surface colour when there is work left, and a tick when there is not. A zero would be technically
 * accurate and useless: "0" and "done" look the same at a glance only if you already know the list.
 */
@Composable
private fun ProjectRowItem(
    project: ProjectRow,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ProjectRowMinHeight)
            // The whole row, not the text: a click target that stops at the label leaves most of the
            // row dead, and on a list this sparse that is most of the screen.
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    ) {
        Icon(
            imageVector = Icons.Outlined.ChecklistRtl,
            // The name next to it already says what this is; describing the icon would make TalkBack
            // read the row twice.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(AppDimens.IconSizeSm),
        )

        Text(
            text = project.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (project.isComplete) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = stringResource(Res.string.projects_complete),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(AppDimens.IconSizeSm),
            )
        } else {
            Text(
                text = pluralStringResource(
                    Res.plurals.projects_open_count,
                    project.openCount,
                    project.openCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val ProjectRowMinHeight = 56.dp

/** Divider starts past the icon so the rule aligns with the text column, not with the icon gutter. */
private val ProjectRowLeadingInset = 52.dp
