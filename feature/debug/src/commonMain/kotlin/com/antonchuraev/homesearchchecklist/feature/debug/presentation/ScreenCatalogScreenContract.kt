package com.antonchuraev.homesearchchecklist.feature.debug.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State

data class ScreenCatalogState(
    val seedSummary: String = "Not seeded",
    val isPremium: Boolean = false,
    val aiCredits: Int = 0,
    val checklistCount: Int = 0,
    val lastSeededChecklistId: Long? = null,
    val lastSeededFillId: Long? = null,
    val isWorking: Boolean = false
) : State

sealed interface ScreenCatalogIntent : Intent {
    // State setup
    data object ResetToEmpty : ScreenCatalogIntent
    data object SeedWithData : ScreenCatalogIntent
    data object SeedWithFreeLimit : ScreenCatalogIntent
    data object SeedAsPremium : ScreenCatalogIntent

    // Lifecycle screens
    data object NavigateOnboarding : ScreenCatalogIntent
    data object NavigateInteractiveOnboarding : ScreenCatalogIntent
    data object NavigateMain : ScreenCatalogIntent

    // Create screens
    data object NavigateTemplates : ScreenCatalogIntent
    data object NavigateTemplatePreview : ScreenCatalogIntent
    data object NavigateCreateNew : ScreenCatalogIntent
    data object NavigateCreateEdit : ScreenCatalogIntent

    // Detail screens
    data object NavigateChecklistDetail : ScreenCatalogIntent
    data object NavigateFillDetail : ScreenCatalogIntent
    data object NavigateFillsList : ScreenCatalogIntent
    data object NavigateShareChecklist : ScreenCatalogIntent

    // AI screens
    data object NavigateAnalyzeEmpty : ScreenCatalogIntent
    data object NavigateAnalyzeForChecklist : ScreenCatalogIntent
    data object NavigateAnalyzeResultPreview : ScreenCatalogIntent

    // Monetisation screens
    data object NavigatePaywall : ScreenCatalogIntent
    data object NavigateSubscriptionStatusSuccess : ScreenCatalogIntent
    data object NavigateSubscriptionStatusPending : ScreenCatalogIntent

    // Other screens
    data object NavigateSettings : ScreenCatalogIntent
    data object NavigateUpdateFeed : ScreenCatalogIntent
    data object NavigateStoreScreenshot : ScreenCatalogIntent

    data object OnBack : ScreenCatalogIntent
}
