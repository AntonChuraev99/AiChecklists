package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.cancel
import aichecklists.core.designsystem.generated.resources.checklist_delete_message
import aichecklists.core.designsystem.generated.resources.checklist_delete_title
import aichecklists.core.designsystem.generated.resources.checklist_name_placeholder
import aichecklists.core.designsystem.generated.resources.checklist_rename
import aichecklists.core.designsystem.generated.resources.checklist_rename_title
import aichecklists.core.designsystem.generated.resources.delete
import aichecklists.core.designsystem.generated.resources.delete_checklist
import aichecklists.core.designsystem.generated.resources.inbox_display_options
import aichecklists.core.designsystem.generated.resources.inbox_empty_description
import aichecklists.core.designsystem.generated.resources.inbox_empty_title
import aichecklists.core.designsystem.generated.resources.inbox_list_actions
import aichecklists.core.designsystem.generated.resources.inbox_menu_open_checklist
import aichecklists.core.designsystem.generated.resources.inbox_project_empty_description
import aichecklists.core.designsystem.generated.resources.inbox_quick_add_placeholder
import aichecklists.core.designsystem.generated.resources.inbox_task_count
import aichecklists.core.designsystem.generated.resources.inbox_title
import aichecklists.core.designsystem.generated.resources.save
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxLayout
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCardDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.components.PlatformBackHandler
import com.antonchuraev.homesearchchecklist.desingsystem.components.QuickCaptureDock
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The v2 Inbox tab — quick capture first, triage later.
 *
 * Layout, top to bottom: a position indicator ([InboxPagerDots]), a [HorizontalPager] of task lists,
 * and — only while [createDockOpen] — a capture dock over the bottom edge. Because that dock targets
 * whichever page is showing, one control is both "capture into the Inbox" and "quick-add to this
 * project".
 *
 * The indicator *scrolls* (unlike CalendarScreen's fixed two-tab `PrimaryTabRow`) because the project
 * count is unbounded — a fixed row collapses its overflow to zero width instead of shrinking, see
 * [InboxPagerDots]. The pill row it replaced was scrollable for the same reason.
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
    homeSignal: Int = 0,
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

    // The toolbar names the page the pager is on — this is what replaced the tab row. Read from the
    // ViewModel's settled index rather than from the pager, because the bar lives OUTSIDE the pager's
    // composable and hoisting the pager state up here would recompose the whole scaffold on every
    // frame of a swipe.
    val currentPage = content?.pages?.getOrNull(content.selectedPage)

    AppScaffold(
        title = currentPage?.title ?: stringResource(Res.string.inbox_title),
        // Open tasks only, matching Todoist: a count that includes finished ones answers a question
        // nobody asked ("how much have I ever put here") instead of "how much is left".
        subtitle = currentPage?.let { page ->
            pluralStringResource(
                Res.plurals.inbox_task_count,
                page.tasks.count { !it.checked },
                page.tasks.count { !it.checked },
            )
        },
        startAlignedTitle = true,
        actions = {
            if (content != null) {
                InboxToolbarActions(
                    // The system Inbox has no rename/delete, so it gets no overflow at all rather
                    // than an overflow of disabled rows.
                    showListMenu = currentPage?.isInbox == false,
                    listMenuOpen = content.listMenuOpen,
                    onIntent = onIntent,
                )
            }
        },
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
                QuickCaptureDock(
                    text = content.quickAddText,
                    onTextChange = { onIntent(InboxIntent.OnQuickAddTextChanged(it)) },
                    onAdd = { onIntent(InboxIntent.OnQuickAddSubmit) },
                    placeholder = stringResource(Res.string.inbox_quick_add_placeholder),
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
                    layout = content.displayOptions.layout,
                    // While the dock is up it occupies the bottomBar slot, so the Scaffold has already
                    // shortened the content by its height — reserving the FAB band on top of that
                    // would strand the list above a gap the size of a stack that is not on screen.
                    contentBottomPadding = if (createDockOpen) 0.dp else contentBottomPadding,
                    onIntent = onIntent,
                    homeSignal = homeSignal,
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

        content.renameDraft?.let { draft ->
            RenameChecklistDialog(
                draft = draft,
                onDraftChange = { onIntent(InboxIntent.OnRenameDraftChanged(it)) },
                onConfirm = { onIntent(InboxIntent.OnConfirmRenameChecklist) },
                onDismiss = { onIntent(InboxIntent.OnDismissRenameChecklist) },
            )
        }

        if (content.deleteConfirmationOpen && page != null) {
            DeleteChecklistDialog(
                checklistName = page.title,
                onConfirm = { onIntent(InboxIntent.OnConfirmDeleteChecklist) },
                onDismiss = { onIntent(InboxIntent.OnDismissDeleteChecklist) },
            )
        }

        if (content.displayOptionsOpen) {
            InboxDisplayOptionsSheet(options = content.displayOptions, onIntent = onIntent)
        }
    }
}

/**
 * The two trailing toolbar icons, in Todoist's order: display options first, overflow second.
 *
 * The overflow anchors its menu on its own [Box] rather than on the app bar, so the popup opens under
 * the icon instead of at the bar's top-start corner.
 */
@Composable
private fun InboxToolbarActions(
    showListMenu: Boolean,
    listMenuOpen: Boolean,
    onIntent: (InboxIntent) -> Unit,
) {
    IconButton(onClick = { onIntent(InboxIntent.OnDisplayOptionsClick) }) {
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = stringResource(Res.string.inbox_display_options),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showListMenu) {
        Box {
            IconButton(onClick = { onIntent(InboxIntent.OnListMenuOpen) }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(Res.string.inbox_list_actions),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = listMenuOpen,
                onDismissRequest = { onIntent(InboxIntent.OnListMenuDismiss) },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.inbox_menu_open_checklist)) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    },
                    // The id is resolved by the ViewModel from the settled page, so the menu never
                    // has to carry one that a swipe could have staled.
                    onClick = { onIntent(InboxIntent.OnOpenCurrentChecklist) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.checklist_rename)) },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = { onIntent(InboxIntent.OnRenameChecklistClick) },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(Res.string.delete_checklist),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = { onIntent(InboxIntent.OnDeleteChecklistClick) },
                )
            }
        }
    }
}

