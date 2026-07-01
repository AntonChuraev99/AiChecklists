package com.antonchuraev.homesearchchecklist.csat

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Manages CSAT trigger evaluation and persistence.
 *
 * Tracks how many "meaningful actions" the user has taken (creating a checklist,
 * creating a fill, completing an AI fill, sharing) and decides when to show the
 * CSAT survey.
 *
 * Uses 3 DataStore keys:
 * - [KEY_ACTION_COUNT] (Int) — lifetime counter of meaningful user actions
 * - [KEY_LAST_SHOWN_DATE] (Int) — epoch day of last CSAT show for cooldown
 * - [KEY_LAST_OUTCOME] (String) — "submitted" or "dismissed" to pick cooldown duration
 */
class CsatManager(private val datastore: AppDatastore) {

    companion object {
        private const val KEY_ACTION_COUNT = "csat_action_count"
        private const val KEY_LAST_SHOWN_DATE = "csat_last_shown_date"
        private const val KEY_LAST_OUTCOME = "csat_last_outcome"
        private const val KEY_DISTINCT_DAYS = "csat_distinct_days"
        private const val KEY_LAST_ACTION_DATE = "csat_last_action_date"
        private const val KEY_SHOW_COUNT = "csat_show_count"

        private const val COOLDOWN_SUBMITTED_DAYS = 45
        private const val COOLDOWN_DISMISSED_DAYS = 7

        // Ask only an engaged, returning user at a success moment (peak-end rule + Play In-App
        // Review guidance): N completed "value" actions AND activity across ≥2 distinct days, so
        // one-and-done first-session users (who skew neutral/noise) are never surveyed. Capped at
        // MAX_LIFETIME_SHOWS prompts ever so we never nag.
        private const val MIN_ACTIONS = 3
        private const val MIN_DISTINCT_DAYS = 2
        private const val MAX_LIFETIME_SHOWS = 3

        const val OUTCOME_SUBMITTED = "submitted"
        const val OUTCOME_DISMISSED = "dismissed"
    }

    // Trigger on SUCCESS/VALUE moments, not on creation. Firing on "*_created" asked the user
    // mid-task (while building a checklist) and skewed responses toward neutral/dismissed; a
    // completed fill ("job done") or a share is a genuine positive peak — and, unlike the old
    // creation triggers, it is a natural post-task boundary rather than an interruption. Using
    // fill_completed also closes the auto-create blind spot: users who only ever get the
    // auto-created starter checklist now still reach a trigger when they finish it.
    private val csatTriggerEvents = setOf(
        "fill_completed",
        "share_checklist",
    )

    private fun todayEpochDays(): Int =
        Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays().toInt()

    suspend fun onUserAction() {
        val count = datastore.observeInt(KEY_ACTION_COUNT, 0).first()
        datastore.saveInt(KEY_ACTION_COUNT, count + 1)

        // Count distinct calendar days on which a value action happened — the returning-user gate.
        val today = todayEpochDays()
        val lastActionDay = datastore.observeInt(KEY_LAST_ACTION_DATE, 0).first()
        if (lastActionDay != today) {
            val distinctDays = datastore.observeInt(KEY_DISTINCT_DAYS, 0).first()
            datastore.saveInt(KEY_DISTINCT_DAYS, distinctDays + 1)
            datastore.saveInt(KEY_LAST_ACTION_DATE, today)
        }
    }

    suspend fun shouldShowCsat(): Boolean {
        val count = datastore.observeInt(KEY_ACTION_COUNT, 0).first()
        if (count < MIN_ACTIONS) return false

        val distinctDays = datastore.observeInt(KEY_DISTINCT_DAYS, 0).first()
        if (distinctDays < MIN_DISTINCT_DAYS) return false

        val shownCount = datastore.observeInt(KEY_SHOW_COUNT, 0).first()
        if (shownCount >= MAX_LIFETIME_SHOWS) return false

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
        val shownCount = datastore.observeInt(KEY_SHOW_COUNT, 0).first()
        datastore.saveInt(KEY_SHOW_COUNT, shownCount + 1)
    }

    suspend fun recordOutcome(outcome: String) {
        datastore.saveString(KEY_LAST_OUTCOME, outcome)
    }

    /**
     * Starts observing analytics events and increments the action counter
     * when a CSAT-relevant event is detected. Invokes [onShouldShow] when
     * conditions are met.
     */
    fun startObserving(
        scope: CoroutineScope,
        analyticsTracker: AnalyticsTracker,
        onShouldShow: suspend () -> Unit,
    ) {
        val observable = analyticsTracker as? ObservableAnalyticsTracker ?: return
        scope.launch {
            observable.events.collect { event ->
                if (event.name in csatTriggerEvents) {
                    onUserAction()
                    if (shouldShowCsat()) {
                        onShouldShow()
                    }
                }
            }
        }
    }
}
