package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_empty_description
import aichecklists.core.designsystem.generated.resources.inbox_empty_title
import aichecklists.core.designsystem.generated.resources.inbox_project_empty_description
import aichecklists.core.designsystem.generated.resources.inbox_quick_add_placeholder
import aichecklists.core.designsystem.generated.resources.inbox_title
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.desingsystem.components.AddItemInputField
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCardDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.components.PlatformBackHandler
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.gistiDockColor
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The v2 Inbox tab — quick capture first, triage later.
 *
 * Layout, top to bottom: a scrollable tab row (Inbox + every project), a [HorizontalPager] of task
 * lists, and — only while [createDockOpen] — a capture dock over the bottom edge. Because that dock
 * targets whichever page is showing, one control is both "capture into the Inbox" and "quick-add to
 * this project".
 *
 * The tab row is *scrollable* (unlike CalendarScreen's fixed two-tab `PrimaryTabRow`) because the
 * project count is unbounded — a fixed row would squeeze 12 projects into unreadable slivers.
 *
 * ## Why capture is behind a FAB now
 * The row used to be PINNED — always visible above the bottom bar. It moved behind the shell's "+"
 * FAB on the owner's call, so the tab reads as a task list rather than as an input form, and the
 * two create paths (AI and manual) sit side by side as sibling FABs instead of living on opposite
 * edges of the screen. The trade-off is real and was accepted knowingly: capture costs one extra tap
 * and the affordance is no longer self-evident on first open.
 *
 * @param contentBottomPadding inset the v2 shell reserves for its bottom bar + FABs. Applied to the
 *   BOTTOMMOST element (the capture dock while it is up, the pager's content padding otherwise) —
 *   applying it to both would reserve the space twice.
 * @param swallowRootBack whether BACK is swallowed here. Same shape as `MainScreen.swallowRootBack`.
 *   Default true = today's behaviour: at the v2 root BACK must not reach the Activity. The host has
 *   to pass false whenever this screen is NOT the top of the back stack — on Expanded the Inbox is a
 *   `ListDetailSceneStrategy` listPane and stays composed next to a pushed ChecklistDetail, and an
 *   always-enabled handler there SWALLOWS the BACK that should dismiss the detail pane.
 * @param createDockOpen whether the capture dock is showing. Owned by the host (see [InboxRoute]).
 * @param onCreateDockDismiss BACK or a tap outside the dock. The host clears the flag.
 */
// AppScaffold's scrollBehavior parameter is typed TopAppBarScrollBehavior, which is still
// @ExperimentalMaterial3Api — an experimental type in the signature forces the opt-in onto every
// call site even though AppScaffold itself already opted in. Mirrors TodayScreen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    state: InboxScreenState,
    contentBottomPadding: Dp,
    onIntent: (InboxIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    swallowRootBack: Boolean = true,
    // No defaults on these two, deliberately. A host that mounts this screen without wiring them
    // would render a "+" FAB whose dock never appears — a silently dead button, which is exactly the
    // failure this arm already shipped once on the rail. Make the compiler ask.
    createDockOpen: Boolean,
    onCreateDockDismiss: () -> Unit,
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView(AnalyticsScreens.INBOX) }

    // Inbox is the v2 HOME root: the back stack holds a single entry, so NavDisplay's own handler is
    // disabled and an unhandled BACK would reach the Activity and finish() the app — a behaviour v1
    // never had (MainScreen swallows BACK for exactly this reason, its handler #2). Product decision:
    // the user leaves via Home, never by backing out of the app.
    //
    // Gated on swallowRootBack because this screen is NOT always the root: as a two-pane listPane it
    // stays composed under a pushed detail, and a handler registered later than NavDisplay's wins —
    // so an unconditional handler makes BACK dead on Expanded instead of dismissing the detail pane.
    // The two handlers are mutually exclusive by state, so their registration order is irrelevant.
    // 1) Capture dock open → BACK closes it (and nothing else): the user is escaping the keyboard,
    //    not the screen.
    PlatformBackHandler(enabled = createDockOpen) {
        onCreateDockDismiss()
    }
    PlatformBackHandler(enabled = swallowRootBack && !createDockOpen) {
        // Intentionally no-op — see above.
    }

    val content = state as? InboxScreenState.Content

    AppScaffold(
        title = stringResource(Res.string.inbox_title),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        // The capture dock lives in the bottomBar SLOT, not inside the content.
        //
        // AppScaffold insets its content by statusBars only and wraps this slot in
        // windowInsetsPadding(ime ∪ navigationBars) — so the slot is the one place that is
        // automatically lifted above the keyboard. Hosting the dock inside the content instead forced
        // a manual imePadding(), and because a bottom inset counts toward a composable's measured
        // HEIGHT, the non-weighted row then claimed the keyboard's height out of the Column: the input
        // slid off-screen while the pager and tab row were squeezed to nothing. That defect is the
        // reason this stays a slot even though the dock is now conditional.
        //
        // Also hidden while there is no page to capture into: with no target checklist the Add button
        // would be an enabled affordance that does nothing (the ViewModel can only log and drop the
        // text). Defensive only — the ViewModel holds Loading until the system Inbox row exists.
        bottomBar = {
            val pages = content?.pages
            if (createDockOpen && content != null && !pages.isNullOrEmpty()) {
                InboxCaptureDock(
                    text = content.quickAddText,
                    onTextChange = { onIntent(InboxIntent.OnQuickAddTextChanged(it)) },
                    onAdd = { onIntent(InboxIntent.OnQuickAddSubmit) },
                )
            }
        },
    ) {
        if (content == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                InboxContent(
                    content = content,
                    pages = content.pages,
                    // While the dock is up it occupies the bottomBar slot, so the Scaffold has already
                    // shortened the content by its height — reserving the FAB band on top of that
                    // would strand the list above a gap the size of a stack that is not on screen.
                    contentBottomPadding = if (createDockOpen) 0.dp else contentBottomPadding,
                    onIntent = onIntent,
                )

                // Tap-outside-to-dismiss for the capture dock. Deliberately NOT dimmed: the dock is a
                // one-line input, not a modal task — darkening the whole list to type a word reads as
                // a much heavier interruption than it is. The surface exists purely to catch the tap,
                // which is also why it renders nothing.
                //
                // `detectTapGestures`, NOT `clickable`: the dock stays open across several adds, and
                // `clickable` claims the initial press, so the list underneath stopped scrolling
                // while capturing — the one moment the user is most likely to want to look down the
                // list. A tap detector leaves the press unconsumed, so a drag falls through to the
                // LazyColumn and only a real tap dismisses.
                if (createDockOpen) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { onCreateDockDismiss() })
                            },
                    )
                }
            }
        }
    }

    // Sheets live OUTSIDE the scaffold so they float above the whole screen, matching the detail
    // screen's ItemDetailsSheet placement.
    if (content != null) {
        val page = content.pages.getOrNull(content.selectedPage)
        val sheetTask = page?.tasks?.firstOrNull { it.fillItemId == content.sheetForTaskId }
        if (page != null && sheetTask != null) {
            InboxTaskSheet(
                task = sheetTask,
                isProjectPage = !page.isInbox,
                projectId = page.checklistId,
                moveTargets = content.moveTargets,
                movePickerOpen = content.movePickerOpen,
                onIntent = onIntent,
            )
        }
    }
}

