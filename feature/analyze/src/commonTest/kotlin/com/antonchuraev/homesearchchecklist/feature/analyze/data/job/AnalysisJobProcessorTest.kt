package com.antonchuraev.homesearchchecklist.feature.analyze.data.job

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobInput
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobOutcome
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobStatus
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ImageDownscaler
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ProcessResult
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.StoredFileKind
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiAnalyzeError
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.PreparedAnalyzeInput
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobTarget

/** FEATURE_SPEC §6.11: idempotency per job id, retries keep the id, the last attempt fails for real. */
class AnalysisJobProcessorTest {

    private val store = InMemoryAnalysisJobStore()
    private val payloads = InMemoryAnalysisPayloadStore()
    private val tracker = RecordingTracker()
    private val notifier = RecordingNotifier()
    private val foreground = FakeForeground()

    private fun processor(repo: ScriptedAnalyzeRepository, downscaler: ImageDownscaler = PassThroughImageDownscaler()) =
        AnalysisJobProcessorImpl(
            store = store,
            payloadStore = payloads,
            downscaler = downscaler,
            analyzeRepository = repo,
            findChecklist = { null },
            analyticsTracker = tracker,
            foregroundMonitor = foreground,
            notifier = notifier,
            logger = SilentAppLogger,
            defaultChecklistName = { "Checklist" },
        )

    @Test
    fun everyAttempt_sendsTheSameRequestId_andRetryLaterKeepsIt() = runTest {
        store.put(queuedJob("job-7"))
        val repo = ScriptedAnalyzeRepository(listOf(Result.failure(AiAnalyzeError.Network), successResult()))
        val sut = processor(repo)

        assertEquals(ProcessResult.RetryLater, sut.process("job-7", isLastAttempt = false))
        assertEquals(AnalysisJobStatus.Queued(waitingForNetwork = true), store.get("job-7")?.status)

        assertEquals(ProcessResult.Done, sut.process("job-7", isLastAttempt = false))
        assertEquals(listOf<String?>("job-7", "job-7"), repo.requestIds)
        assertIs<AnalysisJobStatus.Succeeded>(store.get("job-7")?.status)
        assertEquals(2, store.get("job-7")?.attempt)
    }

    @Test
    fun secondProcessOfASucceededJob_writesNoSecondChecklist() = runTest {
        store.put(queuedJob("job-1"))
        val repo = ScriptedAnalyzeRepository(successResult())
        val sut = processor(repo)

        sut.process("job-1", isLastAttempt = false)
        assertEquals(ProcessResult.Done, sut.process("job-1", isLastAttempt = false))

        assertEquals(1, repo.createCount, "a re-run after process death must not create a second checklist")
        assertEquals(1, repo.requestIds.size, "a terminal job is not sent again")
        assertEquals(1, tracker.events.count { it.first == AnalyticsEvents.Analyze.COMPLETED })
        val outcome = (store.get("job-1")?.status as AnalysisJobStatus.Succeeded).outcome
        assertEquals(101L, (outcome as AnalysisJobOutcome.CreatedChecklist).checklistId)
    }

    @Test
    fun lastAttempt_transportFailure_writesFailed_andEmitsFailedOnce() = runTest {
        store.put(queuedJob("job-2"))
        val sut = processor(ScriptedAnalyzeRepository(Result.failure(AiAnalyzeError.Network)))

        assertEquals(ProcessResult.Done, sut.process("job-2", isLastAttempt = true))

        val failed = assertIs<AnalysisJobStatus.Failed>(store.get("job-2")?.status)
        assertEquals(AiFailureReason.NETWORK, failed.failure.reason)
        assertTrue(failed.failure.retryableWithSameInput)
        val events = tracker.events.filter { it.first == AnalyticsEvents.Analyze.FAILED }
        assertEquals(1, events.size)
        assertEquals("network", events.single().second["failure_reason"])
        assertEquals("foreground", events.single().second["app_state"])
    }

    @Test
    fun inputFailure_isNotRetried_evenBeforeTheLastAttempt() = runTest {
        store.put(queuedJob("job-3"))
        val sut = processor(ScriptedAnalyzeRepository(Result.failure(AiAnalyzeError.Http(413, "too big", "input_too_large"))))

        assertEquals(ProcessResult.Done, sut.process("job-3", isLastAttempt = false))

        val failed = assertIs<AnalysisJobStatus.Failed>(store.get("job-3")?.status)
        assertEquals(AiFailureReason.INPUT_TOO_LARGE, failed.failure.reason)
        assertEquals(false, failed.failure.retryableWithSameInput)
    }

    @Test
    fun finishedInBackground_notifies_inForeground_doesNot() = runTest {
        store.put(queuedJob("bg"))
        store.put(queuedJob("fg"))
        val repo = ScriptedAnalyzeRepository(successResult())

        foreground.isInForeground.value = false
        processor(repo).process("bg", isLastAttempt = false)
        foreground.isInForeground.value = true
        processor(repo).process("fg", isLastAttempt = false)

        assertEquals(listOf("bg"), notifier.notified)
        val completed = tracker.events.filter { it.first == AnalyticsEvents.Analyze.COMPLETED }.map { it.second["app_state"] }
        assertEquals(listOf<Any?>("background", "foreground"), completed)
    }

