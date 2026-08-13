package com.antonchuraev.homesearchchecklist.attribution

import android.content.Context
import android.os.Bundle
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The ad-install cohort must be visible in Amplitude on the DAY it arrived.
 *
 * Today attribution exists only as sticky user-properties (`gclid`, `utm_*`): they never expire, so
 * a user acquired in May is indistinguishable from one acquired today, and there is no event to put
 * on a timeline at all. Owner's question — "who came from ads on this date and what did they do?" —
 * is unanswerable. The answer is a once-per-install EVENT, `install_attributed`, carrying the
 * campaign identity plus the paid/organic verdict.
 *
 * What each test here catches is stated on the test; the two load-bearing ones:
 *  - `is_paid` derived from `utm_medium` instead of `gclid`. Google Ads auto-tagging sends a bare
 *    `gclid` and NEVER `utm_medium`, while Play labels organic store traffic `utm_medium=organic` —
 *    so a medium-based verdict marks real ad installs organic and vice versa. The pair of
 *    `gclid`-only / `cpc`-without-gclid cases below cannot both pass on a medium-based rule.
 *  - an organic or empty referrer emitting nothing. Then the event counts only paid installs and
 *    the cohort has no denominator to compare against.
 *
 * Run: ./gradlew :composeApp:testAndroidHostTest --tests "*InstallReferrerCaptureTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstallReferrerCaptureTest {

    private companion object {
        /** Wire names — this is the contract Amplitude segments on; renaming them breaks charts. */
        const val EVENT_INSTALL_ATTRIBUTED = "install_attributed"
        const val PARAM_SOURCE = "source"
        const val PARAM_MEDIUM = "medium"
        const val PARAM_CAMPAIGN = "campaign"
        const val PARAM_GCLID = "gclid"
        const val PARAM_IS_PAID = "is_paid"

        const val PREFS = "install_attribution"
        const val KEY_CAPTURED = "referrer_captured"
    }

    @Before
    fun clearCapturedFlag() {
        context().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    // ─── A. the event exists, once per install ───────────────────────────────

    /**
     * Google Ads auto-tagging: the referrer carries a bare `gclid` and no utm at all. This is the
     * shape of the majority of paid installs, and the one a `utm_medium == "cpc"` rule misses
     * entirely.
     */
    @Test
    fun capture_gclidOnlyReferrer_emitsInstallAttributedWithIsPaidTrue() {
        val analytics = RecordingAnalytics()
        val service = ScriptedReferrerService(Step(OK, "gclid=EAIaIQobChMIx7"))

        newCapture(analytics, service).capture()

        val params = analytics.singleAttributionEvent()
        assertEquals("EAIaIQobChMIx7", params[PARAM_GCLID], "The click id must travel on the event")
        assertEquals(
            true,
            params[PARAM_IS_PAID],
            "A referrer carrying a gclid IS a paid install — got $params",
        )
    }

    /**
     * A manually tagged ad link: utm_* AND gclid. The campaign identity has to reach the event, or
     * the cohort cannot be split per campaign.
     */
    @Test
    fun capture_taggedPaidReferrer_carriesCampaignIdentityOnTheEvent() {
        val analytics = RecordingAnalytics()
        val service = ScriptedReferrerService(
            Step(OK, "utm_source=google-play&utm_medium=cpc&utm_campaign=summer_sale&gclid=abc123"),
        )

        newCapture(analytics, service).capture()

        val params = analytics.singleAttributionEvent()
        assertEquals("google-play", params[PARAM_SOURCE])
        assertEquals("cpc", params[PARAM_MEDIUM])
        assertEquals("summer_sale", params[PARAM_CAMPAIGN])
        assertEquals("abc123", params[PARAM_GCLID])
        assertEquals(true, params[PARAM_IS_PAID])
    }

    /**
     * `utm_medium=cpc` WITHOUT a gclid is not proof of a paid Google install (any hand-tagged link
     * can claim it). Together with the gclid-only case above, a `utm_medium`-based verdict cannot
     * satisfy both tests at once.
     */
    @Test
    fun capture_cpcMediumWithoutGclid_isNotPaid() {
        val analytics = RecordingAnalytics()
        val service = ScriptedReferrerService(
            Step(OK, "utm_source=facebook&utm_medium=cpc&utm_campaign=retarget"),
        )

        newCapture(analytics, service).capture()

        val params = analytics.singleAttributionEvent()
        assertEquals(
            false,
            params[PARAM_IS_PAID],
            "is_paid must follow the gclid, not the medium — got $params",
        )
    }

    /**
     * Play labels its own organic store traffic. The event must still fire: it is the DENOMINATOR
     * the paid cohort is compared against, and without it "ad installs today" has no baseline.
     * Absent keys stay absent — a defaulted "organic"/""/"unknown" is indistinguishable in Amplitude
     * from a real value and quietly inflates whatever segment it lands in.
     */
    @Test
    fun capture_organicPlayReferrer_emitsEventWithIsPaidFalse_andNoDefaultedKeys() {
        val analytics = RecordingAnalytics()
        val service = ScriptedReferrerService(Step(OK, "utm_source=google-play&utm_medium=organic"))

        newCapture(analytics, service).capture()

        val params = analytics.singleAttributionEvent()
        assertEquals("google-play", params[PARAM_SOURCE])
        assertEquals("organic", params[PARAM_MEDIUM])
        assertEquals(false, params[PARAM_IS_PAID])
        assertFalse(
            params.containsKey(PARAM_CAMPAIGN),
            "An absent campaign must stay absent, never a default — got $params",
        )
        assertFalse(
            params.containsKey(PARAM_GCLID),
            "An absent gclid must stay absent, never an empty string — got $params",
        )
    }

    /** No referrer at all (sideload, restore, Play returning nothing) is still an install. */
    @Test
    fun capture_emptyReferrer_stillEmitsEventWithIsPaidFalse() {
        val analytics = RecordingAnalytics()
        val service = ScriptedReferrerService(Step(OK, ""))

        newCapture(analytics, service).capture()

        val params = analytics.singleAttributionEvent()
        assertEquals(false, params[PARAM_IS_PAID])
        assertTrue(
            params.keys.none { it == PARAM_SOURCE || it == PARAM_MEDIUM || it == PARAM_CAMPAIGN },
            "Nothing was attributed, so no campaign key may be invented — got $params",
        )
    }

    /**
     * `install_attributed` is per INSTALL. A second emission (next cold start) would double every
     * acquisition count and break any per-user funnel starting at the event.
     */
    @Test
    fun capture_secondColdStart_emitsInstallAttributedOnlyOnce() {
        val analytics = RecordingAnalytics()
        val first = ScriptedReferrerService(Step(OK, "gclid=abc123"))
        val second = ScriptedReferrerService(Step(OK, "gclid=abc123"))

        newCapture(analytics, first).capture()
        newCapture(analytics, second).capture()

        assertEquals(
            1,
            analytics.attributionEvents().size,
            "Captured once per install — got ${analytics.events}",
        )
        assertEquals(
            0,
            second.clientsBuilt,
            "Once captured, the next start must not even reconnect to the Play service",
        )
    }

    /** The utm user-properties are what today's dashboards read; the event must be added, not swapped in. */
    @Test
    fun capture_paidReferrer_stillPublishesUtmUserProperties() {
        val analytics = RecordingAnalytics()
        val service = ScriptedReferrerService(
            Step(OK, "utm_source=google-play&utm_medium=cpc&gclid=abc123"),
        )

        newCapture(analytics, service).capture()

        val published = analytics.allPublishedProperties()
        assertEquals("google-play", published[AnalyticsParams.UTM_SOURCE])
        assertEquals("abc123", published[AnalyticsParams.GCLID])
    }

    // ─── E. transient response codes must not burn the one-shot ──────────────

    /**
     * SERVICE_DISCONNECTED is transient (the Play service died mid-call), exactly like
     * SERVICE_UNAVAILABLE — but it falls into the `else` branch that marks the install captured.
     * A user whose first connection drops therefore loses attribution forever, silently: the flag is
     * set, no event was sent, and no later start will retry.
     */
    @Test
    fun capture_serviceDisconnected_doesNotBurnTheOneShot_soNextStartStillAttributes() {
        val analytics = RecordingAnalytics()
        val dropped = ScriptedReferrerService(Step(SERVICE_DISCONNECTED))
        newCapture(analytics, dropped).capture()

        assertTrue(analytics.attributionEvents().isEmpty(), "Nothing to report from a dropped connection")
        assertFalse(
            capturedFlag(),
            "A transient disconnect must NOT mark the install captured — that is the one-shot burnt",
        )

        val retry = ScriptedReferrerService(Step(OK, "gclid=abc123"))
        newCapture(analytics, retry).capture()

        assertEquals(
            1,
            retry.clientsBuilt,
            "The next cold start must reconnect after a transient failure",
        )
        assertEquals("abc123", analytics.singleAttributionEvent()[PARAM_GCLID])
    }

    /** Existing behaviour, kept honest: the other transient code already retries. */
    @Test
    fun capture_serviceUnavailable_doesNotMarkCaptured() {
        val analytics = RecordingAnalytics()

        newCapture(analytics, ScriptedReferrerService(Step(SERVICE_UNAVAILABLE))).capture()

        assertFalse(capturedFlag(), "SERVICE_UNAVAILABLE is transient — the next start must retry")
    }

    /**
     * The counterpart guard: a PERMANENT refusal must still burn the one-shot, or every cold start
     * pays a Play-service connection that can never succeed.
     */
    @Test
    fun capture_featureNotSupported_marksCaptured_andEmitsNothing() {
        val analytics = RecordingAnalytics()

        newCapture(analytics, ScriptedReferrerService(Step(FEATURE_NOT_SUPPORTED))).capture()

        assertTrue(capturedFlag(), "A device that can never return a referrer must not be retried forever")
        assertTrue(analytics.attributionEvents().isEmpty(), "Nothing was attributed — got ${analytics.events}")
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun context(): Context = RuntimeEnvironment.getApplication()

    private fun capturedFlag(): Boolean =
        context().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CAPTURED, false)

    private fun newCapture(analytics: AnalyticsTracker, service: ScriptedReferrerService) =
        InstallReferrerCapture(
            context = context(),
            analytics = analytics,
            logger = NoOpLogger(),
            clientFactory = service.factory,
        )

    private val OK get() = InstallReferrerClient.InstallReferrerResponse.OK
    private val SERVICE_UNAVAILABLE get() = InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE
    private val SERVICE_DISCONNECTED get() = InstallReferrerClient.InstallReferrerResponse.SERVICE_DISCONNECTED
    private val FEATURE_NOT_SUPPORTED get() = InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED

    /** One scripted answer of the Play Install Referrer service. */
    private class Step(val responseCode: Int, val referrer: String = "")

    /**
     * Doubles ONLY the Play IPC: the production class keeps building its own listener, reading
     * [ReferrerDetails] through it, parsing, publishing and persisting — that whole path is under
     * test, which is why the referrer arrives as the raw encoded string Play really returns.
     */
    private class ScriptedReferrerService(private vararg val steps: Step) {
        var clientsBuilt = 0
            private set

        val factory: (Context) -> InstallReferrerClient = {
            val step = steps.getOrElse(clientsBuilt) { steps.last() }
            clientsBuilt++
            FakeInstallReferrerClient(step)
        }
    }

    private class FakeInstallReferrerClient(private val step: Step) : InstallReferrerClient() {
        private var ready = false

        override fun isReady(): Boolean = ready

        override fun startConnection(listener: InstallReferrerStateListener) {
            ready = true
            // The real library delivers this on the main thread, synchronously after binding.
            listener.onInstallReferrerSetupFinished(step.responseCode)
        }

        override fun endConnection() {
            ready = false
        }

        override fun getInstallReferrer(): ReferrerDetails =
            ReferrerDetails(Bundle().apply { putString("install_referrer", step.referrer) })
    }

    private class RecordingAnalytics : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        private val propertyWrites = mutableListOf<Map<String, Any>>()
        private val onceWrites = mutableListOf<Map<String, Any>>()

        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) { propertyWrites += properties }
        override fun setUserPropertiesOnce(properties: Map<String, Any>) { onceWrites += properties }
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) { events += name to params }

        fun attributionEvents(): List<Map<String, Any>> =
            events.filter { it.first == EVENT_INSTALL_ATTRIBUTED }.map { it.second }

        fun singleAttributionEvent(): Map<String, Any> {
            val attributed = attributionEvents()
            assertEquals(
                1,
                attributed.size,
                "Exactly one `$EVENT_INSTALL_ATTRIBUTED` expected — recorded events: $events",
            )
            return attributed.single()
        }

        /** Either channel: the assertion is about what segmentation receives, not how it was sent. */
        fun allPublishedProperties(): Map<String, Any> =
            (propertyWrites + onceWrites).fold(mutableMapOf()) { acc, map -> acc.apply { putAll(map) } }
    }

    private class NoOpLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }
}
