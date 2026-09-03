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
 * This is the entry point App.kt binds to its own `AppNavRoute.Today` destination — the screen is
 * PUSHED (from the drawer in v1, from the Overview tab in v2), it is not a tab swap inside
 * `AppNavRoute.Main`.
 *
 * Visibility: public — App.kt is in a different Gradle module (composeApp) and must
 * access this composable directly.
 *
 * @param onBack Optional explicit back affordance, forwarded to [TodayScreen] and rendered as the
 *   TopAppBar back arrow ONLY when [drawerState] is null. Hosts that PUSH this screen (the v2 shell
 *   opens Today from the Overview tab, where Today is not a tab: no bottom bar, no FAB,
 *   `drawerState = null`) MUST pass it — otherwise the only exit is Android's system BACK, which does
 *   not exist on the wasmJs Web target. Null (the default) keeps rendering identical.
 */
@Composable
fun TodayRoute(
    drawerState: DrawerState?,
    onBack: (() -> Unit)? = null,
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()

    TodayScreen(
        state = state,
        drawerState = drawerState,
        onReminderClick = { checklistId, fillId ->
            viewModel.sendIntent(TodayIntent.OnReminderClick(checklistId, fillId))
        },
        onRetry = { viewModel.sendIntent(TodayIntent.OnRefresh) },
        onBack = onBack,
    )
}