    @Test
    fun photo_isDownscaledBeforeSending_andAFailedDownscaleSendsTheOriginal() = runTest {
        val original = byteArrayOf(1, 2, 3, 4)
        val small = byteArrayOf(9)
        val photo = AnalysisJobInput.StoredFile(StoredFileKind.PHOTO, "p.jpg", "image/jpeg")
        store.put(queuedJob("a", input = photo))
        store.put(queuedJob("b", input = photo))
        payloads.put("a", original)
        payloads.put("b", original)
        val repo = ScriptedAnalyzeRepository(successResult())

        processor(repo, downscaler = FixedDownscaler(small)).process("a", isLastAttempt = false)
        processor(repo, downscaler = FixedDownscaler(null)).process("b", isLastAttempt = false)

        assertContentEquals(small, (repo.inputs[0] as PreparedAnalyzeInput.Image).bytes)
        assertContentEquals(original, (repo.inputs[1] as PreparedAnalyzeInput.Image).bytes)
    }

    @Test
    fun fileJobWithoutItsPayload_failsAsInterrupted_withoutARequest() = runTest {
        store.put(queuedJob("web", input = AnalysisJobInput.StoredFile(StoredFileKind.PDF, "a.pdf", "application/pdf")))
        val repo = ScriptedAnalyzeRepository(successResult())

        processor(repo).process("web", isLastAttempt = true)

        assertEquals(0, repo.requestIds.size)
        assertEquals(AiFailureReason.INTERRUPTED, assertIs<AnalysisJobStatus.Failed>(store.get("web")?.status).failure.reason)
    }

    // ── Review findings 2026-11-20 ────────────────────────────────────────────────────────────

    /** F1: a deleted fill target used to reach the user as the English text "fill target is gone". */
    @Test
    fun deletedFillTarget_isItsOwnLocalizableOutcome_neverTheExceptionText() = runTest {
        store.put(
            queuedJob("fill").copy(
                target = AnalysisJobTarget.Fill(checklistId = 5L, checklistName = "Trip", fillDefault = false, isFillMode = true),
            ),
        )
        val repo = ScriptedAnalyzeRepository(successResult())

        processor(repo).process("fill", isLastAttempt = false)

        val failure = assertIs<AnalysisJobStatus.Failed>(store.get("fill")?.status).failure
        assertEquals(AiFailureReason.TARGET_MISSING, failure.reason)
        assertNull(failure.serverMessage, "a local exception's text must never be shown to the user")
        assertEquals(false, failure.retryableWithSameInput)
        assertEquals(0, repo.requestIds.size, "nothing is sent for a checklist that is gone")
    }

    /** F1: a crash inside the run used to put its message ("boom") into the user-facing text. */
    @Test
    fun crash_failsWithoutItsExceptionText() = runTest {
        store.put(queuedJob("crash"))
        val repo = ScriptedAnalyzeRepository(successResult()).apply { crash = IllegalStateException("boom") }

        processor(repo).process("crash", isLastAttempt = true)

        val failure = assertIs<AnalysisJobStatus.Failed>(store.get("crash")?.status).failure
        assertNull(failure.serverMessage)
    }

    @Test
    fun serverMessage_isKeptOnlyForServerRefusals() = runTest {
        store.put(queuedJob("credits"))
        val repo = ScriptedAnalyzeRepository(Result.failure(AiAnalyzeError.Http(402, "Not enough credits")))

        processor(repo).process("credits", isLastAttempt = true)

        val failure = assertIs<AnalysisJobStatus.Failed>(store.get("credits")?.status).failure
        assertEquals("Not enough credits", failure.serverMessage)
    }

    /**
     * F6a: the server succeeded (credits charged) but the Room write failed. "Try again" used to be a NEW
     * request id = a second charge. Now the SAME job id runs again; the server replays without charging.
     */
    @Test
    fun roomWriteFailure_isRetriedWithTheSameJobId_notFailed() = runTest {
        store.put(queuedJob("room"))
        val repo = ScriptedAnalyzeRepository(successResult()).apply { createFailures = 1 }
        val sut = processor(repo)

        assertEquals(ProcessResult.RetryLater, sut.process("room", isLastAttempt = false))
        assertEquals(ProcessResult.Done, sut.process("room", isLastAttempt = false))

        assertEquals(listOf<String?>("room", "room"), repo.requestIds)
        assertIs<AnalysisJobStatus.Succeeded>(store.get("room")?.status)
        assertEquals(1, repo.createCount)
    }

    /** F5: a job enqueued before registration finished must wait for it, not fail. */
    @Test
    fun userNotReady_waitsForRegistration_withTheSameId() = runTest {
        store.put(queuedJob("early"))
        val repo = ScriptedAnalyzeRepository(listOf(Result.failure(AiAnalyzeError.UserNotReady), successResult()))
        val sut = processor(repo)

        assertEquals(ProcessResult.RetryLater, sut.process("early", isLastAttempt = false))
        assertEquals(AnalysisJobStatus.Queued(waitingForNetwork = false), store.get("early")?.status)
        assertEquals(ProcessResult.Done, sut.process("early", isLastAttempt = false))
        assertIs<AnalysisJobStatus.Succeeded>(store.get("early")?.status)
        assertTrue(tracker.events.none { it.first == AnalyticsEvents.Analyze.FAILED })
    }

    @Test
    fun userNotReady_onTheLastAttempt_failsWithItsOwnReason() = runTest {
        store.put(queuedJob("late"))
        processor(ScriptedAnalyzeRepository(Result.failure(AiAnalyzeError.UserNotReady))).process("late", isLastAttempt = true)

        assertEquals(
            AiFailureReason.USER_NOT_READY,
            assertIs<AnalysisJobStatus.Failed>(store.get("late")?.status).failure.reason,
        )
    }

    private class FixedDownscaler(private val out: ByteArray?) : ImageDownscaler {
        override suspend fun downscaleToJpeg(bytes: ByteArray, maxLongSidePx: Int, jpegQuality: Int): ByteArray? = out
    }
}
