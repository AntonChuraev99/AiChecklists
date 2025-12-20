package com.antonchuraev.homesearchchecklist.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class Screen(val route: String) {
    @Serializable
    data object Onboarding : Screen("onboarding")
    
    @Serializable
    data object Main : Screen("main")

    @Serializable
    sealed class CreateChecklist(private val routeParam: String) : Screen(routeParam) {
        @Serializable
        data class CreateChecklist(val templateId: Int?) : Screen.CreateChecklist("create_checklist")

        @Serializable
        data object Templates : Screen.CreateChecklist("templates")
    }

    @Serializable
    data object Debug : Screen("debug")
}

enum class MainTab {
    HOME,
    FUTURE
}
