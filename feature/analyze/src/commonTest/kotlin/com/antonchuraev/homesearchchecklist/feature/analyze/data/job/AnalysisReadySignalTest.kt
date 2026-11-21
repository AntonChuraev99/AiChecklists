package com.antonchuraev.homesearchchecklist.feature.analyze.data.job

import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobFailure
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobHandoff
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobInput
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobStatus
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisScreenAttachments
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.DeliveryClaimant
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ReadyDestination
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class AnalysisReadySignalTest {

    /**
     * F2 (review 2026-11-20): opening a failed job from the snackbar / notification pruned it (and its
     * payload) before Analyze opened, so the screen got an empty form and "Try again" had nothing to
     * reuse. The job must survive and be handed to the screen.
     */
    @Test
    fun openFailedJob_keepsItAndItsInput_andHandsItToTheAnalyzeScreen() = runTest {
        val store = InMemoryAnalysisJobStore()
        val handoff = AnalysisJobHandoff()
        val failed = queuedJob(
            "failed-url",
            input = AnalysisJobInput.Url("https://example.com/recipe"),
            status = AnalysisJobStatus.Failed(AnalysisJobFailure(AiFailureReason.NETWORK, retryableWithSameInput = true)),
        )
        store.put(failed)
        val scheduler = AnalysisJobManager(
            store = store,
            payloadStore = InMemoryAnalysisPayloadStore(),
            launcher = RecordingLauncher(),
            analyticsTracker = RecordingTracker(),
            logger = SilentAppLogger,
            scope = backgroundScope,
            readFileBytes = { null },
            readFileText = { null },
        )
        val signal = AnalysisReadySignalImpl(
            scheduler = scheduler,
            foregroundMonitor = FakeForeground(),
            attachments = AnalysisScreenAttachments(),
            activationCoordinator = NoOpActivation,
            analyticsTracker = RecordingTracker(),
            notifier = RecordingNotifier(),
            logger = SilentAppLogger,
            scope = backgroundScope,
            defaultFillName = { "Fill" },
            handoff = handoff,
        )
        testScheduler.advanceUntilIdle()

        val destination = signal.onOpen("failed-url", DeliveryClaimant.NOTIFICATION)

        assertIs<ReadyDestination.AnalyzeRetry>(destination)
        val kept = assertNotNull(store.get("failed-url"), "the failed job must outlive the tap — the screen shows it")
        assertEquals(DeliveryClaimant.NOTIFICATION, kept.deliveredBy, "claimed, so no second snackbar")
        assertEquals("failed-url", handoff.take())
    }

    private object NoOpActivation : ActivationCoordinator {
        override val reminderOptInRequests: SharedFlow<Long> = MutableSharedFlow()
        override suspend fun onAiChecklistCreated(checklistId: Long) {}
        override suspend fun onUserChecklistCreated(checklistId: Long) {}
        override suspend fun reportReminderOptInOutcome(granted: Boolean) {}
    }
}
