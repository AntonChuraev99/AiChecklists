package com.antonchuraev.homesearchchecklist.viewmodels

import androidx.lifecycle.ViewModel

/**
 * ViewModel для экрана онбординга
 */
class OnboardingViewModel : ViewModel() {
    
    /**
     * Обработка завершения онбординга
     */
    fun onComplete() {
        // Здесь можно добавить логику сохранения состояния онбординга
        // например, в SharedPreferences или DataStore
    }
}

