package com.antonchuraev.homesearchchecklist.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel для экрана дебаг меню
 */
class DebugViewModel : ViewModel() {
    
    private val _showInfoDialog = MutableStateFlow(false)
    val showInfoDialog: StateFlow<Boolean> = _showInfoDialog.asStateFlow()
    
    /**
     * Показать диалог с информацией
     */
    fun showInfoDialog() {
        _showInfoDialog.value = true
    }
    
    /**
     * Скрыть диалог с информацией
     */
    fun hideInfoDialog() {
        _showInfoDialog.value = false
    }
    
    /**
     * Сброс онбординга
     */
    fun resetOnboarding() {
        // TODO: Реализовать сброс онбординга
    }
    
    /**
     * Очистка данных
     */
    fun clearData() {
        // TODO: Реализовать очистку данных
    }
    
    /**
     * Создание тестовых чек-листов
     */
    fun createTestChecklists() {
        // TODO: Реализовать создание тестовых данных
    }
}

