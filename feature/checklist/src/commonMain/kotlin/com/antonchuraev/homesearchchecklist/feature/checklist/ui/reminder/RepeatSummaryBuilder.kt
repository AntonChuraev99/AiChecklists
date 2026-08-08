package com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType

/**
 * English-only repeat summary — **legacy**, kept alive purely so the call sites outside this
 * module keep compiling while they migrate.
 *
 * It hardcodes one language, which the project forbids for anything user-facing: a Russian or Hindi
 * user reading "Every 2 weeks" is the same class of bug as the shipped "Введите название чек-листа"
 * on the English UI. The localized replacements are [resolveRepeatSummaryLabel] (`@Composable`) and
 * [getRepeatSummaryLabel] (`suspend`); both render the same [RepeatSummary] this function does, so
 * the English wording is unchanged when a call site moves over.
 *
 * Remaining callers, all of which have a localized path available:
 *  - `ChecklistDetailViewModel` (3 sites) — already inside `viewModelScope.launch`, so
 *    `getRepeatSummaryLabel(config)` drops straight in;
 *  - `InteractiveOnboardingViewModel` (1 site) — likewise;
 *  - `ChecklistDetailScreen.ItemCreateChipsRow` (1 site) — `@Composable`, so
 *    `resolveRepeatSummaryLabel(it)`.
 *
 * The reminder sheet itself no longer displays this string: [ReminderSheet] derives the text of the
 * "current repeat" card from the rule it already receives and treats the passed-in summary only as
 * the "is there anything to show" flag.
 */
@Deprecated(
    message = "Hardcodes English. Use resolveRepeatSummaryLabel (@Composable) or " +
        "getRepeatSummaryLabel (suspend) instead.",
    replaceWith = ReplaceWith("getRepeatSummaryLabel(config)"),
)
fun buildRepeatSummary(config: PendingRepeatConfig): String =
    config.toRepeatSummary().toEnglishText()

/**
 * Map a [PendingRepeatConfig] to a preset analytics name.
 * Returns "custom" when [isCustom] is true or the config does not match any known preset.
 */
fun resolvePresetName(config: PendingRepeatConfig): String {
    if (config.isCustom) return "custom"
    return when {
        config.type == RepeatType.DAILY && config.interval == 1 -> "daily"
        config.type == RepeatType.WEEKLY && config.interval == 1
            && config.weekDays == setOf(1, 2, 3, 4, 5) -> "weekdays"
        config.type == RepeatType.WEEKLY && config.interval == 1
            && config.weekDays.isEmpty() -> "weekly"
        config.type == RepeatType.WEEKLY && config.interval == 2
            && config.weekDays.isEmpty() -> "biweekly"
        config.type == RepeatType.MONTHLY && config.interval == 1 -> "monthly"
        config.type == RepeatType.MONTHLY && config.interval == 3 -> "quarterly"
        config.type == RepeatType.YEARLY && config.interval == 1 -> "yearly"
        else -> "custom"
    }
}

/**
 * The wording [buildRepeatSummary] shipped before the summary was localized, reproduced branch for
 * branch so no locale-EN user sees a single character change while the call sites migrate.
 * Do not add call sites — see the deprecation note on [buildRepeatSummary].
 */
private fun RepeatSummary.toEnglishText(): String = when (this) {
    RepeatSummary.Daily -> "Every day"
    RepeatSummary.Weekly -> "Every week"
    RepeatSummary.Biweekly -> "Every 2 weeks"
    RepeatSummary.Monthly -> "Every month"
    RepeatSummary.Quarterly -> "Every 3 months"
    RepeatSummary.Yearly -> "Every year"
    RepeatSummary.Weekdays -> "Mon–Fri"
    is RepeatSummary.OnWeekDays -> isoDays.joinToString(", ") { EnglishWeekDayAbbreviations[it - 1] }
    is RepeatSummary.EveryNDays -> "Every $days days"
    is RepeatSummary.EveryNWeeks -> "Every $weeks weeks"
    is RepeatSummary.EveryNMonths -> "Every $months months"
    is RepeatSummary.EveryNYears -> "Every $years years"
}

private val EnglishWeekDayAbbreviations =
    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
