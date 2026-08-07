package com.antonchuraev.homesearchchecklist.feature.home.presentation.projects

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.main_create_checklist
import aichecklists.core.designsystem.generated.resources.main_error_description
import aichecklists.core.designsystem.generated.resources.main_error_retry
import aichecklists.core.designsystem.generated.resources.main_error_title
import aichecklists.core.designsystem.generated.resources.projects_add_checklist
import aichecklists.core.designsystem.generated.resources.projects_complete
import aichecklists.core.designsystem.generated.resources.projects_count
import aichecklists.core.designsystem.generated.resources.projects_empty_description
import aichecklists.core.designsystem.generated.resources.projects_empty_title
import aichecklists.core.designsystem.generated.resources.projects_no_items
import aichecklists.core.designsystem.generated.resources.projects_progress
import aichecklists.core.designsystem.generated.resources.projects_reminder_count
import aichecklists.core.designsystem.generated.resources.projects_title
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppItemMetaChip
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The v2 "Projects" tab: one card per checklist — progress ring, name, mini-stat badges.
 *
 * ## Why hairline cards and not the v1 gallery
 * The v1 home screen shows cards with cover images and progress bars — a gallery, which is the right
 * shape when that screen IS the app. In v2 it is one tab of four and the question it has to answer in
 * one glance is "which list still has work in it". So: no covers, no second line, no full-width
 * progress bars, one 56dp row per checklist. What it does borrow from the reference
 * (`docs/reference/todoist-ui-reference/06-overview-settings-and-projects.png`) is the CONTAINER —
 * Todoist's Overview puts project rows in inset, rounded, filled blocks, not in full-bleed rows
 * divided by rules. Here that is one [AppCard] per row on the `AppCardDefaults` flat + 1dp-hairline
 * tokens with 8dp gaps: the same object, the same rhythm and the same 16dp gutter as the Inbox tab's
 * card layout, so two adjacent tabs stop looking like two apps. The card gallery stays available to
 * anyone on the classic layout — this screen deliberately does not replace it, it is a different
 * route ([com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute.Projects]).
 *
 * ## Why a progress ring in the leading slot
 * The slot used to hold the same `ChecklistRtl` glyph on every row: N identical marks, no information,
 * and the loudest thing on the screen. The ring spends a number the mapper already computes
 * ([ProjectRow.totalCount]) on the one thing this list is for, and it costs no extra height. It is a
 * read-only indicator with no `clickable` of its own — per `.claude/rules/ui-card-patterns.md`, any
 * real per-project action (rename, delete, archive) belongs in a sheet, never on the row.
 *
 * ## Why the whole BAND — not the card — is one click target
 * Unlike a checklist ITEM (which splits 30/70 between toggling and opening — see
 * `.claude/rules/ui-card-patterns.md`), a project row has exactly one action: open it. A split here
 * would invent a second gesture with nothing to bind it to. The target is the full-width band the row
 * occupies (gutter and inter-card gap included), NOT the visible card: an inset card leaves the 16dp
 * gutter and the 8dp gap between cards dead, and the flat list this replaced had none of that — a
 * thumb tap near the screen edge used to open the project and has to keep doing so. See [ProjectCard]
 * for how the ripple still stays inside the rounded card.
 *
 * @param contentBottomPadding inset the v2 shell reserves for its bottom bar + chat FAB. Defaults to
 *   0.dp so previews and tests get the plain layout.
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
        // Second line under the title, matching the Inbox tab — the count of lists, not of tasks: a
        // cross-checklist task total would be a second opinion about numbers the Inbox tab already
        // publishes, and the two disagreeing is a bug class this feature has hit before.
        subtitle = content?.projects
            ?.takeIf { it.isNotEmpty() }
            ?.let { pluralStringResource(Res.plurals.projects_count, it.size, it.size) },
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
                    // Inbox tab's list: fillMaxSize pins minWidth == maxWidth to the pane, so a
                    // widthIn(max) alone is coerced away and the cap silently does nothing.
                    // wrapContentWidth relaxes the minimum back to 0 and centres the capped column in
                    // the pane it still holds.
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .adaptiveContentWidth(),
                // NO horizontal padding and NO Arrangement.spacedBy here on purpose: both would be
                // subtracted from the click target. The gutter and half of the inter-card gap live
                // INSIDE [ProjectCard]'s clickable band instead, so consecutive bands touch and every
                // pixel of the list opens a project. This is not the double padding
                // `.claude/rules/ui-card-patterns.md` bans — the list pads nothing, the row pads once.
                contentPadding = PaddingValues(
                    // Each row already carries ProjectCardHalfGap of its own top inset; together they
                    // make the same 8dp of air above the first card the gaps between cards have.
                    top = ProjectCardHalfGap,
                    // contentBottomPadding is the v2 shell's FAB band. Dropping it parks the last card
                    // under the FAB, unreachable.
                    bottom = AppDimens.SpacingXl + contentBottomPadding,
                ),
            ) {
                items(content.projects, key = { it.checklistId }) { project ->
                    ProjectCard(
                        project = project,
                        onClick = { onIntent(ProjectsIntent.OnProjectClick(project.checklistId)) },
                    )
                }
            }
        }
    }
}

