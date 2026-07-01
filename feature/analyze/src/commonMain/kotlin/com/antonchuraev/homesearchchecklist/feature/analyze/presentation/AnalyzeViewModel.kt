package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AiModelExperimentTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResultHolder
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.InputDataType
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetSubscriptionStatusUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AnalyzeViewModel(
    private val checklistId: Long?,
    private val fillDefault: Boolean = false,
    private val initialText: String? = null,
    private val autoAnalyze: Boolean = false,
    private val analyzeRepository: AnalyzeRepository,
    private val checklistRepository: ChecklistRepository,
    private val appNavigator: AppNavigator,
    private val userDataRepository: UserDataRepository,
    private val getSubscriptionStatusUseCase: GetSubscriptionStatusUseCase,
    private val analyticsTracker: AnalyticsTracker,
    private val activationCoordinator: ActivationCoordinator,
    private val remoteConfigProvider: RemoteConfigProvider,
    // Best-effort AI-model A/B attribution — nullable so pure-VM tests can omit it. Shared with the
    // chat + create flows so the sticky arm user-property and DataStore-persisted arm stay unified.
    private val aiModelExperimentTracker: AiModelExperimentTracker? = null,
) : AppViewModel<AnalyzeScreenState, AnalyzeScreenIntent, Nothing>() {

    private val _screenState = MutableStateFlow(
        run {
            // Prefill from shared/selected text (ACTION_PROCESS_TEXT "Checklist from text" /
            // "Fill (AI)" actions): pre-select RAW_TEXT and populate the input. By default
            // analysis is NOT auto-run — the user taps Analyze themselves (protects the
            // AI-credit budget). The [autoAnalyze] flag (new-user activation hero) flips this:
            // the chip tap / typed topic IS the explicit intent, so analysis runs once on init.
            val prefill = initialText?.takeIf { it.isNotBlank() }
            AnalyzeScreenState(
                isFillMode = checklistId != null,
                fillDefault = fillDefault,
                selectedChecklistId = checklistId,
                selectedInputType = if (prefill != null) InputDataType.RAW_TEXT else null,
                inputText = prefill.orEmpty()
            )
        }
    )
    override val screenState: StateFlow<AnalyzeScreenState> = _screenState.asStateFlow()

    init {
        if (checklistId != null) {
            loadTargetChecklist(checklistId)
        } else {
            loadChecklists()
        }
        observeUserData()

        // New-user activation hero: the chip tap / typed topic already expressed the intent to
        // generate, so kick off analysis immediately (RAW_TEXT input was prefilled above). On
        // success analyzeInput() navigates to the AI-item preview; on failure it surfaces an error
        // and the user stays on this screen to retry. Only fires when text is present.
        if (autoAnalyze && !initialText.isNullOrBlank()) {
            analyzeInput()
        }
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
            viewModelScope.launch {
                _screenState.update { it.copy(error = getString(Res.string.analyze_error_no_input)) }
            }
            return
        }

        val inputType = state.selectedInputType?.name?.lowercase() ?: "unknown"
        analyticsTracker.event(AnalyticsEvents.Analyze.STARTED, mapOf(AnalyticsParams.INPUT_TYPE to inputType))

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
                    // Mirror the server-assigned A/B arm into the sticky user-property + persist it
                    // (best-effort) so the paywall can attribute revenue even for Analyze/Create-only
                    // users. Guarded/deduped inside the tracker; never throws.
                    aiModelExperimentTracker?.report(result.modelVariant, result.modelId, result.aiFlow)

                    analyticsTracker.event(AnalyticsEvents.Analyze.COMPLETED, buildMap {
                        put(AnalyticsParams.INPUT_TYPE, inputType)
                        put("item_count", result.suggestedItems.size)
                        // Guardrail dimensions — present only when the server sent an arm; omitted
                        // otherwise so the event schema stays clean (matches ai_chat_response_received).
                        result.modelVariant?.let { put(AnalyticsParams.AI_MODEL_VARIANT, it) }
                        result.modelId?.let { put(AnalyticsParams.AI_MODEL_ID, it) }
                        result.aiFlow?.let { put(AnalyticsParams.AI_FLOW, it) }
                    })

                    // FILL flows (fill an existing checklist / apply to its default fill) keep going
                    // through the editable preview — there the user confirms which AI items land in
                    // the EXISTING checklist. Creating a NEW checklist skips the preview entirely: per
                    // product decision an AI "create" drops the user straight onto the finished
                    // checklist (no manual confirm/edit step) — see [createChecklistDirectly].
                    if (state.isFillMode || state.fillDefault) {
                        _screenState.update { it.copy(isAnalyzing = false, analyzeResult = result) }
                        AnalyzeResultHolder.set(
                            items = result.suggestedItems,
                            suggestedName = getString(Res.string.default_fill_name),
                            summary = result.summary,
                            isFillMode = state.isFillMode,
                            fillDefault = state.fillDefault,
                            targetChecklistId = state.selectedChecklistId,
                            targetChecklistName = targetChecklist?.name,
                            fillDefaultItems = result.fillItems,
                            hasFolders = false,
                            fromActivation = false
                        )
                        appNavigator.navigateToAnalyzeResultPreview()
                    } else {
                        createChecklistDirectly(result)
                    }
                }
                .onFailure { error ->
                    analyticsTracker.event(AnalyticsEvents.Analyze.FAILED, mapOf(
                        AnalyticsParams.INPUT_TYPE to inputType,
                        "error" to (error.message ?: "unknown")
                    ))
                    _screenState.update {
                        it.copy(
                            isAnalyzing = false,
                            error = error.message ?: getString(Res.string.analyze_error_analysis_failed)
                        )
                    }
                }
        }
    }

    /**
     * New-checklist AI path: skip the editable preview and create + open the checklist directly. Per
     * product decision an AI "create" lands the user straight on the finished checklist — the old
     * preview/confirm step is now reserved for FILL flows only. Reproduces what the preview's confirm
     * did so nothing regresses:
     *  - the CHECKLIST_CREATED analytics event (source=ai), and
     *  - when this came from the activation hero (autoAnalyze), the activation funnel via
     *    [ActivationCoordinator.onAiChecklistCreated] (it owns the new-user / show-once gating, so
     *    FIRST_AI_CHECKLIST_CREATED + the reminder opt-in still fire).
     *
     * [AnalyzeRepository.createChecklistFromResult] keeps the AI folder structure verbatim
     * (foldersEnabled = result.hasFolders), so structured responses still land as folders — only the
     * preview's soft 10-item cap and manual edits are intentionally dropped. On failure we clear the
     * loader and surface the create error; the user stays on the Analyze screen to retry.
     */
    private suspend fun createChecklistDirectly(result: AnalyzeResult) {
        val name = result.suggestedName?.takeIf { it.isNotBlank() }
            ?: getString(Res.string.default_checklist_name)
        analyzeRepository.createChecklistFromResult(name, result)
            .onSuccess { checklist ->
                analyticsTracker.event(
                    AnalyticsEvents.Checklist.CREATED,
                    mapOf(
                        "source" to "ai",
                        "item_count" to result.suggestedItems.size,
                        "has_folders" to result.hasFolders
                    )
                )
                // Only the activation hero (autoAnalyze) drives the funnel; the coordinator gates it
                // to new users / show-once. Mirrors AnalyzeResultPreviewViewModel's confirm path.
                if (autoAnalyze) {
                    val activationEnabled = remoteConfigProvider.getBoolean(
                        RemoteConfigKeys.ACTIVATION_BUNDLE_V1,
                        RemoteConfigDefaults.ACTIVATION_BUNDLE_V1,
                    )
                    activationCoordinator.onAiChecklistCreated(checklist.id, activationEnabled)
                }
                // Keep the loader visible until navigation: clearBackStack replaces this screen with
                // the checklist detail directly, so resetting isAnalyzing here would flash the Analyze
                // input screen for one frame before the detail opens.
                AnalyzeResultHolder.clear()
                appNavigator.navigateToChecklistDetail(checklist.id, clearBackStack = true)
            }
            .onFailure { error ->
                _screenState.update {
                    it.copy(
                        isAnalyzing = false,
                        error = error.message ?: getString(Res.string.error_create_checklist_failed)
                    )
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
            viewModelScope.launch {
                _screenState.update { it.copy(error = getString(Res.string.analyze_error_select_checklist)) }
            }
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
                        it.copy(error = error.message ?: getString(Res.string.error_save_failed))
                    }
                }
        }
    }

    private fun createNewChecklist() {
        val state = _screenState.value
        val result = state.analyzeResult ?: return

        viewModelScope.launch {
            val name = state.checklistName.takeIf { it.isNotBlank() } ?: getString(Res.string.default_checklist_name)
            analyzeRepository.createChecklistFromResult(name, result)
                .onSuccess {
                    _screenState.update { it.copy(showResultDialog = false) }
                    appNavigator.navigateToMainScreen(clearBackStack = true)
                }
                .onFailure { error ->
                    _screenState.update {
                        it.copy(error = error.message ?: getString(Res.string.error_create_checklist_failed))
                    }
                }
        }
    }

    private fun createFill() {
        val state = _screenState.value
        val checklistId = state.selectedChecklistId ?: return
        val result = state.analyzeResult ?: return

        if (state.isSavingFill) return

        viewModelScope.launch {
            val fillName = state.fillName.takeIf { it.isNotBlank() } ?: getString(Res.string.default_fill_name)
            _screenState.update { it.copy(isSavingFill = true) }

            analyzeRepository.createFillFromResult(checklistId, fillName, result)
                .onSuccess { fillId ->
                    _screenState.update { it.copy(showResultDialog = false, isSavingFill = false) }
                    appNavigator.navigateToFillDetail(fillId, clearBackStack = true)
                }
                .onFailure { error ->
                    _screenState.update {
                        it.copy(
                            error = error.message ?: getString(Res.string.error_create_fill_failed),
                            isSavingFill = false
                        )
                    }
                }
        }
    }
}
