package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RepeatType {
    @SerialName("daily")
    DAILY,

    @SerialName("weekly")
    WEEKLY,

    @SerialName("monthly")
    MONTHLY,

    @SerialName("yearly")
    YEARLY
}

@Serializable
sealed interface RepeatEndCondition {
    @Serializable
    @SerialName("never")
    data object Never : RepeatEndCondition

    @Serializable
    @SerialName("until_date")
    data class UntilDate(val dateMillis: Long) : RepeatEndCondition

    @Serializable
    @SerialName("after_count")
    data class AfterCount(val maxCount: Int) : RepeatEndCondition
}

@Serializable
data class ReminderRepeatRule(
    val type: RepeatType,
    val interval: Int = 1,
    val weekDays: Set<Int>? = null,
    val endCondition: RepeatEndCondition = RepeatEndCondition.Never,
    val resetChecks: Boolean = false
)

/**
 * Compute the next occurrence of a repeating reminder.
 *
 * @param currentTriggerMillis the epoch millis of the current/just-fired trigger
 * @param currentCount how many times this reminder has already fired
 * @param nowMillis current time in epoch millis (for fast-forward past missed occurrences)
 * @return next trigger epoch millis, or null if end condition is reached
 */
fun ReminderRepeatRule.computeNextOccurrence(
    currentTriggerMillis: Long,
    currentCount: Int,
    nowMillis: Long
): Long? {
    // 1. Check AfterCount upfront
    if (endCondition is RepeatEndCondition.AfterCount) {
        if (currentCount + 1 >= endCondition.maxCount) return null
    }

    // 2. Compute raw next time
    val tz = TimeZone.currentSystemDefault()
    val current = Instant.fromEpochMilliseconds(currentTriggerMillis)
        .toLocalDateTime(tz)
    val time = current.time

    val nextDate = when (type) {
        RepeatType.DAILY -> current.date.plus(interval, DateTimeUnit.DAY)

        RepeatType.WEEKLY -> {
            if (weekDays.isNullOrEmpty()) {
                current.date.plus(interval.toLong() * 7, DateTimeUnit.DAY)
            } else {
                findNextWeekday(current.date, weekDays, interval)
            }
        }

        RepeatType.MONTHLY -> {
            // kotlinx-datetime handles month-end clamping: Jan 31 + 1 month -> Feb 28/29
            current.date.plus(interval, DateTimeUnit.MONTH)
        }

        RepeatType.YEARLY -> {
            // kotlinx-datetime handles leap year: Feb 29 + 1 year -> Feb 28
            current.date.plus(interval, DateTimeUnit.YEAR)
        }
    }

    val nextMillis = LocalDateTime(nextDate, time)
        .toInstant(tz).toEpochMilliseconds()

    // 3. Skip past occurrences (fast-forward to future)
    val result = if (nextMillis <= nowMillis) {
        skipToFuture(nextMillis, nowMillis, this, tz, time)
    } else {
        nextMillis
    }

    // 4. Check UntilDate
    if (endCondition is RepeatEndCondition.UntilDate && result > endCondition.dateMillis) {
        return null
    }

    return result
}

/**
 * Expand a recurring rule into every occurrence within `[fromMs..toMs]`.
 *
 * Walks forward from [startTriggerMillis] using [computeNextOccurrence], collecting
 * each fire-time that falls within the requested range. Past occurrences (before
 * [fromMs]) are skipped silently; occurrences after [toMs] terminate the walk.
 *
 * Used by Calendar / Today views to render every future fire-time, not just the
 * single scheduled next occurrence stored in the DB.
 *
 * @param startTriggerMillis the currently scheduled next-fire time (from DB)
 * @param startOccurrenceCount how many times the rule has already fired (from DB)
 * @param fromMs range start (inclusive)
 * @param toMs range end (inclusive)
 * @param maxOccurrences hard cap to defend against runaway iteration (default 200)
 */
