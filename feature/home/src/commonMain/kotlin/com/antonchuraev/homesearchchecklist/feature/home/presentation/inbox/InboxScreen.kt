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
import aichecklists.core.designsystem.generated.resources.inbox_ai_entry_title
import aichecklists.core.designsystem.generated.resources.inbox_display_options
import aichecklists.core.designsystem.generated.resources.inbox_empty_description
import aichecklists.core.designsystem.generated.resources.inbox_empty_title
import aichecklists.core.designsystem.generated.resources.inbox_error_retry
import aichecklists.core.designsystem.generated.resources.inbox_list_actions
import aichecklists.core.designsystem.generated.resources.inbox_menu_open_checklist
import aichecklists.core.designsystem.generated.resources.inbox_open_project_action
import aichecklists.core.designsystem.generated.resources.inbox_project_empty_description
import aichecklists.core.designsystem.generated.resources.inbox_quick_add_placeholder
import aichecklists.core.designsystem.generated.resources.inbox_section_anytime
import aichecklists.core.designsystem.generated.resources.inbox_section_overdue
import aichecklists.core.designsystem.generated.resources.inbox_section_today
import aichecklists.core.designsystem.generated.resources.inbox_section_upcoming
import aichecklists.core.designsystem.generated.resources.inbox_task_count
import aichecklists.core.designsystem.generated.resources.inbox_task_sheet_move
import aichecklists.core.designsystem.generated.resources.inbox_title
import aichecklists.core.designsystem.generated.resources.save
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AiEntrySource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxLayout
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.FilePickerResult
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.FilePickerType
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.rememberFilePickerLauncher
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderDateTimePicker
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheet
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheetCallbacks
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheetState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.AttachmentFullscreenViewer
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.ItemDetailsSheet
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.ItemDetailsSheetRow
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.NoteDialog
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.NotificationPermissionSheet
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.rememberNotificationPermissionRequester
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCardDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppPlanNudge
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppSkeletonLine
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.components.PlatformBackHandler
import com.antonchuraev.homesearchchecklist.desingsystem.components.QuickCaptureDock
import com.antonchuraev.homesearchchecklist.desingsystem.components.SourceRow
import com.antonchuraev.homesearchchecklist.desingsystem.components.captureDockScrimColor
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.TaskCreateChipsRow
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components.CreditsChipSource
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components.CreditsToolbarAction
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDensity
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTextStyles
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiSchedule
import com.antonchuraev.homesearchchecklist.feature.home.presentation.components.AddTaskRow
import com.antonchuraev.homesearchchecklist.feature.home.presentation.components.TaskRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
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
 * ## Where capture is reached from
 * On COMPACT, an [AddTaskRow] at the END of the task list, scrolling with it — Todoist's anatomy,
 * picked by the owner after seeing it and a pinned variant rendered side by side. It replaced the
 * shell's floating "+" FAB, which read as chrome hovering over the content and left the tab's
 * primary action off the list it acts on. Two earlier shapes were tried and rejected: a row PINNED
 * above the bottom bar (it made the tab read as an input form) and the FAB (self-evident, but
 * detached). At rail and permanent-drawer width the shell KEEPS its "+" and the row is withheld —
 * see `showAddTaskRow` in the body.
 *
 * The row is composed for EVERY page state, empty ones included — see the empty-state item in
 * [InboxPageList]. A new user's Inbox is empty by definition, so an add affordance that only appears
 * once there is something to add is no affordance at all.
 *
 * @param contentBottomPadding inset the v2 shell reserves for the part of its raised AI button that
 *   overhangs the bottom bar and is drawn over this screen. (The bar itself needs no reserve: the
 *   shell renders it outside the content slot and consumes `WindowInsets.navigationBars` there.)
 *   Applied to the BOTTOMMOST element — the capture dock while it is up, the pager's content padding
 *   otherwise — because applying it to both would reserve the space twice.
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
    // would render an add-task row whose dock never appears — a silently dead affordance, which is
    // exactly the failure this arm already shipped once on the rail. Make the compiler ask.
    createDockOpen: Boolean,
    onCreateDockDismiss: () -> Unit,
    homeSignal: Int = 0,
    /** See [InboxRoute] — hoisted to the shell because this entry does not survive a pushed route. */
    anchorChecklistId: Long? = null,
    onAnchorChecklistChanged: (Long?) -> Unit = {},
    /**
     * Opens the daily review. **Null means the nudge is not drawn at all.**
     *
     * Nullable rather than a no-op default on purpose: the review screen does not exist yet, and an
     * invitation that leads nowhere is worse than no invitation — the user taps once, nothing
     * happens, and they stop trusting the surface. So the affordance is composed only once a host
     * can actually honour it.
     */
    onPlanDayClick: (() -> Unit)? = null,
    /**
     * Analytics `source` for the AI-credits chip in this screen's top bar; **null draws no chip**.
     *
     * Same shape (and same reason) as [CalendarScreen]'s: the chip is an arm-specific affordance, and
     * a screen cannot see the nav arm. Threading the source rather than a boolean means the host
     * cannot mount the chip without also declaring which surface it reports as — the paywall's
     * `source` is the only thing that separates the four v2 tabs in the funnel, and a chip that
     * reports the wrong one is as invisible as a chip that is absent.
     *
     * Pass [CreditsChipSource.V2_INBOX]. Null keeps previews and screenshot tests Koin-free.
     */
    creditsSource: String? = null,
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

    // Can the dock actually be drawn right now? Not the same question as [createDockOpen], because
    // the dock also needs a page to capture INTO — see the `bottomBar` slot, which is the one place
    // that used to ask this.
    val captureDockRenders = createDockOpen && !content?.pages.isNullOrEmpty()

    // ── The flag must never outlive the dock it stands for ───────────────────────────────────────
    // The v2 shell takes the WHOLE bottom navigation off screen while `createDockOpen` is true (the
    // dock is the bottom chrome in that state — see V2ShellCompactBar). That makes a mismatch between
    // the flag and the dock a dead end rather than a cosmetic slip: a later emission of this screen's
    // state can drop back to Loading (`pages == null` on a re-collect) or to Error (a failed refresh
    // AFTER a successful load), the dock stops rendering with it, and the user is left on a screen
    // with no dock and no navigation. BACK still saves them on Android; on wasmJs `PlatformBackHandler`
    // is a no-op, so there would be no way out at all.
    //
    // Reported to the HOST rather than patched locally: the host owns the flag, and it is the same
    // channel the dismiss gestures use, so the shell's chrome comes back through exactly one path.
    LaunchedEffect(createDockOpen, captureDockRenders) {
        if (createDockOpen && !captureDockRenders) onCreateDockDismiss()
    }

    // Whether the trailing add-task row is composed at all. Two independent reasons to withhold it,
    // and both are decided here rather than inside the list, which knows neither:
    //
    //  1. NOT COMPACT. The v2 shell drops its floating "+" on Compact only; at rail and
    //     permanent-drawer width it still renders one, and `showCreateFab` covers this tab. Drawing
    //     the row there as well gave ONE action two doors on one screen, and made the shell's own
    //     rationale ("without this button there is no way to add a task at rail width at all")
    //     false. Nothing is lost by narrowing it: every window size keeps exactly one way in.
    //  2. THE DOCK IS UP. The dock is the expanded form of this row; leaving both composed puts two
    //     task inputs on one screen (the Calendar tab hides its pinned copy for the same reason),
    //     and the row is unreachable anyway under the dismiss overlay — a visible control that
    //     cannot be tapped.
    //
    // This screen is mounted by the v2 arm only, so there is no arm gate to read first: the classic
    // shell never composes it.
    val showAddTaskRow = !createDockOpen &&
        rememberAppWindowSizeClass() == AppWindowSizeClass.Compact

    // The toolbar names the page the pager is on — this is what replaced the tab row. Read from the
    // ViewModel's settled index rather than from the pager, because the bar lives OUTSIDE the pager's
    // composable and hoisting the pager state up here would recompose the whole scaffold on every
    // frame of a swipe.
    val currentPage = content?.pages?.getOrNull(content.selectedPage)

    // ── Capture-dock scrim: TWO tiled scrims, one flag ───────────────────────────────────────────
    // Same anatomy as the item-create scrim on ChecklistDetailScreen, and for the same reason. The
    // dock lives in the scaffold's `bottomBar`, so the CONTENT scrim (a child of the content slot)
    // already stops exactly where the dock begins — the dock, the snackbar and the system-nav strip
    // stay bright with nothing to measure and nothing to subtract.
    //
    // A single full-screen scrim with the dock's height cut out of the bottom was tried instead and
    // is what this replaces: the slot's ime ∪ navigationBars padding is applied by the scaffold
    // OUTSIDE the measured node, so once the keyboard was up the cut-out sat a keyboard's height
    // BELOW the dock — dimming the input and leaving a bright band under it. The height of a dock
    // that rides the keyboard is not a number this screen can hold correctly; the slot boundary is.
    //
    // [contentTopPx] is the content slot's y in the root = the height of the zone the scaffold owns
    // above it (status bar + app bar). The TOP scrim uses exactly it, so the two tile edge-to-edge:
    // no bright gap at the seam, no double-dark overlap.
    var contentTopPx by remember { mutableStateOf(0f) }
    val captureScrimColor = captureDockScrimColor()

    // Root box so the TOP scrim can be a sibling ABOVE the scaffold — the app bar is the scaffold's
    // own slot, and nothing inside the content can reach it.
    Box(modifier = Modifier.fillMaxSize()) {
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
            // The credits chip LEADS, and it is composed outside the `content != null` guard on
            // purpose: the paywall entry point must survive Loading and Error too. This tab is the v2
            // home, and "I can't find where to open the paywall from the home screen" is the report
            // this chip exists to answer — an entry point that disappears while the list is loading
            // is the same defect with a smaller window.
            //
            // Flat siblings, not a Row: `AppScaffold` forwards this slot into Material's
            // `actions: RowScope.() -> Unit`, so everything emitted here is already laid out in one
            // row, in emission order.
            actions = {
                if (creditsSource != null) {
                    CreditsToolbarAction(source = creditsSource)
                }
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
            // text). No longer merely defensive — the shell hides the whole bottom navigation while the
            // dock is up, so this condition and the flag must agree; see `captureDockRenders` above,
            // which is where the disagreement is now resolved rather than just tolerated.
            //
            // `content != null` is redundant with `captureDockRenders` (it cannot be non-empty on a
            // null Content) and kept only because the body below smart-casts off it.
            bottomBar = {
                if (captureDockRenders && content != null) {
                    QuickCaptureDock(
                        text = content.draft.text,
                        onTextChange = { onIntent(InboxIntent.OnQuickAddTextChanged(it)) },
                        onAdd = { onIntent(InboxIntent.OnQuickAddSubmit) },
                        placeholder = stringResource(Res.string.inbox_quick_add_placeholder),
                        // The THIRD tile of the scrim, and the one that is easy to forget because
                        // nothing about it is dim: it is painted BEHIND an opaque dock, so the only
                        // pixels it ever reaches are the two the dock's `SheetTop` clips away. Those
                        // corners showed the raw page (`#FBFAF8`) against the 45%-dimmed page beside
                        // them (`#8A8988`) — ΔL* +41, the brightest thing in the lower half of the
                        // screen, reported from a Pixel 9 as two light corners next to the dock. The
                        // content scrim cannot reach them: it stops at the content slot's edge, which
                        // is deliberately the dock's top edge. Same colour, same alpha, so the
                        // shoulder and the page above it composite to the same value.
                        modifier = Modifier.background(captureScrimColor),
                        // The same chip row the checklist detail screen has always had. Until now
                        // this dock was a bare text field, so a capture made on the home tab could
                        // carry no reminder and no priority — the surface the user reaches FIRST
                        // was the weakest.
                        //
                        // Pick-time and Repeat stay off: both open sheets that only the detail
                        // screen hosts, and a chip that swallows its tap is worse than an absent
                        // one.
                        aboveInput = {
                            TaskCreateChipsRow(
                                draft = content.draft,
                                onAction = { onIntent(InboxIntent.OnCreateChipAction(it)) },
                                showPickTime = false,
                                showRepeat = false,
                            )
                        },
                        // The main entry into Analyze. Inside the dock rather than behind the "+"
                        // or an overflow: content → checklist is HALF of all checklist creation
                        // (20 of 40 unique creators), and the v2 shell shipped with no route to it
                        // at all — the funnel "saw the v2 shell → started a analysis" read 31 → 0.
                        // A door that has to be discovered is the state we are leaving, not
                        // arriving at.
                        belowInput = {
                            SourceRow(
                                onSelect = { kind ->
                                    onIntent(
                                        InboxIntent.OnAiSourceTapped(
                                            kind = kind,
                                            source = AiEntrySource.CAPTURE_DOCK_INBOX,
                                        )
                                    )
                                },
                            )
                        },
                    )
                }
            },
        ) {
            // Error BEFORE the null-content branch. A failed load leaves `content` null too, so
            // testing for null first is exactly how a failure used to render as a spinner that never
            // resolved — the defect this branch exists to close.
            if (state is InboxScreenState.Error) {
                InboxErrorState(
                    message = state.message,
                    canRetry = state.canRetry,
                    onRetry = { onIntent(InboxIntent.OnRetryLoad) },
                )
            } else if (content == null) {
                // Skeleton rather than a centred spinner: it shows the SHAPE of what is coming, so
                // the list does not jump from a circle to five cards.
                InboxLoadingSkeleton()
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // The seam between the two scrims: this box's y in the root IS the height of
                        // the chrome above it (status bar + app bar), which the top scrim uses as its
                        // own height. Measured rather than assumed because the bar carries a subtitle
                        // and grows with fontScale.
                        .onGloballyPositioned { contentTopPx = it.positionInRoot().y },
                ) {
                    InboxContent(
                        content = content,
                        pages = content.pages,
                        layout = content.displayOptions.layout,
                        // While the dock is up it occupies the bottomBar slot, so the Scaffold has already
                        // shortened the content by its height — reserving the AI button's band on top of
                        // that would strand the list above a gap the size of chrome that is not on screen.
                        contentBottomPadding = if (createDockOpen) 0.dp else contentBottomPadding,
                        showAddTaskRow = showAddTaskRow,
                        onIntent = onIntent,
                        homeSignal = homeSignal,
                        anchorChecklistId = anchorChecklistId,
                        onAnchorChecklistChanged = onAnchorChecklistChanged,
                        onPlanDayClick = onPlanDayClick,
                    )

                    // CONTENT scrim + tap-outside-to-dismiss, in one node. It ends where the content
                    // slot ends, which is exactly where the dock's slot begins — so the dock stays
                    // bright at any keyboard height, and the system-nav strip its slot pads for is
                    // never dimmed (rule `designsystem`: the strip and the dock are one surface).
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
                                .background(captureScrimColor)
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { onCreateDockDismiss() })
                                },
                        )
                    }
                }
            }
        }

        // TOP-BAR scrim — the other tile. Dims the status-bar zone and the app bar, which belong to
        // the scaffold and are out of reach from inside its content slot. Height is exactly the
        // content slot's offset, so it meets the content scrim edge-to-edge.
        //
        // No pointer-input modifier on purpose: a node with only a background takes no part in
        // hit-testing, so the toolbar's actions stay pressable through the dim instead of being
        // swallowed by it.
        if (createDockOpen && contentTopPx > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { contentTopPx.toDp() })
                    .background(captureScrimColor),
            )
        }
    }

    // Sheets live OUTSIDE the scaffold so they float above the whole screen, matching the detail
    // screen's ItemDetailsSheet placement.
    if (content != null) {
        val page = content.pages.getOrNull(content.selectedPage)
        InboxItemSheetHost(content = content, onIntent = onIntent)

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
 * Every surface the per-task item sheet raises, in one place.
 *
 * The sheet itself is the checklist detail screen's [ItemDetailsSheet] — the SAME composable, not a
 * copy. That is the whole point of this host: the Inbox used to draw a four-row look-alike (move,
 * priority, open project, delete), so the screen the v2 user reaches FIRST offered three actions
 * where the screen one tap deeper offered a dozen. Reusing the real sheet means reminder, note,
 * open-link, attachments and inline rename arrive here for free — and stay in step forever, because
 * there is only one of them.
 *
 * [ItemDetailsSheet] renders rows only; each row's target surface belongs to its host. All of them
 * are wired below (reminder sheet, note dialog, date/time picker, notification-permission sheet,
 * file pickers, fullscreen viewer) — a row whose surface is missing is a tap that does nothing, which
 * is worse than an absent row.
 *
 * ## Why the sub-surfaces REPLACE the sheet instead of stacking on it
 * Two simultaneous `ModalBottomSheet`s fight over the scrim and Android's predictive-back gesture.
 * The detail screen sidesteps this by making its item sheet and its reminder sheet mutually
 * exclusive; here the same is done by [subSurfaceOpen], with one improvement: the task id survives,
 * so dismissing a sub-surface returns to the item sheet instead of closing everything.
 */
@Composable
private fun InboxItemSheetHost(
    content: InboxScreenState.Content,
    onIntent: (InboxIntent) -> Unit,
) {
    // Searched across ALL pages, not just the selected one: a sync can shift the pager under an open
    // sheet, and looking the row up by index would then render a different task's data into it.
    val task = content.sheetForTaskId?.let { id ->
        content.pages.firstNotNullOfOrNull { page -> page.tasks.firstOrNull { it.fillItemId == id } }
    }
    val page = content.sheetForTaskId?.let { id ->
        content.pages.firstOrNull { p -> p.tasks.any { it.fillItemId == id } }
    }

    // ── Attachment pickers ───────────────────────────────────────────────────────────────────
    // Composed unconditionally (this host is called for every Content state) so the launcher is
    // still registered when the picker Activity returns. On wasmJs a picker callback captures
    // Compose state at composition time, so both `onIntent` and the pending id are read through
    // rememberUpdatedState — the stale-closure trap this project has hit before.
    val latestOnIntent by rememberUpdatedState(onIntent)
    val pendingAttachmentTaskId by rememberUpdatedState(content.pendingAttachmentTaskId)
    val logger: AppLogger = koinInject()

    val onPicked: (String, FilePickerResult?) -> Unit = { launchedIntentTag, result ->
        val taskId = pendingAttachmentTaskId
        if (taskId == null) {
            // Never silent: a dropped result looks exactly like a picker that did nothing.
            logger.warning("InboxAttachments", "$launchedIntentTag: no pending task — result dropped")
        } else if (result != null) {
            latestOnIntent(
                InboxIntent.OnAttachmentPicked(
                    taskId = taskId,
                    sourcePath = result.filePath,
                    fileName = result.fileName,
                    mimeType = result.mimeType,
                )
            )
        }
    }

    val imagePicker = rememberFilePickerLauncher(type = FilePickerType.IMAGE) { result ->
        latestOnIntent(InboxIntent.OnImagePickerLaunched)
        onPicked("image picker", result)
    }
    val filePicker = rememberFilePickerLauncher(type = FilePickerType.ANY) { result ->
        latestOnIntent(InboxIntent.OnFilePickerLaunched)
        onPicked("file picker", result)
    }
    LaunchedEffect(content.triggerImagePicker) {
        if (content.triggerImagePicker) imagePicker.launch()
    }
    LaunchedEffect(content.triggerFilePicker) {
        if (content.triggerFilePicker) filePicker.launch()
    }

    if (task == null) return

    // ONE surface at a time, arbitrated by a pure function so the "never two modals" rule is pinned
    // by a test rather than by the order of the `if`s below.
    val surface = resolveItemSheetSurface(content, taskFound = true)

    if (surface == InboxItemSheetSurface.ITEM_SHEET) {
        ItemDetailsSheet(
            item = task.item,
            isEditingText = content.sheetTextEditing,
            editingTextDraft = content.sheetTextDraft,
            onStartTextEdit = { onIntent(InboxIntent.OnTaskTextEditStart) },
            onTextDraftChange = { onIntent(InboxIntent.OnTaskTextDraftChanged(it)) },
            onConfirmTextEdit = { onIntent(InboxIntent.OnTaskTextEditConfirm) },
            onReminderClick = { onIntent(InboxIntent.OnTaskReminderClick) },
            onNoteClick = { onIntent(InboxIntent.OnTaskNoteClick) },
            onTogglePriority = { onIntent(InboxIntent.OnToggleImportant) },
            onDelete = { onIntent(InboxIntent.OnDeleteTask) },
            onDismiss = { onIntent(InboxIntent.OnTaskSheetDismiss) },
            onAttachmentClick = { id -> onIntent(InboxIntent.OnAttachmentClick(id)) },
            onAddImageClick = { onIntent(InboxIntent.OnAddImageAttachment) },
            onAddFileClick = { onIntent(InboxIntent.OnAddFileAttachment) },
            canAddAttachment = content.canAddAttachment(task.item.attachments.size),
            hostActions = {
                // "Move to project" — the row this round exists to add. Offered on every page: from
                // the Inbox it is triage, from a project it is a re-file.
                ItemDetailsSheetRow(
                    icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = stringResource(Res.string.inbox_task_sheet_move),
                    subtitle = null,
                    showChevron = true,
                    onClick = { onIntent(InboxIntent.OnMovePickerOpen) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // "Open project" — carried over from the sheet this replaced. Only on project pages:
                // the system Inbox page has no detail screen to open.
                if (page != null && !page.isInbox) {
                    ItemDetailsSheetRow(
                        icon = Icons.Outlined.ChecklistRtl,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(Res.string.inbox_open_project_action),
                        subtitle = null,
                        showChevron = true,
                        onClick = { onIntent(InboxIntent.OnOpenProject(page.checklistId)) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            },
        )
    }

    if (surface == InboxItemSheetSurface.MOVE_PICKER) {
        InboxMoveProjectPicker(moveTargets = content.moveTargets, onIntent = onIntent)
    }

    content.noteDraft?.takeIf { surface == InboxItemSheetSurface.NOTE_DIALOG }?.let { draft ->
        NoteDialog(
            note = draft,
            onNoteChanged = { onIntent(InboxIntent.OnTaskNoteDraftChanged(it)) },
            onDismiss = { onIntent(InboxIntent.OnTaskNoteDismiss) },
            onConfirm = { onIntent(InboxIntent.OnTaskNoteSave) },
        )
    }

    // Asked BEFORE the reminder sheet is usable: on Android 13+ an alarm scheduled without the
    // notification permission is posted and dropped, so the user would be told a reminder is set and
    // never hear it.
    if (surface == InboxItemSheetSurface.NOTIFICATION_PERMISSION) {
        val requestPermission = rememberNotificationPermissionRequester { granted ->
            onIntent(InboxIntent.OnNotificationPermissionResult(granted))
        }
        NotificationPermissionSheet(
            onEnableClick = requestPermission,
            onSkip = { onIntent(InboxIntent.OnNotificationPermissionSkip) },
            onDismiss = { onIntent(InboxIntent.OnNotificationPermissionSkip) },
        )
    }

    // The same shared ReminderSheet the detail screen opens for a per-item reminder — once/repeat
    // tabs, full-screen delivery toggle, calendar export, free-tier locked banner.
    //
    // Held back while the permission sheet or the date picker is up: all three are modal, and the
    // ViewModel keeps `reminderSheet` non-null across both so their state (notably the full-screen
    // toggle) survives and dismissing them lands back here instead of closing everything.
    content.reminderSheet?.takeIf { surface == InboxItemSheetSurface.REMINDER_SHEET }?.let { reminder ->
        ReminderSheet(
            state = ReminderSheetState(
                activeTab = reminder.tab,
                currentReminder = task.item.reminderAt,
                currentRepeatRule = task.item.repeatRule,
                repeatRuleSummary = reminder.repeatRuleSummary,
                pendingRepeatConfig = reminder.pendingRepeatConfig,
                showEndConditionPicker = reminder.showEndConditionPicker,
                isLocked = reminder.locked,
                showFullScreenOption = true,
                fullScreenEnabled = reminder.fullScreen,
            ),
            callbacks = ReminderSheetCallbacks(
                onTabSelected = { onIntent(InboxIntent.OnReminderTabSelected(it)) },
                onPresetSelected = { onIntent(InboxIntent.OnReminderPresetSelected(it)) },
                onCustomDateRequested = { onIntent(InboxIntent.OnCustomDateRequested) },
                onRemoveReminder = { onIntent(InboxIntent.OnReminderRemove) },
                onRepeatTypeSelected = { onIntent(InboxIntent.OnRepeatTypeSelected(it)) },
                onSmartPresetSelected = { onIntent(InboxIntent.OnSmartPresetSelected(it)) },
                onRepeatIntervalChanged = { onIntent(InboxIntent.OnRepeatIntervalChanged(it)) },
                onWeekDayToggled = { onIntent(InboxIntent.OnWeekDayToggled(it)) },
                onResetChecksToggled = { onIntent(InboxIntent.OnResetChecksToggled(it)) },
                onRepeatTimeChanged = { h, m -> onIntent(InboxIntent.OnRepeatTimeChanged(h, m)) },
                onEndConditionClick = { onIntent(InboxIntent.OnEndConditionClick) },
                onEndConditionSelected = { onIntent(InboxIntent.OnEndConditionSelected(it)) },
                onDismissEndCondition = { onIntent(InboxIntent.OnEndConditionDismiss) },
                onSaveRepeat = { onIntent(InboxIntent.OnRepeatSave) },
                onRemoveRepeat = { onIntent(InboxIntent.OnRepeatRemove) },
                onAddToCalendar = { onIntent(InboxIntent.OnReminderAddToCalendar) },
                onFullScreenToggled = { onIntent(InboxIntent.OnReminderFullScreenToggled(it)) },
                onDismiss = { onIntent(InboxIntent.OnReminderSheetDismiss) },
                onUpgradeClick = { onIntent(InboxIntent.OnReminderUpgradeClick) },
            ),
        )
    }

    content.customPicker?.takeIf { surface == InboxItemSheetSurface.CUSTOM_DATE_PICKER }?.let { picker ->
        ReminderDateTimePicker(
            selectedDateMillis = picker.dateMillis,
            minDateMillis = picker.minDateMillis,
            initialHour = picker.initialHour,
            isTimeInPast = picker.timeInPast,
            onDateSelected = { onIntent(InboxIntent.OnCustomDateSelected(it)) },
            onTimeChanged = { h, m -> onIntent(InboxIntent.OnCustomTimeChanged(h, m)) },
            onTimeSelected = { h, m -> onIntent(InboxIntent.OnCustomTimeSelected(h, m)) },
            onDismiss = { onIntent(InboxIntent.OnCustomPickerDismiss) },
        )
    }

    // Drawn ON TOP of the item sheet rather than replacing it (it is a Dialog, not a bottom sheet —
    // the stacking constraint above does not apply), matching the detail screen.
    content.attachmentViewerFor?.let { initialAttachmentId ->
        if (task.item.attachments.isNotEmpty()) {
            AttachmentFullscreenViewer(
                attachments = task.item.attachments,
                initialAttachmentId = initialAttachmentId,
                onClose = { onIntent(InboxIntent.OnAttachmentViewerClose) },
                onDelete = { id -> onIntent(InboxIntent.OnAttachmentDelete(id)) },
                onOpenExternally = { id -> onIntent(InboxIntent.OnAttachmentOpenExternally(id)) },
            )
        } else {
            // Last attachment deleted while the viewer was up — close it instead of showing a blank.
            LaunchedEffect(Unit) { onIntent(InboxIntent.OnAttachmentViewerClose) }
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
    showAddTaskRow: Boolean,
    onIntent: (InboxIntent) -> Unit,
    homeSignal: Int = 0,
    anchorChecklistId: Long? = null,
    onAnchorChecklistChanged: (Long?) -> Unit = {},
    onPlanDayClick: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    // pageCount is a lambda, not a snapshot: rememberPagerState re-reads it every composition, so a
    // project created or deleted while the tab is open resizes the pager without a state reset.
    //
    // initialPage is resolved from the SHELL-held anchor, not from a literal 0 and not from the
    // ViewModel.
    //
    // Opening a checklist pushes the detail route over this entry, and coming back neither this
    // composition nor the InboxViewModel survives — measured on an emulator, not reasoned about: an
    // earlier fix seeded the pager from `content.selectedPage` and the tab still landed on the Inbox,
    // because the ViewModel holding that page had itself been rebuilt. Worse, the write-back effect
    // below then reported the fresh 0 outward, so the composition did not merely lose its own state,
    // it overwrote whatever had survived elsewhere.
    //
    // The anchor is therefore owned by the shell, which outlives the entry, and is a checklist ID
    // rather than an index — a create inserts at position 0 and shifts every index by one.
    val initialPage = remember(pages) {
        pages.indexOfFirst { it.checklistId == anchorChecklistId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pages.size },
    )

    // Pager indices are POSITIONAL, and a new checklist is inserted at position 0
    // (ChecklistRepositoryImpl.addChecklist increments every other position), so it lands at pages[1]
    // and shifts every project one slot right. Anchoring on the checklist id of the page the user
    // settled on is what keeps them on THEIR project when a checklist is created elsewhere (AI chat,
    // widget, sync from another device) — without it the pager silently shows a different project and
    // the pinned quick-add row retargets with it.
    //
    // The anchor itself is the hoisted [anchorChecklistId] param, NOT a local `remember`. A local one
    // dies with this composition, which is precisely why it could not survive opening a checklist.

    // Swipes are the source of truth for the selected page; this mirrors them back into the
    // ViewModel so quick-add and the move-target list follow the finger, and outward to the shell so
    // the page survives a pushed route. Tab taps go the other way (animateScrollToPage), and land
    // here on settle — one direction each, so no feedback loop.
    // Deliberately NOT keyed on `pages`: re-running it on every list edit would re-report an index
    // the user never moved to.
    LaunchedEffect(pagerState.currentPage) {
        onAnchorChecklistChanged(pages.getOrNull(pagerState.currentPage)?.checklistId)
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
            onAnchorChecklistChanged(pages.getOrNull(pagerState.currentPage)?.checklistId)
        } else if (index != pagerState.currentPage) {
            pagerState.scrollToPage(index)
        }
    }

    // No imePadding here: the keyboard inset is owned by the capture dock, and the dock lives in the
    // scaffold's bottomBar slot, which applies it already. Insetting this Column on top of that
    // shortens the content twice — AppScaffold's content slot already sits inside the resolved
    // window insets.
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
                    else -> InboxPageList(
                        page = page,
                        compact = layout == InboxLayout.COMPACT,
                        groupByDate = content.displayOptions.groupByDate,
                        nowMillis = content.nowMillis,
                        // The other half of the "do not move a row out from under the finger"
                        // rule: a sheet is open over the list, so the row it describes must not be
                        // re-filed while the user is reading it.
                        sheetOpen = content.sheetForTaskId != null,
                        contentBottomPadding = contentBottomPadding,
                        showAddTaskRow = showAddTaskRow,
                        planNudgeSuppressed = content.planNudgeDismissed,
                        onPlanDayClick = onPlanDayClick,
                        onIntent = onIntent,
                    )
                }
            }
        }

        // The capture DOCK is not here — it is the scaffold's bottomBar (see InboxScreen), the only
        // slot that gets lifted above the keyboard automatically. What this Column owns is the
        // add-task ROW that raises it, and that row sits INSIDE the pager's LazyColumn rather than
        // pinned here. No imePadding() belongs anywhere in this content: the dock owns the keyboard
        // inset from its slot, and a second reader of it here would fight the scaffold for the same
        // space (that defect already shipped once on this screen).
    }
}

/**
 * One page of the pager: the task list of a single checklist, its empty state, and the add-task row.
 *
 * A composable of its own rather than a lambda body inside [InboxContent] — it is the page anatomy,
 * and inlined it sat eight levels deep, where the horizontal inset below could not be read against
 * the list it applies to.
 *
 * @param showAddTaskRow whether the trailing add-task row is composed. Decided by [InboxScreen] (see
 *   its `showAddTaskRow`), never here: the reasons are the window size and the host's dock flag, and
 *   neither is this composable's business.
 */
@Composable
private fun InboxPageList(
    page: InboxPage,
    compact: Boolean,
    groupByDate: Boolean,
    nowMillis: Long,
    sheetOpen: Boolean,
    contentBottomPadding: Dp,
    showAddTaskRow: Boolean,
    planNudgeSuppressed: Boolean,
    onPlanDayClick: (() -> Unit)?,
    onIntent: (InboxIntent) -> Unit,
) {
    // The screen's horizontal inset, applied PER ITEM instead of once on the list. It used to sit on
    // the LazyColumn's own modifier, and that broke the moment the empty state moved INSIDE the
    // list: `EmptyState` applies `ScreenPaddingHorizontal` itself, so it was inset twice (32dp, plus
    // the 16dp it puts on its own description = 48dp of margin around a two-sentence paragraph) —
    // the "avoid double padding" rule in `.claude/rules/ui-card-patterns.md`, and a visible
    // difference from the very same empty state on every other screen. Per item, each element
    // carries exactly one inset and the empty state carries only its own.
    val rowInset = Modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal)

    // Hoisted out of the LazyColumn purely so the regroup below can ask whether the user's finger
    // is currently on the list. Same scope the implicit one lived in, so scroll position survives
    // exactly as before.
    val listState = rememberLazyListState()

    // 🔴 The clock the SECTIONS are built from, which is not the same thing as the clock the
    // ViewModel ticks. The tick and its application are deliberately separate steps: a minute
    // boundary crossed mid-scroll would re-file a row and slide it out from under the finger that
    // was aiming at it, and the same is true while a sheet describes one of these rows. So the new
    // value waits for the user to be idle.
    //
    // The due chips read this same settled value rather than the live one on purpose: a chip that
    // says "Overdue" under a heading that still says "Today" is a worse defect than a chip that is
    // up to one scroll-gesture stale.
    val settledNow = rememberSettledNow(nowMillis) { listState.isScrollInProgress || sheetOpen }

    val sections = remember(page.tasks, groupByDate, settledNow) {
        sectionInboxTasks(tasks = page.tasks, groupByDate = groupByDate, nowMillis = settledNow)
    }
    val arrivals = rememberSectionArrivals(sections)

    // BoxWithConstraints for ONE number: the page height the empty state is sized against. It has to
    // come from the page rather than from the list because `fillParentMaxHeight` — the only
    // list-side option — can set a FIXED height and nothing else, which is the defect below.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val emptyStateMinHeight = maxHeight * InboxEmptyStateHeightFraction

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // wrapContentWidth sits between the fill and the cap, and it is what makes the cap
                // bind at all: fillMaxSize pins minWidth == maxWidth to the pane, and a widthIn(max)
                // coerced into a fixed range does nothing — on a 1280dp window the rows spanned the
                // whole pane, checkbox at one edge and star at the other. This relaxes the minimum
                // back to 0 so adaptiveContentWidth can actually cut the width, then centres the
                // capped column in the pane it still holds.
                .wrapContentWidth(Alignment.CenterHorizontally)
                .adaptiveContentWidth(),
            // Cards: 8dp, matching the checklist detail list — the cards are the same object there
            // and the two screens must not use two different rhythms for it. Compact: zero, because
            // the rows are separated by a rule instead, and a gap on top of a divider reads as a
            // broken card list.
            verticalArrangement = if (compact) {
                Arrangement.spacedBy(0.dp)
            } else {
                Arrangement.spacedBy(AppDimens.SpacingSm)
            },
            // Top padding so the first card does not touch the dots row; the bottom reserve is the
            // band the shell's raised AI button overhangs, passed in by the host.
            contentPadding = PaddingValues(
                top = AppDimens.SpacingSm,
                bottom = AppDimens.SpacingXl + contentBottomPadding,
            ),
        ) {
            // The empty state is an ITEM of the list rather than a replacement for it, and that is
            // the whole point: it used to be rendered INSTEAD of the LazyColumn, so an add-task row
            // that lives inside the list would have been composed nowhere on an empty page — and a
            // brand-new user, whose Inbox is empty by definition, would have had no visible way to
            // add anything at all.
            //
            // Height-capped instead of filling the viewport: at 100% the row below would sit exactly
            // one screen down, i.e. off-screen, which is the same defect one scroll further out. At
            // 60% the illustration still reads as a centred empty state and the row lands just under
            // it.
            //
            // A MINIMUM, not a fixed height. `EmptyState` is an 88dp icon plus two text blocks, so
            // on a short viewport at fontScale 1.3 in the longest locale it outgrows 60% — and a
            // fixed box does not clip the overflow, it lets it draw straight over the add-task row
            // underneath. Lazy items are measured with an unbounded height, so `fillMaxSize()`
            // inside `EmptyState` resolves to wrap-content here and this Box takes whichever is
            // taller: the 60% band or the content.
            if (page.tasks.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier.heightIn(min = emptyStateMinHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (page.isInbox) {
                            EmptyState(
                                icon = Icons.Outlined.Inbox,
                                title = stringResource(Res.string.inbox_empty_title),
                                description = stringResource(Res.string.inbox_empty_description),
                            )
                        } else {
                            EmptyState(
                                icon = Icons.Outlined.ChecklistRtl,
                                title = page.title,
                                description = stringResource(
                                    Res.string.inbox_project_empty_description
                                ),
                            )
                        }
                    }
                }
            }

            // The plan nudge belongs to the ANYTIME run, so both are emitted section by section
            // rather than as one flat `itemsIndexed`. With grouping off there is exactly one
            // unheaded section and this loop degenerates to what it replaced.
            sections.forEach { section ->
                if (section.header != null) {
                    // NOT `stickyHeader`: a pinned heading eats the list's top padding and its
                    // arrangement spacing, and this list is short enough per group that a heading
                    // scrolling away is not a loss of context.
                    item(key = sectionHeaderKey(section.header)) {
                        InboxSectionHeader(kind = section.header, modifier = rowInset)
                    }
                }

                itemsIndexed(section.tasks, key = { _, task -> task.fillItemId }) { index, task ->
                    // Row and rule share ONE inset wrapper: the divider has to line up with the card
                    // edges, and two separate paddings drift apart the first time one is edited.
                    Column(
                        // Placement only. The row's job here is to TRAVEL to its new section when
                        // its date passes; fade specs are left at their defaults so adding and
                        // removing a task looks exactly as it did.
                        modifier = rowInset.animateItem(placementSpec = AppMotion.spatialDefaultAs()),
                    ) {
                        SectionArrivalHighlight(
                            active = task.fillItemId in arrivals,
                            compact = compact,
                        ) {
                            TaskRow(
                                item = task.item,
                                compact = compact,
                                onCheckedChange = { checked ->
                                    onIntent(InboxIntent.OnTaskCheckedChanged(task.fillItemId, checked))
                                },
                                onDetailsClick = {
                                    onIntent(InboxIntent.OnTaskDetailsClick(task.fillItemId))
                                },
                                nowMillis = settledNow,
                            )
                        }
                        // Between rows only — a trailing rule under the last item would read as "the
                        // list continues below" at the end of a short list. Per SECTION now: the
                        // heading below is the separator, so a rule above it would be a second one.
                        if (compact && index < section.tasks.lastIndex) {
                            HorizontalDivider(
                                thickness = AppDimens.DividerThickness,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }

                // ⛔ Never at the top. At the head of the list this copy reads as a heading, and a
                // heading phrased as an invitation to catch up reads as an accusation. At the tail
                // of the undated run it is what it is: an offer, sitting next to the things it
                // would help with.
                if (section.header.isPlanNudgeAnchor() &&
                    onPlanDayClick != null &&
                    !planNudgeSuppressed &&
                    page.undatedOpenTaskCount() >= PlanNudgeMinUndatedTasks
                ) {
                    item(key = "inbox_plan_nudge") {
                        PlanNudgeRow(
                            onClick = onPlanDayClick,
                            onDismiss = { onIntent(InboxIntent.OnPlanNudgeDismissed) },
                            modifier = rowInset.padding(top = AppDimens.SpacingSm),
                        )
                    }
                }
            }

            // LAST element of the list, and it scrolls with it — the Todoist anatomy the owner
            // picked after seeing a pinned variant rendered too.
            //
            // The extra top gap is what keeps it from reading as one more task: in the compact
            // layout the list's arrangement spacing is zero and the rows are separated by rules, so
            // without it the row would butt straight against the last task with no divider between
            // them. No rule is drawn above it on purpose — a divider would enrol it into the list it
            // must stand apart from.
            if (showAddTaskRow) {
                item(key = "inline_add_task") {
                    AddTaskRow(
                        onClick = { onIntent(InboxIntent.OnAddTaskRowClick) },
                        // On an EMPTY page this row is the only action on screen and it sits under a
                        // full empty state, so it grows instead of staying the list-sized line it is
                        // among tasks. Keyed on the page's own emptiness rather than on a flag from
                        // the host: the host cannot see which page the pager settled on, and the
                        // Inbox and a project page can differ on exactly this.
                        prominent = page.tasks.isEmpty(),
                        modifier = rowInset.padding(top = AppDimens.SpacingSm),
                    )
                }
            }

            // Second door into Analyze, shown only on the INBOX page and only while it is SPARSE.
            //
            // Inbox-only because this composable also renders every PROJECT page of the pager, and
            // the row cannot honestly serve one from the other. Its two surfaces (`INBOX_EMPTY` /
            // `INBOX_SPARSE`) are defined as an Inbox page, so firing them from a project would fuse
            // "the Inbox was empty" with "some project was empty" into one unsplittable series — and
            // the tap itself lands wrong: `navigateToAnalyzeWithInput` carries no `checklistId`, so a
            // user who reached for it from inside a project gets their checklist created elsewhere.
            // The add-task row directly above already draws this line (`SOURCE_INBOX` vs
            // `SOURCE_PROJECT`), and this row must not contradict it.
            //
            // Nothing is lost on a project page: the capture dock hosts the SAME row for the whole
            // tab (see this screen's `bottomBar`), one "+" tap away, and reports its own surface.
            //
            // Sparse-only because this is the moment it helps: an empty or nearly-empty list is a
            // user with nothing to act on, and "hand me a photo instead" is a real answer to that.
            // Once the list fills up the same row would be permanent furniture between the user and
            // their tasks, and the dock's copy is always one tap away regardless.
            //
            // Placed AFTER the add-task row rather than between it and the tasks: that row's own
            // contract is to be the LAST element of the list (its top gap is what stops it reading
            // as one more task), and splitting it from the list it terminates would undo a decision
            // the owner already reviewed on device. The AI block therefore sits below it as a
            // clearly separate offer — still inside the first viewport, which is what the sparse
            // gate guarantees.
            if (page.isInbox && page.tasks.size <= SparseInboxTaskLimit) {
                item(key = "ai_source_row") {
                    // One Column, not two siblings: a LazyColumn item is a single slot, and two
                    // children in it are stacked at the same origin rather than laid out in order.
                    Column(modifier = Modifier.padding(top = AppDimens.SpacingLg)) {
                    // The heading carries the promise; the pills carry the doors. Without it four
                    // bare pills read as "attach something to a task", which is a different (and
                    // already-served) offer — the words are what make this the listing's own
                    // "any content -> checklist".
                    Text(
                        text = stringResource(Res.string.inbox_ai_entry_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = rowInset.padding(bottom = AppDimens.SpacingSm),
                    )
                    SourceRow(
                        onSelect = { kind ->
                            onIntent(
                                InboxIntent.OnAiSourceTapped(
                                    kind = kind,
                                    // Two values, not one: "the list was empty" and "the list was
                                    // nearly empty" are different user states, and collapsing them
                                    // would hide which of the two actually converts.
                                    source = if (page.tasks.isEmpty()) {
                                        AiEntrySource.INBOX_EMPTY
                                    } else {
                                        AiEntrySource.INBOX_SPARSE
                                    },
                                )
                            )
                        },
                        modifier = rowInset,
                    )
                    }
                }
            }
        }
    }
}

/**
 * Up to this many tasks the page still counts as sparse and shows the AI source row.
 *
 * Two rather than zero: a list holding one or two captured lines is still a user who has not got
 * going, and the empty state alone would hide the affordance the moment they typed anything.
 */
private const val SparseInboxTaskLimit = 2

/**
 * Holds the section clock steady while the user is working.
 *
 * The ViewModel ticks once a minute; this is the *other* half of that mechanism, and the two are
 * deliberately separate. Applying a new minute the instant it arrives is what makes a row jump to
 * another section mid-scroll — the finger is aiming at a row that is no longer there. So a new value
 * is parked until [busy] reports the user is neither scrolling nor reading a sheet, and only then
 * becomes the clock the list is built from.
 *
 * [busy] is polled through `snapshotFlow` rather than read in composition: `isScrollInProgress`
 * flips twice per gesture, and reading it in the body would invalidate the whole page for it.
 * `rememberUpdatedState` is what keeps the effect looking at the CURRENT predicate — without it the
 * effect would keep evaluating the closure captured when the tick arrived, whose `sheetOpen` never
 * changes again, and the regroup would never be applied at all.
 */
@Composable
private fun rememberSettledNow(nowMillis: Long, busy: () -> Boolean): Long {
    var settled by remember { mutableStateOf(nowMillis) }
    val currentBusy by rememberUpdatedState(busy)

    LaunchedEffect(nowMillis) {
        snapshotFlow { currentBusy() }.first { !it }
        settled = nowMillis
    }
    return settled
}

/**
 * Task ids that changed section since the previous grouping — the rows that just travelled.
 *
 * Returned as a set the list reads per row, rather than as a flag inside the row, because "did this
 * move" is a question about the list's previous shape and only the list knows it.
 *
 * The set is CLEARED on every regrouping, not only on the ones that produce arrivals. A lazy item is
 * disposed as it scrolls out of view and re-created on the way back, so a set left populated would
 * replay the highlight minutes later on a row that had not moved at all.
 */
@Composable
private fun rememberSectionArrivals(sections: List<InboxTaskSection>): Set<String> {
    val assignment = remember(sections) {
        sections.flatMap { section -> section.tasks.map { it.fillItemId to section.header } }.toMap()
    }
    var arrivals by remember { mutableStateOf(emptySet<String>()) }
    val previous = remember { mutableStateOf<Map<String, InboxSectionKind?>>(emptyMap()) }

    LaunchedEffect(assignment) {
        val before = previous.value
        previous.value = assignment
        // The first pass has nothing to compare against: every row would count as an arrival and
        // the whole list would flash on open.
        arrivals = if (before.isEmpty()) {
            emptySet()
        } else {
            assignment.filter { (id, kind) -> before.containsKey(id) && before[id] != kind }.keys
        }
        if (arrivals.isEmpty()) return@LaunchedEffect
        delay(SectionArrivalHoldMillis)
        arrivals = emptySet()
    }
    return arrivals
}

/**
 * One pass of colour over a row that has just moved to another section.
 *
 * The same language the freshly-captured row already speaks — a tint that fades out once — rather
 * than a second vocabulary for "look here". It is drawn OVER the row and clipped to the row's own
 * shape: painting it behind would put it under the card's opaque fill and leave only a ring.
 *
 * The alpha is read inside `drawWithContent`, so the fade invalidates the draw phase alone; read in
 * composition it would recompose the row on every frame of the fade.
 */
@Composable
private fun SectionArrivalHighlight(
    active: Boolean,
    compact: Boolean,
    content: @Composable () -> Unit,
) {
    val highlight = GistiSchedule.overdueContainer
    // In the compact layout there is no card, so there are no corners to follow.
    val shape = if (compact) RectangleShape else MaterialTheme.shapes.medium
    // Keyed on `active` so the pass runs exactly ONCE per arrival; re-creating it at 0f on the way
    // back out costs nothing and draws nothing.
    val fade = remember(active) { Animatable(if (active) SectionArrivalAlpha else 0f) }
    LaunchedEffect(active) { if (active) fade.animateTo(0f, AppMotion.effectsSlow) }

    Box(
        // propagateMinConstraints so this wrapper is invisible to layout: the row must measure
        // exactly as it does without it.
        propagateMinConstraints = true,
        modifier = Modifier.drawWithContent {
            drawContent()
            val alpha = fade.value
            if (alpha > 0f) {
                drawOutline(
                    outline = shape.createOutline(size, layoutDirection, this),
                    color = highlight,
                    alpha = alpha,
                )
            }
        },
    ) {
        content()
    }
}

/**
 * A group heading — "Overdue", "Today".
 *
 * ⛔ **No count.** The open-task total already sits in the toolbar subtitle, and "Overdue 7" is a
 * debt counter under another name: it grows faster than a person clears it and turns the list into a
 * standing reproach, which works directly against the number of tasks they go on to capture.
 *
 * Not `stickyHeader` either: pinning eats the list's own top padding and arrangement spacing, and a
 * heading that scrolls away costs nothing here because the groups are short.
 */
@Composable
private fun InboxSectionHeader(kind: InboxSectionKind, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = AppDensity.SectionHeaderTop,
                bottom = AppDensity.SectionHeaderBottom,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(kind.labelResource()),
            // Carries its own colour; ⛔ never uppercased — Devanagari has no case, so the transform
            // is a no-op on hi and the heading silently loses its emphasis there.
            style = AppTextStyles.sectionHeader,
        )
    }
}

private fun InboxSectionKind.labelResource(): StringResource = when (this) {
    InboxSectionKind.OVERDUE -> Res.string.inbox_section_overdue
    InboxSectionKind.TODAY -> Res.string.inbox_section_today
    InboxSectionKind.UPCOMING -> Res.string.inbox_section_upcoming
    InboxSectionKind.ANYTIME -> Res.string.inbox_section_anytime
}

/** Lazy-list key for a heading. Prefixed so it can never collide with a fill-item id. */
private fun sectionHeaderKey(kind: InboxSectionKind): String = "inbox_section_${kind.name}"

/**
 * Whether the plan nudge belongs at the end of this section.
 *
 * `null` = the unheaded single run, whose tail IS the list's tail. Otherwise only after the undated
 * group, which is both last in the order and the one the invitation is about.
 */
private fun InboxSectionKind?.isPlanNudgeAnchor(): Boolean =
    this == null || this == InboxSectionKind.ANYTIME

/**
 * How many undated tasks are still open on this page.
 *
 * Open ones only. A finished task is not something left to plan, and counting it would make the
 * invitation appear and disappear as the "Completed tasks" switch is flipped — which has nothing to
 * do with how much there is to schedule.
 */
private fun InboxPage.undatedOpenTaskCount(): Int = tasks.count { !it.checked && it.isUndated() }

/**
 * The plan-your-day invitation, swipeable away.
 *
 * Swipe is a **trigger, not a dismiss**: `snapTo(Settled)` puts the row straight back and the
 * ViewModel is what removes it, after persisting the snooze. Letting the box dismiss it locally
 * would hide the row on a device where the write failed — a nudge that is gone until the next launch
 * and then inexplicably back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanNudgeRow(
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            onDismiss()
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        // Nothing behind it on purpose. A coloured slab with an icon is the vocabulary of deleting
        // something of the user's; this only puts an offer away for a day.
        backgroundContent = {},
    ) {
        AppPlanNudge(onClick = onClick, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * The loading state: the SHAPE of the list that is arriving.
 *
 * Replaced a centred spinner. A spinner says "something is happening somewhere"; five card-shaped
 * placeholders say "your tasks are coming, and they will be here" — and because they are the same
 * cards at the same height, the layout does not jump when the real rows land. The widths are uneven
 * by design: five identical bars read as a graphic, five ragged ones read as text.
 */
@Composable
private fun InboxLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Same width treatment as the real list, so a wide window does not show a full-bleed
            // skeleton that snaps to a narrower column the moment the data lands.
            .wrapContentWidth(Alignment.CenterHorizontally)
            .adaptiveContentWidth()
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
            .padding(top = AppDimens.SpacingSm),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
    ) {
        InboxSkeletonWidthFractions.forEach { fraction ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = AppCardDefaults.colors(),
                border = AppCardDefaults.border(),
                elevation = AppCardDefaults.flatElevation(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppDensity.RowMinHeightComfortable)
                        .padding(
                            horizontal = AppDensity.RowPaddingHorizontal,
                            vertical = AppDensity.RowPaddingVertical,
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    AppSkeletonLine(widthFraction = fraction)
                }
            }
        }
    }
}

