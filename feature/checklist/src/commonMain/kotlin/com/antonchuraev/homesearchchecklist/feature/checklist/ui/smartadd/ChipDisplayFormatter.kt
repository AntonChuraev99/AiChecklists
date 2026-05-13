package com.antonchuraev.homesearchchecklist.feature.checklist.ui.smartadd

import androidx.compose.runtime.Composable
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.smart_add_chip_at_time
import aichecklists.core.designsystem.generated.resources.smart_add_chip_combined_weekday_repeat
import aichecklists.core.designsystem.generated.resources.smart_add_chip_day_after_tomorrow
import aichecklists.core.designsystem.generated.resources.smart_add_chip_in_days
import aichecklists.core.designsystem.generated.resources.smart_add_chip_in_hours
import aichecklists.core.designsystem.generated.resources.smart_add_chip_in_minutes
import aichecklists.core.designsystem.generated.resources.smart_add_chip_in_weeks
import aichecklists.core.designsystem.generated.resources.smart_add_chip_repeat_daily
import aichecklists.core.designsystem.generated.resources.smart_add_chip_repeat_monthly
import aichecklists.core.designsystem.generated.resources.smart_add_chip_repeat_weekdays
import aichecklists.core.designsystem.generated.resources.smart_add_chip_repeat_weekly
import aichecklists.core.designsystem.generated.resources.smart_add_chip_repeat_yearly
import aichecklists.core.designsystem.generated.resources.smart_add_chip_time_only
import aichecklists.core.designsystem.generated.resources.smart_add_chip_today
import aichecklists.core.designsystem.generated.resources.smart_add_chip_tomorrow
import aichecklists.core.designsystem.generated.resources.smart_add_chip_weekday_friday
import aichecklists.core.designsystem.generated.resources.smart_add_chip_weekday_monday
import aichecklists.core.designsystem.generated.resources.smart_add_chip_weekday_saturday
import aichecklists.core.designsystem.generated.resources.smart_add_chip_weekday_sunday
import aichecklists.core.designsystem.generated.resources.smart_add_chip_weekday_thursday
import aichecklists.core.designsystem.generated.resources.smart_add_chip_weekday_tuesday
import aichecklists.core.designsystem.generated.resources.smart_add_chip_weekday_wednesday
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.ChipDisplay
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.DayKey
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.OffsetUnit
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.RepeatKey
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.WeekdayKey
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Maps a [ChipDisplay] to a localized label string for [TokenChipPreview].
 *
 * Lives in feature:checklist (not core:designsystem) because it depends on both
 * [ChipDisplay] (from feature:checklist domain) and string resources from core:designsystem.
 * Placing it here respects the dependency graph: core modules must not depend on feature.
 *
 * Usage in Phase 3 (ViewModel wiring):
 * ```kotlin
 * val parsed: ParsedDateToken? = parser.parse(text, now, timeZone)
 * val chipLabel = parsed?.display?.let { resolveChipLabel(it) }
 * val isRepeat = parsed?.display?.containsRepeat()
 *
 * AddItemInputField(
 *     text = text,
 *     leadingPreview = if (chipLabel != null) {
 *         { TokenChipPreview(label = chipLabel, isRepeat = isRepeat) }
 *     } else null,
 * )
 * ```
 */
@Composable
fun resolveChipLabel(display: ChipDisplay): String = when (display) {
    is ChipDisplay.RelativeDay -> {
        val dayLabel = when (display.dayKey) {
            DayKey.TODAY -> stringResource(Res.string.smart_add_chip_today)
            DayKey.TOMORROW -> stringResource(Res.string.smart_add_chip_tomorrow)
            DayKey.DAY_AFTER_TOMORROW -> stringResource(Res.string.smart_add_chip_day_after_tomorrow)
        }
        if (display.timeMinutes != null) {
            val atTime = stringResource(Res.string.smart_add_chip_at_time, formatTime(display.timeMinutes))
            "$dayLabel $atTime"
        } else {
            dayLabel
        }
    }

    is ChipDisplay.Weekday -> {
        val dayLabel = resolveWeekdayLabel(display.dayKey)
        if (display.timeMinutes != null) {
            val atTime = stringResource(Res.string.smart_add_chip_at_time, formatTime(display.timeMinutes))
            "$dayLabel $atTime"
        } else {
            dayLabel
        }
    }

    is ChipDisplay.RelativeOffset -> when (display.unit) {
        OffsetUnit.MINUTES -> pluralStringResource(
            Res.plurals.smart_add_chip_in_minutes, display.amount, display.amount
        )
        OffsetUnit.HOURS -> pluralStringResource(
            Res.plurals.smart_add_chip_in_hours, display.amount, display.amount
        )
        OffsetUnit.DAYS -> pluralStringResource(
            Res.plurals.smart_add_chip_in_days, display.amount, display.amount
        )
        OffsetUnit.WEEKS -> pluralStringResource(
            Res.plurals.smart_add_chip_in_weeks, display.amount, display.amount
        )
    }

    is ChipDisplay.AbsoluteTime -> stringResource(
        Res.string.smart_add_chip_time_only,
        formatTime(display.hour * 60 + display.minute),
    )

    is ChipDisplay.Repeat -> resolveRepeatLabel(display.repeatKey)

    is ChipDisplay.Combined -> resolveCombinedLabel(display)
}

