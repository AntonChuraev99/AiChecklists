package com.antonchuraev.homesearchchecklist.feature.home.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.UserLimits

/**
 * @PreviewLightDark snapshots for [MainScreenContent] — Gisti variant-D redesign.
 *
 * Three scenarios:
 * - Free user with 3/4 lists — CalmUpgradeHint + SyncAccountBanner visible
 * - Premium user — PremiumBanner visible, no CalmUpgradeHint
 * - Empty state — EmptyState composable centered
 */
@PreviewLightDark
@Composable
private fun MainScreenContentFreeUserPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        MainScreenContent(
            screenState = previewSuccessState(),
            isEditMode = false,
            onChecklistClick = {},
            onAddChecklistClick = {},
            onAiAnalyzeClick = {},
            onPremiumBannerClick = {},
            onEnterEditMode = {},
            onExitEditMode = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun MainScreenContentPremiumUserPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        MainScreenContent(
            screenState = previewSuccessState(isPremium = true),
            isEditMode = false,
            onChecklistClick = {},
            onAddChecklistClick = {},
            onAiAnalyzeClick = {},
            onPremiumBannerClick = {},
            onEnterEditMode = {},
            onExitEditMode = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun MainScreenContentEmptyPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        MainScreenContent(
            screenState = previewSuccessState(checklists = emptyList()),
            isEditMode = false,
            onChecklistClick = {},
            onAddChecklistClick = {},
            onAiAnalyzeClick = {},
            onPremiumBannerClick = {},
            onEnterEditMode = {},
            onExitEditMode = {},
        )
    }
}

// ---------------------------------------------------------------------------
// Fake state helpers — no live data, suitable for Android Studio Preview
// ---------------------------------------------------------------------------

private fun previewSuccessState(
    isPremium: Boolean = false,
    checklists: List<ChecklistWithProgress> = previewChecklists(),
): MainScreenState.Success = MainScreenState.Success(
    checklists = checklists,
    subscriptionStatus = if (isPremium) {
        SubscriptionStatus(isActive = true, activeEntitlements = setOf("AiChecklists Pro"))
    } else {
        SubscriptionStatus.FREE
    },
    aiCredits = if (isPremium) 300 else 8,
    userLimits = UserLimits(
        maxChecklists = 4,
        maxFillsPerChecklist = 5,
        currentChecklistCount = checklists.size,
        isPremium = isPremium,
    ),
    isGoogleLinked = false,
    // Mirror the ViewModel's derived condition so the free-user preview keeps showing the banner:
    // not linked + more than one checklist (a brand-new single-list user wouldn't see it).
    showSyncBanner = checklists.size > 1,
)

private fun previewChecklists(): List<ChecklistWithProgress> = listOf(
    ChecklistWithProgress(
        checklist = Checklist(id = 1L, name = "Groceries", items = emptyList()),
        totalItems = 8,
        checkedItems = 2,
    ),
    ChecklistWithProgress(
        checklist = Checklist(id = 2L, name = "Paris trip packing", items = emptyList()),
        totalItems = 12,
        checkedItems = 12,
    ),
    ChecklistWithProgress(
        checklist = Checklist(id = 3L, name = "Work tasks for the week", items = emptyList()),
        totalItems = 5,
        checkedItems = 1,
    ),
    ChecklistWithProgress(
        checklist = Checklist(id = 4L, name = "Morning routine", items = emptyList()),
        totalItems = 0,
        checkedItems = 0,
    ),
)
