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
 *
 * [attachments]: user-added files/images. Lives in the JSON blob (no SQL column).
 * Decoded with ignoreUnknownKeys=true — old DB rows without this field default to emptyList().
 * Attachments belong to the fill only (not the template), matching [note] semantics.
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
    val repeatOccurrenceCount: Int = 0,
    // ── Attachments (end of constructor — safest position for JSON schema additions) ──
    val attachments: List<Attachment> = emptyList(),
) {
    constructor(
        text: String,
        checked: Boolean,
        note: String? = null,
        weekday: Int? = null,
        priority: Int = 0,
    ) : this(
        text = text,
        checked = checked,
        note = note,
        id = generateId(),
        weekday = weekday,
        priority = priority,
        attachments = emptyList(),
    )

    /** Update checked state while preserving id, weekday, priority, reminder fields, and attachments */
    fun withChecked(checked: Boolean) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments,
    )

    /** Update note while preserving id, weekday, priority, reminder fields, and attachments */
    fun withNote(note: String?) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments,
    )

    /** Update weekday while preserving id, text, checked state, note, priority, reminder fields, and attachments */
    fun withWeekday(weekday: Int?) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments,
    )

    /** Update text while preserving id, checked state, note, weekday, priority, reminder fields, and attachments */
    fun withText(text: String) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments,
    )

    /** Toggle priority between 0 (normal) and 1 (starred); preserves all other fields */
    fun withPriority(priority: Int) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments,
    )

    /** Set or clear the one-shot reminder timestamp; preserves all other fields */
    fun withReminderAt(reminderAt: Long?) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments,
    )

    /** Set the recurring repeat schedule; preserves all other fields */
    fun withRepeatRule(
        rule: ReminderRepeatRule,
        timeOfDayMinutes: Int,
        nextAt: Long,
    ) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, rule, timeOfDayMinutes, nextAt, repeatOccurrenceCount,
        attachments,
    )

    /** Advance the repeat schedule to the next trigger; preserves all other fields */
    fun withRepeatAdvanced(nextAt: Long?, newCount: Int) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, nextAt, newCount,
        attachments,
    )

    /** Clear all reminder data (both one-shot and repeat) while preserving all other fields */
    fun withReminderCleared() = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt = null,
        repeatRule = null,
        repeatTimeOfDayMinutes = null,
        repeatNextAt = null,
        repeatOccurrenceCount = 0,
        attachments = attachments,
    )

    /** Append [att] to the end of [attachments]; preserves all other fields */
    fun withAttachmentAdded(att: Attachment) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments = attachments + att,
    )

    /** Remove the attachment with [attachmentId]; preserves order and all other fields. No-op if id not found. */
    fun withAttachmentRemoved(attachmentId: String) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments = attachments.filter { it.id != attachmentId },
    )

    /** Replace [attachments] entirely; used by the repository after a platform store/delete op. */
    fun withAttachments(attachments: List<Attachment>) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments = attachments,
    )

    /** Returns true if this item has any active reminder (one-shot OR recurring) */
    val hasActiveReminder: Boolean
        get() = reminderAt != null || repeatRule != null

    companion object {
        private fun generateId() = "${currentTimeMillis()}_${Random.nextInt(0, 10000)}"
    }
}