fun ReminderRepeatRule.expandInRange(
    startTriggerMillis: Long,
    startOccurrenceCount: Int,
    fromMs: Long,
    toMs: Long,
    maxOccurrences: Int = 200,
): List<Long> {
    if (toMs < fromMs) return emptyList()
    val result = mutableListOf<Long>()

    // ── Backward walk: collect past occurrences in [fromMs, startTriggerMillis) ──
    // Recurring history: e.g. daily reminder shows on every past day in the range.
    val pastOccurrences = mutableListOf<Long>()
    var prev: Long? = previousOccurrence(startTriggerMillis)
    var backIterations = 0
    while (prev != null && prev >= fromMs && backIterations < maxOccurrences) {
        pastOccurrences += prev
        val nextPrev = previousOccurrence(prev)
        if (nextPrev == null || nextPrev >= prev) break // safety
        prev = nextPrev
        backIterations++
    }
    // Result must be chronological — past occurrences first, oldest to newest.
    result += pastOccurrences.reversed()

    // ── Forward walk: from startTriggerMillis upward to toMs ──
    var current = startTriggerMillis
    var count = startOccurrenceCount
    var fwdIterations = 0
    while (fwdIterations < maxOccurrences) {
        if (current > toMs) break
        if (current >= fromMs) result += current

        val next = computeNextOccurrence(
            currentTriggerMillis = current,
            currentCount = count,
            nowMillis = current,
        ) ?: break
        if (next <= current) break

        current = next
        count++
        fwdIterations++
    }

    return result
}

/**
 * Step one period backward from [currentTriggerMillis]. Mirror of the simple
 * subtract path inside [computeNextOccurrence] — used for backward expansion
 * (showing past recurring occurrences as journal entries in Calendar).
 *
 * Returns null for weekday-based WEEKLY rules (the inverse of [findNextWeekday]
 * isn't worth the complexity for this read-only history feature) and for any
 * rule whose end-condition makes a past occurrence meaningless.
 */
private fun ReminderRepeatRule.previousOccurrence(currentTriggerMillis: Long): Long? {
    val tz = TimeZone.currentSystemDefault()
    val dt = Instant.fromEpochMilliseconds(currentTriggerMillis).toLocalDateTime(tz)
    val time = dt.time
    val prevDate = when (type) {
        RepeatType.DAILY -> dt.date.minus(interval, DateTimeUnit.DAY)
        RepeatType.WEEKLY -> {
            if (weekDays.isNullOrEmpty()) {
                dt.date.minus(interval.toLong() * 7, DateTimeUnit.DAY)
            } else {
                return null // weekday-based backward walk not supported (see doc)
            }
        }
        RepeatType.MONTHLY -> dt.date.minus(interval, DateTimeUnit.MONTH)
        RepeatType.YEARLY -> dt.date.minus(interval, DateTimeUnit.YEAR)
    }
    return LocalDateTime(prevDate, time).toInstant(tz).toEpochMilliseconds()
}

/**
 * Find the next matching weekday from a set of ISO day numbers (1=Mon..7=Sun).
 * If no matching day remains in the current week, jump forward by [intervalWeeks] weeks.
 */
internal fun findNextWeekday(
    fromDate: kotlinx.datetime.LocalDate,
    weekDays: Set<Int>,
    intervalWeeks: Int
): kotlinx.datetime.LocalDate {
    val currentDayNumber = fromDate.dayOfWeek.isoDayNumber
    val sortedDays = weekDays.sorted()

    // Try remaining days in current week (strictly after current day)
    val nextInWeek = sortedDays.firstOrNull { it > currentDayNumber }
    if (nextInWeek != null) {
        return fromDate.plus(nextInWeek - currentDayNumber, DateTimeUnit.DAY)
    }

    // Jump to first matching day of next interval-week
    val daysToNextMonday = 8 - currentDayNumber
    val nextWeekMonday = fromDate.plus(
        daysToNextMonday + (intervalWeeks - 1) * 7,
        DateTimeUnit.DAY
    )
    val firstDay = sortedDays.first()
    return nextWeekMonday.plus(firstDay - 1, DateTimeUnit.DAY)
}

/**
 * O(1) fast-forward for simple intervals (DAILY, WEEKLY without weekdays).
 * For MONTHLY and weekday-based WEEKLY, uses bounded iteration.
 */
