package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.InputDataType
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetSubscriptionStatusUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AnalyzeViewModel(
    private val analyzeRepository: AnalyzeRepository,
    private val checklistRepository: ChecklistRepository,
    private val appNavigator: AppNavigator,
    private val userDataRepository: UserDataRepository,
    private val getSubscriptionStatusUseCase: GetSubscriptionStatusUseCase
) : AppViewModel<AnalyzeScreenState, AnalyzeScreenIntent, Nothing>() {

    private val _screenState = MutableStateFlow(AnalyzeScreenState())
    override val screenState: StateFlow<AnalyzeScreenState> = _screenState.asStateFlow()

    init {
        loadChecklists()
        observeUserData()
    }

    private fun loadChecklists() {
        viewModelScope.launch {
            checklistRepository.checklists.collect { checklists ->
                _screenState.update { it.copy(availableChecklists = checklists) }
            }
        }
    }

    private fun observeUserData() {
        viewModelScope.launch {
            userDataRepository.getUserDataFlow().collect { userData ->
                _screenState.update {
                    it.copy(aiCredits = userData.aiCredits)
                }
            }
        }
        viewModelScope.launch {
            getSubscriptionStatusUseCase().collect { status ->
                _screenState.update {
                    it.copy(isPremium = status.isActive)
                }
            }
        }
    }

    override fun onIntent(intent: AnalyzeScreenIntent) {
        when (intent) {
            AnalyzeScreenIntent.OnBackClick -> appNavigator.onBack()

            is AnalyzeScreenIntent.OnInputTypeSelected -> {
                _screenState.update {
                    it.copy(
                        selectedInputType = intent.type,
                        error = null,
                        analyzeResult = null
                    )
                }
            }

            is AnalyzeScreenIntent.OnTextInputChanged -> {
                _screenState.update { it.copy(inputText = intent.text) }
            }

            is AnalyzeScreenIntent.OnUrlInputChanged -> {
                _screenState.update { it.copy(inputUrl = intent.url) }
            }

            is AnalyzeScreenIntent.OnFileSelected -> {
                _screenState.update {
                    it.copy(
                        selectedFilePath = intent.filePath,
                        selectedFileName = intent.fileName
                    )
                }
            }

            is AnalyzeScreenIntent.OnChecklistSelected -> {
                _screenState.update { it.copy(selectedChecklistId = intent.checklistId) }
            }

            is AnalyzeScreenIntent.OnChecklistNameChanged -> {
                _screenState.update { it.copy(checklistName = intent.name) }
            }

            AnalyzeScreenIntent.OnAnalyzeClick -> analyzeInput()

            AnalyzeScreenIntent.OnApplyToChecklistClick -> applyToExistingChecklist()

            AnalyzeScreenIntent.OnCreateNewChecklistClick -> createNewChecklist()

            AnalyzeScreenIntent.OnDismissResult -> {
                _screenState.update { it.copy(showResultDialog = false) }
            }

            AnalyzeScreenIntent.OnDismissError -> {
                _screenState.update { it.copy(error = null) }
            }

            AnalyzeScreenIntent.OnClearInput -> {
                _screenState.update {
                    it.copy(
                        inputText = "",
                        inputUrl = "",
                        selectedFilePath = null,
                        selectedFileName = null,
                        analyzeResult = null,
                        error = null
                    )
                }
            }
        }
    }

    private fun analyzeInput() {
        val state = _screenState.value
        val inputData = buildInputData(state) ?: run {
            _screenState.update { it.copy(error = "Пожалуйста, введите данные для анализа") }
            return
        }

        viewModelScope.launch {
            _screenState.update { it.copy(isAnalyzing = true, error = null) }

            val targetChecklist = state.selectedChecklistId?.let { id ->
                state.availableChecklists.find { it.id == id }
            }

            analyzeRepository.analyzeData(inputData, targetChecklist)
                .onSuccess { result ->
                    _screenState.update {
                        it.copy(
                            isAnalyzing = false,
                            analyzeResult = result,
                            showResultDialog = true
                        )
                    }
                }
                .onFailure { error ->
                    _screenState.update {
                        it.copy(
                            isAnalyzing = false,
                            error = error.message ?: "Ошибка анализа"
                        )
                    }
                }
        }
    }

    private fun buildInputData(state: AnalyzeScreenState): AnalyzeInputData? {
        return when (state.selectedInputType) {
            InputDataType.PHOTO -> {
                state.selectedFilePath?.let { AnalyzeInputData.Photo(it) }
            }

            InputDataType.PDF -> {
                state.selectedFilePath?.let {
                    AnalyzeInputData.PdfDocument(it, state.selectedFileName ?: it.substringAfterLast("/"))
                }
            }

            InputDataType.TEXT_FILE -> {
                state.selectedFilePath?.let { AnalyzeInputData.TextFile(it) }
            }

            InputDataType.WEB_LINK -> {
                state.inputUrl.takeIf { it.isNotBlank() }?.let { AnalyzeInputData.WebLink(it) }
            }

            InputDataType.RAW_TEXT -> {
                state.inputText.takeIf { it.isNotBlank() }?.let { AnalyzeInputData.RawText(it) }
            }

            null -> null
        }
    }

    private fun applyToExistingChecklist() {
        val state = _screenState.value
        val checklistId = state.selectedChecklistId ?: run {
            _screenState.update { it.copy(error = "Пожалуйста, выберите чек-лист") }
            return
        }
        val result = state.analyzeResult ?: return

        viewModelScope.launch {
            analyzeRepository.applyToChecklist(checklistId, result)
                .onSuccess {
                    _screenState.update { it.copy(showResultDialog = false) }
                    appNavigator.onBack()
                }
                .onFailure { error ->
                    _screenState.update {
                        it.copy(error = error.message ?: "Ошибка сохранения")
                    }
                }
        }
    }

    private fun createNewChecklist() {
        val state = _screenState.value
        val name = state.checklistName.takeIf { it.isNotBlank() } ?: "Новый чек-лист"
        val result = state.analyzeResult ?: return

        viewModelScope.launch {
            analyzeRepository.createChecklistFromResult(name, result)
                .onSuccess {
                    _screenState.update { it.copy(showResultDialog = false) }
                    appNavigator.onBack()
                }
                .onFailure { error ->
                    _screenState.update {
                        it.copy(error = error.message ?: "Ошибка создания чек-листа")
                    }
                }
        }
    }
}
