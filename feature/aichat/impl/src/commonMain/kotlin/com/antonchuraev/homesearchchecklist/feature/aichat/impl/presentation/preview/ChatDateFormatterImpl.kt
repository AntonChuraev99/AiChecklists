package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_preview_datetime
import aichecklists.core.designsystem.generated.resources.chat_preview_day_month
import aichecklists.core.designsystem.generated.resources.chat_preview_weekday_date
import aichecklists.core.designsystem.generated.resources.month_april
import aichecklists.core.designsystem.generated.resources.month_august
import aichecklists.core.designsystem.generated.resources.month_december
import aichecklists.core.designsystem.generated.resources.month_february
import aichecklists.core.designsystem.generated.resources.month_january
import aichecklists.core.designsystem.generated.resources.month_july
import aichecklists.core.designsystem.generated.resources.month_june
import aichecklists.core.designsystem.generated.resources.month_march
import aichecklists.core.designsystem.generated.resources.month_may
import aichecklists.core.designsystem.generated.resources.month_november
import aichecklists.core.designsystem.generated.resources.month_october
import aichecklists.core.designsystem.generated.resources.month_september
import aichecklists.core.designsystem.generated.resources.weekday_friday
import aichecklists.core.designsystem.generated.resources.weekday_monday
import aichecklists.core.designsystem.generated.resources.weekday_saturday
import aichecklists.core.designsystem.generated.resources.weekday_sunday
import aichecklists.core.designsystem.generated.resources.weekday_thursday
import aichecklists.core.designsystem.generated.resources.weekday_tuesday
import aichecklists.core.designsystem.generated.resources.weekday_wednesday
import com.antonchuraev.homesearchchecklist.feature.aichat.api.format.ChatDateFormatter
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Compose-Resources-backed [ChatDateFormatter].
 *
 * Day/month ORDER is locale data, not code: EN says "July 20", RU says "20 июля". Concatenating
 * the parts in Kotlin would hardcode the English order, so the order lives in the format string
 * ([Res.string.chat_preview_day_month]) and Kotlin only supplies the parts. Same reason the
 * "at"/"в" separator is a resource and not a literal.
 *
 * RU month names are therefore in the GENITIVE case ("20 июля", not "20 июль") — they only ever
 * appear after a day number.
 */
internal class ChatDateFormatterImpl : ChatDateFormatter {

    override suspend fun formatDateTime(epochMs: Long): String {
        val dt = localDateTime(epochMs)
        return getString(
            Res.string.chat_preview_datetime,
            getString(weekdayRes(dt.dayOfWeek.isoDayNumber)),
            dayMonth(dt),
            time(dt),
        )
    }

    override suspend fun formatDay(epochMs: Long): String {
        val dt = localDateTime(epochMs)
        return getString(
            Res.string.chat_preview_weekday_date,
            getString(weekdayRes(dt.dayOfWeek.isoDayNumber)),
            dayMonth(dt),
        )
    }

    private fun localDateTime(epochMs: Long): LocalDateTime =
        Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())

    /** "July 20" (EN) / "20 июля" (RU) — order comes from the format string. */
    private suspend fun dayMonth(dt: LocalDateTime): String = getString(
        Res.string.chat_preview_day_month,
        getString(monthRes(dt.month.number)),
        dt.dayOfMonth.toString(),
    )

    /** 24h "09:00". Locale-independent by project convention (the app has no 12h toggle). */
    private fun time(dt: LocalDateTime): String =
        "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"

    /** ISO weekday 1..7 → localized name (mirrors feature/home's weekdayNameKey). */
    private fun weekdayRes(isoDay: Int): StringResource = when (isoDay) {
        1 -> Res.string.weekday_monday
        2 -> Res.string.weekday_tuesday
        3 -> Res.string.weekday_wednesday
        4 -> Res.string.weekday_thursday
        5 -> Res.string.weekday_friday
        6 -> Res.string.weekday_saturday
        7 -> Res.string.weekday_sunday
        else -> Res.string.weekday_monday
    }

    /** Month number 1..12 → localized name (RU: genitive). */
    private fun monthRes(month: Int): StringResource = when (month) {
        1 -> Res.string.month_january
        2 -> Res.string.month_february
        3 -> Res.string.month_march
        4 -> Res.string.month_april
        5 -> Res.string.month_may
        6 -> Res.string.month_june
        7 -> Res.string.month_july
        8 -> Res.string.month_august
        9 -> Res.string.month_september
        10 -> Res.string.month_october
        11 -> Res.string.month_november
        12 -> Res.string.month_december
        else -> Res.string.month_january
    }
}
