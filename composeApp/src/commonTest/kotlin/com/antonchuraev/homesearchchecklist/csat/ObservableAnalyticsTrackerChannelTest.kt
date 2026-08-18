package com.antonchuraev.homesearchchecklist.csat

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks the DECORATOR, not a fake of the interface.
 *
 * Why this file exists: `ObservableAnalyticsTracker` is what production binds as `AnalyticsTracker`
 * on all three platforms, and it is declared `AnalyticsTracker by delegate`. When the interface grew
 * `eventOutOfSession` with a **default body**, two silent failures became possible and neither one
 * would fail compilation:
 *
 *  1. the decorator forwards the new method past its own `_events` broadcast, so anything observing
 *     analytics (CSAT trigger logic) stops seeing that channel;
 *  2. worse, if the call resolves to the interface default (`= event(name, params)`) it lands
 *     virtually back on the overridden `event`, which delegates **in-session** — turning the whole
 *     phantom-session fix into a no-op while every test that fakes the interface directly stays green.
 *
 * Tests that build their own `AnalyticsTracker` fake prove only that a call site picked the right
 * method. They cannot see either failure above, because they never exercise the object production
 * actually uses.
 */
class ObservableAnalyticsTrackerChannelTest {

    private class RecordingTracker : AnalyticsTracker {
        val inSession = mutableListOf<String>()
        val outOfSession = mutableListOf<String>()
        val propertiesInSession = mutableListOf<Map<String, Any>>()
        val propertiesOutOfSession = mutableListOf<Map<String, Any>>()

        override fun setUserId(userId: String) = Unit
        override fun setUserProperties(properties: Map<String, Any>) {
            propertiesInSession += properties
        }

        override fun setUserPropertiesOutOfSession(properties: Map<String, Any>) {
            propertiesOutOfSession += properties
        }

        override fun setUserPropertiesOnce(properties: Map<String, Any>) = Unit
        override fun screenView(name: String) = Unit
        override fun event(name: String, params: Map<String, Any>) {
            inSession += name
        }

        override fun eventOutOfSession(name: String, params: Map<String, Any>) {
            outOfSession += name
        }
    }

    @Test
    fun outOfSessionEmit_reachesTheDelegateOnTheOutOfSessionChannel() {
        val delegate = RecordingTracker()

        ObservableAnalyticsTracker(delegate).eventOutOfSession("push_received", mapOf("k" to "v"))

        // The assertion that matters is the EMPTY one: landing in `inSession` is exactly the silent
        // regression this file guards — the event would still be delivered, just with a session
        // minted around it, and nothing else in the suite would notice.
        assertEquals(listOf("push_received"), delegate.outOfSession)
        assertTrue(delegate.inSession.isEmpty(), "out-of-session emit must not reach the in-session channel")
    }

    @Test
    fun outOfSessionUserProperties_reachTheDelegateOnTheOutOfSessionChannel() {
        val delegate = RecordingTracker()

        ObservableAnalyticsTracker(delegate).setUserPropertiesOutOfSession(mapOf("arm" to "b"))

        assertEquals(listOf(mapOf<String, Any>("arm" to "b")), delegate.propertiesOutOfSession)
        assertTrue(delegate.propertiesInSession.isEmpty())
    }

    @Test
    fun outOfSessionEmit_isStillBroadcastToObservers() = runTest {
        val tracker = ObservableAnalyticsTracker(RecordingTracker())
        val collected = mutableListOf<AnalyticsEvent>()

        // The flow has no replay, so the subscriber must exist before the emit — same ordering the
        // production observer (CsatManager) has, since it subscribes at construction.
        backgroundScope.launch { tracker.events.collect { collected += it } }
        runCurrent()

        tracker.eventOutOfSession("comeback_fired", mapOf("id" to "7"))
        runCurrent()

        // CSAT decides what it observes by event NAME, not by which channel the emit took. The day a
        // background event joins CsatManager's weight map, this broadcast is what makes it work.
        assertEquals(listOf("comeback_fired"), collected.map { it.name })
    }
}
