package com.antonchuraev.homesearchchecklist.feature.paywall.domain.premium

import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator

/**
 * The one gate for "a credits / premium affordance was tapped" → which screen opens.
 *
 * A free user must reach the paywall carrying the affordance's own `source`; a premium user must
 * reach the subscription-status screen instead, because selling a subscription to a subscriber is
 * the worst outcome this branch can produce.
 *
 * ## Why an interface rather than a three-line `if` at each call site
 * Four v2 toolbars needed this branch, beside the copy that already lived in `MainScreenViewModel`.
 * A gate duplicated into N handlers drifts and fixing one site leaves the rest wrong — the shape this
 * project already paid for once (`ToolCallDispatcherImpl`'s free-tier ceiling, 2026-08-10). It is a
 * `fun interface` so a UI test can substitute a one-line recorder instead of a ~30-method
 * [AppNavigator] fake.
 */
fun interface PremiumEntryPoint {
    /**
     * @param isPremium resolved by [isPremiumUser] — the OR of RevenueCat and Firestore, never one
     *   of them alone.
     * @param source analytics attribution for the paywall. It is the ONLY way this entry point shows
     *   up in the funnel, so every call site passes its own distinct value: an affordance that opens
     *   the paywall under someone else's source is indistinguishable, in Amplitude, from an
     *   affordance that does not exist.
     */
    fun open(isPremium: Boolean, source: String)
}

/** Production [PremiumEntryPoint] — the only place the branch is written. */
class PremiumEntryPointImpl(
    private val navigator: AppNavigator,
) : PremiumEntryPoint {
    override fun open(isPremium: Boolean, source: String) {
        if (isPremium) {
            navigator.navigateToSubscriptionStatus()
        } else {
            navigator.navigateToPaywall(source = source)
        }
    }
}