private fun skipToFuture(
    firstNextMillis: Long,
    nowMillis: Long,
    rule: ReminderRepeatRule,
    tz: TimeZone,
    time: kotlinx.datetime.LocalTime
): Long {
    val intervalMillis = when (rule.type) {
        RepeatType.DAILY -> rule.interval.toLong() * 24 * 60 * 60 * 1000L

        RepeatType.WEEKLY -> {
            if (rule.weekDays.isNullOrEmpty()) {
                rule.interval.toLong() * 7 * 24 * 60 * 60 * 1000L
            } else {
                return skipWeekdaysToFuture(firstNextMillis, nowMillis, rule, tz, time)
            }
        }

        RepeatType.MONTHLY -> {
            return skipMonthlyToFuture(firstNextMillis, nowMillis, rule, tz, time)
        }

        RepeatType.YEARLY -> {
            return skipYearlyToFuture(firstNextMillis, nowMillis, rule, tz, time)
        }
    }

    require(intervalMillis > 0) { "intervalMillis must be positive" }
    val elapsed = nowMillis - firstNextMillis
    if (elapsed < 0) return firstNextMillis
    val missedCycles = elapsed / intervalMillis
    return firstNextMillis + (missedCycles + 1) * intervalMillis
}

/**
 * Skip weekday-based weekly reminders to the future.
 * Bounded iteration — max 7 steps per week cycle.
 */
private fun skipWeekdaysToFuture(
    firstNextMillis: Long,
    nowMillis: Long,
    rule: ReminderRepeatRule,
    tz: TimeZone,
    time: kotlinx.datetime.LocalTime
): Long {
    val weekDays = rule.weekDays ?: return firstNextMillis
    var candidate = Instant.fromEpochMilliseconds(firstNextMillis)
        .toLocalDateTime(tz).date

    // Safety bound: max 400 iterations (covers ~1 year of weekly intervals)
    repeat(400) {
        val nextDate = findNextWeekday(candidate, weekDays, rule.interval)
        val nextMillis = LocalDateTime(nextDate, time)
            .toInstant(tz).toEpochMilliseconds()
        if (nextMillis > nowMillis) return nextMillis
        candidate = nextDate
    }

    // Fallback: return the last computed date (should not happen in practice)
    return LocalDateTime(candidate, time)
        .toInstant(tz).toEpochMilliseconds()
}

/**
 * Skip monthly reminders to the future.
 * Must iterate because months have variable lengths.
 * Bounded: max 120 iterations (10 years).
 */
private fun skipMonthlyToFuture(
    firstNextMillis: Long,
    nowMillis: Long,
    rule: ReminderRepeatRule,
    tz: TimeZone,
    time: kotlinx.datetime.LocalTime
): Long {
    var candidate = Instant.fromEpochMilliseconds(firstNextMillis)
        .toLocalDateTime(tz).date

    repeat(120) {
        val nextDate = candidate.plus(rule.interval, DateTimeUnit.MONTH)
        val nextMillis = LocalDateTime(nextDate, time)
            .toInstant(tz).toEpochMilliseconds()
        if (nextMillis > nowMillis) return nextMillis
        candidate = nextDate
    }

    return LocalDateTime(candidate, time)
        .toInstant(tz).toEpochMilliseconds()
}

/**
 * Skip yearly reminders to the future.
 * Must iterate because leap years have variable day counts.
 * Bounded: max 100 iterations (100 years).
 */
private fun skipYearlyToFuture(
    firstNextMillis: Long,
    nowMillis: Long,
    rule: ReminderRepeatRule,
    tz: TimeZone,
    time: kotlinx.datetime.LocalTime
): Long {
    var candidate = Instant.fromEpochMilliseconds(firstNextMillis)
        .toLocalDateTime(tz).date

    repeat(100) {
        val nextDate = candidate.plus(rule.interval, DateTimeUnit.YEAR)
        val nextMillis = LocalDateTime(nextDate, time)
            .toInstant(tz).toEpochMilliseconds()
        if (nextMillis > nowMillis) return nextMillis
        candidate = nextDate
    }

    return LocalDateTime(candidate, time)
        .toInstant(tz).toEpochMilliseconds()
}
