package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem

data class CreateChecklistState(
    val name: String = "",
    val items: List<ChecklistItem> = emptyList(),
    val nameError: String? = null,
    val isEditMode: Boolean = false,
    val editChecklistId: Long? = null,
    val newItemText: String = "",
    // Inline item editing
    val editingItemId: String? = null,
    val editingItemText: String = "",
    // Gate: false when free user is at the checklist limit (edit mode always passes)
    val canCreateChecklist: Boolean = true
) : State

sealed interface CreateChecklistIntent : Intent {
    data object OnBackClick : CreateChecklistIntent
    data object OnSaveClick : CreateChecklistIntent
    data class OnNameChange(val name: String) : CreateChecklistIntent
    data class OnNewItemTextChange(val text: String) : CreateChecklistIntent
    data object OnAddItemFromInput : CreateChecklistIntent
    data class OnDeleteItem(val item: ChecklistItem) : CreateChecklistIntent
    // Inline item editing
    data class OnStartItemEdit(val itemId: String) : CreateChecklistIntent
    data class OnItemEditTextChange(val text: String) : CreateChecklistIntent
    data object OnConfirmItemEdit : CreateChecklistIntent
    data object OnCancelItemEdit : CreateChecklistIntent
}
