package com.antonchuraev.homesearchchecklist.feature.analyze.domain.job

import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason
import kotlin.test.Test
import kotlin.test.assertEquals

/** The four branches of the delivery rule (FEATURE_SPEC §2). */
class ResolveDeliveryTest {

    private fun job(
        status: AnalysisJobStatus = AnalysisJobStatus.Succeeded(
            AnalysisJobOutcome.CreatedChecklist(checklistId = 1L, checklistName = "N", itemCount = 1, hasFolders = false),
        ),
        deliveredBy: DeliveryClaimant? = null,
    ) = AnalysisJob(
        id = "job-1",
        createdAtMs = 0L,
        input = AnalysisJobInput.Text("t"),
        target = AnalysisJobTarget.NewChecklist(fromActivationHero = false),
        analytics = AnalysisJobAnalytics("raw_text", "none"),
        status = status,
        deliveredBy = deliveredBy,
    )

    @Test
    fun notTerminal_orAlreadyDelivered_isNone() {
        assertEquals(DeliveryRoute.None, resolveDelivery(job(AnalysisJobStatus.Running), "job-1", true))
        assertEquals(DeliveryRoute.None, resolveDelivery(job(AnalysisJobStatus.Queued(true)), "job-1", true))
        assertEquals(DeliveryRoute.None, resolveDelivery(job(deliveredBy = DeliveryClaimant.NOTIFICATION), "job-1", true))
    }

    @Test
    fun attachedAndForeground_isScreen() {
        assertEquals(DeliveryRoute.Screen, resolveDelivery(job(), "job-1", true))
    }

    @Test
    fun foregroundButNotAttached_isInAppHost() {
        assertEquals(DeliveryRoute.InAppHost, resolveDelivery(job(), null, true))
        assertEquals(DeliveryRoute.InAppHost, resolveDelivery(job(), "another-job", true))
    }

    @Test
    fun background_isNotification_evenWhenAttached() {
        assertEquals(DeliveryRoute.Notification, resolveDelivery(job(), "job-1", false))
        val failed = job(AnalysisJobStatus.Failed(AnalysisJobFailure(AiFailureReason.NETWORK, retryableWithSameInput = true)))
        assertEquals(DeliveryRoute.Notification, resolveDelivery(failed, null, false))
    }
}
