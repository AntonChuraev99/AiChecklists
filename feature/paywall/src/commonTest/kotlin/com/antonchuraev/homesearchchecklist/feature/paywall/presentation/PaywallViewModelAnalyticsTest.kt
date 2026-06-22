package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.LoginResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetOfferingsUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetPaywallConfigUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.PurchaseProductUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.RestorePurchasesUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Analytics-funnel coverage for [PaywallViewModel].
 *
 * Focus: the intent step of the purchase funnel — `purchase_button_clicked`
 * (AnalyticsEvents.Paywall.PURCHASE_BUTTON_CLICKED), fired the moment the user taps
 * the subscribe CTA, BEFORE billing runs. Without it we can only see paywall_shown
 * and purchase_completed, so a drop between "looked" and "tapped" is invisible.
 *
 * Note: paywall_shown lives in PaywallRoute (a @Composable side-effect) and is not
 * reachable from a pure ViewModel test — covered manually / by the screen layer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelAnalyticsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val monthlyTrialProduct = PaywallProduct(
        id = "premium_monthly",
        title = "Premium Monthly",
        description = "Premium Monthly",
        priceString = "$1.99",
        periodString = "1 month",
        packageId = "\$rc_monthly",
        hasFreeTrial = true,
        freeTrialDays = 3,
    )

    private val monthlyNoTrialProduct = PaywallProduct(
        id = "premium_monthly",
        title = "Premium Monthly",
        description = "Premium Monthly",
        priceString = "$1.99",
        periodString = "1 month",
        packageId = "\$rc_monthly",
        hasFreeTrial = false,
        freeTrialDays = 0,
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        product: PaywallProduct,
        source: String = "test_source",
    ): Pair<PaywallViewModel, RecordingAnalyticsTracker> {
        val tracker = RecordingAnalyticsTracker()
        // purchase() returns Cancelled so the success/error branches (which call
        // getString from Compose Resources, unresolvable in a plain JVM test) never run.
        val paywallRepo = FakePaywallRepository(
            offering = PaywallOffering(id = "default", products = listOf(product)),
            purchaseResult = PurchaseResult.Cancelled,
        )
        val userRepo = FakeUserDataRepository()
        val remoteConfig = FakeRemoteConfigProvider()
        val vm = PaywallViewModel(
            savedStateHandle = SavedStateHandle(),
            navigator = FakeAppNavigator(),
            getOfferingsUseCase = GetOfferingsUseCase(
                paywallRepo,
                GetPaywallConfigUseCase(remoteConfig),
            ),
            purchaseProductUseCase = PurchaseProductUseCase(paywallRepo, userRepo),
            restorePurchasesUseCase = RestorePurchasesUseCase(paywallRepo, userRepo),
            analyticsTracker = tracker,
            remoteConfigProvider = remoteConfig,
            sourceOverride = source,
        )
        return vm to tracker
    }

    @Test
    fun purchaseIntent_firesPurchaseButtonClicked_withSourceProductIdAndTrialFlag() =
        testScope.runTest {
            val (vm, tracker) = createViewModel(monthlyTrialProduct)
            advanceUntilIdle() // let init -> loadProducts() populate state.products

            vm.sendIntent(PaywallIntent.Purchase)
            advanceUntilIdle()

            val event = tracker.events
                .firstOrNull { it.first == AnalyticsEvents.Paywall.PURCHASE_BUTTON_CLICKED }
            assertTrue(
                event != null,
                "purchase_button_clicked must fire when a purchase starts",
            )
            val params = event.second
            assertEquals("test_source", params[AnalyticsParams.SOURCE])
            assertEquals("premium_monthly", params[AnalyticsParams.PRODUCT_ID])
            assertEquals(true, params[AnalyticsParams.HAS_FREE_TRIAL])
        }

    @Test
    fun purchaseIntent_purchaseButtonClicked_reportsHasFreeTrialFalse_forNoTrialProduct() =
        testScope.runTest {
            val (vm, tracker) = createViewModel(monthlyNoTrialProduct)
            advanceUntilIdle()

            vm.sendIntent(PaywallIntent.Purchase)
            advanceUntilIdle()

            val params = tracker.events
                .first { it.first == AnalyticsEvents.Paywall.PURCHASE_BUTTON_CLICKED }
                .second
            assertEquals(false, params[AnalyticsParams.HAS_FREE_TRIAL])
        }

    @Test
    fun purchaseIntent_firesButtonClicked_beforePurchaseCompletedOrCancelled() =
        testScope.runTest {
            val (vm, tracker) = createViewModel(monthlyTrialProduct)
            advanceUntilIdle()

            vm.sendIntent(PaywallIntent.Purchase)
            advanceUntilIdle()

            val clickIndex = tracker.events
                .indexOfFirst { it.first == AnalyticsEvents.Paywall.PURCHASE_BUTTON_CLICKED }
            val cancelIndex = tracker.events
                .indexOfFirst { it.first == AnalyticsEvents.Paywall.PURCHASE_CANCELLED }
            assertTrue(clickIndex >= 0, "intent event must be present")
            assertTrue(cancelIndex >= 0, "outcome event must be present")
            assertTrue(
                clickIndex < cancelIndex,
                "purchase_button_clicked (intent) must precede the outcome event",
            )
        }

    // ── Fakes ────────────────────────────────────────────────────────────────

    private class RecordingAnalyticsTracker : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) { events.add(name to params) }
    }

    private class FakePaywallRepository(
        private val offering: PaywallOffering?,
        private val purchaseResult: PurchaseResult,
    ) : PaywallRepository {
        override val subscriptionStatus: Flow<SubscriptionStatus> = flowOf(SubscriptionStatus.FREE)
        override suspend fun getOfferings(offeringId: String): Result<PaywallOffering?> =
            Result.success(offering)
        override suspend fun purchase(packageId: String): PurchaseResult = purchaseResult
        override suspend fun restorePurchases(): RestoreResult = RestoreResult.NoActiveSubscription
        override suspend fun refreshSubscriptionStatus() {}
        override fun isConfigured(): Boolean = true
        override suspend fun logIn(appUserId: String): Result<LoginResult> =
            Result.failure(NotImplementedError())
        override suspend fun logOut(): Result<SubscriptionStatus> =
            Result.failure(NotImplementedError())
    }

    private class FakeUserDataRepository : UserDataRepository {
        private val data = UserData(userId = "test", isPremium = false)
        override fun getUserDataFlow(): StateFlow<UserData> = MutableStateFlow(data)
        override suspend fun getUserData(): UserData = data
        override suspend fun update(userData: UserData) {}
        override suspend fun ensureUserRegistered(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = data, isNewUser = false))
        override suspend fun syncWithServer(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = data, isNewUser = false))
        override suspend fun isPaywallLinked(): Boolean = false
        override suspend fun setPaywallLinked(linked: Boolean) {}
        override suspend fun restoreCreditsAfterPurchase(): Result<Int> = Result.success(0)
        override suspend fun getFirstLaunchAtMillis(): Long = 0L
    }

    private class FakeRemoteConfigProvider : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getString(key: String, defaultValue: String): String = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
    }

    private class FakeAppNavigator : AppNavigator {
        override val backStack: NavBackStack<NavKey> = NavBackStack()
        private val _events = MutableSharedFlow<AppNavEvent>()
        override val events: SharedFlow<AppNavEvent> = _events.asSharedFlow()

        override fun showWidgetInstruction() {}
        override fun requestCreateWeeklyChecklist() {}
        override fun onBack() {}
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
        override fun navigateToPaywall(source: String) {}
        override fun navigateToPaywallVariant(source: String, forceVariant: String) {}
        override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) {}
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
