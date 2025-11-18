package com.antonchuraev.homesearchchecklist.viewmodels

import androidx.lifecycle.ViewModel
import com.antonchuraev.homesearchchecklist.data.repository.CreateChecklistBottomSheetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel для главного экрана с навигацией
 */
class MainViewModel(
    private val createChecklistBottomSheetRepository: CreateChecklistBottomSheetRepository
) : ViewModel() {
    
    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    val isShowCreateChecklistBottomSheet = createChecklistBottomSheetRepository.isVisible

    /**
     * Изменение выбранного таба
     */
    fun onTabSelected(index: Int) {
        _selectedTabIndex.value = index
    }
}

