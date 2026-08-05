package com.antonchuraev.homesearchchecklist.feature.user.domain.experiment

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.NavVariant
import com.antonchuraev.homesearchchecklist.core.datastore.api.NavExperimentPrefsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the resolver AFTER the 2026-08-03 direction change: the navigation shell is a persisted
 * USER SETTING, not a Remote Config A/B arm
 * (`docs/decisions/2026-08-03-shift-from-ai-first-to-checklist-first.md`).
 *
 * The previous suite tested the experiment machinery — the "empty RC value must never be persisted
 * as control" invariant, the unassigned negative-cache backoff, and RC read counts. All three
 * describe code that no longer exists; keeping them would have meant asserting on a mechanism the
 * product deliberately removed.
 */
class NavExperimentResolverImplTest {

    // ---------------------------------------------------------------------
    // The invariant this class exists to protect
    // ---------------------------------------------------------------------

    /**
     * v2 is the product; v1 is the opt-out. An install that has never opened Settings must get v2.
     *
     * This inverts the old experiment default, and getting it wrong is silent: falling back to v1
     * would ship the previous app to every user who never touched the switch, and nothing would
     * fail — it would simply look like the redesign was never released.
     */
    @Test
    fun ensureResolved_nothingStored_returnsV2() = runTest {
        val resolver = resolver(prefs = FakeNavPrefs(stored = null))

        assertEquals(NavVariant.V2, resolver.ensureResolved())
    }

    /** Before resolution runs, the non-suspending read must also report v2 — not a flash of v1. */
    @Test
    fun currentArm_beforeResolution_returnsV2() {
        val resolver = resolver(prefs = FakeNavPrefs(stored = null))

        assertEquals(NavVariant.V2, resolver.currentArm())
        assertFalse(resolver.isArmAssigned(), "nothing has been read yet")
    }

    @Test
    fun ensureResolved_storedControl_returnsControl() = runTest {
        val resolver = resolver(prefs = FakeNavPrefs(stored = "control"))

        assertEquals(NavVariant.CONTROL, resolver.ensureResolved())
    }

    @Test
    fun ensureResolved_storedV2_returnsV2() = runTest {
        val resolver = resolver(prefs = FakeNavPrefs(stored = "v2"))

        assertEquals(NavVariant.V2, resolver.ensureResolved())
    }

    /**
     * A value written by a future version (or a corrupted one) must degrade to the DEFAULT, not to
     * v1. Downgrading an unknown string to the old shell would turn any future rename of the wire
     * value into a silent mass rollback.
     */
    @Test
    fun ensureResolved_unknownStoredValue_fallsBackToDefault() = runTest {
        val resolver = resolver(prefs = FakeNavPrefs(stored = "v3-experimental"))

        assertEquals(NavVariant.V2, resolver.ensureResolved())
    }

    /**
     * A BLANK stored value is "never chose", not "corrupt".
     *
     * The empty string is the prefs layer's own absent sentinel, so it can reach the resolver from any
     * writer that clears the choice. Treating it as unrecognised resolved to the same variant but
     * logged a corruption warning on every launch of an install that had simply never opened Settings
     * — noise that would bury a real corrupt value when one shows up.
     */
    @Test
    fun ensureResolved_blankStoredValue_isTreatedAsAbsent() = runTest {
        val logger = RecordingLogger()
        val resolver = resolver(prefs = FakeNavPrefs(stored = ""), logger = logger)

        assertEquals(NavVariant.V2, resolver.ensureResolved())
        assertTrue(logger.warnings.isEmpty(), "blank is absent, not corrupt: ${logger.warnings}")
    }

    /** DataStore being unreadable must not break navigation — it renders the default instead. */
    @Test
    fun ensureResolved_datastoreThrows_stillResolvesToDefault() = runTest {
        val resolver = resolver(prefs = ThrowingNavPrefs())

        assertEquals(NavVariant.V2, resolver.ensureResolved())
    }

