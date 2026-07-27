package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_empty_description
import aichecklists.core.designsystem.generated.resources.inbox_empty_title
import aichecklists.core.designsystem.generated.resources.inbox_project_empty_description
import aichecklists.core.designsystem.generated.resources.inbox_quick_add_placeholder
import aichecklists.core.designsystem.generated.resources.inbox_title
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.desingsystem.components.AddItemInputField
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.components.PlatformBackHandler
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
 * lists, and a PINNED quick-add row. Pinning the input outside the pager is the whole design: the
 * capture affordance must never scroll away, and because it targets whichever page is showing, the
 * same control is both "capture into the Inbox" and "quick-add to this project".
 *
 * The tab row is *scrollable* (unlike CalendarScreen's fixed two-tab `PrimaryTabRow`) because the
 * project count is unbounded — a fixed row would squeeze 12 projects into unreadable slivers.
 *
 * @param contentBottomPadding inset the v2 shell reserves for its bottom bar + chat FAB. Applied to
 *   the PINNED ROW (the bottommost element), not to the list: the row is what the floating FAB would
 *   otherwise cover, and padding both would reserve the space twice.
 * @param swallowRootBack whether BACK is swallowed here. Same shape as `MainScreen.swallowRootBack`.
 *   Default true = today's behaviour: at the v2 root BACK must not reach the Activity. The host has
 *   to pass false whenever this screen is NOT the top of the back stack — on Expanded the Inbox is a
 *   `ListDetailSceneStrategy` listPane and stays composed next to a pushed ChecklistDetail, and an
 *   always-enabled handler there SWALLOWS the BACK that should dismiss the detail pane.
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
    PlatformBackHandler(enabled = swallowRootBack) {
        // Intentionally no-op — see above.
    }

    val content = state as? InboxScreenState.Content

    AppScaffold(
        title = stringResource(Res.string.inbox_title),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        // The capture row lives in the bottomBar SLOT, not inside the content.
        //
        // AppScaffold insets its content by statusBars only and wraps this slot in
        // windowInsetsPadding(ime ∪ navigationBars) — so the slot is the one place that is
        // automatically lifted above the keyboard. Hosting the row inside the content instead forced a
        // manual imePadding(), and because a bottom inset counts toward a composable's measured
        // HEIGHT, the non-weighted row then claimed the keyboard's height out of the Column: the input
        // slid off-screen while the pager and tab row were squeezed to nothing.
        //
        // Hidden while there is no page to capture into: with no target checklist the Add button would
        // be an enabled affordance that does nothing (the ViewModel can only log and drop the text).
        // Defensive only — the ViewModel holds Loading until the system Inbox row exists.
        bottomBar = {
            val pages = content?.pages
            if (content != null && !pages.isNullOrEmpty()) {
                PinnedQuickAddRow(
                    text = content.quickAddText,
                    contentBottomPadding = contentBottomPadding,
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
            InboxContent(
                content = content,
                pages = content.pages,
                contentBottomPadding = contentBottomPadding,
                onIntent = onIntent,
            )
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
            PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage.coerceIn(0, pages.lastIndex),
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                pages.forEachIndexed { index, page ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                // Index 0 is the system Inbox; its label comes from resources rather
                                // than the row name so it stays localized even though the row was
                                // created once, in whatever language was active back then.
                                text = if (index == 0 && page.isInbox) inboxTabLabel else page.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }

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
                        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
                        // Only clearance from the PINNED row below — the shell's bar/FAB reserve
                        // lives on that row, which sits between this list and the screen bottom.
                        contentPadding = PaddingValues(bottom = AppDimens.SpacingXl),
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
 * The always-visible capture affordance, kept in its own composable so the per-frame keyboard inset
 * read below re-composes ONLY this row — reading `WindowInsets.ime` up in [InboxContent] would
 * re-run the tab row and the pager on every frame of the keyboard animation.
 *
 * Hosted in AppScaffold's `bottomBar`, which already applies `ime ∪ navigationBars` — so this row must
 * NOT add an inset of its own. It only decides whether the v2 shell's bar/FAB reserve still applies:
 * with the keyboard up the bottom bar and the FAB are behind it, and reserving for both would strand
 * the input in the middle of the screen.
 */
@Composable
private fun PinnedQuickAddRow(
    text: String,
    contentBottomPadding: Dp,
    onTextChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    // The IME reserve and the shell reserve are alternatives, never a sum.
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeVisible = imeBottom > navBottom + 8.dp

    AddItemInputField(
        text = text,
        onTextChange = onTextChange,
        onAdd = onAdd,
        placeholder = stringResource(Res.string.inbox_quick_add_placeholder),
        modifier = Modifier
            .adaptiveContentWidth()
            .padding(
                start = AppDimens.ScreenPaddingHorizontal,
                end = AppDimens.ScreenPaddingHorizontal,
                top = AppDimens.SpacingSm,
                bottom = if (imeVisible) AppDimens.SpacingSm else contentBottomPadding,
            ),
    )
}

/**
 * One task row, with the project-wide 30/70 hit-zone split: the left 30% toggles the checkbox, the
 * right 70% opens the triage sheet (`.claude/rules/ui-card-patterns.md`). The star is a READ-ONLY
 * indicator with no `clickable` of its own — importance is toggled from the sheet.
 */
@Composable
private fun InboxTaskRow(
    task: InboxTask,
    onCheckedChange: (Boolean) -> Unit,
    onDetailsClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.MinTouchTarget),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(vertical = AppDimens.SpacingXs),
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
                    modifier = Modifier.size(20.dp),
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
