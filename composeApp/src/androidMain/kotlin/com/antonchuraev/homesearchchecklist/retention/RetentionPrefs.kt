package com.antonchuraev.homesearchchecklist.retention

import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import kotlinx.coroutines.flow.first

/**
 * On-device state for the LOCAL retention-push system (behavioral timing + frequency cap).
 *
 * Two independent concerns, both intentionally lightweight (no dedicated tracking pipeline — the
 * activity histogram is fed only by cheap app-open / foreground signals):
 *
 *  1. **Most-active-hour histogram** — a 24-slot counter stored as a CSV string. [recordActiveHour]
 *     bumps the current-hour slot (debounced to at most once per [RECORD_DEBOUNCE_MS] so a resume
 *     storm can't skew it); [mostActiveHour] returns the arg-max, or the caller's default until we
 *     have at least [MIN_SAMPLES] observations (so a brand-new install isn't scheduled off noise).
 *
 *  2. **Daily frequency cap** — one local date key ("last retention push shown"). [canShowOn] /
 *     [markShown] gate ALL retention pushes (streak/overdue/digest) so they never stack: at most one
 *     per calendar day. This is a check-then-set with a tiny residual race between two alarms firing
 *     in the same instant (worst case 2/day, within the product's <=1-2/day budget).
 *
 * Backed by the shared [AppDatastore]; every value has a safe default so a cold read never throws.
 */
class RetentionPrefs(
    private val dataStore: AppDatastore,
) {

    // ─── Activity histogram (behavioral timing) ───

    /**
     * Record that the user was active in the current [hour] (0..23). Debounced: a call within
     * [RECORD_DEBOUNCE_MS] of the previous recorded sample is ignored, so many rapid foregrounds
     * (billing sheet round-trips, quick app switches) count once, not dozens of times.
     */
    suspend fun recordActiveHour(hour: Int, nowMs: Long) {
        if (hour !in 0..23) return
        val lastMs = dataStore.observeString(KEY_LAST_ACTIVE_MS, "").first().toLongOrNull() ?: 0L
        if (nowMs - lastMs < RECORD_DEBOUNCE_MS) return

        val counts = readHistogram()
        counts[hour] = counts[hour] + 1
        dataStore.saveString(KEY_HISTOGRAM, counts.joinToString(SEPARATOR))
        dataStore.saveString(KEY_LAST_ACTIVE_MS, nowMs.toString())
    }

    /**
     * The user's most-active hour (0..23), or [defaultHour] until at least [MIN_SAMPLES] activity
     * observations exist. Ties resolve to the earliest hour (arg-max of the first maximum).
     */
    suspend fun mostActiveHour(defaultHour: Int): Int {
        val counts = readHistogram()
        val total = counts.sum()
        if (total < MIN_SAMPLES) return defaultHour
        var bestHour = 0
        var bestCount = -1
        for (h in 0..23) {
            if (counts[h] > bestCount) {
                bestCount = counts[h]
                bestHour = h
            }
        }
        return bestHour
    }

    private suspend fun readHistogram(): IntArray {
        val raw = dataStore.observeString(KEY_HISTOGRAM, "").first()
        val parsed = IntArray(24)
        if (raw.isBlank()) return parsed
        val parts = raw.split(SEPARATOR)
        for (i in 0 until minOf(24, parts.size)) {
            parsed[i] = parts[i].toIntOrNull()?.coerceAtLeast(0) ?: 0
        }
        return parsed
    }

    // ─── Daily frequency cap ───

    /** True iff no retention push has been shown yet on [dateKey] (a local "yyyy-MM-dd" string). */
    suspend fun canShowOn(dateKey: String): Boolean =
        dataStore.observeString(KEY_LAST_SHOWN_DATE, "").first() != dateKey

    /** Record that a retention push was shown on [dateKey] — closes the cap for the rest of that day. */
    suspend fun markShown(dateKey: String) {
        dataStore.saveString(KEY_LAST_SHOWN_DATE, dateKey)
    }

    // ─── D0->D1 come-back nudge (one-shot arm state) ───

    /**
     * True once a come-back alarm has EVER been armed (pending or already fired). This is the
     * arm-at-most-once-per-user guard: the first checklist arms it, and it never re-arms — even if
     * the user later deletes every checklist and creates a new "first" one.
     */
    suspend fun isComebackArmed(): Boolean = comebackState() != COMEBACK_STATE_NONE

    /**
     * True while a come-back alarm is armed and still PENDING (not yet fired). Gates the boot-time
     * re-arm: an already-fired come-back must never be re-scheduled on reboot.
     */
    suspend fun isComebackPending(): Boolean = comebackState() == COMEBACK_STATE_SCHEDULED

    /** Record the come-back alarm as armed/pending for [checklistId] at [armedAtMs]. */
    suspend fun markComebackScheduled(checklistId: Long, armedAtMs: Long) {
        dataStore.saveString(KEY_COMEBACK_CHECKLIST_ID, checklistId.toString())
        dataStore.saveString(KEY_COMEBACK_ARMED_AT, armedAtMs.toString())
        dataStore.saveString(KEY_COMEBACK_STATE, COMEBACK_STATE_SCHEDULED)
    }

    /** Mark the come-back alarm as fired/consumed so a later boot never re-arms it. */
    suspend fun markComebackFired() {
        dataStore.saveString(KEY_COMEBACK_STATE, COMEBACK_STATE_FIRED)
    }

    /** The target checklist id captured when the come-back was armed, or null if never armed. */
    suspend fun comebackChecklistId(): Long? =
        dataStore.observeString(KEY_COMEBACK_CHECKLIST_ID, "").first().toLongOrNull()

    /** When the come-back alarm was armed (epoch ms), or 0 if never armed. */
    suspend fun comebackArmedAt(): Long =
        dataStore.observeString(KEY_COMEBACK_ARMED_AT, "").first().toLongOrNull() ?: 0L

    /**
     * Last recorded foreground/activity sample (epoch ms), or 0 if none yet. Fed by
     * [recordActiveHour]; the come-back honest signal compares it against [comebackArmedAt] to skip
     * nudging a user who already returned on their own after the alarm was armed.
     */
    suspend fun lastActiveMs(): Long =
        dataStore.observeString(KEY_LAST_ACTIVE_MS, "").first().toLongOrNull() ?: 0L

    private suspend fun comebackState(): String =
        dataStore.observeString(KEY_COMEBACK_STATE, COMEBACK_STATE_NONE).first()

    private companion object {
        const val KEY_HISTOGRAM = "retention_hour_histogram"
        const val KEY_LAST_ACTIVE_MS = "retention_last_active_ms"
        const val KEY_LAST_SHOWN_DATE = "retention_last_shown_date"
        const val SEPARATOR = ","

        // Come-back one-shot arm state machine (persisted): none -> scheduled -> fired.
        const val KEY_COMEBACK_STATE = "retention_comeback_state"
        const val KEY_COMEBACK_CHECKLIST_ID = "retention_comeback_checklist_id"
        const val KEY_COMEBACK_ARMED_AT = "retention_comeback_armed_at"
        const val COMEBACK_STATE_NONE = ""
        const val COMEBACK_STATE_SCHEDULED = "scheduled"
        const val COMEBACK_STATE_FIRED = "fired"

        /** Ignore activity samples closer together than this (15 min) so a resume storm counts once. */
        const val RECORD_DEBOUNCE_MS = 15 * 60 * 1000L

        /** Minimum activity observations before behavioral timing overrides the default window. */
        const val MIN_SAMPLES = 3
    }
}
