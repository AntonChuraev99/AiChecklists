package com.antonchuraev.homesearchchecklist.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel для главного таба со списком чек-листов
 */
class HomeTabViewModel : ViewModel() {
    
    private val _checklists = MutableStateFlow<List<String>>(emptyList())
    val checklists: StateFlow<List<String>> = _checklists.asStateFlow()
    
    /**
     * Создание нового чек-листа
     */
    fun createChecklist() {
        // TODO: Реализовать создание чек-листа
    }
    
    /**
     * Удаление чек-листа
     */
    fun deleteChecklist(id: String) {
        // TODO: Реализовать удаление чек-листа
    }
}

