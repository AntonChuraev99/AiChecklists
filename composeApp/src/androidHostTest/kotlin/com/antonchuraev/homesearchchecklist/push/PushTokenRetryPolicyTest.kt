package com.antonchuraev.homesearchchecklist.push

import com.antonchuraev.homesearchchecklist.push.PushTokenRetryPolicy.Next
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks the bounded FCM-token retry chain. Before it, every app start of a device stuck in
 * `FIS_AUTH_ERROR` produced a Crashlytics non-fatal and nothing ever asked for the token again.
 */
class PushTokenRetryPolicyTest {

    @Test
    fun everyRunBeforeTheLast_retries() {
        for (runAttemptCount in 0 until PushTokenRetryPolicy.MAX_ATTEMPTS - 1) {
            assertEquals(Next.RETRY, PushTokenRetryPolicy.afterFailure(runAttemptCount), "run $runAttemptCount")
        }
    }

    @Test
    fun lastAllowedRun_givesUp() {
        assertEquals(Next.GIVE_UP, PushTokenRetryPolicy.afterFailure(PushTokenRetryPolicy.MAX_ATTEMPTS - 1))
    }

    @Test
    fun runPastTheLimit_stillGivesUp() {
        // WorkManager can report a higher count after a reschedule (e.g. process death mid-run).
        assertEquals(Next.GIVE_UP, PushTokenRetryPolicy.afterFailure(PushTokenRetryPolicy.MAX_ATTEMPTS + 3))
    }

    @Test
    fun chainIsBoundedToFiveRuns() {
        val runs = generateSequence(0) { it + 1 }
            .takeWhile { PushTokenRetryPolicy.afterFailure(it) == Next.RETRY }
            .count() + 1
        assertEquals(5, runs)
    }

    // ── Post-exhaustion cooldown: KEEP does not dedupe a FAILED chain, every start re-enqueues ──

    private val exhaustedAt = 1_760_000_000_000L
    private val day = PushTokenRetryPolicy.COOLDOWN_AFTER_EXHAUSTION_MS

    @Test
    fun neverExhausted_startsChain() {
        assertTrue(PushTokenRetryPolicy.shouldStartChain(lastExhaustedAtMs = 0L, nowMs = exhaustedAt))
    }

    @Test
    fun withinCooldown_doesNotStartChain() {
        assertFalse(PushTokenRetryPolicy.shouldStartChain(exhaustedAt, nowMs = exhaustedAt))
        assertFalse(PushTokenRetryPolicy.shouldStartChain(exhaustedAt, nowMs = exhaustedAt + day - 1))
    }

    @Test
    fun cooldownElapsed_startsChain() {
        assertTrue(PushTokenRetryPolicy.shouldStartChain(exhaustedAt, nowMs = exhaustedAt + day))
        assertTrue(PushTokenRetryPolicy.shouldStartChain(exhaustedAt, nowMs = exhaustedAt + 3 * day))
    }

    @Test
    fun clockSetBackPastRecord_startsChain() {
        // A backwards clock jump must not freeze retries for the length of the jump.
        assertTrue(PushTokenRetryPolicy.shouldStartChain(exhaustedAt, nowMs = exhaustedAt - 1))
    }

    @Test
    fun cooldownIsOneDay() {
        assertEquals(24L * 60 * 60 * 1000, PushTokenRetryPolicy.COOLDOWN_AFTER_EXHAUSTION_MS)
    }
}