@Composable
private fun InboxContent(
    content: InboxScreenState.Content,
    pages: List<InboxPage>,
    contentBottomPadding: Dp,
    onIntent: (InboxIntent) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // pageCount is a lambda, not a snapshot: rememberPagerState re-reads it every composition, so a
    // project created or deleted while the tab is open resizes the pager without a state reset.
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pages.size })

    // Pager indices are POSITIONAL, and a new checklist is inserted at position 0
    // (ChecklistRepositoryImpl.addChecklist increments every other position), so it lands at pages[1]
    // and shifts every project one slot right. Anchoring on the checklist id of the page the user
    // settled on is what keeps them on THEIR project when a checklist is created elsewhere (AI chat,
    // widget, sync from another device) — without it the pager silently shows a different project and
    // the pinned quick-add row retargets with it.
    var anchorChecklistId by remember { mutableStateOf<Long?>(null) }

    // Swipes are the source of truth for the selected page; this mirrors them back into the
    // ViewModel so quick-add and the move-target list follow the finger. Tab taps go the other way
    // (animateScrollToPage), and land here on settle — one direction each, so no feedback loop.
    // Deliberately NOT keyed on `pages`: re-running it on every list edit would re-report an index
    // the user never moved to.
    LaunchedEffect(pagerState.currentPage) {
        anchorChecklistId = pages.getOrNull(pagerState.currentPage)?.checklistId
        onIntent(InboxIntent.OnPageSelected(pagerState.currentPage))
    }

    // The other half of the anchor: when the SET of pages changes, follow the anchored checklist to
    // its new slot. scrollToPage settles immediately, so the effect above then reports the new index
    // and re-records the same anchor — no feedback loop, because the anchor id does not change.
    //
    // Keyed on the id LIST, not on `pages`: an InboxPage carries its tasks, so keying on the whole
    // list re-ran this on every checked box and every background sync — i.e. constantly, for changes
    // that cannot move a page. The isScrollInProgress guard covers the rest: mid-swipe `currentPage`
    // may already report the neighbour, and calling scrollToPage then yanks the pager out from under
    // the user's finger.
    val pageIds = remember(pages) { pages.map { it.checklistId } }
    LaunchedEffect(pageIds) {
        val anchor = anchorChecklistId ?: return@LaunchedEffect
        if (pagerState.isScrollInProgress) return@LaunchedEffect
        val index = pageIds.indexOfFirst { it == anchor }
        if (index < 0) {
            // Anchored checklist gone (deleted here or on another device): re-anchor on whatever
            // occupies the slot now instead of chasing a dead id on every future change.
            anchorChecklistId = pages.getOrNull(pagerState.currentPage)?.checklistId
        } else if (index != pagerState.currentPage) {
            pagerState.scrollToPage(index)
        }
    }

    val inboxTabLabel = stringResource(Res.string.inbox_title)

    // No imePadding here: the keyboard inset is owned by the PINNED ROW (see [PinnedQuickAddRow]).
    // Insetting this Column instead pushes the row out of the visible area entirely, because
    // AppScaffold's content slot already sits inside the resolved window insets.
    Column(modifier = Modifier.fillMaxSize()) {
        if (pages.isEmpty()) {
            // Defensive only: the ViewModel holds InboxScreenState.Loading until the system Inbox row
            // exists, so Content always carries at least the Inbox page and this branch is
            // unreachable. Kept as a legible fallback (a 0-page pager is tolerated but flashes an
            // unexplained blank) rather than as an assumption that crashes if the invariant moves.
            Box(modifier = Modifier.weight(1f)) {
                EmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = stringResource(Res.string.inbox_empty_title),
                    description = stringResource(Res.string.inbox_empty_description),
                )
            }
        } else {
            InboxTabHeader(
                projectTitles = remember(pages) { pages.drop(1).map { it.title } },
                inboxLabel = inboxTabLabel,
                selectedPage = pagerState.currentPage,
                onSelectPage = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                // Stable page identity. Without a key the pager identifies its pages by INDEX, so an
                // insert at position 0 keeps the user's index while the CONTENT under it becomes a
                // different project. getOrNull guards the frame where pageCount has already shrunk
                // but this lambda is still asked about the old index (a Long id and an Int index can
                // never collide as keys — Long.equals(Int) is false).
                key = { index -> pages.getOrNull(index)?.checklistId ?: index },
            ) { pageIndex ->
                val page = pages.getOrNull(pageIndex)
                when {
                    page == null -> Box(modifier = Modifier.fillMaxSize())
                    page.tasks.isEmpty() && page.isInbox -> EmptyState(
                        icon = Icons.Outlined.Inbox,
                        title = stringResource(Res.string.inbox_empty_title),
                        description = stringResource(Res.string.inbox_empty_description),
                    )
                    page.tasks.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.ChecklistRtl,
                        title = page.title,
                        description = stringResource(Res.string.inbox_project_empty_description),
                    )
                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .adaptiveContentWidth()
                            .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
                        // 8dp, matching the checklist detail list — the cards are the same object
                        // there and the two screens must not use two different rhythms for it.
                        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                        // Top padding so the first card does not touch the header's rule; the bottom
                        // reserve is the shell's FAB band, passed in by the host.
                        contentPadding = PaddingValues(
                            top = AppDimens.SpacingSm,
                            bottom = AppDimens.SpacingXl + contentBottomPadding,
                        ),
                    ) {
                        items(page.tasks, key = { it.fillItemId }) { task ->
                            InboxTaskRow(
                                task = task,
                                onCheckedChange = { checked ->
                                    onIntent(
                                        InboxIntent.OnTaskCheckedChanged(task.fillItemId, checked)
                                    )
                                },
                                onDetailsClick = {
                                    onIntent(InboxIntent.OnTaskDetailsClick(task.fillItemId))
                                },
                            )
                        }
                    }
                }
            }
        }

        // The capture row is NOT here — it is the scaffold's bottomBar (see InboxScreen), which is the
        // only slot that gets lifted above the keyboard automatically.
    }
}

