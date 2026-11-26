package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AiEntrySource
import com.antonchuraev.homesearchchecklist.core.common.api.AiModelExperimentTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.AlwaysForegroundMonitor
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.inProcessAnalysisJobScheduler
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.wire
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJob
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobAnalytics
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobInput
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobHandoff
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisNotificationAskLatch
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.InMemoryAnalysisNotificationAskLatch
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobLimitReached
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobLimits
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobOutcome
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobRequest
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobRequestInput
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobScheduler
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobStatus
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobTarget
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisReadyNotifier
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisScreenAttachments
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AppForegroundMonitor
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.DeliveryClaimant
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.DeliveryRoute
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.StoredFileKind
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.isTerminal
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.resolveDelivery
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiAnalyzeError
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResultHolder
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.InputDataType
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.normalizeAnalyzeUrl
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AiActionCostProvider
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetSubscriptionStatusUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Analyze screen. Since 2026-11-20 (FEATURE_SPEC analyze-reliability-background) the analysis is NOT
 * owned by this ViewModel: the Analyze tap enqueues an app-scoped job ([AnalysisJobScheduler]) and
 * this ViewModel only WATCHES it. Leaving the screen never cancels or loses the result, and the
 * screen navigates only when it wins [AnalysisJobScheduler.claimDelivery] while it is attached and the
 * app is visible — otherwise the app snackbar or the notification delivers.
 *
 * The ViewModel is Activity-scoped (no ViewModel-store entry decorator in NavDisplay), so one
 * instance spans several visits: [AnalyzeJobIntent.OnScreenEntered] with a new token starts a new
 * visit (fresh form, detached from the previous job).
 */
