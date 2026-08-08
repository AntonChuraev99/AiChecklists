package com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.reminder_repeat_summary_biweekly
import aichecklists.core.designsystem.generated.resources.reminder_repeat_summary_daily
import aichecklists.core.designsystem.generated.resources.reminder_repeat_summary_every_n_days
import aichecklists.core.designsystem.generated.resources.reminder_repeat_summary_every_n_months
import aichecklists.core.designsystem.generated.resources.reminder_repeat_summary_every_n_weeks
import aichecklists.core.designsystem.generated.resources.reminder_repeat_summary_every_n_years
import aichecklists.core.designsystem.generated.resources.reminder_repeat_summary_monthly
import aichecklists.core.designsystem.generated.resources.reminder_repeat_summary_quarterly
import aichecklists.core.designsystem.generated.resources.reminder_repeat_summary_weekdays
import aichecklists.core.designsystem.generated.resources.reminder_repeat_summary_weekly
import aichecklists.core.designsystem.generated.resources.reminder_repeat_summary_yearly
import aichecklists.core.designsystem.generated.resources.reminder_weekday_abbr_fri
import aichecklists.core.designsystem.generated.resources.reminder_weekday_abbr_mon
import aichecklists.core.designsystem.generated.resources.reminder_weekday_abbr_sat
import aichecklists.core.designsystem.generated.resources.reminder_weekday_abbr_sun
import aichecklists.core.designsystem.generated.resources.reminder_weekday_abbr_thu
import aichecklists.core.designsystem.generated.resources.reminder_weekday_abbr_tue
import aichecklists.core.designsystem.generated.resources.reminder_weekday_abbr_wed
import androidx.compose.runtime.Composable
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Localized, human-readable phrasing of a repeat schedule ("Every day", "Mon–Fri", "Mon, Wed, Fri",
 * "Every 3 weeks").
 *
 * Lives in `feature:checklist` (not `core:designsystem`) for the same reason as `resolveChipLabel`:
 * it needs both the domain model from this module and the strings from `core:designsystem`, and a
 * core module must never depend on a feature module.
 *
 * Two flavours of the same mapping exist because Compose Resources exposes two access paths and
 * neither covers both call-site kinds:
 *  - [resolveRepeatSummaryLabel] — `@Composable`, for screens and sheets;
 *  - [getRepeatSummaryLabel] — `suspend`, for ViewModels that store the text in their state.
 *
 * @see RepeatSummary for the locale-free decision layer these functions render.
 */
@Composable
fun resolveRepeatSummaryLabel(summary: RepeatSummary): String = when (summary) {
    RepeatSummary.Daily -> stringResource(Res.string.reminder_repeat_summary_daily)
    RepeatSummary.Weekly -> stringResource(Res.string.reminder_repeat_summary_weekly)
    RepeatSummary.Biweekly -> stringResource(Res.string.reminder_repeat_summary_biweekly)
    RepeatSummary.Monthly -> stringResource(Res.string.reminder_repeat_summary_monthly)
    RepeatSummary.Quarterly -> stringResource(Res.string.reminder_repeat_summary_quarterly)
    RepeatSummary.Yearly -> stringResource(Res.string.reminder_repeat_summary_yearly)
    RepeatSummary.Weekdays -> stringResource(Res.string.reminder_repeat_summary_weekdays)

    is RepeatSummary.OnWeekDays -> {
        // All seven names are resolved unconditionally: resolving only the selected ones would make
        // the number of composable calls depend on the selection and change it between
        // recompositions, which is exactly the shape Compose cannot key correctly on its own.
        val names = weekDayAbbreviations()
        summary.isoDays.joinToString(WeekDaySeparator) { names[it - 1] }
    }

    is RepeatSummary.EveryNDays -> pluralStringResource(
        Res.plurals.reminder_repeat_summary_every_n_days, summary.days, summary.days
    )
    is RepeatSummary.EveryNWeeks -> pluralStringResource(
        Res.plurals.reminder_repeat_summary_every_n_weeks, summary.weeks, summary.weeks
    )
    is RepeatSummary.EveryNMonths -> pluralStringResource(
        Res.plurals.reminder_repeat_summary_every_n_months, summary.months, summary.months
    )
    is RepeatSummary.EveryNYears -> pluralStringResource(
        Res.plurals.reminder_repeat_summary_every_n_years, summary.years, summary.years
    )
}

