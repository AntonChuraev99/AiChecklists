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
    val reminderAt: Long? = null,
    val repeatRule: ReminderRepeatRule? = null,
    val repeatTimeOfDayMinutes: Int? = null,
    val repeatNextAt: Long? = null,
    val repeatOccurrenceCount: Int = 0,
    val separateCompleted: Boolean = false,
    val position: Int = 0,
    val autoDeleteCompleted: Boolean = false,
    val viewMode: ChecklistViewMode = ChecklistViewMode.Standard,
    val foldersEnabled: Boolean = false,
    val cloudId: String? = null,
    val userId: String? = null,
    val updatedAt: Long = 0L,
    val syncStatus: Int = 0,
    val isDeleted: Boolean = false,
)

/**
 * Single item in a checklist template
 * id is auto-generated for stable LazyColumn keys
 * weekday: ISO day-of-week (1=Mon..7=Sun), non-null only when checklist viewMode=Weekly
 * priority: 0=normal, 1=starred (important). Higher values reserved for future use.
 *
 * Folder-tree structure lives ONLY on the template (fills stay flat and link back via
 * [ChecklistFillItem.templateItemId]):
 * - [type]: [ChecklistNodeType.ITEM] (a checkable leaf, default) or [ChecklistNodeType.FOLDER]
 *   (a container). JSON-safe: defaults + ignoreUnknownKeys mean legacy rows decode as ITEM.
 * - [parentId]: id of the parent FOLDER node; null = root of the checklist (default). No DB
 *   migration needed — both fields live inside the items JSON blob (schema version 16).
 */
