package com.antonchuraev.homesearchchecklist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.data.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.data.repository.CreateChecklistBottomSheetRepository
import com.antonchuraev.homesearchchecklist.domain.model.Checklist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel для главного экрана с навигацией
 */
class MainViewModel(
    private val createChecklistBottomSheetRepository: CreateChecklistBottomSheetRepository,
    private val checklistRepository: ChecklistRepository
) : ViewModel() {

    val screenState = combine(
        checklistRepository.checklists,
        flowOf(1)
    ){ checklists , _ ->

        return@combine MainScreenState.Success(checklists)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MainScreenState.Loading
    )

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    val isShowCreateChecklistBottomSheet = createChecklistBottomSheetRepository.isVisible


    fun showCreateChecklistClick() {
        createChecklistBottomSheetRepository.show()
    }


    fun hideCreateChecklistBottomSheet() {
        createChecklistBottomSheetRepository.hide()
    }
}

sealed interface MainScreenState {

    object Loading : MainScreenState

    data class Success(val checkLists:List<Checklist>): MainScreenState

}