/**
 * One project: progress ring, name, mini-stat. The whole full-width band is one click target — a project
 * has exactly one action (open it), so unlike a checklist ITEM there is no 30/70 split to inherit
 * (`.claude/rules/ui-card-patterns.md`); inventing a second zone here would give it nothing to do.
 *
 * ## Why the click target is the band and the ripple is the card
 * The visible card is inset by the 16dp gutter and separated from its neighbours by 8dp. If the card
 * itself were the clickable, all of that would be dead: a tap under the thumb at the screen edge, or
 * in the seam between two cards, would do nothing — which is a regression against the flat list this
 * replaced, where the gutter was inside `fillMaxWidth().clickable()` and rows were contiguous. So the
 * clickable is the band (`fillMaxWidth()` + the gutter + half a gap on each side, so two consecutive
 * bands touch) with `indication = null`, and the card's CONTENT slot draws the ripple off the SAME
 * [MutableInteractionSource] — the Card clips that slot to its 12dp corners, so the ripple still
 * stops at them instead of bleeding a rectangle past them. The one cost is that a bounded ripple
 * started outside the card animates from a point offset by the gutter; a slightly off-centre ripple
 * is worth a hit target that is ~30% larger.
 *
 * `role = Role.Button` is explicit and must stay so. It does NOT come for free from `AppCard`: with
 * no `onLongClick` that path is `Card(onClick=)` → `Surface(onClick=)`, and in the pinned
 * material3 1.11.0-alpha07 that Surface applies `.clickable(interactionSource, indication, enabled,
 * onClick)` with no `role` argument at all (Surface.kt:231-236) — TalkBack would stop announcing
 * "Button" and Switch Access / Voice Access would stop addressing the row as a control.
 *
 * ## TalkBack order
 * Name first, state second, always. Both leading glyphs are decorative (`contentDescription = null`):
 * `clickable` merges its descendants in CHILD order, so a described leading tick is announced BEFORE
 * the checklist name ("All done, Groceries"). The state travels as `stateDescription` on the band
 * instead, which is read after the merged row text, and it carries done/total plus the reminder
 * count — everything the badges draw, in words, since a bare "20" read aloud says nothing.
 * [ProjectMiniStat] is `clearAndSetSemantics`-ed for exactly that reason: it is the visual copy of
 * state the band already announces, and letting its digits merge into the row text would produce
 * "Groceries 20 12 3, Button, 12 of 32 done".
 *
 * ## Why badges and not a sentence
 * The trailing slot used to print a plural phrase ("20 left"). Its RU form is 40% longer, so at the
 * width this slot can afford it truncated to "осталось …" — the ellipsis ate the number, which was
 * the only informative part of the string. Numbers and icons carry no language, so the slot now holds
 * [ProjectMiniStat]: the same read-only [AppItemMetaChip] badge v1 already uses for item metadata
 * (reminder / priority / attachment count on `ChecklistItemCard`, aggregate progress on `FolderCard`),
 * three of them — tasks left, tasks done, reminders. Per `.claude/rules/ui-card-patterns.md` a
 * read-only indicator on a card is allowed as long as it has no `clickable` of its own; these have
 * none, and any real per-project action still belongs in a sheet.
 *
 * A zero would be technically accurate and useless — "0" and "done" look the same at a glance — so
 * every badge is omitted at zero rather than printed: a finished list shows the leading tick and the
 * done badge only, a fresh list shows only tasks-left, and a checklist whose only content is folders
 * (no leaves to count, but not [ProjectRow.isEmpty] either) shows the ring and the name alone instead
 * of the "0 left" it used to print. A list that renders nothing at all ([ProjectRow.isEmpty]) still
 * says so in words — that is a state, not a count, and it is one short word in every locale.
 */
