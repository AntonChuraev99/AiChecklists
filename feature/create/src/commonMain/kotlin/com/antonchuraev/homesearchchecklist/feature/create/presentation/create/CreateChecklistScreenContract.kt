package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem

data class CreateChecklistState(
    val name: String = "",
    val items: List<ChecklistItem> = emptyList()
) : State

sealed interface CreateChecklistIntent : Intent {
    data object OnBackClick : CreateChecklistIntent
    data object OnSaveClick : CreateChecklistIntent
    data class OnNameChange(val name: String) : CreateChecklistIntent
    data class OnAddItem(val itemText: String) : CreateChecklistIntent
    data class OnDeleteItem(val item: ChecklistItem) : CreateChecklistIntent
}
