package com.antonchuraev.homesearchchecklist.push

/**
 * Bounded retry policy for fetching the FCM registration token after the app-start fetch failed.
 *
 * Why a retry at all: `FirebaseMessaging.getToken()` fails on a sizeable share of devices with
 * `IOException: FIS_AUTH_ERROR` (Firebase Installations could not authenticate). The API-key
 * restrictions allow `firebaseinstallations.googleapis.com`, so the failure is a device-side state
 * of GMS / Installations rather than a configuration error — the kind a later attempt can clear.
 * Without a token the user is not addressable by server pushes and `lastActiveAt` never moves.
 *
 * Why bounded: some causes are persistent (TLS interception, broken GMS). An unbounded chain would
 * wake the device forever, so the chain gives up after [MAX_ATTEMPTS] runs and reports ONE
 * non-fatal. App start runs on EVERY process start (background wakes included), and unique-work
 * KEEP does not dedupe a FINISHED chain, so after exhaustion [shouldStartChain] holds new chains
 * back for [COOLDOWN_AFTER_EXHAUSTION_MS] — at most one non-fatal + [MAX_ATTEMPTS] wakes per day.
 *
 * Pure logic so it is host-testable; [PushTokenRetryWorker] maps the decision onto WorkManager.
 */
internal object PushTokenRetryPolicy {

    /** Total worker runs per chain (WorkManager `runAttemptCount` 0 until [MAX_ATTEMPTS] - 1). */
    const val MAX_ATTEMPTS = 5

    /**
     * First backoff of the exponential chain, also used as the initial delay so the first retry
     * does not land in the same second as the failed app-start fetch. 1, 2, 4, 8 min between runs
     * -> the chain spans roughly a quarter of an hour of connected time.
     */
    const val BACKOFF_MINUTES = 1L

    enum class Next {
        /** Hand the run back to WorkManager for another exponential-backoff attempt. */
        RETRY,

        /** This was the last allowed run: stop the chain and report the failure once. */
        GIVE_UP,
    }

    /**
     * Decide what follows a failed token fetch in the run whose WorkManager `runAttemptCount`
     * is [runAttemptCount] (0 for the first run of the chain).
     */
    fun afterFailure(runAttemptCount: Int): Next =
        if (runAttemptCount + 1 >= MAX_ATTEMPTS) Next.GIVE_UP else Next.RETRY

    /** No new chain for this long after one exhausted [MAX_ATTEMPTS] without a token. */
    const val COOLDOWN_AFTER_EXHAUSTION_MS = 24L * 60 * 60 * 1000

    /** AppDatastore key: epoch ms of the last exhausted chain; absent / 0 = never exhausted. */
    const val KEY_EXHAUSTED_AT_MS = "push_token_retry_exhausted_at_ms"

    /**
     * Whether a failed app-start fetch may open a new retry chain.
     *
     * @param lastExhaustedAtMs epoch ms written when the last chain gave up, 0 if none ever did.
     * @param nowMs current wall clock.
     * A wall clock set BACK past the record (negative elapsed) counts as expired: otherwise one
     * clock jump could block the retry for as long as the jump was.
     */
    fun shouldStartChain(lastExhaustedAtMs: Long, nowMs: Long): Boolean {
        if (lastExhaustedAtMs <= 0L) return true
        val elapsed = nowMs - lastExhaustedAtMs
        return elapsed < 0L || elapsed >= COOLDOWN_AFTER_EXHAUSTION_MS
    }
}
