package com.antonchuraev.homesearchchecklist.csat

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CsatViewModelTest {

    private companion object {
        /**
         * Virtual-time budget for awaiting a review request. runTest skips idle delays, so this
         * costs no wall clock — it only bounds "nothing will ever arrive" so the test fails with an
         * assertion instead of hanging.
         */
        const val REQUEST_TIMEOUT_MS = 1_000L
    }

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
        assertNotNull(
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) { vm.reviewRequests.first() },
            "Love It! must emit a review request to the launcher",
        )
        assertFalse(state.showFeedbackThanks, "Thanks snackbar must wait until review completes")
        assertTrue(
            fakeAnalyticsTracker.hasEvent("csat_review_tapped"),
            "Expected csat_review_tapped analytics event on Love It!",
        )
    }

    /**
     * The regression this whole design exists for: the request must be CONSUMED, not latched.
     * With the old `shouldLaunchReview` Boolean the launcher re-read a still-true flag after any
     * Activity recreation and called the Play API a second time for one tap, burning a review
     * opportunity. A conflated channel hands the element out exactly once.
     */
    @Test
    fun selectLoveIt_emitsReviewRequestOnlyOnce() = runTest {
        val vm = createViewModel()

        vm.onIntent(CsatIntent.ForceShow)
        vm.onIntent(CsatIntent.SelectRating(CsatRating.LoveIt))
        advanceUntilIdle()

        assertNotNull(
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) { vm.reviewRequests.first() },
            "Sanity: the first collector receives the request",
        )
        assertNull(
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) { vm.reviewRequests.first() },
            "A re-created collector must NOT receive the already-handled request again",
        )
    }

    // --- Review completion thanks the user and resets state ---

    @Test
    fun reviewComplete_afterLoveIt_showsThanksAndResets() = runTest {
        val vm = createViewModel()

        vm.onIntent(CsatIntent.ForceShow)
        vm.onIntent(CsatIntent.SelectRating(CsatRating.LoveIt))
        vm.onIntent(CsatIntent.ReviewComplete(ReviewLaunchOutcome.Launched))
        advanceUntilIdle()

        val state = vm.screenState.value
        assertTrue(state.showFeedbackThanks, "Thanks snackbar flag must be set on review completion")
        assertNull(state.selectedRating, "Rating must reset after review completes")
    }

    /**
     * The review never reached the platform (no host Activity here). The user still picked
     * "Love It" and must get the thanks snackbar — a tap with no visible response reads as a
     * freeze — but analytics must NOT count a launch that never happened.
     */
    @Test
    fun reviewComplete_whenNeverLaunched_thanksUserButIsStampedNotShown() = runTest {
        val vm = createViewModel()

        vm.onIntent(CsatIntent.ForceShow)
        vm.onIntent(CsatIntent.SelectRating(CsatRating.LoveIt))
        vm.onIntent(CsatIntent.ReviewComplete(ReviewLaunchOutcome.NoHostActivity))
        advanceUntilIdle()

        assertTrue(
            vm.screenState.value.showFeedbackThanks,
            "The user is owed feedback even when no review could launch",
        )
        assertEquals(
            "not_shown",
            fakeAnalyticsTracker.paramOf("csat_review_completed", "source"),
            "A request that never launched must not be stamped as a launch",
        )
        assertEquals(
            "no_host_activity",
            fakeAnalyticsTracker.paramOf("csat_review_completed", "not_shown_reason"),
            "not_shown is four different failures — without the reason the arm is unreadable",
        )
        assertEquals(
            0,
            fakeAnalyticsTracker.countEventsWithParam("csat_review_completed", "source", "review_launch"),
            "The launch arm must stay empty when nothing launched",
        )
    }

    /**
     * Cancellation (Activity recreation while the Play call is in flight) is the one path where the
     * request is already consumed from the channel and can never be retried. It must still produce
     * a completion, or the tap leaves the user with no response at all.
     */
    @Test
    fun reviewComplete_whenCancelledMidFlight_stillThanksUserAndIsAttributed() = runTest {
        val vm = createViewModel()

        vm.onIntent(CsatIntent.ForceShow)
        vm.onIntent(CsatIntent.SelectRating(CsatRating.LoveIt))
        vm.onIntent(CsatIntent.ReviewComplete(ReviewLaunchOutcome.Cancelled))
        advanceUntilIdle()

        assertTrue(
            vm.screenState.value.showFeedbackThanks,
            "A cancelled launch must not swallow the user's feedback",
        )
        assertEquals(
            "cancelled",
            fakeAnalyticsTracker.paramOf("csat_review_completed", "not_shown_reason"),
            "Cancellation must be separable from a genuine platform rejection",
        )
    }

    /**
     * A launch the platform accepted carries no reason: `not_shown_reason` present on a
     * `review_launch` row would make the arm unfilterable.
     */
    @Test
    fun reviewComplete_whenLaunched_carriesNoNotShownReason() = runTest {
        val vm = createViewModel()

        vm.onIntent(CsatIntent.ForceShow)
        vm.onIntent(CsatIntent.SelectRating(CsatRating.LoveIt))
        vm.onIntent(CsatIntent.ReviewComplete(ReviewLaunchOutcome.Launched))
        advanceUntilIdle()

        assertNull(
            fakeAnalyticsTracker.paramOf("csat_review_completed", "not_shown_reason"),
            "An accepted launch has no not-shown reason to report",
        )
    }

    // --- Review completion is attributable to the tap that started it ---

    @Test
    fun reviewComplete_afterLoveIt_isStampedAsReviewLaunch() = runTest {
        val vm = createViewModel()

        vm.onIntent(CsatIntent.ForceShow)
        vm.onIntent(CsatIntent.SelectRating(CsatRating.LoveIt))
        vm.onIntent(CsatIntent.ReviewComplete(ReviewLaunchOutcome.Launched))
        advanceUntilIdle()

        assertEquals(
            1,
            fakeAnalyticsTracker.countEvents("csat_review_completed"),
            "One tap must produce exactly one completion",
        )
        assertEquals(
            "review_launch",
            fakeAnalyticsTracker.paramOf("csat_review_completed", "source"),
            "The completion that closes a real launch must be attributable to its tap",
        )
    }

    /**
     * Prod showed 6 csat_review_completed against 4 csat_review_tapped over 30 days, because the
     * launcher could call back twice for ONE tap (Activity recreation re-ran its LaunchedEffect
     * while the review flag was still latched — the flag was cleared asynchronously through the
     * intent bus). The one-shot channel should now make that impossible, but the stamping stays:
     * if a repeat ever arrives again it must be labelled, not silently counted as another review.
     */
    @Test
    fun reviewComplete_repeatCallbackForSameTap_isNotCountedAsAnotherReview() = runTest {
        val vm = createViewModel()

        vm.onIntent(CsatIntent.ForceShow)
        vm.onIntent(CsatIntent.SelectRating(CsatRating.LoveIt))
        vm.onIntent(CsatIntent.ReviewComplete(ReviewLaunchOutcome.Launched))
        vm.onIntent(CsatIntent.ReviewComplete(ReviewLaunchOutcome.Launched)) // launcher re-entered composition
        advanceUntilIdle()

        assertEquals(
            1,
            fakeAnalyticsTracker.countEvents("csat_review_tapped"),
            "Sanity: still a single tap",
        )
        assertEquals(
            1,
            fakeAnalyticsTracker.countEventsWithParam("csat_review_completed", "source", "review_launch"),
            "Only ONE completion may be attributed to the tap — the funnel arm must not exceed tapped",
        )
        assertEquals(
            1,
            fakeAnalyticsTracker.countEventsWithParam("csat_review_completed", "source", "repeat_callback"),
            "The repeat is labelled, not dropped — the duplication must stay measurable",
        )
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
        fun countEvents(name: String): Int = events.count { it.first == name }
        fun paramOf(name: String, param: String): Any? =
            events.firstOrNull { it.first == name }?.second?.get(param)
        fun countEventsWithParam(name: String, param: String, value: Any): Int =
            events.count { it.first == name && it.second[param] == value }
    }
}
