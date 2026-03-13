package com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType

fun buildRepeatSummary(config: PendingRepeatConfig): String {
    return when (config.type) {
        RepeatType.DAILY -> if (config.interval == 1) "Every day" else "Every ${config.interval} days"
        RepeatType.WEEKLY -> {
            if (config.weekDays.isNotEmpty()) {
                val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                val selected = config.weekDays.sorted().map { dayNames[it - 1] }
                if (selected == listOf("Mon", "Tue", "Wed", "Thu", "Fri")) {
                    "Mon–Fri"
                } else {
                    selected.joinToString(", ")
                }
            } else if (config.interval == 1) {
                "Every week"
            } else {
                "Every ${config.interval} weeks"
            }
        }
        RepeatType.MONTHLY -> if (config.interval == 1) "Every month" else "Every ${config.interval} months"
        RepeatType.YEARLY -> if (config.interval == 1) "Every year" else "Every ${config.interval} years"
    }
}

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