class AnalyzeViewModel(
    private val checklistId: Long?,
    private val fillDefault: Boolean = false,
    private val initialText: String? = null,
    private val autoAnalyze: Boolean = false,
    /**
     * Material chosen at the DOOR (the v2 capture dock's Photo / PDF / Link / Voice row), so the
     * screen opens on that input instead of on its own source picker. See [resolveEntryMaterial].
     */
    private val initialInputKind: AnalyzeInputKind? = null,
    /** Which affordance opened this screen — stamped onto every `ai_analyze_*` event below. */
    private val entrySource: AiEntrySource? = null,
    private val analyzeRepository: AnalyzeRepository,
    private val checklistRepository: ChecklistRepository,
    private val appNavigator: AppNavigator,
    private val userDataRepository: UserDataRepository,
    private val getSubscriptionStatusUseCase: GetSubscriptionStatusUseCase,
    private val analyticsTracker: AnalyticsTracker,
    private val activationCoordinator: ActivationCoordinator,
    // Best-effort AI-model A/B attribution — nullable so pure-VM tests can omit it. Shared with the
    // chat + create flows so the sticky arm user-property and DataStore-persisted arm stay unified.
    private val aiModelExperimentTracker: AiModelExperimentTracker? = null,
    /** Server-side cost of one run; null = unknown, the button is then not gated on it. */
    private val actionCostProvider: AiActionCostProvider? = null,
    /** Compose Resources lookup; injectable because resources do not load on a plain JVM host. */
    private val resolveString: suspend (StringResource) -> String = { getString(it) },
    /** App-scoped job owner from Koin. Null = an in-process graph on this ViewModel (unit tests). */
    jobScheduler: AnalysisJobScheduler? = null,
    private val attachments: AnalysisScreenAttachments = AnalysisScreenAttachments(),
    private val foregroundMonitor: AppForegroundMonitor = AlwaysForegroundMonitor(),
    private val readyNotifier: AnalysisReadyNotifier? = null,
    /** A failed job handed over by the app snackbar / notification, shown in the Failed phase. */
    private val jobHandoff: AnalysisJobHandoff = AnalysisJobHandoff(),
    /** "Notification permission asked once ever" — persisted in production. */
    private val notificationAskLatch: AnalysisNotificationAskLatch = InMemoryAnalysisNotificationAskLatch(),
) : AppViewModel<AnalyzeScreenState, AnalyzeScreenIntent, Nothing>() {

    private val scheduler: AnalysisJobScheduler = jobScheduler ?: inProcessAnalysisJobScheduler(
        scope = viewModelScope,
        analyzeRepository = analyzeRepository,
        checklistRepository = checklistRepository,
        analyticsTracker = analyticsTracker,
        foregroundMonitor = foregroundMonitor,
        defaultChecklistName = { resolveString(Res.string.default_checklist_name) },
        aiModelExperimentTracker = aiModelExperimentTracker,
    )

    private val _screenState = MutableStateFlow(initialFormState())
    override val screenState: StateFlow<AnalyzeScreenState> = _screenState.asStateFlow()

    /** `rememberSaveable` token of the current visit; null until the screen reports its first one. */
    private var visitToken: String? = null

    /** The job THIS visit started (or retried). Null in Editing. */
    private var currentJobId: String? = null
    private var watchJob: Job? = null
    private val attached = MutableStateFlow(true)

    /** Latch between the Analyze tap and the enqueue result: a double tap must not start two jobs. */
    private var enqueueInFlight = false

    init {
        if (checklistId != null) {
            loadTargetChecklist(checklistId)
        } else {
            loadChecklists()
        }
        observeUserData()
        observeCost()
        observeOtherJobs()
        adoptHandedOverJob()

        // New-user activation hero: the chip tap / typed topic already expressed the intent to
        // generate, so kick off analysis immediately (RAW_TEXT input was prefilled). Once per visit:
        // a new visit token re-runs it ([startNewVisit]).
        if (autoAnalyze && !initialText.isNullOrBlank()) {
            analyzeInput()
        }
    }

    /** Form seeded from the route arguments — the state of every new visit. */
    private fun initialFormState(): AnalyzeScreenState {
        // Prefill from shared/selected text (ACTION_PROCESS_TEXT "Checklist from text" /
        // "Fill (AI)" actions): pre-select RAW_TEXT and populate the input. By default
        // analysis is NOT auto-run — the user taps Analyze themselves (protects the
        // AI-credit budget). The [autoAnalyze] flag (new-user activation hero) flips this.
        val prefill = initialText?.takeIf { it.isNotBlank() }
        return AnalyzeScreenState(
            isFillMode = checklistId != null,
            fillDefault = fillDefault,
            selectedChecklistId = checklistId,
            // Prefill wins over the door's material — see [resolveEntryMaterial]; the screen's
            // on-entry auto-open asks the same function, so the two can never disagree.
            selectedInputType = resolveEntryMaterial(initialText, initialInputKind),
            inputText = prefill.orEmpty(),
            maxParallelJobs = AnalysisJobLimits.MAX_RUNNING,
        )
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
            // `projects`: this list is the "apply the AI result to an existing checklist" picker,
            // and the v2 system Inbox must be hidden from every picker.
            checklistRepository.projects.collect { checklists ->
                _screenState.update { it.copy(availableChecklists = checklists) }
            }
        }
    }

    private fun observeUserData() {
        viewModelScope.launch {
            userDataRepository.getUserDataFlow().collect { userData ->
                _screenState.update { it.copy(aiCredits = userData.aiCredits) }
            }
        }
        viewModelScope.launch {
            getSubscriptionStatusUseCase().collect { status ->
                _screenState.update { it.copy(isPremium = status.isActive) }
            }
        }
    }

    private fun observeCost() {
        val provider = actionCostProvider ?: return
        viewModelScope.launch {
            provider.actionCost.collect { cost -> _screenState.update { it.copy(aiActionCost = cost) } }
        }
        refreshCost()
    }

    private fun refreshCost() {
        val provider = actionCostProvider ?: return
        // A failure keeps the last good value (logged inside the provider); null = not gated.
        viewModelScope.launch { provider.refresh() }
    }

    /** "1 analysis in progress" strip and the credits held by running jobs (owner decision 1). */
    private fun observeOtherJobs() {
        viewModelScope.launch {
            combine(scheduler.jobs, _screenState) { jobs, state -> jobs to state.aiActionCost }
                .collect { (jobs, cost) ->
                    val running = jobs.filter { !it.status.isTerminal }
                    val others = running.count { it.id != currentJobId }
                    val reserved = running.size * (cost ?: 0)
                    _screenState.update {
                        if (it.otherJobsInProgress == others && it.creditsReservedByRunningJobs == reserved) it
                        else it.copy(otherJobsInProgress = others, creditsReservedByRunningJobs = reserved)
                    }
                }
        }
    }

    override fun onIntent(intent: AnalyzeScreenIntent) {
        when (intent) {
            AnalyzeScreenIntent.OnBackClick -> appNavigator.onBack()

            is AnalyzeScreenIntent.OnInputTypeSelected -> {
                _screenState.update { state ->
                    // Changing the material clears the FILE fields and only those: PHOTO, PDF and
                    // TEXT_FILE share `selectedFilePath` / `selectedFileName`, so a stale value there
                    // could be sent under the wrong material. Every other payload field belongs to one
                    // material only — clearing it would destroy typed text or a recording on a mis-tap.
                    if (state.selectedInputType == intent.type) {
                        // Re-selecting the material you are already on is a no-op, NOT a reset.
                        state.copy(error = null, analyzeResult = null)
                    } else {
                        state.copy(
                            selectedInputType = intent.type,
                            error = null,
                            analyzeResult = null,
                            selectedFilePath = null,
                            selectedFileName = null
                        )
                    }
                }
            }

            is AnalyzeScreenIntent.OnTextInputChanged -> {
                _screenState.update { it.copy(inputText = intent.text) }
            }

            is AnalyzeScreenIntent.OnUrlInputChanged -> {
                _screenState.update { it.copy(inputUrl = intent.url, inputError = null) }
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
                // The legacy error dialog mirrors the Failed phase: dismissing it is "back to the form".
                if (_screenState.value.jobPhase is AnalyzeJobPhase.Failed) backToEditing()
                else _screenState.update { it.copy(error = null) }
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
                        inputError = null,
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

            is AnalyzeJobIntent -> onJobIntent(intent)

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

    private fun onJobIntent(intent: AnalyzeJobIntent) {
        when (intent) {
            is AnalyzeJobIntent.OnScreenEntered -> onScreenEntered(intent.entryToken)

            AnalyzeJobIntent.OnScreenLeft -> {
                attached.value = false
                currentJobId?.let { attachments.detach(it) }
            }

            // Same as Back, stated as a promise: the job keeps running and is delivered elsewhere.
            AnalyzeJobIntent.OnContinueInBackgroundClick -> appNavigator.onBack()

            AnalyzeJobIntent.OnCancelWaitingClick -> {
                val jobId = currentJobId ?: return
                viewModelScope.launch {
                    if (scheduler.cancel(jobId)) {
                        detachFromJob()
                        _screenState.update { it.withPhase(AnalyzeJobPhase.Editing) }
                    } else {
                        // Already sent: credits are reserved, a sent request is never cancelled. The
                        // Waiting phase now reports canCancel = false, which hides the button.
                        _screenState.update { it.withPhase(AnalyzeJobPhase.Waiting(waitingForNetwork = false, canCancel = false)) }
                    }
                }
            }

            AnalyzeJobIntent.OnRetryClick -> retry()

            AnalyzeJobIntent.OnBackToEditingClick -> backToEditing()

            AnalyzeJobIntent.OnUpgradeClick -> {
                // Only from the Upgrade CTA of a Failed phase: a second tap lands on Editing and is
                // ignored, so a double tap cannot push two paywalls. The form is restored first so
                // the user returns from the paywall to their input, not to a stale failure.
                val phase = _screenState.value.jobPhase
                if (phase is AnalyzeJobPhase.Failed && phase.cta == FailedCta.Upgrade) {
                    backToEditing()
                    appNavigator.navigateToPaywall(source = PAYWALL_SOURCE_INSUFFICIENT_CREDITS)
                }
            }

            is AnalyzeJobIntent.OnNotificationPermissionResult -> {
                _screenState.update { it.copy(askNotificationPermission = false) }
            }
        }
    }

    private fun onScreenEntered(token: String) {
        val previous = visitToken
        visitToken = token
        attached.value = true
        currentJobId?.let { attachments.attach(it) }
        if (previous != null && previous != token) startNewVisit()
    }

    /**
     * A new visit through the same door (the Activity-scoped ViewModel survived): fresh form from the
     * route arguments, detached from whatever the previous visit started. The previous job is NOT
     * cancelled — it finishes and is delivered by the app host if nobody claimed it.
     */
    private fun startNewVisit() {
        val previousJob = currentJobId
        detachFromJob()
        enqueueInFlight = false
        val keep = _screenState.value
        _screenState.value = initialFormState().copy(
            targetChecklist = keep.targetChecklist,
            availableChecklists = keep.availableChecklists,
            aiCredits = keep.aiCredits,
            aiActionCost = keep.aiActionCost,
            isPremium = keep.isPremium,
            otherJobsInProgress = keep.otherJobsInProgress,
            creditsReservedByRunningJobs = keep.creditsReservedByRunningJobs,
        )
        // A finished outcome the previous visit already showed needs no further signal.
        if (previousJob != null) {
            viewModelScope.launch {
                val job = scheduler.observe(previousJob).first()
                if (job != null && job.status.isTerminal && job.deliveredBy != null) scheduler.acknowledge(previousJob)
            }
        }
        refreshCost()
        if (adoptHandedOverJob()) return
        if (autoAnalyze && !initialText.isNullOrBlank()) analyzeInput()
    }

    private fun analyzeInput() {
        val state = _screenState.value
        if (state.jobPhase != AnalyzeJobPhase.Editing || enqueueInFlight) return

        if (state.selectedInputType == InputDataType.WEB_LINK && state.inputUrl.isNotBlank() &&
            normalizeAnalyzeUrl(state.inputUrl) == null
        ) {
            // Never send what is not a link: it used to reach the server and cost credits.
            viewModelScope.launch {
                _screenState.update { it.copy(inputError = resolveString(Res.string.analyze_error_invalid_url)) }
            }
            return
        }
        val input = buildRequestInput(state) ?: run {
            viewModelScope.launch {
                _screenState.update { it.copy(error = resolveString(Res.string.analyze_error_no_input)) }
            }
            return
        }

        val inputType = state.selectedInputType?.name?.lowercase() ?: "unknown"
        // "none", never a missing property: an omitted dimension has no denominator. Stamped on all
        // three analyze events (the job carries it to completed / failed).
        val entrySourceParam = entrySource?.wire ?: "none"
        val target = if (state.isFillMode && checklistId != null) {
            AnalysisJobTarget.Fill(
                checklistId = checklistId,
                checklistName = state.targetChecklist?.name.orEmpty(),
                fillDefault = state.fillDefault,
                isFillMode = state.isFillMode,
            )
        } else {
            AnalysisJobTarget.NewChecklist(fromActivationHero = autoAnalyze)
        }

        enqueueInFlight = true
        _screenState.update { it.copy(error = null, inputError = null) }
        viewModelScope.launch {
            val result = scheduler.enqueue(
                AnalysisJobRequest(
                    input = input,
                    target = target,
                    analytics = AnalysisJobAnalytics(inputType = inputType, source = entrySourceParam),
                ),
            )
            enqueueInFlight = false
            result
                .onSuccess { jobId ->
                    analyticsTracker.event(
                        AnalyticsEvents.Analyze.STARTED,
                        AnalyzeEventParams.started(inputType, entrySourceParam),
                    )
                    // Owner decision 3: asked once EVER (persisted latch), not once per ViewModel.
                    val askNow = notificationAskLatch.claimFirstAsk()
                    _screenState.update {
                        it.withPhase(AnalyzeJobPhase.Waiting(waitingForNetwork = false, canCancel = false))
                            .copy(askNotificationPermission = askNow)
                    }
                    watch(jobId)
                }
                .onFailure { error -> onEnqueueFailed(error) }
        }
    }

    /** Nothing was sent and nothing charged: tell the user what to do about it. */
    private suspend fun onEnqueueFailed(error: Throwable) {
        val message = when (error) {
            is AnalysisJobLimitReached -> resolveString(Res.string.analyze_too_many_jobs).replace("%1\$d", error.max.toString())
            is AiAnalyzeError.InputUnreadable -> resolveString(Res.string.analyze_error_input_unreadable)
            else -> resolveString(Res.string.analyze_error_analysis_failed)
        }
        _screenState.update {
            val cleared = if (error is AiAnalyzeError.InputUnreadable) {
                // The picked file is gone (cache cleared): make the user pick it again.
                it.copy(selectedFilePath = null, selectedFileName = null)
            } else {
                it
            }
            cleared.withPhase(AnalyzeJobPhase.Editing).copy(
                error = message,
                hintError = message.takeIf { error is AnalysisJobLimitReached },
            )
        }
    }

    /**
     * A failed job opened from the app snackbar / notification ([AnalysisJobHandoff]): show it Failed
     * — its reason, its buttons and the captured input — instead of an empty form. Its delivery was
     * already claimed by that surface, so nothing is claimed here. Returns true when a job was adopted.
     */
    private fun adoptHandedOverJob(): Boolean {
        val jobId = jobHandoff.take() ?: return false
        currentJobId = jobId
        if (attached.value) attachments.attach(jobId)
        viewModelScope.launch {
            val job = scheduler.observe(jobId).first()
            val status = job?.status as? AnalysisJobStatus.Failed
            if (job == null || status == null) {
                // Pruned meanwhile (another surface acknowledged it): a plain form is the honest state.
                detachFromJob()
                return@launch
            }
            _screenState.update { it.restoringInputOf(job) }
            deliverFailure(status)
        }
        return true
    }

    /** The material of [job] back in the form; a file's bytes stay in the job (retry reuses them). */
    private fun AnalyzeScreenState.restoringInputOf(job: AnalysisJob): AnalyzeScreenState = when (val input = job.input) {
        is AnalysisJobInput.Text -> copy(selectedInputType = InputDataType.RAW_TEXT, inputText = input.text)
        is AnalysisJobInput.Url -> copy(selectedInputType = InputDataType.WEB_LINK, inputUrl = input.url)
        is AnalysisJobInput.StoredFile -> copy(
            selectedInputType = when (input.kind) {
                StoredFileKind.PHOTO -> InputDataType.PHOTO
                StoredFileKind.PDF -> InputDataType.PDF
                StoredFileKind.AUDIO -> InputDataType.VOICE
            },
            selectedFilePath = null,
            selectedFileName = null,
        )
    }

    private fun watch(jobId: String) {
        detachFromJob()
        currentJobId = jobId
        if (attached.value) attachments.attach(jobId)
        watchJob = viewModelScope.launch {
            combine(scheduler.observe(jobId), attached, foregroundMonitor.isInForeground) { job, isAttached, fg ->
                Triple(job, isAttached, fg)
            }.collect { (job, isAttached, fg) ->
                if (job != null && job.id == currentJobId) onJobChanged(job, isAttached, fg)
            }
        }
    }

    private fun detachFromJob() {
        watchJob?.cancel()
        watchJob = null
        currentJobId?.let { attachments.detach(it) }
        currentJobId = null
    }

    private suspend fun onJobChanged(job: AnalysisJob, isAttached: Boolean, isForeground: Boolean) {
        when (val status = job.status) {
            is AnalysisJobStatus.Queued -> _screenState.update {
                it.withPhase(AnalyzeJobPhase.Waiting(status.waitingForNetwork, canCancel = status.waitingForNetwork))
            }
            AnalysisJobStatus.Running -> _screenState.update {
                it.withPhase(AnalyzeJobPhase.Waiting(waitingForNetwork = false, canCancel = false))
            }
            AnalysisJobStatus.Cancelled -> _screenState.update { it.withPhase(AnalyzeJobPhase.Editing) }
            is AnalysisJobStatus.Succeeded, is AnalysisJobStatus.Failed -> {
                val route = resolveDelivery(job, attachedJobId = job.id.takeIf { isAttached }, isForeground = isForeground)
                // Detached or backgrounded: the app snackbar / notification delivers. Never yank the user.
                if (route != DeliveryRoute.Screen) return
                if (!scheduler.claimDelivery(job.id, DeliveryClaimant.SCREEN)) return
                readyNotifier?.cancel(job.id)
                if (status is AnalysisJobStatus.Succeeded) deliverSuccess(job, status.outcome)
                else deliverFailure((status as AnalysisJobStatus.Failed))
            }
        }
    }

    private suspend fun deliverSuccess(job: AnalysisJob, outcome: AnalysisJobOutcome) {
        when (outcome) {
            is AnalysisJobOutcome.CreatedChecklist -> {
                // Keep the loader up until navigation: clearBackStack replaces this screen with the
                // detail directly; dropping it here would flash the form for a frame.
                _screenState.update { it.withPhase(AnalyzeJobPhase.Delivering) }
                // The coordinator gates both calls to new users / show-once. Only the activation hero
                // counts as the "first AI checklist"; any other create counts as the user's first one.
                // Run here (delivery), not in the job: a worker emission with no UI would burn the sheet.
                if ((job.target as? AnalysisJobTarget.NewChecklist)?.fromActivationHero == true) {
                    activationCoordinator.onAiChecklistCreated(outcome.checklistId)
                } else {
                    activationCoordinator.onUserChecklistCreated(outcome.checklistId)
                }
                analyticsTracker.event(
                    AnalyzeEventParams.EVENT_RESULT_OPENED,
                    AnalyzeEventParams.resultOpened(DeliveryClaimant.SCREEN.wire, "checklist"),
                )
                AnalyzeResultHolder.clear()
                appNavigator.navigateToChecklistDetail(outcome.checklistId, clearBackStack = true)
                scheduler.acknowledge(job.id)
            }
            is AnalysisJobOutcome.FillReady -> {
                val state = _screenState.value
                val result = AnalyzeResult(
                    suggestedItems = outcome.suggestedItems,
                    summary = outcome.summary,
                    fillItems = outcome.fillItems,
                )
                val target = job.target as? AnalysisJobTarget.Fill
                // FILL flows keep going through the editable preview: there the user confirms which AI
                // items land in the EXISTING checklist. Nothing was written by the job.
                _screenState.update { it.withPhase(AnalyzeJobPhase.Editing).copy(analyzeResult = result) }
                AnalyzeResultHolder.set(
                    items = outcome.suggestedItems,
                    suggestedName = resolveString(Res.string.default_fill_name),
                    summary = outcome.summary,
                    isFillMode = target?.isFillMode ?: state.isFillMode,
                    fillDefault = target?.fillDefault ?: state.fillDefault,
                    targetChecklistId = target?.checklistId ?: state.selectedChecklistId,
                    targetChecklistName = target?.checklistName?.ifBlank { null } ?: state.targetChecklist?.name,
                    fillDefaultItems = outcome.fillItems,
                    hasFolders = false,
                    fromActivation = false
                )
                analyticsTracker.event(
                    AnalyzeEventParams.EVENT_RESULT_OPENED,
                    AnalyzeEventParams.resultOpened(DeliveryClaimant.SCREEN.wire, "fill"),
                )
                detachFromJob()
                scheduler.acknowledge(job.id)
                appNavigator.navigateToAnalyzeResultPreview()
            }
        }
    }

    /**
     * Shown on the screen; the job stays until the user picks a CTA (so "Try again" can reuse the
     * captured input). [AnalyzeScreenState.error] mirrors the message for the legacy error dialog.
     */
    private suspend fun deliverFailure(status: AnalysisJobStatus.Failed) {
        val message = status.failure.userFacingMessage(resolveString)
        // premium = revenueCat || firestore: state.isPremium is the store status, the user doc is
        // the Firestore half (a restore that failed client-side still leaves it true).
        val isPremium = _screenState.value.isPremium || userDataRepository.getUserDataFlow().value.isPremium
        _screenState.update {
            it.withPhase(AnalyzeJobPhase.Failed(message, status.failure.failedCta(isPremium))).copy(error = message)
        }
    }

    private fun retry() {
        val jobId = currentJobId
        if (_screenState.value.jobPhase !is AnalyzeJobPhase.Failed || jobId == null || enqueueInFlight) return
        enqueueInFlight = true
        viewModelScope.launch {
            val result = scheduler.retry(jobId)
            enqueueInFlight = false
            result
                .onSuccess { newId ->
                    val analytics = scheduler.observe(newId).first()?.analytics
                    analyticsTracker.event(
                        AnalyticsEvents.Analyze.STARTED,
                        AnalyzeEventParams.started(
                            analytics?.inputType ?: _screenState.value.selectedInputType?.name?.lowercase() ?: "unknown",
                            analytics?.source ?: entrySource?.wire ?: "none",
                        ),
                    )
                    _screenState.update {
                        it.withPhase(AnalyzeJobPhase.Waiting(waitingForNetwork = false, canCancel = false)).copy(error = null)
                    }
                    watch(newId)
                }
                .onFailure { error ->
                    // The captured input is gone or too many jobs run: back to the form with the reason.
                    detachFromJob()
                    onEnqueueFailed(error)
                }
        }
    }

    private fun backToEditing() {
        val jobId = currentJobId
        detachFromJob()
        _screenState.update { it.withPhase(AnalyzeJobPhase.Editing).copy(error = null) }
        if (jobId != null) viewModelScope.launch { scheduler.acknowledge(jobId) }
    }

    /** Pre-capture input for the job; null = nothing to analyze ("Select a source" dialog). */
    private fun buildRequestInput(state: AnalyzeScreenState): AnalysisJobRequestInput? {
        return when (state.selectedInputType) {
            InputDataType.PHOTO -> state.selectedFilePath?.let { path ->
                AnalysisJobRequestInput.File(
                    kind = StoredFileKind.PHOTO,
                    filePath = path,
                    fileName = state.selectedFileName ?: path.substringAfterLast("/"),
                    mimeType = imageMimeType(state.selectedFileName ?: path),
                )
            }

            InputDataType.PDF -> state.selectedFilePath?.let { path ->
                AnalysisJobRequestInput.File(
                    kind = StoredFileKind.PDF,
                    filePath = path,
                    fileName = state.selectedFileName ?: path.substringAfterLast("/"),
                    mimeType = "application/pdf",
                )
            }

            InputDataType.TEXT_FILE -> state.selectedFilePath?.let { AnalysisJobRequestInput.TextFile(it) }

            InputDataType.WEB_LINK -> state.inputUrl.takeIf { it.isNotBlank() }
                ?.let(::normalizeAnalyzeUrl)
                ?.let { AnalysisJobRequestInput.Url(it) }

            InputDataType.RAW_TEXT -> state.inputText.takeIf { it.isNotBlank() }?.let { AnalysisJobRequestInput.Text(it) }

            InputDataType.VOICE -> state.recordedAudioPath?.let { path ->
                AnalysisJobRequestInput.File(
                    kind = StoredFileKind.AUDIO,
                    filePath = path,
                    fileName = path.substringAfterLast("/"),
                    mimeType = "audio/m4a",
                )
            }

            null -> null
        }
    }

    private fun imageMimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        else -> "image/jpeg"
    }

    private fun applyToExistingChecklist() {
        val state = _screenState.value
        val checklistId = state.selectedChecklistId ?: run {
            viewModelScope.launch {
                _screenState.update { it.copy(error = resolveString(Res.string.analyze_error_select_checklist)) }
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
                        it.copy(error = error.message ?: resolveString(Res.string.error_save_failed))
                    }
                }
        }
    }

    private fun createNewChecklist() {
        val state = _screenState.value
        val result = state.analyzeResult ?: return

        viewModelScope.launch {
            val name = state.checklistName.takeIf { it.isNotBlank() } ?: resolveString(Res.string.default_checklist_name)
            analyzeRepository.createChecklistFromResult(name, result)
                .onSuccess {
                    _screenState.update { it.copy(showResultDialog = false) }
                    appNavigator.navigateToMainScreen(clearBackStack = true)
                }
                .onFailure { error ->
                    _screenState.update {
                        it.copy(error = error.message ?: resolveString(Res.string.error_create_checklist_failed))
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
            val fillName = state.fillName.takeIf { it.isNotBlank() } ?: resolveString(Res.string.default_fill_name)
            _screenState.update { it.copy(isSavingFill = true) }

            analyzeRepository.createFillFromResult(checklistId, fillName, result)
                .onSuccess { fillId ->
                    _screenState.update { it.copy(showResultDialog = false, isSavingFill = false) }
                    appNavigator.navigateToFillDetail(fillId, clearBackStack = true)
                }
                .onFailure { error ->
                    _screenState.update {
                        it.copy(
                            error = error.message ?: resolveString(Res.string.error_create_fill_failed),
                            isSavingFill = false
                        )
                    }
                }
        }
    }

    override fun onCleared() {
        currentJobId?.let { attachments.detach(it) }
        super.onCleared()
    }
}

/**
 * `paywall_shown.source` of the Analyze "Get Pro" CTA after a run was refused for credits (402).
 * A source of its own so the funnel can tell it from the chat's `chat_insufficient_credits`.
 */
internal const val PAYWALL_SOURCE_INSUFFICIENT_CREDITS = "analyze_insufficient_credits"

/** [AnalyzeScreenState.isAnalyzing] is derived from the phase — never latched on its own. */
internal fun AnalyzeScreenState.withPhase(phase: AnalyzeJobPhase): AnalyzeScreenState = copy(
    jobPhase = phase,
    isAnalyzing = phase is AnalyzeJobPhase.Waiting || phase is AnalyzeJobPhase.Delivering,
)
