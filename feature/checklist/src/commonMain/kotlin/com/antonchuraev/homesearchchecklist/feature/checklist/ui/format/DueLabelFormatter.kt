package com.antonchuraev.homesearchchecklist.feature.checklist.ui.format

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.due_absolute_date_time
import aichecklists.core.designsystem.generated.resources.due_date
import aichecklists.core.designsystem.generated.resources.due_missed
import aichecklists.core.designsystem.generated.resources.due_overdue_day
import aichecklists.core.designsystem.generated.resources.due_overdue_time
import aichecklists.core.designsystem.generated.resources.due_repeat_daily
import aichecklists.core.designsystem.generated.resources.due_repeat_monthly
import aichecklists.core.designsystem.generated.resources.due_repeat_weekly
import aichecklists.core.designsystem.generated.resources.due_repeat_weekly_any
import aichecklists.core.designsystem.generated.resources.due_repeat_weekly_named
import aichecklists.core.designsystem.generated.resources.due_repeat_yearly
import aichecklists.core.designsystem.generated.resources.due_someday
import aichecklists.core.designsystem.generated.resources.due_today
import aichecklists.core.designsystem.generated.resources.due_tomorrow
import aichecklists.core.designsystem.generated.resources.due_weekday
import aichecklists.core.designsystem.generated.resources.month_abbr_apr
import aichecklists.core.designsystem.generated.resources.month_abbr_aug
import aichecklists.core.designsystem.generated.resources.month_abbr_dec
import aichecklists.core.designsystem.generated.resources.month_abbr_feb
import aichecklists.core.designsystem.generated.resources.month_abbr_jan
import aichecklists.core.designsystem.generated.resources.month_abbr_jul
import aichecklists.core.designsystem.generated.resources.month_abbr_jun
import aichecklists.core.designsystem.generated.resources.month_abbr_mar
import aichecklists.core.designsystem.generated.resources.month_abbr_may
import aichecklists.core.designsystem.generated.resources.month_abbr_nov
import aichecklists.core.designsystem.generated.resources.month_abbr_oct
import aichecklists.core.designsystem.generated.resources.month_abbr_sep
import aichecklists.core.designsystem.generated.resources.reminder_weekday_abbr_fri
import aichecklists.core.designsystem.generated.resources.reminder_weekday_abbr_mon
import aichecklists.core.designsystem.generated.resources.reminder_weekday_abbr_sat
import aichecklists.core.designsystem.generated.resources.reminder_weekday_abbr_sun
import aichecklists.core.designsystem.generated.resources.reminder_weekday_abbr_thu
import aichecklists.core.designsystem.generated.resources.reminder_weekday_abbr_tue
import aichecklists.core.designsystem.generated.resources.reminder_weekday_abbr_wed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiScheduleState
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The one place that turns a task's reminder fields into the words a user reads.
 *
 * ## Why this exists
 * The checklist detail screen grew its own formatter with the labels **"Daily"**, **"Weekly"**,
 * **"Mon"** and **"Missed (May 5)"** written as Kotlin string literals — which means one language,
 * shipped to every locale. The new due chip needs the same information in a shorter vocabulary, and
 * a second copy of that logic would be a second copy of the defect: two functions, two clocks, two
 * sets of month names, drifting apart within a release. So both call sites resolve through here.
 *
 * ## Two halves, and why they are split
 * [resolveDueLabel] is **pure**: domain fields plus a clock in, a [DueLabelSpec] out — which colour
 * state applies and which shape of label goes with it. No strings, no Compose, so every branch is
 * unit-testable on any target.
 *
 * [label] is the `@Composable` half and does nothing but look the chosen shape up in
 * `strings.xml`. That split is also the answer to *"`stringResource` only works inside a
 * `@Composable`"*: the decision does not need Compose, and the only thing that does — the string —
 * is produced where Compose is already running. Both current call sites (`TaskRow`, the detail
 * screen's `formatItemReminderLabel`) are composables, so no suspend twin exists yet; if a
 * ViewModel or a notification ever needs the same words, it maps the same [DueLabelSpec] with
 * `getString` and the branch logic above stays untouched.
 *
 * ## Day/month order is locale data
 * "September 14" in English is "14 сентября" in Russian. Concatenating the parts in Kotlin would
 * hardcode the English order, so the parts go into the format string and the ORDER lives in the
 * translation (`due_date` is `%1$s %2$s` in `values/` and `%2$s %1$s` in `values-ru/`) — the same
 * rule the chat date formatter already follows.
 */
enum class DueLabelStyle {

    /**
     * Short and relative — what the due chip on a task row shows: "Today 18:00", "Overdue Sep 14".
     *
     * Relative words are what makes a chip readable at a glance in a list: "Tomorrow" answers the
     * question the user actually has, where "May 6" makes them do the arithmetic.
     */
    Relative,

    /**
     * Absolute — the vocabulary the checklist detail screen has shown since it was written:
     * "May 5, 18:00", "Missed (May 5)", "Weekly Mon 18:00".
     *
     * Kept byte-for-byte (in English) on purpose. Moving that screen to relative wording would be a
     * silent redesign of a screen this task is not redesigning; the only thing that changes there is
     * that the words are now translatable.
     */
    Absolute,
}

/**
 * Which label a task gets, as a shape plus its already-computed parts — no strings yet.
 *
 * Each variant maps 1:1 onto one string resource. The parts are primitives (a zero-padded "HH:mm",
 * an ISO weekday number, a month number) rather than finished text precisely because the finished
 * text is locale-dependent and this type is not.
 */
@Immutable
sealed interface DueLabelForm {

    /** Parked with no concrete date — "Someday". Drawn with the chip's dashed outline. */
    data object Someday : DueLabelForm

    /** Due later today — "Today 18:00". */
    data class Today(val time: String) : DueLabelForm

    /** Due tomorrow — "Tomorrow 09:00". */
    data class Tomorrow(val time: String) : DueLabelForm

    /** Due in 2–6 days, still nameable by weekday — "Fri 18:00". */
    data class ThisWeek(val isoDayOfWeek: Int, val time: String) : DueLabelForm

    /** Due a week or more out — "Sep 14". The time stops being the interesting part. */
    data class Date(val monthNumber: Int, val dayOfMonth: Int) : DueLabelForm

    /** Missed earlier today — "Overdue 09:00". */
    data class OverdueTime(val time: String) : DueLabelForm

    /** Missed on an earlier day — "Overdue Sep 14". */
    data class OverdueDate(val monthNumber: Int, val dayOfMonth: Int) : DueLabelForm

    /** [DueLabelStyle.Absolute] one-shot in the future — "May 5, 18:00". */
    data class DateTime(
        val monthNumber: Int,
        val dayOfMonth: Int,
        val time: String,
    ) : DueLabelForm

    /** [DueLabelStyle.Absolute] one-shot in the past — "Missed (May 5)", date only. */
    data class Missed(val monthNumber: Int, val dayOfMonth: Int) : DueLabelForm

    /** Repeats every day — "Daily 09:00". Identical in both styles, so one form and one key. */
    data class RepeatDaily(val time: String) : DueLabelForm

    /** Repeats on named days, chip vocabulary — "Mon,Wed 18:00". */
    data class RepeatWeekly(val isoDaysOfWeek: List<Int>, val time: String) : DueLabelForm

    /** Repeats on named days, detail-screen vocabulary — "Weekly Mon,Wed 18:00". */
    data class RepeatWeeklyNamed(val isoDaysOfWeek: List<Int>, val time: String) : DueLabelForm

    /**
     * Repeats weekly with no day selection — "Weekly 18:00".
     *
     * Shared by both styles: with nothing to list, the day slot in the other two forms would render
     * as a stray leading space.
     */
    data class RepeatWeeklyAny(val time: String) : DueLabelForm

    /** Repeats monthly — "Monthly 09:00". */
    data class RepeatMonthly(val time: String) : DueLabelForm

    /** Repeats yearly — "Yearly 09:00". */
    data class RepeatYearly(val time: String) : DueLabelForm
}

/**
 * A due label: how it should look ([state]) and what it should say ([form]).
 *
 * The two travel together because they are decided by the same comparison against the clock, and
 * splitting them would let a call site paint an overdue colour on a "Tomorrow" label.
 */
@Immutable
data class DueLabelSpec(val state: GistiScheduleState, val form: DueLabelForm) {

    companion object {

        /**
         * The parked state.
         *
         * Exposed as a constant rather than derived, because **no domain field carries it yet** —
         * "someday" is an explicit user choice that arrives with the task-source model, and
         * inferring it from "has no date" would be wrong: a task with no date renders no chip at
         * all (zero pixels, zero blame), which is a different thing from one deliberately parked.
         */
        val Someday = DueLabelSpec(GistiScheduleState.Someday, DueLabelForm.Someday)
    }
}

/**
 * Decides which due label a task gets. Pure — no Compose, no ambient clock, no ambient timezone.
 *
 * Returns `null` when there is nothing to say: no one-shot reminder and no repeat rule. That is not
 * an error case and not an empty label — the call site draws **no chip**, which is the cheapest
 * neutral state the design has.
 *
 * A repeat rule outranks a one-shot timestamp when both are present, matching what the detail screen
 * has always shown: the recurring rule is what describes the task, the timestamp is bookkeeping.
 *
 * @param reminderAt one-shot trigger, epoch millis.
 * @param repeatRule recurring schedule, if any.
 * @param repeatTimeOfDayMinutes minutes since midnight the repeat fires at; `null` reads as 00:00,
 *   matching the existing detail-screen behaviour.
 * @param repeatNextAt next occurrence of [repeatRule]. Only the COLOUR comes from it — the label of
 *   a repeating task is its rule. `null` (exhausted or not yet computed) stays neutral rather than
 *   overdue: nothing has been missed, the series simply has no next date.
 * @param nowMillis the clock, passed in so the decision is reproducible in tests.
 * @param style which vocabulary to use — see [DueLabelStyle].
 * @param timeZone zone the dates are interpreted in. Explicit so a test does not change meaning
 *   with the machine it runs on.
 */
fun resolveDueLabel(
    reminderAt: Long?,
    repeatRule: ReminderRepeatRule?,
    repeatTimeOfDayMinutes: Int? = null,
    repeatNextAt: Long? = null,
    nowMillis: Long,
    style: DueLabelStyle = DueLabelStyle.Relative,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): DueLabelSpec? {
    if (repeatRule != null) {
        return DueLabelSpec(
            state = repeatNextAt?.let { scheduleStateOf(it, nowMillis, timeZone) }
                ?: GistiScheduleState.Later,
            form = repeatForm(repeatRule, repeatTimeOfDayMinutes, style),
        )
    }

    val at = reminderAt ?: return null
    val due = at.toLocalDateTime(timeZone)
    val now = nowMillis.toLocalDateTime(timeZone)
    val time = due.timeOfDay()
    val isPast = at < nowMillis
    val daysAway = due.date.toEpochDays().toLong() - now.date.toEpochDays().toLong()

    val form = when {
        style == DueLabelStyle.Absolute && isPast ->
            DueLabelForm.Missed(due.month.number, due.day)

        style == DueLabelStyle.Absolute ->
            DueLabelForm.DateTime(due.month.number, due.day, time)

        isPast && daysAway < 0L -> DueLabelForm.OverdueDate(due.month.number, due.day)
        isPast -> DueLabelForm.OverdueTime(time)
        daysAway == 0L -> DueLabelForm.Today(time)
        daysAway == 1L -> DueLabelForm.Tomorrow(time)
        // Up to six days out a weekday name is unambiguous; on the seventh it collides with today's
        // own name ("Tue" could be tomorrow-week or a year from now), so the date takes over.
        daysAway <= WEEKDAY_HORIZON_DAYS -> DueLabelForm.ThisWeek(due.dayOfWeek.isoDayNumber, time)
        else -> DueLabelForm.Date(due.month.number, due.day)
    }

    return DueLabelSpec(state = scheduleStateOf(at, nowMillis, timeZone), form = form)
}

/**
 * [resolveDueLabel] over a domain item — the form both call sites actually use.
 *
 * Kept as an extension rather than an overload so the pure function above stays free of the domain
 * model and can be exercised field by field.
 */
fun ChecklistFillItem.dueLabelSpec(
    nowMillis: Long,
    style: DueLabelStyle = DueLabelStyle.Relative,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): DueLabelSpec? = resolveDueLabel(
    reminderAt = reminderAt,
    repeatRule = repeatRule,
    repeatTimeOfDayMinutes = repeatTimeOfDayMinutes,
    repeatNextAt = repeatNextAt,
    nowMillis = nowMillis,
    style = style,
    timeZone = timeZone,
)

/** Localized text for this label. The only place a due label becomes a string. */
@Composable
fun DueLabelSpec.label(): String = form.label()

/** Localized text for a bare form, when the caller already knows the colour it wants. */
@Composable
fun DueLabelForm.label(): String = when (this) {
    DueLabelForm.Someday -> stringResource(Res.string.due_someday)
    is DueLabelForm.Today -> stringResource(Res.string.due_today, time)
    is DueLabelForm.Tomorrow -> stringResource(Res.string.due_tomorrow, time)
    is DueLabelForm.ThisWeek ->
        stringResource(Res.string.due_weekday, stringResource(weekdayAbbr(isoDayOfWeek)), time)

    is DueLabelForm.Date -> dateText(monthNumber, dayOfMonth)
    is DueLabelForm.OverdueTime -> stringResource(Res.string.due_overdue_time, time)
    is DueLabelForm.OverdueDate ->
        stringResource(Res.string.due_overdue_day, dateText(monthNumber, dayOfMonth))

    is DueLabelForm.DateTime -> stringResource(
        Res.string.due_absolute_date_time,
        dateText(monthNumber, dayOfMonth),
        time,
    )

    is DueLabelForm.Missed ->
        stringResource(Res.string.due_missed, dateText(monthNumber, dayOfMonth))

    is DueLabelForm.RepeatDaily -> stringResource(Res.string.due_repeat_daily, time)
    is DueLabelForm.RepeatWeekly ->
        stringResource(Res.string.due_repeat_weekly, weekdayList(isoDaysOfWeek), time)

    is DueLabelForm.RepeatWeeklyNamed ->
        stringResource(Res.string.due_repeat_weekly_named, weekdayList(isoDaysOfWeek), time)

    is DueLabelForm.RepeatWeeklyAny -> stringResource(Res.string.due_repeat_weekly_any, time)
    is DueLabelForm.RepeatMonthly -> stringResource(Res.string.due_repeat_monthly, time)
    is DueLabelForm.RepeatYearly -> stringResource(Res.string.due_repeat_yearly, time)
}

/** Last day out that still gets a weekday name instead of a date. */
private const val WEEKDAY_HORIZON_DAYS = 6L

/** Separator between weekdays in a repeat label. Punctuation, identical in en / ru / hi. */
private const val WEEKDAY_SEPARATOR = ","

/** "Sep 14" / "14 сент." — the order comes from the translation, not from this concatenation. */
@Composable
private fun dateText(monthNumber: Int, dayOfMonth: Int): String = stringResource(
    Res.string.due_date,
    stringResource(monthAbbr(monthNumber)),
    dayOfMonth.toString(),
)

/**
 * "Mon,Wed" — ISO order, so the list reads the same way the week does.
 *
 * The names are resolved first and joined afterwards: `joinToString` is not an inline function, so
 * its `transform` lambda cannot host a `@Composable` call, while `map` can.
 */
@Composable
private fun weekdayList(isoDaysOfWeek: List<Int>): String =
    isoDaysOfWeek.map { stringResource(weekdayAbbr(it)) }.joinToString(WEEKDAY_SEPARATOR)

/**
 * How the label's colour is chosen. Deliberately coarser than the label itself: "Fri 18:00" and
 * "Sep 14" are different words for the same neutral state.
 */
private fun scheduleStateOf(
    atMillis: Long,
    nowMillis: Long,
    timeZone: TimeZone,
): GistiScheduleState = when {
    atMillis < nowMillis -> GistiScheduleState.Overdue
    // Today and tomorrow share the accent colour — "soon enough that it is your problem now".
    atMillis.toLocalDateTime(timeZone).date.toEpochDays().toLong() -
        nowMillis.toLocalDateTime(timeZone).date.toEpochDays().toLong() <= 1L ->
        GistiScheduleState.Active

    else -> GistiScheduleState.Later
}

/** The label of a repeating task is its rule; only the colour comes from the next occurrence. */
private fun repeatForm(
    rule: ReminderRepeatRule,
    repeatTimeOfDayMinutes: Int?,
    style: DueLabelStyle,
): DueLabelForm {
    val time = timeOfDay(repeatTimeOfDayMinutes ?: 0)
    val days = rule.weekDays?.sorted().orEmpty()

    return when (rule.type) {
        RepeatType.DAILY -> DueLabelForm.RepeatDaily(time)
        RepeatType.WEEKLY -> when {
            days.isEmpty() -> DueLabelForm.RepeatWeeklyAny(time)
            style == DueLabelStyle.Absolute -> DueLabelForm.RepeatWeeklyNamed(days, time)
            else -> DueLabelForm.RepeatWeekly(days, time)
        }

        RepeatType.MONTHLY -> DueLabelForm.RepeatMonthly(time)
        RepeatType.YEARLY -> DueLabelForm.RepeatYearly(time)
    }
}

private fun Long.toLocalDateTime(timeZone: TimeZone): LocalDateTime =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(timeZone)

/**
 * 24h "09:00", zero-padded.
 *
 * Locale-independent by project convention (the app has no 12h toggle), and the padding is load
 * bearing: the chip renders with tabular figures so a ticking clock does not resize it, which only
 * works while every time is the same number of glyphs.
 */
private fun LocalDateTime.timeOfDay(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** Same shape as [timeOfDay], from minutes-since-midnight. */
private fun timeOfDay(minutesSinceMidnight: Int): String {
    val hours = minutesSinceMidnight / MINUTES_PER_HOUR
    val minutes = minutesSinceMidnight % MINUTES_PER_HOUR
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
}

private const val MINUTES_PER_HOUR = 60

/** ISO weekday 1..7 → the abbreviated name already translated for the repeat summary. */
private fun weekdayAbbr(isoDay: Int): StringResource = when (isoDay) {
    1 -> Res.string.reminder_weekday_abbr_mon
    2 -> Res.string.reminder_weekday_abbr_tue
    3 -> Res.string.reminder_weekday_abbr_wed
    4 -> Res.string.reminder_weekday_abbr_thu
    5 -> Res.string.reminder_weekday_abbr_fri
    6 -> Res.string.reminder_weekday_abbr_sat
    else -> Res.string.reminder_weekday_abbr_sun
}

/**
 * Month 1..12 → its abbreviated name.
 *
 * Abbreviated rather than the existing full `month_*` set: a due chip is capped at 140dp and
 * "September 14" would truncate, while the detail screen has always shown three letters ("May 5")
 * and must keep doing so. RU forms are genitive for the same reason the full names are — they only
 * ever appear next to a day number.
 */
private fun monthAbbr(month: Int): StringResource = when (month) {
    1 -> Res.string.month_abbr_jan
    2 -> Res.string.month_abbr_feb
    3 -> Res.string.month_abbr_mar
    4 -> Res.string.month_abbr_apr
    5 -> Res.string.month_abbr_may
    6 -> Res.string.month_abbr_jun
    7 -> Res.string.month_abbr_jul
    8 -> Res.string.month_abbr_aug
    9 -> Res.string.month_abbr_sep
    10 -> Res.string.month_abbr_oct
    11 -> Res.string.month_abbr_nov
    else -> Res.string.month_abbr_dec
}
