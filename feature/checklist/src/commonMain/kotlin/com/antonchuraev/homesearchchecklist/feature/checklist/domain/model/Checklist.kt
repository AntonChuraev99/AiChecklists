package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import kotlin.random.Random
import kotlinx.serialization.Serializable

/**
 * Client-generated id for the entities that live inside a checklist's JSON blob (items, fill items,
 * attachments) and therefore have no database sequence to draw from.
 *
 * Shape is `"<prefix><epochMillis>_<random>"`. Nothing parses it — it is an opaque key — but the
 * timestamp half keeps it sortable and greppable in logs.
 *
 * **The random half carries the entire uniqueness burden.** These entities are created in tight
 * loops (an AI-built list maps up to 100 items in one pass, a template application does the same),
 * so `currentTimeMillis()` is identical across the whole batch. The previous 10^4 suffix space made
 * a 100-item batch collide with probability ~39% by the birthday bound (`1 - exp(-N^2/2M)`), and two
 * items sharing an id inside one checklist silently misroute toggles and collapse folder membership
 * — the flake seen in `ProjectRowMappingTest` was this, not a test bug. 63 bits take the same batch
 * to ~1e-16.
 *
 * Single definition on purpose: the three call sites used to carry three copies of the expression
 * plus a comment claiming they matched, which is the drift pattern this repo has been bitten by.
 */