/** Convenience overload for the config the reminder sheet is editing. */
@Composable
fun resolveRepeatSummaryLabel(config: PendingRepeatConfig): String =
    resolveRepeatSummaryLabel(config.toRepeatSummary())

/** Convenience overload for a persisted rule (checklist-level or per-item). */
@Composable
fun resolveRepeatSummaryLabel(rule: ReminderRepeatRule): String =
    resolveRepeatSummaryLabel(rule.toRepeatSummary())

/**
 * `suspend` twin of [resolveRepeatSummaryLabel], for ViewModels.
 *
 * Call it inside the coroutine that is already updating the state — `getString` is suspending, so a
 * non-coroutine call site has to be wrapped in `viewModelScope.launch { }` first.
 */
suspend fun getRepeatSummaryLabel(summary: RepeatSummary): String = when (summary) {
    RepeatSummary.Daily -> getString(Res.string.reminder_repeat_summary_daily)
    RepeatSummary.Weekly -> getString(Res.string.reminder_repeat_summary_weekly)
    RepeatSummary.Biweekly -> getString(Res.string.reminder_repeat_summary_biweekly)
    RepeatSummary.Monthly -> getString(Res.string.reminder_repeat_summary_monthly)
    RepeatSummary.Quarterly -> getString(Res.string.reminder_repeat_summary_quarterly)
    RepeatSummary.Yearly -> getString(Res.string.reminder_repeat_summary_yearly)
    RepeatSummary.Weekdays -> getString(Res.string.reminder_repeat_summary_weekdays)

    is RepeatSummary.OnWeekDays -> summary.isoDays
        .map { getString(weekDayAbbreviationResource(it)) }
        .joinToString(WeekDaySeparator)

    is RepeatSummary.EveryNDays -> getPluralString(
        Res.plurals.reminder_repeat_summary_every_n_days, summary.days, summary.days
    )
    is RepeatSummary.EveryNWeeks -> getPluralString(
        Res.plurals.reminder_repeat_summary_every_n_weeks, summary.weeks, summary.weeks
    )
    is RepeatSummary.EveryNMonths -> getPluralString(
        Res.plurals.reminder_repeat_summary_every_n_months, summary.months, summary.months
    )
    is RepeatSummary.EveryNYears -> getPluralString(
        Res.plurals.reminder_repeat_summary_every_n_years, summary.years, summary.years
    )
}

/** Convenience overload for the config the reminder sheet is editing. */
suspend fun getRepeatSummaryLabel(config: PendingRepeatConfig): String =
    getRepeatSummaryLabel(config.toRepeatSummary())

/** Convenience overload for a persisted rule (checklist-level or per-item). */
suspend fun getRepeatSummaryLabel(rule: ReminderRepeatRule): String =
    getRepeatSummaryLabel(rule.toRepeatSummary())

// ─── Private helpers ──────────────────────────────────────────────────────────

/**
 * Separator between the days of an explicit weekday selection.
 *
 * Punctuation, not copy: EN, RU and HI all list with a comma plus a space. Kept as a literal for the
 * same reason regexes and log tags are — translating it would invite a trailing-whitespace bug in
 * XML for no gain in any of the three shipped locales.
 */
private const val WeekDaySeparator = ", "

/** [isoDay] is 1 = Monday .. 7 = Sunday; [RepeatSummary.OnWeekDays] guarantees that range. */
private fun weekDayAbbreviationResource(isoDay: Int): StringResource = when (isoDay) {
    1 -> Res.string.reminder_weekday_abbr_mon
    2 -> Res.string.reminder_weekday_abbr_tue
    3 -> Res.string.reminder_weekday_abbr_wed
    4 -> Res.string.reminder_weekday_abbr_thu
    5 -> Res.string.reminder_weekday_abbr_fri
    6 -> Res.string.reminder_weekday_abbr_sat
    else -> Res.string.reminder_weekday_abbr_sun
}

@Composable
private fun weekDayAbbreviations(): List<String> = listOf(
    stringResource(Res.string.reminder_weekday_abbr_mon),
    stringResource(Res.string.reminder_weekday_abbr_tue),
    stringResource(Res.string.reminder_weekday_abbr_wed),
    stringResource(Res.string.reminder_weekday_abbr_thu),
    stringResource(Res.string.reminder_weekday_abbr_fri),
    stringResource(Res.string.reminder_weekday_abbr_sat),
    stringResource(Res.string.reminder_weekday_abbr_sun),
)
