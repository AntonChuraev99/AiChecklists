package com.antonchuraev.homesearchchecklist.core.navigation.api

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppNavRoute {
    @Serializable
    data object Splash : AppNavRoute

    @Serializable
    data object Onboarding : AppNavRoute

    @Serializable
    data object Main : AppNavRoute

    @Serializable
    sealed interface CreateChecklistRoute : AppNavRoute {
        @Serializable
        data class CreateChecklist(val templateId: Int?) : CreateChecklistRoute

        @Serializable
        data object Templates : CreateChecklistRoute
    }

    @Serializable
    data object Debug : AppNavRoute
}