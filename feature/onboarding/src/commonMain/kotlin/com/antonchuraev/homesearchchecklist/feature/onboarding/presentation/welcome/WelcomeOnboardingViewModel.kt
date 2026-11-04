package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_checklist_name
import aichecklists.core.designsystem.generated.resources.onboarding_sample_checklist_name
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_default_checklist_name
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AiEntryDestination
import com.antonchuraev.homesearchchecklist.core.common.api.AiEntrySource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.ChecklistSource
import com.antonchuraev.homesearchchecklist.core.common.api.NavExperimentResolver
import com.antonchuraev.homesearchchecklist.core.common.api.NavVariant
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.navigation.api.ShellLaunch
import com.antonchuraev.homesearchchecklist.core.navigation.api.ShellLaunchRequests
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.AnalyzeEventParams
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.NEW_CHECKLIST_SEPARATE_COMPLETED
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase.EnsureInboxUseCase
import com.antonchuraev.homesearchchecklist.feature.onboarding.isWebTarget
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The `ai_welcome` onboarding, "Showcase → Try it".
 *
 * Behaviour is specified in docs/designs/onboarding-refresh-2026-11-04/FEATURE_SPEC.md (state x event
 * matrix §2–3, finish branches §4, latches §6, analytics §7); the invariants it names (I1–I8) are
 * referenced below.
 *
 * Activity-scoped (Nav3 has no ViewModel entry decorator here), so every once-per-lifetime promise is
 * latched in [SavedStateHandle] and survives process death too.
 */
