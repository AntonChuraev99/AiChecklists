package com.antonchuraev.homesearchchecklist.csat

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Manages CSAT trigger evaluation and persistence using a **scored-eligibility** model
 * (v1) instead of the old binary AND-gate (min-actions AND min-distinct-days AND max-shows).
 *
 * Every "value" analytics event contributes a weight to a single decaying engagement score.
 * The survey becomes eligible once the score crosses [THRESHOLD]; a per-outcome cooldown then
 * gates re-showing. The score decays with a [DECAY_HALF_LIFE_DAYS]-day half-life, so eligibility
 * is earned by *recent* engagement and naturally re-earned after a long cooldown (a submitted
 * survey's 60-day cooldown decays the score to near-zero, forcing a fresh accumulation).
 *
 * Why scored instead of the binary gate: the AND-gate treated a power-sharer and a two-action
 * dabbler identically and hard-capped at 3 lifetime prompts. Weighting lets a strong positive
 * peak (a share, weight 8) reach the survey fast while a single low-signal action never does,
 * and decay replaces the crude lifetime cap.
 *
 * DataStore keys (all [Int]; the fractional score is stored scaled by [SCORE_SCALE]):
 * - [KEY_SCORE] — engagement score, persisted as `round(score * SCORE_SCALE)`
 * - [KEY_LAST_SCORE_DAY] — epoch day of the last score update, used for decay
 * - [KEY_LAST_ACTION_DATE] — epoch day of the last value action, used for the new-day bonus
 * - [KEY_CHECKLIST_CREATED_COUNT] — lifetime `checklist_created` counter for milestone awards
 * - [KEY_LAST_SHOWN_DATE] — epoch day of the last CSAT show, used for cooldown
 * - [KEY_LAST_OUTCOME] — "submitted"/"dismissed", picks the cooldown duration
 * - [KEY_SHOW_COUNT] — lifetime show counter, kept for analytics only (NOT a gate)
 */
class CsatManager(
    private val datastore: AppDatastore,
    // Injectable time seams (default to real clock). Let unit tests simulate distinct calendar days
    // (decay, new-day bonus, cooldown boundaries) and the reminder window without touching Clock.System.
    private val today: () -> Int = {
        Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays().toInt()
    },
    private val now: () -> Instant = { Clock.System.now() },
) {

    companion object {
        private const val KEY_SCORE = "csat_score"
        private const val KEY_LAST_SCORE_DAY = "csat_last_score_day"
        private const val KEY_LAST_ACTION_DATE = "csat_last_action_date"
        private const val KEY_CHECKLIST_CREATED_COUNT = "csat_checklist_created_count"
        private const val KEY_LAST_SHOWN_DATE = "csat_last_shown_date"
        private const val KEY_LAST_OUTCOME = "csat_last_outcome"
        private const val KEY_SHOW_COUNT = "csat_show_count"

        // Fractional score stored as a scaled Int (AppDatastore has no Double support and we must
        // not add one). round(score * 100) keeps two decimals of precision — ample for a threshold
        // model where the smallest weight is 3.
        private const val SCORE_SCALE = 100

        // Eligibility threshold. Reachable by e.g. a single share (8) + new-day bonus (3) + one more
        // value action, or two same-day value actions plus the first-day bonus.
        private const val THRESHOLD = 12.0

        // Exponential decay: score is multiplied by DECAY_BASE^(elapsedDays / DECAY_HALF_LIFE_DAYS).
        private const val DECAY_BASE = 0.5
        private const val DECAY_HALF_LIFE_DAYS = 7.0

        // Distinct-day engagement expressed as weight, not a hard gate: a value action on a new
        // calendar day (vs KEY_LAST_ACTION_DATE) adds this bonus.
        private const val NEW_DAY_BONUS = 3

        // Milestone: reaching the 3rd and 5th lifetime checklist is a value signal on its own.
        private const val MILESTONE_WEIGHT = 4
        private const val MILESTONE_FIRST_COUNT = 3
        private const val MILESTONE_SECOND_COUNT = 5

        // Reminder-composite: tapping a reminder and then checking an item shortly after is a
        // "the reminder worked" peak worth surveying.
        private const val REMINDER_WEIGHT = 4
        private val REMINDER_WINDOW = 10.minutes

        // Cooldown by last outcome. Submitted = long rest (we have their answer); dismissed = short.
        private const val COOLDOWN_SUBMITTED_DAYS = 60
        private const val COOLDOWN_DISMISSED_DAYS = 14

        // Observed value events and their score weights. Reminder-composite and milestone events
        // are handled specially (see startObserving) and are intentionally NOT in this map.
        private const val EVENT_SHARE = "share_checklist"
        private const val EVENT_FILL_COMPLETED = "fill_completed"
        private const val EVENT_AI_CHAT_PREVIEW_CONFIRMED = "ai_chat_preview_confirmed"
        private const val EVENT_AI_ANALYZE_COMPLETED = "ai_analyze_completed"

        private val EVENT_WEIGHTS = mapOf(
            EVENT_SHARE to 8,
            EVENT_FILL_COMPLETED to 5,
            EVENT_AI_CHAT_PREVIEW_CONFIRMED to 5,
            EVENT_AI_ANALYZE_COMPLETED to 5,
        )

        // Special (non-weighted-map) observed events.
        private const val EVENT_CHECKLIST_CREATED = "checklist_created"
        private const val EVENT_REMINDER_TAPPED = "reminder_notification_tapped"
        private const val EVENT_ITEM_CHECKED = "item_checked"

        // Analytics trigger-source labels for csat_shown.trigger_event (NOT analytics event names) —
        // the milestone/composite paths have no single source event, so they carry a synthetic label.
        private const val TRIGGER_CHECKLIST_MILESTONE = "checklist_milestone"
        private const val TRIGGER_REMINDER_COMPLETED = "reminder_completed"

        const val OUTCOME_SUBMITTED = "submitted"
        const val OUTCOME_DISMISSED = "dismissed"
    }

    private fun todayEpochDays(): Int = today()

    private suspend fun readScore(): Double =
        datastore.observeInt(KEY_SCORE, 0).first() / SCORE_SCALE.toDouble()

    /**
     * Pure decay math (no I/O, easily unit-tested): the stored score aged from [lastScoreDay] to
     * [today]. A stored day of 0 (never persisted) or a non-positive score decays to itself.
     * Clamps negative elapsed (clock/timezone moving backwards) so the score can never grow via decay.
     */
    private fun decay(storedScore: Double, lastScoreDay: Int, today: Int): Double {
        if (storedScore <= 0.0 || lastScoreDay == 0) return storedScore
        val elapsedDays = (today - lastScoreDay).coerceAtLeast(0)
        return storedScore * DECAY_BASE.pow(elapsedDays / DECAY_HALF_LIFE_DAYS)
    }

    /** Read-only: the current score decayed forward to today, WITHOUT adding or persisting. */
    private suspend fun decayedScoreToday(): Double {
        val stored = readScore()
        val lastScoreDay = datastore.observeInt(KEY_LAST_SCORE_DAY, 0).first()
        return decay(stored, lastScoreDay, todayEpochDays())
    }

    /**
     * Decays the stored score to today, adds [weight] plus a [NEW_DAY_BONUS] when this is the
     * first value action of a new calendar day, then persists the new score and today's epoch day.
     */
    private suspend fun awardScore(weight: Int) {
        val today = todayEpochDays()
        val stored = readScore()
        val lastScoreDay = datastore.observeInt(KEY_LAST_SCORE_DAY, 0).first()
        val decayed = decay(stored, lastScoreDay, today)

        val lastActionDay = datastore.observeInt(KEY_LAST_ACTION_DATE, 0).first()
        val newDayBonus = if (lastActionDay != today) {
            datastore.saveInt(KEY_LAST_ACTION_DATE, today)
            NEW_DAY_BONUS
        } else {
            0
        }

        val newScore = decayed + weight + newDayBonus
        datastore.saveInt(KEY_SCORE, (newScore * SCORE_SCALE).roundToInt())
        datastore.saveInt(KEY_LAST_SCORE_DAY, today)
    }

    /**
     * Increments the lifetime `checklist_created` counter and awards [MILESTONE_WEIGHT] only when
     * the 3rd or 5th checklist is reached. Returns true when a milestone was awarded, so the caller
     * only re-checks eligibility on an actual value moment (a bare creation must not pop the survey
     * mid-task — the reason the original design never triggered on `*_created`).
     */
    private suspend fun recordChecklistCreatedMilestone(): Boolean {
        val count = datastore.observeInt(KEY_CHECKLIST_CREATED_COUNT, 0).first() + 1
        datastore.saveInt(KEY_CHECKLIST_CREATED_COUNT, count)
        return if (count == MILESTONE_FIRST_COUNT || count == MILESTONE_SECOND_COUNT) {
            awardScore(MILESTONE_WEIGHT)
            true
        } else {
            false
        }
    }

    /**
     * Eligibility check:
     * 1. Decay the stored score to today (no points added).
     * 2. Below [THRESHOLD] → not eligible.
     * 3. Never shown before → eligible.
     * 4. Otherwise honor the per-outcome cooldown (60d submitted / 14d dismissed).
     */
    suspend fun shouldShowCsat(): Boolean {
        val decayedScore = decayedScoreToday()
        if (decayedScore < THRESHOLD) return false

        val lastShownDay = datastore.observeInt(KEY_LAST_SHOWN_DATE, 0).first()
        if (lastShownDay == 0) return true

        val today = todayEpochDays()
        val outcome = datastore.observeString(KEY_LAST_OUTCOME, OUTCOME_DISMISSED).first()
        val cooldownDays = if (outcome == OUTCOME_SUBMITTED) {
            COOLDOWN_SUBMITTED_DAYS
        } else {
            COOLDOWN_DISMISSED_DAYS
        }

        return (today - lastShownDay) >= cooldownDays
    }

    suspend fun recordShown() {
        datastore.saveInt(KEY_LAST_SHOWN_DATE, todayEpochDays())
        // Kept for analytics only — no longer a lifetime cap gate.
        val shownCount = datastore.observeInt(KEY_SHOW_COUNT, 0).first()
        datastore.saveInt(KEY_SHOW_COUNT, shownCount + 1)
    }

    suspend fun recordOutcome(outcome: String) {
        datastore.saveString(KEY_LAST_OUTCOME, outcome)
    }

    /**
     * Observes analytics events and feeds the scored model. Value events add their weight;
     * `checklist_created` awards a milestone at the 3rd/5th checklist; a `reminder_notification_tapped`
     * followed by an `item_checked` within [REMINDER_WINDOW] awards the reminder-composite. After any
     * event that changes the score, re-checks [shouldShowCsat] and invokes [onShouldShow] when eligible.
     *
     * The reminder tap state is transient (in the collector's memory, never persisted) — a stale tap
     * from a previous session must not later mark an unrelated item-check as reminder-driven.
     */
    // Transient reminder-composite state — instance-scoped, never persisted. startObserving runs a
    // single collector, so one field suffices; a stale tap must not later mark an unrelated
    // item-check as reminder-driven, so it is cleared once consumed.
    private var lastReminderTapAt: Instant? = null

    /**
     * Feeds one analytics event into the scored model: adds its weight (value events), awards a
     * milestone (3rd/5th checklist_created), or completes the reminder composite (tap→check within
     * [REMINDER_WINDOW]). Returns the trigger-source label to attribute a survey show to when the
     * event makes the survey due, or null otherwise. `internal` so unit tests drive scoring
     * deterministically without the analytics SharedFlow + a collector dispatcher in the way.
     */
    internal suspend fun processEvent(eventName: String): String? {
        val weight = EVENT_WEIGHTS[eventName]
        return when {
            weight != null -> {
                awardScore(weight)
                eventName.takeIf { shouldShowCsat() }
            }

            eventName == EVENT_CHECKLIST_CREATED ->
                // Only a milestone (3rd/5th) is a value moment worth surveying at.
                if (recordChecklistCreatedMilestone() && shouldShowCsat()) TRIGGER_CHECKLIST_MILESTONE else null

            eventName == EVENT_REMINDER_TAPPED -> {
                // Arm the composite; no score change, so never due off this event alone.
                lastReminderTapAt = now()
                null
            }

            eventName == EVENT_ITEM_CHECKED -> {
                val tappedAt = lastReminderTapAt
                if (tappedAt != null && (now() - tappedAt) <= REMINDER_WINDOW) {
                    awardScore(REMINDER_WEIGHT)
                    lastReminderTapAt = null
                    TRIGGER_REMINDER_COMPLETED.takeIf { shouldShowCsat() }
                } else {
                    null // plain item-check — ignored, no score
                }
            }

            else -> null
        }
    }

    /**
     * Observes analytics events and feeds each into [processEvent]; when an event makes the survey
     * due, invokes [onShouldShow] with the trigger-source label and the current (decayed) score.
     */
    fun startObserving(
        scope: CoroutineScope,
        analyticsTracker: AnalyticsTracker,
        onShouldShow: suspend (triggerEvent: String, score: Int) -> Unit,
    ) {
        val observable = analyticsTracker as? ObservableAnalyticsTracker ?: return
        scope.launch {
            observable.events.collect { event ->
                val trigger = processEvent(event.name)
                if (trigger != null) onShouldShow(trigger, decayedScoreToday().roundToInt())
            }
        }
    }
}
