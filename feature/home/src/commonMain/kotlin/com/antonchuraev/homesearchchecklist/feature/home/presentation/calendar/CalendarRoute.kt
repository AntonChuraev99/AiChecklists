package com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Route composable for the Calendar screen.
 *
 * Injects [CalendarViewModel] via Koin and connects it to [CalendarScreen].
 * Public — App.kt lives in a different Gradle module (composeApp) and must
 * access this composable directly.
 *
 * SideEffect routing pattern: this project routes navigation directly from the
 * ViewModel via [AppNavigator], so no LaunchedEffect side-effect collection is
 * needed here (same pattern as TodayRoute).
 */
@Composable
fun CalendarRoute(
    drawerState: DrawerState,
    onCreateChecklistClick: () -> Unit,
    viewModel: CalendarViewModel = koinViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()

    CalendarScreen(
        state = state,
        drawerState = drawerState,
        onIntent = { intent ->
            // Intercept OnCreateChecklistClick to also trigger the external callback
            // (keeps the Route in control of outbound navigation, same as TodayRoute).
            if (intent is CalendarIntent.OnCreateChecklistClick) {
                onCreateChecklistClick()
            }
            viewModel.sendIntent(intent)
        },
    )
}
