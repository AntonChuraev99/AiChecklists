package com.antonchuraev.homesearchchecklist.feature.home.presentation.create

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.ParsedDateToken
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Which reminder preset chip is currently active on a task being created. The reminder chips are
 * single-select among themselves; [CUSTOM] is set when the user resolves a time via the
 * "Pick time…" date/time picker (the chip then shows the absolute datetime).
 *
 * Declaration order here is arbitrary — the order the user SEES is [presetDisplayOrder], which the
 * dock renders and which this enum deliberately does not encode: reordering chips is a design call,
 * and tying it to `values()` would make a cosmetic change a source-compatibility one.
 */
enum class ItemCreateReminderPreset(val anchor: ReminderAnchor) {
    ONE_HOUR(ReminderAnchor.OFFSET_FROM_SEND),
    TOMORROW_MORNING(ReminderAnchor.WALL_CLOCK),
    TONIGHT(ReminderAnchor.WALL_CLOCK),
    WEEKEND(ReminderAnchor.WALL_CLOCK),
    NEXT_WEEK(ReminderAnchor.WALL_CLOCK),
    CUSTOM(ReminderAnchor.PICKED),
}

/**
 * What a preset's time is measured AGAINST — the property that decides what Send is allowed to do
 * with the moment captured back when the chip was tapped.
 *
 * This is a constructor parameter rather than a KDoc convention on purpose: the dock can sit open
 * for minutes, so every preset is eventually resolved twice, and the two answers only agree for
 * some of them. Adding a preset without deciding which kind it is would be a silent choice, and the
 * wrong silent choice moves a user's task by days without telling them.
 */
enum class ReminderAnchor {
    /**
     * "In 1 hour" — an offset from the moment of SEND. The stored value is a preview, not a
     * promise: an hour spent typing has to move it, or the reminder fires while the user is still
     * looking at the task they just created.
     */
    OFFSET_FROM_SEND,

    /**
     * "Tomorrow 09:00", "Saturday 10:00", "tonight" — a named wall-clock moment. The stored value
     * IS the answer and survives Send untouched; it is recomputed only once it has actually passed.
     * Recomputing one that is still ahead is what silently pushed a Sunday-night "next week" a full
     * seven days on for the sake of five minutes across midnight.
     */
    WALL_CLOCK,

    /** The picker's result: an absolute instant the user chose. Never recomputed, even if past. */
    PICKED,
}

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
     *
     * Written by the capture host, debounced, on every change to [text]; [resolveReminderAtNow] is
     * where it is honoured, which is also what the leading chip's label is read from, so what the
     * dock SHOWS and what Send WRITES are the same value by construction. Until 2026-08-19 nothing
     * wrote it at all: the field, the chip branch that renders it and the `parsed_from_text`
     * analytics value were all reachable only in principle.
     *
     * Hosts stage ONE-SHOT tokens only. A recognised repeat ("every Monday") is dropped rather than
     * staged, for two independent reasons: a recurring reminder is behind the free-tier gate the
     * planner's Repeat control enforces, and the draft carries a repeat as a `PendingRepeatConfig`
     * that this token cannot produce — so staging one would file a one-shot at its first occurrence
     * while the chip said "Weekly".
     */
    val parsedToken: ParsedDateToken? = null,
    val reminderPreset: ItemCreateReminderPreset? = null,
    /** Resolved epoch millis for [reminderPreset]; null when no reminder chip is active. */
    val reminderAt: Long? = null,
    val important: Boolean = false,
    val repeat: PendingRepeatConfig? = null,
    val attachments: List<PendingItemAttachment> = emptyList(),
    /**
     * The user has said "no date" OUT LOUD for this draft — the `x` on the leading chip, or Remove
     * inside the reminder sheet — so a phrase the parser recognises must stay quiet until they
     * answer differently.
     *
     * Without it the `x` on a PARSED date is a control that does not work. [parsedToken] is a live
     * derivative of [text]: the host re-parses after every keystroke, so clearing a date the text
     * still spells out lasts exactly until the next character, and the date the user just removed
     * comes back on its own. The flag is what makes the clear stick for as long as the draft lives;
     * [cleared] drops it with everything else after Send.
     *
     * It is deliberately NOT set by re-tapping an active preset chip. "I don't want THIS chip" and
     * "I want no date" are different answers, and conflating them would switch Smart-Add off
     * permanently on the Today tab, whose draft opens with a preset already applied — the only way
     * to reach the parsed date there is to clear that chip first.
     */
    val dueDismissed: Boolean = false,
) {
    /** Send is refused on a blank text even when files are staged — an untitled item is unreadable. */
    val canSend: Boolean get() = text.isNotBlank()

    val hasAttachments: Boolean get() = attachments.isNotEmpty()
}


