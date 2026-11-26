package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The full matrix of [resolveOutOfCreditsBanner] + the latch release rule (FEATURE_SPEC 2026-11-26 §1-2). */
class OutOfCreditsBannerResolverTest {

    private fun resolve(
        registered: Boolean = true,
        balance: Int = 0,
        isPremium: Boolean = false,
        isWebGuest: Boolean = false,
        refusedAtBalance: Int? = null,
        cap: Int? = 777,
    ) = resolveOutOfCreditsBanner(registered, balance, isPremium, isWebGuest, refusedAtBalance, cap)

    @Test
    fun creditsLeftAndNoRefusal_noBanner() {
        assertNull(resolve(balance = 1))
        assertNull(resolve(balance = 300, isPremium = true))
    }

    @Test
    fun unregistered_zeroBalanceIsNotAFact_noBanner() {
        assertNull(resolve(registered = false, balance = 0))
    }

    @Test
    fun unregistered_butRefused_showsTheBanner() {
        // The server refused (402) before the user doc was read — the refusal itself is the fact.
        assertEquals(OutOfCreditsBannerState.Free(777), resolve(registered = false, balance = 0, refusedAtBalance = 0))
    }

    @Test
    fun emptyWallet_freeUser_carriesTheConfiguredCap() {
        assertEquals(OutOfCreditsBannerState.Free(premiumDailyCredits = 777), resolve(balance = 0))
        assertEquals(OutOfCreditsBannerState.Free(premiumDailyCredits = 777), resolve(balance = -3))
    }

    @Test
    fun configNotLoaded_freeBannerWithoutANumber() {
        assertEquals(OutOfCreditsBannerState.Free(premiumDailyCredits = null), resolve(cap = null))
    }

    @Test
    fun refusedWithCreditsLeft_isLowBalance() {
        assertEquals(
            OutOfCreditsBannerState.Free(premiumDailyCredits = 777, lowBalance = true),
            resolve(balance = 2, refusedAtBalance = 2),
        )
        assertEquals(OutOfCreditsBannerState.Premium(lowBalance = true), resolve(balance = 2, isPremium = true, refusedAtBalance = 2))
    }

    @Test
    fun premium_emptyWallet_noUpsell() {
        assertEquals(OutOfCreditsBannerState.Premium(lowBalance = false), resolve(balance = 0, isPremium = true))
    }

    @Test
    fun webGuest_winsOverPremiumAndFree() {
        assertEquals(OutOfCreditsBannerState.Guest, resolve(balance = 0, isWebGuest = true))
        assertEquals(OutOfCreditsBannerState.Guest, resolve(balance = 0, isWebGuest = true, isPremium = true))
    }

    @Test
    fun webGuest_withCredits_noBanner() {
        assertNull(resolve(balance = 50, isWebGuest = true))
    }

    @Test
    fun latch_releasedOnlyByABalanceAboveTheSnapshot() {
        assertNull(releaseRefusalLatchOnBalance(refusedAtBalance = null, balance = 0))
        assertEquals(2, releaseRefusalLatchOnBalance(refusedAtBalance = 2, balance = 2))
        assertEquals(2, releaseRefusalLatchOnBalance(refusedAtBalance = 2, balance = 1))
        assertNull(releaseRefusalLatchOnBalance(refusedAtBalance = 2, balance = 3))
    }

    @Test
    fun analyticsVariants_areTheWireValues() {
        assertEquals("free", OutOfCreditsBannerState.Free(null).analyticsVariant)
        assertEquals("premium", OutOfCreditsBannerState.Premium().analyticsVariant)
        assertEquals("guest", OutOfCreditsBannerState.Guest.analyticsVariant)
    }
}
