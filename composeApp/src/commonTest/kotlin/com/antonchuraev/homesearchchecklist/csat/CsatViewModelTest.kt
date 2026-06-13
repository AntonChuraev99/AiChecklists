package com.antonchuraev.homesearchchecklist.csat

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CsatViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeAnalyticsTracker: RecordingAnalyticsTracker

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeAnalyticsTracker = RecordingAnalyticsTracker()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Creates a [CsatViewModel] backed by an on-disk test DataStore in build/.
     *
     * [CsatManager.startObserving] exits early when analyticsTracker is not
     * [ObservableAnalyticsTracker], so the auto-show delay never fires — tests
     * are fully deterministic.
     */
    private fun createViewModel(): CsatViewModel {
        val datastore = AppDatastore(
            PreferenceDataStoreFactory.createWithPath {
                "build/csat_test_prefs_${Random.nextLong()}.preferences_pb".toPath()
            },
            testDispatcher,
        )
        return CsatViewModel(
            csatManager = CsatManager(datastore),
            analyticsTracker = fakeAnalyticsTracker,
        )
    }

    // --- ForceShowFeedback ---

    @Test
    fun forceShowFeedback_setsFeedbackOnlyTrue_andShowsBottomSheet() = runTest {
        val vm = createViewModel()

        vm.onIntent(CsatIntent.ForceShowFeedback)
        advanceUntilIdle()

        val state = vm.screenState.value
        assertTrue(state.showBottomSheet, "Expected showBottomSheet = true")
        assertTrue(state.isFeedbackOnly, "Expected isFeedbackOnly = true")
        assertTrue(
            fakeAnalyticsTracker.hasEvent("feedback_opened"),
            "Expected feedback_opened analytics event",
        )
    }

    // --- Submit in feedback-only mode ---

    @Test
    fun submit_inFeedbackOnly_closesSheetWithoutThankYou() = runTest {
        val vm = createViewModel()

        vm.onIntent(CsatIntent.ForceShowFeedback)
        advanceUntilIdle()

        vm.onIntent(CsatIntent.UpdateText("Great app!"))
        advanceUntilIdle()

        vm.onIntent(CsatIntent.Submit)
        advanceUntilIdle()

        val state = vm.screenState.value
        assertFalse(state.showBottomSheet, "Expected showBottomSheet = false after submit")
        assertFalse(state.isFeedbackOnly, "Expected isFeedbackOnly reset to false after close")
        assertTrue(state.showFeedbackThanks, "Expected showFeedbackThanks = true for snackbar trigger")
        assertTrue(
            fakeAnalyticsTracker.hasEvent("feedback_submitted"),
            "Expected feedback_submitted analytics event",
        )
        assertFalse(
            fakeAnalyticsTracker.hasEvent("csat_submitted"),
            "Expected NO csat_submitted event in feedback-only mode",
        )
    }

    // --- Submit with NotGood/Okay rating triggers the thanks snackbar ---

    @Test
    fun submit_negativeRating_closesAndShowsThanks() = runTest {
        val vm = createViewModel()

        vm.onIntent(CsatIntent.ForceShow)
        vm.onIntent(CsatIntent.SelectRating(CsatRating.NotGood))
        vm.onIntent(CsatIntent.UpdateText("Crashes a lot"))
        vm.onIntent(CsatIntent.Submit)
        advanceUntilIdle()

        val state = vm.screenState.value
        assertFalse(state.showBottomSheet, "Sheet must close after Negative submit")
        assertTrue(state.showFeedbackThanks, "Snackbar flag must be true for Negative submit")
        assertTrue(
            fakeAnalyticsTracker.hasEvent("csat_submitted"),
            "csat_submitted must still be tracked for rating flow",
        )
    }

    // --- Selecting "Love It!" launches the in-app review immediately ---

    @Test
    fun selectLoveIt_closesSheetAndLaunchesReview() = runTest {
        val vm = createViewModel()

        vm.onIntent(CsatIntent.ForceShow)
        vm.onIntent(CsatIntent.SelectRating(CsatRating.LoveIt))
        advanceUntilIdle()

        val state = vm.screenState.value
        assertFalse(state.showBottomSheet, "Sheet must close immediately on Love It! selection")
        assertTrue(state.shouldLaunchReview, "Love It! must trigger the in-app review launcher")
        assertFalse(state.showFeedbackThanks, "Thanks snackbar must wait until review completes")
        assertTrue(
            fakeAnalyticsTracker.hasEvent("csat_review_tapped"),
            "Expected csat_review_tapped analytics event on Love It!",
        )
    }

    // --- Review completion thanks the user and resets state ---

    @Test
    fun reviewComplete_afterLoveIt_showsThanksAndResets() = runTest {
        val vm = createViewModel()

        vm.onIntent(CsatIntent.ForceShow)
        vm.onIntent(CsatIntent.SelectRating(CsatRating.LoveIt))
        vm.onIntent(CsatIntent.ReviewComplete)
        advanceUntilIdle()

        val state = vm.screenState.value
        assertFalse(state.shouldLaunchReview, "shouldLaunchReview must reset after review completes")
        assertTrue(state.showFeedbackThanks, "Thanks snackbar flag must be set on review completion")
        assertNull(state.selectedRating, "Rating must reset after review completes")
    }

    // --- FeedbackThanksShown clears the snackbar trigger ---

    @Test
    fun feedbackThanksShown_clearsShowFeedbackThanksFlag() = runTest {
        val vm = createViewModel()

        vm.onIntent(CsatIntent.ForceShowFeedback)
        vm.onIntent(CsatIntent.UpdateText("Nice"))
        vm.onIntent(CsatIntent.Submit)
        advanceUntilIdle()
        assertTrue(vm.screenState.value.showFeedbackThanks)

        vm.onIntent(CsatIntent.FeedbackThanksShown)
        advanceUntilIdle()

        assertFalse(vm.screenState.value.showFeedbackThanks)
    }

    // --- Dismiss resets feedbackOnly flag ---

    @Test
    fun dismiss_afterForceShowFeedback_resetsFeedbackOnlyFlag() = runTest {
        val vm = createViewModel()

        vm.onIntent(CsatIntent.ForceShowFeedback)
        advanceUntilIdle()

        vm.onIntent(CsatIntent.Dismiss)
        advanceUntilIdle()

        val state = vm.screenState.value
        assertFalse(state.showBottomSheet)
        assertFalse(state.isFeedbackOnly, "isFeedbackOnly must be reset on dismiss")
    }

    // --- Fake ---

    private class RecordingAnalyticsTracker : AnalyticsTracker {
        private val events = mutableListOf<Pair<String, Map<String, Any>>>()

        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {
            events.add(name to params)
        }

        fun hasEvent(name: String): Boolean = events.any { it.first == name }
    }
}