/**
 * The characters of [TaskDraft.text] the capture dock is allowed to tint as "this is the date I
 * recognised" — or null, meaning show no highlight at all.
 *
 * ## The trap this function exists for
 * [ParsedDateToken.startIndex] / [ParsedDateToken.endIndex] do NOT address the string in the text
 * field. The parser measures against a whitespace-NORMALISED copy of the input (`trim()` plus every
 * run of whitespace collapsed to one space), so the moment the user types a leading space or a
 * double space the offsets slide, silently and by exactly as many characters as were removed. On
 * " call mum tomorrow" the token's 9..17 lands on " tomorro" — a highlight one character off,
 * covering a space and clipping a letter, with nothing anywhere to say it is wrong.
 *
 * ## Why the answer is a re-check and not a re-computation
 * The offsets could be mapped back by counting non-whitespace characters, and they could be found
 * again by searching for [ParsedDateToken.originalSubstring]. Both were rejected: the first
 * re-implements the parser's private normalisation outside the parser, where it drifts on the next
 * change to it, and the second picks the WRONG occurrence the first time someone writes "tomorrow,
 * and again tomorrow". So this only ever ANSWERS the question the indices already claim to answer —
 * does the raw text, at exactly these offsets, hold exactly the phrase the parser matched? — and
 * returns null on anything else.
 *
 * A missing highlight costs a decoration; a confidently wrong one tells the user the app read a
 * different word than it did, right before it writes a date from it. Miss quietly, never mislead.
 */
fun TaskDraft.smartAddHighlightRange(): IntRange? {
    val token = parsedToken ?: return null
    val start = token.startIndex
    val end = token.endIndex
    if (start < 0 || start >= end || end > text.length) return null
    if (text.substring(start, end) != token.originalSubstring) return null
    return start until end
}

