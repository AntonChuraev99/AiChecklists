package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreateChecklistViewModel(
    private val checklistRepository: ChecklistRepository,
    private val appNavigator: AppNavigator
) : AppViewModel<CreateChecklistState, CreateChecklistIntent, Nothing>() {

    private val _screenState = MutableStateFlow(CreateChecklistState())
    override val screenState: StateFlow<CreateChecklistState> = _screenState.asStateFlow()

    override fun onIntent(intent: CreateChecklistIntent) {
        when (intent) {
            CreateChecklistIntent.OnBackClick -> appNavigator.onBack()
            CreateChecklistIntent.OnSaveClick -> onSaveClick()
            is CreateChecklistIntent.OnNameChange -> _screenState.update {
                it.copy(name = intent.name, nameError = null)
            }
            is CreateChecklistIntent.OnAddItem -> _screenState.update {
                it.copy(items = it.items + ChecklistItem(intent.itemText, false))
            }

            is CreateChecklistIntent.OnDeleteItem -> _screenState.update {
                it.copy(items = it.items - intent.item)
            }
        }
    }

    private fun onSaveClick() {
        val currentState = _screenState.value

        if (currentState.name.isBlank()) {
            _screenState.update { it.copy(nameError = "Введите название чек-листа") }
            return
        }

        viewModelScope.launch {
            checklistRepository.addChecklist(
                Checklist(name = currentState.name.trim(), items = currentState.items)
            )
            appNavigator.onBack()
        }
    }
}