    // ---------------------------------------------------------------------
    // Caching
    // ---------------------------------------------------------------------

    /**
     * The stored value is read ONCE per process. Re-reading it would let a write from another screen
     * swap the whole shell out from under a live screen — the failure mode the resolver is shaped
     * to prevent.
     */
    @Test
    fun ensureResolved_calledRepeatedly_readsStorageOnce() = runTest {
        val prefs = FakeNavPrefs(stored = "control")
        val resolver = resolver(prefs = prefs)

        repeat(5) { resolver.ensureResolved() }

        assertEquals(1, prefs.readCount)
    }

    @Test
    fun isArmAssigned_afterResolution_isTrue() = runTest {
        val resolver = resolver(prefs = FakeNavPrefs(stored = null))

        resolver.ensureResolved()

        assertTrue(resolver.isArmAssigned())
    }

    // ---------------------------------------------------------------------
    // setVariant — the Settings switch
    // ---------------------------------------------------------------------

    /**
     * The switch must take effect for THIS process, not only after a restart: the cached value is
     * what the shell reads on the next recomposition.
     */
    @Test
    fun setVariant_updatesCacheAndPersists() = runTest {
        val prefs = FakeNavPrefs(stored = null)
        val resolver = resolver(prefs = prefs)
        resolver.ensureResolved()

        resolver.setVariant(NavVariant.CONTROL)

        assertEquals(NavVariant.CONTROL, resolver.currentArm())
        assertEquals("control", prefs.stored)
    }

    @Test
    fun setVariant_backToV2_persistsV2() = runTest {
        val prefs = FakeNavPrefs(stored = "control")
        val resolver = resolver(prefs = prefs)
        resolver.ensureResolved()

        resolver.setVariant(NavVariant.V2)

        assertEquals(NavVariant.V2, resolver.currentArm())
        assertEquals("v2", prefs.stored)
    }

    /**
     * A failed write must not be reported as applied.
     *
     * The cache is what every caller reads back, so advancing it on a failed write would switch the
     * shell now and lose the setting on the next launch — a change the user made, silently undone.
     * Dropping it instead keeps `currentArm()` honest, which is how Settings knows to tell them.
     */
    @Test
    fun setVariant_persistFails_doesNotApply() = runTest {
        val resolver = resolver(prefs = ThrowingNavPrefs())
        resolver.ensureResolved()

        resolver.setVariant(NavVariant.CONTROL)

        assertEquals(NavVariant.V2, resolver.currentArm())
    }

    /**
     * The sticky `nav_arm` user property is written once per process and NOT re-written on toggle.
     *
     * A user who flips the switch a few times would otherwise emit a stream of contradictory
     * property values, and every dashboard reading it would attribute their whole session to
     * whichever write happened to land last. `Settings.NAV_VARIANT_SELECTED` is the event that
     * records switches.
     */
    @Test
    fun setVariant_doesNotRewriteStickyUserProperty() = runTest {
        val analytics = RecordingAnalytics()
        val resolver = resolver(prefs = FakeNavPrefs(stored = null), analytics = analytics)
        resolver.ensureResolved()
        assertEquals(1, analytics.userPropertyCalls.size)

        resolver.setVariant(NavVariant.CONTROL)
        resolver.setVariant(NavVariant.V2)

        assertEquals(1, analytics.userPropertyCalls.size)
    }

    // ---------------------------------------------------------------------
    // clearVariant — the debug screen's "remove override"
    // ---------------------------------------------------------------------

    /**
     * Clearing must move BOTH layers, which is the whole reason it exists as a resolver call.
     *
     * The debug screen used to clear by writing DataStore itself; that left this cache still reporting
     * the variant it had just deleted, so the Settings switch (which seeds from `currentArm()`) showed
     * an override that was no longer stored.
     */
    @Test
    fun clearVariant_clearsStorageAndResetsCacheToDefault() = runTest {
        val prefs = FakeNavPrefs(stored = "control")
        val resolver = resolver(prefs = prefs)
        resolver.ensureResolved()
        assertEquals(NavVariant.CONTROL, resolver.currentArm())

        resolver.clearVariant()

        assertEquals(NavVariant.V2, resolver.currentArm())
        // The empty string, not a literal "v2": absence must read back as "never chose" so a later
        // change of DEFAULT_VARIANT reaches installs that never made a choice.
        assertEquals("", prefs.stored)
    }

