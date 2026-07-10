package com.antonchuraev.homesearchchecklist.csat

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Green-coverage for the scored-eligibility model of [CsatManager].
 *
 * Scoring is driven the way production does — one analytics event at a time — but via the
 * `internal` [CsatManager.processEvent] seam rather than the analytics SharedFlow, so the test
 * exercises the pure scoring logic without a collector coroutine / dispatcher in between (that
 * indirection previously starved the score: the emits weren't processed before the read-back).
 * [Harness.emit] returns/records whether that event made the survey due; assertions use the public
 * [CsatManager.shouldShowCsat] gate and the show count.
 *
 * Event weights under test: share_checklist=8, fill_completed=5, ai_chat_preview_confirmed=5,
 * ai_analyze_completed=5, milestone(3rd/5th checklist_created)=4, reminder-composite(tap→check)=4,
 * new-day bonus=3, THRESHOLD=12. Tests never assert on raw score numbers — they pick event
 * COMBINATIONS whose defined sum is unambiguously below/above 12.
 *
 * Two seams make the model deterministic: an injectable [FakeClock] (advance the calendar for
 * decay/cooldown and the wall clock for the reminder window) and an in-memory MutableStateFlow-backed
 * DataStore (every write is instantly visible to the next read).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CsatManagerScoredTest {

    // ── Case 1: below threshold, no show ────────────────────────────────────────

    @Test
    fun singleFillCompleted_belowThreshold_doesNotShow() = runTest {
        val h = newHarness()

        // One completed fill = 5 (+3 first-day bonus = 8) < THRESHOLD 12.
        h.emit(AnalyticsEvents.Checklist.FILL_COMPLETED)

        assertFalse(
            h.manager.shouldShowCsat(),
            "a single fill_completed (5) is below the 12-point threshold — must not be eligible",
        )
        assertEquals(0, h.shownCount, "no survey may be triggered below the threshold")
    }

    // ── Case 2: one engaged session crosses the threshold (the headline behavior change) ──

    @Test
    fun threePositiveActionsSameSession_crossesThreshold_shows() = runTest {
        val h = newHarness()

        // preview(5) + fill(5) + analyze(5) = 15 (+3 first-day) ≥ 12, all in one session/day. The
        // old count+distinct-days gate never surveyed a one-session user; the scored model must.
        h.emit(AnalyticsEvents.Chat.PREVIEW_CONFIRMED)
        h.emit(AnalyticsEvents.Checklist.FILL_COMPLETED)
        h.emit(AnalyticsEvents.Analyze.COMPLETED)

        assertTrue(
            h.manager.shouldShowCsat(),
            "three high-value actions in one session (15 pts) must cross the threshold",
        )
        assertTrue(h.shownCount >= 1, "crossing the threshold must mark the survey due at least once")
    }

    // ── Case 5: milestone weight lands on the 3rd checklist_created, not the 2nd ──

    @Test
    fun milestone_awardsAt3rdChecklistCreated_notAt2nd() = runTest {
        val h = newHarness()

        // Base engagement: one share (8 + 3 first-day = 11), still below THRESHOLD 12.
        h.emit(AnalyticsEvents.Checklist.SHARED)
        assertFalse(h.manager.shouldShowCsat(), "share alone (11) must stay below the threshold")

        // checklist_created carries NO base weight; only the 3rd and 5th award a +4 milestone.
        h.emit(AnalyticsEvents.Checklist.CREATED)
        h.emit(AnalyticsEvents.Checklist.CREATED)
        assertFalse(
            h.manager.shouldShowCsat(),
            "2 checklist_created must NOT award a milestone → still below threshold",
        )
        assertEquals(0, h.shownCount, "no show may fire before the milestone tips the score over")

        // 3rd checklist_created → +4 milestone → 11 + 4 = 15 ≥ THRESHOLD → eligible.
        h.emit(AnalyticsEvents.Checklist.CREATED)
        assertTrue(
            h.manager.shouldShowCsat(),
            "the 3rd checklist_created must award the milestone weight and cross the threshold",
        )
    }

    // ── Case 6: cooldown suppresses a re-show while the score is still high ──

    @Test
    fun cooldownAfterSubmitted_withinWindowSameDay_blocksReshow() = runTest {
        val h = newHarness()

        h.emit(AnalyticsEvents.Chat.PREVIEW_CONFIRMED)
        h.emit(AnalyticsEvents.Checklist.FILL_COMPLETED)
        h.emit(AnalyticsEvents.Analyze.COMPLETED)
        assertTrue(h.manager.shouldShowCsat(), "15 points must be eligible before any show is recorded")

        // Survey shown today, user submitted → the 60-day cooldown starts.
        h.manager.recordShown()
        h.manager.recordOutcome(CsatManager.OUTCOME_SUBMITTED)

        assertFalse(
            h.manager.shouldShowCsat(),
            "a submitted survey shown today must be on cooldown despite the high score",
        )
    }

    @Test
    fun cooldownAfterDismissed_withinWindowSameDay_blocksReshow() = runTest {
        val h = newHarness()

        h.emit(AnalyticsEvents.Chat.PREVIEW_CONFIRMED)
        h.emit(AnalyticsEvents.Checklist.FILL_COMPLETED)
        h.emit(AnalyticsEvents.Analyze.COMPLETED)
        assertTrue(h.manager.shouldShowCsat(), "15 points must be eligible before any show is recorded")

        h.manager.recordShown()
        h.manager.recordOutcome(CsatManager.OUTCOME_DISMISSED)

        assertFalse(
            h.manager.shouldShowCsat(),
            "a dismissed survey shown today must be on cooldown despite the high score",
        )
    }

    // ── Reminder-composite: a tap followed by an item-check within the window awards +4 ──

    @Test
    fun reminderTapThenItemCheck_withinWindow_awardsCompositeWeight() = runTest {
        val h = newHarness()

        // Base: one share (8 + 3 first-day = 11), just below THRESHOLD 12.
        h.emit(AnalyticsEvents.Checklist.SHARED)
        h.emit(AnalyticsEvents.Reminder.NOTIFICATION_TAPPED)
        assertFalse(
            h.manager.shouldShowCsat(),
            "share(11) plus a bare reminder tap is still below the threshold",
        )

        // Item checked immediately (same fake instant) → inside the 10-min window → +4 → 15.
        h.emit(AnalyticsEvents.Item.CHECKED)
        assertTrue(
            h.manager.shouldShowCsat(),
            "an item checked within the window after a reminder tap must award the composite weight",
        )
    }

    @Test
    fun itemCheckedWithoutReminderTap_awardsNothing() = runTest {
        val h = newHarness()

        h.emit(AnalyticsEvents.Checklist.SHARED)
        h.emit(AnalyticsEvents.Item.CHECKED)
        h.emit(AnalyticsEvents.Item.CHECKED)

        assertFalse(
            h.manager.shouldShowCsat(),
            "item_checked without an armed reminder must add no score → still below threshold",
        )
        assertEquals(0, h.shownCount, "a plain item-check must not trigger the survey")
    }

    // ── Time-gated cases (unlocked by the injectable clock seam) ─────────────────

    @Test
    fun reminderItemCheckOutsideWindow_awardsNothing() = runTest {
        val h = newHarness()

        // Base: share (11), below threshold. Arm the composite, then let the window lapse.
        h.emit(AnalyticsEvents.Checklist.SHARED)
        h.emit(AnalyticsEvents.Reminder.NOTIFICATION_TAPPED)
        h.clock.advanceMinutes(11) // > the 10-minute reminder window

        h.emit(AnalyticsEvents.Item.CHECKED)
        assertFalse(
            h.manager.shouldShowCsat(),
            "an item checked 11 min after the tap is outside the window — no composite, stays below 12",
        )
        assertEquals(0, h.shownCount, "no survey may fire when the composite is not awarded")
    }

    @Test
    fun decay_afterTwoHalfLives_dropsBelowThreshold() = runTest {
        val h = newHarness()

        // 15 pts on day 0 → eligible.
        h.emit(AnalyticsEvents.Chat.PREVIEW_CONFIRMED)
        h.emit(AnalyticsEvents.Checklist.FILL_COMPLETED)
        h.emit(AnalyticsEvents.Analyze.COMPLETED)
        assertTrue(h.manager.shouldShowCsat(), "15 pts on day 0 is eligible")

        // 14 days = 2 half-lives → score × 0.25 → ~3.75 < 12.
        h.clock.advanceDays(14)
        assertFalse(
            h.manager.shouldShowCsat(),
            "after two decay half-lives the stale score must fall below the threshold",
        )
    }

    @Test
    fun noLifetimeCap_reearnAfterCooldown_showsAgainBeyondThreeShows() = runTest {
        val h = newHarness()

        // Four earn → show → submit → wait-out-cooldown cycles. The OLD model hard-capped at 3
        // lifetime shows; the scored model must stay re-eligible once the cooldown elapses and the
        // score is re-earned (the 60-day cooldown itself decays the old score toward zero).
        repeat(4) { cycle ->
            h.emit(AnalyticsEvents.Chat.PREVIEW_CONFIRMED)
            h.emit(AnalyticsEvents.Checklist.FILL_COMPLETED)
            h.emit(AnalyticsEvents.Analyze.COMPLETED)
            assertTrue(
                h.manager.shouldShowCsat(),
                "cycle $cycle: re-earned score must be eligible — no lifetime cap may block it",
            )

            h.manager.recordShown()
            h.manager.recordOutcome(CsatManager.OUTCOME_SUBMITTED)
            h.clock.advanceDays(60) // submitted cooldown elapses; old score decays toward zero
        }
    }

    // ── Harness ────────────────────────────────────────────────────────────────

    /** Controllable time seam: epoch day for decay/cooldown, instant for the reminder window. */
    private class FakeClock(var epochDay: Int = 20_000) {
        var instant: Instant = Instant.fromEpochSeconds(20_000L * 86_400)
            private set

        fun advanceDays(days: Int) {
            epochDay += days
            instant = Instant.fromEpochSeconds(epochDay.toLong() * 86_400)
        }

        fun advanceMinutes(minutes: Int) {
            instant = instant.plus(minutes.minutes)
        }
    }

    /** Wires a real [CsatManager] and counts events that made the survey due. */
    private class Harness(
        val manager: CsatManager,
        val clock: FakeClock,
    ) {
        var shownCount = 0
            private set

        /** Feeds one event through the scored model; a non-null trigger means the survey is due. */
        suspend fun emit(eventName: String) {
            if (manager.processEvent(eventName) != null) shownCount++
        }
    }

    /**
     * Builds a manager backed by an in-memory [DataStore] (writes instantly visible to reads) on an
     * [UnconfinedTestDispatcher], plus a [FakeClock] driving the manager's time seams.
     */
    private fun TestScope.newHarness(): Harness {
        val clock = FakeClock()
        val datastore = AppDatastore(
            InMemoryPreferencesDataStore(),
            UnconfinedTestDispatcher(testScheduler),
        )
        val manager = CsatManager(
            datastore = datastore,
            today = { clock.epochDay },
            now = { clock.instant },
        )
        return Harness(manager, clock)
    }

    /** MutableStateFlow-backed [DataStore] — deterministic in-memory prefs, no file IO. */
    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
