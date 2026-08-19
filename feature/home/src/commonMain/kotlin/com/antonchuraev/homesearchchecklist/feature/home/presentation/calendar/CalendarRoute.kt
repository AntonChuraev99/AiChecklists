package com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar

import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayIntent
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodaySideEffect
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayViewModel
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components.CreditsChipSource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Route composable for the Calendar destination.
 *
 * Hosts a 2-tab UI ("Today" and "Calendar"). The screen embeds the Today
 * agenda body in tab 0 and the week-grid + range agenda in tab 1. Two
 * separate ViewModels are injected — they live for the lifetime of this
 * route and survive tab switches without re-creation.
 *
 * SideEffect routing pattern: this project routes navigation directly from
 * each ViewModel via [AppNavigator], so no LaunchedEffect side-effect
 * collection is needed here.
 */
@Composable
fun CalendarRoute(
    drawerState: DrawerState?,
    /**
     * Extra bottom inset the HOST reserves below this screen (v2 shell: bottom bar + chat FAB).
     * The Calendar had no bottom-padding parameter at all, so without this its agenda rows scroll
     * under the v2 NavigationBar. 0.dp — the default and what the control arm passes — reproduces
     * the previous layout byte for byte.
     */
    contentBottomPadding: Dp = 0.dp,
    /**
     * Quick-capture on the Today tab, hoisted to the v2 shell exactly like the Inbox tab's dock: the
     * shell's FABs draw above this screen and must hide while the dock is up, so a flag private to
     * this route would leave the "+" FAB floating over its own dock.
     *
     * Defaults keep the control arm (which passes neither) rendering as before.
     */
    captureDockOpen: Boolean = false,
    /**
     * Whether the HOST offers a capture affordance at all (the v2 shell's "+" FAB) — not whether the
     * dock happens to be open right now. Threaded rather than derived from the nav variant, which
     * this screen cannot see and should not have to: the Today body's empty state promises an input
     * ("Add a task below"), and that promise is only true where a FAB exists to raise the dock.
     *
     * Default false = the classic layout's neutral wording, so the control arm is right unchanged.
     */
    captureEnabled: Boolean = false,
    onCaptureDockDismiss: () -> Unit = {},
    /**
     * An add-task affordance on this tab was tapped — the HOST must raise [captureDockOpen] (and
     * close any other bottom dock first; two of them cannot share the bottom edge).
     *
     * "The row" in the name is now the smaller half of the truth: the same callback also backs the
     * Today page's and the Calendar page's placeholder buttons, which are that row in another
     * carrier. One action, one callback, one analytics series.
     *
     * No default, unlike the capture params above: those have a correct "off" value for the control
     * arm, this one does not. A host that renders the row ([captureEnabled] = true) and forgets this
     * callback ships a row that looks tappable and does nothing — the exact dead affordance this arm
     * already shipped once on the rail. Let the compiler ask.
     *
     * ⚠️ Do NOT emit `nav_create_fab_tapped` here: this route already sends
     * [TodayIntent.OnAddTaskRowClick], and the ViewModel emits it with `source = "inline_row"`.
     */
    onAddTaskRowClick: () -> Unit,
    /**
     * Analytics `source` for the top bar's AI-credits chip — see [CalendarScreen]; **null draws no
     * chip**.
     *
     * Default null, UNLIKE `InboxRoute` / `ProjectsRoute`, and that asymmetry is the point: this
     * route is also what the v1 drawer pushes, so it is the one v2 tab whose screen is shared with
     * the A/B CONTROL arm. Defaulting it on would add a paywall entry point to control and confound
     * the experiment. The v2 host opts in with [CreditsChipSource.V2_CALENDAR]; the control arm
     * passes nothing, exactly as it does for [captureEnabled].
     */
    creditsSource: String? = null,
    todayViewModel: TodayViewModel = koinViewModel(),
    calendarViewModel: CalendarViewModel = koinViewModel(),
) {
    val todayState by todayViewModel.screenState.collectAsStateWithLifecycle()
    val calendarState by calendarViewModel.screenState.collectAsStateWithLifecycle()
    val draft by todayViewModel.draft.collectAsStateWithLifecycle()
    val due by todayViewModel.due.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(todayViewModel) {
        todayViewModel.sideEffect.collect { effect ->
            when (effect) {
                is TodaySideEffect.ShowCaptureMessage -> {
                    val result = snackbarHostState.showSnackbar(
                        message = effect.text,
                        actionLabel = effect.actionLabel,
                        // The capture dock stays open for the next task, so the snackbar sits over an
                        // active keyboard; Short is long enough to read "Added to Inbox" and short
                        // enough not to cover the input while typing the next one.
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        todayViewModel.sendIntent(TodayIntent.OnOpenCapturedChecklist)
                    }
                }
            }
        }
    }

    CalendarScreen(
        todayState = todayState,
        calendarState = calendarState,
        drawerState = drawerState,
        contentBottomPadding = contentBottomPadding,
        draft = draft,
        due = due,
        onDueIntent = { todayViewModel.sendIntent(TodayIntent.OnDue(it)) },
        captureDockOpen = captureDockOpen,
        captureEnabled = captureEnabled,
        onCaptureDockDismiss = onCaptureDockDismiss,
        // Broadcast, not routed: the ViewModel gets the tap for the analytics (the event used to
        // hang off the shell's "+" FAB, which is being deleted), the host gets it to raise the dock
        // flag it owns.
        //
        // ONE callback for all THREE doors into the dock on this tab — the pinned row, the Today
        // page's placeholder button and (since 2026-08-19) the Calendar page's. They are one action,
        // so they share one wiring; a second entry point with its own would split `inline_row` into
        // two series that cannot be summed.
        onAddTaskRowClick = {
            todayViewModel.sendIntent(TodayIntent.OnAddTaskRowClick)
            onAddTaskRowClick()
        },
        onQuickAddTextChange = { todayViewModel.sendIntent(TodayIntent.OnQuickAddTextChanged(it)) },
        onQuickAddSubmit = { todayViewModel.sendIntent(TodayIntent.OnQuickAddSubmit) },
        onCreateChipAction = { todayViewModel.sendIntent(TodayIntent.OnCreateChipAction(it)) },
        // Straight to the ViewModel: it owns BOTH halves of the response (the `ai_entry_tapped`
        // emit and the navigation into Analyze). Unlike the add-task row above, the host has
        // nothing to do here — no dock flag changes, so there is no callback to fork.
        onAiSourceTapped = { todayViewModel.sendIntent(TodayIntent.OnAiSourceTapped(it)) },
        snackbarHostState = snackbarHostState,
        onTodayReminderClick = { checklistId, fillId ->
            todayViewModel.sendIntent(TodayIntent.OnReminderClick(checklistId, fillId))
        },
        onTodayRetry = { todayViewModel.sendIntent(TodayIntent.OnRefresh) },
        creditsSource = creditsSource,
        onCalendarIntent = calendarViewModel::sendIntent,
    )
}