    /** Same failure contract as setVariant: a write that does not land changes nothing. */
    @Test
    fun clearVariant_persistFails_leavesStoredChoiceInEffect() = runTest {
        val prefs = FakeNavPrefs(stored = "control", writeFails = true)
        val resolver = resolver(prefs = prefs)
        resolver.ensureResolved()

        resolver.clearVariant()

        assertEquals(NavVariant.CONTROL, resolver.currentArm())
        assertEquals("control", prefs.stored)
    }

    // ---------------------------------------------------------------------
    // Analytics mirroring
    // ---------------------------------------------------------------------

    @Test
    fun ensureResolved_mirrorsVariantIntoUserPropertyOnce() = runTest {
        val analytics = RecordingAnalytics()
        val resolver = resolver(prefs = FakeNavPrefs(stored = "control"), analytics = analytics)

        repeat(3) { resolver.ensureResolved() }

        assertEquals(1, analytics.userPropertyCalls.size)
        assertEquals("control", analytics.userPropertyCalls.single()[AnalyticsParams.NAV_ARM])
    }

    /**
     * The tracker may not be initialised on a very early call. A failed mirror must RESET the guard
     * so a later pass retries — otherwise the property is lost for the whole process.
     */
    @Test
    fun mirror_failsFirstTime_retriesOnNextCall() = runTest {
        val analytics = RecordingAnalytics(failTimes = 1)
        val resolver = resolver(prefs = FakeNavPrefs(stored = "v2"), analytics = analytics)

        resolver.ensureResolved()
        assertTrue(analytics.userPropertyCalls.isEmpty(), "first attempt threw")

        resolver.ensureResolved()
        assertEquals(1, analytics.userPropertyCalls.size)
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun resolver(
        prefs: NavExperimentPrefsRepository,
        analytics: AnalyticsTracker = RecordingAnalytics(),
        logger: AppLogger = NoOpLogger(),
    ) = NavExperimentResolverImpl(
        prefs = prefs,
        analytics = analytics,
        logger = logger,
    )

    private class FakeNavPrefs(
        var stored: String? = null,
        /** Reads still succeed — the case where a resolved install cannot persist a NEW choice. */
        private val writeFails: Boolean = false,
    ) : NavExperimentPrefsRepository {
        var readCount = 0
            private set

        override suspend fun getNavArm(): String? {
            readCount++
            return stored
        }

        override suspend fun setNavArm(arm: String) {
            if (writeFails) throw IllegalStateException("datastore read-only")
            stored = arm
        }
    }

    private class ThrowingNavPrefs : NavExperimentPrefsRepository {
        override suspend fun getNavArm(): String? = throw IllegalStateException("datastore corrupt")
        override suspend fun setNavArm(arm: String) = throw IllegalStateException("datastore corrupt")
    }

    private class RecordingAnalytics(private var failTimes: Int = 0) : AnalyticsTracker {
        val userPropertyCalls = mutableListOf<Map<String, Any>>()

        override fun setUserId(userId: String) = Unit
        override fun setUserProperties(properties: Map<String, Any>) {
            if (failTimes > 0) {
                failTimes--
                throw IllegalStateException("tracker not ready")
            }
            userPropertyCalls += properties
        }

        override fun screenView(name: String) = Unit
        override fun event(name: String, params: Map<String, Any>) = Unit
    }

    private class NoOpLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }

    /** Only warnings are collected — they are the corruption signal a blank value must not trip. */
    private class RecordingLogger : AppLogger {
        val warnings = mutableListOf<String>()

        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {
            warnings += message
        }

        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }
}
