package com.antonchuraev.homesearchchecklist.screens.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.data.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.domain.model.ChecklistItem
import kotlinx.coroutines.launch

class CreateChecklistViewModel(
    private val checklistRepository: ChecklistRepository
) : ViewModel() {


    fun onSaveClick(name: String, elements: List<String>) {
        viewModelScope.launch {
            checklistRepository.addChecklist(
                Checklist(
                    name = name,
                    items = elements.map { ChecklistItem(it, false) }
                )
            )
        }
    }

}