/**
 * The header of the Inbox tab: a PINNED Inbox pill, a vertical rule, then the projects.
 *
 * ## Why the Inbox is outside the scrolling row
 * It used to be tab index 0 of one `PrimaryScrollableTabRow`, which made it look like a project and —
 * worse — let it scroll off the left edge once the user had five or six projects. The system capture
 * bucket is the one destination that must always be one tap away, so it is pinned and the projects
 * scroll beside it. The vertical rule is what says "these two groups are different kinds of thing".
 *
 * ## Why pills instead of Material tabs for the projects
 * A scrollable `TabRow` brings three defaults that all have to be overridden here — a 52dp start
 * `edgePadding`, its own divider (which would stop short of the pinned pill, leaving a line that
 * covers two thirds of the screen) and an indicator that is always drawn, so on the Inbox page it
 * would falsely underline the first project. Overriding the indicator means the `TabIndicatorScope`
 * API, which is still moving between Compose releases. Pills carry their selection in the container
 * colour, so the Inbox page simply has no highlighted project — nothing to suppress. `Role.Tab` +
 * `selected` keep the accessibility semantics a tab row would have given.
 *
 * @param projectTitles the pages AFTER the Inbox, in page order. Index `i` here is page `i + 1`.
 */
@Composable
private fun InboxTabHeader(
    projectTitles: List<String>,
    inboxLabel: String,
    selectedPage: Int,
    onSelectPage: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // heightIn, not height: both this row and the pills inside it are sized around a
                // one-line label, and a hard height clips that label in Hindi (taller glyph box) or
                // at a large system font scale. A minimum keeps the rhythm and still lets the row grow.
                .heightIn(min = InboxTabRowHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InboxTabPill(
                label = inboxLabel,
                icon = Icons.Outlined.Inbox,
                selected = selectedPage == 0,
                onClick = { onSelectPage(0) },
                modifier = Modifier.padding(start = AppDimens.ScreenPaddingHorizontal),
            )

            if (projectTitles.isNotEmpty()) {
                VerticalDivider(
                    thickness = AppDimens.DividerThickness,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier
                        .padding(horizontal = AppDimens.SpacingSm)
                        .height(InboxTabDividerHeight),
                )
                LazyRow(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
                    // Trailing padding only: the leading gap is the divider's. The last pill bleeds
                    // past the screen edge while scrolling, which is the row's only scroll cue.
                    contentPadding = PaddingValues(end = AppDimens.ScreenPaddingHorizontal),
                ) {
                    itemsIndexed(projectTitles) { index, title ->
                        InboxTabPill(
                            label = title,
                            icon = null,
                            selected = selectedPage == index + 1,
                            onClick = { onSelectPage(index + 1) },
                        )
                    }
                }
            }
        }

        // One rule under the WHOLE header (pinned pill included) — a TabRow's built-in divider would
        // only span the scrolling part and stop dead under the pill.
        HorizontalDivider(
            thickness = AppDimens.DividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/** One header pill. Selection is carried by the container colour, not by an underline. */
@Composable
private fun InboxTabPill(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        // Same token as the AppNavigationBar's selected-item pill, so "selected" reads identically
        // everywhere in the v2 shell.
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = RoundedCornerShape(InboxTabPillHeight / 2),
        modifier = modifier
            .heightIn(min = InboxTabPillHeight)
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppDimens.SpacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs + 2.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    // The label right next to it already says what this is; a description here would
                    // make TalkBack read the destination twice.
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Without a cap a long project name simply stretches its pill — inside a scrolling
                // row nothing constrains the width, so `Ellipsis` would never trigger.
                modifier = Modifier.widthIn(max = InboxTabPillMaxLabelWidth),
            )
        }
    }
}

