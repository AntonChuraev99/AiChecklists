package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

/**
 * The out-of-credits banner pinned above the AI chat input (both surfaces: the full-screen
 * [ChatScreen] and the v2 shell dock in `App.kt`). Replaces the refusal bubble with a "Become Pro"
 * CTA — a refused turn no longer writes an assistant message at all; this banner IS the answer.
 *
 * Exposed as [ChatScreenState.outOfCredits]; null = the wallet is not known to be empty, render
 * nothing. Derived in the ViewModel, never latched in the UI:
 *
 *  - **empty** = `(registered && creditBalance <= 0) || refusal latch`. The refusal latch is the
 *    balance snapshot taken at the last HTTP 402; it is released as soon as the live balance rises
 *    above that snapshot (purchase restore, Premium daily refill, Google sign-in on web, a credit
 *    pushed by the Firestore listener) or a later turn is answered (the server charged → the wallet
 *    works). The latch covers the cases the balance alone misses: a cached balance that lags the
 *    server, and a Layer 3 turn (3 credits) refused while 1–2 credits remain.
 *  - The banner never blocks Send: the server stays the gate, a refusal costs 0 credits, and a stale
 *    "0" on the client must not lock out a user who actually has credits.
 *
 * Numbers come from the server credits config (`get_credits_info` → `premium_daily_credits_cap`,
 * via `CreditsConfigProvider`), never a literal and never Firebase RC `ai_daily_limit_premium`.
 * Null = config not fetched yet / fetch failed → the copy without a number, never a made-up one.
 */
sealed interface OutOfCreditsBannerState {

    /**
     * Free user, wallet empty (or too low for the refused turn), no refill. CTA "Get Pro" → paywall
     * (`source = chat_insufficient_credits`, wire value kept for Amplitude continuity).
     *
     * @param premiumDailyCredits What Pro refills to every day (`premium_daily_credits_cap`), shown
     *   in the body ("Pro gives you up to N a day"); null → the body without a number.
     * @param lowBalance The server refused a turn while the balance is still > 0 (e.g. 2 credits
     *   left, a Layer 3 turn costs 3) → title "Not enough AI credits" instead of "used up".
     */
    data class Free(
        val premiumDailyCredits: Int?,
        val lowBalance: Boolean = false,
    ) : OutOfCreditsBannerState

    /**
     * Premium user who spent today's credits. Informational only — no upsell, no CTA: the balance
     * refills on the server's daily schedule (body = `credits_refill_info`, no number).
     *
     * @param lowBalance Refused while the balance is still > 0 → "Not enough AI credits" title.
     */
    data class Premium(val lowBalance: Boolean = false) : OutOfCreditsBannerState

    /**
     * Web (wasmJs) visitor not signed in with Google: 0 credits until the sign-in grants the starter
     * pack. CTA "Sign in with Google" → the host's existing sign-in flow. Never on Android/iOS — a
     * non-linked Android user already got the starter pack at install, so signing in grants nothing.
     */
    data object Guest : OutOfCreditsBannerState
}

/** Analytics `variant` of the banner (`ai_chat_out_of_credits_banner_shown` / `_cta_tapped`). */
val OutOfCreditsBannerState.analyticsVariant: String
    get() = when (this) {
        is OutOfCreditsBannerState.Free -> "free"
        is OutOfCreditsBannerState.Premium -> "premium"
        OutOfCreditsBannerState.Guest -> "guest"
    }
