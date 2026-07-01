package com.antonchuraev.homesearchchecklist.core.common.impl

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AiExperimentPrefsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiModelExperimentTrackerImplTest {

    private fun tracker(
        analytics: RecordingAnalyticsTracker = RecordingAnalyticsTracker(),
        prefs: FakeAiExperimentPrefsRepository = FakeAiExperimentPrefsRepository(),
    ) = AiModelExperimentTrackerImpl(analytics = analytics, prefs = prefs, logger = NoOpLogger)

    @Test
    fun report_firstArm_setsStickyPropertyOnceAndPersists() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val prefs = FakeAiExperimentPrefsRepository()
        val t = tracker(analytics, prefs)

        t.report("variant_b", "gemini-3.1-flash-lite", "chat_agent")

        val variantCalls = analytics.userPropertyCallsFor("ai_model_variant")
        assertEquals(1, variantCalls.size, "sticky ai_model_variant must be set exactly once")
        assertEquals(mapOf("ai_model_variant" to "variant_b"), variantCalls.first())
        // Persisted for later (possibly AI-less) purchase attribution.
        assertEquals("variant_b", prefs.getModelVariant())
        assertEquals("gemini-3.1-flash-lite", prefs.getModelId())
    }

    @Test
    fun report_sameArmTwice_setsPropertyOnlyOnce() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val prefs = FakeAiExperimentPrefsRepository()
        val t = tracker(analytics, prefs)

        t.report("variant_b", "gemini-3.1-flash-lite", "chat_agent")
        t.report("variant_b", "gemini-3.1-flash-lite", "analyze") // duplicate arm, different flow

        assertEquals(
            1,
            analytics.userPropertyCallsFor("ai_model_variant").size,
            "duplicate arm within a session must NOT re-set the sticky property",
        )
        assertEquals(1, prefs.setCallCount, "duplicate arm must NOT re-persist")
    }

    @Test
    fun report_nullOrBlankArm_isNoOp() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val prefs = FakeAiExperimentPrefsRepository()
        val t = tracker(analytics, prefs)

        t.report(null, "gemini-3.1-flash-lite", "chat_agent")
        t.report("", "gemini-3.1-flash-lite", "chat_agent")
        t.report("   ", "gemini-3.1-flash-lite", "chat_agent")

        assertTrue(
            analytics.userPropertyCallsFor("ai_model_variant").isEmpty(),
            "experiment-off / older-server responses must not set the sticky property",
        )
        assertEquals(0, prefs.setCallCount)
    }

    @Test
    fun report_changedArm_reSetsProperty() = runTest {
        val analytics = RecordingAnalyticsTracker()
        val prefs = FakeAiExperimentPrefsRepository()
        val t = tracker(analytics, prefs)

        t.report("control", "gemini-2.5-flash-lite", "chat_agent")
        t.report("variant_b", "gemini-3.1-flash-lite", "chat_agent")

        val calls = analytics.userPropertyCallsFor("ai_model_variant")
        assertEquals(2, calls.size, "a genuinely different arm must re-set the property")
        assertEquals("variant_b", calls.last()["ai_model_variant"])
        assertEquals("variant_b", prefs.getModelVariant())
    }

    @Test
    fun current_afterReport_returnsPersistedArm() = runTest {
        val prefs = FakeAiExperimentPrefsRepository()
        val t = tracker(prefs = prefs)

        t.report("variant_b", "gemini-3.1-flash-lite", "generate")
        val arm = t.current()

        assertEquals("variant_b", arm?.variant)
        assertEquals("gemini-3.1-flash-lite", arm?.modelId)
    }

    @Test
    fun current_neverReported_returnsNull() = runTest {
        assertNull(tracker().current(), "unknown arm must be null, not a blank AiModelArm")
    }

    @Test
    fun current_variantWithoutModelId_returnsArmWithNullModelId() = runTest {
        val prefs = FakeAiExperimentPrefsRepository()
        val t = tracker(prefs = prefs)

        t.report("override", null, "chat_agent")
        val arm = t.current()

        assertEquals("override", arm?.variant)
        assertNull(arm?.modelId)
    }

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class RecordingAnalyticsTracker : AnalyticsTracker {
        val userPropertyCalls = mutableListOf<Map<String, Any>>()
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {
            userPropertyCalls.add(properties)
        }
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {}
        fun userPropertyCallsFor(key: String): List<Map<String, Any>> =
            userPropertyCalls.filter { it.containsKey(key) }
    }

    private class FakeAiExperimentPrefsRepository : AiExperimentPrefsRepository {
        private var variant: String? = null
        private var modelId: String? = null
        var setCallCount = 0
            private set
        override suspend fun getModelVariant(): String? = variant
        override suspend fun getModelId(): String? = modelId
        override suspend fun setModelArm(variant: String, modelId: String?) {
            this.variant = variant
            this.modelId = modelId
            setCallCount++
        }
    }

    private object NoOpLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }
}