/**
 * The load failed, and says so.
 *
 * Before this branch existed the same failures rendered as [InboxLoadingSkeleton]'s predecessor —
 * a spinner that never resolved. A four-second snackbar is not a substitute: it is gone long before
 * the user works out that nothing is coming.
 *
 * @param message the reason, already localized by the ViewModel.
 * @param canRetry false renders the reason alone rather than a button that cannot help.
 */
@Composable
private fun InboxErrorState(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppDimens.ScreenPaddingHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Column(
                modifier = Modifier.padding(AppDimens.SpacingLg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    // Decorative: the sentence underneath is the message, and announcing "error"
                    // before it would read it out twice.
                    contentDescription = null,
                    modifier = Modifier.size(InboxErrorIconSize),
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                if (canRetry) {
                    AppButtonSecondary(
                        text = stringResource(Res.string.inbox_error_retry),
                        onClick = onRetry,
                        // The block's own palette, not the app accent. Left at the default this
                        // button drew a primary-blue outline and a neutral-grey label on the pink
                        // errorContainer — three palettes in one control, and it read as a
                        // foreign object dropped into the message rather than its action.
                        //
                        // The action stays INSIDE the block deliberately: lifted out onto the page
                        // it becomes an orphan control on an otherwise empty screen, with nothing
                        // tying "something went wrong" to "try again" but proximity.
                        accentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
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
 * Share of the pager viewport the empty-state illustration gets while it lives INSIDE the list.
 *
 * Not 1f: the add-task row is composed right after it, and a full-height empty state would push that
 * row exactly one screen down — invisible on open, which is the very defect moving the empty state
 * into the list was meant to fix. 0.6 leaves the illustration comfortably centred in the upper part
 * of the page and the row just below it, on every window this tab renders on.
 */
private const val InboxEmptyStateHeightFraction = 0.6f

/**
 * How many OPEN undated tasks a page needs before the plan-your-day invitation appears.
 *
 * Below three there is nothing to triage — a review session of one or two items is more ceremony
 * than the tasks are worth, and offering it there teaches the user the invitation is noise.
 */
private const val PlanNudgeMinUndatedTasks = 3

/** Peak opacity of the one-pass tint on a row that has just changed section. */
private const val SectionArrivalAlpha = 0.35f

/**
 * How long the arrival set stays populated.
 *
 * Comfortably longer than the fade itself ([AppMotion.effectsSlow] settles in ~400 ms), because this
 * is not the animation's clock — the `Animatable` is. It is the window after which a row scrolled
 * back into view must no longer replay the pass.
 */
private const val SectionArrivalHoldMillis = 700L

/**
 * Widths of the five loading placeholders, as fractions of the row.
 *
 * Ragged on purpose, and in this order: an ascending or repeating run reads as a chart, while an
 * uneven one reads as sentences of different lengths — which is what is actually arriving.
 */
private val InboxSkeletonWidthFractions = listOf(0.72f, 0.55f, 0.84f, 0.44f, 0.66f)

/** Error glyph. Small: the sentence is the message, the icon only labels its genre. */
private val InboxErrorIconSize = 20.dp
