package com.antonchuraev.homesearchchecklist.feature.home.presentation.create

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.ParsedDateToken
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Which reminder preset chip is currently active on a task being created. The four reminder chips
 * are single-select among themselves; [CUSTOM] is set when the user resolves a time via the
 * "Pick time…" date/time picker (the chip then shows the absolute datetime).
 */
enum class ItemCreateReminderPreset { ONE_HOUR, TOMORROW_MORNING, TONIGHT, CUSTOM }

/**
 * A file picked while composing a task, BEFORE the task exists. Written to the freshly created item
 * on Send; dropped when the draft is abandoned.
 *
 * [sourcePath] is the raw picker result path (rendered directly by Coil in the preview strip — it is
 * NOT yet a stored/synced attachment path).
 */
data class PendingItemAttachment(
    val sourcePath: String,
    val fileName: String,
    val mimeType: String?,
)

/**
 * Everything the user has staged about a task that does not exist yet — the text plus every chip
 * they toggled — held as ONE value instead of as loose fields on a screen state.
 *
 * The shape is deliberately host-agnostic: the same draft backs the Inbox, the Today tab and the
 * checklist detail screen, so the create dock behaves identically wherever it is opened. Before this
 * existed, only the detail screen carried these fields and the two v2 tabs shipped a bare text input
 * — which is how a "quick add" on Today could not set the reminder that made the task visible on the
 * very screen it was created from.
 *
 * Mutation goes through the extension functions below rather than through `copy` at call sites: the
 * chip rules (single-select reminders, re-tap clears, a custom time cancels a repeat) are the same on
 * every host, and a second hand-rolled copy of them is exactly the drift this type removes.
 */
data class TaskDraft(
    val text: String = "",
    /**
     * Live Smart-Add parse of [text] ("tomorrow at 7" → a date token), shown as a preview chip.
     * Applied to the created item only when no explicit reminder chip is active — an explicit chip is
     * a deliberate choice and must win over a phrase the parser merely recognised.
     */
    val parsedToken: ParsedDateToken? = null,
    val reminderPreset: ItemCreateReminderPreset? = null,
    /** Resolved epoch millis for [reminderPreset]; null when no reminder chip is active. */
    val reminderAt: Long? = null,
    val important: Boolean = false,
    val repeat: PendingRepeatConfig? = null,
    val attachments: List<PendingItemAttachment> = emptyList(),
) {
    /** Send is refused on a blank text even when files are staged — an untitled item is unreadable. */
    val canSend: Boolean get() = text.isNotBlank()

    val hasAttachments: Boolean get() = attachments.isNotEmpty()
}

/**
 * Toggle semantics of the four reminder chips: tapping the ACTIVE one clears the reminder, tapping
 * another one replaces it. Re-tap-to-clear is why this is not a plain setter — without it a reminder
 * set by accident could only be removed by sending the task and editing it.
 *
 * [now] is passed in rather than read here so the caller (and its tests) controls the clock.
 */
fun TaskDraft.withPreset(
    preset: ItemCreateReminderPreset,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): TaskDraft = if (reminderPreset == preset) {
    copy(reminderPreset = null, reminderAt = null)
} else {
    copy(reminderPreset = preset, reminderAt = computePresetReminderAt(preset, now, timeZone))
}

/**
 * Result of the "Pick time…" picker. A null [at] cancels the reminder.
 *
 * Clearing [repeat] is intentional and matches the detail screen's shipped behaviour: a one-shot
 * absolute time and a recurring rule are two different answers to "when", and keeping both would
 * leave the chip row showing a contradiction the create path cannot honour.
 */
fun TaskDraft.withCustomReminder(at: Long?): TaskDraft = copy(
    reminderAt = at,
    reminderPreset = if (at != null) ItemCreateReminderPreset.CUSTOM else null,
    repeat = null,
)

fun TaskDraft.withImportantToggled(): TaskDraft = copy(important = !important)

/** Saving a repeat rule supersedes any one-shot reminder, mirroring [withCustomReminder]. */
fun TaskDraft.withRepeat(config: PendingRepeatConfig?): TaskDraft = copy(
    repeat = config,
    reminderAt = if (config != null) null else reminderAt,
    reminderPreset = if (config != null) null else reminderPreset,
)

fun TaskDraft.withAttachment(attachment: PendingItemAttachment): TaskDraft =
    copy(attachments = attachments + attachment)

