package com.antonchuraev.homesearchchecklist

import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator

class AppViewModel(
    private val appNavigator: AppNavigator
) : ViewModel() {

    fun installNavController(navRouter: NavController) {
        appNavigator.installNavController(navRouter)
    }

}