@ConsistentCopyVisibility
@Serializable
data class ChecklistItem private constructor(
    val text: String,
    val checked: Boolean = false,
    val id: String = generateId(),
    val weekday: Int? = null,
    val priority: Int = 0,
    val type: ChecklistNodeType = ChecklistNodeType.ITEM,
    val parentId: String? = null,
) {
    constructor(
        text: String,
        checked: Boolean = false,
        weekday: Int? = null,
        priority: Int = 0,
        type: ChecklistNodeType = ChecklistNodeType.ITEM,
        parentId: String? = null,
    ) : this(
        text = text,
        checked = checked,
        id = generateId(),
        weekday = weekday,
        priority = priority,
        type = type,
        parentId = parentId,
    )

    /** Update text while preserving id, checked state, weekday, priority, type, and parentId */
    fun withText(text: String) = ChecklistItem(text, checked, id, weekday, priority, type, parentId)

    /**
     * Assign an explicit [id] while preserving all other fields.
     *
     * The default id is `"${currentTimeMillis()}_${Random.nextInt}"`, which can collide when
     * many items are created in a tight loop (e.g. bulk-parsing dozens of AI nodes in the same
     * millisecond — birthday-paradox ~12% over 50 nodes). A collision breaks parent linking
     * because a child's [parentId] could point at a duplicated folder id. Callers building a
     * folder tree from AI output should assign a guaranteed-unique id (e.g. `Uuid.random()`).
     * Existing/persisted ids are NOT touched — this is only for freshly created nodes.
     */
    fun withId(id: String) = ChecklistItem(text, checked, id, weekday, priority, type, parentId)

    /** Update weekday while preserving id, text, checked state, priority, type, and parentId */
    fun withWeekday(weekday: Int?) = ChecklistItem(text, checked, id, weekday, priority, type, parentId)

    /** Toggle priority between 0 (normal) and 1 (starred), preserving all other fields */
    fun withPriority(priority: Int) = ChecklistItem(text, checked, id, weekday, priority, type, parentId)

    /** Move this node under [parentId] (null = checklist root); preserves all other fields */
    fun withParentId(parentId: String?) = ChecklistItem(text, checked, id, weekday, priority, type, parentId)

    /** Change the node [type] (ITEM vs FOLDER); preserves all other fields */
    fun withType(type: ChecklistNodeType) = ChecklistItem(text, checked, id, weekday, priority, type, parentId)

    /** True when this node is a folder container rather than a checkable leaf */
    val isFolder: Boolean get() = type == ChecklistNodeType.FOLDER

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
    val isDefault: Boolean = false,
    val cloudId: String? = null,
    val userId: String? = null,
    val updatedAt: Long = 0L,
    val syncStatus: Int = 0,
    val isDeleted: Boolean = false,
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
 *
 * [templateItemId]: stable link to the template [ChecklistItem.id]; null on legacy rows,
 * backfilled by text match on next write. Lets fill↔template reconciliation survive text
 * edits and (later) nested-structure reshuffles without orphaning checked/note/attachments.
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
    // ── Stable link to the template item (end of constructor — safest for JSON schema) ──
    val templateItemId: String? = null,
) {
    constructor(
        text: String,
        checked: Boolean,
        note: String? = null,
        weekday: Int? = null,
        priority: Int = 0,
        templateItemId: String? = null,
    ) : this(
        text = text,
        checked = checked,
        note = note,
        id = generateId(),
        weekday = weekday,
        priority = priority,
        attachments = emptyList(),
        templateItemId = templateItemId,
    )

    /** Update checked state while preserving id, weekday, priority, reminder fields, attachments, and template link */
    fun withChecked(checked: Boolean) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments, templateItemId,
    )

    /** Update note while preserving id, weekday, priority, reminder fields, attachments, and template link */
    fun withNote(note: String?) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments, templateItemId,
    )

    /** Update weekday while preserving id, text, checked state, note, priority, reminder fields, attachments, and template link */
    fun withWeekday(weekday: Int?) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments, templateItemId,
    )

    /** Update text while preserving id, checked state, note, weekday, priority, reminder fields, attachments, and template link */
    fun withText(text: String) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments, templateItemId,
    )

    /** Toggle priority between 0 (normal) and 1 (starred); preserves all other fields */
    fun withPriority(priority: Int) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments, templateItemId,
    )

    /** Set or clear the one-shot reminder timestamp; preserves all other fields */
    fun withReminderAt(reminderAt: Long?) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments, templateItemId,
    )

    /** Set the recurring repeat schedule; preserves all other fields */
    fun withRepeatRule(
        rule: ReminderRepeatRule,
        timeOfDayMinutes: Int,
        nextAt: Long,
    ) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, rule, timeOfDayMinutes, nextAt, repeatOccurrenceCount,
        attachments, templateItemId,
    )

    /** Advance the repeat schedule to the next trigger; preserves all other fields */
    fun withRepeatAdvanced(nextAt: Long?, newCount: Int) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, nextAt, newCount,
        attachments, templateItemId,
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
        templateItemId = templateItemId,
    )

    /** Append [att] to the end of [attachments]; preserves all other fields */
    fun withAttachmentAdded(att: Attachment) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments = attachments + att,
        templateItemId = templateItemId,
    )

    /** Remove the attachment with [attachmentId]; preserves order and all other fields. No-op if id not found. */
    fun withAttachmentRemoved(attachmentId: String) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments = attachments.filter { it.id != attachmentId },
        templateItemId = templateItemId,
    )

    /** Replace [attachments] entirely; used by the repository after a platform store/delete op. */
    fun withAttachments(attachments: List<Attachment>) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments = attachments,
        templateItemId = templateItemId,
    )

    /** Set or update the stable link to the template [ChecklistItem.id]; preserves all other fields */
    fun withTemplateItemId(templateItemId: String?) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments, templateItemId,
    )

    /** Returns true if this item has any active reminder (one-shot OR recurring) */
    val hasActiveReminder: Boolean
        get() = reminderAt != null || repeatRule != null

    companion object {
        private fun generateId() = "${currentTimeMillis()}_${Random.nextInt(0, 10000)}"
    }
}
