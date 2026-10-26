package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.NavExperimentResolver
import com.antonchuraev.homesearchchecklist.core.common.api.NavVariant
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.NEW_CHECKLIST_SEPARATE_COMPLETED
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase.EnsureInboxUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_checklist_name
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_default_checklist_name
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class WelcomeOnboardingViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val navigator: AppNavigator,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val checklistRepository: ChecklistRepository,
    private val analyzeRepository: AnalyzeRepository,
    private val activationCoordinator: ActivationCoordinator,
    private val analyticsTracker: AnalyticsTracker,
    private val logger: AppLogger,
    // Branch 3 (empty input) depends on the shell: the v2 shell has a system Inbox to drop the
    // getting-started tips into, the classic (v1) shell does not — see [resolveFirstChecklist].
    private val navExperimentResolver: NavExperimentResolver,
    private val ensureInbox: EnsureInboxUseCase,
    private val isDebugBuild: Boolean = false,
    // Defaults to the production Compose Resources `getString`; overridden in tests where that
    // path is not resolvable on the JVM host (see [WelcomeStringResolver]).
    private val stringResolver: WelcomeStringResolver = WelcomeStringResolver { getString(it) },
) : AppViewModel<WelcomeOnboardingState, WelcomeOnboardingIntent, WelcomeOnboardingSideEffect>() {

    private val _screenState = MutableStateFlow(WelcomeOnboardingState())
    override val screenState: StateFlow<WelcomeOnboardingState> = _screenState.asStateFlow()

    // The base AppViewModel has no SideEffect channel yet (see its `todo add side effect`),
    // so expose a local one — the established project convention (MainScreenViewModel,
    // ChatViewModel). The Screen collects it to show snackbars / resolve string keys.
    private val _sideEffect = MutableSharedFlow<WelcomeOnboardingSideEffect>(extraBufferCapacity = 16)
    val sideEffect: Flow<WelcomeOnboardingSideEffect> = _sideEffect.asSharedFlow()

    // True once the getting-started tips are in the Inbox (both template and fill). Makes the
    // final-step retry idempotent — see [appendTipsToInbox]. Process-scoped on purpose: after process
    // death the VM is rebuilt and the onboarding flag is what decides whether this step re-runs.
    private var inboxTipsWritten = false

    init {
        if (!isDebugBuild) {
            val alreadyTracked = savedStateHandle.get<Boolean>(KEY_STARTED_TRACKED) == true
            if (!alreadyTracked) {
                analyticsTracker.event(
                    AnalyticsEvents.Onboarding.STARTED,
                    mapOf(AnalyticsParams.VARIANT to VARIANT),
                )
                savedStateHandle[KEY_STARTED_TRACKED] = true
            }
            // Always track creation for diagnostics (helps identify process death restores).
            analyticsTracker.event(
                AnalyticsEvents.Onboarding.VM_CREATED,
                mapOf(
                    AnalyticsParams.VARIANT to VARIANT,
                    "is_restored" to alreadyTracked.toString(),
                ),
            )
        }
    }

    override fun onIntent(intent: WelcomeOnboardingIntent) {
        when (intent) {
            WelcomeOnboardingIntent.OnNext -> handleNext()
            WelcomeOnboardingIntent.OnBack -> handleBack()
            WelcomeOnboardingIntent.OnSkip -> handleSkip()
            is WelcomeOnboardingIntent.OnInputChanged ->
                _screenState.update { it.copy(inputText = intent.text) }
            is WelcomeOnboardingIntent.OnTemplateSelected -> handleTemplateSelected(intent.key)
            WelcomeOnboardingIntent.OnCreateFirstChecklist -> handleCreateFirstChecklist()
            WelcomeOnboardingIntent.OnMoreWaysToStart -> handleMoreWaysToStart()
        }
    }

    private fun handleNext() {
        val current = _screenState.value.currentStep
        val nextStep = WelcomeOnboardingStep.entries.getOrNull(current.ordinal + 1)
        if (nextStep != null) {
            _screenState.update { it.copy(currentStep = nextStep) }
            trackStep(current.name)
        }
    }

    private fun handleBack() {
        val current = _screenState.value.currentStep
        val prevStep = WelcomeOnboardingStep.entries.getOrNull(current.ordinal - 1)
        // No-op on the first step (Welcome) — the back affordance is hidden there anyway.
        if (prevStep != null) {
            _screenState.update { it.copy(currentStep = prevStep) }
        }
    }

    private fun handleSkip() {
        if (!isDebugBuild) {
            analyticsTracker.event(
                AnalyticsEvents.Onboarding.SKIPPED,
                mapOf(
                    AnalyticsParams.VARIANT to VARIANT,
                    "step" to _screenState.value.currentStep.name,
                ),
            )
        }
        completeOnboarding(checklistCreated = false)
    }

    private fun handleTemplateSelected(key: String) {
        // Tapping a chip toggles selection; selecting a chip also clears any typed text so the
        // chip becomes the unambiguous seed (and re-tapping the same chip deselects it).
        _screenState.update {
            val newKey = if (it.selectedTemplateKey == key) null else key
            it.copy(
                selectedTemplateKey = newKey,
                inputText = if (newKey != null) "" else it.inputText,
            )
        }
    }

    /**
     * Final step. Resolves the user's intent and either creates a pre-seeded checklist (chip),
     * generates one from a free-text prompt via AI, or seeds the getting-started tips, then completes
     * onboarding and drops the user where that content lives (the activation "aha" moment). Three
     * branches:
     *  1. A starter chip is selected → create a checklist pre-filled from the chip's hardcoded
     *     preset items (NO AI — fast, no credit/network), land on its detail screen.
     *  2. No chip, but text was typed → generate a checklist from that text via AI *in place*
     *     (full-screen loading, no Analyze hand-off, no preview), create it, and land on its detail
     *     screen — see [generateFirstChecklistFromText].
     *  3. No chip, empty text → seed the default getting-started tips so the mandatory final step is
     *     never blank. Where they go depends on the shell:
     *     - v2 (default): appended as tasks to the system **Inbox**, then land on the main screen
     *       (which is the Inbox tab in v2) — see [appendTipsToInbox]. The tips are "how to use the
     *       app", not a themed list, so a separate "My Checklist" project was noise in Projects.
     *     - v1 (classic layout, opt-in): there is no Inbox in that shell, so it still creates a
     *       separate "My Checklist" from the tips and lands on its detail screen.
     *
     * Empty input never blocks the CTA: branch 3 keeps the step actionable (the whole point of
     * closing the no-checklist activation leak). Because the input field is hidden once a chip is
     * selected (see the Screen), the "chip + typed text" combination is unreachable from the UI —
     * the branches are mutually exclusive.
     *
     * The in-flight guard covers BOTH loading states: [WelcomeOnboardingState.isCreating] (the chip/
     * default seed path's CTA spinner) and [WelcomeOnboardingState.isGeneratingAi] (the full-screen
     * AI loader). Either being true means a create is already running.
     */
    private fun handleCreateFirstChecklist() {
        val state = _screenState.value
        if (state.isCreating || state.isGeneratingAi) return
        viewModelScope.launch {
            when (val action = resolveFirstChecklist()) {
                is FirstChecklistAction.Seed -> createSeededChecklist(action)
                is FirstChecklistAction.AiAnalyze -> generateFirstChecklistFromText(action.prompt)
                is FirstChecklistAction.InboxTips -> appendTipsToInbox(action)
            }
        }
    }

    /**
     * Branch 1, and branch 3 in the v1 shell: create a checklist from a hardcoded preset (chip) or the
     * default getting-started starter (empty input). No AI/network — uses the CTA spinner
     * ([WelcomeOnboardingState.isCreating]), then lands on the created checklist's detail screen.
     */
    private suspend fun createSeededChecklist(action: FirstChecklistAction.Seed) {
        _screenState.update { it.copy(isCreating = true, error = null) }
        try {
            // addChecklist creates the default fill automatically (same path as the splash auto-seed
            // and the interactive onboarding), so the starter items show up checked-off-able on the
            // detail screen we land on next.
            val checklistId = checklistRepository.addChecklist(
                Checklist(
                    name = action.name,
                    items = action.items,
                    separateCompleted = NEW_CHECKLIST_SEPARATE_COMPLETED,
                ),
            )
            trackCompleted(checklistCreated = true)
            completeOnboardingUseCase()
            // Land on the freshly created checklist with a cleared back stack so system back exits
            // to home, not back into onboarding.
            navigator.navigateToChecklistDetail(checklistId, clearBackStack = true)
        } catch (e: Exception) {
            logger.error(TAG, "seeded first checklist creation failed: ${e.message}", e)
            _screenState.update { it.copy(isCreating = false, error = ERROR_KEY) }
            _sideEffect.emit(WelcomeOnboardingSideEffect.ShowSnackbar(ERROR_KEY))
        }
    }

    /**
     * Branch 3 in the v2 shell: append the getting-started tips to the system Inbox as ordinary
     * tasks, then land on the main screen (the Inbox tab in v2). No checklist is created.
     *
     * Each tip is written as a template + fill PAIR — template item first so the fill row carries
     * its stable [ChecklistFillItem.templateItemId] from birth, and BOTH halves persisted (rule
     * `checklist-domain`: writing one half only is the template↔fill desync). Same shape as the
     * Inbox quick-add (`InboxViewModel.addTask`), minus due dates / reminders.
     *
     * Any failure (Inbox could not be ensured, row or default fill missing, a write threw) keeps the
     * user on the final step with the create-failure snackbar — never a silent no-op.
     */
    private suspend fun appendTipsToInbox(action: FirstChecklistAction.InboxTips) {
        _screenState.update { it.copy(isCreating = true, error = null) }
        try {
            // A retry after a failure LATER in this function (e.g. completeOnboardingUseCase) must
            // not append the tips a second time — ensureInbox returns the same id, so without this
            // latch the Inbox would end up with every tip twice.
            if (!inboxTipsWritten) {
                // EnsureInboxUseCase logs its own failure; null here means "no Inbox to write into".
                val inboxId = ensureInbox(stringResolver.resolve(Res.string.inbox_checklist_name))
                    ?: error("Inbox could not be ensured")
                val inbox = checklistRepository.getChecklistById(inboxId)
                    ?: error("Inbox checklist $inboxId not found")
                val fill = checklistRepository.getDefaultFillByChecklistId(inboxId).first()
                    ?: error("No default fill for Inbox $inboxId")

                val templateItems = action.texts.map { ChecklistItem(text = it) }
                val fillItems = templateItems.map { item ->
                    ChecklistFillItem(text = item.text, checked = false, templateItemId = item.id)
                }
                checklistRepository.updateFill(fill.copy(items = fill.items + fillItems))
                checklistRepository.updateChecklistTemplate(inbox.copy(items = inbox.items + templateItems))
                // Set only once BOTH halves landed. Known gap (out of scope): a template write that
                // fails after the fill write leaves the latch down, so a retry re-appends to the fill.
                inboxTipsWritten = true
            }

            // checklist_created=true: the funnel reads it as "left onboarding with starter content",
            // and the tips are that content exactly as v1's "My Checklist" is — false would show v2
            // as an activation drop. Checklist.CREATED is still NOT fired (the Inbox is a system
            // row, same as EnsureInboxUseCase). seed="inbox" separates this path from v1's "default".
            trackCompleted(checklistCreated = true, seedOverride = SEED_INBOX)
            completeOnboardingUseCase()
            // Main screen = Inbox tab in v2; cleared back stack so system back exits, not re-enters
            // onboarding.
            navigator.navigateToMainScreen(clearBackStack = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(TAG, "onboarding tips → Inbox failed: ${e.message}", e)
            _screenState.update { it.copy(isCreating = false, error = ERROR_KEY) }
            _sideEffect.emit(WelcomeOnboardingSideEffect.ShowSnackbar(ERROR_KEY))
        }
    }

    /**
     * Branch 2: generate the first checklist from the user's free text via AI, in place. Shows a
     * full-screen loader ([WelcomeOnboardingState.isGeneratingAi], which hides the top bar / progress
     * / footer so the user can't navigate away mid-generation), runs the AI generation + creation
     * itself, fires the activation funnel, then lands on the created checklist's detail screen.
     *
     * This replaces the old hand-off to the Analyze screen (generate → preview → manual confirm →
     * create). Because we skip the Analyze preview, we MUST reproduce what its confirm step did for
     * the activation funnel: hand the new checklist to [ActivationCoordinator.onAiChecklistCreated],
     * which fires `FIRST_AI_CHECKLIST_CREATED` and (in the treatment arm) drives the one-time reminder
     * opt-in soft-ask. That soft-ask is shown by the App shell (App.kt collects
     * `reminderOptInRequests`), so it surfaces over the detail screen we navigate to — no extra wiring
     * needed here.
     *
     * On any failure (generation or creation) we drop the loader, stay on the final step, log the
     * error, and show the create-failure snackbar — never a dead/blank screen.
     */
    private suspend fun generateFirstChecklistFromText(prompt: String) {
        _screenState.update { it.copy(isGeneratingAi = true, error = null) }
        try {
            val result = analyzeRepository.analyzeData(AnalyzeInputData.RawText(prompt))
                .getOrElse { e ->
                    failAiGeneration("AI generation failed", e)
                    return
                }

            // Prefer the AI-suggested name (the CF `checklist_name`) so the generated checklist keeps
            // its own title; fall back to the localized default when the server omitted it (mirrors
            // AnalyzeViewModel.analyzeInput).
            val name = result.suggestedName?.takeIf { it.isNotBlank() }
                ?: stringResolver.resolve(Res.string.onboarding_welcome_default_checklist_name)

            val checklist = analyzeRepository.createChecklistFromResult(name, result)
                .getOrElse { e ->
                    failAiGeneration("AI checklist creation failed", e)
                    return
                }

            // Reproduce the Analyze-preview confirm's activation side effects (we skipped the preview):
            // fire FIRST_AI_CHECKLIST_CREATED + drive the reminder opt-in via the coordinator. It owns
            // the new-user / show-once gating, so this is safe to always call.
            activationCoordinator.onAiChecklistCreated(checklist.id)

            // checklist_created=true: unlike the old hand-off (where the Analyze funnel fired its own
            // event on confirm), this branch now creates the checklist itself.
            trackCompleted(checklistCreated = true)
            completeOnboardingUseCase()
            // Keep the full-screen loader visible right up to navigation. clearBackStack tears this
            // screen (and VM) down, so the loader is replaced DIRECTLY by the checklist detail with no
            // intermediate frame. Do NOT reset isGeneratingAi here: that recomposes the final step for
            // one frame — the "preview flash" the user sees before the detail opens.
            navigator.navigateToChecklistDetail(checklist.id, clearBackStack = true)
        } catch (e: Exception) {
            failAiGeneration("AI first checklist flow failed", e)
        }
    }

    /** Common AI-generation failure path: log, drop the full-screen loader, surface the snackbar. */
    private suspend fun failAiGeneration(context: String, e: Throwable) {
        logger.error(TAG, "$context: ${e.message}", e)
        _screenState.update { it.copy(isGeneratingAi = false, error = ERROR_KEY) }
        _sideEffect.emit(WelcomeOnboardingSideEffect.ShowSnackbar(ERROR_KEY))
    }

    /**
     * "More ways to start" card on the final step. Opens the Analyze hub with all-default arguments
     * (no prompt, no auto-analyze) so the user picks the input type (Photo/PDF/voice/link) themselves.
     * Symmetric to the [FirstChecklistAction.AiAnalyze] branch: complete onboarding FIRST (the user
     * must not be able to return to onboarding from the Analyze result), then hand off. We do NOT
     * create a checklist here — the Analyze flow generates + previews + creates it via AI and fires
     * its own creation event, so `checklist_created=false`; seed="multimodal" distinguishes this
     * card-driven entry from the typed/chip/default seeds in the funnel analytics.
     */
    private fun handleMoreWaysToStart() {
        val state = _screenState.value
        if (state.isCreating || state.isGeneratingAi) return
        viewModelScope.launch {
            trackCompleted(checklistCreated = false, seedOverride = SEED_MULTIMODAL)
            completeOnboardingUseCase()
            navigator.navigateToAnalyzeScreen()
        }
    }

    private fun trackCompleted(checklistCreated: Boolean, seedOverride: String? = null) {
        if (isDebugBuild) return
        analyticsTracker.event(
            AnalyticsEvents.Onboarding.COMPLETED,
            mapOf(
                AnalyticsParams.VARIANT to VARIANT,
                "checklist_created" to checklistCreated.toString(),
                "seed" to (seedOverride ?: seedKind()),
            ),
        )
    }

    /**
     * Resolves what the final step should do across the three branches described on
     * [handleCreateFirstChecklist]. Resource keys come from the contract; text is resolved here via
     * the suspend `getString` (never in the domain layer).
     *
     * Branch 3 reads the shell via [NavExperimentResolver.ensureResolved] (not the non-suspending
     * `currentArm()`, which reports the v2 default before the stored choice is read — a v1 user
     * would then get tips written into an Inbox their shell never shows).
     */
    private suspend fun resolveFirstChecklist(): FirstChecklistAction {
        val state = _screenState.value
        val typed = state.inputText.trim()
        val template = WelcomeStarterTemplate.fromKey(state.selectedTemplateKey)
        return when {
            // 1. Chip selected — name + items from the chip's hardcoded preset (no AI).
            template != null -> FirstChecklistAction.Seed(
                name = stringResolver.resolve(template.checklistNameRes),
                items = template.itemRes.map { ChecklistItem(text = stringResolver.resolve(it)) },
            )
            // 2. No chip, text typed — hand the prompt to the AI Analyze flow.
            typed.isNotEmpty() -> FirstChecklistAction.AiAnalyze(prompt = typed)
            // 3. No chip, empty text — getting-started tips (never blank): into the Inbox in v2,
            //    as a separate default-named checklist in v1 (no Inbox there).
            else -> {
                val tips = WelcomeStarterTemplate.DEFAULT_STARTER_ITEMS.map { stringResolver.resolve(it) }
                if (navExperimentResolver.ensureResolved() == NavVariant.V2) {
                    FirstChecklistAction.InboxTips(texts = tips)
                } else {
                    FirstChecklistAction.Seed(
                        name = stringResolver.resolve(Res.string.onboarding_welcome_default_checklist_name),
                        items = tips.map { ChecklistItem(text = it) },
                    )
                }
            }
        }
    }

    private sealed interface FirstChecklistAction {
        /** Branch 1, and branch 3 in v1: create a pre-seeded checklist, then land on its detail screen. */
        data class Seed(
            val name: String,
            val items: List<ChecklistItem>,
        ) : FirstChecklistAction

        /** Branch 2: hand the free-text prompt to the AI Analyze flow (it creates the checklist). */
        data class AiAnalyze(val prompt: String) : FirstChecklistAction

        /** Branch 3 in v2: append the resolved tip texts to the system Inbox, then land on main. */
        data class InboxTips(val texts: List<String>) : FirstChecklistAction
    }

    private fun seedKind(): String = when {
        _screenState.value.selectedTemplateKey != null -> "chip"
        _screenState.value.inputText.isNotBlank() -> "typed"
        else -> "default"
    }

    private fun completeOnboarding(checklistCreated: Boolean) {
        viewModelScope.launch {
            if (!isDebugBuild) {
                analyticsTracker.event(
                    AnalyticsEvents.Onboarding.COMPLETED,
                    mapOf(
                        AnalyticsParams.VARIANT to VARIANT,
                        "checklist_created" to checklistCreated.toString(),
                    ),
                )
            }
            completeOnboardingUseCase()
            navigator.navigateToMainScreen(clearBackStack = true)
        }
    }

    private fun trackStep(stepName: String) {
        if (isDebugBuild) return
        analyticsTracker.event(
            AnalyticsEvents.Onboarding.STEP_COMPLETED,
            mapOf(AnalyticsParams.VARIANT to VARIANT, "step" to stepName),
        )
    }

    companion object {
        private const val TAG = "WelcomeOnboarding"
        private const val VARIANT = "ai_welcome"
        private const val KEY_STARTED_TRACKED = "onboarding_started_tracked"
        // Funnel "seed" value for the "More ways to start" card (Analyze hub); the typed/chip/default
        // seeds come from seedKind(), which reads state — this card path has neither chip nor text.
        private const val SEED_MULTIMODAL = "multimodal"
        // Funnel "seed" value for branch 3 in the v2 shell (tips appended to the Inbox); the v1 shell
        // keeps seedKind()'s "default".
        private const val SEED_INBOX = "inbox"
        // Resolved to onboarding_welcome_create_error in the Screen (string-key indirection so the
        // ViewModel never imports Compose Resources for snackbar text).
        const val ERROR_KEY = "onboarding_welcome_create_error"
    }
}