/**
 * Toggle semantics of the reminder chips: tapping the ACTIVE one clears the reminder, tapping
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
    // Re-tap clears the CHIP, not the date question — [dueDismissed] deliberately stays where it is
    // so a recognised phrase can answer instead. See its KDoc.
    copy(reminderPreset = null, reminderAt = null)
} else {
    copy(
        reminderPreset = preset,
        reminderAt = computePresetReminderAt(preset, now, timeZone),
        dueDismissed = false,
    )
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
    // A null [at] is the ONE place the user says "no date" rather than "not this date" — the `x` on
    // the leading chip and Remove in the sheet both land here. See [TaskDraft.dueDismissed].
    //
    // The token goes WITH the flag rather than waiting for the host's debounced re-parse to drop it:
    // a chip that keeps showing a date for 200ms after the tap that removed it is a control that
    // looks broken. The flag is what keeps it gone; this is what makes it go now.
    dueDismissed = at == null,
    parsedToken = if (at == null) null else parsedToken,
)

fun TaskDraft.withImportantToggled(): TaskDraft = copy(important = !important)

/** Saving a repeat rule supersedes any one-shot reminder, mirroring [withCustomReminder]. */
fun TaskDraft.withRepeat(config: PendingRepeatConfig?): TaskDraft = copy(
    repeat = config,
    reminderAt = if (config != null) null else reminderAt,
    reminderPreset = if (config != null) null else reminderPreset,
    // Saving a rule is an answer; removing one is the same "no date" [withCustomReminder] hears.
    dueDismissed = config == null,
    parsedToken = if (config == null) null else parsedToken,
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
 * Resolves a quick-preset reminder time: +1h, tomorrow 09:00, "tonight" 18:00 (rolling to tomorrow
 * 18:00 once past 18:00 today), the coming weekend Saturday 10:00, and next week Monday 09:00.
 *
 * EVERY branch is required to land strictly in the future for every possible [now] — that is the one
 * invariant this function carries, and [availablePresets] is its executable statement. A preset that
 * resolves into the past is a chip that swallows a tap and silently does nothing, which is the exact
 * failure mode the dock's "Pick time" and "Repeat" chips are switched off to avoid today.
 *
 * All date maths goes date-first — [LocalDate.plus] whole days, then a wall-clock time, then
 * [toInstant]. Adding hours to a [LocalDateTime]'s fields instead would drift by an hour across a DST
 * transition and, worse, would do so only in some time zones and only twice a year.
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

        ItemCreateReminderPreset.TOMORROW_MORNING ->
            today.plus(1, DateTimeUnit.DAY).atHour(MORNING_HOUR, timeZone)

        ItemCreateReminderPreset.TONIGHT -> {
            val todayEvening = today.atHour(EVENING_HOUR, timeZone)
            if (todayEvening > nowMs) {
                todayEvening
            } else {
                today.plus(1, DateTimeUnit.DAY).atHour(EVENING_HOUR, timeZone)
            }
        }

        /**
         * The coming Saturday at 10:00 — and on a Saturday whose 10:00 has already gone, SUNDAY,
         * not the Saturday a week out. "Weekend" tapped on Saturday afternoon means the weekend the
         * user is standing in; pushing it seven days would be a silent six-day error, the kind that
         * is only noticed after the deadline it was meant to cover has passed.
         */
        ItemCreateReminderPreset.WEEKEND -> {
            val daysToSaturday = today.dayOfWeek.daysUntil(DayOfWeek.SATURDAY)
            if (daysToSaturday == 0) {
                val saturdayMorning = today.atHour(WEEKEND_HOUR, timeZone)
                if (saturdayMorning > nowMs) {
                    saturdayMorning
                } else {
                    today.plus(1, DateTimeUnit.DAY).atHour(WEEKEND_HOUR, timeZone)
                }
            } else {
                today.plus(daysToSaturday, DateTimeUnit.DAY).atHour(WEEKEND_HOUR, timeZone)
            }
        }

        /**
         * The coming Monday at 09:00, and on a Monday the one seven days out. Resolving "next week"
         * to the Monday the user is living through would answer "when" with "now" — the same defect
         * as a past reminder, wearing a future timestamp.
         */
        ItemCreateReminderPreset.NEXT_WEEK -> {
            val daysToMonday = today.dayOfWeek.daysUntil(DayOfWeek.MONDAY)
                .takeIf { it > 0 }
                ?: DAYS_IN_WEEK
            today.plus(daysToMonday, DateTimeUnit.DAY).atHour(MORNING_HOUR, timeZone)
        }

        ItemCreateReminderPreset.CUSTOM -> nowMs
    }
}

/**
 * The presets worth OFFERING at [now], in the order the dock shows them.
 *
 * Every preset in [presetDisplayOrder] is built to resolve into the future at any hour of any day,
 * so today this returns all five. The filter is therefore not a prediction that some chip will drop
 * out — it is the invariant made executable: should a preset ever resolve into the past, it stops
 * being rendered rather than becoming a tap that silently does nothing.
 *
 * [ItemCreateReminderPreset.CUSTOM] is never a member: it is not a preset but the RESULT of the
 * picker, and offering it as a value alongside real times would ask the user to pick a time by
 * picking "a time I will pick".
 */
