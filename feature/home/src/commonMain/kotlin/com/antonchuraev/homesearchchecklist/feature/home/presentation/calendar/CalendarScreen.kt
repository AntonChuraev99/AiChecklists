package com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.calendar_empty_cta
import aichecklists.core.designsystem.generated.resources.calendar_nav_label
import aichecklists.core.designsystem.generated.resources.today_title
import aichecklists.core.designsystem.generated.resources.calendar_empty_description
import aichecklists.core.designsystem.generated.resources.calendar_empty_title
import aichecklists.core.designsystem.generated.resources.calendar_error_retry
import aichecklists.core.designsystem.generated.resources.calendar_error_title
import aichecklists.core.designsystem.generated.resources.calendar_grid_week_label
import aichecklists.core.designsystem.generated.resources.calendar_next_week
import aichecklists.core.designsystem.generated.resources.calendar_prev_week
import aichecklists.core.designsystem.generated.resources.calendar_title
import aichecklists.core.designsystem.generated.resources.today_open_menu
import aichecklists.core.designsystem.generated.resources.today_quick_add_placeholder
import androidx.compose.material3.TopAppBarDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.components.PlatformBackHandler
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.desingsystem.components.QuickCaptureDock
import com.antonchuraev.homesearchchecklist.desingsystem.components.SourceRow
import com.antonchuraev.homesearchchecklist.desingsystem.components.captureDockScrimColor
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiItemCreateAction
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.TaskCreateChipsRow
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.TaskDraft
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components.CreditsChipSource
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components.CreditsToolbarAction
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.home.presentation.components.AddTaskRow
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayBody
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayScreenState
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource

// ---------------------------------------------------------------------------
// CalendarScreen — Agenda + Week-grid view
//
// Structure:
//   CalendarScreen (scaffold shell + state routing)
//   └── CalendarContent (top banner + LazyColumn)
//       ├── PremiumTeaserChip  — free, chip not dismissed
//       ├── WeekGridContent    — premium
//       └── AgendaItems        — DateHeader + ReminderRow via LazyColumn
//   CalendarEmptyState — no reminders in window
//   CalendarErrorState  — repository error + retry
//   CalendarLoadingContent — initial load spinner
//
// Design decisions:
//  - Flat LazyColumn with stickyHeader for DateHeaders (matches TodayScreen).
//  - ReminderRow: flat ListItem-style, no AppCard per row (same rationale as Today).
//  - WeekGridContent: 7 equal weight(1f) cells, scroll-to-DateHeader on tap via
//    LazyListState.animateScrollToItem (UI-only side-effect, no ViewModel intent).
//  - PremiumTeaserChip: AppCard container + AppButtonText CTA + Close IconButton.
//  - Past-due DateHeader uses error color + Alarm icon (soft warning, not alarming).
//  - Accessibility: each ReminderRow has merged semantics (role=Button + full desc).
// ---------------------------------------------------------------------------

