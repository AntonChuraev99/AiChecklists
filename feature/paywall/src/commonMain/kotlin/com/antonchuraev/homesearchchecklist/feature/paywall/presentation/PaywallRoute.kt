package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.paywall_save_percent
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import kotlin.math.round
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Format the monthly equivalent of a yearly subscription price by replacing the
 * numeric portion of the yearly priceString with the divided-by-12 amount.
 *
 * This preserves locale-specific currency formatting (symbol position, thousand
 * and decimal separators, optional trailing currency code) since RC's priceString
 * is already locale-aware. We avoid platform-specific NumberFormat (KMP common-
 * Main has no locale APIs) and don't try to rebuild formatting from scratch.
 *
 * Examples (yearlyAmount/yearlyPriceString → output):
 *   29.99    / "$29.99"        → "$2.50"
 *   10990.00 / "10 990,00 ₸"   → "915,83 ₸"
 *   29990    / "29 990 ₸"      → "2499 ₸"   (no decimals in source, no decimals in output)
 */
private fun monthlyEquivalent(yearlyPriceString: String, yearlyPriceAmount: Double): String {
    if (yearlyPriceAmount <= 0.0) return yearlyPriceString
    val monthly = yearlyPriceAmount / 12.0

    val hasDecimals = Regex("[.,]\\d{2}").containsMatchIn(yearlyPriceString)
    val decSeparator = if (Regex(",\\d{2}").containsMatchIn(yearlyPriceString)) "," else "."

    val numberStr = if (hasDecimals) {
        val cents = round(monthly * 100).toLong()
        val whole = cents / 100
        val frac = cents % 100
        "$whole$decSeparator${if (frac < 10) "0$frac" else "$frac"}"
    } else {
        round(monthly).toLong().toString()
    }

    // Match number with optional thousand-separator spaces and decimal section.
    return yearlyPriceString.replace(Regex("\\d[\\d\\s.,]*\\d|\\d"), numberStr)
}
/**
 * PaywallRoute — wires ViewModel, analytics, navigation and URI handling.
 *
 * The Screen layer is kept pure (no DI); all side-effects live here.
 */
@Composable
fun PaywallRoute(
    sourceOverride: String? = null,
    onPurchaseSuccess: () -> Unit = {},
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    val uriHandler = LocalUriHandler.current

    // Web target replaces the paywall with an "install the mobile app" CTA:
    // RevenueCat IAP isn't available in the browser. Done before VM injection
    // so we don't spin up a PaywallViewModel + offerings flow we won't use.
    if (isWebPaywallTarget) {
        val navigator: AppNavigator = koinInject()
        LaunchedEffect(Unit) {
            analyticsTracker.screenView("paywall_web_install")
        }
        WebInstallAppScreen(
            onClose = {
                analyticsTracker.event(
                    "paywall_web_install_closed",
                    emptyMap(),
                )
                navigator.onBack()
            },
            onInstallAndroidClick = {
                analyticsTracker.event(
                    "paywall_web_install_android_clicked",
                    mapOf("destination" to "google_play"),
                )
                uriHandler.openUri(GISTI_GOOGLE_PLAY_URL)
            },
        )
        return
    }

    LaunchedEffect(Unit) { analyticsTracker.screenView("paywall") }

    val viewModel: PaywallViewModel = koinViewModel { parametersOf(sourceOverride) }
    val state by viewModel.screenState.collectAsStateWithLifecycle()

    // Navigate away on purchase success (handled by ViewModel in Phase 2)
    LaunchedEffect(state.purchaseSuccess) {
        if (state.purchaseSuccess) onPurchaseSuccess()
    }

    // Rapid-tap guard: Close fires navigator.onBack(); two taps within ~50ms
    // would pop twice and escape the backstack past Main into blank.
    var closeConsumed by remember { mutableStateOf(false) }

    // Match yearly/monthly by id substring + period fallback. We had this hardcoded
    // to "premium_yearly:main-20"/"premium_monthly:monthly" which broke on Google
    // Play because RC SDK strips the basePlan suffix; periodString worked for
    // monthly but not yearly on KZ region (period field was null in practice),
    // so the substring match on id is the most reliable primary signal.
    val yearlyProduct  = state.products.find {
        it.id.contains("year", ignoreCase = true) ||
            it.id.contains("annual", ignoreCase = true) ||
            it.periodString?.contains("year") == true
    }
    val monthlyProduct = state.products.find {
        it.id.contains("month", ignoreCase = true) ||
            it.periodString?.contains("month") == true
    }

    // Compute savings % from real numeric prices (both must be > 0 and same currency)
    val yearlySavings: String? = if (
        yearlyProduct != null && monthlyProduct != null &&
        yearlyProduct.priceAmount > 0.0 && monthlyProduct.priceAmount > 0.0 &&
        (yearlyProduct.priceCurrencyCode == monthlyProduct.priceCurrencyCode ||
            yearlyProduct.priceCurrencyCode.isEmpty() || monthlyProduct.priceCurrencyCode.isEmpty())
    ) {
        val yearlyMonthlyEq = yearlyProduct.priceAmount / 12.0
        val savingsPct = ((1.0 - yearlyMonthlyEq / monthlyProduct.priceAmount) * 100).toInt()
        if (savingsPct >= 5) stringResource(Res.string.paywall_save_percent, savingsPct) else null
    } else null

    val yearlyMonthlyEq = if (yearlyProduct != null && yearlyProduct.priceAmount > 0.0) {
        monthlyEquivalent(yearlyProduct.priceString, yearlyProduct.priceAmount)
    } else PaywallUiState().yearlyMonthly

    val uiState = PaywallUiState(
        selectedPlan  = state.selectedPlan,
        variant       = state.variant,
        yearlyPrice   = yearlyProduct?.priceString  ?: PaywallUiState().yearlyPrice,
        yearlyMonthly = yearlyMonthlyEq,
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

