package com.antonchuraev.homesearchchecklist.feature.paywall.domain.premium

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.CreditsBadge
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.retryWhen
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val TAG = "CreditsBadge"

/**
 * Live credit balance + premium state for any always-on credits affordance.
 *
 * A read-only projection over the two repositories that already own the data — it holds no state of
 * its own, so several chips on several screens observing it can never disagree.
 *
 * An interface (not just the impl) so that a Compose test of a screen that hosts the chip can supply
 * a two-line fake instead of standing up a `PaywallRepository` and a `UserDataRepository`.
 */
interface CreditsBadgeProvider {

    /**
     * Re-emits on every credit spend and every subscription change — for as long as it is collected.
     *
     * Never throws: a failing source is logged, degraded to the last known balance, and RETRIED. This
     * flow is collected inside composition, where an exception takes the whole screen down — and the
     * screen it takes down is the tab the user is standing on, for a decorative chip.
     *
     * "Retried" is the load-bearing half. A stream that survives one throw by emitting a fallback and
     * then COMPLETING is worse than a crash: the chip on all four v2 tabs freezes on that value and
     * every later spend, purchase and restore is silently invisible until the process restarts.
     */
    fun badge(): Flow<CreditsBadge>

    /**
     * Synchronous value for the first composed frame.
     *
     * [badge] combines a COLD flow (`subscriptionStatus`) with a StateFlow, so it has emitted
     * nothing at the moment the chip first composes. Seeding from [CreditsBadge.EMPTY] instead would
     * flash the zero-credit "Get More" CTA on every tab open at users who have credits.
     */
    fun currentBadge(): CreditsBadge
}

/**
 * @param logger nullable so lightweight tests need not register one; production always has it. A
 *   silent degrade here would make the next "my credits show 0" report undiagnosable.
 */
class CreditsBadgeProviderImpl(
    private val paywallRepository: PaywallRepository,
    private val userDataRepository: UserDataRepository,
    private val logger: AppLogger?,
) : CreditsBadgeProvider {

    override fun badge(): Flow<CreditsBadge> = combine(
        paywallRepository.subscriptionStatus,
        userDataRepository.getUserDataFlow(),
    ) { status, userData ->
        CreditsBadge(
            credits = userData.aiCredits,
            isPremium = isPremiumUser(status, userData.isPremium),
        )
    }.retryWhen { cause, attempt ->
        // Cancellation is not a failure — retrying it would resurrect a stream whose screen is gone.
        if (cause is CancellationException) return@retryWhen false
        logger?.error(TAG, "credits_badge_stream_failed (attempt $attempt): ${cause.message}", cause)
        // Show the last KNOWN balance while backing off, so the chip degrades to something true
        // instead of holding the frame the failure happened to land on. `currentBadge()` reads the
        // `UserData` StateFlow, i.e. the half that did not fail — on the common failure (RevenueCat
        // catalog load) the credit count is still exactly right.
        emit(currentBadge())
        delay(retryBackoff(attempt))
        // ALWAYS retry. There is no "give up" state that makes sense for this stream: it lives
        // exactly as long as a visible chip, so giving up means a permanently wrong number on screen
        // with no path back short of a process restart. The subscription re-runs the failing source,
        // which is the only thing that can recover a RevenueCat entitlement.
        true
    }

    /**
     * Reads `UserData` only. It is the StateFlow of the pair and it carries BOTH fields the badge
     * needs, including the Firestore premium flag — so the seed is already correct for a premium
     * user whose RevenueCat status has not arrived (or never will, on a broken catalog load).
     */
    override fun currentBadge(): CreditsBadge {
        val userData = userDataRepository.getUserDataFlow().value
        return CreditsBadge(credits = userData.aiCredits, isPremium = userData.isPremium)
    }
}

/**
 * Exponential, capped — 1s, 2s, 4s … 32s.
 *
 * Bounded because the failing source is usually the RevenueCat catalog, which fails for reasons that
 * clear on their own (no Play billing yet, no regional price, transient network): a fixed tight
 * interval would spin a retry loop behind a chip nobody is looking at, and an unbounded backoff would
 * eventually stop reacting to a recovery within the life of a session.
 */
private fun retryBackoff(attempt: Long): Duration {
    val seconds = 1L shl attempt.coerceAtMost(MaxBackoffShift).toInt()
    return seconds.seconds
}

/** 2^5 = 32s, clamped by the shift itself rather than by a second constant that could disagree. */
private const val MaxBackoffShift = 5L