internal fun generateEntityId(prefix: String = ""): String =
    "$prefix${currentTimeMillis()}_${Random.nextLong(0, Long.MAX_VALUE)}"

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
    // Per-checklist opt-in: fire the checklist-level reminder as an alarm-style full-screen
    // notification (over the lock screen). Mirrors ChecklistFillItem.reminderFullScreen (per-item).
    val reminderFullScreen: Boolean = false,
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
    /**
     * True only for the auto-created **system Inbox** (v2 nav arm quick-capture zone).
     *
     * It stays a real checklist row so sync / Firestore / widget / MCP keep working unchanged, but
     * it is hidden from the Projects list and from every checklist picker, and is never counted
     * against the free-tier checklist limit — otherwise the v2 arm would silently lose one of the
     * 5 free slots, a monetisation delta *inside* the A/B experiment.
     *
     * Kept LAST in the constructor on purpose: [Checklist] is `@Serializable` and appending a
     * defaulted field keeps every already-persisted JSON payload decodable.
     */
    val isInbox: Boolean = false,
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
     * Used when the caller already owns an id — restoring a persisted node, or mirroring a template
     * item into a fill so the two stay linked.
     *
     * It is no longer needed as a collision workaround: [generateEntityId] now draws 63 bits, so a
     * bulk parse of AI nodes inside one millisecond no longer risks a duplicate id (it did at ~12%
     * over 50 nodes with the old 10^4 suffix, which broke parent linking whenever a child's
     * [parentId] pointed at a duplicated folder id). Existing/persisted ids are NOT touched.
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
        private fun generateId() = generateEntityId()
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
 *
 * [alarmEnabled]: splits "this task has a date" from "an OS alarm is armed for it". A date is free
 * and unlimited; the alarm is the metered half, so the two need separate fields for the due chip to
 * be able to say which one a task actually has. Defaults to `true` — every [reminderAt] already in
 * the field was written by a path that also armed an alarm, so a `false` default would decode all
 * existing data as "alarm off" and silently stop notifying current users.
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
    // ── Per-item full-screen-intent reminder (Android alarm-style over lock screen) ──
    // Lives in the JSON blob (no SQL column / Firestore field — fills sync via itemsJson).
    // Only meaningful while a reminder is set; cleared by withReminderCleared().
    val reminderFullScreen: Boolean = false,
    // ── Is an OS alarm actually armed for this date? (end of constructor — safest for JSON schema) ──
    // Same carrier as every field above it: the fill's itemsJson blob, so no SQL migration and no
    // Firestore mapper entry (AndroidFirestoreSyncDataSource maps the fill ROW, not its items).
    // Default TRUE, never false: legacy rows carry no such key, and reading them as "alarm off"
    // would mute notifications for every existing user. Cleared back to true by withReminderCleared().
    val alarmEnabled: Boolean = true,
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
        attachments, templateItemId, reminderFullScreen, alarmEnabled,
    )

    /** Update note while preserving id, weekday, priority, reminder fields, attachments, and template link */
    fun withNote(note: String?) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments, templateItemId, reminderFullScreen, alarmEnabled,
    )

    /** Update weekday while preserving id, text, checked state, note, priority, reminder fields, attachments, and template link */
    fun withWeekday(weekday: Int?) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments, templateItemId, reminderFullScreen, alarmEnabled,
    )

    /** Update text while preserving id, checked state, note, weekday, priority, reminder fields, attachments, and template link */
    fun withText(text: String) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments, templateItemId, reminderFullScreen, alarmEnabled,
    )

    /** Toggle priority between 0 (normal) and 1 (starred); preserves all other fields */
    fun withPriority(priority: Int) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments, templateItemId, reminderFullScreen, alarmEnabled,
    )

    /** Set or clear the one-shot reminder timestamp; preserves all other fields */
    fun withReminderAt(reminderAt: Long?) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments, templateItemId, reminderFullScreen, alarmEnabled,
    )

    /** Set the recurring repeat schedule; preserves all other fields */
    fun withRepeatRule(
        rule: ReminderRepeatRule,
        timeOfDayMinutes: Int,
        nextAt: Long,
    ) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, rule, timeOfDayMinutes, nextAt, repeatOccurrenceCount,
        attachments, templateItemId, reminderFullScreen, alarmEnabled,
    )

    /** Advance the repeat schedule to the next trigger; preserves all other fields */
    fun withRepeatAdvanced(nextAt: Long?, newCount: Int) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, nextAt, newCount,
        attachments, templateItemId, reminderFullScreen, alarmEnabled,
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
        reminderFullScreen = false,
        // Back to the default, not left at whatever it was: with no date there is no alarm to keep
        // disarmed, and a sticky `false` would make the NEXT date the user sets arrive silently.
        alarmEnabled = true,
    )

    /** Append [att] to the end of [attachments]; preserves all other fields */
    fun withAttachmentAdded(att: Attachment) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments = attachments + att,
        templateItemId = templateItemId,
        reminderFullScreen = reminderFullScreen,
        alarmEnabled = alarmEnabled,
    )

    /** Remove the attachment with [attachmentId]; preserves order and all other fields. No-op if id not found. */
    fun withAttachmentRemoved(attachmentId: String) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments = attachments.filter { it.id != attachmentId },
        templateItemId = templateItemId,
        reminderFullScreen = reminderFullScreen,
        alarmEnabled = alarmEnabled,
    )

    /** Replace [attachments] entirely; used by the repository after a platform store/delete op. */
    fun withAttachments(attachments: List<Attachment>) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments = attachments,
        templateItemId = templateItemId,
        reminderFullScreen = reminderFullScreen,
        alarmEnabled = alarmEnabled,
    )

    /** Set or update the stable link to the template [ChecklistItem.id]; preserves all other fields */
    fun withTemplateItemId(templateItemId: String?) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments, templateItemId, reminderFullScreen, alarmEnabled,
    )

    /** Set the per-item full-screen-intent flag (alarm-style delivery); preserves all other fields */
    fun withReminderFullScreen(fullScreen: Boolean) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments, templateItemId, fullScreen, alarmEnabled,
    )

    /**
     * Arm or disarm the OS alarm without touching the date; preserves all other fields.
     *
     * The date stays exactly where it is — that is the whole point of the split. Turning the alarm
     * off leaves a task that still shows its due chip and still sorts into "Today", it just does not
     * buzz.
     */
    fun withAlarmEnabled(alarmEnabled: Boolean) = ChecklistFillItem(
        text, checked, note, id, weekday, priority,
        reminderAt, repeatRule, repeatTimeOfDayMinutes, repeatNextAt, repeatOccurrenceCount,
        attachments, templateItemId, reminderFullScreen, alarmEnabled,
    )

    /** Returns true if this item has any active reminder (one-shot OR recurring) */
    val hasActiveReminder: Boolean
        get() = reminderAt != null || repeatRule != null

    companion object {
        private fun generateId() = generateEntityId()
    }
}
