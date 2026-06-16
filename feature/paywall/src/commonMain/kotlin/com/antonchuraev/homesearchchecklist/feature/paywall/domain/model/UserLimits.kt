package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

/**
 * Represents the limits for free users.
 * Premium users have no limits.
 */
data class UserLimits(
    val maxChecklists: Int,
    val maxFillsPerChecklist: Int,
    val currentChecklistCount: Int,
    val isPremium: Boolean,
    // Weekly mode limits (separate from standard checklist limit)
    val maxWeeklyChecklists: Int = if (isPremium) Int.MAX_VALUE else 1,
    val currentWeeklyChecklistCount: Int = 0,
    // Recurring reminder limit (RC-driven: max_recurring_reminders_free). Premium = unlimited.
    val maxRecurringReminders: Int = if (isPremium) Int.MAX_VALUE else 1
) {
    val canCreateChecklist: Boolean
        get() = isPremium || currentChecklistCount < maxChecklists

    val remainingChecklists: Int
        get() = if (isPremium) Int.MAX_VALUE else maxOf(0, maxChecklists - currentChecklistCount)

    /** Whether the user can create a new weekly checklist. Free users: max 1, Premium: unlimited. */
    val canCreateWeeklyChecklist: Boolean
        get() = isPremium || currentWeeklyChecklistCount < maxWeeklyChecklists

    /** Whether the user can create another recurring reminder. Free users: RC limit, Premium: unlimited. */
    fun canCreateRecurringReminder(currentReminderCount: Int): Boolean =
        isPremium || currentReminderCount < maxRecurringReminders

    fun canCreateFill(currentFillCount: Int): Boolean =
        isPremium || currentFillCount < maxFillsPerChecklist

    fun remainingFills(currentFillCount: Int): Int =
        if (isPremium) Int.MAX_VALUE else maxOf(0, maxFillsPerChecklist - currentFillCount)
}
