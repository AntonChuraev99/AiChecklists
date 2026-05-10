package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct

// ─── A/B Variant ──────────────────────────────────────────────────────────────

/**
 * Which paywall layout to show. Assigned via Remote Config in Phase 2.
 * Defaults to Timeline (current proven layout).
 */
enum class PaywallVariant { Timeline, Features, Compare }

// ─── Plan selection ───────────────────────────────────────────────────────────

/**
 * Which billing period the user has selected in the plan picker.
 * Phase 2 will map these to RevenueCat product IDs.
 */
enum class PaywallPlan { Yearly, Monthly }

// ─── UI State ─────────────────────────────────────────────────────────────────

/**
 * Display-only pricing strings — filled from RevenueCat offerings in Phase 2.
 * Safe defaults shown until real prices load.
 *
 * Defaults match production prices: $20/yr, $1.99/mo.
 * yearlySavings = 1 - (20 / (1.99 * 12)) = 1 - 0.838 ≈ 16%
 */
data class PaywallUiState(
    val yearlyPrice: String        = "$20",
    /** Monthly equivalent of yearly price (formatted, no period suffix). Combined
     *  with localized "/mo · billed annually" string in the UI layer. */
    val yearlyMonthly: String      = "$1.67",
    /** Nullable — null means savings badge is hidden (e.g. yearly is not actually cheaper). */
    val yearlySavings: String?     = "Save 16%",
    val monthlyPrice: String       = "$1.99",
    val trialDays: Int             = 3,
    val ctaSubtext: String         = "Then $20/year. Cancel anytime.",
    val selectedPlan: PaywallPlan  = PaywallPlan.Monthly,
    val variant: PaywallVariant    = PaywallVariant.Timeline,
)

// ─── MVI State (ViewModel ↔ RevenueCat) ───────────────────────────────────────

data class PaywallState(
    val isLoading: Boolean = true,
    val isPurchasing: Boolean = false,
    val products: List<PaywallProduct> = emptyList(),
    val selectedProductId: String? = null,
    val error: String? = null,
    val purchaseSuccess: Boolean = false,
    val source: String = "unknown",
    // A/B test fields — populated from Remote Config
    val selectedPlan: PaywallPlan = PaywallPlan.Monthly,
    val variant: PaywallVariant   = PaywallVariant.Timeline,
) : State

// ─── MVI Intent ───────────────────────────────────────────────────────────────

sealed interface PaywallIntent : Intent {
    data object LoadProducts : PaywallIntent
    data class SelectProduct(val productId: String) : PaywallIntent
    data object Purchase : PaywallIntent
    data object RestorePurchases : PaywallIntent
    data object DismissError : PaywallIntent
    data object Close : PaywallIntent
    // Phase 2: handled by ViewModel after RemoteConfig integration
    data class SelectPlan(val plan: PaywallPlan) : PaywallIntent
}
