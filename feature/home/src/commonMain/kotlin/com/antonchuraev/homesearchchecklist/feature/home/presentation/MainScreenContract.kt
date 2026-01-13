package com.antonchuraev.homesearchchecklist.feature.home.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist

sealed interface MainScreenState : State {
    data object Loading : MainScreenState
    data class Success(val checklists: List<Checklist>) : MainScreenState
}

sealed interface MainScreenIntent : Intent {

    object OnAddChecklistClick : MainScreenIntent

    object OnAddChecklistFromTemplatesClick : MainScreenIntent

    object OnAiAnalyzeClick : MainScreenIntent

    data class OnChecklistClick(val checklist: Checklist) : MainScreenIntent
}
