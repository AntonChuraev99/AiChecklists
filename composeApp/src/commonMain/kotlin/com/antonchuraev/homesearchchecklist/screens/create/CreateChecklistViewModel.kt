package com.antonchuraev.homesearchchecklist.screens.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.data.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.domain.model.ChecklistItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CreateChecklistViewModel(
    private val checklistRepository: ChecklistRepository
) : ViewModel() {

    private val name: MutableStateFlow<String> = MutableStateFlow("")

    private val items: MutableStateFlow<List<ChecklistItem>> = MutableStateFlow(emptyList())

    val screenState = combine(
        items,
        name
    ){ items , name ->
        return@combine ScreenState(
            name = name,
            items = items
        )
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ScreenState()
        )

    fun onNameChange(newName: String){
        name.value = newName
    }

    fun onAddItem(itemText: String){
        items.value += ChecklistItem(itemText, false)
    }

    fun onDeleteItem(item: ChecklistItem){
        items.value -= item
    }

    fun onSaveClick() {
        viewModelScope.launch {
            checklistRepository.addChecklist(
                Checklist(
                    name = screenState.value.name,
                    items = screenState.value.items
                )
            )
        }
    }

}

data class ScreenState(
    val name: String = "",
    val items: List<ChecklistItem> = emptyList(),
)