package com.antonchuraev.homesearchchecklist.deeplink

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.feature.create.domain.usecase.CreateChecklistFromGalleryTemplateUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Funnel contract for the SEO-gallery deep-link: `opened = created(gallery) + failed`, ONE of each
 * per arrival.
 *
 * Regression guard for the prod double-count (2026-07-28): 7 `gallery_deeplink_opened` against
 * 1 `checklist_created` and 0 `gallery_deeplink_failed`, spread over ~1.5 unique users. `opened`
 * was emitted before the create suspended, while the pending link is deliberately NOT consumed
 * when the handling coroutine is cancelled (Activity recreation mid-create) — so the next collector
 * re-processed the same arrival and re-emitted `opened`, making the documented contract unreachable
 * by construction.
 *
 * The cancel-then-retry path is the whole point of these tests, so they drive
 * [handleGalleryDeepLink] directly rather than the `LaunchedEffect` in `App.kt` that hosts it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GalleryDeepLinkFunnelTest {

    private val link = GalleryDeepLink(
        slug = "morning-routine",
        utm = mapOf(AnalyticsParams.UTM_SOURCE to "pinterest"),
    )

    /**
     * The reported bug, end to end: the first collector is cancelled while the create is still in
     * flight (rotation / theme / locale recreate), a second collector picks the retained link up
     * and finishes it. Exactly one `opened` and exactly one terminal event must reach analytics.
     */
    @Test
    fun cancelledMidCreateThenRetried_emitsOpenedOnceAndOneTerminal() = runTest {
        val holder = PendingGalleryDeepLink()
        val analytics = RecordingAnalyticsTracker()
        holder.submit(link)

        val firstAttempt = launch {
            handleGalleryDeepLink(
                link = link,
                pending = holder,
                analyticsTracker = analytics,
                // A slow Firestore fetch: still in flight when the Activity is torn down.
                createFromGallery = { awaitCancellation() },
                onCreated = {},
                showNotFoundMessage = {},
                showErrorMessage = {},
            )
        }
        runCurrent()
        assertEquals(
            1,
            analytics.countOf(AnalyticsEvents.Gallery.DEEPLINK_OPENED),
            "the first attempt must report the arrival before the create suspends",
        )

        firstAttempt.cancel()
        firstAttempt.join()

        // Load-bearing: the arrival must SURVIVE the cancellation, otherwise the user gets no
        // checklist at all. Fixing the double-count must not regress this.
        assertEquals(
            link,
            holder.pending.value,
            "a cancelled attempt must not consume the link — the retry needs it",
        )

        var navigatedTo: Long? = null
        handleGalleryDeepLink(
            link = link,
            pending = holder,
            analyticsTracker = analytics,
            createFromGallery = { CreateChecklistFromGalleryTemplateUseCase.Result.Created(42L) },
            onCreated = { navigatedTo = it },
            showNotFoundMessage = {},
            showErrorMessage = {},
        )

        assertEquals(
            1,
            analytics.countOf(AnalyticsEvents.Gallery.DEEPLINK_OPENED),
            "the retry re-processes the SAME arrival — it must not open the funnel a second time",
        )
        assertEquals(1, analytics.countOf(AnalyticsEvents.Checklist.CREATED))
        assertEquals(0, analytics.countOf(AnalyticsEvents.Gallery.DEEPLINK_FAILED))
        assertEquals(42L, navigatedTo, "the retry must still navigate to the created checklist")
        assertNull(holder.pending.value, "a completed attempt consumes the link")
    }

    /** The utm captured at parse time must ride along on the funnel events, both of them. */
    @Test
    fun openedAndCreated_carryTemplateSlugAndUtm() = runTest {
        val holder = PendingGalleryDeepLink()
        val analytics = RecordingAnalyticsTracker()
        holder.submit(link)

        handleGalleryDeepLink(
            link = link,
            pending = holder,
            analyticsTracker = analytics,
            createFromGallery = { CreateChecklistFromGalleryTemplateUseCase.Result.Created(7L) },
            onCreated = {},
            showNotFoundMessage = {},
            showErrorMessage = {},
        )

        val opened = analytics.paramsOf(AnalyticsEvents.Gallery.DEEPLINK_OPENED).single()
        assertEquals("morning-routine", opened[AnalyticsParams.TEMPLATE_SLUG])
        assertEquals("pinterest", opened[AnalyticsParams.UTM_SOURCE])
        val created = analytics.paramsOf(AnalyticsEvents.Checklist.CREATED).single()
        assertEquals("gallery", created[AnalyticsParams.SOURCE])
        assertEquals("pinterest", created[AnalyticsParams.UTM_SOURCE])
    }

    /**
     * A stale slug is still ONE arrival with ONE terminal event, and it must be consumed — a
     * `not_found` that stayed pending would be retried forever.
     */
    @Test
    fun notFoundSlug_emitsOneFailedAndConsumes() = runTest {
        val holder = PendingGalleryDeepLink()
        val analytics = RecordingAnalyticsTracker()
        holder.submit(link)
        var snackbarShown = false

        handleGalleryDeepLink(
            link = link,
            pending = holder,
            analyticsTracker = analytics,
            createFromGallery = { CreateChecklistFromGalleryTemplateUseCase.Result.NotFound },
            onCreated = {},
            showNotFoundMessage = { snackbarShown = true },
            showErrorMessage = {},
        )

        assertEquals(1, analytics.countOf(AnalyticsEvents.Gallery.DEEPLINK_OPENED))
        assertEquals(1, analytics.countOf(AnalyticsEvents.Gallery.DEEPLINK_FAILED))
        assertEquals(0, analytics.countOf(AnalyticsEvents.Checklist.CREATED))
        assertEquals(
            "not_found",
            analytics.paramsOf(AnalyticsEvents.Gallery.DEEPLINK_FAILED).single()[AnalyticsParams.REASON],
        )
        assertTrue(snackbarShown, "an unknown slug must give the user visible feedback")
        assertNull(holder.pending.value)
    }

    /**
     * Guards the fix from over-correcting: a user who taps the SAME gallery link again after the
     * first one finished is a genuinely new arrival and must open the funnel again. The dedup is
     * per arrival, not "once per slug for the app's lifetime".
     */
    @Test
    fun sameLinkTappedAgainAfterCompletion_opensFunnelAgain() = runTest {
        val holder = PendingGalleryDeepLink()
        val analytics = RecordingAnalyticsTracker()

        repeat(2) {
            holder.submit(link)
            handleGalleryDeepLink(
                link = link,
                pending = holder,
                analyticsTracker = analytics,
                createFromGallery = { CreateChecklistFromGalleryTemplateUseCase.Result.Created(1L) },
                onCreated = {},
                showNotFoundMessage = {},
                showErrorMessage = {},
            )
        }

        assertEquals(2, analytics.countOf(AnalyticsEvents.Gallery.DEEPLINK_OPENED))
        assertEquals(2, analytics.countOf(AnalyticsEvents.Checklist.CREATED))
    }

    /**
     * The marker is scoped to the arrival it was set for: a DIFFERENT link submitted after a
     * cancelled attempt (warm `onNewIntent` with another template) still gets its own `opened`.
     */
    @Test
    fun differentLinkAfterCancelledAttempt_getsItsOwnOpened() = runTest {
        val holder = PendingGalleryDeepLink()
        val analytics = RecordingAnalyticsTracker()
        holder.submit(link)

        val firstAttempt = launch {
            handleGalleryDeepLink(
                link = link,
                pending = holder,
                analyticsTracker = analytics,
                createFromGallery = { awaitCancellation() },
                onCreated = {},
                showNotFoundMessage = {},
                showErrorMessage = {},
            )
        }
        runCurrent()
        firstAttempt.cancel()
        firstAttempt.join()

        val other = GalleryDeepLink(slug = "packing-list")
        holder.submit(other)
        handleGalleryDeepLink(
            link = other,
            pending = holder,
            analyticsTracker = analytics,
            createFromGallery = { CreateChecklistFromGalleryTemplateUseCase.Result.Created(2L) },
            onCreated = {},
            showNotFoundMessage = {},
            showErrorMessage = {},
        )

        assertEquals(
            2,
            analytics.countOf(AnalyticsEvents.Gallery.DEEPLINK_OPENED),
            "two distinct arrivals — the dedup marker must not leak across links",
        )
        assertEquals(
            listOf("morning-routine", "packing-list"),
            analytics.paramsOf(AnalyticsEvents.Gallery.DEEPLINK_OPENED)
                .map { it[AnalyticsParams.TEMPLATE_SLUG] },
        )
    }

    // --- Test doubles ---

    private class RecordingAnalyticsTracker : AnalyticsTracker {
        private val events = mutableListOf<Pair<String, Map<String, Any>>>()

        override fun setUserId(userId: String) = Unit
        override fun setUserProperties(properties: Map<String, Any>) = Unit
        override fun screenView(name: String) = Unit
        override fun event(name: String, params: Map<String, Any>) {
            events += name to params
        }

        fun countOf(name: String): Int = events.count { it.first == name }
        fun paramsOf(name: String): List<Map<String, Any>> =
            events.filter { it.first == name }.map { it.second }
    }
}
