package com.antonchuraev.homesearchchecklist.feature.home.presentation.fills

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill

sealed interface FillsListState : State {
    data object Loading : FillsListState
    data object NotFound : FillsListState
    data class Content(
        val checklist: Checklist,
        val fills: List<ChecklistFill>,
        val fillToDelete: ChecklistFill? = null
    ) : FillsListState
}

sealed interface FillsListIntent : Intent {
    data object OnBackClick : FillsListIntent
    data class OnFillClick(val fill: ChecklistFill) : FillsListIntent
    data class OnDeleteFillClick(val fill: ChecklistFill) : FillsListIntent
    data object OnConfirmDeleteFill : FillsListIntent
    data object OnDismissDeleteDialog : FillsListIntent
}
