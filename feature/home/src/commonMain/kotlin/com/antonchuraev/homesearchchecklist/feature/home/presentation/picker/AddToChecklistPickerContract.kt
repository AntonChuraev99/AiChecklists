package com.antonchuraev.homesearchchecklist.feature.home.presentation.picker

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist

/**
 * State for the "Add to existing checklist" picker (ACTION_PROCESS_TEXT flow).
 * Lists the user's checklists; selecting one appends the shared text as a single item.
 */
data class AddToChecklistPickerState(
    val isLoading: Boolean = true,
    val checklists: List<Checklist> = emptyList(),
) : State

sealed interface AddToChecklistPickerIntent : Intent {
    data object OnBackClick : AddToChecklistPickerIntent
    data class OnChecklistSelected(val checklist: Checklist) : AddToChecklistPickerIntent
    data object OnCreateNewClick : AddToChecklistPickerIntent
}
