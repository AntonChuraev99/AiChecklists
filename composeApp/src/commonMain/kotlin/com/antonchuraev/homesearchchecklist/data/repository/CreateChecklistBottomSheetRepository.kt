package com.antonchuraev.homesearchchecklist.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CreateChecklistBottomSheetRepository() {

    private val _isVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val isVisible = _isVisible.asStateFlow()

    fun show(){
        _isVisible.value = true
    }

    fun hide(){
        _isVisible.value = false
    }

}