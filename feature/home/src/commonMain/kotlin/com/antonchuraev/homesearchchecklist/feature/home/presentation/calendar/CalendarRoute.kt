package com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayIntent
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
    drawerState: DrawerState,
    onCreateChecklistClick: () -> Unit,
    todayViewModel: TodayViewModel = koinViewModel(),
    calendarViewModel: CalendarViewModel = koinViewModel(),
) {
    val todayState by todayViewModel.screenState.collectAsStateWithLifecycle()
    val calendarState by calendarViewModel.screenState.collectAsStateWithLifecycle()

    CalendarScreen(
        todayState = todayState,
        calendarState = calendarState,
        drawerState = drawerState,
        onTodayReminderClick = { checklistId, fillId ->
            todayViewModel.sendIntent(TodayIntent.OnReminderClick(checklistId, fillId))
        },
        onTodayCreateChecklistClick = {
            todayViewModel.sendIntent(TodayIntent.OnCreateChecklistClick)
            onCreateChecklistClick()
        },
        onCalendarIntent = { intent ->
            if (intent is CalendarIntent.OnCreateChecklistClick) {
                onCreateChecklistClick()
            }
            calendarViewModel.sendIntent(intent)
        },
    )
}
