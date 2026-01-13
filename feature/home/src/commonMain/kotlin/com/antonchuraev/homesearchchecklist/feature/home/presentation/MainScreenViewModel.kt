package com.antonchuraev.homesearchchecklist.feature.home.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class MainScreenViewModel(
    private val repository: ChecklistRepository,
    private val appNavigator: AppNavigator,
) : AppViewModel<MainScreenState, MainScreenIntent, Nothing>() {

    override val screenState: StateFlow<MainScreenState>
        get() = repository.checklists
            .map { MainScreenState.Success(it) }
            .defaultStateIn(MainScreenState.Loading)

    override fun onIntent(intent: MainScreenIntent) {
        when (intent) {
            MainScreenIntent.OnAddChecklistClick -> appNavigator.navigateToCreateChecklistScreen(
                null
            )

            MainScreenIntent.OnAddChecklistFromTemplatesClick -> appNavigator.navigateToTemplatesScreen()
            MainScreenIntent.OnAiAnalyzeClick -> appNavigator.navigateToAnalyzeScreen()
            is MainScreenIntent.OnChecklistClick -> appNavigator.navigateToChecklistDetail(intent.checklist.id)
        }
    }

}


