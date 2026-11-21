package com.antonchuraev.homesearchchecklist.feature.analyze.data.job

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobAnalytics
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobFailure
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobInput
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobLimitReached
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobRequest
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobRequestInput
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobStatus
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobTarget
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.DeliveryClaimant
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.StoredFileKind
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiAnalyzeError
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalysisJobManagerTest {

    private val store = InMemoryAnalysisJobStore()
    private val payloads = InMemoryAnalysisPayloadStore()
    private val launcher = RecordingLauncher()
    private val tracker = RecordingTracker()
    private var ids = 0

    private fun CoroutineScope.manager(files: Map<String, ByteArray> = emptyMap(), maxRunning: Int = 3) = AnalysisJobManager(
        store = store,
        payloadStore = payloads,
        launcher = launcher,
        analyticsTracker = tracker,
        logger = SilentAppLogger,
        scope = this,
        readFileBytes = { files[it] },
        readFileText = { null },
        now = { 0L },
        newId = { "job-${++ids}" },
        maxRunning = maxRunning,
    )

    private fun textRequest(text: String = "trip") = AnalysisJobRequest(
        input = AnalysisJobRequestInput.Text(text),
        target = AnalysisJobTarget.NewChecklist(fromActivationHero = false),
        analytics = AnalysisJobAnalytics("raw_text", "none"),
    )

    /** FEATURE_SPEC §6.12: screen, snackbar and notification race for one outcome — one wins. */
    @Test
    fun claimDelivery_hasExactlyOneWinner() = runTest {
        val sut = backgroundScope.manager()
        val id = sut.enqueue(textRequest()).getOrThrow()
        store.update(id) { it.copy(status = AnalysisJobStatus.Failed(AnalysisJobFailure(AiFailureReason.NETWORK, retryableWithSameInput = true))) }

        val wins = DeliveryClaimant.entries.map { claimant -> async { sut.claimDelivery(id, claimant) } }.awaitAll()

        assertEquals(1, wins.count { it }, "exactly one claimant may deliver")
        assertTrue(store.get(id)?.deliveredBy != null)
    }

    @Test
    fun claimDelivery_ofARunningJob_isRefused() = runTest {
        val sut = backgroundScope.manager()
        val id = sut.enqueue(textRequest()).getOrThrow()

        assertEquals(false, sut.claimDelivery(id, DeliveryClaimant.SCREEN))
    }

    @Test
    fun enqueue_persistsBeforeLaunching_andCopiesTheFile() = runTest {
        val bytes = byteArrayOf(7, 7, 7)
        val sut = backgroundScope.manager(files = mapOf("/cache/a.jpg" to bytes))

        val id = sut.enqueue(
            AnalysisJobRequest(
                input = AnalysisJobRequestInput.File(StoredFileKind.PHOTO, "/cache/a.jpg", "a.jpg", "image/jpeg"),
                target = AnalysisJobTarget.NewChecklist(fromActivationHero = false),
                analytics = AnalysisJobAnalytics("photo", "none"),
            ),
        ).getOrThrow()

        assertEquals(listOf(id), launcher.launched)
        assertIs<AnalysisJobInput.StoredFile>(store.get(id)?.input)
        assertContentEquals(bytes, payloads.read(id))
    }

    @Test
    fun enqueue_unreadableFile_failsLocally_nothingStoredOrLaunched() = runTest {
        val sut = backgroundScope.manager()

        val result = sut.enqueue(
            AnalysisJobRequest(
                input = AnalysisJobRequestInput.File(StoredFileKind.PDF, "/gone.pdf", "gone.pdf", "application/pdf"),
                target = AnalysisJobTarget.NewChecklist(fromActivationHero = false),
                analytics = AnalysisJobAnalytics("pdf", "none"),
            ),
        )

        assertIs<AiAnalyzeError.InputUnreadable>(result.exceptionOrNull())
        assertTrue(launcher.launched.isEmpty())
    }

    @Test
    fun enqueue_beyondTheCap_isRefused() = runTest {
        val sut = backgroundScope.manager(maxRunning = 2)
        sut.enqueue(textRequest("a")).getOrThrow()
        sut.enqueue(textRequest("b")).getOrThrow()

        val third = sut.enqueue(textRequest("c"))

        assertEquals(2, assertIs<AnalysisJobLimitReached>(third.exceptionOrNull()).max)
        assertEquals(2, launcher.launched.size)
    }

    @Test
    fun cancel_queuedJob_prunesIt_andClosesTheFunnel() = runTest {
        val sut = backgroundScope.manager()
        val id = sut.enqueue(textRequest()).getOrThrow()

        assertTrue(sut.cancel(id))

        assertNull(store.get(id))
        assertEquals(listOf(id), launcher.cancelled)
        assertEquals("cancelled", tracker.events.single { it.first == AnalyticsEvents.Analyze.FAILED }.second["failure_reason"])
    }

    @Test
    fun cancel_sentJob_isRefused() = runTest {
        val sut = backgroundScope.manager()
        val id = sut.enqueue(textRequest()).getOrThrow()
        store.update(id) { it.copy(status = AnalysisJobStatus.Running) }

        assertEquals(false, sut.cancel(id))
    }

    @Test
    fun retry_isANewJobWithANewIdAndTheSameCapturedBytes() = runTest {
        val bytes = byteArrayOf(4, 2)
        val sut = backgroundScope.manager(files = mapOf("/cache/v.m4a" to bytes))
        val old = sut.enqueue(
            AnalysisJobRequest(
                input = AnalysisJobRequestInput.File(StoredFileKind.AUDIO, "/cache/v.m4a", "v.m4a", "audio/m4a"),
                target = AnalysisJobTarget.NewChecklist(fromActivationHero = false),
                analytics = AnalysisJobAnalytics("voice", "none"),
            ),
        ).getOrThrow()
        store.update(old) { it.copy(status = AnalysisJobStatus.Failed(AnalysisJobFailure(AiFailureReason.NETWORK, retryableWithSameInput = true))) }

        val new = sut.retry(old).getOrThrow()

        assertNotEquals(old, new, "a retry is a new server request_id")
        assertContentEquals(bytes, payloads.read(new))
        assertNull(store.get(old), "the old job is acknowledged")
        assertEquals(AnalysisJobStatus.Queued(false), store.get(new)?.status)
    }

    @Test
    fun resumePending_relaunchesUnfinished_andFailsFileJobsWithoutBytes() = runTest {
        store.put(queuedJob("text", status = AnalysisJobStatus.Running))
        store.put(queuedJob("file", input = AnalysisJobInput.StoredFile(StoredFileKind.PDF, "a.pdf", "application/pdf")))
        store.put(queuedJob("done", status = AnalysisJobStatus.Cancelled))
        val sut = backgroundScope.manager()

        sut.resumePending()

        assertEquals(listOf("text"), launcher.launched)
        val failed = assertIs<AnalysisJobStatus.Failed>(store.get("file")?.status)
        assertEquals(AiFailureReason.INTERRUPTED, failed.failure.reason)
        assertEquals("interrupted", tracker.events.single { it.first == AnalyticsEvents.Analyze.FAILED }.second["failure_reason"])
    }
}
