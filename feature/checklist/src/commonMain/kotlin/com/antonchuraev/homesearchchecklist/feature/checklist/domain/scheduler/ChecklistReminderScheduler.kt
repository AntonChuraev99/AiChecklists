package com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler

interface ChecklistReminderScheduler {
    // ── Checklist-level one-shot reminders ──

    fun scheduleReminder(checklistId: Long, triggerAtMillis: Long)
    fun cancelReminder(checklistId: Long)
    suspend fun rescheduleAllActiveReminders()

    // ── Checklist-level independent repeat schedule ──

    fun scheduleRepeat(checklistId: Long, triggerAtMillis: Long)
    fun cancelRepeat(checklistId: Long)
    suspend fun rescheduleAllActiveRepeats()

    // ── Per-item one-shot reminders ──
    //
    // Android impl note: use a dedicated request-code namespace to avoid
    // collisions with checklist-level alarms. Suggested ranges:
    //   Checklist one-shot :  checklistId.toInt()           (no offset)
    //   Checklist repeat   :  checklistId.toInt() + 100_000
    //   Item one-shot      :  itemId.hashCode()  + 200_000  (absolute value)
    //   Item repeat        :  itemId.hashCode()  + 300_000  (absolute value)
    // itemId is a stable String id generated at item creation time.

    /**
     * Schedule (or re-schedule) a one-shot alarm for a single fill item.
     *
     * @param checklistId  Used in the notification tap deep-link.
     * @param fillId       Used to load + update the correct fill on delivery.
     * @param itemId       Stable String id of the [com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem].
     * @param triggerAtMillis  Epoch millis when the alarm should fire.
     */
    fun scheduleItemReminder(checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long)

    /**
     * Cancel the one-shot alarm for a single fill item.
     * No-op if no alarm was previously registered for this item.
     */
    fun cancelItemReminder(checklistId: Long, fillId: Long, itemId: String)

    // ── Per-item repeat schedule ──

    /**
     * Schedule (or re-schedule) the next recurring alarm for a single fill item.
     * Called both on initial setup and after each occurrence fires (advance pattern).
     *
     * @param triggerAtMillis  Epoch millis for the *next* occurrence.
     */
    fun scheduleItemRepeat(checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long)

    /**
     * Cancel the recurring alarm for a single fill item.
     * No-op if no repeat alarm was previously registered for this item.
     */
    fun cancelItemRepeat(checklistId: Long, fillId: Long, itemId: String)

    // ── Permission helpers (default implementations — no-op on non-Android) ──

    /** Returns true if exact alarms can be scheduled (always true below API 31). */
    fun canScheduleExactAlarms(): Boolean = true

    /** Opens system settings for granting exact alarm permission. No-op below API 31. */
    fun openExactAlarmSettings() {}

    /** Returns true if the app can post notifications (always true below API 33). */
    fun hasNotificationPermission(): Boolean = true
}
