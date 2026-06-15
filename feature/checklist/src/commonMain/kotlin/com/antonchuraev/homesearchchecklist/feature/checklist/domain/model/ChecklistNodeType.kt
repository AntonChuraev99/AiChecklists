package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

import kotlinx.serialization.Serializable

/**
 * Structural kind of a [ChecklistItem] node in the (template-only) folder tree.
 * ITEM = a regular checkable leaf (default).
 * FOLDER = a container grouping child items/folders via their [ChecklistItem.parentId].
 */
@Serializable
enum class ChecklistNodeType { ITEM, FOLDER }
