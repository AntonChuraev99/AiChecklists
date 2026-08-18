package com.antonchuraev.homesearchchecklist.feature.paywall.domain.premium

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.CreditsBadge
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The credits chip must SURVIVE a failing source, not merely avoid crashing on one.
 *
 * This is the difference between two one-line implementations that both look defensive.
 * `Flow.catch { emit(fallback) }` TERMINATES the flow: one throw from RevenueCat and the chip on all
 * four v2 tabs is frozen on whatever number it happened to be showing — every later credit spend,
 * purchase and restore is invisible until the process restarts, and the user reports "my credits
 * don't update". That failure is not hypothetical here: `products_load_failed` fires 743 times
 * against 355 successful catalog loads in production, i.e. the unreliable source is the NORMAL case.
 *
 * So the claims pinned below are about what happens AFTER the throw, which is exactly what a
 * "does it emit a fallback" test cannot see.
 *
 * Run:
 *   ./gradlew :feature:paywall:testAndroidHostTest --tests "*CreditsBadgeResilienceTest*"
 */
class CreditsBadgeResilienceTest {

    /**
     * The regression guard proper: a spend that happens after the failure must reach the chip.
     *
     * A terminating `catch` passes every "it emitted something" assertion and fails this one — the
     * collector simply completes and the later balance is never delivered.
     */
    @Test
    fun badge_afterASourceFails_stillDeliversLaterBalances() = runTest {
        val userData = MutableStateFlow(UserData(aiCredits = 100, isPremium = false))
        val status = FlakyStatusSource(failures = 1)
        val provider = provider(status, userData)

        val seen = mutableListOf<CreditsBadge>()
        val collector = launch { provider.badge().collect { seen += it } }
        advanceUntilIdle()

        // An AI action is taken while the stream is degraded — 20 credits at 20/action.
        userData.value = UserData(aiCredits = 80, isPremium = false)
        advanceUntilIdle()
        collector.cancel()

        assertEquals(
            80,
            seen.last().credits,
            "a spend after an upstream failure must still reach the chip; seen=$seen",
        )
    }

    /** A purchase after the failure is the same claim on the axis that costs money. */
    @Test
    fun badge_afterASourceFails_stillReflectsBecomingPremium() = runTest {
        val userData = MutableStateFlow(UserData(aiCredits = 0, isPremium = false))
        val provider = provider(FlakyStatusSource(failures = 1), userData)

        val seen = mutableListOf<CreditsBadge>()
        val collector = launch { provider.badge().collect { seen += it } }
        advanceUntilIdle()

        userData.value = UserData(aiCredits = 0, isPremium = true)
        advanceUntilIdle()
        collector.cancel()

        assertTrue(
            seen.last().isPremium,
            "a subscriber who bought while the stream was degraded must stop being offered the " +
                "paywall; seen=$seen",
        )
    }

    /**
     * Retrying must actually RE-SUBSCRIBE to the failing source, not just keep the collector alive on
     * the surviving one. Only a re-subscribe recovers the RevenueCat entitlement, and the
     * Firestore-only path cannot see it.
     */
    @Test
    fun badge_retriesTheFailingSourceUntilItRecovers() = runTest {
        val status = FlakyStatusSource(failures = 2)
        val provider = provider(status, MutableStateFlow(UserData(aiCredits = 3, isPremium = false)))

        val seen = mutableListOf<CreditsBadge>()
        val collector = launch { provider.badge().collect { seen += it } }
        advanceUntilIdle()
        collector.cancel()

        assertEquals(
            3,
            status.subscriptions,
            "two failures must be followed by a third subscription that succeeds",
        )
    }

    /**
     * While it is backing off the chip shows the last KNOWN balance rather than a stale frame or a
     * gap. `currentBadge()` reads the `UserData` StateFlow, which is the half that did not fail.
     */
    @Test
    fun badge_whileDegraded_fallsBackToTheSynchronousSeed() = runTest {
        val provider = provider(
            FlakyStatusSource(failures = 1),
            MutableStateFlow(UserData(aiCredits = 42, isPremium = false)),
        )

        val seen = mutableListOf<CreditsBadge>()
        val collector = launch { provider.badge().collect { seen += it } }
        advanceUntilIdle()
        collector.cancel()

        assertEquals(
            CreditsBadge(credits = 42, isPremium = false),
            seen.first(),
            "the first value after a failure must be the degraded seed, not nothing",
        )
    }