private val InboxTabRowHeight = 48.dp
private val InboxTabPillHeight = 36.dp
private val InboxTabDividerHeight = 24.dp
private val InboxTabPillMaxLabelWidth = 160.dp

/**
 * The capture affordance, raised by the shell's "+" FAB. Kept in its own composable so the per-frame
 * keyboard inset read below re-composes ONLY this row — reading `WindowInsets.ime` up in
 * [InboxContent] would re-run the tab row and the pager on every frame of the keyboard animation.
 *
 * Hosted in AppScaffold's `bottomBar`, which already applies `ime ∪ navigationBars` — so this row must
 * NOT add an inset of its own, and it does not reserve the shell's FAB band either: the host hides
 * both FABs while the dock is up, so reserving for a stack that is not on screen would float the
 * input a FAB-height above the bottom edge.
 *
 * Styled as a raised dock (surface + top hairline + rounded top corners) rather than as a bare row,
 * because it now APPEARS over the list instead of always being part of the layout: without an edge of
 * its own it reads as a task row that suddenly grew an input.
 *
 * The keyboard is raised on appearance and the dock STAYS open after each Add — capture is usually
 * more than one item, and closing after every send would cost a FAB tap per task. Dismissal is
 * explicit: BACK, or a tap on the list behind it.
 */
