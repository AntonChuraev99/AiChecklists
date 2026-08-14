package com.antonchuraev.homesearchchecklist.feature.paywall.domain.premium

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.common.api.AiEntrySource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute
import com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.CreditsBadge
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.Entitlements
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two collaborators every credits affordance in the app is built out of.
 *
 * [PremiumEntryPoint] is THE gate "a credits/premium affordance was tapped → which screen opens".
 * It exists as one object because the same three-line `if (isPremium) … else …` was about to be
 * pasted into four v2 toolbars beside the copy that already lives in `MainScreenViewModel`, and this
 * project has already paid for that shape once: a free-tier ceiling copy-pasted into two handlers
 * drifted, and fixing one site left the other wrong (`ToolCallDispatcherImpl` 2026-08-10).
 *
 * [CreditsBadgeProvider] is the matching read side. Its whole job is the `revenueCat || firestore`
 * rule from CLAUDE.md — getting it wrong sends a PAYING subscriber to the paywall.
 *
 * Run:
 *   ./gradlew :feature:paywall:testAndroidHostTest --tests "*CreditsEntryPointTest*"
 */
class CreditsEntryPointTest {

    // ── PremiumEntryPoint: which destination ─────────────────────────────────

    @Test
    fun entryPoint_forAFreeUser_opensThePaywallCarryingTheCallerSource() {
        val navigator = RecordingNavigator()

        PremiumEntryPointImpl(navigator).open(isPremium = false, source = "v2_inbox_credits_chip")

        assertEquals("v2_inbox_credits_chip", navigator.paywallSource)
        // The whole point of the source is measurement: an entry point that opened the paywall with
        // someone else's source would look, in Amplitude, exactly like an entry point that is absent.
        assertEquals(0, navigator.subscriptionStatusCalls)
    }

    @Test
    fun entryPoint_forAPremiumUser_opensSubscriptionStatus_neverThePaywall() {
        val navigator = RecordingNavigator()

        PremiumEntryPointImpl(navigator).open(isPremium = true, source = "v2_inbox_credits_chip")

        assertEquals(1, navigator.subscriptionStatusCalls)
        // Selling a subscription to someone who already pays is the regression this branch prevents.
        assertNull(navigator.paywallSource)
    }

    // ── CreditsBadgeProvider: premium is an OR of two independent truths ──────

    @Test
    fun badge_whenOnlyRevenueCatSaysPremium_isPremium() = runTest {
        val provider = provider(
            status = SubscriptionStatus(
                isActive = true,
                activeEntitlements = setOf(Entitlements.PREMIUM),
            ),
            userData = UserData(aiCredits = 0, isPremium = false),
        )

        assertTrue(provider.badge().first().isPremium)
    }

    /**
     * The half that matters most here. `products_load_failed` fires on ~68% of catalog loads in
     * production, so RevenueCat is the LESS reliable of the two signals: a Firestore-premium user
     * whose RevenueCat session is broken must still be recognised, or the chip offers "Get More" to
     * someone who already pays.
     */
    @Test
    fun badge_whenOnlyFirestoreSaysPremium_isStillPremium() = runTest {
        val provider = provider(
            status = SubscriptionStatus.FREE,
            userData = UserData(aiCredits = 0, isPremium = true),
        )

        assertTrue(provider.badge().first().isPremium)
    }

    /**
     * `isActive` is NOT the entitlement. `PaywallRepositoryImpl` sets `isActive = isPremium ||
     * activeEntitlements.isNotEmpty()`, so a status can be active on some OTHER entitlement; the
     * premium rule the rest of the app uses (`GetUserLimitsUseCase`) reads the entitlement set.
     */
    @Test
    fun badge_whenTheActiveEntitlementIsNotPremium_isNotPremium() = runTest {
        val provider = provider(
            status = SubscriptionStatus(isActive = true, activeEntitlements = setOf("something_else")),
            userData = UserData(aiCredits = 4, isPremium = false),
        )

        assertEquals(CreditsBadge(credits = 4, isPremium = false), provider.badge().first())
    }