    /**
     * A silent degrade here is what makes the next "my credits show 0" report undiagnosable — the
     * chip looks merely stale and nothing in Crashlytics says why (CLAUDE.md: every error path goes
     * through `AppLogger.error` with the throwable, which is what triggers `recordException`).
     */
    @Test
    fun badge_logsEveryFailureWithTheThrowable() = runTest {
        val logger = RecordingLogger()
        val provider = CreditsBadgeProviderImpl(
            paywallRepository = FakePaywallRepository(FlakyStatusSource(failures = 2).flow),
            userDataRepository = FakeUserDataRepository(
                MutableStateFlow(UserData(aiCredits = 1, isPremium = false)),
            ),
            logger = logger,
        )

        val collector = launch { provider.badge().collect { } }
        advanceUntilIdle()
        collector.cancel()

        assertEquals(2, logger.errors.size, "each failure must be recorded, not just the first")
        assertTrue(
            logger.errors.all { it.second != null },
            "the throwable must be passed through — without it Crashlytics records nothing",
        )
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun provider(status: FlakyStatusSource, userData: MutableStateFlow<UserData>) =
        CreditsBadgeProviderImpl(
            paywallRepository = FakePaywallRepository(status.flow),
            userDataRepository = FakeUserDataRepository(userData),
            logger = null,
        )

    /**
     * RevenueCat as production actually behaves: the first N collections blow up, a later one works.
     *
     * Counting subscriptions is the point — it is the only way to tell "the stream recovered" from
     * "the stream kept running on the other source".
     */
    private class FlakyStatusSource(private val failures: Int) {
        var subscriptions: Int = 0
            private set

        val flow: Flow<SubscriptionStatus> = flow {
            subscriptions++
            if (subscriptions <= failures) error("products_load_failed")
            emit(SubscriptionStatus.FREE)
            // Stays open like the real repository flow, so the test observes a LIVE stream rather
            // than a completed one that could not deliver a later value anyway.
            awaitCancellation()
        }
    }

    private class RecordingLogger : AppLogger {
        val errors = mutableListOf<Pair<String, Throwable?>>()
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {
            errors += message to throwable
        }
    }

    private class FakePaywallRepository(
        override val subscriptionStatus: Flow<SubscriptionStatus>,
    ) : PaywallRepository {
        override suspend fun getOfferings(offeringId: String): Result<PaywallOffering?> =
            Result.success(null)

        override suspend fun purchase(packageId: String): PurchaseResult = PurchaseResult.Cancelled
        override suspend fun restorePurchases(): RestoreResult = RestoreResult.NoActiveSubscription
        override suspend fun refreshSubscriptionStatus() {}
        override fun isConfigured(): Boolean = true
        override suspend fun logIn(appUserId: String): Result<LoginResult> =
            Result.failure(UnsupportedOperationException())

        override suspend fun logOut(): Result<SubscriptionStatus> =
            Result.success(SubscriptionStatus.FREE)
    }

    private class FakeUserDataRepository(
        private val flow: MutableStateFlow<UserData>,
    ) : UserDataRepository {
        override fun getUserDataFlow(): StateFlow<UserData> = flow
        override suspend fun getUserData(): UserData = flow.value
        override suspend fun update(userData: UserData) {
            flow.value = userData
        }

        override suspend fun ensureUserRegistered(): Result<RegistrationData> =
            Result.failure(UnsupportedOperationException())

        override suspend fun syncWithServer(): Result<RegistrationData> =
            Result.failure(UnsupportedOperationException())

        override suspend fun isPaywallLinked(): Boolean = false
        override suspend fun setPaywallLinked(linked: Boolean) {}
        override suspend fun restoreCreditsAfterPurchase(): Result<Int> = Result.success(0)
        override suspend fun getFirstLaunchAtMillis(): Long = 0
    }
}
