package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Production symptom this file reproduces:
 *
 * `state.products.isEmpty()` means TWO different things — "the catalog has not arrived yet" and
 * "the catalog arrived and is empty" — and the paywall treats both as "there is a trial". Every
 * price then falls back to a hardcoded [PaywallUiState] default. With ~68% of catalog loads failing
 * in production (`products_load_failed` 743 vs `products_load_success` 355) the user is regularly
 * shown a plausible "$20 / $1.99", a "Save 16%" badge and "Start your 3-day free trial" over a dead
 * catalog — taps it, and gets a connection error instead of a purchase.
 *
 * The derivation lives inline in the `PaywallRoute` @Composable today, which no unit test can
 * reach; [buildPaywallUiState] is the seam it has to move to.
 */
class PaywallUiStateMapperTest {

    private val yearly = PaywallProduct(
        id = "premium_yearly",
        title = "Premium Yearly",
        description = "Premium Yearly",
        priceString = "$29.99",
        priceAmount = 29.99,
        priceCurrencyCode = "USD",
        periodString = "1 year",
        packageId = "\$rc_annual",
        hasFreeTrial = true,
        freeTrialDays = 7,
    )

    private val monthly = PaywallProduct(
        id = "premium_monthly",
        title = "Premium Monthly",
        description = "Premium Monthly",
        priceString = "$2.99",
        priceAmount = 2.99,
        priceCurrencyCode = "USD",
        periodString = "1 month",
        packageId = "\$rc_monthly",
        hasFreeTrial = true,
        freeTrialDays = 7,
    )

    @Test
    fun buildPaywallUiState_emptyCatalogAfterLoadingFinished_promisesNoTrialAndNoInventedPrice() {
        // ── The defect: loading is OVER and nothing came back. ────────────────────────────────
        val soldOut = buildPaywallUiState(
            state = PaywallState(isLoading = false, products = emptyList()),
            savingsLabel = null,
        )

        assertFalse(
            soldOut.hasFreeTrial,
            "the catalog finished loading with no products, so nothing grants a trial — the " +
                "screen must not promise one (prod: 'Start your 3-day free trial' rendered over " +
                "a catalog that failed to load)",
        )
        assertFalse(
            soldOut.yearlyPrice.any { it.isDigit() },
            "no product loaded means no yearly price is known; any number here is invented — " +
                "prod falls back to '${PaywallUiState().yearlyPrice}', was '${soldOut.yearlyPrice}'",
        )
        assertFalse(
            soldOut.monthlyPrice.any { it.isDigit() },
            "no product loaded means no monthly price is known; any number here is invented — " +
                "prod falls back to '${PaywallUiState().monthlyPrice}', was " +
                "'${soldOut.monthlyPrice}'",
        )
        assertFalse(
            soldOut.yearlyMonthly.any { it.isDigit() },
            "the per-month equivalent is derived from a yearly price that does not exist here — " +
                "prod falls back to '${PaywallUiState().yearlyMonthly}', was " +
                "'${soldOut.yearlyMonthly}'",
        )
        assertNull(
            soldOut.yearlySavings,
            "a 'Save N%' badge with no products behind it is a claim about two prices the app " +
                "does not have — prod falls back to '${PaywallUiState().yearlySavings}'",
        )

        // ── Discriminator 1: the empty catalog only means "no offer" once loading has finished.
        // Hardcoding hasFreeTrial = false would satisfy the assert above and bring back the
        // no-trial copy flash the optimistic default was added for.
        val stillLoading = buildPaywallUiState(
            state = PaywallState(isLoading = true, products = emptyList()),
            savingsLabel = null,
        )
        assertTrue(
            stillLoading.hasFreeTrial,
            "while the catalog is still in flight the common (trial) offering stays assumed, so " +
                "the trial copy does not flash to no-trial and back",
        )

        // ── Discriminator 2: a loaded catalog must still drive real numbers. Blanking every price
        // unconditionally would satisfy the asserts above and ship an empty paywall.
        val loaded = buildPaywallUiState(
            state = PaywallState(isLoading = false, products = listOf(yearly, monthly)),
            savingsLabel = "Save 16%",
        )
        assertEquals("$29.99", loaded.yearlyPrice, "real yearly price comes from the product")
        assertEquals("$2.99", loaded.monthlyPrice, "real monthly price comes from the product")
        assertEquals(
            "$2.50",
            loaded.yearlyMonthly,
            "per-month equivalent of $29.99/year, keeping the product's own currency formatting",
        )
        assertTrue(loaded.hasFreeTrial, "both loaded products carry a real free trial")
        assertEquals(7, loaded.trialDays, "trial length comes from the product, not from the 3-day default")
        assertEquals("Save 16%", loaded.yearlySavings, "the caller-resolved badge is passed through")
    }
}