@Composable
private fun RenameChecklistDialog(
    draft: String,
    onDraftChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // The dialog exists to edit one field; opening it without the keyboard costs a tap every time.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.checklist_rename_title)) },
        text = {
            AppTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = stringResource(Res.string.checklist_name_placeholder),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        confirmButton = {
            // Enabled state mirrors the ViewModel's own blank check. Both exist on purpose: this one
            // stops the user before the fact, the ViewModel's covers a whitespace-only draft that
            // isBlank() catches and this button (isNotBlank) would too — they agree, so a rename can
            // never be silently dropped.
            TextButton(onClick = onConfirm, enabled = draft.isNotBlank()) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun DeleteChecklistDialog(
    checklistName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.checklist_delete_title)) },
        // Names the checklist: the pager can have moved since the menu was opened, and "are you sure"
        // with no subject is exactly how the wrong list gets deleted.
        text = { Text(stringResource(Res.string.checklist_delete_message, checklistName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(Res.string.delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
private fun InboxContent(
    content: InboxScreenState.Content,
    pages: List<InboxPage>,
    layout: InboxLayout,
    contentBottomPadding: Dp,
    onIntent: (InboxIntent) -> Unit,
    homeSignal: Int = 0,
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

    // Tapping the tab named "Inbox" returns to the Inbox page. The pager's page survives the tab's
    // pop-to-root (it lives in this screen, not in the back stack), so without this the tab lands on
    // whichever project was last swiped to and the inbox itself is reachable only by swiping back.
    // The host sends a monotonic counter, so a second tap on an already-selected tab still fires.
    // Page 0 is the Inbox by construction — InboxViewModel builds `pages` with it first.
    LaunchedEffect(homeSignal) {
        if (homeSignal == 0) return@LaunchedEffect
        pagerState.animateScrollToPage(0)
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
            InboxPagerDots(
                pageTitles = pages.map { it.title },
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
                    else -> {
                        val compact = layout == InboxLayout.COMPACT
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                // wrapContentWidth sits between the fill and the cap, and it is what
                                // makes the cap bind at all: fillMaxSize pins minWidth == maxWidth
                                // to the pane, and a widthIn(max) coerced into a fixed range does
                                // nothing — on a 1280dp window the rows spanned the whole pane,
                                // checkbox at one edge and star at the other. This relaxes the
                                // minimum back to 0 so adaptiveContentWidth can actually cut the
                                // width, then centres the capped column in the pane it still holds.
                                .wrapContentWidth(Alignment.CenterHorizontally)
                                .adaptiveContentWidth()
                                .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
                            // Cards: 8dp, matching the checklist detail list — the cards are the same
                            // object there and the two screens must not use two different rhythms for
                            // it. Compact: zero, because the rows are separated by a rule instead, and
                            // a gap on top of a divider reads as a broken card list.
                            verticalArrangement = if (compact) {
                                Arrangement.spacedBy(0.dp)
                            } else {
                                Arrangement.spacedBy(AppDimens.SpacingSm)
                            },
                            // Top padding so the first card does not touch the dots row; the bottom
                            // reserve is the shell's FAB band, passed in by the host.
                            contentPadding = PaddingValues(
                                top = AppDimens.SpacingSm,
                                bottom = AppDimens.SpacingXl + contentBottomPadding,
                            ),
                        ) {
                            itemsIndexed(page.tasks, key = { _, task -> task.fillItemId }) { index, task ->
                                InboxTaskRow(
                                    task = task,
                                    compact = compact,
                                    onCheckedChange = { checked ->
                                        onIntent(
                                            InboxIntent.OnTaskCheckedChanged(task.fillItemId, checked)
                                        )
                                    },
                                    onDetailsClick = {
                                        onIntent(InboxIntent.OnTaskDetailsClick(task.fillItemId))
                                    },
                                )
                                // Between rows only — a trailing rule under the last item would read
                                // as "the list continues below" at the end of a short list.
                                if (compact && index < page.tasks.lastIndex) {
                                    HorizontalDivider(
                                        thickness = AppDimens.DividerThickness,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
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
 * Position indicator for the checklist pager — the only thing left where the tab row used to be.
 *
 * ## Why the tab row went away
 * It carried a PINNED Inbox pill plus a scrolling row of project pills, and it lost on both counts:
 * the pinned pill and the scrolling ones read as one undifferentiated strip, and with five or six
 * projects the row became a second navigation surface competing with the bottom bar. Todoist has no
 * such strip — its toolbar simply names the one view you are on, which is where the title and the
 * open-task count moved.
 *
 * ## Why dots stayed
 * Todoist can drop the indicator because it has no pager at all: you switch views through Overview,
 * so "where am I" is never ambiguous. Here the pages ARE swipeable, so with nothing in this slot a
 * swipe would be an undiscoverable gesture and the page count invisible. Dots are the minimum that
 * answers both — the title says WHICH page, the dots say WHERE in the run and HOW MANY.
 *
 * Each dot stays tappable so the row keeps the direct jump the pills used to offer. The target is
 * 32dp, not the 48dp minimum: a 48dp row under the toolbar would cost more vertical space than the
 * tab row it replaced. That is acceptable here and only here — tapping a dot is a shortcut, while
 * swiping is the primary way to change page, so a missed tap costs a retry, not access.
 *
 * @param pageTitles one per page, IN PAGER ORDER — a dot is a `Role.Tab` and a tab without a name is
 *   announced as an anonymous one, so a screen-reader user could no longer tell which checklist a
 *   dot jumps to (the pill row it replaced carried the title as its label). The title itself is the
 *   name, exactly as the pills read it: it needs no format string and therefore no new resource.
 */
@Composable
private fun InboxPagerDots(
    pageTitles: List<String>,
    selectedPage: Int,
    onSelectPage: (Int) -> Unit,
) {
    // One page = no position to report. Rendering a lone dot would be decoration that implies a
    // second page exists.
    if (pageTitles.size <= 1) return

    val dotsState = rememberLazyListState()

    // Keeps the selected dot on screen once the run overflows: swiping past the last dot that fits
    // would otherwise leave the highlight beyond the row's edge, which is the same silent lie the
    // lazy row exists to prevent, just one swipe further out. Centres it rather than merely
    // revealing it, so the neighbours on both sides stay one tap away. A lazy list clamps at both
    // ends and cannot scroll at all while everything fits, so a short run is untouched.
    LaunchedEffect(selectedPage, pageTitles.size) {
        val layout = dotsState.layoutInfo
        val viewport = layout.viewportEndOffset - layout.viewportStartOffset
        val dotWidth = layout.visibleItemsInfo.firstOrNull()?.size ?: 0
        dotsState.animateScrollToItem(
            // Coerced, not trusted: this reads a pager index against a list that a delete on another
            // device can shorten a frame earlier.
            index = selectedPage.coerceIn(0, pageTitles.lastIndex),
            scrollOffset = -(viewport - dotWidth) / 2,
        )
    }

    // A LAZY row, and the shape is load-bearing rather than a preference. A plain Row measures its
    // overflowing unweighted children against a remaining maxWidth of 0, so past
    // windowWidth / InboxDotTouchTarget every further dot renders at zero width — INCLUDING the
    // selected one. With 12 projects on a 360dp phone (13 pages) the last two dots disappear and
    // swiping there highlights nothing, i.e. the position indicator silently lies about where the
    // user is. The project count is unbounded, which is exactly why the tab row this replaced was
    // scrollable too. Lazy layout measures each dot against its own constraints and lets the surplus
    // scroll instead of collapsing — do not "simplify" this back to a Row.
    LazyRow(
        state = dotsState,
        modifier = Modifier
            .fillMaxWidth()
            .height(InboxDotsRowHeight),
        // A lazy list applies its arrangement only while the whole run fits, so this centres a short
        // run and a long one simply packs from the start and scrolls.
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(pageTitles) { index, title ->
            val selected = index == selectedPage
            Box(
                modifier = Modifier
                    // Touch target first, mark second: sizing the clickable to the dot itself gives
                    // an 8dp target, so the jump would be a coin flip.
                    .size(InboxDotTouchTarget)
                    .clickable(
                        // No ripple: a 48dp circular ripple around an 8dp dot looks like a bug.
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onSelectPage(index) },
                    )
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                        // The dot draws nothing readable, so this is the ONLY thing that identifies
                        // the page to a screen reader.
                        contentDescription = title
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (selected) InboxDotSizeSelected else InboxDotSize)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                )
            }
        }
    }
}

private val InboxDotsRowHeight = 32.dp
private val InboxDotTouchTarget = 32.dp
private val InboxDotSize = 6.dp
private val InboxDotSizeSelected = 8.dp

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
 *
 * @param compact renders the same row WITHOUT the card: a shorter, flat line whose only separator is
 *   the divider the list draws between neighbours. The chrome is what costs the vertical space, so
 *   dropping it — rather than shrinking the type — is what fits more tasks on screen while the text
 *   stays exactly as readable.
 */
@Composable
private fun InboxTaskRow(
    task: InboxTask,
    compact: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDetailsClick: () -> Unit,
) {
    // One content block, two containers. Extracting the content into a local lambda keeps the 30/70
    // hit-zone split and the strike-through logic single-sourced — two copies would drift, and this
    // row's hit zones are load-bearing enough that a drift here is a silent UX regression.
    val rowContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 56dp for cards (not the detail card's 64dp: an Inbox row carries no note or meta
                // chips); 44dp compact, which is still a full touch target for the checkbox zone.
                .heightIn(min = if (compact) InboxTaskRowCompactMinHeight else InboxTaskRowMinHeight),
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

    if (compact) {
        // No Card, no border, no shape: on the page background this is a plain line, and the list's
        // own divider is the separator. Wrapping it in a zero-elevation borderless Card instead would
        // still paint the card's container colour and leave the row looking like a card that lost its
        // outline.
        rowContent()
    } else {
        Card(
            colors = AppCardDefaults.colors(),
            border = AppCardDefaults.border(),
            elevation = AppCardDefaults.flatElevation(),
            shape = MaterialTheme.shapes.medium,
        ) {
            rowContent()
        }
    }
}

private val InboxTaskRowMinHeight = 56.dp
private val InboxTaskRowCompactMinHeight = 44.dp
