package com.antonchuraev.homesearchchecklist.retention

import com.antonchuraev.homesearchchecklist.retention.ComebackGate.Candidate
import com.antonchuraev.homesearchchecklist.retention.ComebackGate.Decision
import com.antonchuraev.homesearchchecklist.retention.ComebackGate.TargetPick
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks the come-back nudge gate. In prod (08-17..10-13) the nudge was shown ZERO times: every
 * `already_active` skip consumed the one-shot forever, even when the "activity" was the user still
 * finishing their first session a few minutes after the alarm was armed.
 */
class ComebackGateTest {

    private val delay = 22 * HOUR
    private val window = 72 * HOUR
    private val armedAt = 1_000_000_000_000L

    private fun decide(
        now: Long,
        lastForeground: Long,
        notificationsEnabled: Boolean = true,
        armed: Long = armedAt,
    ): Decision = ComebackGate.decide(
        nowMs = now,
        armedAtMs = armed,
        lastForegroundMs = lastForeground,
        notificationsEnabled = notificationsEnabled,
        delayMs = delay,
        maxWindowMs = window,
    )

    // ─── decide ───

    @Test
    fun userStillInFirstSessionAfterArm_reschedulesInsteadOfConsuming() {
        val lastForeground = armedAt + 10 * MINUTE
        assertEquals(
            Decision.Reschedule(lastForeground + delay),
            decide(now = armedAt + delay, lastForeground = lastForeground),
        )
    }

    @Test
    fun userReturnedOnTheirOwn_reschedulesToLastForegroundPlusDelay() {
        val lastForeground = armedAt + 5 * HOUR
        assertEquals(
            Decision.Reschedule(armedAt + 27 * HOUR),
            decide(now = armedAt + delay, lastForeground = lastForeground),
        )
    }

    @Test
    fun noForegroundSinceArm_shows() {
        assertEquals(Decision.Show, decide(now = armedAt + delay, lastForeground = armedAt - MINUTE))
    }

    @Test
    fun noForegroundSampleAtAll_shows() {
        assertEquals(Decision.Show, decide(now = armedAt + delay, lastForeground = 0L))
    }

    @Test
    fun quietPeriodAlreadyElapsedOnLateDelivery_shows() {
        // Alarm delivered late (Doze / reboot re-arm): the user came back at +1h, has been away 29h.
        assertEquals(Decision.Show, decide(now = armedAt + 30 * HOUR, lastForeground = armedAt + HOUR))
    }

    @Test
    fun rescheduleWouldLandPastTheWindow_consumesAsAlreadyActive() {
        // Active at +60h -> next quiet point +82h > 72h window: the user is retained, stop nudging.
        assertEquals(
            Decision.Skip(ComebackGate.REASON_ALREADY_ACTIVE),
            decide(now = armedAt + 70 * HOUR, lastForeground = armedAt + 60 * HOUR),
        )
    }

    @Test
    fun rescheduleExactlyAtWindowEdge_isStillAllowed() {
        val lastForeground = armedAt + window - delay
        assertEquals(
            Decision.Reschedule(armedAt + window),
            decide(now = armedAt + 55 * HOUR, lastForeground = lastForeground),
        )
    }

    @Test
    fun permissionOff_userAway_skipsPermissionOff() {
        assertEquals(
            Decision.Skip(ComebackGate.REASON_PERMISSION_OFF),
            decide(now = armedAt + delay, lastForeground = 0L, notificationsEnabled = false),
        )
    }

    @Test
    fun permissionOff_butUserRecentlyActive_reschedulesSoPermissionIsRecheckedLater() {
        // The user may grant the permission during that very session — don't burn the one-shot.
        val lastForeground = armedAt + 2 * HOUR
        assertEquals(
            Decision.Reschedule(lastForeground + delay),
            decide(now = armedAt + delay, lastForeground = lastForeground, notificationsEnabled = false),
        )
    }

    @Test
    fun unknownArmTime_recentlyActive_consumesAsAlreadyActive() {
        assertEquals(
            Decision.Skip(ComebackGate.REASON_ALREADY_ACTIVE),
            decide(now = armedAt, lastForeground = armedAt - HOUR, armed = 0L),
        )
    }

    // ─── pickTarget ───

    @Test
    fun armedChecklistWithOpenItems_isTheTarget() {
        val pick = ComebackGate.pickTarget(
            armedId = 1L,
            candidates = listOf(Candidate(1L, 3, 10L), Candidate(2L, 5, 99L)),
            force = false,
        )
        assertEquals(TargetPick.Target(1L, 3), pick)
    }

    @Test
    fun armedChecklistDeleted_fallsBackToMostRecentlyUpdatedWithOpenItems() {
        val pick = ComebackGate.pickTarget(
            armedId = 1L,
            candidates = listOf(Candidate(2L, 4, 50L), Candidate(3L, 2, 80L), Candidate(4L, 0, 99L)),
            force = false,
        )
        assertEquals(TargetPick.Target(3L, 2), pick)
    }

    @Test
    fun armedChecklistFullyChecked_fallsBackToAnotherWithOpenItems() {
        val pick = ComebackGate.pickTarget(
            armedId = 1L,
            candidates = listOf(Candidate(1L, 0, 99L), Candidate(2L, 4, 50L)),
            force = false,
        )
        assertEquals(TargetPick.Target(2L, 4), pick)
    }

    @Test
    fun noChecklistsLeft_skipsNoChecklist() {
        assertEquals(
            TargetPick.Skip(ComebackGate.REASON_NO_CHECKLIST),
            ComebackGate.pickTarget(armedId = 1L, candidates = emptyList(), force = false),
        )
    }

    @Test
    fun checklistsExistButAllChecked_skipsNoUnchecked() {
        assertEquals(
            TargetPick.Skip(ComebackGate.REASON_NO_UNCHECKED),
            ComebackGate.pickTarget(
                armedId = 1L,
                candidates = listOf(Candidate(2L, 0, 50L), Candidate(3L, 0, 60L)),
                force = false,
            ),
        )
    }

    @Test
    fun forceWithoutArmedId_picksNewestChecklist() {
        val pick = ComebackGate.pickTarget(
            armedId = null,
            candidates = listOf(Candidate(2L, 1, 99L), Candidate(7L, 4, 10L)),
            force = true,
        )
        assertEquals(TargetPick.Target(7L, 4), pick)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
