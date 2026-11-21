package com.antonchuraev.homesearchchecklist.feature.analyze.platform

import android.content.Intent
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJob
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobAnalytics
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobFailure
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobInput
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobOutcome
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobStatus
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobTarget
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the "ready" notification says, and that its tap intent is recognised only by its own action
 * (a reminder's ACTION_OPEN_CHECKLIST must never be read as an analysis tap, and vice versa).
 *
 * Run: `./gradlew :feature:analyze:testAndroidHostTest --tests "*AnalysisNotificationCopyTest*"`
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnalysisNotificationCopyTest {

    private fun job(status: AnalysisJobStatus, target: AnalysisJobTarget = AnalysisJobTarget.NewChecklist(false)) =
        AnalysisJob(
            id = "job-1",
            createdAtMs = 0L,
            input = AnalysisJobInput.Text("x"),
            target = target,
            analytics = AnalysisJobAnalytics(inputType = "raw_text", source = "none"),
            status = status,
        )

    @Test
    fun createdChecklist_textIsTheChecklistName_blankFallsBackToGeneric() {
        val named = job(AnalysisJobStatus.Succeeded(AnalysisJobOutcome.CreatedChecklist(7L, "Trip to Rome", 5, false)))
        val blank = job(AnalysisJobStatus.Succeeded(AnalysisJobOutcome.CreatedChecklist(7L, "  ", 5, false)))

        assertEquals(AnalysisNotificationCopy.ChecklistReady("Trip to Rome"), AnalysisNotificationCopy.of(named))
        assertEquals(AnalysisNotificationCopy.ChecklistReady(null), AnalysisNotificationCopy.of(blank))
    }

    @Test
    fun fillReady_textIsTheTargetChecklistName() {
        val fill = job(
            status = AnalysisJobStatus.Succeeded(AnalysisJobOutcome.FillReady(emptyList(), emptyList(), null)),
            target = AnalysisJobTarget.Fill(checklistId = 3L, checklistName = "Move-in", fillDefault = false, isFillMode = true),
        )
        assertEquals(AnalysisNotificationCopy.FillReady("Move-in"), AnalysisNotificationCopy.of(fill))
    }

    @Test
    fun failed_textIsTheServerMessage_orGeneric() {
        val withMessage = job(AnalysisJobStatus.Failed(AnalysisJobFailure(AiFailureReason.CREDIT_GATE, "Not enough credits", false)))
        val withoutMessage = job(AnalysisJobStatus.Failed(AnalysisJobFailure(AiFailureReason.NETWORK, null, true)))

        assertEquals(AnalysisNotificationCopy.Failed("Not enough credits"), AnalysisNotificationCopy.of(withMessage))
        assertEquals(AnalysisNotificationCopy.Failed(null), AnalysisNotificationCopy.of(withoutMessage))
    }

    @Test
    fun nonTerminalOrCancelled_postsNothing() {
        assertNull(AnalysisNotificationCopy.of(job(AnalysisJobStatus.Running)))
        assertNull(AnalysisNotificationCopy.of(job(AnalysisJobStatus.Queued(waitingForNetwork = true))))
        assertNull(AnalysisNotificationCopy.of(job(AnalysisJobStatus.Cancelled)))
    }

    @Test
    fun tapIntent_isRecognisedOnlyByItsOwnAction() {
        val analysisTap = Intent(AnalysisNotificationIntents.ACTION_OPEN_ANALYSIS_RESULT)
            .putExtra(AnalysisNotificationIntents.EXTRA_ANALYSIS_JOB_ID, "job-9")
        val reminderTap = Intent("com.antonchuraev.aichecklists.action.OPEN_CHECKLIST")
            .putExtra(AnalysisNotificationIntents.EXTRA_ANALYSIS_JOB_ID, "job-9")
        val blankId = Intent(AnalysisNotificationIntents.ACTION_OPEN_ANALYSIS_RESULT)
            .putExtra(AnalysisNotificationIntents.EXTRA_ANALYSIS_JOB_ID, " ")

        assertEquals("job-9", AnalysisNotificationIntents.jobIdFrom(analysisTap))
        assertNull(AnalysisNotificationIntents.jobIdFrom(reminderTap))
        assertNull(AnalysisNotificationIntents.jobIdFrom(blankId))
        assertNull(AnalysisNotificationIntents.jobIdFrom(null))
    }
}