    @Test
    fun badge_carriesTheLiveCreditBalance() = runTest {
        val provider = provider(
            status = SubscriptionStatus.FREE,
            userData = UserData(aiCredits = 7, isPremium = false),
        )

        assertEquals(7, provider.badge().first().credits)
    }

    /**
     * The chip is composed on the first frame of a tab, and `subscriptionStatus` is a cold flow that
     * has not emitted yet at that point. Without a synchronous seed every tab open flashes the
     * "Get More" CTA at a user who has credits — the chip's zero-credit state.
     */
    @Test
    fun currentBadge_isReadableSynchronously_fromTheUserDataStateFlow() {
        val provider = provider(
            status = SubscriptionStatus.FREE,
            userData = UserData(aiCredits = 12, isPremium = true),
        )

        assertEquals(CreditsBadge(credits = 12, isPremium = true), provider.currentBadge())
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun provider(status: SubscriptionStatus, userData: UserData) = CreditsBadgeProviderImpl(
        paywallRepository = FakePaywallRepository(status),
        userDataRepository = FakeUserDataRepository(userData),
        logger = null,
    )

    private class FakePaywallRepository(status: SubscriptionStatus) : PaywallRepository {
        override val subscriptionStatus: Flow<SubscriptionStatus> = MutableStateFlow(status)
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

    private class FakeUserDataRepository(userData: UserData) : UserDataRepository {
        private val flow = MutableStateFlow(userData)
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

    private class RecordingNavigator : AppNavigator {
        var paywallSource: String? = null
        var subscriptionStatusCalls: Int = 0

        override val backStack: NavBackStack<NavKey> = NavBackStack()
        private val _events = MutableSharedFlow<AppNavEvent>()
        override val events: SharedFlow<AppNavEvent> = _events.asSharedFlow()

        override fun navigateToPaywall(source: String) {
            paywallSource = source
        }

        override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) {
            subscriptionStatusCalls++
        }

        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
        override fun onBack() {}
        override fun setDefaultRootRoute(route: AppNavRoute) {}
        override fun navigateToOnboarding() {}
        override fun navigateToInteractiveOnboarding() {}
        override fun navigateToWelcomeOnboarding() {}
        override fun navigateToMainScreen(clearBackStack: Boolean) {}
        override fun navigateToDebugMenu() {}
        override fun navigateToStoreScreenshot() {}
        override fun navigateToCreateChecklistScreen(templateId: Int?, initialText: String?) {}
        override fun navigateToEditChecklist(checklistId: Long) {}
        override fun navigateToTemplatesScreen() {}
        override fun navigateToTemplatePreview(templateId: String) {}
        override fun navigateToAnalyzeWithInput(
            inputKind: AnalyzeInputKind,
            entrySource: AiEntrySource,
        ) = Unit

        override fun navigateToAnalyzeScreen(
            checklistId: Long?,
            fillDefault: Boolean,
            initialText: String?,
            autoAnalyze: Boolean,
        ) {}

        override fun navigateToAnalyzeResultPreview() {}
        override fun navigateToChecklistDetail(
            checklistId: Long,
            focusItemId: String?,
            clearBackStack: Boolean,
        ) {}

        override fun navigateToFillDetail(fillId: Long, clearBackStack: Boolean) {}
        override fun navigateToFillsList(checklistId: Long) {}
        override fun navigateToPaywallVariant(source: String, forceVariant: String) {}
        override fun navigateToShareChecklist(checklistId: Long) {}
        override fun navigateToUpdateFeed() {}
        override fun navigateToSettings() {}
        override fun navigateToToday() {}
        override fun navigateToCalendar() {}
        override fun navigateToAiChat() {}
        override fun navigateToScreenCatalog() {}
        override fun navigateToOnboardings() {}
        override fun navigateToAddToChecklistPicker(text: String, purpose: AddToChecklistPurpose) {}
    }
}
