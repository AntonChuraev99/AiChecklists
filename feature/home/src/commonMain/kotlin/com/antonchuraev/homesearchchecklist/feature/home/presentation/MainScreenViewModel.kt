package com.antonchuraev.homesearchchecklist.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MainScreenViewModel(
    private val repository: ChecklistRepository
) : ViewModel() {
    val state = repository.checklists.map { MainScreenState.Success(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenState.Loading)
}

sealed interface MainScreenState {
    data object Loading : MainScreenState
    data class Success(val checklists: List<Checklist>) : MainScreenState
}