/**
 * Stateless Calendar screen composable.
 *
 * Receives [state] and [onIntent] from [CalendarRoute] — no ViewModel access here.
 * Renders [AppScaffold] with a hamburger icon that opens [drawerState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    todayState: TodayScreenState,
    calendarState: CalendarState,
    drawerState: DrawerState?,
    onTodayReminderClick: (checklistId: Long, fillId: Long?) -> Unit,
    onTodayCreateChecklistClick: () -> Unit,
    onTodayRetry: () -> Unit,
    onCalendarIntent: (CalendarIntent) -> Unit,
    /**
     * Extra bottom inset the HOST reserves below this screen — in the v2 shell, the part of the
     * raised AI button that overhangs the bar and is drawn over this screen's content.
     *
     * Applied to the BOTTOMMOST element and to that one only: the pinned add-task row where it is
     * drawn, the pager bodies' `contentPadding` where it is not. Giving it to both reserves the
     * space twice and leaves a dead strip in the middle. 0.dp (the default, and what the control arm
     * passes) keeps the previous padding exactly.
     */
    contentBottomPadding: Dp = 0.dp,
    /**
     * Quick-capture, mirroring the Inbox tab's dock. Defaults make every existing caller (control
     * arm, previews, tests) render exactly as before: no dock, no snackbar host of its own.
     *
     * The dock is hoisted to the HOST for the same reason the Inbox one is — the v2 shell's FABs are
     * drawn above this screen and must hide while it is up.
     */
    draft: TaskDraft = TaskDraft(),
    onCreateChipAction: (GistiItemCreateAction) -> Unit = {},
    captureDockOpen: Boolean = false,
    /**
     * Whether this HOST offers a capture affordance at all — NOT whether the dock is currently up
     * ([captureDockOpen]), which is false most of the time on a host that can still open it.
     *
     * TWO readers, and it is checked FIRST in both — before any window-size, window-inset or layout
     * state is touched — so the control arm's composition is byte-for-byte what it was:
     *  1. it gates the [AddTaskRow] below the pager, the entry point that replaced the v2 shell's
     *     floating "+" (on Compact this tab's ONLY way into the capture dock, so dropping the FAB
     *     without it would silently delete the feature). See `captureRowVisible` in the body: the
     *     row is drawn on Compact ONLY, because the rail and the permanent drawer keep a "+" of
     *     their own and two doors to one action on one screen is the defect, not the feature.
     *  2. the Today body's empty-state copy reads it to choose between naming the capture input and
     *     staying neutral. NOT narrowed to Compact: at rail width the "+" is still there, so
     *     "add a task" is still a true promise — only its shape differs.
     *
     * Default false = the classic layout: no row, no dock, the neutral wording — so the control arm
     * needs no call-site change.
     */
    captureEnabled: Boolean = false,
    onCaptureDockDismiss: () -> Unit = {},
    /**
     * The inline "add task" row was tapped — the HOST must raise [captureDockOpen].
     *
     * Defaulted here (unlike on `CalendarRoute`) only so previews and screenshot tests of this
     * stateless composable keep compiling; every production path goes through the route, which
     * requires it.
     *
     * ⚠️ Must NOT emit `nav_create_fab_tapped` — `TodayViewModel` already does, with
     * `source = "inline_row"`.
     */
    onAddTaskRowClick: () -> Unit = {},
    onQuickAddTextChange: (String) -> Unit = {},
    onQuickAddSubmit: () -> Unit = {},
    /**
     * One of the capture dock's AI source pills was tapped (Photo / PDF / Web Link / Voice).
     *
     * Defaulted to a no-op like its dock siblings above so the standalone Today route — which has
     * no dock — stays unchanged. The v2 Calendar tab wires it in `CalendarRoute`.
     */
    onAiSourceTapped: (AnalyzeInputKind) -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    /**
     * Analytics `source` for the AI-credits chip in the top bar; **null draws no chip**.
     *
     * The arm gate matters MORE here than on the other three v2 tabs, and it is the whole reason this
     * is a nullable source rather than an unconditional chip: `AppNavRoute.Calendar` is also what the
     * v1 DRAWER pushes, so this is the one shared screen of the four. An unconditional chip would
     * plant a brand-new paywall entry point inside the A/B CONTROL arm and confound the experiment
     * the v2 shell is being judged by. Same reason [captureEnabled] is threaded rather than derived:
     * the screen cannot see which arm mounted it.
     *
     * v2 hosts pass [CreditsChipSource.V2_CALENDAR]; the control arm passes nothing.
     */
    creditsSource: String? = null,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Capture is offered on BOTH pages of this tab, deliberately.
    //
    // It used to be gated on the Today page, and that turned the shell's "+" into a DEAD BUTTON on
    // the week grid: the shell computes FAB visibility from the OUTER tab, so the button rendered
    // there, tapping it raised this flag — which hides both FABs — and no dock ever appeared. BACK
    // was not intercepted either (it is gated on the same value), so the only way out of that state
    // was leaving the tab. Auto-closing the dock when the page leaves 0 does not fix it: tapping "+"
    // on the week page would still do nothing visible, which is the same dead button one frame
    // shorter.
    //
    // The gate had no product basis to preserve. A capture ALWAYS lands in the system Inbox (see
    // TodayViewModel.captureTask) — never on the page under it, not even on Today — so the visible
    // page carries no ambiguity to resolve, and the snackbar names where the task went either way.
    val captureVisible = captureDockOpen

    // Whether the pinned add-task ROW is drawn this frame. Three gates, in this order and
    // deliberately:
    //
    //  1. [captureEnabled] — the ARM gate, evaluated FIRST so the control arm never reaches the rest
    //     of the expression. In particular it never reads the window size, so its composition (and
    //     its invalidation scope) is byte-for-byte what it was.
    //  2. the dock — it IS the expanded form of this row, and leaving both on screen stacks two
    //     inputs on one edge.
    //  3. window size — COMPACT ONLY, and this is the part that changed after review. The v2 shell
    //     drops its floating "+" on Compact alone; at rail and permanent-drawer width it still
    //     renders one, and `showCreateFab` covers this tab as well as the Inbox
    //     (`v2SelectedTab == Inbox || v2SelectedTab == Calendar`). Drawing the row there too gave
    //     ONE action two doors on one screen and made the shell's own rationale ("without this
    //     button there is no way to add a task at rail width at all") false. Gating here rather than
    //     widening the host's flag keeps that comment true and costs no capture path: every window
    //     size still has exactly one way into the dock.
    val captureRowVisible = if (captureEnabled && !captureVisible) {
        rememberAppWindowSizeClass() == AppWindowSizeClass.Compact
    } else {
        false
    }

    // The host's reserve goes to whatever is actually the BOTTOMMOST element, never to two things at
    // once — that is the whole contract of [contentBottomPadding].
    //
    // With the row pinned under the pager, the row is that element and the reserve belongs to IT
    // (see the row's own modifier below): the v2 shell's raised AI button overhangs the bar's top
    // edge by 22dp and is drawn OVER this screen's content box, so a row flush against the bar was
    // covered by the circle for its bottom third — visually and, because the button's hit zone is a
    // sibling drawn after the content, by touch as well: a tap on the middle of the row opened the
    // CHAT instead of the capture dock.
    //
    // The pager therefore gets 0: it is no longer the bottommost element (the row bounds it from
    // below), and leaving the reserve on it left a dead ~30dp strip between the last reminder and
    // the row. While the dock is up, the Scaffold has already shortened the content by the dock's
    // height, so the reserve would strand the list above a band the height of chrome that is not on
    // screen. Both of those collapse to the same rule: whoever is last carries it.
    val bodyBottomPadding = if (captureVisible || captureRowVisible) 0.dp else contentBottomPadding

    // ── Capture-dock scrim: TWO tiled scrims, one flag (same anatomy as the Inbox tab) ───────────
    // The dock is the scaffold's `bottomBar`, so a scrim scoped to the CONTENT already stops exactly
    // where the dock begins — at any keyboard height, with nothing to measure. [contentTopPx] is the
    // content slot's y in the root, i.e. the height of the chrome above it, and the top scrim uses
    // exactly that so the two tile without a seam.
    var contentTopPx by remember { mutableStateOf(0f) }
    val captureScrimColor = captureDockScrimColor()

    // BACK closes the dock before anything else — the user is escaping the keyboard, not the screen.
    PlatformBackHandler(enabled = captureVisible) { onCaptureDockDismiss() }

    // Root box so the scrim below can be a SIBLING of the whole scaffold: as a child of the content
    // slot it dimmed the pager alone and left the toolbar and the tab row bright.
    Box(modifier = Modifier.fillMaxSize()) {
        AppScaffold(
            title = stringResource(Res.string.calendar_title),
            navigationIcon = if (drawerState != null) {
                {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = stringResource(Res.string.today_open_menu),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else null,
            // This tab had NO actions slot at all before — the v2 shell left it without any paywall
            // entry point, like its three siblings. Flat emission, not a Row: `AppScaffold` forwards
            // the slot into Material's `actions: RowScope.() -> Unit`.
            actions = {
                if (creditsSource != null) {
                    CreditsToolbarAction(source = creditsSource)
                }
            },
            scrollBehavior = scrollBehavior,
            snackbarHost = {
                if (snackbarHostState != null) SnackbarHost(hostState = snackbarHostState)
            },
            // bottomBar, not inside the content: this slot is the only one AppScaffold lifts above the
            // keyboard (it applies ime ∪ navigationBars). Hosting the dock in the content forced a manual
            // imePadding() on the Inbox tab and slid the input off-screen — same trap, same fix.
            bottomBar = {
                if (captureVisible) {
                    QuickCaptureDock(
                        text = draft.text,
                        onTextChange = onQuickAddTextChange,
                        onAdd = onQuickAddSubmit,
                        placeholder = stringResource(Res.string.today_quick_add_placeholder),
                        // Behind the dock, not over it: the only pixels this reaches are the two
                        // corners `SheetTop` clips away, which otherwise show the raw page against
                        // the dimmed page beside them (measured ΔL* +41 in light). The content scrim
                        // above cannot cover them — it stops at the content slot's edge on purpose.
                        // Identical to the Inbox tab's, deliberately: the two docks are one surface
                        // and must sit in the same depth of dim.
                        modifier = Modifier.background(captureScrimColor),
                        // This tab draws the day's reminders, so its draft arrives with one chip
                        // already selected ("Tonight", or "In 1 hour" once the evening has
                        // started) — that chip is what keeps a task captured here visible on the
                        // screen that captured it.
                        //
                        // Pick-time and Repeat stay off: their picker and repeat sheet live on the
                        // detail screen, and a chip that swallows its tap is worse than an absent
                        // one.
                        aboveInput = {
                            TaskCreateChipsRow(
                                draft = draft,
                                onAction = onCreateChipAction,
                                showPickTime = false,
                                showRepeat = false,
                            )
                        },
                        // Same four doors as the Inbox tab, from the same shared component — the
                        // two tabs must not drift into two different answers to "what can I hand
                        // the AI". Only the reported source differs.
                        belowInput = { SourceRow(onSelect = onAiSourceTapped) },
                    )
                }
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Seam for the top scrim: this column's y in the root is the height of the chrome
                    // above it (status bar + app bar), which the top scrim below uses as its height.
                    .onGloballyPositioned { contentTopPx = it.positionInRoot().y }
                    // CONTENT scrim, as a draw layer rather than an overlay node — the tab row and
                    // the pager beneath it must stay interactive, and an overlay is exactly what the
                    // pager's dismiss comment below rules out (hit-testing stops at the topmost
                    // sibling). Drawing over the column dims the tabs AND the pages, ends where the
                    // content slot ends — so the dock in `bottomBar` stays bright at any keyboard
                    // height — and changes neither layout nor hit-testing.
                    .then(
                        if (captureVisible) {
                            Modifier.drawWithContent {
                                drawContent()
                                drawRect(captureScrimColor)
                            }
                        } else {
                            Modifier
                        }
                    ),
            ) {
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text(stringResource(Res.string.today_title)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text(stringResource(Res.string.calendar_nav_label)) },
                    )
                }
                HorizontalPager(
                    state = pagerState,
                    // Tap-outside-to-dismiss for the capture dock. Without it BACK is the ONLY way out
                    // — and on wasmJs `PlatformBackHandler` is an empty actual, so web users were left
                    // with a raised keyboard, no FABs and no exit but a tab switch.
                    //
                    // It rides the PAGER'S OWN modifier chain rather than a full-bleed overlay Box, and
                    // both halves of that matter:
                    //  - Scoped to the pager, the tab row above stays live. An overlay sized to the
                    //    whole Column covered the tabs too, so while the dock was up the first tap on
                    //    Today/Calendar only dismissed and switching pages cost two taps.
                    //  - In the chain rather than over it, the pages keep scrolling while capturing.
                    //    An overlay cannot be "tapped through": hit-testing stops at the topmost
                    //    sibling that hits, and a sibling below is reached only if the one above opts
                    //    into `sharePointerInputWithSiblings()`, which `pointerInput` does not
                    //    (PointerInputModifierNode defaults it to false). Consumption is beside the
                    //    point — `detectTapGestures` consumes the down as well.
                    //    As an ANCESTOR it is dispatched after its children on the Main pass, so a
                    //    vertical scroll or a page swipe consumes the movement first and cancels the
                    //    tap, and a row's own `clickable` consumes the down, so tapping a reminder
                    //    opens it instead of being spent on dismissing the dock.
                    // weight(1f) rather than fillMaxSize(): the add-task row below is a sibling in this
                    // Column and a filling pager would push it off the bottom. With no row composed
                    // (control arm) a single weighted child occupies exactly the space fillMaxSize gave
                    // it, so that arm's layout is unchanged.
                    //
                    // The DIMMING is not here: it is a root-level scrim drawn over the whole screen (see
                    // the end of this composable). Dimming just the pager left the toolbar and the tab
                    // row bright, which the owner called out on both tabs (2026-08-13).
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .then(
                            if (captureVisible) {
                                Modifier.pointerInput(Unit) {
                                    detectTapGestures(onTap = { onCaptureDockDismiss() })
                                }
                            } else {
                                Modifier
                            }
                        ),
                ) { page ->
                    when (page) {
                        0 -> TodayBody(
                            state = todayState,
                            onReminderClick = onTodayReminderClick,
                            onCreateChecklistClick = onTodayCreateChecklistClick,
                            onRetry = onTodayRetry,
                            contentBottomPadding = bodyBottomPadding,
                            canCapture = captureEnabled,
                        )
                        1 -> CalendarTabBody(
                            state = calendarState,
                            onIntent = onCalendarIntent,
                            contentBottomPadding = bodyBottomPadding,
                        )
                    }
                }

                // The add-task row, PINNED under the pager rather than appended to a list — the one place
                // this tab differs from the Inbox, and deliberately.
                //
                // Both of this tab's pages show REMINDERS, not a checklist you append to, and a capture
                // here always lands in the system Inbox (see TodayViewModel.captureTask) — never on the
                // day under the finger. A row sitting at the end of the agenda would promise "add to this
                // date", which is the one thing it does not do. Pinned, it reads as the tab's action.
                //
                // One insertion point also covers BOTH pages and every state the bodies can be in
                // (loading, empty, error, agenda) — the "+" it replaces was visible across all of them,
                // and reproducing that inside two independent list bodies and their five empty branches
                // is where the affordance would go missing on one of them.
                //
                // Gating and gate ORDER live in [captureRowVisible] at the top of this composable — see
                // there for why the arm flag is read before the window size.
                if (captureRowVisible) {
                    AddTaskRow(
                        onClick = onAddTaskRowClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            // Same chain as the bodies above: fillMaxWidth pins minWidth == maxWidth, and
                            // a widthIn(max) coerced into a fixed range is a no-op, so the relax step in
                            // the middle is what lets the cap bind on a tablet.
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .adaptiveContentWidth()
                            .padding(
                                horizontal = AppDimens.ScreenPaddingHorizontal,
                                vertical = AppDimens.SpacingXs,
                            )
                            // The host's reserve, applied to the row because the row is the bottommost
                            // element of this tab (see [bodyBottomPadding] above). It sits OUTSIDE
                            // AddTaskRow's own `clickable`, which the component applies after the caller's
                            // modifier — so this is dead space, not an enlarged target overlapping the
                            // shell's AI button. 0.dp in the control arm, which draws no row at all.
                            .padding(bottom = contentBottomPadding),
                    )
                }
            }
        }

        // TOP-BAR scrim — the other tile, dimming the status-bar zone and the app bar the scaffold
        // owns and the content cannot reach. Height is exactly the content column's offset, so the
        // two tiles meet without a bright seam or a double-dark band. No pointer-input modifier: a
        // background-only node is invisible to hit-testing, so the toolbar stays pressable.
        if (captureVisible && contentTopPx > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { contentTopPx.toDp() })
                    .background(captureScrimColor),
            )
        }
    }
}

