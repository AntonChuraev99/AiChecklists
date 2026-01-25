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

    override fun navigateToMainScreen(clearBackStack: Boolean) {
        if (clearBackStack) {
            navController.navigate(AppNavRoute.Main) {
                popUpTo(0) { inclusive = true }
            }
        } else {
            navController.navigate(AppNavRoute.Main)
        }
    }

    override fun navigateToDebugMenu() {
        navController.navigate(AppNavRoute.Debug)
    }

    override fun navigateToStoreScreenshot() {
        navController.navigate(AppNavRoute.StoreScreenshot)
    }

    override fun navigateToCreateChecklistScreen(templateId: Int?) {
        navController.navigate(AppNavRoute.CreateChecklistRoute.CreateChecklist(templateId))
    }

    override fun navigateToEditChecklist(checklistId: Long) {
        navController.navigate(AppNavRoute.CreateChecklistRoute.CreateChecklist(editChecklistId = checklistId))
    }

    override fun navigateToTemplatesScreen() {
        navController.navigate(AppNavRoute.CreateChecklistRoute.Templates)
    }

    override fun navigateToTemplatePreview(templateId: String) {
        navController.navigate(AppNavRoute.CreateChecklistRoute.TemplatePreview(templateId))
    }

    override fun navigateToAnalyzeScreen(checklistId: Long?) {
        navController.navigate(AppNavRoute.Analyze(checklistId))
    }

    override fun navigateToAnalyzeResultPreview() {
        navController.navigate(AppNavRoute.AnalyzeResultPreview)
    }

    override fun navigateToChecklistDetail(checklistId: Long, clearBackStack: Boolean) {
        if (clearBackStack) {
            navController.navigate(AppNavRoute.ChecklistDetail(checklistId)) {
                popUpTo(AppNavRoute.Main) { inclusive = false }
            }
        } else {
            navController.navigate(AppNavRoute.ChecklistDetail(checklistId))
        }
    }

    override fun navigateToFillDetail(fillId: Long, clearBackStack: Boolean) {
        if (clearBackStack) {
            navController.navigate(AppNavRoute.FillDetail(fillId)) {
                popUpTo(AppNavRoute.Main) { inclusive = false }
            }
        } else {
            navController.navigate(AppNavRoute.FillDetail(fillId))
        }
    }

    override fun navigateToFillsList(checklistId: Long) {
        navController.navigate(AppNavRoute.FillsList(checklistId))
    }

    override fun navigateToPaywall() {
        navController.navigate(AppNavRoute.Paywall)
    }

    override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) {
        navController.navigate(AppNavRoute.SubscriptionStatus(showSuccessMessage))
    }

    override fun navigateToShareChecklist(checklistId: Long) {
        navController.navigate(AppNavRoute.ShareChecklist(checklistId))
    }
}

