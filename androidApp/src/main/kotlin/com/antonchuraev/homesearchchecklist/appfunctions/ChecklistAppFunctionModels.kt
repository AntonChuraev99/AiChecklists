package com.antonchuraev.homesearchchecklist.appfunctions

import androidx.appfunctions.AppFunctionSerializable
import java.time.LocalDateTime

// Results returned to system agents (Gemini and others) by [BaseChecklistAppFunctionService].
//
// KSP reads the documentation of an @AppFunctionSerializable class ONLY from inline KDoc on each
// property: class-level @param / @property tags are dropped from the published metadata.

/** One of the user's checklists. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ChecklistSummary(
    /** Stable checklist ID. Pass it as `checklistId` to target exactly this checklist. */
    val id: Long,
    /** Checklist name as the user sees it. */
    val name: String,
    /** Number of items in the checklist, folders excluded. */
    val itemCount: Int,
)

/** The user's checklists. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ChecklistsResult(
    /** Checklists in the user's own order. Empty when the user has no checklists yet. */
    val checklists: List<ChecklistSummary>,
)

/** One item of a checklist. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ChecklistEntry(
    /** Item text. */
    val text: String,
    /** True when the item is marked as done. */
    val checked: Boolean,
)

/** The items of one checklist. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ChecklistContent(
    /** Checklist ID. */
    val id: Long,
    /** Checklist name. */
    val name: String,
    /** Items in display order, with their done state. */
    val items: List<ChecklistEntry>,
)

/** Outcome of marking an item as done. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CompletedItem(
    /** ID of the checklist that holds the item. */
    val checklistId: Long,
    /** Name of the checklist that holds the item. */
    val checklistName: String,
    /** Full text of the item that matched. */
    val itemText: String,
    /** True when the item was already done, so nothing changed. */
    val wasAlreadyCompleted: Boolean,
)

/** Outcome of setting a reminder on an item. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ItemReminder(
    /** ID of the checklist that holds the item. */
    val checklistId: Long,
    /** Name of the checklist that holds the item. */
    val checklistName: String,
    /** Full text of the item that matched. */
    val itemText: String,
    /** When the reminder fires, in the device's local time zone. */
    val remindAt: LocalDateTime,
)

/** Outcome of moving an item to another checklist. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MovedItem(
    /** Full text of the item that matched. */
    val itemText: String,
    /** ID of the checklist the item is in now. */
    val toChecklistId: Long,
    /** Name of the checklist the item is in now. */
    val toChecklistName: String,
    /** True when the item was already in that checklist, so nothing changed. */
    val wasAlreadyThere: Boolean,
)

/** Items found by a text search across all checklists. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class FoundItems(
    /** Number of matching items, capped at 10. Zero when nothing matched. */
    val matchCount: Int,
    /**
     * Up to 5 matches as `«item text» in checklist name`, separated by `; `, with `(+N more)`
     * when there are more. Empty when nothing matched.
     */
    val summary: String,
)
