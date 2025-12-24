package com.antonchuraev.homesearchchecklist.feature.debug.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DebugViewModel : ViewModel() {
    private val _showInfoDialog = MutableStateFlow(false)
    val showInfoDialog: StateFlow<Boolean> = _showInfoDialog.asStateFlow()

    fun showInfoDialog() {
        _showInfoDialog.value = true
    }

    fun hideInfoDialog() {
        _showInfoDialog.value = false
    }

    fun resetOnboarding() {
        // TODO: Реализовать сброс онбординга
    }

    fun clearData() {
        // TODO: Реализовать очистку данных
    }

    fun createTestChecklists() {
        // TODO: Реализовать создание тестовых данных
    }
}

