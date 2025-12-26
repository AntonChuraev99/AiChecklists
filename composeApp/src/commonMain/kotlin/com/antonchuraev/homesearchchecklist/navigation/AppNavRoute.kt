package com.antonchuraev.homesearchchecklist.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class AppNavRoute(val route: String) {
    @Serializable
    data object Splash : AppNavRoute("splash")

    @Serializable
    data object Onboarding : AppNavRoute("onboarding")
    
    @Serializable
    data object Main : AppNavRoute("main")

    @Serializable
    sealed class CreateChecklistRoute(private val routeParam: String) : AppNavRoute(routeParam) {
        @Serializable
        data class CreateChecklist(val templateId: Int?) : CreateChecklistRoute("create_checklist")

        @Serializable
        data object Templates : CreateChecklistRoute("templates")
    }

    @Serializable
    data object Debug : AppNavRoute("debug")
}