@Composable
private fun ProjectCard(
    project: ProjectRow,
    onClick: () -> Unit,
) {
    // Read once, outside the semantics lambda: stringResource is a @Composable read.
    val progressState: String? = when {
        project.isComplete -> stringResource(Res.string.projects_complete)
        // Nothing to say about the progress of a checklist with no tasks in it; the trailing slot
        // already reads "Empty" and a "0 of 0 done" would be noise on every such row.
        project.totalCount > 0 -> stringResource(
            Res.string.projects_progress,
            project.totalCount - project.openCount,
            project.totalCount,
        )

        else -> null
    }
    // The bell badge is the ONLY place this number appears, and it appears as a bare digit — so
    // unlike the two task badges it has to be spelled out here or a screen-reader user never learns
    // the row has reminders at all.
    val reminderState: String? = project.reminderCount
        .takeIf { it > 0 }
        ?.let { pluralStringResource(Res.plurals.projects_reminder_count, it, it) }
    // ", " is punctuation, not copy: the two halves are separate localized clauses and every locale
    // this app ships (en/ru/hi) separates an enumeration the same way.
    val rowState: String? = listOfNotNull(progressState, reminderState)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = ", ")
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                // The card draws the ripple, clipped to its corners — see the KDoc above.
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { rowState?.let { stateDescription = it } }
            .padding(
                horizontal = AppDimens.ScreenPaddingHorizontal,
                vertical = ProjectCardHalfGap,
            ),
    ) {
        AppCard(
            // Zero: the Box below owns the 56dp geometry and the Row owns the insets, exactly like
            // InboxTaskRow's card. AppCard's default 16dp would stack on top of both.
            contentPadding = PaddingValues(0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ProjectCardMinHeight)
                    // The ripple for the band's click lives HERE, on the card's content slot, and
                    // not on AppCard's outer modifier: the Card clips this slot to its 12dp corners
                    // itself, so the ripple stops at them with no second clip of our own to keep in
                    // sync with `MaterialTheme.shapes.medium`.
                    .indication(interactionSource, ripple()),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(
                            horizontal = AppDimens.SpacingMd,
                            vertical = AppDimens.SpacingSm,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    // 12dp, not the Inbox row's 8dp: there the leading element is a Checkbox carrying
                    // ~12dp of phantom minimumInteractiveComponentSize inset, so 8dp is ~20dp
                    // optically. This leading element is a bare canvas, so it needs the gap for real.
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
                ) {
                    // Branches on isComplete, never on doneFraction == 1f: an empty checklist also has
                    // zero open items, and re-deriving completeness here would congratulate the user
                    // for a list they never wrote (there is a mapper test pinning exactly that
                    // invariant).
                    if (project.isComplete) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            // Decorative: "All done" is on the row as stateDescription, so it is read
                            // AFTER the name instead of ahead of it.
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(AppDimens.IconSizeMd),
                        )
                    } else {
                        ProjectProgressRing(
                            fraction = project.doneFraction,
                            modifier = Modifier.size(AppDimens.IconSizeMd),
                        )
                    }

                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    if (project.isEmpty) {
                        // A checklist that renders nothing when opened. NOT `totalCount == 0` — a
                        // checklist holding only folders has no leaves but is not empty on screen,
                        // and that one shows no badges rather than this word.
                        Text(
                            text = stringResource(Res.string.projects_no_items),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // Right-aligned inside its slot, so it sits flush with the card edge the
                            // way an intrinsically-sized child would.
                            textAlign = TextAlign.End,
                            modifier = Modifier.widthIn(max = ProjectEmptyLabelMaxWidth),
                        )
                    } else {
                        ProjectMiniStat(
                            project = project,
                            modifier = Modifier.widthIn(max = ProjectMiniStatMaxWidth),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The trailing mini-stat: up to three read-only number badges — tasks left, tasks done, reminders.
 *
 * ## Why [AppItemMetaChip] and not a new v2 badge
 * This is the badge v1 already ships: `ChecklistItemCard`'s meta row draws reminder / priority /
 * attachment-count chips with it, and `FolderCard` draws its aggregate "checked/total" with it. Same
 * component, same 6dp "data tag" shape, same 14dp icon, same `*Container` / `on*Container` color
 * pairing — so a project row and the rows inside that project speak one visual language, and a future
 * fix to the badge lands on both. Nothing about it needed changing, so nothing was changed.
 *
 * ## The three colors
 * - **left** — [GistiColors.successContainer] / [GistiColors.onSuccessContainer], the theme's ready-made
 *   green badge pair (until now unused). Green marks the number the user actually acts on; a donut
 *   glyph echoes the leading ring so the badge reads as "progress", not as "total".
 * - **done** — `secondaryContainer`, matching `FolderCard`'s progress chip exactly. Neutral on
 *   purpose: it is a record, not a call to action.
 * - **reminders** — `primaryContainer` + `Icons.Filled.Notifications`, identical to the per-item
 *   reminder chip in `ChecklistDetailScreen`'s meta row, so a bell means one thing in this app.
 *
 * ## Zeros are omitted, never printed
 * Each badge appears only when its number is non-zero, which is also what keeps the slot narrow: the
 * two task counts sum to [ProjectRow.totalCount], so a three-digit count forces its sibling to zero
 * and out of the row. The widest this can measure at font scale 1.0 is three two-digit badges
 * (~150dp), which is what `ProjectMiniStatMaxWidth` is sized for.
 */
@Composable
private fun ProjectMiniStat(
    project: ProjectRow,
    modifier: Modifier = Modifier,
) {
    // Derived here, not carried on the row: it is totalCount minus openCount by definition, and a
    // second stored copy is how a numerator and a denominator start disagreeing.
    val doneCount = project.totalCount - project.openCount
    Row(
        // Cleared, not described. Every number here is already spoken by the band's stateDescription
        // in words ("12 of 32 done, 3 reminders"); merged into the row text these badges would read
        // as a string of bare digits BEFORE that, and in child order, which is precisely the
        // announcement order [ProjectCard]'s KDoc exists to prevent.
        modifier = modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (project.openCount > 0) {
            AppItemMetaChip(
                icon = Icons.Outlined.DonutLarge,
                label = project.openCount.toString(),
                containerColor = GistiColors.successContainer,
                contentColor = GistiColors.onSuccessContainer,
            )
        }
        if (doneCount > 0) {
            AppItemMetaChip(
                icon = Icons.Outlined.CheckCircle,
                label = doneCount.toString(),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        // Last on purpose: if a huge font scale ever squeezes this row, the badge that gives way is
        // the one whose absence costs the least — the ring and the two task badges still describe
        // the work, and the reminder count is still announced in full by the row's stateDescription.
        if (project.reminderCount > 0) {
            AppItemMetaChip(
                icon = Icons.Filled.Notifications,
                label = project.reminderCount.toString(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * Determinate ring, drawn rather than composed.
 *
 * `CircularProgressIndicator` would drag in whichever gap/stop-indicator behaviour the pinned M3
 * version ships and animates on every recomposition; this is two arcs and no state. It carries no
 * semantics of its own on purpose — the fraction it draws reaches TalkBack once, as the row's
 * `stateDescription` in [ProjectCard]; describing the canvas as well would say the same thing twice
 * per row, and would say it BEFORE the checklist name.
 */
@Composable
private fun ProjectProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val progressColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val stroke = ProjectRingStroke.toPx()
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )
        val sweep = 360f * fraction.coerceIn(0f, 1f)
        if (sweep > 0f) {
            drawArc(
                color = progressColor,
                // -90f = 12 o'clock. Clockwise in every locale: the ring is a symbol, not reading
                // order.
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/** Same 56dp as `InboxTaskRow`'s card — one object, one rhythm across the two tabs. */
private val ProjectCardMinHeight = 56.dp

/**
 * Half of the 8dp gap between cards, paid by each row's own clickable band rather than by the list.
 *
 * Two consecutive bands therefore TOUCH: the seam between two cards still looks like 8dp of air but
 * belongs to one row or the other, so there is no strip of the list that swallows a tap. Changing
 * this to a full gap on the list (`Arrangement.spacedBy`) is what made the seam dead before.
 */
private val ProjectCardHalfGap = AppDimens.SpacingXs

/**
 * Ceiling on the trailing mini-stat's width.
 *
 * A cap is needed at all because `Row` measures unweighted children against the FULL remaining
 * constraint before it distributes weights: uncapped, a wide trailing slot takes everything,
 * `Modifier.weight(1f)` on the title resolves to 0dp and the checklist name disappears from its own
 * row.
 *
 * 160dp is derived, not guessed. A badge is `8 + 14 icon + 4 + digits + 8` ≈ 34dp of chrome plus
 * ~7dp per digit at `labelSmall`, and the badges are omitted at zero, so the widest reachable
 * mini-stat at font scale 1.0 is three TWO-digit badges (~150dp incl. two 4dp gaps). Three-digit
 * numbers cannot widen it further: `openCount + doneCount == totalCount`, capped at the
 * 100-items-per-checklist limit, so a "100" forces its sibling badge to zero and out of the row.
 * At 160dp nothing in this slot truncates at scale 1.0 — which is the entire point of the change
 * that replaced the plural phrase here: the 96dp phrase cap this replaces DID truncate at scale 1.0,
 * printing "осталось …" and deleting the number it was there to show.
 *
 * Beyond scale 1.0 the cap squeezes the LAST badge (reminders) rather than the title — see the
 * ordering note in [ProjectMiniStat]. The name is the thing you scan for and it ellipsizes
 * gracefully; a truncated number would print a WRONG value, so the numbers get the fixed budget.
 */
private val ProjectMiniStatMaxWidth = 160.dp

/**
 * Ceiling on the "Empty" label — the one trailing element that is still a word.
 *
 * Same reason as [ProjectMiniStatMaxWidth] (unweighted children are measured first), but it can stay
 * tight: this slot holds a single short state word in every locale ("Empty" / "Пусто" / "खाली"), never
 * a number, so ellipsizing it at an extreme font scale costs a letter and not a wrong value.
 */
private val ProjectEmptyLabelMaxWidth = 96.dp

/**
 * 2dp against the 24dp ring diameter — a 1:12 ratio, chosen so a small fraction is still visible.
 *
 * The arc is the only mark that distinguishes "just started" from "not started": at 1dp the sweep of
 * a 1-of-12 checklist reads as a rendering artefact next to the 1dp hairline of the card border it
 * sits in, and at 3dp+ the ring turns into a donut that outweighs the checklist name beside it. Tune
 * it against the SMALLEST non-zero fraction the list can show, not against a half-full ring.
 */
private val ProjectRingStroke = 2.dp
