package com.antonchuraev.homesearchchecklist.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute
import com.antonchuraev.homesearchchecklist.settings.presentation.SettingsRoute

fun NavGraphBuilder.settingsScreen(onBackClick: () -> Unit) {
    composable<AppNavRoute.Settings> {
        SettingsRoute(onBackClick = onBackClick)
    }
}