@Composable
private fun InboxCaptureDock(
    text: String,
    onTextChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // Raise the keyboard as the dock appears. The dock is only composed while it is open, so `Unit`
    // is the correct key: one focus request per appearance, and none while it is closed.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        color = gistiDockColor(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth(),
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
                placeholder = stringResource(Res.string.inbox_quick_add_placeholder),
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

/**
 * One task, with the project-wide 30/70 hit-zone split: the left 30% toggles the checkbox, the right
 * 70% opens the triage sheet (`.claude/rules/ui-card-patterns.md`). The star is a READ-ONLY indicator
 * with no `clickable` of its own — importance is toggled from the sheet.
 *
 * ## Why a card
 * This used to be a bare `Row` on the page background: no container, no rule, nothing but 4dp of air
 * between neighbours, so the tasks read as floating text rather than as objects ("пункты парят").
 * The very same object on the checklist detail screen is a card, so the fix is not a new style but
 * the existing one — `AppCardDefaults` (filled `surfaceContainerLowest` + 1dp `outlineVariant`
 * hairline + zero elevation). On the app's warm off-white page a pure-white card reads clearly; the
 * hairline is what carries the separation, which is why elevation stays flat (stacked shadows in a
 * dense list produce grey "ears" around every card).
 *
 * Uses Material3 [Card] directly rather than `AppCard`: `AppCard` exposes a single `onClick` for the
 * whole surface, which cannot express the 30/70 split. The click handling stays on an INNER overlay
 * Box — moving it onto the Card's own modifier lets the ripple escape the rounded corners (precedent
 * `appcard-onlongclick-ripple-clip`).
 */
@Composable
private fun InboxTaskRow(
    task: InboxTask,
    onCheckedChange: (Boolean) -> Unit,
    onDetailsClick: () -> Unit,
) {
    Card(
        colors = AppCardDefaults.colors(),
        border = AppCardDefaults.border(),
        elevation = AppCardDefaults.flatElevation(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 56dp, not the detail card's 64dp: an Inbox row carries no note or meta chips, so
                // the extra height would be dead space.
                .heightIn(min = InboxTaskRowMinHeight),
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
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
            ) {
                Checkbox(
                    checked = task.checked,
                    // null → the tap overlay below owns the gesture, so the checkbox never competes
                    // with the 30/70 split for the same pointer.
                    onCheckedChange = null,
                )
                Text(
                    text = task.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (task.checked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    // Done state is carried by the strike-through and the dimmer text, never by a
                    // tinted container: a list of alternately tinted cards is harder to scan than a
                    // uniform one, and the strike-through is the colour-independent (WCAG) signal.
                    textDecoration = if (task.checked) TextDecoration.LineThrough else null,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    // fillMaxWidth is mandatory for any Text inside a HorizontalPager — without it the
                    // text overflows the page instead of wrapping (rule ui-card-patterns).
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                if (task.priority > 0) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(AppDimens.IconSizeSm),
                    )
                }
            }

            // Invisible tap overlay — no ripple, matching ChecklistItemCard: the feedback is the state
            // change (checkbox flip / sheet appearing), not an indication.
            val checkInteraction = remember { MutableInteractionSource() }
            val detailsInteraction = remember { MutableInteractionSource() }
            Row(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .weight(0.30f)
                        .fillMaxHeight()
                        .combinedClickable(
                            interactionSource = checkInteraction,
                            indication = null,
                            onClick = { onCheckedChange(!task.checked) },
                            onLongClick = onDetailsClick,
                        ),
                )
                Box(
                    modifier = Modifier
                        .weight(0.70f)
                        .fillMaxHeight()
                        .combinedClickable(
                            interactionSource = detailsInteraction,
                            indication = null,
                            onClick = onDetailsClick,
                            onLongClick = onDetailsClick,
                        ),
                )
            }
        }
    }
}

private val InboxTaskRowMinHeight = 56.dp
