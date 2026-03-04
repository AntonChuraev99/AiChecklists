package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
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
    MONTHLY
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
