package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

/**
 * The ONE rule for [ChatScreenState.outOfCredits] (FEATURE_SPEC 2026-11-26 §1). Pure, so the whole
 * matrix is unit-tested without a ViewModel.
 *
 * The wallet is "empty" when either
 *  - a registered user's live balance is `<= 0` ([registered] = non-blank user id, which is also
 *    the "balance loaded" signal: before the user doc is read the balance is a default 0, not a
 *    fact, so no banner — that is the first-frame flicker guard), or
 *  - the refusal latch is set ([refusedAtBalance] = the balance at the last HTTP 402). The latch
 *    covers what the balance alone misses: a cached balance lagging the server, and a Layer 3 turn
 *    (3 credits) refused while 1–2 remain. Releasing it is the caller's job.
 *
 * Variant: web guest (not Google-linked, wasmJs only) → [OutOfCreditsBannerState.Guest];
 * Premium (`revenueCat || firestore`) → [OutOfCreditsBannerState.Premium]; otherwise
 * [OutOfCreditsBannerState.Free]. `lowBalance` = empty only by the latch while the balance is still
 * positive — "used up" would then be false.
 *
 * @param premiumDailyCreditsCap `premium_daily_credits_cap` from the server credits config; null =
 *   not loaded → the Free copy without a number. Never substituted with a client constant.
 */
internal fun resolveOutOfCreditsBanner(
    registered: Boolean,
    balance: Int,
    isPremium: Boolean,
    isWebGuest: Boolean,
    refusedAtBalance: Int?,
    premiumDailyCreditsCap: Int?,
): OutOfCreditsBannerState? {
    val empty = refusedAtBalance != null || (registered && balance <= 0)
    if (!empty) return null
    val lowBalance = balance > 0
    return when {
        isWebGuest -> OutOfCreditsBannerState.Guest
        isPremium -> OutOfCreditsBannerState.Premium(lowBalance = lowBalance)
        else -> OutOfCreditsBannerState.Free(premiumDailyCredits = premiumDailyCreditsCap, lowBalance = lowBalance)
    }
}

/**
 * The refusal latch after the live balance changed: released once the balance rises ABOVE the
 * snapshot (purchase restore, Premium refill, Google sign-in, a Firestore push). Equal or lower
 * keeps it — the server just refused at that balance.
 */
internal fun releaseRefusalLatchOnBalance(refusedAtBalance: Int?, balance: Int): Int? =
    refusedAtBalance?.takeIf { balance <= it }

/** `getPlatformName()` value of the wasmJs build — the only platform with a Guest banner. */
internal const val PLATFORM_WEB = "web"
