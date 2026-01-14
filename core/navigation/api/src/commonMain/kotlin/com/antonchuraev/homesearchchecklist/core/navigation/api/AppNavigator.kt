package com.antonchuraev.homesearchchecklist.core.navigation.api

import androidx.navigation.NavController

interface AppNavigator {

    fun installNavController(navController: NavController)

    fun onBack()

    fun navigateToOnboarding()

    /**
     * todo clear back stack
     */
    fun navigateToMainScreen()

    fun navigateToDebugMenu()

    /**
     * todo change templateId to template class
     */
    fun navigateToCreateChecklistScreen(templateId: Int?)

    fun navigateToTemplatesScreen()

    fun navigateToAnalyzeScreen()

    fun navigateToChecklistDetail(checklistId: Long)

    fun navigateToFillDetail(fillId: Long)

    fun navigateToPaywall()
}