/** Calendar tab body (was the body of the standalone CalendarScreen). */
@Composable
private fun CalendarTabBody(
    state: CalendarState,
    onIntent: (CalendarIntent) -> Unit,
    contentBottomPadding: Dp = 0.dp,
) {
    when (state) {
        CalendarState.Loading -> CalendarLoadingContent()
        is CalendarState.Content -> CalendarContent(
            state = state,
            onIntent = onIntent,
            contentBottomPadding = contentBottomPadding,
        )
        CalendarState.Empty -> CalendarEmptyState(onIntent = onIntent)
        is CalendarState.Error -> CalendarErrorState(state = state, onIntent = onIntent)
    }
}

// ---------------------------------------------------------------------------
// Loading
// ---------------------------------------------------------------------------

@Composable
private fun CalendarLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

// ---------------------------------------------------------------------------
// Content — top banner (teaser / grid) + agenda LazyColumn
// ---------------------------------------------------------------------------

@Composable
private fun CalendarContent(
    state: CalendarState.Content,
    onIntent: (CalendarIntent) -> Unit,
    contentBottomPadding: Dp = 0.dp,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Build a flat index map: epochDay → LazyColumn item index (for grid cell scroll).
    // Headers are stickyHeader items; we need the position of the matching sticky item.
    // We do a single pass here at composition time (cheap for ~35 agenda items max).
    val headerIndexMap: Map<Long, Int> = buildHeaderIndexMap(state.agenda)

    // Briefly highlight the DateHeader matching a tapped week-grid cell.
    // Works regardless of whether LazyColumn can actually scroll — if agenda
    // fits the viewport, scroll is a no-op but the highlight still gives
    // unambiguous "yes, I targeted this date" feedback.
    var tappedEpochDay by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(tappedEpochDay) {
        if (tappedEpochDay != null) {
            kotlinx.coroutines.delay(900)
            tappedEpochDay = null
        }
    }

    // First-open auto-scroll: jump to today's DateHeader so the user lands on
    // "now" rather than on -7 days of past history. Gated by rememberSaveable
    // so manual scrolling past this point is preserved on tab switch / config
    // change (we don't snap back to today every time CalendarContent recomposes).
    var didInitialScroll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(headerIndexMap, didInitialScroll) {
        if (didInitialScroll) return@LaunchedEffect
        if (headerIndexMap.isEmpty()) return@LaunchedEffect
        val todayDate = Instant.fromEpochMilliseconds(currentTimeMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        val todayEpochDay = todayDate.toEpochDays().toLong()
        val index = headerIndexMap[todayEpochDay] ?: return@LaunchedEffect
        listState.scrollToItem(index)
        didInitialScroll = true
    }

    // Derive the epochDay of the agenda's currently-visible "leading" DateHeader.
    // Walking backward from firstVisibleItemIndex finds the section the user is
    // currently looking at. WeekGridContent uses this to (a) align its week to
    // the visible date and (b) underline the matching day cell.
    val firstVisibleEpochDay by remember(state.agenda) {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            var i = firstVisible.coerceAtMost(state.agenda.lastIndex)
            while (i >= 0) {
                val item = state.agenda.getOrNull(i)
                if (item is AgendaItem.DateHeader && item.epochDay != Long.MIN_VALUE) {
                    return@derivedStateOf item.epochDay
                }
                i--
            }
            null
        }
    }

    // The readable-width cap sits HERE rather than on the agenda list, so the week grid and the
    // agenda under it share one column edge — capping only the list left a full-bleed 1280dp grid
    // of 175dp squares above a 720dp list.
    //
    // wrapContentWidth between the fill and the cap is what makes the cap bind at all: fillMaxSize
    // pins minWidth == maxWidth to the pane, and Constraints.constrain() coerces a widthIn(max)
    // back up into that fixed range, so `.fillMaxSize().adaptiveContentWidth()` is a no-op. This
    // relaxes the minimum back to 0 so the cap can cut the width, then centres the capped column in
    // the pane the wrapper still occupies. Same chain as the Inbox and Projects tabs.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .adaptiveContentWidth(),
    ) {

        // ---- Top banner: week grid (free for everyone — monetization TBD) ----
        WeekGridContent(
            agenda = state.agenda,
            currentVisibleEpochDay = firstVisibleEpochDay,
            onCellClick = { epochDay ->
                tappedEpochDay = epochDay
                headerIndexMap[epochDay]?.let { index ->
                    scope.launch { listState.animateScrollToItem(index) }
                }
            },
            onPrevWeekClick = {
                val anchor = firstVisibleEpochDay
                if (anchor != null) {
                    val target = weekMondayEpochDay(anchor) - 7
                    val index = headerIndexMap[target]
                        ?: headerIndexMap.entries
                            .filter { it.key < anchor }
                            .maxByOrNull { it.key }
                            ?.value
                    if (index != null) {
                        scope.launch { listState.animateScrollToItem(index) }
                    }
                }
            },
            onNextWeekClick = {
                val anchor = firstVisibleEpochDay
                if (anchor != null) {
                    val target = weekMondayEpochDay(anchor) + 7
                    val index = headerIndexMap[target]
                        ?: headerIndexMap.entries
                            .filter { it.key > anchor }
                            .minByOrNull { it.key }
                            ?.value
                    if (index != null) {
                        scope.launch { listState.animateScrollToItem(index) }
                    }
                }
            },
        )

        // ---- Agenda list ----
        AgendaListContent(
            agenda = state.agenda,
            listState = listState,
            highlightedEpochDay = tappedEpochDay,
            onReminderClick = { info -> onIntent(CalendarIntent.OnReminderClick(info)) },
            contentBottomPadding = contentBottomPadding,
        )
    }
}

