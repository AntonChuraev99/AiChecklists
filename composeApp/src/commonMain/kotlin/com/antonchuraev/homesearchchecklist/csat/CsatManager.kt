package com.antonchuraev.homesearchchecklist.csat

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Manages CSAT trigger evaluation and persistence.
 *
 * Tracks how many "meaningful actions" the user has taken (creating a checklist,
 * creating a fill, completing an AI fill) and decides when to show the CSAT survey.
 *
 * Uses 2 DataStore keys:
 * - [KEY_ACTION_COUNT] (Int) — lifetime counter of meaningful user actions
 * - [KEY_LAST_SHOWN_DATE] (Int) — epoch day of last CSAT show for 30-day cooldown
 */
class CsatManager(private val datastore: AppDatastore) {

    companion object {
        private const val KEY_ACTION_COUNT = "csat_action_count"
        private const val KEY_LAST_SHOWN_DATE = "csat_last_shown_date"
        private const val COOLDOWN_DAYS = 30
        private const val MIN_ACTIONS = 2
    }

    private val csatTriggerEvents = setOf(
        "checklist_created",
        "fill_created",
        "default_fill_updated",
    )

    private fun todayEpochDays(): Int =
        Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays()

    suspend fun onUserAction() {
        val count = datastore.observeInt(KEY_ACTION_COUNT, 0).first()
        datastore.saveInt(KEY_ACTION_COUNT, count + 1)
    }

    suspend fun shouldShowCsat(): Boolean {
        val count = datastore.observeInt(KEY_ACTION_COUNT, 0).first()
        if (count < MIN_ACTIONS) return false

        val lastShownDay = datastore.observeInt(KEY_LAST_SHOWN_DATE, 0).first()
        val today = todayEpochDays()

        return lastShownDay == 0 || (today - lastShownDay) >= COOLDOWN_DAYS
    }

    suspend fun recordShown() {
        datastore.saveInt(KEY_LAST_SHOWN_DATE, todayEpochDays())
    }

    /**
     * Starts observing analytics events and increments the action counter
     * when a CSAT-relevant event is detected. Returns a callback to check
     * whether CSAT should be shown after the latest action.
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
