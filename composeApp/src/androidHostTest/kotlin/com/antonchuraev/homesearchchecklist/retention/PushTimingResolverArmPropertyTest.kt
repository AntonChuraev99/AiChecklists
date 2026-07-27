package com.antonchuraev.homesearchchecklist.retention

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks the name split between the two push A/B arms, which collided in prod until 2026-07-27.
 *
 * The retention-push TIMING arm (client, Remote Config) used to be mirrored into the user-property
 * `push_ab_arm` — the same name the server uses for the per-send push-COPY arm (control | a | b).
 * Because a user-property carries no `push_ab_experiment` companion to disambiguate it,
 * `gp:push_ab_arm` read as "behavioral" for 190/190 users and every copy-A/B segmentation compared
 * the control arm against itself, with nothing in the output to reveal the mistake.
 */
class PushTimingResolverArmPropertyTest {

    @Test
    fun ensureStickyArm_writesTimingArmUnderItsOwnKey_neverPushAbArm() {
        val analytics = RecordingAnalyticsTracker()
        val resolver = createResolver(analytics, arm = PushTimingResolver.ARM_BEHAVIORAL)

        resolver.ensureStickyArm()

        val properties = analytics.userProperties.single()
        assertEquals(
            PushTimingResolver.ARM_BEHAVIORAL,
            properties[AnalyticsParams.PUSH_TIMING_ARM],
            "The timing arm must be published as its own user-property",
        )
        assertFalse(
            properties.containsKey(AnalyticsParams.PUSH_AB_ARM),
            "push_ab_arm is the server's event-scoped COPY arm — the client must never claim that " +
                "name as a user-property, or the copy A/B silently segments on the timing arm",
        )
    }

    @Test
    fun ensureStickyArm_isIdempotentPerProcess() {
        val analytics = RecordingAnalyticsTracker()
        val resolver = createResolver(analytics, arm = PushTimingResolver.ARM_FIXED)

        resolver.ensureStickyArm()
        resolver.ensureStickyArm()

        assertEquals(1, analytics.userProperties.size, "Sticky property must be written once per process")
        assertEquals(PushTimingResolver.ARM_FIXED, analytics.userProperties.single()[AnalyticsParams.PUSH_TIMING_ARM])
    }

    @Test
    fun experimentParams_keepEventScopedPushAbArmPairedWithItsExperiment() {
        val resolver = createResolver(RecordingAnalyticsTracker(), arm = PushTimingResolver.ARM_FIXED)

        val params = resolver.experimentParams()

        // On an EVENT the shared push_ab_arm key stays — it is unambiguous there because
        // push_ab_experiment says which experiment the arm belongs to. Renaming the user-property
        // must not disturb that convention.
        assertEquals(PushTimingResolver.EXPERIMENT_TIMING, params[AnalyticsParams.PUSH_AB_EXPERIMENT])
        assertEquals(PushTimingResolver.ARM_FIXED, params[AnalyticsParams.PUSH_AB_ARM])
        assertTrue(
            AnalyticsParams.PUSH_TIMING_ARM !in params.keys,
            "The user-property key has no business on an event — push_ab_experiment already scopes it",
        )
    }

    // ─── Fakes ───────────────────────────────────────────────────────────────

    private fun createResolver(analytics: AnalyticsTracker, arm: String): PushTimingResolver {
        val datastore = AppDatastore(
            PreferenceDataStoreFactory.createWithPath {
                "build/push_timing_test_${Random.nextLong()}.preferences_pb".toPath()
            },
            Dispatchers.Unconfined,
        )
        return PushTimingResolver(
            remoteConfig = StubRemoteConfig(arm),
            prefs = RetentionPrefs(datastore),
            analytics = analytics,
            logger = NoOpLogger(),
        )
    }

    private class StubRemoteConfig(private val arm: String) : RemoteConfigProvider {
        override suspend fun fetchAndActivate(): Boolean = true
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
        override fun getString(key: String, defaultValue: String): String =
            if (key == RemoteConfigKeys.PUSH_TIMING_ARM) arm else defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
    }

    private class RecordingAnalyticsTracker : AnalyticsTracker {
        val userProperties = mutableListOf<Map<String, Any>>()
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {
            userProperties.add(properties)
        }
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {}
    }

    private class NoOpLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }
}
