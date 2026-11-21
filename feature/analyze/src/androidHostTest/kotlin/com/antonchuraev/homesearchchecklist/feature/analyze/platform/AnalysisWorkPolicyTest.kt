package com.antonchuraev.homesearchchecklist.feature.analyze.platform

import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import androidx.work.OutOfQuotaPolicy
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The WorkManager shape of a background analysis. Each assertion pins a property whose loss is
 * invisible in a happy-path run: no expedite = minutes of delay in the background; FAIL instead of
 * RUN_AS_NON_EXPEDITED = enqueue throws once the quota is spent; no CONNECTED = an offline run burns
 * an attempt; a shared unique name = a second job KEEPs the first and never runs.
 *
 * Run: `./gradlew :feature:analyze:testAndroidHostTest --tests "*AnalysisWorkPolicyTest*"`
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnalysisWorkPolicyTest {

    @Test
    fun request_isExpeditedWithNonExpeditedFallback_networkBound_boundedBackoff() {
        val spec = AnalysisWorkPolicy.buildRequest("job-1").workSpec

        assertTrue(spec.expedited)
        assertEquals(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, spec.outOfQuotaPolicy)
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(10_000L, spec.backoffDelayDuration)
        assertEquals(0L, spec.initialDelay)
        assertEquals("job-1", spec.input.getString(AnalysisWorkPolicy.KEY_JOB_ID))
        assertEquals(AnalysisWorker::class.java.name, spec.workerClassName)
    }

    @Test
    fun uniqueName_isPerJob() {
        assertNotEquals(AnalysisWorkPolicy.uniqueWorkName("a"), AnalysisWorkPolicy.uniqueWorkName("b"))
        assertEquals(AnalysisWorkPolicy.uniqueWorkName("a"), AnalysisWorkPolicy.uniqueWorkName("a"))
    }

    @Test
    fun lastAttempt_isTheFifthRun() {
        // runAttemptCount is 0-based: runs 1..4 may retry, run 5 must write the final status.
        (0 until AnalysisWorkPolicy.MAX_ATTEMPTS - 1).forEach { assertFalse(AnalysisWorkPolicy.isLastAttempt(it)) }
        assertTrue(AnalysisWorkPolicy.isLastAttempt(AnalysisWorkPolicy.MAX_ATTEMPTS - 1))
        assertTrue(AnalysisWorkPolicy.isLastAttempt(AnalysisWorkPolicy.MAX_ATTEMPTS + 3))
    }
}
