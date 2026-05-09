package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import kotlin.random.Random
import kotlinx.serialization.Serializable

/**
 * Checklist template - defines the items to check
 */
@Serializable
data class Checklist(
    val id: Long = 0L,
    val name: String,
    val items: List<ChecklistItem>,
    // One-shot reminder (independent of repeat)
    val reminderAt: Long? = null,
    // Recurring repeat schedule (independent of reminder)
    val repeatRule: ReminderRepeatRule? = null,
    val repeatTimeOfDayMinutes: Int? = null,
    val repeatNextAt: Long? = null,
    val repeatOccurrenceCount: Int = 0,
    val separateCompleted: Boolean = false,
    val position: Int = 0,
    val autoDeleteCompleted: Boolean = false,
    // View mode: Standard (flat list) or Weekly (items grouped by weekday)
    val viewMode: ChecklistViewMode = ChecklistViewMode.Standard
)

/**
 * Single item in a checklist template
 * id is auto-generated for stable LazyColumn keys
 * weekday: ISO day-of-week (1=Mon..7=Sun), non-null only when checklist viewMode=Weekly
 * priority: 0=normal, 1=starred (important). Higher values reserved for future use.
 */
@ConsistentCopyVisibility
@Serializable
data class ChecklistItem private constructor(
    val text: String,
    val checked: Boolean = false,
    val id: String = generateId(),
    val weekday: Int? = null,
    val priority: Int = 0
) {
    constructor(text: String, checked: Boolean = false, weekday: Int? = null, priority: Int = 0) : this(
        text = text,
        checked = checked,
        id = generateId(),
        weekday = weekday,
        priority = priority
    )

    /** Update text while preserving id, checked state, weekday, and priority */
    fun withText(text: String) = ChecklistItem(text, checked, id, weekday, priority)

    /** Update weekday while preserving id, text, checked state, and priority */
    fun withWeekday(weekday: Int?) = ChecklistItem(text, checked, id, weekday, priority)

    /** Toggle priority between 0 (normal) and 1 (starred), preserving all other fields */
    fun withPriority(priority: Int) = ChecklistItem(text, checked, id, weekday, priority)

    companion object {
        private fun generateId() = "${currentTimeMillis()}_${Random.nextInt(0, 10000)}"
    }
}

/**
 * A filled instance of a checklist
 * Each fill represents one "session" of using the checklist (e.g., viewing a specific apartment)
 * isDefault = true means this is the primary fill created automatically with the checklist
 */
@Serializable
data class ChecklistFill(
    val id: Long = 0L,
    val checklistId: Long,
    val name: String,
    val coverImagePath: String? = null,
    val items: List<ChecklistFillItem>,
    val createdAt: Long = 0L,
    val isDefault: Boolean = false
)

/**
 * Item state in a filled checklist
 * id is auto-generated for stable LazyColumn keys
 * weekday: ISO day-of-week (1=Mon..7=Sun), non-null only when checklist viewMode=Weekly
 * priority: 0=normal, 1=starred (important). Higher values reserved for future use.
 *
 * Per-item reminder fields mirror the checklist-level fields in [Checklist]:
 * - [reminderAt]: one-shot trigger epoch millis; null = no pending one-shot
 * - [repeatRule]: recurring schedule; null = not recurring
 * - [repeatTimeOfDayMinutes]: minutes-since-midnight for repeat trigger
 * - [repeatNextAt]: next repeat epoch millis
 * - [repeatOccurrenceCount]: how many times this item's repeat has fired
 */
@ConsistentCopyVisibility
@Serializable
data class ChecklistFillItem private constructor(
    val text: String,
    val checked: Boolean,
    val note: String? = null,
    val id: String = generateId(),
    val weekday: Int? = null,
    val priority: Int = 0,
    // ── Per-item reminder fields ──
    val reminderAt: Long? = null,
    val repeatRule: ReminderRepeatRule? = null,
    val repeatTimeOfDayMinutes: Int? = null,
    val repeatNextAt: Long? = null,
    val repeatOccurrenceCount: Int = 0
) {
    constructor(
        text: String,
        checked: Boolean,
        note: String? = null,
        weekday: Int? = null,
        priority: Int = 0
    ) : this(
        text = text,
        checked = checked,
        note = note,
        id = generateId(),
        weekday = weekday,
        priority = priority
    )

    /** Update checked state while preserving id, weekday, priority, and reminder fields */
    fun withChecked(checked: Boolean) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount
    )

    /** Update note while preserving id, weekday, priority, and reminder fields */
    fun withNote(note: String?) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount
    )

    /** Update weekday while preserving id, text, checked state, note, priority, and reminder fields */
    fun withWeekday(weekday: Int?) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount
    )

    /** Toggle priority between 0 (normal) and 1 (starred); preserves all other fields */
    fun withPriority(priority: Int) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount
    )

    /** Set or clear the one-shot reminder timestamp; preserves all other fields */
    fun withReminderAt(reminderAt: Long?) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount
    )

    /** Set the recurring repeat schedule; preserves all other fields */
    fun withRepeatRule(
        rule: ReminderRepeatRule,
        timeOfDayMinutes: Int,
        nextAt: Long
    ) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, rule, timeOfDayMinutes, nextAt, repeatOccurrenceCount
    )

    /** Advance the repeat schedule to the next trigger; preserves all other fields */
    fun withRepeatAdvanced(nextAt: Long?, newCount: Int) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, nextAt, newCount
    )

    /** Clear all reminder data (both one-shot and repeat) while preserving all other fields */
    fun withReminderCleared() = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt = null,
        repeatRule = null,
        repeatTimeOfDayMinutes = null,
        repeatNextAt = null,
        repeatOccurrenceCount = 0
    )

    /** Returns true if this item has any active reminder (one-shot OR recurring) */
    val hasActiveReminder: Boolean
        get() = reminderAt != null || repeatRule != null

    companion object {
        private fun generateId() = "${currentTimeMillis()}_${Random.nextInt(0, 10000)}"
    }
}