// ---------------------------------------------------------------------------
// AgendaListContent — LazyColumn with stickyHeader + items
// ---------------------------------------------------------------------------

@Composable
private fun AgendaListContent(
    agenda: List<AgendaItem>,
    listState: LazyListState,
    highlightedEpochDay: Long?,
    onReminderClick: (TodayReminderInfo) -> Unit,
    modifier: Modifier = Modifier,
    contentBottomPadding: Dp = 0.dp,
) {
    LazyColumn(
        state = listState,
        // No width cap here — CalendarContent already caps the column this list lives in, and a
        // second widthIn(max) inside an already-narrower parent would be dead weight.
        modifier = modifier.fillMaxSize(),
        // The host reserve is ADDED to the existing SpacingXl, never substituted for it: 0.dp in the
        // control arm reproduces the original padding exactly.
        contentPadding = PaddingValues(bottom = AppDimens.SpacingXl + contentBottomPadding),
    ) {
        // Render each AgendaItem — DateHeaders as stickyHeader, ReminderRows as items.
        // We iterate the flat list manually to mix stickyHeader + item calls.
        // Group consecutive same-type items: DateHeader → stickyHeader, ReminderRow → item.
        agenda.forEach { agendaItem ->
            when (agendaItem) {
                is AgendaItem.DateHeader -> {
                    stickyHeader(key = "header:${agendaItem.epochDay}") {
                        CalendarDateHeader(
                            header = agendaItem,
                            isHighlighted = agendaItem.epochDay == highlightedEpochDay,
                        )
                    }
                }
                is AgendaItem.ReminderRow -> {
                    val reminderKey = when (val info = agendaItem.info) {
                        is TodayReminderInfo.ItemLevel -> "reminder:${info.reminderAt}:${info.checklistId}:${info.itemId}"
                        is TodayReminderInfo.ChecklistLevel -> "reminder:${info.reminderAt}:${info.checklistId}"
                    }
                    item(key = reminderKey) {
                        CalendarReminderRow(
                            info = agendaItem.info,
                            onClick = { onReminderClick(agendaItem.info) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Builds a map of epochDay → approximate flat index in the LazyColumn for scroll targeting.
 * Only DateHeader items are tracked (stickyHeader index in the combined list).
 */
private fun buildHeaderIndexMap(agenda: List<AgendaItem>): Map<Long, Int> {
    val map = mutableMapOf<Long, Int>()
    agenda.forEachIndexed { index, item ->
        if (item is AgendaItem.DateHeader) {
            map[item.epochDay] = index
        }
    }
    return map
}

// ---------------------------------------------------------------------------
// DateHeader — sticky section separator
// ---------------------------------------------------------------------------

@Composable
private fun CalendarDateHeader(
    header: AgendaItem.DateHeader,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val targetBg = if (isHighlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.background
    }
    val bgColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = 250),
        label = "calendar_header_highlight",
    )
    val labelColor = if (header.isPastDue) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
            .padding(top = AppDimens.SpacingLg, bottom = AppDimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
    ) {
        if (header.isPastDue) {
            Icon(
                imageVector = Icons.Outlined.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(AppDimens.IconSizeMd),
            )
        }
        Text(
            text = header.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// ReminderRow — flat ListItem-style row (no AppCard, matches TodayScreen)
// ---------------------------------------------------------------------------

@Composable
private fun CalendarReminderRow(
    info: TodayReminderInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isItemLevel = info is TodayReminderInfo.ItemLevel
    val primaryText = when (info) {
        is TodayReminderInfo.ItemLevel -> info.itemText
        is TodayReminderInfo.ChecklistLevel -> info.checklistName
    }
    val timeLabel = formatReminderTime(info.reminderAt)

    val rowDescription = buildString {
        append(timeLabel)
        append(" — ")
        if (isItemLevel) {
            append(primaryText)
            append(" in ")
        }
        append(info.checklistName)
        if (info.isRecurring) append(", recurring")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
                role = Role.Button
            }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = AppDimens.ScreenPaddingHorizontal,
                    vertical = AppDimens.SpacingMd,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading icon: recurring → bell, one-shot → alarm clock
            Icon(
                imageVector = if (info.isRecurring) Icons.Outlined.Notifications else Icons.Outlined.Alarm,
                contentDescription = null, // merged via parent semantics
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(AppDimens.IconSizeMd),
            )

            Spacer(modifier = Modifier.width(AppDimens.SpacingLg))

            // Text block — primary + supporting
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Show parent checklist name only for per-item reminders
                    if (isItemLevel) {
                        Text(
                            text = info.checklistName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(
                start = AppDimens.ScreenPaddingHorizontal + AppDimens.IconSizeMd + AppDimens.SpacingLg,
            ),
            thickness = AppDimens.DividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * Formats epoch millis to HH:mm string, KMP-safe (no java.text.SimpleDateFormat).
 */
private fun formatReminderTime(reminderAt: Long): String {
    val instant = Instant.fromEpochMilliseconds(reminderAt)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val h = local.hour.toString().padStart(2, '0')
    val m = local.minute.toString().padStart(2, '0')
    return "$h:$m"
}

// ---------------------------------------------------------------------------
// WeekGridContent — Mon-Sun 7-cell row above agenda
// ---------------------------------------------------------------------------

@Composable
private fun WeekGridContent(
    agenda: List<AgendaItem>,
    currentVisibleEpochDay: Long?,
    onCellClick: (epochDay: Long) -> Unit,
    onPrevWeekClick: () -> Unit,
    onNextWeekClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Anchor the displayed week on the agenda's currently-visible date.
    // Falls back to "today" when nothing is visible yet (initial composition).
    val today = Instant.fromEpochMilliseconds(currentTimeMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    val anchorDate = currentVisibleEpochDay?.let {
        kotlinx.datetime.LocalDate.fromEpochDays(it.toInt())
    } ?: today
    val mondayOffset = anchorDate.dayOfWeek.ordinal // Mon=0 … Sun=6
    val monday = anchorDate.minus(mondayOffset, DateTimeUnit.DAY)
    val sunday = monday.plus(6, DateTimeUnit.DAY)

    // Collect dates that have at least one reminder.
    val datesWithReminders: Set<Long> = agenda
        .filterIsInstance<AgendaItem.ReminderRow>()
        .map { row ->
            Instant.fromEpochMilliseconds(row.info.reminderAt)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays().toLong()
        }
        .toSet()

    val weekLabel = buildString {
        append(formatShortDate(monday))
        append(" — ")
        append(formatShortDate(sunday))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.SpacingLg, vertical = AppDimens.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevWeekClick,
                modifier = Modifier.size(AppDimens.IconSizeMd + AppDimens.SpacingMd),
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = stringResource(Res.string.calendar_prev_week),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppDimens.IconSizeMd),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = weekLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(
                onClick = onNextWeekClick,
                modifier = Modifier.size(AppDimens.IconSizeMd + AppDimens.SpacingMd),
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = stringResource(Res.string.calendar_next_week),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppDimens.IconSizeMd),
                )
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingXs))

        // BoxWithConstraints, not a bare Row: a cell has to know its own WIDTH to use it as its
        // minimum HEIGHT, and a `weight(1f)` child cannot read the width it was given. See
        // [WeekGridCell] for why the square became a floor instead of an aspect ratio.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cellSide = weekCellSide(maxWidth)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
            ) {
                for (dayOffset in 0..6) {
                    val cellDate = monday.plus(dayOffset, DateTimeUnit.DAY)
                    val cellEpochDay = cellDate.toEpochDays().toLong()
                    val isToday = cellDate == today
                    val isPast = cellDate < today
                    val hasReminder = cellEpochDay in datesWithReminders
                    val isCurrentScroll = cellEpochDay == currentVisibleEpochDay

                    WeekGridCell(
                        date = cellDate,
                        isToday = isToday,
                        isPast = isPast,
                        hasReminder = hasReminder,
                        isCurrentScroll = isCurrentScroll,
                        minSide = cellSide,
                        onClick = { onCellClick(cellEpochDay) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Scroll-position indicator: a single primary-colored bar that slides
        // horizontally under the day cell whose date matches the agenda's
        // currently-visible DateHeader. M3 tab-indicator pattern with a tween
        // slide animation between positions.
        Spacer(modifier = Modifier.height(AppDimens.SpacingXs))
        val currentIdx = (0..6).firstOrNull { dayOffset ->
            monday.plus(dayOffset, DateTimeUnit.DAY).toEpochDays().toLong() ==
                currentVisibleEpochDay
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(3.dp)) {
            // Same helper as the cells above, deliberately: the indicator slides to a cell's centre,
            // so if the two ever computed the width differently the bar would drift off its day.
            val cellW = weekCellSide(this.maxWidth)
            val indicatorW = cellW * 0.6f
            val centerOffset = (cellW - indicatorW) / 2
            val targetX = if (currentIdx != null) {
                (cellW + AppDimens.SpacingXs) * currentIdx + centerOffset
            } else {
                0.dp
            }
            val animX by animateDpAsState(
                targetValue = targetX,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                label = "calendar_indicator_x",
            )
            if (currentIdx != null) {
                Box(
                    modifier = Modifier
                        .offset(x = animX)
                        .width(indicatorW)
                        .height(3.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }

    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = AppDimens.DividerThickness,
    )
}

/**
 * Side of ONE day tile in a week grid [totalWidth] wide: seven equal cells with six [AppDimens.SpacingXs]
 * gaps between them.
 *
 * Shared by the cells (which use it as their minimum height) and by the scroll indicator (which uses
 * it to place itself under a cell's centre) so the two cannot drift apart.
 */
private fun weekCellSide(totalWidth: Dp): Dp = (totalWidth - AppDimens.SpacingXs * 6) / 7

/** Compute the epoch-day of the Monday of the week containing [anchorEpochDay]. */
private fun weekMondayEpochDay(anchorEpochDay: Long): Long {
    val date = LocalDate.fromEpochDays(anchorEpochDay.toInt())
    val mondayOffset = date.dayOfWeek.ordinal // Mon=0 … Sun=6
    return date.minus(mondayOffset, DateTimeUnit.DAY).toEpochDays().toLong()
}

/**
 * One day tile of the week grid.
 *
 * @param minSide the tile's width, applied as its MINIMUM height — see the `heightIn` call below.
 */
@Composable
private fun WeekGridCell(
    date: LocalDate,
    isToday: Boolean,
    isPast: Boolean,
    hasReminder: Boolean,
    isCurrentScroll: Boolean,
    minSide: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tappable on today, past-with-history, or future-with-reminders.
    val isInteractive = hasReminder || isToday
    val bgColor = when {
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    // Past days are dimmed (history) regardless of whether they have content.
    // Future days without reminders are also dimmed (nothing scheduled).
    // Today stays full-opacity (primaryContainer signals it).
    val textColor = when {
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        isPast -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        !isInteractive -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Weekday letter: Mon → "M", Tue → "T", etc.
    val weekdayLetter = date.dayOfWeek.name.first().toString()

    Column(
        modifier = modifier
            .testTag(WeekGridCellTestTag)
            // A MINIMUM square, not `aspectRatio(1f)`.
            //
            // `aspectRatio` pins height == width exactly, and width here is a fraction of the SCREEN
            // (a dp), while everything inside is measured in sp and grows with the system font scale.
            // The two never had to agree, and `clip()` above silently cut whatever did not fit: the
            // reminder dot rendered as a dash welded to the tile's bottom edge at fontScale 1.0 on a
            // 411dp phone, vanished entirely at 360dp, and at fontScale 1.3 the DAY NUMBER itself was
            // sliced through the middle. Recut typography (`Type.kt`, `lineHeightStyle = Trim.None`
            // on every role) is what pushed an already-tight budget over the edge, but the fixed
            // height is the defect — the same "height() pins min AND max" trap `AppDensity` spells
            // out for the task rows: heights are minimums, never fixed values.
            //
            // With `heightIn(min = …)` the tile is exactly square whenever the content fits — the
            // common case, pixel-identical to the intended design — and grows instead of clipping
            // when it does not. All seven cells hold the same anatomy (one letter, one number, the
            // marker slot), so they measure the same height and the row stays even without any
            // intrinsic-measurement machinery.
            .heightIn(min = minSide)
            .clip(RoundedCornerShape(AppDimens.SpacingSm))
            .background(bgColor)
            .then(if (isInteractive) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = AppDimens.SpacingXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = weekdayLetter,
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.7f),
        )
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Bottom slot: dot for dates with reminders; fixed-size spacer otherwise.
        // (Current-scroll indicator lives as a text underline on the number above.)
        when {
            hasReminder -> Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = if (isPast) 0.45f else 1f,
                        ),
                        CircleShape,
                    ),
            )
            else -> Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

/**
 * Test tag on every day tile of the week grid.
 *
 * The tile's box is what the geometry regression asserts against — that its content (weekday letter,
 * day number, reminder marker) fits INSIDE it — and there is no other way to reach those bounds:
 * the cell carries no merged semantics, and its vertical origin cannot be derived from the texts.
 */
internal const val WeekGridCellTestTag = "calendar_week_day_cell"

/** Formats a LocalDate to "Mon D" short form, e.g. "May 12". KMP-safe, no locale. */
private fun formatShortDate(date: LocalDate): String {
    val monthAbbrev = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    return "$monthAbbrev ${date.dayOfMonth}"
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

@Composable
private fun CalendarEmptyState(
    onIntent: (CalendarIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = Icons.Outlined.CalendarMonth,
        title = stringResource(Res.string.calendar_empty_title),
        description = stringResource(Res.string.calendar_empty_description),
        modifier = modifier,
        action = {
            AppButton(
                text = stringResource(Res.string.calendar_empty_cta),
                onClick = { onIntent(CalendarIntent.OnCreateChecklistClick) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

// ---------------------------------------------------------------------------
// Error state
// ---------------------------------------------------------------------------

@Composable
private fun CalendarErrorState(
    state: CalendarState.Error,
    onIntent: (CalendarIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = Icons.Outlined.ErrorOutline,
        title = stringResource(Res.string.calendar_error_title),
        description = state.message,
        modifier = modifier,
        action = {
            AppButton(
                text = stringResource(Res.string.calendar_error_retry),
                onClick = { onIntent(CalendarIntent.OnRetry) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

// ---------------------------------------------------------------------------
// Note: Compose Previews for CalendarScreen live in androidMain.
// Add previews in:
//   feature/home/src/androidMain/.../calendar/CalendarScreenPreviews.kt
// ---------------------------------------------------------------------------
