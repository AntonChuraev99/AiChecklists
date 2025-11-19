package com.antonchuraev.homesearchchecklist.screens.create

import androidx.lifecycle.ViewModel
import com.antonchuraev.homesearchchecklist.data.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.domain.model.ChecklistItem

class CreateChecklistViewModel(
    private val checklistRepository: ChecklistRepository
) : ViewModel() {


    fun onSaveClick(name: String, elements: List<String>) {
        checklistRepository.addChecklist(
            Checklist(
                name = name,
                items = elements.map { ChecklistItem(it , false) }
            )
        )
    }

}