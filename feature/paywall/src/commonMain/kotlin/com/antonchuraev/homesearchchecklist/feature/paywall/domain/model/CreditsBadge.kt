package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

/**
 * Everything a credits affordance needs to render and to route its own tap.
 *
 * Two fields rather than two separate reads because they are ALWAYS used together and must agree:
 * `credits = 0` renders the "Get More" upsell only when [isPremium] is false, and the same
 * [isPremium] then decides whether the tap opens the paywall or the subscription-status screen. Two
 * independently-collected flows would let those two disagree for a frame and send a subscriber to
 * the paywall.
 *
 * @param credits  AI credit balance. Server truth, mirrored into DataStore by the Firestore listener.
 * @param isPremium Resolved by [com.antonchuraev.homesearchchecklist.feature.paywall.domain.premium.isPremiumUser]
 *   — RevenueCat entitlement OR the Firestore flag, never one of the two alone.
 */
data class CreditsBadge(
    val credits: Int,
    val isPremium: Boolean,
) {
    companion object {
        /**
         * Pre-resolution value. Deliberately the free/zero state: it is only ever visible if BOTH
         * sources fail, and in that case offering the upsell is the safe error — the alternative
         * (claiming premium) hides the app's only paywall entry point behind a wrong guess.
         */
        val EMPTY = CreditsBadge(credits = 0, isPremium = false)
    }
}
