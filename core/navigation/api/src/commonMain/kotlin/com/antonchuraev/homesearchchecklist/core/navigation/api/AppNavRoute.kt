package com.antonchuraev.homesearchchecklist.core.navigation.api

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppNavRoute {
    @Serializable
    data object Splash : AppNavRoute

    @Serializable
    data object Onboarding : AppNavRoute

    @Serializable
    data object InteractiveOnboarding : AppNavRoute

    @Serializable
    data object Main : AppNavRoute

    @Serializable
    sealed interface CreateChecklistRoute : AppNavRoute {
        @Serializable
        data class CreateChecklist(val templateId: Int? = null, val editChecklistId: Long? = null) : CreateChecklistRoute

        @Serializable
        data object Templates : CreateChecklistRoute

        @Serializable
        data class TemplatePreview(val templateId: String) : CreateChecklistRoute
    }

    @Serializable
    data object Debug : AppNavRoute

    @Serializable
    data object StoreScreenshot : AppNavRoute

    @Serializable
    data class Analyze(val checklistId: Long? = null, val fillDefault: Boolean = false) : AppNavRoute

    @Serializable
    data object AnalyzeResultPreview : AppNavRoute

    @Serializable
    data class ChecklistDetail(val checklistId: Long) : AppNavRoute

    @Serializable
    data class FillDetail(val fillId: Long) : AppNavRoute

    @Serializable
    data class FillsList(val checklistId: Long) : AppNavRoute

    @Serializable
    data class Paywall(val source: String = "unknown") : AppNavRoute

    @Serializable
    data class SubscriptionStatus(val showSuccessMessage: Boolean = false) : AppNavRoute

    @Serializable
    data class ShareChecklist(val checklistId: Long) : AppNavRoute
}