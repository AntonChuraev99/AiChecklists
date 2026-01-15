package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

import kotlinx.serialization.Serializable

/**
 * Checklist template - defines the items to check
 */
@Serializable
data class Checklist(
    val id: Long = 0L,
    val name: String,
    val items: List<ChecklistItem>
)

/**
 * Single item in a checklist template
 */
@Serializable
data class ChecklistItem(
    val text: String,
    val checked: Boolean = false
)

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
 */
@Serializable
data class ChecklistFillItem(
    val text: String,
    val checked: Boolean,
    val note: String? = null
)
