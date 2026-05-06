package com.antonchuraev.homesearchchecklist.feature.home.presentation.today

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Route composable for the Today screen.
 *
 * Injects [TodayViewModel] via Koin and connects it to [TodayScreen].
 * This is the public entry point used by App.kt in the tab swap inside AppNavRoute.Main.
 *
 * Visibility: public — App.kt is in a different Gradle module (composeApp) and must
 * access this composable directly. TodayScreen (the pure UI composable) remains private.
 */
@Composable
fun TodayRoute(
    drawerState: DrawerState,
    onCreateChecklistClick: () -> Unit,
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()

    TodayScreen(
        state = state,
        drawerState = drawerState,
        onReminderClick = { checklistId, fillId ->
            viewModel.sendIntent(TodayIntent.OnReminderClick(checklistId, fillId))
        },
        onCreateChecklistClick = {
            viewModel.sendIntent(TodayIntent.OnCreateChecklistClick)
            onCreateChecklistClick()
        },
    )
}
