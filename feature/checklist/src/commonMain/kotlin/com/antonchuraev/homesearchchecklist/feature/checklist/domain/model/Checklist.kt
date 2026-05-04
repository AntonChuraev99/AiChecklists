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
 */
@ConsistentCopyVisibility
@Serializable
data class ChecklistItem private constructor(
    val text: String,
    val checked: Boolean = false,
    val id: String = generateId(),
    val weekday: Int? = null
) {
    constructor(text: String, checked: Boolean = false, weekday: Int? = null) : this(
        text = text,
        checked = checked,
        id = generateId(),
        weekday = weekday
    )

    /** Update text while preserving id, checked state, and weekday */
    fun withText(text: String) = ChecklistItem(text, checked, id, weekday)

    /** Update weekday while preserving id, text, and checked state */
    fun withWeekday(weekday: Int?) = ChecklistItem(text, checked, id, weekday)

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
 */
@ConsistentCopyVisibility
@Serializable
data class ChecklistFillItem private constructor(
    val text: String,
    val checked: Boolean,
    val note: String? = null,
    val id: String = generateId(),
    val weekday: Int? = null
) {
    constructor(
        text: String,
        checked: Boolean,
        note: String? = null,
        weekday: Int? = null
    ) : this(
        text = text,
        checked = checked,
        note = note,
        id = generateId(),
        weekday = weekday
    )

    /** Update checked state while preserving id and weekday */
    fun withChecked(checked: Boolean) = ChecklistFillItem(text, checked, note, id, weekday)

    /** Update note while preserving id and weekday */
    fun withNote(note: String?) = ChecklistFillItem(text, checked, note, id, weekday)

    /** Update weekday while preserving id, text, checked state, and note */
    fun withWeekday(weekday: Int?) = ChecklistFillItem(text, checked, note, id, weekday)

    companion object {
        private fun generateId() = "${currentTimeMillis()}_${Random.nextInt(0, 10000)}"
    }
}
