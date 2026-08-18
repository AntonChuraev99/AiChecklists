package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct
import kotlin.math.round

/**
 * Shown in place of a price the app does not have. Typography, not copy: a language-neutral
 * placeholder (same class as the "·" separator), so it needs no localization. It exists to keep the
 * paywall from inventing a number — see [buildPaywallUiState].
 *
 * The user-facing explanation of *why* there is no price is NOT this dash: the load failure already
 * raises the localized `paywall_load_error` snackbar (PaywallViewModel.handleEmptyProducts), and a
 * tap on the CTA raises it again. A dedicated "offer unavailable" screen state with its own copy is
 * the follow-up task.
 */
private const val PRICE_UNAVAILABLE = "—"

/**
 * Maps the RevenueCat-backed [PaywallState] onto the display-only [PaywallUiState].
 *
 * The one distinction this mapper exists to make: an empty [PaywallState.products] means TWO
 * different things, and the paywall used to treat both as "there is a trial at the default price".
 *
 *  - `isLoading && products.isEmpty()` — the catalog has not arrived **yet**. Nothing is known, so
 *    the common (trial) offering stays optimistically assumed and the [PaywallUiState] defaults
 *    stand in, exactly as before. This keeps the trial copy from flashing no-trial and back.
 *  - `!isLoading && products.isEmpty()` — the catalog arrived (or failed) and there is no offer.
 *    Every claim about the offer must now be dropped: no trial promise, no price, no savings badge.
 *
 * Production symptom that made the split necessary: with ~68% of catalog loads failing
 * (`products_load_failed` 743 vs `products_load_success` 355) users were regularly shown a plausible
 * "$20 / $1.99", a "Save 16%" badge and "Start your 3-day free trial" over a dead catalog — they
 * tapped, and got a connection error instead of a purchase.
 *
 * Pure by design (no Compose, no suspend) so it is unit-testable: the same derivation used to live
 * inline in the `PaywallRoute` @Composable, where no test could reach it.
 *
 * @param state the ViewModel state — [PaywallState.products] plus [PaywallState.isLoading], which
 *   together tell "catalog not fetched yet" apart from "catalog fetched and empty".
 * @param savingsLabel already-localized "Save N%" badge, or null when no badge should be shown.
 *   Resolved by the caller (from [yearlySavingsPercent]) because `stringResource` is
 *   @Composable-only and this mapper must stay pure.
 */
internal fun buildPaywallUiState(
    state: PaywallState,
    savingsLabel: String?,
): PaywallUiState {
    // The ONLY place an empty product list is still allowed to mean "unknown" rather than "none".
    val catalogPending = state.isLoading && state.products.isEmpty()
    val defaults = PaywallUiState()

    val yearlyProduct = state.products.yearly()
    val monthlyProduct = state.products.monthly()

    // Real trial length from RevenueCat — 0 means no free trial for this user/offering (e.g. the
    // *NoTrial offering, or a trial-ineligible user). Trial state is resolved per platform in the
    // repository (Android: subscriptionOptions.freeTrial; iOS: introductoryDiscount).
    val resolvedTrialDays = yearlyProduct?.freeTrialDays?.takeIf { it > 0 }
        ?: monthlyProduct?.freeTrialDays?.takeIf { it > 0 }
        ?: 0

    return PaywallUiState(
        selectedPlan = state.selectedPlan,
        variant = state.variant,
        yearlyPrice = yearlyProduct?.priceString
            ?: unknownPrice(catalogPending, defaults.yearlyPrice),
        yearlyMonthly = yearlyProduct
            ?.takeIf { it.priceAmount > 0.0 }
            ?.let { monthlyEquivalent(it.priceString, it.priceAmount) }
            ?: unknownPrice(catalogPending, defaults.yearlyMonthly),
        monthlyPrice = monthlyProduct?.priceString
            ?: unknownPrice(catalogPending, defaults.monthlyPrice),
        trialDays = resolvedTrialDays.takeIf { it > 0 } ?: PaywallConfig.DEFAULT_FREE_TRIAL_DAYS,
        hasFreeTrial = catalogPending || resolvedTrialDays > 0,
        // No fallback: a "Save N%" badge is a claim about two prices. No products behind it, no
        // badge — the caller returns null whenever the percentage cannot be computed from real ones.
        yearlySavings = savingsLabel,
    )
}

/**
 * Savings of the yearly plan over 12× monthly, as a whole percentage, or null when it cannot be
 * claimed: a plan missing, a non-positive price, a cross-currency comparison, or a difference too
 * small (< 5%) to be worth a badge.
 *
 * Split out of [buildPaywallUiState] because the caller has to turn the number into localized copy
 * (`paywall_save_percent`) and `stringResource` is @Composable-only — the arithmetic stays here,
 * where it is testable.
 */
internal fun yearlySavingsPercent(state: PaywallState): Int? {
    val yearly = state.products.yearly() ?: return null
    val monthly = state.products.monthly() ?: return null
    if (yearly.priceAmount <= 0.0 || monthly.priceAmount <= 0.0) return null

    // An empty currency code means the store did not report one — not a mismatch.
    val comparableCurrency = yearly.priceCurrencyCode == monthly.priceCurrencyCode ||
        yearly.priceCurrencyCode.isEmpty() ||
        monthly.priceCurrencyCode.isEmpty()
    if (!comparableCurrency) return null

    val yearlyPerMonth = yearly.priceAmount / 12.0
    val savingsPct = ((1.0 - yearlyPerMonth / monthly.priceAmount) * 100).toInt()
    return savingsPct.takeIf { it >= 5 }
}

/**
 * While the catalog is in flight the optimistic default stands in (unchanged loading appearance);
 * once loading is over with nothing to show, any number would be invented.
 */
private fun unknownPrice(catalogPending: Boolean, whileLoading: String): String =
    if (catalogPending) whileLoading else PRICE_UNAVAILABLE

// Match yearly/monthly by id substring + period fallback. We had this hardcoded to
// "premium_yearly:main-20"/"premium_monthly:monthly" which broke on Google Play because the RC SDK
// strips the basePlan suffix; periodString worked for monthly but not yearly on the KZ region (the
// period field was null in practice), so the substring match on id is the most reliable signal.
private fun List<PaywallProduct>.yearly(): PaywallProduct? = find {
    it.id.contains("year", ignoreCase = true) ||
        it.id.contains("annual", ignoreCase = true) ||
        it.periodString?.contains("year") == true
}

private fun List<PaywallProduct>.monthly(): PaywallProduct? = find {
    it.id.contains("month", ignoreCase = true) ||
        it.periodString?.contains("month") == true
}

/**
 * Format the monthly equivalent of a yearly subscription price by replacing the numeric portion of
 * the yearly priceString with the divided-by-12 amount.
 *
 * This preserves locale-specific currency formatting (symbol position, thousand and decimal
 * separators, optional trailing currency code) since RC's priceString is already locale-aware. We
 * avoid platform-specific NumberFormat (KMP commonMain has no locale APIs) and don't try to rebuild
 * formatting from scratch.
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
