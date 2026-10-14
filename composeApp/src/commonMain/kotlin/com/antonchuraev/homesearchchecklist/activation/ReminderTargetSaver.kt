package com.antonchuraev.homesearchchecklist.activation

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.antonchuraev.homesearchchecklist.core.common.api.ReminderTarget

private const val KIND_CHECKLIST = "checklist"
private const val KIND_ITEM = "item"

/** Bundle-safe form of a [ReminderTarget] (strings only). Pure — see [reminderTargetFromSaveable]. */
fun ReminderTarget.toSaveable(): List<String> = when (this) {
    is ReminderTarget.Checklist -> listOf(KIND_CHECKLIST, checklistId.toString())
    is ReminderTarget.Item -> listOf(KIND_ITEM, checklistId.toString(), fillId.toString(), itemId, text)
}

/** Inverse of [toSaveable]; null for anything malformed (never guesses a target). */
fun reminderTargetFromSaveable(values: List<String>): ReminderTarget? = when (values.firstOrNull()) {
    KIND_CHECKLIST -> values.getOrNull(1)?.toLongOrNull()?.let { ReminderTarget.Checklist(it) }
    KIND_ITEM -> {
        val checklistId = values.getOrNull(1)?.toLongOrNull()
        val fillId = values.getOrNull(2)?.toLongOrNull()
        val itemId = values.getOrNull(3)
        val text = values.getOrNull(4)
        if (checklistId != null && fillId != null && itemId != null && text != null) {
            ReminderTarget.Item(checklistId, fillId, itemId, text)
        } else {
            null
        }
    }
    else -> null
}

/** `rememberSaveable` saver for the activation sheet's nullable target. */
val ReminderTargetSaver: Saver<ReminderTarget?, Any> = listSaver<ReminderTarget?, String>(
    save = { target -> target?.toSaveable() ?: emptyList() },
    restore = { values -> reminderTargetFromSaveable(values) },
)
