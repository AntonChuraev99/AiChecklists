package com.antonchuraev.homesearchchecklist.core.navigation.impl

import androidx.navigation.NavController
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator

class AppNavigatorImpl() : AppNavigator {

    private lateinit var navController: NavController

    override fun installNavController(navController: NavController) {
        this@AppNavigatorImpl.navController = navController
    }

    override fun onBack() {
        navController.popBackStack()
    }

    override fun navigateToOnboarding() {
        navController.navigate(AppNavRoute.Onboarding)
    }

    /**
     * todo clear back stack
     */
    override fun navigateToMainScreen() {
        navController.navigate(AppNavRoute.Main)
        /*navController.navigate(AppNavRoute.Main) {
            popUpTo(AppNavRoute.Onboarding) { inclusive = true }
        }*/
    }

    override fun navigateToDebugMenu() {
        navController.navigate(AppNavRoute.Debug)
    }

    override fun navigateToCreateChecklistScreen(templateId: Int?) {
        navController.navigate(AppNavRoute.CreateChecklistRoute.CreateChecklist(templateId))
    }

    override fun navigateToTemplatesScreen() {
        navController.navigate(AppNavRoute.CreateChecklistRoute.Templates)
    }

    override fun navigateToAnalyzeScreen() {
        navController.navigate(AppNavRoute.Analyze)
    }

    override fun navigateToChecklistDetail(checklistId: Long) {
        navController.navigate(AppNavRoute.ChecklistDetail(checklistId))
    }
}