/**
 * Returns true when [ChipDisplay] contains a repeat component.
 * Used to determine which icon the chip shows (Repeat vs Notifications).
 */
fun ChipDisplay.containsRepeat(): Boolean = when (this) {
    is ChipDisplay.Repeat -> true
    is ChipDisplay.Combined -> first is ChipDisplay.Repeat || second is ChipDisplay.Repeat
    else -> false
}

// ─── Private helpers ──────────────────────────────────────────────────────────

@Composable
private fun resolveCombinedLabel(display: ChipDisplay.Combined): String {
    val first = display.first
    val second = display.second

    // Weekday (±time) + Repeat(WEEKLY) → "Every Monday at HH:MM"
    if (first is ChipDisplay.Weekday && second is ChipDisplay.Repeat) {
        val weekdayName = resolveWeekdayLabel(first.dayKey)
        val timeStr = first.timeMinutes?.let { formatTime(it) }
        return if (timeStr != null) {
            stringResource(Res.string.smart_add_chip_combined_weekday_repeat, weekdayName, timeStr)
        } else {
            // Template "По %1$s в %2$s" — drop trailing " в" when no time
            stringResource(Res.string.smart_add_chip_combined_weekday_repeat, weekdayName, "")
                .trimEnd()
        }
    }

    // Repeat + Weekday (reversed order)
    if (first is ChipDisplay.Repeat && second is ChipDisplay.Weekday) {
        val weekdayName = resolveWeekdayLabel(second.dayKey)
        val timeStr = second.timeMinutes?.let { formatTime(it) }
        return if (timeStr != null) {
            stringResource(Res.string.smart_add_chip_combined_weekday_repeat, weekdayName, timeStr)
        } else {
            stringResource(Res.string.smart_add_chip_combined_weekday_repeat, weekdayName, "")
                .trimEnd()
        }
    }

    // Repeat + AbsoluteTime → "Daily at HH:MM"
    if (first is ChipDisplay.Repeat && second is ChipDisplay.AbsoluteTime) {
        val repeatLabel = resolveRepeatLabel(first.repeatKey)
        val atTime = stringResource(
            Res.string.smart_add_chip_at_time,
            formatTime(second.hour * 60 + second.minute),
        )
        return "$repeatLabel $atTime"
    }

    // Generic fallback
    val firstLabel = resolveChipLabel(first)
    val secondLabel = resolveChipLabel(second)
    return "$firstLabel · $secondLabel"
}

@Composable
private fun resolveWeekdayLabel(key: WeekdayKey): String = when (key) {
    WeekdayKey.MONDAY -> stringResource(Res.string.smart_add_chip_weekday_monday)
    WeekdayKey.TUESDAY -> stringResource(Res.string.smart_add_chip_weekday_tuesday)
    WeekdayKey.WEDNESDAY -> stringResource(Res.string.smart_add_chip_weekday_wednesday)
    WeekdayKey.THURSDAY -> stringResource(Res.string.smart_add_chip_weekday_thursday)
    WeekdayKey.FRIDAY -> stringResource(Res.string.smart_add_chip_weekday_friday)
    WeekdayKey.SATURDAY -> stringResource(Res.string.smart_add_chip_weekday_saturday)
    WeekdayKey.SUNDAY -> stringResource(Res.string.smart_add_chip_weekday_sunday)
}

@Composable
private fun resolveRepeatLabel(key: RepeatKey): String = when (key) {
    RepeatKey.DAILY -> stringResource(Res.string.smart_add_chip_repeat_daily)
    RepeatKey.WEEKLY -> stringResource(Res.string.smart_add_chip_repeat_weekly)
    RepeatKey.MONTHLY -> stringResource(Res.string.smart_add_chip_repeat_monthly)
    RepeatKey.YEARLY -> stringResource(Res.string.smart_add_chip_repeat_yearly)
    RepeatKey.WEEKDAYS -> stringResource(Res.string.smart_add_chip_repeat_weekdays)
}

/**
 * Formats minutes-since-midnight (0..1439) as "HH:MM".
 * Pure Int math — no java.time, safe in commonMain.
 */
private fun formatTime(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}
