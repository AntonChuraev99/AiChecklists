package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.common.api.AiModelArm
import com.antonchuraev.homesearchchecklist.core.common.api.AiModelExperimentTracker
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
import kotlinx.coroutines.delay
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
 * Also covers the funnel ENTRY — `paywall_shown`. Since 2026-07-28 it is emitted by this
 * ViewModel (it used to live in PaywallRoute, which the two onboarding paywall hosts never
 * compose, so they produced taps with no impression), which makes it unit-testable here.
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
        // Default Cancelled so the success/error branches (which call getString from Compose
        // Resources, unresolvable in a plain JVM test) never run. Override to Success to exercise
        // the purchase_completed branch (that branch itself does NOT call getString).
        purchaseResult: PurchaseResult = PurchaseResult.Cancelled,
        // null = "arm unknown" (experiment off / not seen yet); non-null = a persisted arm.
        experimentArm: AiModelArm? = null,
        // > 0 keeps a purchase in flight across the next intent — see FakePaywallRepository.
        purchaseDelayMs: Long = 0L,
    ): Pair<PaywallViewModel, RecordingAnalyticsTracker> {
        val tracker = RecordingAnalyticsTracker()
        val paywallRepo = FakePaywallRepository(
            offering = PaywallOffering(id = "default", products = listOf(product)),
            purchaseResult = purchaseResult,
            purchaseDelayMs = purchaseDelayMs,
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
            aiModelExperimentTracker = FakeAiModelExperimentTracker(experimentArm),
        )
        return vm to tracker
    }

    // ── Funnel entry: paywall_shown must cover EVERY paywall host ─────────────

    @Test
    fun init_firesPaywallShown_soEveryHostOfThisViewModelProducesAnImpression() =
        testScope.runTest {
            val (_, tracker) = createViewModel(monthlyTrialProduct)
            advanceUntilIdle()

            val shown = tracker.events.filter { it.first == AnalyticsEvents.Paywall.SHOWN }
            assertEquals(
                1,
                shown.size,
                "paywall_shown must fire exactly once per ViewModel — it is the funnel " +
                    "denominator, and every paywall host (standalone screen + both onboarding " +
                    "steps) drives this ViewModel",
            )
            assertEquals("test_source", shown.first().second[AnalyticsParams.SOURCE])
        }

    @Test
    fun init_paywallShownAndPaywallOpened_carryIdenticalParams() =
        testScope.runTest {
            val (_, tracker) = createViewModel(monthlyTrialProduct)
            advanceUntilIdle()

            val shown = tracker.events.first { it.first == AnalyticsEvents.Paywall.SHOWN }.second
            val opened = tracker.events.first { it.first == "paywall_opened" }.second
            assertEquals(
                opened,
                shown,
                "the two names must stay interchangeable: they drifted apart once (different " +
                    "emit layers, different surface coverage) and that broke the funnel",
            )
        }

    @Test
    fun onboardingSource_isTaggedAsTheOnboardingSurface_notThePaywallScreen() =
        testScope.runTest {
            val (_, tracker) = createViewModel(
                product = monthlyTrialProduct,
                source = AnalyticsEvents.Paywall.SOURCE_ONBOARDING_TRIAL,
            )
            advanceUntilIdle()

            val shown = tracker.events.first { it.first == AnalyticsEvents.Paywall.SHOWN }.second
            assertEquals(
                AnalyticsEvents.Paywall.SURFACE_ONBOARDING,
                shown[AnalyticsParams.SURFACE],
                "an onboarding paywall is shown to everyone; pooling it with limit-gate " +
                    "impressions mixes two populations with very different intent",
            )
        }

    @Test
    fun gateSource_isTaggedAsThePaywallScreenSurface() =
        testScope.runTest {
            val (_, tracker) = createViewModel(monthlyTrialProduct, source = "checklist_limit")
            advanceUntilIdle()

            val shown = tracker.events.first { it.first == AnalyticsEvents.Paywall.SHOWN }.second
            assertEquals(
                AnalyticsEvents.Paywall.SURFACE_PAYWALL_SCREEN,
                shown[AnalyticsParams.SURFACE],
            )
        }

    @Test
    fun purchaseFunnel_impressionTapAndOutcome_allCarryTheSameSurface() =
        testScope.runTest {
            val (vm, tracker) = createViewModel(
                product = monthlyTrialProduct,
                source = AnalyticsEvents.Paywall.SOURCE_ONBOARDING_TRIAL,
            )
            advanceUntilIdle()

            vm.sendIntent(PaywallIntent.Purchase)
            advanceUntilIdle()

            listOf(
                AnalyticsEvents.Paywall.SHOWN,
                AnalyticsEvents.Paywall.PURCHASE_BUTTON_CLICKED,
                AnalyticsEvents.Paywall.PURCHASE_CANCELLED,
            ).forEach { name ->
                assertEquals(
                    AnalyticsEvents.Paywall.SURFACE_ONBOARDING,
                    tracker.events.first { it.first == name }.second[AnalyticsParams.SURFACE],
                    "$name must carry surface, or the funnel cannot be split per host",
                )
            }
        }

    // ── Duplicate taps are marked, never dropped ──────────────────────────────

    @Test
    fun purchaseIntent_firstTap_isNotMarkedAsRepeat() =
        testScope.runTest {
            val (vm, tracker) = createViewModel(monthlyTrialProduct)
            advanceUntilIdle()

            vm.sendIntent(PaywallIntent.Purchase)
            advanceUntilIdle()

            val params = tracker.events
                .first { it.first == AnalyticsEvents.Paywall.PURCHASE_BUTTON_CLICKED }
                .second
            assertEquals(false, params[AnalyticsParams.IS_REPEAT_TAP])
        }

    @Test
    fun purchaseIntent_secondTapInSameFrame_isEmittedAndMarkedAsRepeat() =
        testScope.runTest {
            val (vm, tracker) = createViewModel(monthlyTrialProduct, purchaseDelayMs = 10L)
            advanceUntilIdle()

            // No advance between the taps: state.isPurchasing is only set INSIDE the purchase
            // coroutine, so this is exactly the window in which the UI still shows an idle CTA.
            vm.sendIntent(PaywallIntent.Purchase)
            vm.sendIntent(PaywallIntent.Purchase)
            advanceUntilIdle()

            val taps = tracker.events
                .filter { it.first == AnalyticsEvents.Paywall.PURCHASE_BUTTON_CLICKED }
            assertEquals(2, taps.size, "the duplicate must be recorded, not silently swallowed")
            assertEquals(false, taps[0].second[AnalyticsParams.IS_REPEAT_TAP])
            assertEquals(
                true,
                taps[1].second[AnalyticsParams.IS_REPEAT_TAP],
                "a tap landing while a purchase is already running is a duplicate of the same " +
                    "intent — counting it as a second intent inflates the funnel numerator",
            )
        }

    @Test
    fun purchaseIntent_afterAnEarlierPurchaseFinished_isNotMarkedAsRepeat() =
        testScope.runTest {
            val (vm, tracker) = createViewModel(monthlyTrialProduct)
            advanceUntilIdle()

            vm.sendIntent(PaywallIntent.Purchase)
            advanceUntilIdle() // first purchase runs to completion (Cancelled) -> flag clears

            vm.sendIntent(PaywallIntent.Purchase)
            advanceUntilIdle()

            val taps = tracker.events
                .filter { it.first == AnalyticsEvents.Paywall.PURCHASE_BUTTON_CLICKED }
            assertEquals(2, taps.size)
            assertEquals(
                false,
                taps[1].second[AnalyticsParams.IS_REPEAT_TAP],
                "a genuine retry after a cancelled purchase is a NEW intent — a leaked " +
                    "in-flight flag would misreport every later tap and zero the numerator",
            )
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

    // ── AI-model A/B attribution on the purchase event ────────────────────────

    @Test
    fun purchaseCompleted_whenArmKnown_carriesModelVariantAndId() =
        testScope.runTest {
            val (vm, tracker) = createViewModel(
                product = monthlyTrialProduct,
                purchaseResult = PurchaseResult.Success(
                    subscriptionStatus = SubscriptionStatus.FREE,
                    transactionId = "txn_test",
                    hasFreeTrial = true,
                ),
                experimentArm = AiModelArm("variant_b", "gemini-3.1-flash-lite"),
            )
            advanceUntilIdle() // init reads the persisted arm; loadProducts populates state

            vm.sendIntent(PaywallIntent.Purchase)
            advanceUntilIdle()

            val params = tracker.events
                .first { it.first == AnalyticsEvents.Paywall.PURCHASE_COMPLETED }
                .second
            assertEquals("variant_b", params[AnalyticsParams.AI_MODEL_VARIANT])
            assertEquals("gemini-3.1-flash-lite", params[AnalyticsParams.AI_MODEL_ID])
        }

    @Test
    fun purchaseCompleted_whenArmUnknown_omitsModelParams() =
        testScope.runTest {
            val (vm, tracker) = createViewModel(
                product = monthlyTrialProduct,
                purchaseResult = PurchaseResult.Success(
                    subscriptionStatus = SubscriptionStatus.FREE,
                    transactionId = "txn_test",
                    hasFreeTrial = true,
                ),
                experimentArm = null, // arm unknown → best-effort omit, never crash
            )
            advanceUntilIdle()

            vm.sendIntent(PaywallIntent.Purchase)
            advanceUntilIdle()

            val params = tracker.events
                .first { it.first == AnalyticsEvents.Paywall.PURCHASE_COMPLETED }
                .second
            assertTrue(
                !params.containsKey(AnalyticsParams.AI_MODEL_VARIANT) &&
                    !params.containsKey(AnalyticsParams.AI_MODEL_ID),
                "unknown arm must not add ai_model_* params (and must not crash the purchase)",
            )
        }

    // ── Fakes ────────────────────────────────────────────────────────────────

    private class FakeAiModelExperimentTracker(
        private val arm: AiModelArm?,
    ) : AiModelExperimentTracker {
        override suspend fun report(variant: String?, modelId: String?, aiFlow: String?) {}
        override suspend fun current(): AiModelArm? = arm
    }

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
        // Real billing suspends (the store sheet is open). With 0 the purchase coroutine runs to
        // completion before the NEXT buffered intent is dequeued, so the "two taps while one
        // purchase is running" window cannot be reproduced at all.
        private val purchaseDelayMs: Long = 0L,
    ) : PaywallRepository {
        override val subscriptionStatus: Flow<SubscriptionStatus> = flowOf(SubscriptionStatus.FREE)
        override suspend fun getOfferings(offeringId: String): Result<PaywallOffering?> =
            Result.success(offering)
        override suspend fun purchase(packageId: String): PurchaseResult {
            if (purchaseDelayMs > 0) delay(purchaseDelayMs)
            return purchaseResult
        }
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
