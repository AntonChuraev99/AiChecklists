package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

/**
 * In-memory, once-per-app-session cap for the post-cancel reason picker.
 *
 * Registered as a Koin `single` so the [shown] flag is shared across every [PaywallViewModel]
 * instance within one process (the paywall can be re-opened from many sources) but resets on cold
 * start. Without this gate the reason sheet would pop on EVERY cancel — annoyance + store-policy
 * risk.
 *
 * v1 is intentionally NOT persisted (DataStore) and NOT Remote-Config gated: an aggressive cap
 * would starve the sample while we collect baseline "why do people cancel" data. Persisting the cap
 * (once per N days) or an RC rollout flag is an optional v2 once the funnel has enough volume.
 *
 * Single-threaded expectation: `shouldShow()`/`markShown()` are called from the ViewModel on its
 * `viewModelScope` (main dispatcher), so a plain `var` is sufficient — no synchronization needed.
 */
class CancelReasonSessionGate {
    private var shown = false

    fun shouldShow(): Boolean = !shown

    fun markShown() {
        shown = true
    }
}
