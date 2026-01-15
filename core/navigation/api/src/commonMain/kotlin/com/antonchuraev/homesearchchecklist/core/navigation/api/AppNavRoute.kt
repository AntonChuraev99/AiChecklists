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

    @Serializable
    data class Analyze(val checklistId: Long? = null) : AppNavRoute

    @Serializable
    data class ChecklistDetail(val checklistId: Long) : AppNavRoute

    @Serializable
    data class FillDetail(val fillId: Long) : AppNavRoute

    @Serializable
    data class FillsList(val checklistId: Long) : AppNavRoute

    @Serializable
    data object Paywall : AppNavRoute

    @Serializable
    data object SubscriptionStatus : AppNavRoute
}