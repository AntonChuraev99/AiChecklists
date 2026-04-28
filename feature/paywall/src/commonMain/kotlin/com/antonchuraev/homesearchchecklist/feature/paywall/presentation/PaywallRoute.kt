package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
/**
 * PaywallRoute — wires ViewModel, analytics, navigation and URI handling.
 *
 * The Screen layer is kept pure (no DI); all side-effects live here.
 */
@Composable
fun PaywallRoute(
    viewModel: PaywallViewModel = koinViewModel(),
    onPurchaseSuccess: () -> Unit = {},
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView("paywall") }

    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    // Navigate away on purchase success (handled by ViewModel in Phase 2)
    LaunchedEffect(state.purchaseSuccess) {
        if (state.purchaseSuccess) onPurchaseSuccess()
    }

    // Rapid-tap guard: Close fires navigator.onBack(); two taps within ~50ms
    // would pop twice and escape the backstack past Main into blank.
    var closeConsumed by remember { mutableStateOf(false) }

    // Build UI state from MVI state — sourcing prices from loaded RevenueCat products
    val yearlyProduct  = state.products.find { it.id == "premium_yearly:main-20" }
    val monthlyProduct = state.products.find { it.id == "premium_monthly:monthly" }

    // Compute savings % from real numeric prices (both must be > 0 and same currency)
    val yearlySavings: String? = if (
        yearlyProduct != null && monthlyProduct != null &&
        yearlyProduct.priceAmount > 0.0 && monthlyProduct.priceAmount > 0.0 &&
        (yearlyProduct.priceCurrencyCode == monthlyProduct.priceCurrencyCode ||
            yearlyProduct.priceCurrencyCode.isEmpty() || monthlyProduct.priceCurrencyCode.isEmpty())
    ) {
        val yearlyMonthlyEq = yearlyProduct.priceAmount / 12.0
        val savingsPct = ((1.0 - yearlyMonthlyEq / monthlyProduct.priceAmount) * 100).toInt()
        if (savingsPct >= 5) "Save $savingsPct%" else null
    } else null

    val uiState = PaywallUiState(
        selectedPlan  = state.selectedPlan,
        variant       = state.variant,
        yearlyPrice   = yearlyProduct?.priceString  ?: PaywallUiState().yearlyPrice,
        monthlyPrice  = monthlyProduct?.priceString ?: PaywallUiState().monthlyPrice,
        trialDays     = yearlyProduct?.freeTrialDays?.takeIf { it > 0 }
            ?: monthlyProduct?.freeTrialDays?.takeIf { it > 0 }
            ?: PaywallConfig.DEFAULT_FREE_TRIAL_DAYS,
        yearlySavings = yearlySavings ?: PaywallUiState().yearlySavings,
    )

    PaywallScreen(
        state        = uiState,
        onPlanSelected = { viewModel.sendIntent(PaywallIntent.SelectPlan(it)) },
        onStartTrial = { viewModel.sendIntent(PaywallIntent.Purchase) },
        onClose      = {
            if (closeConsumed) return@PaywallScreen
            closeConsumed = true
            analyticsTracker.event("paywall_closed", mapOf("source" to state.source))
            viewModel.sendIntent(PaywallIntent.Close)
        },
        onRestore    = { viewModel.sendIntent(PaywallIntent.RestorePurchases) },
        onTermsClick = {
            analyticsTracker.event("paywall_terms_clicked", mapOf("source" to state.source))
            uriHandler.openUri(PaywallConfig.TERMS_OF_USE_URL)
        },
        onPrivacyClick = {
            analyticsTracker.event("paywall_privacy_clicked", mapOf("source" to state.source))
            uriHandler.openUri(PaywallConfig.PRIVACY_POLICY_URL)
        },
        onSupportClick = {
            analyticsTracker.event("paywall_support_clicked", mapOf("source" to state.source))
            uriHandler.openUri("mailto:${PaywallConfig.SUPPORT_EMAIL}")
        },
    )
}

