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
    val newItemText: String = ""
) : State

sealed interface CreateChecklistIntent : Intent {
    data object OnBackClick : CreateChecklistIntent
    data object OnSaveClick : CreateChecklistIntent
    data class OnNameChange(val name: String) : CreateChecklistIntent
    data class OnNewItemTextChange(val text: String) : CreateChecklistIntent
    data object OnAddItemFromInput : CreateChecklistIntent
    data class OnDeleteItem(val item: ChecklistItem) : CreateChecklistIntent
}
