package com.antonchuraev.homesearchchecklist.feature.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.Checklist
import com.antonchuraev.homesearchchecklist.core.common.api.ChecklistItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CreateChecklistViewModel(
    private val repository: ChecklistRepository
) : ViewModel() {
    private val name = MutableStateFlow("")
    private val items = MutableStateFlow<List<ChecklistItem>>(emptyList())

    val state = combine(name, items) { n, i ->
        CreateChecklistState(n, i)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CreateChecklistState())

    fun onNameChange(newName: String) {
        name.value = newName
    }

    fun onAddItem(itemText: String) {
        items.value += ChecklistItem(itemText, false)
    }

    fun onDeleteItem(item: ChecklistItem) {
        items.value -= item
    }

    fun onSaveClick() {
        viewModelScope.launch {
            repository.addChecklist(Checklist(name = state.value.name, items = state.value.items))
        }
    }
}

data class CreateChecklistState(
    val name: String = "",
    val items: List<ChecklistItem> = emptyList()
)

