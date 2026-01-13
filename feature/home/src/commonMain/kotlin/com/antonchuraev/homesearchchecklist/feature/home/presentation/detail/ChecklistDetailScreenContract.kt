package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist

sealed interface ChecklistDetailState : State {
    data object Loading : ChecklistDetailState
    data object NotFound : ChecklistDetailState
    data class Content(
        val checklist: Checklist,
        val isEditing: Boolean = false,
        val editingName: String = "",
        val showDeleteConfirmation: Boolean = false
    ) : ChecklistDetailState
}

sealed interface ChecklistDetailIntent : Intent {
    data object OnBackClick : ChecklistDetailIntent
    data class OnItemCheckedChange(val itemIndex: Int, val checked: Boolean) : ChecklistDetailIntent
    data object OnEditClick : ChecklistDetailIntent
    data object OnSaveClick : ChecklistDetailIntent
    data object OnCancelEditClick : ChecklistDetailIntent
    data class OnNameChanged(val name: String) : ChecklistDetailIntent
    data class OnDeleteItemClick(val itemIndex: Int) : ChecklistDetailIntent
    data object OnDeleteChecklistClick : ChecklistDetailIntent
    data object OnConfirmDelete : ChecklistDetailIntent
    data object OnDismissDeleteConfirmation : ChecklistDetailIntent
    data object OnAddItemClick : ChecklistDetailIntent
    data class OnAddItem(val text: String) : ChecklistDetailIntent
}
