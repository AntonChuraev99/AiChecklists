package com.antonchuraev.homesearchchecklist.core.navigation.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * All navigation destinations in the app.
 *
 * Implements [NavKey] so that NavDisplay (Navigation 3) can use each route
 * directly as a back-stack entry without an additional serialization step.
 * The [Serializable] annotation is retained for argument passing consistency.
 *
 * Stage 2: sealed interface now extends NavKey directly.
 * Previous Nav 2 approach used @Serializable routes consumed by NavController;
 * Nav 3 NavDisplay renders entries based on type identity.
 */
@Serializable
sealed interface AppNavRoute : NavKey {
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
    data class ChecklistDetail(
        val checklistId: Long,
        val focusItemId: String? = null,
    ) : AppNavRoute

    @Serializable
    data class FillDetail(val fillId: Long) : AppNavRoute

    @Serializable
    data class FillsList(val checklistId: Long) : AppNavRoute

    @Serializable
    data class Paywall(
        val source: String = "unknown",
        val forceVariant: String? = null,  // "timeline" | "features" | "compare" | null (uses RC)
    ) : AppNavRoute

    @Serializable
    data class SubscriptionStatus(val showSuccessMessage: Boolean = false) : AppNavRoute

    @Serializable
    data class ShareChecklist(val checklistId: Long) : AppNavRoute

    @Serializable
    data object UpdateFeed : AppNavRoute

    @Serializable
    data object Settings : AppNavRoute

    @Serializable
    data object Today : AppNavRoute

    @Serializable
    data object Calendar : AppNavRoute

    @Serializable
    data object ScreenCatalog : AppNavRoute

    @Serializable
    data object AiChat : AppNavRoute

    @Serializable
    data object Onboardings : AppNavRoute
}
