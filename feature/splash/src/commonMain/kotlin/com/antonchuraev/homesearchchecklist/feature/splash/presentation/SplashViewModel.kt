package com.antonchuraev.homesearchchecklist.feature.splash.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SplashViewModel(
    private val userDataRepository: UserDataRepository,
) : ViewModel() {

    init {
        viewModelScope.launch {
            val userData = userDataRepository.getUserData()

            /*when (userData.isOnboardingPassed){
                true -> TODO("open main screen")
                false -> TODO("open onboarding")
            }*/
        }
    }
}


