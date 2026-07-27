package com.antonchuraev.homesearchchecklist.feature.user.domain.experiment

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.NavVariant
import com.antonchuraev.homesearchchecklist.core.datastore.api.NavExperimentPrefsRepository
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.GetNavVariantUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavExperimentResolverImplTest {

    // ---------------------------------------------------------------------
    // The invariant this whole class exists to protect
    // ---------------------------------------------------------------------

    /**
     * The experiment-killing bug: SplashViewModel only awaits `fetchAndActivate()` for users who
     * have not passed onboarding, so an existing user routinely reaches the shell with Remote
     * Config still un-activated and `nav_v2_arm` empty. If that empty read were persisted as
     * "control", the entire installed base would be pinned to control forever and the experiment
     * would read 100/0.
     */
    @Test
    fun ensureResolved_rcEmpty_returnsControlButPersistsNothing() = runTest {
        val prefs = FakeNavPrefs()
        val analytics = RecordingAnalytics()
        val resolver = resolver(rcValue = "", prefs = prefs, analytics = analytics)

        val arm = resolver.ensureResolved()

        assertEquals(NavVariant.CONTROL, arm, "an unassigned arm must render the safe shell")
        assertNull(prefs.stored, "an un-activated RC read must NEVER be persisted as control")
        assertTrue(
            analytics.userPropertyCalls.isEmpty(),
            "unassigned users must carry no nav_arm — they are the rc-activation-gap population " +
                "and must be excluded from the analysis, not counted as control",
        )
    }

    /** An unassigned read must leave the resolver unresolved so a later call can still pick up the arm. */
    @Test
    fun ensureResolved_rcEmptyThenAssigned_picksUpTheArmOnRetry() = runTest {
        val prefs = FakeNavPrefs()
        val rc = MutableRemoteConfig(value = "")
        val clock = FakeClock()
        val resolver = NavExperimentResolverImpl(
            getNavVariant = GetNavVariantUseCase(rc, NoOpLogger()),
            prefs = prefs,
            analytics = RecordingAnalytics(),
            logger = NoOpLogger(),
            nowMs = clock::now,
        )

        assertEquals(NavVariant.CONTROL, resolver.ensureResolved())
        assertEquals(NavVariant.CONTROL, resolver.currentArm(), "must stay unresolved, not cached")

        rc.value = "v2" // the fetch finally landed
        clock.advance(NavExperimentResolverImpl.UNASSIGNED_BACKOFF_MS) // past the negative cache

        assertEquals(NavVariant.V2, resolver.ensureResolved())
        assertEquals("v2", prefs.stored)
    }

    // ---------------------------------------------------------------------
    // Negative cache for the rc-activation-gap population
    // ---------------------------------------------------------------------

    /**
     * An unassigned attempt must not be repeated on every call: for the ~35% of users whose Remote
     * Config never activates, nothing is ever cached, so each call would otherwise pay a suspending
     * DataStore read + an RC read forever.
     */
    @Test
    fun ensureResolved_unassigned_backsOffInsteadOfReAttemptingEveryCall() = runTest {
        val prefs = FakeNavPrefs()
        val rc = MutableRemoteConfig(value = "")
        val clock = FakeClock()
        val resolver = NavExperimentResolverImpl(
            getNavVariant = GetNavVariantUseCase(rc, NoOpLogger()),
            prefs = prefs,
            analytics = RecordingAnalytics(),
            logger = NoOpLogger(),
            nowMs = clock::now,
        )

        assertEquals(NavVariant.CONTROL, resolver.ensureResolved())
        assertEquals(1, rc.readCount)
        assertEquals(1, prefs.readCount)

        // Three more calls inside the window — the shell still renders CONTROL, at zero I/O cost.
        clock.advance(NavExperimentResolverImpl.UNASSIGNED_BACKOFF_MS - 1)
        repeat(3) { assertEquals(NavVariant.CONTROL, resolver.ensureResolved()) }
        assertEquals(1, rc.readCount, "calls inside the backoff window must not re-read Remote Config")
        assertEquals(1, prefs.readCount, "calls inside the backoff window must not re-read DataStore")

        // Nothing is persisted either way, so stickiness is unaffected by the backoff.
        assertNull(prefs.stored, "the negative cache must never turn into a persisted control arm")

        // Past the window the round-trip resumes.
        clock.advance(1L)
        assertEquals(NavVariant.CONTROL, resolver.ensureResolved())
        assertEquals(2, rc.readCount, "the window must expire, not latch")
    }

    /**
     * A backwards clock jump (NTP correction, user changing the date) must expire the window rather
     * than pin it: a negative elapsed time satisfies a bare `elapsed < BACKOFF` test, which would
     * freeze the resolver in the window until the clock caught back up.
     */
    @Test
    fun ensureResolved_clockJumpsBackwards_expiresTheBackoffWindow() = runTest {
        val rc = MutableRemoteConfig(value = "")
        val clock = FakeClock(startAt = 10_000_000L)
        val resolver = NavExperimentResolverImpl(
            getNavVariant = GetNavVariantUseCase(rc, NoOpLogger()),
            prefs = FakeNavPrefs(),
            analytics = RecordingAnalytics(),
            logger = NoOpLogger(),
            nowMs = clock::now,
        )

        assertEquals(NavVariant.CONTROL, resolver.ensureResolved())
        assertEquals(1, rc.readCount)

        clock.advance(-5_000_000L) // clock corrected backwards
        assertEquals(NavVariant.CONTROL, resolver.ensureResolved())
        assertEquals(2, rc.readCount, "a backwards clock must not freeze the resolver in the window")
    }

    /**
     * A console typo is a mistake, not an assignment. Caching or persisting it would make the
     * mistake permanent for the install even after the console is fixed.
     */
    @Test
    fun ensureResolved_unknownRcValue_returnsControlAndPersistsNothing() = runTest {
        val prefs = FakeNavPrefs()
        val resolver = resolver(rcValue = "V2_beta", prefs = prefs)

        assertEquals(NavVariant.CONTROL, resolver.ensureResolved())
        assertNull(prefs.stored)
    }

    // ---------------------------------------------------------------------
    // Stickiness
    // ---------------------------------------------------------------------

    @Test
    fun ensureResolved_assignedArm_isCachedPersistedAndMirroredOnce() = runTest {
        val prefs = FakeNavPrefs()
        val analytics = RecordingAnalytics()
        val resolver = resolver(rcValue = "v2", prefs = prefs, analytics = analytics)

        assertEquals(NavVariant.V2, resolver.ensureResolved())
        assertEquals(NavVariant.V2, resolver.ensureResolved()) // idempotent re-entry
        assertEquals(NavVariant.V2, resolver.currentArm())

        assertEquals("v2", prefs.stored)
        assertEquals(1, prefs.writeCount, "a resolved arm must not be re-persisted on every call")
        assertEquals(
            listOf<Map<String, Any>>(mapOf("nav_arm" to "v2")),
            analytics.userPropertyCalls,
            "nav_arm must be set exactly once per process",
        )
    }

    /** Control is an arm too: without the property a breakdown compares treatment vs "undefined". */
    @Test
    fun ensureResolved_controlArm_alsoMirrorsUserProperty() = runTest {
        val analytics = RecordingAnalytics()
        val prefs = FakeNavPrefs()
        val resolver = resolver(rcValue = "control", prefs = prefs, analytics = analytics)

        assertEquals(NavVariant.CONTROL, resolver.ensureResolved())
        assertEquals("control", prefs.stored)
        assertEquals(listOf<Map<String, Any>>(mapOf("nav_arm" to "control")), analytics.userPropertyCalls)
    }

    /**
     * Once persisted, Remote Config must never be consulted again — otherwise a mid-session
     * `fetchAndActivate()` could swap the whole navigation shell out from under the user.
     */
    @Test
    fun ensureResolved_persistedArmWins_overADisagreeingRemoteConfig() = runTest {
        val prefs = FakeNavPrefs(stored = "v2")
        val rc = MutableRemoteConfig(value = "control") // console flipped after assignment
        val resolver = NavExperimentResolverImpl(
            getNavVariant = GetNavVariantUseCase(rc, NoOpLogger()),
            prefs = prefs,
            analytics = RecordingAnalytics(),
            logger = NoOpLogger(),
        )

        assertEquals(NavVariant.V2, resolver.ensureResolved())
        assertEquals(0, rc.readCount, "a persisted arm must short-circuit the RC read entirely")
    }

    /** A corrupted / future-version stored value must degrade to today's navigation. */
    @Test
    fun ensureResolved_corruptedPersistedArm_readsAsControl() = runTest {
        val resolver = resolver(rcValue = "v2", prefs = FakeNavPrefs(stored = "v3_unreleased"))

        assertEquals(NavVariant.CONTROL, resolver.ensureResolved())
    }

    // ---------------------------------------------------------------------
    // Never break navigation
    // ---------------------------------------------------------------------

    @Test
    fun ensureResolved_datastoreThrows_stillResolvesFromRemoteConfig() = runTest {
        val resolver = resolver(rcValue = "v2", prefs = ThrowingNavPrefs())

        assertEquals(
            NavVariant.V2,
            resolver.ensureResolved(),
            "a broken DataStore must cost stickiness, never the shell",
        )
    }

    @Test
    fun ensureResolved_analyticsThrows_stillResolvesAndRetriesTheProperty() = runTest {
        val analytics = RecordingAnalytics(failTimes = 1)
        val resolver = resolver(rcValue = "v2", analytics = analytics)

        assertEquals(NavVariant.V2, resolver.ensureResolved())
        // The guard is reset on failure (PushTimingResolver pattern), so a later pass retries.
        resolver.ensureResolved()
        assertEquals(
            listOf<Map<String, Any>>(mapOf("nav_arm" to "v2")),
            analytics.userPropertyCalls,
            "the retry must land exactly one successful nav_arm set",
        )
    }

    // ---------------------------------------------------------------------
    // Regression guard on the client default
    // ---------------------------------------------------------------------

    /** If someone gives NAV_V2_ARM a non-empty client default, "not assigned" becomes unrepresentable. */
    @Test
    fun clientDefaultForNavArm_staysEmpty() {
        assertEquals(
            "",
            RemoteConfigDefaults.NAV_V2_ARM,
            "a non-empty client default would enrol every un-fetched user into a real arm",
        )
    }

    // --- helpers / test doubles ---

    private fun resolver(
        rcValue: String,
        prefs: NavExperimentPrefsRepository = FakeNavPrefs(),
        analytics: AnalyticsTracker = RecordingAnalytics(),
    ) = NavExperimentResolverImpl(
        getNavVariant = GetNavVariantUseCase(MutableRemoteConfig(rcValue), NoOpLogger()),
        prefs = prefs,
        analytics = analytics,
        logger = NoOpLogger(),
    )

    private class MutableRemoteConfig(var value: String) : RemoteConfigProvider {
        var readCount = 0
            private set

        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
        override fun getString(key: String, defaultValue: String): String =
            if (key == RemoteConfigKeys.NAV_V2_ARM) {
                readCount++
                value
            } else {
                defaultValue
            }
    }

    private class FakeNavPrefs(var stored: String? = null) : NavExperimentPrefsRepository {
        var writeCount = 0
            private set
        var readCount = 0
            private set

        override suspend fun getNavArm(): String? {
            readCount++
            return stored
        }

        override suspend fun setNavArm(arm: String) {
            writeCount++
            stored = arm
        }
    }

    /** Hand-cranked wall clock — the backoff is measured in real ms, which `runTest` does not move. */
    private class FakeClock(startAt: Long = 1_000_000L) {
        private var value = startAt
        fun now(): Long = value
        fun advance(byMs: Long) {
            value += byMs
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
}
