package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResultHolder
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
    private val checklistId: Long?,
    private val analyzeRepository: AnalyzeRepository,
    private val checklistRepository: ChecklistRepository,
    private val appNavigator: AppNavigator,
    private val userDataRepository: UserDataRepository,
    private val getSubscriptionStatusUseCase: GetSubscriptionStatusUseCase,
    private val analyticsTracker: AnalyticsTracker
) : AppViewModel<AnalyzeScreenState, AnalyzeScreenIntent, Nothing>() {

    private val _screenState = MutableStateFlow(AnalyzeScreenState(
        isFillMode = checklistId != null,
        selectedChecklistId = checklistId
    ))
    override val screenState: StateFlow<AnalyzeScreenState> = _screenState.asStateFlow()

    init {
        if (checklistId != null) {
            loadTargetChecklist(checklistId)
        } else {
            loadChecklists()
        }
        observeUserData()
    }

    private fun loadTargetChecklist(checklistId: Long) {
        viewModelScope.launch {
            val checklist = checklistRepository.getChecklistById(checklistId)
            if (checklist != null) {
                _screenState.update {
                    it.copy(
                        targetChecklist = checklist,
                        selectedChecklistId = checklistId
                    )
                }
            }
        }
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

            is AnalyzeScreenIntent.OnFillNameChanged -> {
                _screenState.update { it.copy(fillName = intent.name) }
            }

            AnalyzeScreenIntent.OnCreateFillClick -> createFill()

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
                        error = null,
                        recordedAudioPath = null,
                        recordedAudioDuration = 0L
                    )
                }
            }

            // Voice recording intents
            AnalyzeScreenIntent.OnStartRecording -> {
                _screenState.update { it.copy(isRecording = true, error = null) }
            }

            AnalyzeScreenIntent.OnStopRecording -> {
                _screenState.update { it.copy(isRecording = false) }
            }

            is AnalyzeScreenIntent.OnRecordingComplete -> {
                _screenState.update {
                    it.copy(
                        isRecording = false,
                        recordedAudioPath = intent.filePath,
                        recordedAudioDuration = intent.durationMs
                    )
                }
            }

            is AnalyzeScreenIntent.OnRecordingError -> {
                _screenState.update {
                    it.copy(
                        isRecording = false,
                        error = intent.error
                    )
                }
            }

            AnalyzeScreenIntent.OnDeleteRecording -> {
                _screenState.update {
                    it.copy(
                        recordedAudioPath = null,
                        recordedAudioDuration = 0L
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

        val inputType = state.selectedInputType?.name?.lowercase() ?: "unknown"
        analyticsTracker.event("ai_analyze_started", mapOf("input_type" to inputType))

        viewModelScope.launch {
            _screenState.update { it.copy(isAnalyzing = true, error = null) }

            // In fill mode, use the target checklist; otherwise look in available checklists
            val targetChecklist = if (state.isFillMode) {
                state.targetChecklist
            } else {
                state.selectedChecklistId?.let { id ->
                    state.availableChecklists.find { it.id == id }
                }
            }

            analyzeRepository.analyzeData(inputData, targetChecklist)
                .onSuccess { result ->
                    analyticsTracker.event("ai_analyze_completed", mapOf(
                        "input_type" to inputType,
                        "item_count" to result.suggestedItems.size
                    ))
                    _screenState.update {
                        it.copy(
                            isAnalyzing = false,
                            analyzeResult = result
                        )
                    }

                    // Store result in holder and navigate to preview screen
                    AnalyzeResultHolder.set(
                        items = result.suggestedItems,
                        suggestedName = if (state.isFillMode) "AI Fill" else "New Checklist",
                        summary = result.summary,
                        isFillMode = state.isFillMode,
                        targetChecklistId = state.selectedChecklistId,
                        targetChecklistName = targetChecklist?.name
                    )
                    appNavigator.navigateToAnalyzeResultPreview()
                }
                .onFailure { error ->
                    analyticsTracker.event("ai_analyze_failed", mapOf(
                        "input_type" to inputType,
                        "error" to (error.message ?: "unknown")
                    ))
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

            InputDataType.VOICE -> {
                state.recordedAudioPath?.let { AnalyzeInputData.Audio(it) }
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
                    appNavigator.navigateToMainScreen(clearBackStack = true)
                }
                .onFailure { error ->
                    _screenState.update {
                        it.copy(error = error.message ?: "Ошибка создания чек-листа")
                    }
                }
        }
    }

    private fun createFill() {
        val state = _screenState.value
        val checklistId = state.selectedChecklistId ?: return
        val result = state.analyzeResult ?: return
        val fillName = state.fillName.takeIf { it.isNotBlank() } ?: "AI Fill"

        if (state.isSavingFill) return

        viewModelScope.launch {
            _screenState.update { it.copy(isSavingFill = true) }

            analyzeRepository.createFillFromResult(checklistId, fillName, result)
                .onSuccess { fillId ->
                    _screenState.update { it.copy(showResultDialog = false, isSavingFill = false) }
                    appNavigator.navigateToFillDetail(fillId, clearBackStack = true)
                }
                .onFailure { error ->
                    _screenState.update {
                        it.copy(
                            error = error.message ?: "Ошибка создания заполнения",
                            isSavingFill = false
                        )
                    }
                }
        }
    }
}
