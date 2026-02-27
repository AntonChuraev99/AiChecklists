package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import kotlin.random.Random
import kotlinx.serialization.Serializable

/**
 * Checklist template - defines the items to check
 */
@Serializable
data class Checklist(
    val id: Long = 0L,
    val name: String,
    val items: List<ChecklistItem>,
    val reminderAt: Long? = null,
    val separateCompleted: Boolean = false,
    val position: Int = 0
)

/**
 * Single item in a checklist template
 * id is auto-generated for stable LazyColumn keys
 */
@ConsistentCopyVisibility
@Serializable
data class ChecklistItem private constructor(
    val text: String,
    val checked: Boolean = false,
    val id: String = generateId()
) {
    constructor(text: String, checked: Boolean = false) : this(
        text = text,
        checked = checked,
        id = generateId()
    )

    companion object {
        private fun generateId() = "${currentTimeMillis()}_${Random.nextInt(0, 10000)}"
    }
}

/**
 * A filled instance of a checklist
 * Each fill represents one "session" of using the checklist (e.g., viewing a specific apartment)
 * isDefault = true means this is the primary fill created automatically with the checklist
 */
@Serializable
data class ChecklistFill(
    val id: Long = 0L,
    val checklistId: Long,
    val name: String,
    val coverImagePath: String? = null,
    val items: List<ChecklistFillItem>,
    val createdAt: Long = 0L,
    val isDefault: Boolean = false
)

/**
 * Item state in a filled checklist
 * id is auto-generated for stable LazyColumn keys
 */
@ConsistentCopyVisibility
@Serializable
data class ChecklistFillItem private constructor(
    val text: String,
    val checked: Boolean,
    val note: String? = null,
    val id: String = generateId()
) {
    constructor(
        text: String,
        checked: Boolean,
        note: String? = null
    ) : this(
        text = text,
        checked = checked,
        note = note,
        id = generateId()
    )

    /** Update checked state while preserving id */
    fun withChecked(checked: Boolean) = ChecklistFillItem(text, checked, note, id)

    /** Update note while preserving id */
    fun withNote(note: String?) = ChecklistFillItem(text, checked, note, id)

    companion object {
        private fun generateId() = "${currentTimeMillis()}_${Random.nextInt(0, 10000)}"
    }
}
