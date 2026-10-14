package com.antonchuraev.homesearchchecklist.activation

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.ReminderTarget
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import kotlinx.coroutines.flow.first

/** `source` of `item_reminder_set` when the activation sheet set it. */
private const val SOURCE_ACTIVATION_SHEET = "activation_sheet"

/**
 * Sets the activation sheet's chosen reminder on its [target] — the host's only write.
 *
 * - [ReminderTarget.Checklist]: checklist-level reminder (`setReminder` + `scheduleReminder`),
 *   exactly what the sheet always did.
 * - [ReminderTarget.Item]: the existing item-reminder path, as InboxViewModel.saveItemReminder
 *   writes it — `withReminderAt` on the default-fill row, persist, `scheduleItemReminder`, then
 *   `item_reminder_set`. Persist BEFORE arming: an alarm for a row the UI says has no reminder
 *   is worse than no alarm.
 *
 * Throws on failure (item gone, write failed) — the host logs it and reports no snackbar.
 */
suspend fun applyActivationReminder(
    target: ReminderTarget,
    triggerAtMs: Long,
    repository: ChecklistRepository,
    scheduler: ChecklistReminderScheduler,
    analytics: AnalyticsTracker,
) {
    when (target) {
        is ReminderTarget.Checklist -> {
            repository.setReminder(target.checklistId, triggerAtMs)
            scheduler.scheduleReminder(target.checklistId, triggerAtMs)
        }
        is ReminderTarget.Item -> {
            val fill = repository.getDefaultFillByChecklistId(target.checklistId).first()
                ?: error("no default fill for checklist ${target.checklistId}")
            val item = fill.items.firstOrNull { it.id == target.itemId }
                ?: error("task ${target.itemId} no longer in checklist ${target.checklistId}")
            val updated = item.withReminderAt(triggerAtMs)
            repository.updateFill(fill.copy(items = fill.items.map { if (it.id == item.id) updated else it }))
            scheduler.scheduleItemReminder(target.checklistId, fill.id, item.id, triggerAtMs)
            analytics.event(
                AnalyticsEvents.Reminder.ITEM_SET,
                mapOf(
                    AnalyticsParams.CHECKLIST_ID to target.checklistId.toString(),
                    "has_repeat" to "false",
                    AnalyticsParams.SOURCE to SOURCE_ACTIVATION_SHEET,
                ),
            )
        }
    }
}

/** Preview line of the sheet: the task text for an item, the checklist name otherwise. */
suspend fun activationReminderPreviewText(target: ReminderTarget, repository: ChecklistRepository): String =
    when (target) {
        is ReminderTarget.Item -> target.text
        is ReminderTarget.Checklist -> repository.getChecklistById(target.checklistId)?.name.orEmpty()
    }