@Suppress("LongParameterList", "TooManyFunctions")
class WelcomeOnboardingViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val navigator: AppNavigator,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val checklistRepository: ChecklistRepository,
    private val analyzeRepository: AnalyzeRepository,
    private val activationCoordinator: ActivationCoordinator,
    private val analyticsTracker: AnalyticsTracker,
    private val logger: AppLogger,
    // Inbox tips go only into the v2 shell's system Inbox; the classic (v1) shell has none.
    private val navExperimentResolver: NavExperimentResolver,
    private val ensureInbox: EnsureInboxUseCase,
    // Chat dock and voice call live in App.kt, not behind a route (FEATURE_SPEC §5.1).
    private val shellLaunchRequests: ShellLaunchRequests,
    // Google-linked flag for the web "sign in to use AI" box (§5.3).
    private val userDataRepository: UserDataRepository,
    // Same expression as the Ask-pill call button, computed in App.kt (§5.4).
    isCallAvailable: Boolean = false,
    private val isDebugBuild: Boolean = false,
    // Source-set fact (OnboardingPlatform.kt), not a platform string compared to a mirrored constant.
    private val isWeb: Boolean = isWebTarget,
    // Defaults to the production Compose Resources `getString`; overridden in tests where that
    // path is not resolvable on the JVM host (see [WelcomeStringResolver]).
    private val stringResolver: WelcomeStringResolver = WelcomeStringResolver { getString(it) },
    private val sampleWriteTimeout: Duration = SAMPLE_WRITE_TIMEOUT,
    private val slowGenerationAfter: Duration = SLOW_GENERATION_AFTER,
) : AppViewModel<OnboardingUiState, OnboardingIntent, OnboardingSideEffect>() {

    private val _screenState = MutableStateFlow(
        OnboardingUiState(
            step = savedStateHandle.get<String>(KEY_STEP)
                ?.let { saved -> OnboardingStep.entries.firstOrNull { it.name == saved } }
                ?: OnboardingStep.Showcase,
            inputText = savedStateHandle.get<String>(KEY_INPUT).orEmpty(),
            isCallAvailable = isCallAvailable,
        ),
    )
    override val screenState: StateFlow<OnboardingUiState> = _screenState.asStateFlow()

    // The base AppViewModel has no SideEffect channel (see its `todo add side effect`), so expose a
    // local one — the project convention (MainScreenViewModel, ChatViewModel).
    private val _sideEffect = MutableSharedFlow<OnboardingSideEffect>(extraBufferCapacity = 16)
    val sideEffect: Flow<OnboardingSideEffect> = _sideEffect.asSharedFlow()

    // ── Sample run (§4.1): navigate once BOTH the write and the overlay animation are done. ──
    private var sampleChecklistId: Long? = null
    private var sampleAnimationFinished = false
    // A sample write still running after the 5 s timeout keeps going (its id lands in the I6 latch);
    // a second tap re-joins it instead of inserting again.
    private var sampleWrite: Deferred<Long>? = null

    private var slowGenerationJob: Job? = null

    init {
        val restored = savedStateHandle.get<Boolean>(KEY_STARTED_TRACKED) == true
        if (!restored) {
            track(AnalyticsEvents.Onboarding.STARTED)
            savedStateHandle[KEY_STARTED_TRACKED] = true
            trackStepViewed(_screenState.value.step)
        }
        // Every init, for diagnostics (tells process-death restores apart).
        track(AnalyticsEvents.Onboarding.VM_CREATED, mapOf("is_restored" to restored.toString()))
    }

    override fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            // Always allowed, even while busy (I1).
            is OnboardingIntent.OnInputChanged -> handleInputChanged(intent.text)
            OnboardingIntent.OnModeFocusConsumed -> _screenState.update { it.copy(pendingFocus = null) }
            OnboardingIntent.OnSampleAnimationFinished -> handleSampleAnimationFinished()
            else -> if (_screenState.value.isBusy) {
                // The visible feedback is the loader / disabled controls already on screen.
                logger.info(TAG, "ignored $intent while ${_screenState.value.phase}")
            } else {
                handleAction(intent)
            }
        }
    }

    private fun handleAction(intent: OnboardingIntent) {
        when (intent) {
            is OnboardingIntent.OnShowcaseTileClick -> goToTryIt(intent.mode)
            OnboardingIntent.OnShowcaseCtaClick -> goToTryIt(OnboardingMode.Default)
            OnboardingIntent.OnBack -> handleBack()
            OnboardingIntent.OnSkip -> handleSkip()
            OnboardingIntent.OnSendClick -> handleSend()
            is OnboardingIntent.OnSampleClick -> handleSample(intent.origin)
            is OnboardingIntent.OnOwnSourceClick -> handleOwnSource(intent.kind)
            is OnboardingIntent.OnStarterChipClick -> handleChip(intent.template)
            OnboardingIntent.OnSavePlainListClick -> handleSavePlainList()
            OnboardingIntent.OnChatClick -> handleChat()
            OnboardingIntent.OnCallClick -> handleCall()
            is OnboardingIntent.OnInputChanged,
            OnboardingIntent.OnModeFocusConsumed,
            OnboardingIntent.OnSampleAnimationFinished,
            -> Unit // routed in onIntent
        }
    }

    // ── Steps ────────────────────────────────────────────────────────────────

    private fun goToTryIt(mode: OnboardingMode) {
        if (_screenState.value.step != OnboardingStep.Showcase) {
            logger.info(TAG, "Showcase action $mode ignored: already on Try it")
            return
        }
        trackStepCompleted(OnboardingStep.Showcase)
        track(AnalyticsEvents.Onboarding.MODE_SELECTED, mapOf(AnalyticsParams.MODE to mode.wire))
        setStep(OnboardingStep.TryIt)
        _screenState.update { it.copy(pendingFocus = mode.takeIf { m -> m != OnboardingMode.Default }) }
    }

    private fun handleBack() {
        if (_screenState.value.step != OnboardingStep.TryIt) {
            logger.info(TAG, "back on Showcase is not handled by the screen")
            return
        }
        setStep(OnboardingStep.Showcase)
        // I3 / I4: the error box and the pending highlight belong to Try it.
        _screenState.update { it.copy(pendingFocus = null, aiError = null, showEmptyInputHint = false) }
    }

    private fun setStep(step: OnboardingStep) {
        _screenState.update { it.copy(step = step) }
        savedStateHandle[KEY_STEP] = step.name
        trackStepViewed(step)
    }

    private fun handleInputChanged(text: String) {
        _screenState.update { it.copy(inputText = text, showEmptyInputHint = false, aiError = null) }
        savedStateHandle[KEY_INPUT] = text
    }

    // ── §4.1 Sample ──────────────────────────────────────────────────────────

    private fun handleSample(origin: SampleOrigin) {
        track(AnalyticsEvents.Onboarding.SAMPLE_USED, mapOf(AnalyticsParams.ORIGIN to origin.wire))
        if (origin == SampleOrigin.AiError) trackFallback(FALLBACK_SAMPLE)
        // A failed sample run puts the AI-error box back (if it was up), like a failed plain list:
        // the box is still the way on, and the snackbar alone would leave Try it without it.
        val errorBefore = _screenState.value.aiError
        sampleChecklistId = null
        sampleAnimationFinished = false
        _screenState.update {
            it.copy(phase = OnboardingPhase.SampleRunning(SampleStage.Reading), aiError = null, showEmptyInputHint = false)
        }
        viewModelScope.launch {
            val write = sampleWrite?.takeIf { it.isActive }
                ?: viewModelScope.async {
                    createOnce(CREATED_KEY_SAMPLE, ChecklistSource.ONBOARDING_SAMPLE) { insertSample() }
                }.also { sampleWrite = it }
            val id = try {
                withTimeout(sampleWriteTimeout) { write.await() }
            } catch (e: TimeoutCancellationException) {
                failToIdle("sample write timed out after $sampleWriteTimeout", e, restoreAiError = errorBefore)
                return@launch
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failToIdle("sample write failed: ${e.message}", e, restoreAiError = errorBefore)
                return@launch
            }
            sampleChecklistId = id
            _screenState.update { it.copy(phase = OnboardingPhase.SampleRunning(SampleStage.Ready)) }
            maybeFinishSample()
        }
    }

    private fun handleSampleAnimationFinished() {
        if (_screenState.value.phase !is OnboardingPhase.SampleRunning) {
            logger.info(TAG, "sample animation finished outside a sample run: ${_screenState.value.phase}")
            return
        }
        sampleAnimationFinished = true
        maybeFinishSample()
    }

    /** Order-independent: whichever of "write done" and "animation done" comes second navigates. */
    private fun maybeFinishSample() {
        val phase = _screenState.value.phase
        val id = sampleChecklistId
        if (phase != OnboardingPhase.SampleRunning(SampleStage.Ready) || !sampleAnimationFinished || id == null) return
        _screenState.update { it.copy(phase = OnboardingPhase.Leaving(OnboardingSeed.Sample)) }
        viewModelScope.launch {
            finish(OnboardingSeed.Sample, checklistCreated = true) {
                navigator.navigateToChecklistDetail(id, clearBackStack = true)
            }
        }
    }

    private suspend fun insertSample(): Pair<Long, Int> {
        val items = SAMPLE_ITEMS.map { ChecklistItem(text = stringResolver.resolve(it)) }
        // No due dates, no reminders (owner decision): no permission prompt, no reminder allowance.
        val id = checklistRepository.addChecklist(
            Checklist(
                name = stringResolver.resolve(Res.string.onboarding_sample_checklist_name),
                items = items,
                separateCompleted = NEW_CHECKLIST_SEPARATE_COMPLETED,
            ),
        )
        return id to items.size
    }

    // ── §4.2 Own file ────────────────────────────────────────────────────────

    private fun handleOwnSource(kind: AnalyzeInputKind) {
        val seed = OnboardingSeed.OwnFile(kind)
        send(
            AnalyticsEvents.AiEntry.TAPPED,
            mapOf(
                AnalyticsParams.DESTINATION to AiEntryDestination.ANALYZE.wire,
                AnalyticsParams.SOURCE to AiEntrySource.ONBOARDING.wire,
                AnalyticsParams.INPUT_TYPE to kind.wire,
            ),
        )
        _screenState.update { it.copy(phase = OnboardingPhase.Leaving(seed)) }
        viewModelScope.launch {
            finish(seed, checklistCreated = false) {
                // navigateToAnalyzeWithInput only PUSHES: from here it would leave [Onboarding, Analyze]
                // and back from Analyze would land on this screen, frozen in Leaving. Collapse onto the
                // shell's home first, so back from Analyze → Inbox (same shape as the detail exits).
                navigator.navigateToMainScreen(clearBackStack = true)
                navigator.navigateToAnalyzeWithInput(kind, AiEntrySource.ONBOARDING)
            }
        }
    }

    // ── §4.3 Typed → AI ──────────────────────────────────────────────────────

    private fun handleSend() {
        val prompt = _screenState.value.inputText.trim()
        if (prompt.isEmpty()) {
            // Feedback = the hint under the field (the UI focuses the field). Never an AI call.
            logger.info(TAG, "send with an empty field: showing the hint")
            _screenState.update { it.copy(showEmptyInputHint = true) }
            return
        }
        _screenState.update {
            it.copy(phase = OnboardingPhase.AiGenerating(prompt), aiError = null, showEmptyInputHint = false)
        }
        slowGenerationJob?.cancel()
        slowGenerationJob = viewModelScope.launch {
            delay(slowGenerationAfter)
            _screenState.update { s ->
                val phase = s.phase
                if (phase is OnboardingPhase.AiGenerating) s.copy(phase = phase.copy(isSlow = true)) else s
            }
        }
        viewModelScope.launch { generateFromText(prompt) }
    }

    private suspend fun generateFromText(prompt: String) {
        // I6: a retry after a failed completion reuses the list — no second AI call, no credits. The
        // latch is keyed by the prompt: after an edit, Send asks for a DIFFERENT list, and reopening
        // the one made from the old text would answer a question the user no longer asks.
        val createdKey = "$CREATED_KEY_TYPED_PREFIX$prompt"
        createdIdFor(createdKey)?.let { id ->
            finish(OnboardingSeed.Typed, checklistCreated = true) { navigator.navigateToChecklistDetail(id, clearBackStack = true) }
            return
        }
        val inputType = AnalyzeInputKind.RAW_TEXT.wire
        val source = AiEntrySource.ONBOARDING.wire
        // surface = onboarding: the analysis runs in place, not on the Analyze screen. CSAT skips exactly
        // this signature (an Analyze screen opened from onboarding has source = onboarding, no surface).
        val surface = AnalyticsEvents.Analyze.SURFACE_ONBOARDING
        send(AnalyticsEvents.Analyze.STARTED, AnalyzeEventParams.started(inputType, source, surface))
        try {
            // A throwing repository (not a Result.failure) still closes the started → failed pair.
            val analysis = try {
                analyzeRepository.analyzeData(AnalyzeInputData.RawText(prompt))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            val result = analysis.getOrElse { e ->
                val reason = AiFailureReason.classify(e)
                send(AnalyticsEvents.Analyze.FAILED, AnalyzeEventParams.failed(inputType, source, reason, e, surface))
                failAi("AI generation failed: ${e.message}", e, reason)
                return
            }
            send(AnalyticsEvents.Analyze.COMPLETED, AnalyzeEventParams.completed(inputType, source, result, surface))

            // Prefer the AI-suggested name; the localized default when the server omitted it.
            val name = result.suggestedName?.takeIf { it.isNotBlank() }
                ?: stringResolver.resolve(Res.string.onboarding_welcome_default_checklist_name)
            val checklist = analyzeRepository.createChecklistFromResult(name, result).getOrElse { e ->
                // The analysis itself succeeded (ai_analyze_completed is out), so no ai_analyze_failed.
                failAi("AI checklist creation failed: ${e.message}", e, AiFailureReason.UNKNOWN)
                return
            }
            rememberCreated(createdKey, checklist.id)
            trackChecklistCreated(ChecklistSource.ONBOARDING_AI, checklist.items.size)
            // We skip the Analyze preview, so reproduce its activation side effect (FIRST_AI_CHECKLIST_CREATED
            // + reminder opt-in soft-ask). The coordinator owns its own gating.
            activationCoordinator.onAiChecklistCreated(checklist.id)
            // Keep the loader up through navigation (resetting it would flash Try it for one frame).
            finish(OnboardingSeed.Typed, checklistCreated = true) {
                navigator.navigateToChecklistDetail(checklist.id, clearBackStack = true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failAi("AI first checklist flow failed: ${e.message}", e, AiFailureReason.classify(e))
        }
    }

    /** No snackbar: the box under the field is the feedback, and it offers two AI-free ways on. */
    private suspend fun failAi(context: String, e: Throwable, reason: AiFailureReason) {
        logger.error(TAG, context, e)
        slowGenerationJob?.cancel()
        val kind = resolveAiError(reason, isWeb, isGoogleLinked())
        _screenState.update { it.copy(phase = OnboardingPhase.Idle, aiError = kind) }
        track(
            AnalyticsEvents.Onboarding.AI_ERROR_SHOWN,
            mapOf(AnalyticsParams.KIND to kind.wire, AnalyticsParams.FAILURE_REASON to reason.wireValue),
        )
    }

    private suspend fun isGoogleLinked(): Boolean = try {
        userDataRepository.getUserData().isGoogleLinked
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.warning(TAG, "Google-linked flag unreadable, assuming signed out: ${e.message}")
        false
    }

    // ── §4.4 Chip / §4.9 Plain list ──────────────────────────────────────────

    private fun handleChip(template: WelcomeStarterTemplate) {
        track(AnalyticsEvents.Onboarding.CHIP_TAPPED, mapOf(AnalyticsParams.KEY to template.key))
        createLocalAndOpen(
            seed = OnboardingSeed.Chip,
            createdKey = "$CREATED_KEY_CHIP_PREFIX${template.key}",
            source = ChecklistSource.ONBOARDING_CHIP,
            name = { stringResolver.resolve(template.checklistNameRes) },
            items = { template.itemRes.map { stringResolver.resolve(it) } },
        )
    }

    private fun handleSavePlainList() {
        val text = _screenState.value.inputText.trim()
        if (text.isEmpty()) {
            logger.info(TAG, "plain list with an empty field: showing the hint")
            _screenState.update { it.copy(showEmptyInputHint = true, aiError = null) }
            return
        }
        trackFallback(FALLBACK_PLAIN_LIST)
        createLocalAndOpen(
            seed = OnboardingSeed.PlainList,
            createdKey = CREATED_KEY_PLAIN_LIST,
            source = ChecklistSource.ONBOARDING_PLAIN,
            name = { plainListName(text) },
            items = { WelcomeStarterTemplate.DEFAULT_STARTER_ITEMS.map { stringResolver.resolve(it) } },
        )
    }

    /** Local, AI-free create → detail. Failure: back to Idle with a snackbar; the error box (if any) stays. */
    private fun createLocalAndOpen(
        seed: OnboardingSeed,
        createdKey: String,
        source: ChecklistSource,
        name: suspend () -> String,
        items: suspend () -> List<String>,
    ) {
        val errorBefore = _screenState.value.aiError
        _screenState.update { it.copy(phase = OnboardingPhase.Creating(seed), aiError = null, showEmptyInputHint = false) }
        viewModelScope.launch {
            val id = try {
                createOnce(createdKey, source) {
                    val checklistItems = items().map { ChecklistItem(text = it) }
                    checklistRepository.addChecklist(
                        Checklist(name = name(), items = checklistItems, separateCompleted = NEW_CHECKLIST_SEPARATE_COMPLETED),
                    ) to checklistItems.size
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failToIdle("onboarding ${seed.wire} create failed: ${e.message}", e, restoreAiError = errorBefore)
                return@launch
            }
            finish(seed, checklistCreated = true) { navigator.navigateToChecklistDetail(id, clearBackStack = true) }
        }
    }

    // ── §4.5 Chat / §4.6 Call / §4.7 Skip ────────────────────────────────────

    private fun handleChat() {
        val prefill = _screenState.value.inputText.trim().ifBlank { null }
        track(AnalyticsEvents.Onboarding.CHAT_TAPPED, mapOf(AnalyticsParams.PREFILLED to (prefill != null).toString()))
        leaveToMain(OnboardingSeed.Chat) { shellLaunchRequests.request(ShellLaunch.OpenChat(prefill)) }
    }

    private fun handleCall() {
        if (!_screenState.value.isCallAvailable) {
            // Not a UX path: the button is not rendered when the call is unavailable.
            logger.warning(TAG, "call tapped while the call is unavailable: ignored")
            return
        }
        track(AnalyticsEvents.Onboarding.CALL_TAPPED)
        leaveToMain(OnboardingSeed.Call) { shellLaunchRequests.request(ShellLaunch.StartVoiceCall) }
    }

    private fun handleSkip() {
        val step = _screenState.value.step
        track(
            AnalyticsEvents.Onboarding.SKIPPED,
            mapOf(AnalyticsParams.STEP to step.wire, AnalyticsParams.STEP_INDEX to step.index),
        )
        leaveToMain(OnboardingSeed.Skip) {}
    }

    /**
     * Skip / Chat / Call: tips into the Inbox (fail-open, §4.8), complete, then [beforeNavigate] (the
     * shell request — made only once completion succeeded, so a failed completion leaves nothing
     * pending) and main with a cleared stack.
     */
    private fun leaveToMain(seed: OnboardingSeed, beforeNavigate: () -> Unit) {
        _screenState.update { it.copy(phase = OnboardingPhase.Leaving(seed)) }
        viewModelScope.launch {
            val tips = appendTipsToInboxFailOpen()
            finish(seed, checklistCreated = tips) {
                beforeNavigate()
                navigator.navigateToMainScreen(clearBackStack = true)
            }
        }
    }

    /**
     * §4.8 (PR #69 kept): the getting-started tips as Inbox tasks, v2 shell only. Each tip is a
     * template + fill PAIR — template item first so the fill row carries its `templateItemId` from
     * birth, and BOTH halves persisted (rule `checklist-domain`). Returns whether the tips are there.
     *
     * Fail-open: Skip / Chat / Call must never trap the user on onboarding, so a failure logs a
     * warning with the reason and the user goes on to an Inbox without tips.
     */
    private suspend fun appendTipsToInboxFailOpen(): Boolean {
        if (savedStateHandle.get<Boolean>(KEY_INBOX_TIPS_WRITTEN) == true) return true // I8
        return try {
            // ensureResolved, not currentArm(): the latter reports the v2 default before the stored
            // choice is read, and a v1 user would get tips in an Inbox their shell never shows.
            if (navExperimentResolver.ensureResolved() != NavVariant.V2) return false
            val texts = WelcomeStarterTemplate.DEFAULT_STARTER_ITEMS.map { stringResolver.resolve(it) }
            // EnsureInboxUseCase logs its own failure; null = no Inbox to write into.
            val inboxId = ensureInbox(stringResolver.resolve(Res.string.inbox_checklist_name))
                ?: error("Inbox could not be ensured")
            val inbox = checklistRepository.getChecklistById(inboxId) ?: error("Inbox checklist $inboxId not found")
            val fill = checklistRepository.getDefaultFillByChecklistId(inboxId).first()
                ?: error("No default fill for Inbox $inboxId")
            val templateItems = texts.map { ChecklistItem(text = it) }
            val fillItems = templateItems.map { ChecklistFillItem(text = it.text, checked = false, templateItemId = it.id) }
            checklistRepository.updateFill(fill.copy(items = fill.items + fillItems))
            checklistRepository.updateChecklistTemplate(inbox.copy(items = inbox.items + templateItems))
            // Set only once BOTH halves landed (known gap: a template write failing after the fill
            // write leaves the latch down).
            savedStateHandle[KEY_INBOX_TIPS_WRITTEN] = true
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warning(TAG, "onboarding tips → Inbox failed, continuing without them: ${e.message}")
            false
        }
    }

    // ── Common tail ──────────────────────────────────────────────────────────

    /**
     * `finish(seed, target)` of FEATURE_SPEC §4: Leaving → `onboarding_completed` (once, I5) →
     * completeOnboardingUseCase (before any navigation, I7) → [navigate]. A failed completion is the
     * only way out of Leaving (I2): Idle + snackbar; the completion latch stays set, so a retry does
     * not resend the event.
     */
    private suspend fun finish(seed: OnboardingSeed, checklistCreated: Boolean, navigate: () -> Unit) {
        _screenState.update { it.copy(phase = OnboardingPhase.Leaving(seed)) }
        val exitStep = _screenState.value.step
        if (savedStateHandle.get<Boolean>(KEY_COMPLETED_TRACKED) != true) {
            savedStateHandle[KEY_COMPLETED_TRACKED] = true
            if (seed != OnboardingSeed.Skip) trackStepCompleted(exitStep)
            track(
                AnalyticsEvents.Onboarding.COMPLETED,
                mapOf(
                    AnalyticsParams.CHECKLIST_CREATED to checklistCreated.toString(),
                    AnalyticsParams.SEED to seed.wire,
                    AnalyticsParams.STEP_INDEX to exitStep.index,
                ),
            )
        }
        try {
            completeOnboardingUseCase()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failToIdle("completing onboarding failed: ${e.message}", e)
            return
        }
        navigate()
    }

    private suspend fun failToIdle(context: String, e: Throwable, restoreAiError: OnboardingAiError? = null) {
        logger.error(TAG, context, e)
        slowGenerationJob?.cancel()
        _screenState.update { it.copy(phase = OnboardingPhase.Idle, aiError = restoreAiError) }
        _sideEffect.emit(OnboardingSideEffect.ShowSnackbar(ERROR_KEY))
    }

    // ── I6: one insert per action per lifetime ──────────────────────────────

    private fun createdIdFor(createdKey: String): Long? =
        savedStateHandle.get<Long>(KEY_CREATED_CHECKLIST_ID)
            ?.takeIf { savedStateHandle.get<String>(KEY_CREATED_SEED) == createdKey }

    private fun rememberCreated(createdKey: String, id: Long) {
        savedStateHandle[KEY_CREATED_CHECKLIST_ID] = id
        savedStateHandle[KEY_CREATED_SEED] = createdKey
    }

    /** Runs [insert] unless this action already created its list; emits `checklist_created` on insert only. */
    private suspend fun createOnce(
        createdKey: String,
        source: ChecklistSource,
        insert: suspend () -> Pair<Long, Int>,
    ): Long {
        createdIdFor(createdKey)?.let { return it }
        val (id, itemCount) = insert()
        rememberCreated(createdKey, id)
        trackChecklistCreated(source, itemCount)
        return id
    }

    // ── Analytics (debug builds send nothing) ────────────────────────────────

    private fun send(name: String, params: Map<String, Any> = emptyMap()) {
        if (isDebugBuild) return
        analyticsTracker.event(name, params)
    }

    /** An onboarding event: always carries `variant`. */
    private fun track(name: String, params: Map<String, Any> = emptyMap()) =
        send(name, mapOf<String, Any>(AnalyticsParams.VARIANT to VARIANT) + params)

    private fun stepParams(step: OnboardingStep): Map<String, Any> = mapOf(
        AnalyticsParams.STEP to step.wire,
        AnalyticsParams.STEP_INDEX to step.index,
        AnalyticsParams.STEP_COUNT to OnboardingStep.count,
    )

    private fun trackStepViewed(step: OnboardingStep) = track(AnalyticsEvents.Onboarding.STEP_VIEWED, stepParams(step))

    private fun trackStepCompleted(step: OnboardingStep) = track(AnalyticsEvents.Onboarding.STEP_COMPLETED, stepParams(step))

    private fun trackFallback(action: String) =
        track(AnalyticsEvents.Onboarding.FALLBACK_ACTION, mapOf(AnalyticsParams.ACTION to action))

    private fun trackChecklistCreated(source: ChecklistSource, itemCount: Int) = send(
        AnalyticsEvents.Checklist.CREATED,
        mapOf(AnalyticsParams.SOURCE to source.wire, AnalyticsParams.ITEM_COUNT to itemCount),
    )

    companion object {
        private const val TAG = "WelcomeOnboarding"
        private const val VARIANT = "ai_welcome"

        private val SAMPLE_WRITE_TIMEOUT = 5.seconds
        private val SLOW_GENERATION_AFTER = 8.seconds

        private const val FALLBACK_SAMPLE = "sample"
        private const val FALLBACK_PLAIN_LIST = "plain_list"

        // SavedStateHandle keys (FEATURE_SPEC §6).
        private const val KEY_STARTED_TRACKED = "onboarding_started_tracked"
        private const val KEY_STEP = "onboarding_step"
        private const val KEY_INPUT = "onboarding_input"
        private const val KEY_CREATED_CHECKLIST_ID = "onboarding_created_checklist_id"
        private const val KEY_CREATED_SEED = "onboarding_created_seed"
        private const val KEY_COMPLETED_TRACKED = "onboarding_completed_tracked"
        private const val KEY_INBOX_TIPS_WRITTEN = "onboarding_inbox_tips_written"

        // Values of KEY_CREATED_SEED: the action that created the list (a chip carries its key, so a
        // retry with a DIFFERENT chip creates that chip's list instead of reopening the first one; the
        // typed goal carries its prompt for the same reason).
        private const val CREATED_KEY_SAMPLE = "sample"
        private const val CREATED_KEY_TYPED_PREFIX = "typed:"
        private const val CREATED_KEY_PLAIN_LIST = "plain_list"
        private const val CREATED_KEY_CHIP_PREFIX = "chip:"

        /** Resolved to `onboarding_welcome_create_error` in the Screen (the VM never resolves snackbar text). */
        const val ERROR_KEY = "onboarding_welcome_create_error"
    }
}