fun availablePresets(
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<ItemCreateReminderPreset> {
    val nowMs = now.toEpochMilliseconds()
    return presetDisplayOrder.filter { computePresetReminderAt(it, now, timeZone) > nowMs }
}

/**
 * The order the dock shows the chips in — a fixed design decision, NOT sorted by how soon each one
 * fires ("In 1 hour" deliberately sits fourth, behind two presets that resolve later than it does).
 *
 * It lives here rather than in the dock because [availablePresets] has to return the presets already
 * in show order; a second list at the render site would be free to disagree with this one, and the
 * disagreement would be invisible until someone noticed the chips had reshuffled.
 */
val presetDisplayOrder: List<ItemCreateReminderPreset> = listOf(
    ItemCreateReminderPreset.TONIGHT,
    ItemCreateReminderPreset.TOMORROW_MORNING,
    ItemCreateReminderPreset.WEEKEND,
    ItemCreateReminderPreset.ONE_HOUR,
    ItemCreateReminderPreset.NEXT_WEEK,
)

/** Epoch millis of [hour]:00 wall-clock on this date in [timeZone]. */
private fun LocalDate.atHour(hour: Int, timeZone: TimeZone): Long =
    LocalDateTime(this, LocalTime(hour, 0)).toInstant(timeZone).toEpochMilliseconds()

/**
 * Whole days from this weekday forward to [target], 0 when they are the same day.
 *
 * Callers that must never answer "today" map the 0 themselves — see
 * [ItemCreateReminderPreset.NEXT_WEEK] — because the two presets disagree about it: landing on
 * Saturday is a valid answer for "weekend", landing on Monday is not one for "next week".
 */
private fun DayOfWeek.daysUntil(target: DayOfWeek): Int =
    (target.isoDayNumber - isoDayNumber + DAYS_IN_WEEK) % DAYS_IN_WEEK

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
 * The reminder to actually write on Send, resolved from the ACTIVE preset at this moment — or, with
 * no preset active, from the phrase Smart-Add recognised in the text ([TaskDraft.parsedToken]).
 *
 * [TaskDraft.reminderAt] is fixed when the chip is TAPPED, and the dock then stays open for as long
 * as the user types — so by Send the captured value may be wrong in either direction, and the two
 * errors need opposite treatment:
 *
 * - it has gone into the PAST (tap "Tonight" at 17:50, send at 18:10) — write it and the reminder
 *   never fires. Every [ReminderAnchor.WALL_CLOCK] preset is therefore recomputed once its moment
 *   has passed.
 * - it is still perfectly good, but the calendar has moved under it (tap "Next week" on Sunday at
 *   23:55, send at 00:05) — recomputing NOW re-reads "today is Monday", applies the
 *   on-Monday-means-next-Monday rule, and files the task seven days out. Five minutes cost a week,
 *   and nothing tells the user. A still-future wall-clock answer is therefore kept as it is.
 *
 * [ReminderAnchor.OFFSET_FROM_SEND] is the deliberate exception that stops "recompute only when
 * stale" from being applied blanket-fashion: "in 1 hour" is measured from Send by definition, so its
 * still-valid stored value is nevertheless the wrong answer.
 */
fun TaskDraft.resolveReminderAtNow(
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Long? {
    // No chip: the phrase the user typed answers instead. This one line is what connects Smart-Add
    // to the capture dock, and it is HERE rather than at the two call sites on purpose — the leading
    // chip's label and the value Send writes are both read from this function, so a date shown and a
    // date written cannot disagree. Split across two call sites, they could: the dock would render
    // "Tomorrow 09:00" over a task filed with no date at all, and report `has_due_date = false`
    // while the user was looking at the date.
    //
    // An explicit chip still wins — it is checked first, and that is the rule [TaskDraft.parsedToken]
    // states: a tap is a decision, a recognised phrase is a guess about one.
    val preset = reminderPreset ?: return parsedToken?.reminderAt
    return when (preset.anchor) {
        ReminderAnchor.PICKED -> reminderAt
        ReminderAnchor.OFFSET_FROM_SEND -> computePresetReminderAt(preset, now, timeZone)
        ReminderAnchor.WALL_CLOCK ->
            reminderAt?.takeIf { it > now.toEpochMilliseconds() }
                ?: computePresetReminderAt(preset, now, timeZone)
    }
}

/**
 * 09:00 — what "morning" means, both for "tomorrow morning" and for the Monday of "next week".
 * Deliberately ONE constant: the two presets are the same promise ("at the start of a working day")
 * pointed at different dates, and splitting them would let the app mean two different mornings.
 */
private const val MORNING_HOUR = 9

/** 18:00 — what "tonight" means, matching the reminder sheet. */
private const val EVENING_HOUR = 18

/**
 * 10:00 — what "weekend" means. Later than [MORNING_HOUR] on purpose: a Saturday reminder is not a
 * working-day reminder, and firing it at nine buys the app a notification the user resents.
 */
private const val WEEKEND_HOUR = 10

private const val DAYS_IN_WEEK = 7
