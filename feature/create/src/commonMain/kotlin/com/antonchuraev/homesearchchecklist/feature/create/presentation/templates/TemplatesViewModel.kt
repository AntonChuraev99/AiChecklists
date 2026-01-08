package com.antonchuraev.homesearchchecklist.feature.create.presentation.templates

import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TemplatesViewModel(
    private val appNavigator: AppNavigator
) : AppViewModel<TemplatesScreenState, TemplatesScreenIntent, Nothing>() {

    override val screenState: StateFlow<TemplatesScreenState>
        get() = MutableStateFlow(TemplatesScreenState)

    override fun onIntent(intent: TemplatesScreenIntent) {
        when (intent) {
            TemplatesScreenIntent.OnBackClick -> appNavigator.onBack()
        }
    }
}

