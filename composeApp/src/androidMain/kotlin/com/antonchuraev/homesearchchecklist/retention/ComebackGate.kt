package com.antonchuraev.homesearchchecklist.retention

/**
 * Pure decision logic of the D0->D1 come-back nudge, extracted from [RetentionPushReceiver] so the
 * semantics are unit-testable without AlarmManager, DataStore or Room.
 *
 * Why it exists (prod 08-17..10-13: shown ZERO times): the old gate consumed the one-shot on
 * `lastActive > armedAt`, where `lastActive` was also written by every BACKGROUND process start
 * (FCM, widget, alarms — including the come-back alarm's own cold start) and by the user simply
 * finishing the first session a few minutes after the alarm was armed. Every such fire burned the
 * nudge forever. Now:
 *  - "active" means a real FOREGROUND sample ([RetentionPrefs.lastForegroundMs]);
 *  - a user seen recently is not skipped but RESCHEDULED to `lastForeground + delay` (a quiet
 *    period of the same length as the original one), bounded by `armedAt + maxWindow`;
 *  - the permission is re-checked only at a firing where the user has been away for the full quiet
 *    period, so a user who grants it during a later session is not lost.
 */
internal object ComebackGate {

    sealed interface Decision {
        /** Proceed to the remaining gates (frequency cap, target) and show. Consumes the one-shot. */
        data object Show : Decision

        /** User was seen recently: re-arm at [atMs], keep the one-shot pending. */
        data class Reschedule(val atMs: Long) : Decision

        /** Final skip with a wire [reason]. Consumes the one-shot. */
        data class Skip(val reason: String) : Decision
    }

    /** A checklist the nudge could be about, reduced to what the choice needs. */
    data class Candidate(val id: Long, val uncheckedCount: Int, val updatedAtMs: Long)

    sealed interface TargetPick {
        data class Target(val id: Long, val uncheckedCount: Int) : TargetPick
        data class Skip(val reason: String) : TargetPick
    }

    /**
     * @param lastForegroundMs last time the app was actually on screen (0 = never recorded). Must be
     *   a foreground-only signal — a background process wake is not the user coming back.
     * @param armedAtMs when the one-shot was armed (0 = unknown, legacy state: no reschedule possible).
     * @param delayMs the quiet period (22h release; collapsed on debug builds).
     * @param maxWindowMs a reschedule landing later than `armedAt + maxWindow` means the user is
     *   retained on their own — consume as [REASON_ALREADY_ACTIVE].
     */
    fun decide(
        nowMs: Long,
        armedAtMs: Long,
        lastForegroundMs: Long,
        notificationsEnabled: Boolean,
        delayMs: Long,
        maxWindowMs: Long,
    ): Decision {
        val quietUntil = if (lastForegroundMs > 0L) lastForegroundMs + delayMs else 0L
        if (quietUntil > nowMs) {
            if (armedAtMs <= 0L) return Decision.Skip(REASON_ALREADY_ACTIVE)
            return if (quietUntil <= armedAtMs + maxWindowMs) {
                Decision.Reschedule(quietUntil)
            } else {
                Decision.Skip(REASON_ALREADY_ACTIVE)
            }
        }
        if (!notificationsEnabled) return Decision.Skip(REASON_PERMISSION_OFF)
        return Decision.Show
    }

    /**
     * The checklist to nudge about: the armed one if it still has open items; otherwise (deleted or
     * fully checked) the most recently updated candidate with open items. On a debug force run with
     * no armed id the newest checklist (max id) is preferred. [candidates] must already exclude
     * system lists (the v2 Inbox) — the caller passes `ChecklistRepository.projects`.
     */
    fun pickTarget(armedId: Long?, candidates: List<Candidate>, force: Boolean): TargetPick {
        if (candidates.isEmpty()) return TargetPick.Skip(REASON_NO_CHECKLIST)
        val preferred = when {
            armedId != null -> candidates.firstOrNull { it.id == armedId }
            force -> candidates.maxByOrNull { it.id }
            else -> null
        }
        if (preferred != null && preferred.uncheckedCount > 0) {
            return TargetPick.Target(preferred.id, preferred.uncheckedCount)
        }
        val fallback = candidates
            .filter { it.uncheckedCount > 0 }
            .maxWithOrNull(compareBy<Candidate>({ it.updatedAtMs }, { it.id }))
            ?: return TargetPick.Skip(REASON_NO_UNCHECKED)
        return TargetPick.Target(fallback.id, fallback.uncheckedCount)
    }

    // COMEBACK_SKIPPED reasons (wire values — keep in sync with AnalyticsEvents.Retention doc).
    const val REASON_PERMISSION_OFF = "permission_off"
    const val REASON_NO_CHECKLIST = "no_checklist"
    const val REASON_ALREADY_ACTIVE = "already_active"
    const val REASON_FREQUENCY_CAP = "frequency_cap"
    const val REASON_NO_UNCHECKED = "no_unchecked"
}
