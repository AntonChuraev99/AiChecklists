package com.antonchuraev.homesearchchecklist.feature.home.presentation.fill

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill

sealed interface FillDetailState : State {
    data object Loading : FillDetailState
    data object NotFound : FillDetailState
    data class Content(
        val fill: ChecklistFill,
        val isEditing: Boolean = false,
        val editingName: String = "",
        val showDeleteConfirmation: Boolean = false,
        val noteDialogItemIndex: Int? = null,
        val editingNote: String = ""
    ) : FillDetailState
}

sealed interface FillDetailIntent : Intent {
    data object OnBackClick : FillDetailIntent

    // Edit mode
    data object OnEditClick : FillDetailIntent
    data object OnSaveClick : FillDetailIntent
    data object OnCancelEditClick : FillDetailIntent
    data class OnNameChanged(val name: String) : FillDetailIntent

    // Delete fill
    data object OnDeleteClick : FillDetailIntent
    data object OnConfirmDelete : FillDetailIntent
    data object OnDismissDeleteConfirmation : FillDetailIntent

    // Item interactions
    data class OnItemCheckedChange(val itemIndex: Int, val checked: Boolean) : FillDetailIntent

    // Notes
    data class OnAddNoteClick(val itemIndex: Int) : FillDetailIntent
    data class OnNoteChanged(val note: String) : FillDetailIntent
    data object OnSaveNote : FillDetailIntent
    data object OnDismissNoteDialog : FillDetailIntent

    // Cover image
    data object OnChangeCoverClick : FillDetailIntent
    data object OnRemoveCoverClick : FillDetailIntent
}