fun TaskDraft.withoutAttachment(sourcePath: String): TaskDraft =
    copy(attachments = attachments.filterNot { it.sourcePath == sourcePath })

/**
 * Clears the draft after a successful Send.
 *
 * Everything goes, chips included: the create dock stays open for a SERIES of captures, and a
 * sticky "Tonight" chip would silently stamp a reminder onto every following task the user typed.
 */
fun TaskDraft.cleared(): TaskDraft = TaskDraft()

/**
 * Resolves a quick-preset reminder time: +1h, tomorrow 09:00, and "tonight" 18:00 — rolling to
 * tomorrow 18:00 once past 18:00 today.
 *
 * [ItemCreateReminderPreset.CUSTOM] never reaches here; the picker sets its time directly.
 */
fun computePresetReminderAt(
    preset: ItemCreateReminderPreset,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Long {
    val nowMs = now.toEpochMilliseconds()
    val today = now.toLocalDateTime(timeZone).date
    return when (preset) {
        ItemCreateReminderPreset.ONE_HOUR -> nowMs + 3_600_000L
        ItemCreateReminderPreset.TOMORROW_MORNING -> {
            val tomorrow = today.plus(1, DateTimeUnit.DAY)
            LocalDateTime(tomorrow, LocalTime(MORNING_HOUR, 0)).toInstant(timeZone).toEpochMilliseconds()
        }
        ItemCreateReminderPreset.TONIGHT -> {
            val todayEvening = LocalDateTime(today, LocalTime(EVENING_HOUR, 0))
                .toInstant(timeZone)
                .toEpochMilliseconds()
            if (todayEvening > nowMs) {
                todayEvening
            } else {
                val tomorrow = today.plus(1, DateTimeUnit.DAY)
                LocalDateTime(tomorrow, LocalTime(EVENING_HOUR, 0)).toInstant(timeZone).toEpochMilliseconds()
            }
        }
        ItemCreateReminderPreset.CUSTOM -> nowMs
    }
}

/**
 * The reminder chip pre-selected when the create dock opens from a DAY-shaped screen (Today /
 * Calendar).
 *
 * Those screens draw the day's reminders, so a task captured there without one is created into a
 * list the user is not looking at and vanishes from the screen that produced it. Pre-selecting a
 * chip — rather than silently stamping a time — keeps the reminder visible and one tap away from
 * being removed.
 *
 * "Tonight" is the default; past [EVENING_HOUR] it would resolve to TOMORROW evening (see
 * [computePresetReminderAt]), which is no longer "today", so the evening hours fall back to "+1 hour"
 * — still inside the day the user is looking at.
 */
fun defaultDayScreenPreset(
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ItemCreateReminderPreset =
    if (now.toLocalDateTime(timeZone).hour >= EVENING_HOUR) {
        ItemCreateReminderPreset.ONE_HOUR
    } else {
        ItemCreateReminderPreset.TONIGHT
    }

/**
 * A fresh draft for a DAY-shaped screen: empty text with the day's reminder chip already on.
 *
 * Used both when the capture dock first opens and after every Send, so the preset is recomputed
 * against the clock each time. Seeding it once at construction would leave a task typed at 19:00
 * carrying the "Tonight" the user saw at 17:00.
 */
fun dayScreenDraft(
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): TaskDraft = TaskDraft().withPreset(defaultDayScreenPreset(now, timeZone), now, timeZone)

/**
 * The reminder to actually write on Send, recomputed from the ACTIVE preset at this moment.
 *
 * [TaskDraft.reminderAt] is resolved when the chip is tapped, so a dock left open across the preset's
 * own boundary would fire in the past: tap "Tonight" at 17:50, finish typing at 18:10, and the stored
 * time is already gone. A picked CUSTOM time is returned untouched — that one is an absolute instant
 * the user chose, not a relative promise.
 */
fun TaskDraft.resolveReminderAtNow(
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Long? = when (reminderPreset) {
    null -> null
    ItemCreateReminderPreset.CUSTOM -> reminderAt
    else -> computePresetReminderAt(reminderPreset, now, timeZone)
}

/** 09:00 — what "tomorrow morning" means, matching the reminder sheet. */
private const val MORNING_HOUR = 9

/** 18:00 — what "tonight" means, matching the reminder sheet. */
private const val EVENING_HOUR = 18
