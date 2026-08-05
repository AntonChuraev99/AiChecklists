package com.antonchuraev.homesearchchecklist.feature.home.presentation.projects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Route composable for the v2 Projects tab.
 *
 * Public because App.kt (module `composeApp`) mounts it from `entry<AppNavRoute.Projects>`;
 * [ProjectsScreen] stays a pure state-in / intents-out composable so it can be previewed and
 * screenshot-tested without Koin. Mirrors `InboxRoute` / `TodayRoute`.
 *
 * No side-effect channel, matching `CalendarViewModel`/`TodayViewModel`: the only thing this tab
 * ever had to report is a failed read, and that is a persistent, retryable
 * [ProjectsScreenState.Error] rather than a snackbar the user can miss.
 */
@Composable
fun ProjectsRoute(
    contentBottomPadding: Dp = 0.dp,
    viewModel: ProjectsViewModel = koinViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()

    ProjectsScreen(
        state = state,
        onIntent = viewModel::sendIntent,
        contentBottomPadding = contentBottomPadding,
    )
}
