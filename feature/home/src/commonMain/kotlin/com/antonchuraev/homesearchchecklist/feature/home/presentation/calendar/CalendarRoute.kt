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
    onCreateChecklistClick: () -> Unit,
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
    todayViewModel: TodayViewModel = koinViewModel(),
    calendarViewModel: CalendarViewModel = koinViewModel(),
) {
    val todayState by todayViewModel.screenState.collectAsStateWithLifecycle()
    val calendarState by calendarViewModel.screenState.collectAsStateWithLifecycle()
    val quickAddText by todayViewModel.quickAddText.collectAsStateWithLifecycle()
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
        quickAddText = quickAddText,
        captureDockOpen = captureDockOpen,
        captureEnabled = captureEnabled,
        onCaptureDockDismiss = onCaptureDockDismiss,
        onQuickAddTextChange = { todayViewModel.sendIntent(TodayIntent.OnQuickAddTextChanged(it)) },
        onQuickAddSubmit = { todayViewModel.sendIntent(TodayIntent.OnQuickAddSubmit) },
        snackbarHostState = snackbarHostState,
        onTodayReminderClick = { checklistId, fillId ->
            todayViewModel.sendIntent(TodayIntent.OnReminderClick(checklistId, fillId))
        },
        onTodayCreateChecklistClick = {
            todayViewModel.sendIntent(TodayIntent.OnCreateChecklistClick)
            onCreateChecklistClick()
        },
        onTodayRetry = { todayViewModel.sendIntent(TodayIntent.OnRefresh) },
        onCalendarIntent = { intent ->
            if (intent is CalendarIntent.OnCreateChecklistClick) {
                onCreateChecklistClick()
            }
            calendarViewModel.sendIntent(intent)
        },
    )
}
