package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.datastore.api.AiChatPreferencesRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher.ToolCallDispatcher
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatChoice
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceAction
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceOption
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.locale.ChatLocaleProvider
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentStepResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AiChatRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistItemContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatHistoryRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscriptionOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent.AgentToolCallMapper
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent.AgentToolResultSerializer
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview.ToolCallPreviewRenderer
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistNodeType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_choice_action_add
import aichecklists.core.designsystem.generated.resources.chat_choice_action_attach
import aichecklists.core.designsystem.generated.resources.chat_choice_action_complete
import aichecklists.core.designsystem.generated.resources.chat_choice_action_create
import aichecklists.core.designsystem.generated.resources.chat_choice_action_delete
import aichecklists.core.designsystem.generated.resources.chat_choice_action_move
import aichecklists.core.designsystem.generated.resources.chat_choice_action_set_reminder
import aichecklists.core.designsystem.generated.resources.chat_choice_add_default_list
import aichecklists.core.designsystem.generated.resources.chat_choice_add_to_list
import aichecklists.core.designsystem.generated.resources.chat_choice_apply_actions
import aichecklists.core.designsystem.generated.resources.chat_choice_attach
import aichecklists.core.designsystem.generated.resources.chat_choice_cancel
import aichecklists.core.designsystem.generated.resources.chat_choice_complete
import aichecklists.core.designsystem.generated.resources.chat_choice_create
import aichecklists.core.designsystem.generated.resources.chat_choice_create_from_file
import aichecklists.core.designsystem.generated.resources.chat_choice_delete
import aichecklists.core.designsystem.generated.resources.chat_choice_edit
import aichecklists.core.designsystem.generated.resources.chat_choice_execute_all
import aichecklists.core.designsystem.generated.resources.chat_choice_executing_add
import aichecklists.core.designsystem.generated.resources.chat_choice_executing_attach
import aichecklists.core.designsystem.generated.resources.chat_choice_executing_complete
import aichecklists.core.designsystem.generated.resources.chat_choice_executing_create
import aichecklists.core.designsystem.generated.resources.chat_choice_executing_default
import aichecklists.core.designsystem.generated.resources.chat_choice_executing_delete
import aichecklists.core.designsystem.generated.resources.chat_choice_executing_move
import aichecklists.core.designsystem.generated.resources.chat_choice_executing_set_reminder
import aichecklists.core.designsystem.generated.resources.chat_choice_move_reminders
import aichecklists.core.designsystem.generated.resources.chat_choice_other
import aichecklists.core.designsystem.generated.resources.chat_choice_set_reminder
import aichecklists.core.designsystem.generated.resources.chat_choice_which_list
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * ViewModel for the AI Chat screen.
 *
 * State machine (idle ↔ processing ↔ preview):
 *   1. idle: [ChatScreenState.isProcessing]=false, pendingChoice=null
 *   2. OnSendClick → blank check → classify intent → resolve to a choice block (write) or inline (read)
 *   3. OnChoiceSelected(Execute) → dispatch ToolCall → success message
 *   4. OnChoiceDismissed → back to idle, with a visible "cancelled" reply
 *
 * Phase C additions:
 *   - [ChatIntent.FreeForm] → [runAgentTurn] (Layer 3 via chat_agent CF)
 *   - History persistence via [chatHistoryRepository] (Room, survives restarts)
 *   - Checklist context built from [checklistRepository] for Layer 3 requests
 */
class ChatViewModel(
    private val aiChatRepository: AiChatRepository,
    private val toolCallDispatcher: ToolCallDispatcher,
    private val previewRenderer: ToolCallPreviewRenderer,
    private val localeProvider: ChatLocaleProvider,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val checklistRepository: ChecklistRepository,
    private val userDataRepository: UserDataRepository,
    private val aiChatPreferencesRepository: AiChatPreferencesRepository,
    private val analytics: AnalyticsTracker,
    private val logger: AppLogger,
) : AppViewModel<ChatScreenState, ChatScreenIntent, ChatScreenSideEffect>() {

    // Welcome message is rendered as a fixed UI affordance in ChatScreen
    // (via stringResource so it follows the system locale). ViewModel keeps
    // messages strictly as user-driven content — seeded from Room history on init.
    private val _screenState = MutableStateFlow(ChatScreenState())
    override val screenState: StateFlow<ChatScreenState> = _screenState

    private val _sideEffect = MutableSharedFlow<ChatScreenSideEffect>(extraBufferCapacity = 16)
    val sideEffect: Flow<ChatScreenSideEffect> = _sideEffect.asSharedFlow()

    /**
     * Pause/resume mechanism for the agent loop choice block.
     *
     * When the agent returns mutating tool calls, [runAgentTurn] sets this to a new
     * [CompletableDeferred] and suspends on [await()]. The choice handlers
     * ([handleChoiceSelected] for the ExecuteAll chip / [handleChoiceDismissed] for the
     * escape chip) complete it with true/false respectively. The loop resumes after the
     * user decides. Cleared to null at the start of each new turn so stale completions are ignored.
     */
    @Volatile
    private var _pendingAgentDecision: CompletableDeferred<Boolean>? = null

    /**
     * Escalation context for the currently-shown write-intent choice block. Captured when the
     * choice is built (write-intent preview path) so the FreeForm ("Something else") chip can
     * reproduce the old reject-escalation by source layer (Local → Classifier → FullChat).
     * Null when no write-intent choice is shown (e.g. agent batch / ambiguous match).
     */
    @Volatile
    private var _choiceSourceLayer: RoutingLayer? = null

    /**
     * Wall-clock millis captured when the user taps Send (start of a turn). Read back when the
     * assistant response lands to compute [AnalyticsParams.LATENCY_MS] for [AnalyticsEvents.Chat.RESPONSE_RECEIVED].
     * The chat processes one turn at a time (gated by [ChatScreenState.isProcessing]), so a single
     * field is sufficient — no per-message map needed. Null means "no in-flight turn to measure".
     */
    @Volatile
    private var _turnStartMs: Long? = null

    init {
        // Seed the message list from persisted history so the user sees
        // their previous conversation immediately on re-entry.
        //
        // This runs from the singleton ChatViewModel's init at app startup. On web
        // (wasmJs) the Room/OPFS Web Worker driver is often not ready yet at that moment,
        // so the first query throws. Previously that surfaced an error snackbar — which
        // (a) the user never triggered and cannot act on, and (b) fired before Compose
        // Resources (.cvr) finished loading, so stringResource() was still empty → an
        // empty black snackbar on every web open. It also left chat history unseeded,
        // because the seed is one-shot (no continuous re-observe). Fix: retry the query
        // with backoff until the DB is ready instead of toasting. Android resolves the
        // DB instantly, so this never retries there.
        viewModelScope.launch {
            runCatching {
                chatHistoryRepository.observeRecent(HISTORY_DISPLAY_LIMIT)
                    .retryWhen { cause, attempt ->
                        if (attempt < HISTORY_LOAD_MAX_RETRIES) {
                            logger.warning(
                                TAG,
                                "init: history load attempt ${attempt + 1} failed " +
                                    "(DB not ready?) — ${cause.message}",
                            )
                            delay(HISTORY_LOAD_RETRY_DELAY_MS)
                            true
                        } else {
                            false
                        }
                    }
                    .first()
                    .also { history ->
                        // Guard against clobbering live messages that may have arrived
                        // during the retry window.
                        if (history.isNotEmpty() && _screenState.value.messages.isEmpty()) {
                            _screenState.value = _screenState.value.copy(messages = history)
                            logger.debug(TAG, "init: loaded ${history.size} messages from history")
                        }
                    }
            }.onFailure { e ->
                // Retries exhausted (or a non-transient failure). Log only — never a
                // user-facing snackbar for a background startup seed the user can't act on.
                logger.error(TAG, "init: failed to load history after retries — ${e.message}", e)
            }
        }

        // Mirror the cached credit balance from UserDataRepository.
        // This is the canonical source (same pattern as MainScreenViewModel.aiCredits).
        // Server API responses (Layer 2 / Layer 3) also update state directly for instant
        // feedback; the next Firestore sync will reconcile any drift.
        viewModelScope.launch {
            userDataRepository.getUserDataFlow().collect { userData ->
                _screenState.value = _screenState.value.copy(
                    creditBalance = userData.aiCredits,
                    isPremium = userData.isPremium,
                )
            }
        }

        // Mirror persisted Deep Thinking preference so the settings sheet toggle
        // reflects the stored value on every screen entry.
        viewModelScope.launch {
            aiChatPreferencesRepository.deepThinkingEnabledFlow.collect { enabled ->
                _screenState.value = _screenState.value.copy(deepThinkingEnabled = enabled)
            }
        }
    }

    override fun onIntent(intent: ChatScreenIntent) {
        when (intent) {
            is ChatScreenIntent.OnInputChange -> {
                _screenState.value = _screenState.value.copy(inputText = intent.text)
            }

            is ChatScreenIntent.OnPrefillInput -> {
                // Programmatic prefill from a quick-action chip — set text, no send.
                _screenState.value = _screenState.value.copy(inputText = intent.text)
            }

            is ChatScreenIntent.OnPrefillAndSend -> {
                // Set text then dispatch in the same step (state updates are synchronous,
                // so handleSend() sees the freshly-set inputText).
                // forceAgent=true (checklist reasoning chips) bypasses classify() — see handleSend.
                _screenState.value = _screenState.value.copy(inputText = intent.text)
                handleSend(forceAgent = intent.forceAgent)
            }

            ChatScreenIntent.OnSendClick -> handleSend()

            is ChatScreenIntent.OnChoiceEditChange -> {
                val current = _screenState.value.pendingChoice ?: return
                _screenState.value = _screenState.value.copy(
                    pendingChoice = current.copy(editText = intent.text),
                )
            }

            is ChatScreenIntent.OnChoiceSelected -> handleChoiceSelected(intent.optionId)

            ChatScreenIntent.OnChoiceDismissed -> handleChoiceDismissed()

            ChatScreenIntent.OnChoiceEditConfirmed -> handleChoiceEditConfirmed()

            is ChatScreenIntent.AppendAssistantMessage -> {
                // ChatRoute resolved a localised messageKey and is round-tripping
                // the final text back so it lands in chat history with correct locale.
                // linkedChecklistId is preserved for the "Open checklist" button.
                // askAiForText is preserved for the "Ask AI" fallback button on Unknown responses.
                addAssistantMessage(
                    intent.text,
                    linkedChecklistId = intent.linkedChecklistId,
                    askAiForText = intent.askAiForText,
                )
            }

            ChatScreenIntent.OnHelpClick -> {
                _screenState.value = _screenState.value.copy(showPricingSheet = true)
            }

            ChatScreenIntent.OnHelpDismiss -> {
                _screenState.value = _screenState.value.copy(showPricingSheet = false)
            }

            ChatScreenIntent.OnFeaturesHelpClick -> {
                _screenState.value = _screenState.value.copy(showFeaturesSheet = true)
            }

            ChatScreenIntent.OnFeaturesHelpDismiss -> {
                _screenState.value = _screenState.value.copy(showFeaturesSheet = false)
            }

            ChatScreenIntent.OnBackClick -> {
                viewModelScope.launch { _sideEffect.emit(ChatScreenSideEffect.NavigateBack) }
            }

            ChatScreenIntent.OnSettingsClick -> {
                _screenState.value = _screenState.value.copy(showSettingsSheet = true)
            }

            ChatScreenIntent.OnSettingsDismiss -> {
                _screenState.value = _screenState.value.copy(showSettingsSheet = false)
            }

            is ChatScreenIntent.OnDeepThinkingToggle -> {
                // Persist to DataStore; the Flow collector in init {} will update state automatically.
                viewModelScope.launch {
                    aiChatPreferencesRepository.setDeepThinkingEnabled(intent.enabled)
                }
            }

            is ChatScreenIntent.OnClearChat -> {
                viewModelScope.launch {
                    chatHistoryRepository.clear()
                    _screenState.value = _screenState.value.copy(
                        messages = emptyList(),
                        showSettingsSheet = false,
                    )
                }
            }

            is ChatScreenIntent.OnOpenChecklist -> {
                viewModelScope.launch {
                    _sideEffect.emit(ChatScreenSideEffect.NavigateToChecklist(intent.checklistId))
                }
            }

            is ChatScreenIntent.OnAskAiFallback -> handleAskAiFallback(intent.text)

            // ── Attachment intents ────────────────────────────────────────────

            is ChatScreenIntent.OnPickAttachment -> {
                // Trigger-flag pattern (item-attachments solution): set the picker type so
                // the UI's LaunchedEffect can launch the correct platform picker.
                _screenState.value = _screenState.value.copy(
                    attachmentPickerType = intent.source,
                )
            }

            ChatScreenIntent.OnAttachmentPickerTriggered -> {
                // Reset trigger-flag after UI has consumed it. Prevents re-launch on recompose.
                _screenState.value = _screenState.value.copy(attachmentPickerType = null)
            }

            is ChatScreenIntent.OnAttachmentPicked -> handleAttachmentPicked(intent.attachment)

            is ChatScreenIntent.OnRemoveAttachment -> {
                _screenState.value = _screenState.value.copy(
                    pendingAttachments = _screenState.value.pendingAttachments
                        .filter { it.sourcePath != intent.sourcePath }
                )
            }

            ChatScreenIntent.OnVoiceRecordingStarted -> {
                // Phase 1: bookkeep state. Phase 3 will add AudioRecorder + permission check.
                // Emit RequestRecordAudioPermission so the UI can ask at the right moment.
                viewModelScope.launch {
                    _sideEffect.emit(ChatScreenSideEffect.RequestRecordAudioPermission)
                }
                _screenState.value = _screenState.value.copy(
                    isRecording = true,
                    voiceRecordingError = null,
                )
            }

            is ChatScreenIntent.OnVoiceRecordingStopped -> handleVoiceRecordingStopped(intent.recordingPath, intent.mimeType)

            is ChatScreenIntent.OnFeedbackOpen -> {
                // Opening the feedback sheet IS the thumb-down moment (the bubble's feedback icon
                // is the dislike affordance — there is no separate OnThumbDownClick intent). Track
                // THUMB_DOWN here; the FEEDBACK event fires later on Submit with the written text.
                val msg = intent.message
                analytics.event(
                    name = AnalyticsEvents.Chat.THUMB_DOWN,
                    params = mapOf(
                        AnalyticsParams.MESSAGE_ID to msg.id,
                        AnalyticsParams.ROUTED_LAYER to (msg.routedLayer?.name ?: "unknown"),
                        AnalyticsParams.DEEP_THINKING_ENABLED to _screenState.value.deepThinkingEnabled.toString(),
                    ),
                )
                logger.info(TAG, "THUMB_DOWN tracked: message_id=${msg.id}")
                _screenState.value = _screenState.value.copy(
                    feedbackTarget = intent.message,
                    feedbackText = "",
                )
            }

            is ChatScreenIntent.OnThumbUpClick -> {
                // Fire-and-forget positive feedback signal + lightweight snackbar
                // confirmation so the user sees that their tap was registered.
                val msg = intent.message
                analytics.event(
                    name = AnalyticsEvents.Chat.THUMB_UP,
                    params = mapOf(
                        AnalyticsParams.MESSAGE_ID to msg.id,
                        AnalyticsParams.ROUTED_LAYER to (msg.routedLayer?.name ?: "unknown"),
                        AnalyticsParams.DEEP_THINKING_ENABLED to _screenState.value.deepThinkingEnabled.toString(),
                    ),
                )
                logger.info(TAG, "THUMB_UP tracked: message_id=${msg.id}")
                viewModelScope.launch {
                    _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_thumb_up_thanks"))
                }
            }

            is ChatScreenIntent.OnFeedbackTextChange -> {
                _screenState.value = _screenState.value.copy(feedbackText = intent.text)
            }

            ChatScreenIntent.OnFeedbackDismiss -> {
                _screenState.value = _screenState.value.copy(
                    feedbackTarget = null,
                    feedbackText = "",
                    isSubmittingFeedback = false,
                )
            }

            ChatScreenIntent.OnFeedbackSubmit -> handleFeedbackSubmit()

            is ChatScreenIntent.OnSetContextChecklist -> {
                _screenState.value = _screenState.value.copy(
                    contextChecklistId = intent.checklistId,
                )
                logger.debug(TAG, "context checklist set: ${intent.checklistId}")
            }

            is ChatScreenIntent.OnChatOpened -> {
                // Fired once per open from the screen composition root (ChatRoute) or the
                // inline dock (App.kt) — NOT from init (the ViewModel is an App-scoped singleton,
                // so init runs once per process and would under-count opens).
                analytics.screenView(AnalyticsScreens.CHAT)
                analytics.event(
                    name = AnalyticsEvents.Chat.OPENED,
                    params = mapOf(AnalyticsParams.SOURCE to intent.source),
                )
                logger.debug(TAG, "chat opened: source=${intent.source}")
            }
        }
    }

    // ─── Feedback flow ────────────────────────────────────────────────────────

    private fun handleFeedbackSubmit() {
        val state = _screenState.value
        val target = state.feedbackTarget ?: return
        val feedbackText = state.feedbackText.trim()

        // Blank feedback is allowed — a bare thumb-down without comment is itself a valid
        // signal (user disliked the answer but didn't want to elaborate). The Submit button
        // in ChatFeedbackSheet is enabled regardless of text, so this handler must mirror
        // that — silently dropping the submit because of empty text breaks the UI contract.

        // Find the user question that preceded this assistant message in the history.
        val messages = state.messages
        val assistantIdx = messages.indexOfFirst { it.id == target.id }
        val precedingUser = messages
            .take(assistantIdx.coerceAtLeast(0))
            .lastOrNull { it.role == ChatRole.User }
        val question = precedingUser?.content ?: ""
        val answer = target.content

        _screenState.value = state.copy(isSubmittingFeedback = true)

        viewModelScope.launch {
            // Emit an Amplitude/Firebase event with the question/answer/feedback triplet
            // so the team can mine it offline (skill /ai-chat-feedback-fixer). A dedicated
            // per-feedback Cloud Function endpoint was intentionally NOT built — Amplitude
            // `ai_chat_feedback` already covers the knowledge-base goal (decision 2026-06-17).
            // Deep Thinking toggle state is included so analytics can correlate
            // feedback complaints with the toggle. Real-world: most "didn't parse"
            // feedbacks have been from users who left Deep Thinking ON between
            // sessions — segmenting by this flag lets us measure if the
            // command-override fix actually closes that segment.
            analytics.event(
                name = AnalyticsEvents.Chat.FEEDBACK,
                params = mapOf(
                    "question" to question,
                    "answer" to answer,
                    "feedback" to feedbackText,
                    AnalyticsParams.MESSAGE_ID to target.id,
                    AnalyticsParams.ROUTED_LAYER to (target.routedLayer?.name ?: "unknown"),
                    AnalyticsParams.DEEP_THINKING_ENABLED to state.deepThinkingEnabled.toString(),
                ),
            )
            logger.info(TAG, "FEEDBACK tracked: feedback_len=${feedbackText.length} message_id=${target.id}")
            _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_feedback_submitted"))
            _screenState.value = _screenState.value.copy(
                feedbackTarget = null,
                feedbackText = "",
                isSubmittingFeedback = false,
            )
        }
    }

    // ─── Response funnel analytics ──────────────────────────────────────────────

    /**
     * Tracks [AnalyticsEvents.Chat.RESPONSE_RECEIVED] — the bottom of the send funnel.
     *
     * Called from every terminal point of a turn (inline dispatch result, preview shown,
     * agent Final, agent error). [outcome] is one of:
     *  - "answer"  → a textual answer or inline dispatch result landed
     *  - "preview" → a write-intent preview / agent plan-card was shown for confirmation
     *  - "error"   → the turn failed (service/network/round-cap/insufficient-credits)
     *
     * Latency is measured from [_turnStartMs] (set in [handleSend]); null when there is no
     * in-flight turn (e.g. an "Ask AI" fallback opened without going through handleSend — then
     * the latency param is simply omitted). [_turnStartMs] is cleared so a late duplicate
     * terminal call doesn't emit a second response_received for the same turn.
     *
     * @param routedLayer The layer that produced the response; null → "unknown".
     */
    private fun trackResponseReceived(routedLayer: RoutingLayer?, outcome: String) {
        val params = mutableMapOf<String, Any>(
            AnalyticsParams.ROUTED_LAYER to (routedLayer?.name ?: "unknown"),
            AnalyticsParams.OUTCOME to outcome,
        )
        creditsForLayer(routedLayer)?.let { params[AnalyticsParams.CREDITS_USED] = it }
        _turnStartMs?.let { start -> params[AnalyticsParams.LATENCY_MS] = nowMillis() - start }
        _turnStartMs = null
        analytics.event(name = AnalyticsEvents.Chat.RESPONSE_RECEIVED, params = params)
    }

    /** Maps a routing layer to its credit cost (Layer 1 = 0, Layer 2 = 1, Layer 3 = 3). */
    private fun creditsForLayer(layer: RoutingLayer?): Int? = when (layer) {
        RoutingLayer.Local -> 0
        RoutingLayer.Classifier -> 1
        RoutingLayer.FullChat -> 3
        null -> null
    }

    /**
     * Tracks [AnalyticsEvents.Chat.PREVIEW_SHOWN] — top of the preview confirm funnel.
     * [AnalyticsParams.ACTION_TYPE] is the ToolCall's simple name (AddItem, DeleteItem,
     * CreateChecklist, …) so the funnel can be segmented by action kind.
     */
    private fun trackPreviewShown(toolCall: ToolCall) {
        analytics.event(
            name = AnalyticsEvents.Chat.PREVIEW_SHOWN,
            params = mapOf(AnalyticsParams.ACTION_TYPE to (toolCall::class.simpleName ?: "unknown")),
        )
    }

    // ─── Send flow ────────────────────────────────────────────────────────────

    /**
     * @param forceAgent When true, skip Layer 1/2 classification and route the message straight
     *                  to the reasoning agent ([runAgentTurn]). Used by the checklist-detail
     *                  reasoning chips (What's missing? / Summary / Add items) whose intent is
     *                  already a free-form question about the open checklist — classifying them
     *                  mis-routes to FindItems ("Nothing matches") or Unknown (Amplitude bug,
     *                  2026-06-02). The user message is still appended + persisted; the agent's
     *                  3-credit cost is attributed to the assistant reply (same as FreeForm).
     *                  forceAgent is ignored for attachment-only sends (those have a dedicated
     *                  CreateChecklistFromAttachment path that never reaches the agent).
     */
    private fun handleSend(forceAgent: Boolean = false) {
        val text = _screenState.value.inputText.trim()
        val attachments = _screenState.value.pendingAttachments

        // Blank guard (silent skip FORBIDDEN — CLAUDE.md rule).
        // With attachments, blank text is valid: user wants to Create checklist from file.
        if (text.isBlank() && attachments.isEmpty()) {
            viewModelScope.launch {
                _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_unknown_intent_hint"))
            }
            return
        }

        // Attachments only (no text) → CreateChecklistFromAttachment directly, skip classifier.
        if (text.isBlank() && attachments.isNotEmpty()) {
            handleSendAttachmentsOnly(attachments)
            return
        }

        // Append user message (with any pending attachments attached to it)
        val userMsg = ChatMessage(
            id = generateId(),
            role = ChatRole.User,
            content = text,
            timestamp = nowMillis(),
            costCredits = 0,
            routedLayer = null,
            attachments = attachments,
        )
        // Capture the pending attachments BEFORE clearing them off the state below. The
        // AttachToItem branch (in the when-block) needs this list to build its ToolCall —
        // reading _screenState.value.pendingAttachments there would see the cleared (empty)
        // field and wrongly emit a "no files" snackbar. (These are the same files attached to
        // userMsg.attachments above; we keep an explicit local for clarity.)
        val sentAttachments = attachments
        updateMessages { it + userMsg }
        _screenState.value = _screenState.value.copy(
            inputText = "",
            pendingAttachments = emptyList(),
            isProcessing = true,
        )

        // ── Funnel: message_sent (top of the send funnel) ──────────────────────
        // Fired once here, after the blank/attachments-only guards, so it counts only real
        // sends. Start the latency clock on the same line so response_received can measure it.
        // INPUT_METHOD is "text": the voice flow only fills the input field (transcription
        // merges into inputText) — the actual dispatch is always this text-send path, so we
        // cannot distinguish a voice-originated send here without a separate state flag.
        _turnStartMs = nowMillis()
        analytics.event(
            name = AnalyticsEvents.Chat.MESSAGE_SENT,
            params = buildMap {
                put(AnalyticsParams.DEEP_THINKING_ENABLED, _screenState.value.deepThinkingEnabled.toString())
                put(AnalyticsParams.HAS_CONTEXT_CHECKLIST, (_screenState.value.contextChecklistId != null).toString())
                put(AnalyticsParams.INPUT_METHOD, "text")
                put(AnalyticsParams.CHAR_LEN, text.length)
            },
        )

        // ── forceAgent fast-path: skip classify() and the when-block entirely ──
        // The reasoning chips already know the intent is a free-form question, so any
        // classification is at best wasteful and at worst harmful (Layer 1/2 mis-tag these
        // as FindItems → "Nothing matches"). Tag the user message as FullChat (cost 0; the
        // 3-credit charge lands on the assistant reply) and go straight to the agent loop.
        if (forceAgent) {
            val taggedUserMsg = userMsg.copy(routedLayer = RoutingLayer.FullChat, costCredits = 0)
            updateMessage(userMsg.id) { taggedUserMsg }
            viewModelScope.launch {
                runCatching {
                    withContext(NonCancellable) { chatHistoryRepository.append(taggedUserMsg) }
                    val locale = localeProvider.current()
                    runAgentTurn(text, locale)
                }.onFailure { e ->
                    logger.error(TAG, "handleSend(forceAgent) failed", e)
                    _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_generic_error"))
                    trackResponseReceived(RoutingLayer.FullChat, outcome = "error")
                    _screenState.value = _screenState.value.copy(isProcessing = false)
                }
            }
            return
        }

        viewModelScope.launch {
            runCatching {
                val locale = localeProvider.current()
                val classification = aiChatRepository.classify(text, locale)
                logger.debug(TAG, "Classified '${text.take(40)}' → ${classification.intent::class.simpleName} conf=${classification.confidence}")

                // Update user message with routing metadata + cost, then persist it.
                // Cost is known only after classification — appended in a single .copy() to
                // avoid double recompose.
                val userCost = when (classification.layer) {
                    RoutingLayer.Classifier -> 1   // Layer 2: classify_chat_intent costs 1 credit
                    else -> 0                       // Layer 1 (local) is free; unknown → 0
                }
                val taggedUserMsg = userMsg.copy(
                    routedLayer = classification.layer,
                    costCredits = userCost,
                )
                updateMessage(userMsg.id) { taggedUserMsg }
                // Persist with NonCancellable so a back-nav during classify/dispatch
                // doesn't drop the user message between state.messages and Room.
                withContext(NonCancellable) { chatHistoryRepository.append(taggedUserMsg) }

                when (val intent = classification.intent) {
                    is ChatIntent.Unknown -> {
                        // Emit the Unknown hint WITH the original text so ChatRoute can
                        // round-trip it as askAiForText on the AppendAssistantMessage intent.
                        // The "Ask AI" button in ChatMessageBubble lets the user opt-in to
                        // Layer 3 (3 credits) without auto-burning on gibberish.
                        _sideEffect.emit(
                            ChatScreenSideEffect.ShowAssistantMessage(
                                messageKey = "chat_unknown_intent_hint",
                                askAiForText = text,
                            )
                        )
                        trackResponseReceived(classification.layer, outcome = "answer")
                        _screenState.value = _screenState.value.copy(isProcessing = false)
                    }

                    // Layer 3 — open-ended conversation, no tool call preview.
                    // Always routed through the stateless agent loop (chat_agent CF).
                    ChatIntent.FreeForm -> {
                        runAgentTurn(text, locale)
                    }

                    // Read intent — dispatch inline, no preview
                    ChatIntent.FindItems -> {
                        val query = extractQuery(text)
                        val outcome = toolCallDispatcher.dispatch(ToolCall.FindItemsQuery(query))
                        handleOutcomeInline(outcome)
                        trackResponseReceived(classification.layer, outcome = "answer")
                        _screenState.value = _screenState.value.copy(isProcessing = false)
                    }

                    // Write intents — show preview card
                    ChatIntent.CreateItem,
                    ChatIntent.DeleteItem,
                    ChatIntent.CompleteItem,
                    is ChatIntent.CreateChecklist,
                    ChatIntent.SetReminder,
                    ChatIntent.MoveReminders -> {
                        val builtToolCall = classification.preBuiltToolCall ?: buildToolCall(intent, text, locale)
                        if (builtToolCall == null) {
                            // Entity extraction failed (e.g. "remind me tomorrow" without item context).
                            // Escalate to Layer 3 (full chat) which has conversation history and can
                            // understand what the user is referring to from previous messages.
                            logger.info(TAG, "ToolCall null for ${intent::class.simpleName} — escalating to Layer 3 (agent)")
                            runAgentTurn(text, locale)
                            return@runCatching
                        }
                        // P5: bias list-less commands to the currently-open checklist. Resolved to
                        // the checklist name (not id) so it flows through the dispatcher's existing
                        // hint → name-match path. Explicit hints are preserved (user choice wins).
                        val toolCall = biasToolCallToContext(builtToolCall)
                        // Generic-target "add to a checklist" with no resolvable list AND no open
                        // checklist (hint still null after context-bias) → ask "which list?" up front
                        // when 2+ lists exist (ambiguous). With 0 or 1 list there is nothing to pick,
                        // so fall through — the dispatcher resolves a null hint to the single/none list.
                        // Covers both single (AddItem) and multi (AddItems) adds; withHint() handles both.
                        val hintlessAdd = (toolCall is ToolCall.AddItem && toolCall.checklistHint == null) ||
                            (toolCall is ToolCall.AddItems && toolCall.checklistHint == null)
                        if (hintlessAdd) {
                            val names = runCatching {
                                checklistRepository.checklists.first().map { it.name }
                            }.getOrDefault(emptyList())
                            if (names.size >= 2) {
                                showWhichListChoice(toolCall, names, sourceLayer = classification.layer)
                                return@runCatching
                            }
                        }
                        showWriteChoice(toolCall, originalText = text, sourceLayer = classification.layer)
                    }

                    // AttachToItem — show preview only if attachments are present;
                    // otherwise emit a snackbar (silent-skip is forbidden).
                    is ChatIntent.AttachToItem -> {
                        // Use the attachments captured at send time (sentAttachments), NOT the
                        // live pendingAttachments — the latter was cleared above before this branch.
                        val currentAttachments = sentAttachments
                        if (currentAttachments.isEmpty()) {
                            _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_attach_no_files"))
                            trackResponseReceived(classification.layer, outcome = "answer")
                            _screenState.value = _screenState.value.copy(isProcessing = false)
                            return@runCatching
                        }
                        // "attach this" with no real target item ("this"/"это"/blank) → the user just
                        // wants the file turned into a checklist, not pinned to a named item → build a
                        // CreateChecklistFromAttachment. A real item name ("attach this to milk") is NOT
                        // referential → stays an AttachToItem.
                        val builtToolCall = if (isReferentialAttachTarget(intent.itemText, locale)) {
                            ToolCall.CreateChecklistFromAttachment(attachments = currentAttachments)
                        } else {
                            ToolCall.AttachToItem(
                                checklistHint = intent.checklistHint,
                                itemText = intent.itemText,
                                attachments = currentAttachments,
                            )
                        }
                        // P5: bias to the open checklist when the user didn't name a list.
                        val toolCall = biasToolCallToContext(builtToolCall)
                        showWriteChoice(toolCall, originalText = text, sourceLayer = classification.layer)
                    }
                }
            }.onFailure { e ->
                logger.error(TAG, "handleSend failed", e)
                _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_generic_error"))
                trackResponseReceived(routedLayer = null, outcome = "error")
                _screenState.value = _screenState.value.copy(isProcessing = false)
            }
        }
    }

    // ─── Choice block — build (write-intent path) ─────────────────────────────

    /**
     * Builds and shows an [AiChoiceResponse] for a single write-intent [toolCall] (the old
     * write-intent preview card). Caller must be in a suspend context (getString).
     *
     * Options:
     *  - Primary [ChoiceAction.Execute] (Add / Delete / Create / …; Destructive role for delete).
     *  - Default [ChoiceAction.Edit] (only when the tool call has editable text).
     *  - Escape: for CreateChecklistFromAttachment → [ChoiceAction.Dismiss] (no original text to
     *    re-classify). Otherwise → [ChoiceAction.FreeForm] ("Something else") which reproduces the
     *    old reject-escalation by [sourceLayer]; a separate Dismiss cancel is not added for
     *    destructive intents (Dismiss IS the safe option) and is folded into FreeForm otherwise.
     */
    private suspend fun showWriteChoice(toolCall: ToolCall, originalText: String, sourceLayer: RoutingLayer) {
        val isDelete = toolCall is ToolCall.DeleteItem
        val editable = extractItemText(toolCall).isNotBlank()
        val isFromAttachment = toolCall is ToolCall.CreateChecklistFromAttachment

        val options = buildList {
            add(
                ChoiceOption(
                    id = CHOICE_EXECUTE,
                    label = choiceString(toolCall.primaryActionLabel()),
                    role = if (isDelete) ChoiceRole.Destructive else ChoiceRole.Primary,
                    action = ChoiceAction.Execute(toolCall),
                ),
            )
            if (editable) {
                add(
                    ChoiceOption(
                        id = CHOICE_EDIT,
                        label = choiceString(Res.string.chat_choice_edit),
                        role = ChoiceRole.Default,
                        action = ChoiceAction.Edit,
                    ),
                )
            }
        }

        val escape = if (isFromAttachment) {
            ChoiceOption(
                id = CHOICE_ESCAPE,
                label = choiceString(Res.string.chat_choice_cancel),
                role = ChoiceRole.Escape,
                action = ChoiceAction.Dismiss,
            )
        } else {
            ChoiceOption(
                id = CHOICE_ESCAPE,
                label = choiceString(Res.string.chat_choice_other),
                role = ChoiceRole.Escape,
                action = ChoiceAction.FreeForm(originalText),
            )
        }

        val prompt = choiceString(toolCall.promptRes(), *toolCall.promptArgs())
        _choiceSourceLayer = sourceLayer
        _screenState.value = _screenState.value.copy(
            pendingChoice = PendingChoice(
                choice = ChatChoice(prompt = prompt, options = options, escape = escape),
            ),
            isProcessing = false,
        )
        trackPreviewShown(toolCall)
        trackResponseReceived(sourceLayer, outcome = "preview")
    }

    /**
     * Shows a "Which list?" choice: one Default Execute chip per candidate list (each re-runs
     * [sourceToolCall] with that list's name as the hint), plus a Dismiss escape.
     *
     * Used both up-front (a generic "add to a checklist" with 2+ lists, no resolvable target)
     * and post-dispatch (an [DispatchOutcome.AmbiguousMatch] where a hint matched several lists).
     * Candidates whose tool call carries no per-list hint are skipped (see [ToolCall.withHint]).
     */
    private suspend fun showWhichListChoice(
        sourceToolCall: ToolCall,
        names: List<String>,
        sourceLayer: RoutingLayer?,
    ) {
        val options = names.take(MAX_CHOICE_OPTIONS).mapIndexedNotNull { index, name ->
            val tc = sourceToolCall.withHint(name) ?: return@mapIndexedNotNull null
            ChoiceOption(
                id = "$CHOICE_CANDIDATE_PREFIX$index",
                label = name,
                role = ChoiceRole.Default,
                action = ChoiceAction.Execute(tc),
            )
        }
        val escape = ChoiceOption(
            id = CHOICE_ESCAPE,
            label = choiceString(Res.string.chat_choice_cancel),
            role = ChoiceRole.Escape,
            action = ChoiceAction.Dismiss,
        )
        _choiceSourceLayer = sourceLayer
        _screenState.value = _screenState.value.copy(
            pendingChoice = PendingChoice(
                choice = ChatChoice(
                    prompt = choiceString(Res.string.chat_choice_which_list),
                    options = options,
                    escape = escape,
                ),
            ),
            isProcessing = false,
        )
        // PREVIEW_SHOWN keeps the shown->confirmed funnel intact for the which-list picker: a chip
        // tap fires PREVIEW_CONFIRMED via executeChoice, so without this the confirms are orphaned.
        trackPreviewShown(sourceToolCall)
        if (sourceLayer != null) trackResponseReceived(sourceLayer, outcome = "preview")
    }

    // ─── Choice block — handlers ──────────────────────────────────────────────

    /** Resolves the tapped chip id to its [ChoiceAction] and runs it. */
    private fun handleChoiceSelected(optionId: String) {
        val pending = _screenState.value.pendingChoice ?: return
        // Block double-taps while a chip is already executing.
        if (pending.executingId != null) return
        val option = pending.choice.options.firstOrNull { it.id == optionId }
            ?: pending.choice.escape?.takeIf { it.id == optionId }
            ?: return

        when (val action = option.action) {
            is ChoiceAction.Execute -> executeChoice(option, action.toolCall)
            ChoiceAction.ExecuteAll -> {
                analytics.event(
                    name = AnalyticsEvents.Chat.PREVIEW_CONFIRMED,
                    params = mapOf(AnalyticsParams.ACTION_TYPE to "agent_plan"),
                )
                // Resume the suspended agent loop (runAgentTurn clears pendingChoice itself).
                _pendingAgentDecision?.complete(true)
            }
            is ChoiceAction.FreeForm -> {
                analytics.event(
                    name = AnalyticsEvents.Chat.PREVIEW_REJECTED,
                    params = mapOf(
                        AnalyticsParams.ACTION_TYPE to "freeform",
                        AnalyticsParams.ROUTED_LAYER to (_choiceSourceLayer?.name ?: "unknown"),
                    ),
                )
                escalateChoice(action.text)
            }
            is ChoiceAction.SendMessage -> {
                analytics.event(
                    name = AnalyticsEvents.Chat.PREVIEW_CONFIRMED,
                    params = mapOf(AnalyticsParams.ACTION_TYPE to "option"),
                )
                sendOptionAsTurn(option, action.text)
            }
            ChoiceAction.Edit -> {
                // Open the inline edit field seeded with the current editable text.
                val seed = (pending.choice.options
                    .firstOrNull { it.action is ChoiceAction.Execute }
                    ?.action as? ChoiceAction.Execute)
                    ?.let { extractItemText(it.toolCall) }
                    ?: ""
                _screenState.value = _screenState.value.copy(
                    pendingChoice = pending.copy(editText = seed),
                )
            }
            ChoiceAction.Dismiss -> handleChoiceDismissed()
        }
    }

    /** Dispatches a single Execute choice (the old Apply path), with a per-chip loading state. */
    private fun executeChoice(option: ChoiceOption, toolCall: ToolCall) {
        analytics.event(
            name = AnalyticsEvents.Chat.PREVIEW_CONFIRMED,
            params = mapOf(AnalyticsParams.ACTION_TYPE to (toolCall::class.simpleName ?: "unknown")),
        )
        viewModelScope.launch {
            // Mark the chip loading (whole block goes non-interactive in the UI).
            val loadingLabel = choiceString(toolCall.executingLabel())
            _screenState.value.pendingChoice?.let { current ->
                _screenState.value = _screenState.value.copy(
                    pendingChoice = current.copy(executingId = option.id, executingLabel = loadingLabel),
                )
            }
            runCatching {
                val outcome = toolCallDispatcher.dispatch(toolCall)
                // Clear first; handleOutcomeInline may set a NEW choice (AmbiguousMatch → "Which list?").
                clearChoice()
                handleOutcomeInline(outcome, sourceToolCall = toolCall)
            }.onFailure { e ->
                logger.error(TAG, "executeChoice failed", e)
                clearChoice()
                _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_apply_error"))
            }
        }
    }

    /**
     * User tapped an AI-generated answer option. Sends the chip's [text] as a fresh agent turn
     * (forceAgent semantics). The label text is NOT re-classified — it goes straight to the agent.
     *
     * History order [...assistant: question][user: label][assistant: answer]: the options question
     * lives only inside the choice block until now, so we persist it as an assistant message HERE
     * (before the user label) so it (a) stays visible in history and (b) is in the next turn's
     * transcript context. Credits were already charged server-side — costCredits=3 is display-only.
     */
    private fun sendOptionAsTurn(option: ChoiceOption, text: String) {
        // Capture the question prompt before clearing the choice block.
        val questionPrompt = _screenState.value.pendingChoice?.choice?.prompt?.takeIf { it.isNotBlank() }
        // Visible feedback: mark the tapped chip loading.
        _screenState.value.pendingChoice?.let { current ->
            _screenState.value = _screenState.value.copy(
                pendingChoice = current.copy(executingId = option.id),
            )
        }
        val userMsg = ChatMessage(
            id = generateId(),
            role = ChatRole.User,
            content = text,
            timestamp = nowMillis(),
            costCredits = 0,
            routedLayer = RoutingLayer.FullChat,
        )
        clearChoice()
        _screenState.value = _screenState.value.copy(isProcessing = true)
        _turnStartMs = nowMillis()

        viewModelScope.launch {
            runCatching {
                // Persist the question first (assistant), then the tapped label (user) — preserves
                // [assistant: question][user: label] order in both in-memory state and Room.
                if (questionPrompt != null) {
                    addAndPersistAssistantMessage(
                        content = questionPrompt,
                        routedLayer = RoutingLayer.FullChat,
                        costCredits = 3,
                    )
                }
                updateMessages { it + userMsg }
                withContext(NonCancellable) { chatHistoryRepository.append(userMsg) }
                val locale = localeProvider.current()
                runAgentTurn(text, locale)
            }.onFailure { e ->
                logger.error(TAG, "sendOptionAsTurn failed", e)
                _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_generic_error"))
                trackResponseReceived(RoutingLayer.FullChat, outcome = "error")
                _screenState.value = _screenState.value.copy(isProcessing = false)
            }
        }
    }

    /** User confirmed the inline edit — apply the edited text to the Execute tool call and dispatch. */
    private fun handleChoiceEditConfirmed() {
        val pending = _screenState.value.pendingChoice ?: return
        val executeOption = pending.choice.options.firstOrNull { it.action is ChoiceAction.Execute }
        val baseToolCall = (executeOption?.action as? ChoiceAction.Execute)?.toolCall ?: return
        val edited = pending.editText?.trim().orEmpty()

        // Silent-skip guard (CLAUDE.md): a blank edit must not drop quietly.
        if (edited.isEmpty()) {
            viewModelScope.launch {
                _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_choice_edit_empty_hint"))
            }
            return
        }

        val finalToolCall = applyEditedText(baseToolCall, edited)
        // Close the edit field, then dispatch via the standard execute path.
        _screenState.value = _screenState.value.copy(pendingChoice = pending.copy(editText = null))
        executeChoice(executeOption, finalToolCall)
    }

    /**
     * User dismissed the choice (escape chip / back). Clears it with a visible response.
     * For an agent-batch choice this also resolves the suspended agent decision with `false`.
     * For an AI-options choice the question only lived inside the choice block, so we persist it
     * as an assistant message BEFORE clearing — the question stays visible in history and no extra
     * "cancelled" reply is needed.
     */
    private fun handleChoiceDismissed() {
        val pending = _screenState.value.pendingChoice ?: return
        if (pending.executingId != null) return

        val isAgentBatch = pending.batchItems != null
        // AI-options choice: chips are SendMessage (a fresh turn), not a write-intent confirm.
        val isOptions = pending.choice.options.any { it.action is ChoiceAction.SendMessage }
        analytics.event(
            name = AnalyticsEvents.Chat.PREVIEW_REJECTED,
            params = mapOf(
                AnalyticsParams.ACTION_TYPE to when {
                    isAgentBatch -> "agent_plan"
                    isOptions -> "options"
                    else -> "dismiss"
                },
                // Layer the cancelled surface came from, so a which-list cancel is segmentable
                // from a write-preview cancel (both otherwise log action_type="dismiss").
                AnalyticsParams.ROUTED_LAYER to (_choiceSourceLayer?.name ?: "unknown"),
            ),
        )

        if (isAgentBatch) {
            // The agent loop owns clearing pendingChoice after the deferred resolves (it sends
            // declined results then continues). Just resolve the decision with "declined".
            _pendingAgentDecision?.complete(false)
            return
        }

        if (isOptions) {
            // Persist the question (it only lived inside the choice block) so it stays visible,
            // then clear — no extra "cancelled" reply (the question itself is the visible response).
            val questionPrompt = pending.choice.prompt.takeIf { it.isNotBlank() }
            clearChoice()
            if (questionPrompt != null) {
                viewModelScope.launch {
                    addAndPersistAssistantMessage(
                        content = questionPrompt,
                        routedLayer = RoutingLayer.FullChat,
                        costCredits = 3,
                    )
                }
            }
            return
        }

        clearChoice()
        // Write-intent dismiss must reply (silent dismiss FORBIDDEN — CLAUDE.md).
        viewModelScope.launch {
            _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_choice_dismissed_message"))
        }
    }

    /** Clears the pending choice + its escalation context. */
    private fun clearChoice() {
        _choiceSourceLayer = null
        _screenState.value = _screenState.value.copy(pendingChoice = null)
    }

    // ─── Choice escalation flow ("Something else") ────────────────────────────

    /**
     * User tapped "Something else" — re-classify the original input in the next pipeline layer.
     * Reproduces the old reject-escalation by [_choiceSourceLayer]:
     * - Local → re-classify with skipLayer1=true (Layer 2); write-intent → new choice, else agent.
     * - Classifier → straight to Layer 3 (agent).
     * - FullChat → safety fallback (Layer 3 never produces a choice).
     *
     * The user message already lives in Room from [handleSend] — not re-persisted.
     */
    private fun escalateChoice(originalText: String) {
        val sourceLayer = _choiceSourceLayer ?: RoutingLayer.Local

        if (originalText.isBlank()) {
            viewModelScope.launch {
                _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_extract_fail"))
            }
            clearChoice()
            return
        }

        clearChoice()
        _screenState.value = _screenState.value.copy(isProcessing = true)

        viewModelScope.launch {
            runCatching {
                val locale = localeProvider.current()
                when (sourceLayer) {
                    RoutingLayer.Local -> {
                        logger.info(TAG, "escalateChoice: Layer1 source → escalating to Layer2 (skipLayer1=true)")
                        val classification = aiChatRepository.classify(
                            input = originalText,
                            locale = locale,
                            skipLayer1 = true,
                        )
                        logger.debug(TAG, "Escalate re-classify → ${classification.intent::class.simpleName} layer=${classification.layer}")

                        when (val intent = classification.intent) {
                            ChatIntent.FreeForm,
                            is ChatIntent.Unknown -> runAgentTurn(originalText, locale)

                            ChatIntent.FindItems -> {
                                val query = extractQuery(originalText)
                                val outcome = toolCallDispatcher.dispatch(ToolCall.FindItemsQuery(query))
                                handleOutcomeInline(outcome)
                                _screenState.value = _screenState.value.copy(isProcessing = false)
                            }

                            ChatIntent.CreateItem,
                            ChatIntent.DeleteItem,
                            ChatIntent.CompleteItem,
                            is ChatIntent.CreateChecklist,
                            ChatIntent.SetReminder,
                            ChatIntent.MoveReminders -> {
                                val builtToolCall = classification.preBuiltToolCall
                                    ?: buildToolCall(intent, originalText, locale)
                                if (builtToolCall == null) {
                                    _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_extract_fail"))
                                    _screenState.value = _screenState.value.copy(isProcessing = false)
                                    return@runCatching
                                }
                                val toolCall = biasToolCallToContext(builtToolCall)
                                showWriteChoice(toolCall, originalText = originalText, sourceLayer = classification.layer)
                            }

                            is ChatIntent.AttachToItem -> {
                                _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_attach_no_files"))
                                _screenState.value = _screenState.value.copy(isProcessing = false)
                            }
                        }
                    }

                    RoutingLayer.Classifier -> {
                        logger.info(TAG, "escalateChoice: Classifier source → escalating to Layer3 (agent)")
                        runAgentTurn(originalText, locale)
                    }

                    RoutingLayer.FullChat -> {
                        logger.warning(TAG, "escalateChoice: unexpected FullChat sourceLayer — ignoring")
                        _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_unknown_intent_hint"))
                        _screenState.value = _screenState.value.copy(isProcessing = false)
                    }
                }
            }.onFailure { e ->
                logger.error(TAG, "escalateChoice failed", e)
                _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_generic_error"))
                _screenState.value = _screenState.value.copy(isProcessing = false)
            }
        }
    }

    // ─── Attachment-only send (no text, files present) ───────────────────────

    /**
     * Handles the case where the user tapped Send with attachments but no text.
     * Dispatches directly to [ToolCall.CreateChecklistFromAttachment] (mirrors Create via AI UX).
     * A preview card is shown so the user can confirm before execution.
     */
    private fun handleSendAttachmentsOnly(attachments: List<ChatAttachment>) {
        val userMsg = ChatMessage(
            id = generateId(),
            role = ChatRole.User,
            // Content shows file names as a summary since there is no text
            content = attachments.joinToString(", ") { it.fileName },
            timestamp = nowMillis(),
            costCredits = 0,
            routedLayer = RoutingLayer.Local,
            attachments = attachments,
        )
        updateMessages { it + userMsg }
        _screenState.value = _screenState.value.copy(
            pendingAttachments = emptyList(),
            isProcessing = true,
        )

        // Funnel: this is a send (attachments-only path). char_len is 0 (no text); input_method
        // "text" (the attachment was picked, not dictated). Start the latency clock.
        _turnStartMs = nowMillis()
        analytics.event(
            name = AnalyticsEvents.Chat.MESSAGE_SENT,
            params = buildMap {
                put(AnalyticsParams.DEEP_THINKING_ENABLED, _screenState.value.deepThinkingEnabled.toString())
                put(AnalyticsParams.HAS_CONTEXT_CHECKLIST, (_screenState.value.contextChecklistId != null).toString())
                put(AnalyticsParams.INPUT_METHOD, "text")
                put(AnalyticsParams.CHAR_LEN, 0)
            },
        )

        viewModelScope.launch {
            runCatching {
                withContext(NonCancellable) { chatHistoryRepository.append(userMsg) }
                val toolCall = ToolCall.CreateChecklistFromAttachment(attachments)
                // No original text → escape is Dismiss (handled inside showWriteChoice).
                showWriteChoice(toolCall, originalText = "", sourceLayer = RoutingLayer.Local)
            }.onFailure { e ->
                logger.error(TAG, "handleSendAttachmentsOnly failed", e)
                trackResponseReceived(RoutingLayer.Local, outcome = "error")
                _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_generic_error"))
                _screenState.value = _screenState.value.copy(isProcessing = false)
            }
        }
    }

    // ─── Attachment picked / voice recording ─────────────────────────────────

    private fun handleAttachmentPicked(attachment: ChatAttachment) {
        val current = _screenState.value.pendingAttachments
        val isPremium = userDataRepository.getUserDataFlow().value.isPremium
        val limit = if (isPremium) MAX_ATTACHMENTS_PREMIUM else MAX_ATTACHMENTS_FREE

        if (current.size >= limit) {
            viewModelScope.launch {
                _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_attach_limit_reached"))
            }
            return
        }
        _screenState.value = _screenState.value.copy(
            pendingAttachments = current + attachment,
        )
    }

    private fun handleVoiceRecordingStopped(recordingPath: String?, mimeType: String) {
        _screenState.value = _screenState.value.copy(isRecording = false)

        if (recordingPath == null) {
            // User cancelled — silent skip FORBIDDEN, emit snackbar
            viewModelScope.launch {
                _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_recording_cancelled"))
            }
            return
        }

        // Successful recording → transcribe to text via Cloud Function.
        // Repository owns file cleanup; we just handle the outcome.
        _screenState.value = _screenState.value.copy(isTranscribing = true)
        viewModelScope.launch {
            val locale = localeProvider.current()
            when (val outcome = aiChatRepository.transcribeAudio(recordingPath, mimeType, locale)) {
                is TranscriptionOutcome.Success -> {
                    // Append transcript to existing input so the user can dictate multiple times
                    val currentInput = _screenState.value.inputText
                    val merged = if (currentInput.isBlank()) outcome.transcript
                                 else "$currentInput ${outcome.transcript}"
                    _screenState.value = _screenState.value.copy(
                        inputText = merged,
                        isTranscribing = false,
                    )
                }
                TranscriptionOutcome.EmptyTranscript -> {
                    _screenState.value = _screenState.value.copy(isTranscribing = false)
                    _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_transcribe_empty"))
                }
                TranscriptionOutcome.FileMissing,
                TranscriptionOutcome.NetworkError,
                TranscriptionOutcome.ServiceError -> {
                    _screenState.value = _screenState.value.copy(isTranscribing = false)
                    _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_transcribe_error"))
                }
                TranscriptionOutcome.InsufficientCredits -> {
                    _screenState.value = _screenState.value.copy(isTranscribing = false)
                    _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_insufficient_credits"))
                }
            }
        }
    }

    // ─── Layer 3 checklist context ─────────────────────────────────────────────

    /**
     * Builds a compact summary of the user's top [CHECKLIST_SUMMARY_LIMIT] checklists for Layer 3.
     *
     * Each entry carries the name + counts AND a bounded tail-of-list slice of item text
     * ([ChecklistContext.recentItems]) so the model can answer "what did I add recently /
     * find the task about X". This is the ONLY place item text leaves the device (Layer 3 only).
     *
     * Token budgeting (keeps the request small — see CLAUDE.md unit-economics):
     *  - Only leaf items are sent; FOLDER nodes are skipped (they carry no user task text).
     *  - Per checklist we take the LAST [RECENT_ITEMS_PER_CHECKLIST] leaves (the freshest — items
     *    are appended, so the tail is the most-recently-added end).
     *  - A global cap of [RECENT_ITEMS_TOTAL_BUDGET] items across ALL checklists bounds the worst
     *    case regardless of how many lists the user has.
     *
     * Recency is POSITIONAL, not wall-clock: the domain [ChecklistItem] has no add-timestamp, so
     * [ChecklistItemContext.position] (list index) is the best available recency proxy. Answering
     * an absolute "when did I add X" would require a schema change (a per-item createdAt column).
     */
    private suspend fun buildChecklistsSummary(): List<ChecklistContext> = runCatching {
        var remainingBudget = RECENT_ITEMS_TOTAL_BUDGET
        checklistRepository.checklists.first()
            .take(CHECKLIST_SUMMARY_LIMIT)
            .map { checklist ->
                // Index the FULL item list first so position reflects the real list order,
                // then keep only leaves, then take the freshest tail within the global budget.
                val perListCap = minOf(RECENT_ITEMS_PER_CHECKLIST, remainingBudget).coerceAtLeast(0)
                val recent = if (perListCap == 0) {
                    emptyList()
                } else {
                    checklist.items
                        .mapIndexed { index, item -> index to item }
                        .filter { (_, item) -> item.type == ChecklistNodeType.ITEM }
                        .takeLast(perListCap)
                        .map { (index, item) ->
                            ChecklistItemContext(
                                text = item.text,
                                checked = item.checked,
                                position = index,
                            )
                        }
                }
                remainingBudget -= recent.size
                ChecklistContext(
                    name = checklist.name,
                    totalItems = checklist.items.count { it.type == ChecklistNodeType.ITEM },
                    doneItems = checklist.items.count { it.type == ChecklistNodeType.ITEM && it.checked },
                    recentItems = recent,
                )
            }
    }.getOrElse { e ->
        logger.error(TAG, "buildChecklistsSummary failed — ${e.message}", e)
        emptyList()
    }

    // ─── Agentic loop (Phase 2d) ──────────────────────────────────────────────

    /**
     * Runs the stateless agent loop for one user turn.
     *
     * Algorithm:
     * 1. Seed [transcript] from recent chat history:
     *    user messages → [AgentTranscriptEntry.UserText], assistant → [AgentTranscriptEntry.ModelText].
     *    The latest user message (already persisted to history from [handleSend]) appears as
     *    the last [UserText] — no double-add.
     * 2. Loop up to [AGENT_MAX_ROUNDS] times:
     *    a. Call [AiChatRepository.agentStep] with current transcript.
     *    b. On [AgentStepResult.ToolCalls]: split into readOnly / mutating.
     *       - Read-only (find_items, read_checklist) are dispatched immediately without a plan-card.
     *       - Mutating calls → build a batch [PendingChoice], show the choice block, suspend until
     *         user decides (ExecuteAll chip → dispatch; escape/Dismiss chip → declined results).
     *       - COUNT INVARIANT: allResults.size == calls.size (one result per call, in order).
     *    c. Append [ModelToolCalls] + [ToolResults] to transcript, increment round, continue.
     *    d. On [AgentStepResult.Final]: persist assistant message, return.
     *    e. On error results: show appropriate snackbar/message, return.
     * 3. If loop exits via round cap: emit fallback assistant message.
     *
     * [isProcessing] is true during step()/dispatch; false while the plan-card is interactive.
     */
    private suspend fun runAgentTurn(userInput: String, locale: ChatLocale) {
        // ── 1. Seed transcript from history ──────────────────────────────────
        val checklistsSummary = buildChecklistsSummary()
        // P5: resolve the open checklist once for the whole turn so the agent biases
        // list-less commands toward it. Null when the dock was opened from the home screen.
        val contextChecklistName = resolveContextChecklistName()
        val historyMessages = _screenState.value.messages

        val seedTranscript: MutableList<AgentTranscriptEntry> = mutableListOf()
        for (msg in historyMessages) {
            when (msg.role) {
                ChatRole.User -> seedTranscript.add(AgentTranscriptEntry.UserText(msg.content))
                ChatRole.Assistant -> seedTranscript.add(AgentTranscriptEntry.ModelText(msg.content))
            }
        }
        // Ensure the latest user message is the final UserText (it was already added above
        // from persisted history — if the list is up-to-date it is already there, so no
        // double-add is needed here; the in-memory messages list reflects the just-sent user msg).

        logger.debug(TAG, "runAgentTurn: seeded ${seedTranscript.size} transcript entries")

        // ── 2. Agent loop ────────────────────────────────────────────────────
        val transcript = seedTranscript
        var round = 0

        // Clear any stale deferred from a previous turn.
        _pendingAgentDecision = null

        while (round < AGENT_MAX_ROUNDS) {
            _screenState.value = _screenState.value.copy(isProcessing = true)

            val stepResult = aiChatRepository.agentStep(
                transcript = transcript,
                locale = locale,
                checklistsSummary = checklistsSummary,
                contextChecklistName = contextChecklistName,
            )

            when (stepResult) {
                is AgentStepResult.ToolCalls -> {
                    val calls = stepResult.calls
                    // Optimistic credit update from server response.
                    _screenState.value = _screenState.value.copy(
                        creditBalance = stepResult.creditsRemaining,
                    )

                    // Partition into read-only (no confirmation needed) and mutating (plan-card).
                    val readOnlyNames = setOf("find_items", "read_checklist")
                    val readOnlyCalls = calls.filter { it.name in readOnlyNames }
                    val mutatingCalls = calls.filter { it.name !in readOnlyNames }

                    // Dispatch read-only calls immediately, no plan-card.
                    val readOnlyResults = mutableListOf<AgentToolResult>()
                    for (call in readOnlyCalls) {
                        val toolCall = AgentToolCallMapper.map(call)
                        val resultJson = if (toolCall == null) {
                            logger.warning(TAG, "runAgentTurn: unmappable read-only call '${call.name}' — sending error result")
                            buildJsonObjectError("unknown_tool", call.name)
                        } else {
                            val outcome = toolCallDispatcher.dispatch(toolCall)
                            AgentToolResultSerializer.serialize(outcome)
                        }
                        readOnlyResults.add(AgentToolResult(call.id, call.name, resultJson))
                    }

                    // Dispatch mutating calls — show choice block if any.
                    val mutatingResults = mutableListOf<AgentToolResult>()
                    if (mutatingCalls.isNotEmpty()) {
                        // Build numbered batch items for the prompt bubble.
                        val planItems = mutatingCalls.map { call ->
                            val toolCall = AgentToolCallMapper.map(call)
                            val text = if (toolCall != null) previewRenderer.render(toolCall) else call.name
                            AgentPlanItem(text = text, isDestructive = call.name == "delete_item")
                        }

                        // Suspend the loop — show the choice block, wait for user.
                        // The choice block is the agent's preview funnel: emit PREVIEW_SHOWN per
                        // mutating action, plus a single response_received(outcome="preview") so the
                        // latency-to-first-response is captured here (the later Final is a follow-up).
                        mutatingCalls.forEach { call ->
                            analytics.event(
                                name = AnalyticsEvents.Chat.PREVIEW_SHOWN,
                                params = mapOf(AnalyticsParams.ACTION_TYPE to call.name),
                            )
                        }
                        trackResponseReceived(RoutingLayer.FullChat, outcome = "preview")
                        val decision = CompletableDeferred<Boolean>()
                        _pendingAgentDecision = decision
                        // Agent batch choice: one Primary "Do it all" (ExecuteAll) + Dismiss escape.
                        val batchChoice = ChatChoice(
                            prompt = choiceString(Res.string.chat_choice_apply_actions),
                            options = listOf(
                                ChoiceOption(
                                    id = CHOICE_EXECUTE_ALL,
                                    label = choiceString(Res.string.chat_choice_execute_all),
                                    role = ChoiceRole.Primary,
                                    action = ChoiceAction.ExecuteAll,
                                ),
                            ),
                            escape = ChoiceOption(
                                id = CHOICE_ESCAPE,
                                label = choiceString(Res.string.chat_choice_cancel),
                                role = ChoiceRole.Escape,
                                action = ChoiceAction.Dismiss,
                            ),
                        )
                        _screenState.value = _screenState.value.copy(
                            isProcessing = false,
                            pendingChoice = PendingChoice(choice = batchChoice, batchItems = planItems),
                        )

                        val approved = decision.await()

                        // Clear choice block and resume processing.
                        _pendingAgentDecision = null
                        _screenState.value = _screenState.value.copy(
                            pendingChoice = null,
                            isProcessing = true,
                        )

                        for (call in mutatingCalls) {
                            val resultJson = if (!approved) {
                                AgentToolResultSerializer.declinedResult()
                            } else {
                                val toolCall = AgentToolCallMapper.map(call)
                                if (toolCall == null) {
                                    logger.warning(TAG, "runAgentTurn: unmappable mutating call '${call.name}' — sending error result")
                                    buildJsonObjectError("unknown_tool", call.name)
                                } else {
                                    val outcome = toolCallDispatcher.dispatch(toolCall)
                                    AgentToolResultSerializer.serialize(outcome)
                                }
                            }
                            mutatingResults.add(AgentToolResult(call.id, call.name, resultJson))
                        }
                    }

                    // ── COUNT INVARIANT: assemble results in the same order as calls ──
                    // Build a map by call.id so the merge is order-preserving regardless
                    // of how readOnly / mutating were partitioned.
                    val resultById = (readOnlyResults + mutatingResults)
                        .associateBy { it.id }
                    val allResults = calls.map { call ->
                        resultById[call.id] ?: AgentToolResult(
                            id = call.id,
                            name = call.name,
                            result = buildJsonObjectError("missing_result", call.name),
                        )
                    }
                    check(allResults.size == calls.size) {
                        "COUNT INVARIANT violated: calls=${calls.size} results=${allResults.size}"
                    }

                    // Extend transcript and continue.
                    transcript.add(AgentTranscriptEntry.ModelToolCalls(calls))
                    transcript.add(AgentTranscriptEntry.ToolResults(allResults))
                    round++
                }

                is AgentStepResult.Final -> {
                    // Persist the optimistic credit balance.
                    _screenState.value = _screenState.value.copy(
                        creditBalance = stepResult.creditsRemaining,
                        isProcessing = false,
                    )
                    runCatching {
                        val currentUserData = userDataRepository.getUserData()
                        userDataRepository.update(currentUserData.copy(aiCredits = stepResult.creditsRemaining))
                    }.onFailure { e ->
                        logger.error(TAG, "runAgentTurn: failed to persist credit balance — ${e.message}", e)
                    }
                    trackResponseReceived(RoutingLayer.FullChat, outcome = "answer")
                    addAndPersistAssistantMessage(
                        content = stepResult.content,
                        routedLayer = RoutingLayer.FullChat,
                        costCredits = 3,
                    )
                    return
                }

                is AgentStepResult.Options -> {
                    // Terminal turn (like Final) with AI-generated tappable answer options.
                    // Persist the optimistic credit balance.
                    _screenState.value = _screenState.value.copy(
                        creditBalance = stepResult.creditsRemaining,
                        isProcessing = false,
                    )
                    runCatching {
                        val currentUserData = userDataRepository.getUserData()
                        userDataRepository.update(currentUserData.copy(aiCredits = stepResult.creditsRemaining))
                    }.onFailure { e ->
                        logger.error(TAG, "runAgentTurn: failed to persist credit balance — ${e.message}", e)
                    }
                    trackResponseReceived(RoutingLayer.FullChat, outcome = "options")
                    // The question lives INSIDE the choice block (ChatChoice.prompt), NOT as a
                    // separate persisted message: the inline dock overlays only the pendingChoice
                    // over the last message, so a separately-persisted question would be hidden.
                    // We persist it as an assistant message only on RESOLVE (chip tap / dismiss) —
                    // see [sendOptionAsTurn] / [handleChoiceDismissed] — so history + next-turn
                    // transcript get it without double-rendering it here.
                    val options = stepResult.options.mapIndexed { index, label ->
                        ChoiceOption(
                            id = "$CHOICE_OPTION_PREFIX$index",
                            label = label,
                            role = ChoiceRole.Default,
                            action = ChoiceAction.SendMessage(label),
                        )
                    }
                    _choiceSourceLayer = null
                    _screenState.value = _screenState.value.copy(
                        pendingChoice = PendingChoice(
                            choice = ChatChoice(
                                prompt = stepResult.prompt,
                                options = options,
                                escape = ChoiceOption(
                                    id = CHOICE_ESCAPE,
                                    label = choiceString(Res.string.chat_choice_cancel),
                                    role = ChoiceRole.Escape,
                                    action = ChoiceAction.Dismiss,
                                ),
                            ),
                        ),
                    )
                    return
                }

                AgentStepResult.InsufficientCredits -> {
                    logger.info(TAG, "runAgentTurn: InsufficientCredits")
                    trackResponseReceived(RoutingLayer.FullChat, outcome = "error")
                    _screenState.value = _screenState.value.copy(isProcessing = false)
                    _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_insufficient_credits"))
                    return
                }

                AgentStepResult.NetworkError,
                AgentStepResult.ServiceError -> {
                    logger.warning(TAG, "runAgentTurn: ${stepResult::class.simpleName}")
                    trackResponseReceived(RoutingLayer.FullChat, outcome = "error")
                    _screenState.value = _screenState.value.copy(isProcessing = false)
                    _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_completion_error"))
                    return
                }
            }
        }

        // Round cap reached — emit fallback message.
        logger.warning(TAG, "runAgentTurn: hit round cap ($AGENT_MAX_ROUNDS rounds) without Final")
        trackResponseReceived(RoutingLayer.FullChat, outcome = "error")
        _screenState.value = _screenState.value.copy(isProcessing = false)
        _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_agent_round_limit"))
    }

    /** Builds a minimal error JsonObject for tool results that couldn't be executed. */
    private fun buildJsonObjectError(status: String, detail: String): kotlinx.serialization.json.JsonObject =
        kotlinx.serialization.json.buildJsonObject {
            put("status", status)
            put("detail", detail)
        }

    /**
     * Adds an assistant message to [_screenState] and persists it to [chatHistoryRepository].
     * Used for Layer 3 completions where the content is known at call time.
     */
    private suspend fun addAndPersistAssistantMessage(
        content: String,
        routedLayer: RoutingLayer?,
        costCredits: Int = 0,
    ) {
        val msg = ChatMessage(
            id = generateId(),
            role = ChatRole.Assistant,
            content = content,
            timestamp = nowMillis(),
            costCredits = costCredits,
            routedLayer = routedLayer,
        )
        updateMessages { it + msg }
        // NonCancellable: Layer 3 response is the user-visible reply; never lose it
        // to a back-nav that cancels viewModelScope mid-flight.
        withContext(NonCancellable) { chatHistoryRepository.append(msg) }
    }

    /**
     * Replaces the item text in [original] with the user's edited [edited] value.
     * Item-less tool calls (CreateChecklist with no name change yet, MoveAllReminders, FindItemsQuery)
     * are returned unchanged — they are not surfaced through the editable preview field today.
     */
    private fun applyEditedText(original: ToolCall, edited: String): ToolCall {
        val trimmed = edited.trim()
        if (trimmed.isEmpty()) return original
        return when (original) {
            is ToolCall.AddItem -> original.copy(itemText = trimmed)
            is ToolCall.DeleteItem -> original.copy(itemText = trimmed)
            is ToolCall.CompleteItem -> original.copy(itemText = trimmed)
            is ToolCall.SetItemReminder -> original.copy(itemText = trimmed)
            is ToolCall.CreateChecklist -> original.copy(name = trimmed)
            is ToolCall.MoveAllReminders -> original
            is ToolCall.FindItemsQuery -> original
            // Attachment tool calls: edited text updates itemText (item to attach to)
            is ToolCall.AttachToItem -> original.copy(itemText = trimmed)
            // CreateChecklistFromAttachment has no user-editable text field
            is ToolCall.CreateChecklistFromAttachment -> original
            // Agent-only: AddItems has no single editable text field in Layer-1 preview
            is ToolCall.AddItems -> original
            // Agent-only: RenameChecklist — no Layer-1 preview editing path
            is ToolCall.RenameChecklist -> original
            // Agent-only: ReadChecklist — read-only, no preview card
            is ToolCall.ReadChecklist -> original
        }
    }

    // ─── Context-checklist bias (P5) ─────────────────────────────────────────

    /**
     * Resolves [ChatScreenState.contextChecklistId] to the checklist's display name.
     *
     * Returns null when there is no active context, or when the context checklist was
     * deleted between opening the dock and sending the command (logged as a warning so
     * the silent fallback to the default-resolution path is traceable).
     *
     * The resolved name is used to bias list-less commands (e.g. "add milk") toward the
     * checklist the user currently has open, instead of the dispatcher's "first checklist"
     * fallback. Name-based (not id-based) so it flows through the existing hint → name-match
     * resolution in [ToolCallDispatcher] with zero dispatcher changes.
     */
    private suspend fun resolveContextChecklistName(): String? {
        val contextId = _screenState.value.contextChecklistId ?: return null
        val checklist = runCatching { checklistRepository.getChecklistById(contextId) }
            .getOrElse { e ->
                logger.error(TAG, "resolveContextChecklistName: lookup failed for id=$contextId — ${e.message}", e)
                null
            }
        if (checklist == null) {
            logger.warning(TAG, "resolveContextChecklistName: context checklist id=$contextId not found — falling back to default resolution")
            return null
        }
        return checklist.name
    }

    /**
     * Applies the active context checklist to a list-less command [toolCall].
     *
     * For command variants that target an existing checklist (AddItem, AddItems, CompleteItem,
     * DeleteItem, SetItemReminder, AttachToItem) whose [checklistHint] is null, returns a copy
     * with the hint set to [contextName]. An explicit hint is NEVER overwritten — the user
     * naming a list always wins over the open-screen context.
     *
     * CreateChecklist / RenameChecklist are intentionally excluded: there the "list" is the
     * target/output of the action, not the context to operate within. CreateChecklistFromAttachment,
     * MoveAllReminders, FindItemsQuery and ReadChecklist carry no per-list hint either.
     */
    private fun applyContextChecklist(toolCall: ToolCall, contextName: String): ToolCall = when (toolCall) {
        is ToolCall.AddItem ->
            if (toolCall.checklistHint == null) toolCall.copy(checklistHint = contextName) else toolCall
        is ToolCall.AddItems ->
            if (toolCall.checklistHint == null) toolCall.copy(checklistHint = contextName) else toolCall
        is ToolCall.CompleteItem ->
            if (toolCall.checklistHint == null) toolCall.copy(checklistHint = contextName) else toolCall
        is ToolCall.DeleteItem ->
            if (toolCall.checklistHint == null) toolCall.copy(checklistHint = contextName) else toolCall
        is ToolCall.SetItemReminder ->
            if (toolCall.checklistHint == null) toolCall.copy(checklistHint = contextName) else toolCall
        is ToolCall.AttachToItem ->
            if (toolCall.checklistHint == null) toolCall.copy(checklistHint = contextName) else toolCall
        // Excluded by design — list is the target, not the context, or no hint field.
        is ToolCall.CreateChecklist,
        is ToolCall.RenameChecklist,
        is ToolCall.CreateChecklistFromAttachment,
        is ToolCall.MoveAllReminders,
        is ToolCall.FindItemsQuery,
        is ToolCall.ReadChecklist -> toolCall
    }

    /**
     * Convenience wrapper: resolves the context checklist name (if any) and applies it to
     * [toolCall] when the hint is null. No-op when there is no context or the command already
     * carries an explicit hint. Used on the Layer-1/Layer-2 preview-build path.
     */
    private suspend fun biasToolCallToContext(toolCall: ToolCall): ToolCall {
        val contextName = resolveContextChecklistName() ?: return toolCall
        return applyContextChecklist(toolCall, contextName)
    }

    /**
     * Extracts a checklist hint (target list name) from a write-intent ToolCall for preview display.
     * Returns null for tool calls that don't carry a target list (CreateChecklist, MoveAllReminders, FindItemsQuery).
     */
    private fun extractHint(toolCall: ToolCall): String? = when (toolCall) {
        is ToolCall.AddItem -> toolCall.checklistHint
        is ToolCall.DeleteItem -> toolCall.checklistHint
        is ToolCall.CompleteItem -> toolCall.checklistHint
        is ToolCall.SetItemReminder -> toolCall.checklistHint
        is ToolCall.AttachToItem -> toolCall.checklistHint
        is ToolCall.CreateChecklist,
        is ToolCall.MoveAllReminders,
        is ToolCall.FindItemsQuery,
        is ToolCall.CreateChecklistFromAttachment,
        is ToolCall.ReadChecklist -> null
        // Agent-only: hint present on these variants, surface it for potential preview display
        is ToolCall.AddItems -> toolCall.checklistHint
        is ToolCall.RenameChecklist -> toolCall.checklistHint
    }

    /**
     * Resolves a choice-copy string resource, tolerating the unit-test host environment where
     * Compose Resources `getString` throws "Resources.getSystem not mocked" (plain Android host
     * test, no Robolectric — see AnalyzeViewModelTest note). On-device this is a normal getString;
     * in tests it falls back to a stable non-blank token so the choice block still builds with the
     * correct structure (the tests assert on the tool call / roles / ids, not the resolved copy).
     */
    private suspend fun choiceString(res: StringResource, vararg args: Any): String =
        runCatching { if (args.isEmpty()) getString(res) else getString(res, *args) }
            .getOrElse { e ->
                logger.warning(TAG, "choiceString: resource resolution failed (test env?) — ${e.message}")
                "…"
            }

    // ─── Choice copy helpers (write-intent prompt / chip / loading labels) ─────

    /**
     * The prompt string resource + its positional args for a write-intent choice.
     * e.g. AddItem → "Add to %1$s?" with the target list name (or a generic prompt when
     * the list is unspecified). Strings are localized via [getString] at the call site.
     */
    private fun ToolCall.promptRes(): StringResource = when (this) {
        is ToolCall.AddItem -> if (checklistHint.isNullOrBlank()) Res.string.chat_choice_add_default_list else Res.string.chat_choice_add_to_list
        is ToolCall.DeleteItem -> Res.string.chat_choice_delete
        is ToolCall.CompleteItem -> Res.string.chat_choice_complete
        is ToolCall.CreateChecklist -> Res.string.chat_choice_create
        is ToolCall.SetItemReminder -> Res.string.chat_choice_set_reminder
        is ToolCall.MoveAllReminders -> Res.string.chat_choice_move_reminders
        is ToolCall.AttachToItem -> Res.string.chat_choice_attach
        is ToolCall.CreateChecklistFromAttachment -> Res.string.chat_choice_create_from_file
        // Agent-only / read variants never produce a write-intent choice; safe fallback.
        is ToolCall.FindItemsQuery,
        is ToolCall.AddItems,
        is ToolCall.ReadChecklist,
        is ToolCall.RenameChecklist -> Res.string.chat_choice_apply_actions
    }

    /** Positional args for [promptRes] — the item/list name highlighted in the prompt. */
    private fun ToolCall.promptArgs(): Array<Any> = when (this) {
        is ToolCall.AddItem -> if (checklistHint.isNullOrBlank()) emptyArray() else arrayOf(checklistHint!!)
        is ToolCall.DeleteItem -> arrayOf(itemText)
        is ToolCall.CompleteItem -> arrayOf(itemText)
        is ToolCall.CreateChecklist -> arrayOf(name)
        is ToolCall.SetItemReminder -> arrayOf(itemText)
        is ToolCall.AttachToItem -> arrayOf(itemText)
        is ToolCall.MoveAllReminders,
        is ToolCall.CreateChecklistFromAttachment,
        is ToolCall.FindItemsQuery,
        is ToolCall.AddItems,
        is ToolCall.ReadChecklist,
        is ToolCall.RenameChecklist -> emptyArray()
    }

    /** Primary-chip label resource for a write-intent ("Add" / "Delete" / "Create" / …). */
    private fun ToolCall.primaryActionLabel(): StringResource = when (this) {
        is ToolCall.AddItem, is ToolCall.AddItems -> Res.string.chat_choice_action_add
        is ToolCall.DeleteItem -> Res.string.chat_choice_action_delete
        is ToolCall.CompleteItem -> Res.string.chat_choice_action_complete
        is ToolCall.CreateChecklist, is ToolCall.CreateChecklistFromAttachment -> Res.string.chat_choice_action_create
        is ToolCall.SetItemReminder -> Res.string.chat_choice_action_set_reminder
        is ToolCall.MoveAllReminders -> Res.string.chat_choice_action_move
        is ToolCall.AttachToItem -> Res.string.chat_choice_action_attach
        is ToolCall.FindItemsQuery,
        is ToolCall.ReadChecklist,
        is ToolCall.RenameChecklist -> Res.string.chat_choice_action_create
    }

    /** Loading label resource for a write-intent ("Adding…" / "Deleting…" / …). */
    private fun ToolCall.executingLabel(): StringResource = when (this) {
        is ToolCall.AddItem, is ToolCall.AddItems -> Res.string.chat_choice_executing_add
        is ToolCall.DeleteItem -> Res.string.chat_choice_executing_delete
        is ToolCall.CompleteItem -> Res.string.chat_choice_executing_complete
        is ToolCall.CreateChecklist, is ToolCall.CreateChecklistFromAttachment -> Res.string.chat_choice_executing_create
        is ToolCall.SetItemReminder -> Res.string.chat_choice_executing_set_reminder
        is ToolCall.MoveAllReminders -> Res.string.chat_choice_executing_move
        is ToolCall.AttachToItem -> Res.string.chat_choice_executing_attach
        is ToolCall.FindItemsQuery,
        is ToolCall.ReadChecklist,
        is ToolCall.RenameChecklist -> Res.string.chat_choice_executing_default
    }

    /**
     * Returns a copy of this tool call with its checklist hint set to [name], for the
     * AmbiguousMatch "Which list?" choice. Null for tool calls that carry no per-list hint.
     */
    private fun ToolCall.withHint(name: String): ToolCall? = when (this) {
        is ToolCall.AddItem -> copy(checklistHint = name)
        is ToolCall.DeleteItem -> copy(checklistHint = name)
        is ToolCall.CompleteItem -> copy(checklistHint = name)
        is ToolCall.SetItemReminder -> copy(checklistHint = name)
        is ToolCall.AttachToItem -> copy(checklistHint = name)
        is ToolCall.AddItems -> copy(checklistHint = name)
        is ToolCall.CreateChecklist,
        is ToolCall.CreateChecklistFromAttachment,
        is ToolCall.MoveAllReminders,
        is ToolCall.FindItemsQuery,
        is ToolCall.ReadChecklist,
        is ToolCall.RenameChecklist -> null
    }

    /**
     * Extracts the user-editable text (item name or new list name) for preview's text field.
     */
    private fun extractItemText(toolCall: ToolCall): String = when (toolCall) {
        is ToolCall.AddItem -> toolCall.itemText
        is ToolCall.DeleteItem -> toolCall.itemText
        is ToolCall.CompleteItem -> toolCall.itemText
        is ToolCall.SetItemReminder -> toolCall.itemText
        is ToolCall.AttachToItem -> toolCall.itemText
        is ToolCall.CreateChecklist -> toolCall.name
        is ToolCall.MoveAllReminders,
        is ToolCall.FindItemsQuery,
        is ToolCall.CreateChecklistFromAttachment,
        // Agent-only: no single editable item text for these variants
        is ToolCall.AddItems,
        is ToolCall.RenameChecklist,
        is ToolCall.ReadChecklist -> ""
    }

    // ─── Outcome handlers ─────────────────────────────────────────────────────

    /**
     * Renders a [DispatchOutcome] inline.
     *
     * @param sourceToolCall The tool call that produced [outcome], when available. Used to turn
     *   an [DispatchOutcome.AmbiguousMatch] into a "Which list?" choice block whose chips re-run
     *   the same command against a specific candidate (hint swapped). Null on read-only paths
     *   (FindItemsQuery) where there is no per-list hint to swap → falls back to a text hint.
     */
    private suspend fun handleOutcomeInline(outcome: DispatchOutcome, sourceToolCall: ToolCall? = null) {
        when (outcome) {
            is DispatchOutcome.Success -> {
                _sideEffect.emit(
                    ChatScreenSideEffect.ShowAssistantMessage(
                        messageKey = outcome.messageKey,
                        args = outcome.args,
                        linkedChecklistId = outcome.linkedChecklistId,
                    )
                )
            }
            is DispatchOutcome.ChecklistContent -> {
                // Agent-only read outcome: never emitted via the Layer-1 preview path.
                // If somehow surfaced inline (e.g. agent loop hands back to handleOutcomeInline),
                // show a summary line so the user is not left with a blank response.
                val summary = outcome.items.take(5).joinToString(", ") { it.text }
                val suffix = if (outcome.items.size > 5) " (+${outcome.items.size - 5} more)" else ""
                _sideEffect.emit(
                    ChatScreenSideEffect.ShowAssistantMessage(
                        messageKey = "chat_generic_error",
                        args = emptyList(),
                    )
                )
                logger.debug(TAG, "ChecklistContent inline (agent): ${outcome.checklistName} — $summary$suffix")
            }
            is DispatchOutcome.AmbiguousMatch -> {
                val withHint = sourceToolCall?.let { tc ->
                    outcome.candidates.take(MAX_CHOICE_OPTIONS).mapNotNull { tc.withHint(it) }
                }
                if (withHint.isNullOrEmpty()) {
                    // No swappable hint (read path) → keep the old text clarification.
                    val candidates = outcome.candidates.take(MAX_CHOICE_OPTIONS).joinToString(", ")
                    _sideEffect.emit(
                        ChatScreenSideEffect.ShowAssistantMessage(
                            messageKey = "chat_ambiguous_match",
                            args = listOf(candidates),
                        )
                    )
                } else {
                    // Build a "Which list?" choice: one Default chip per candidate that re-runs the
                    // command against that specific list, plus a Dismiss escape. sourceLayer = null
                    // keeps the post-dispatch behaviour (no extra response_received, _choiceSourceLayer
                    // cleared) — the original turn already tracked its outcome.
                    showWhichListChoice(sourceToolCall, outcome.candidates, sourceLayer = null)
                }
            }
            is DispatchOutcome.NotFound -> {
                _sideEffect.emit(
                    ChatScreenSideEffect.ShowAssistantMessage(
                        messageKey = outcome.messageKey,
                        args = outcome.args,
                    )
                )
            }
            DispatchOutcome.RequiresPremium -> {
                _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_requires_premium"))
            }
        }
    }

    // ─── ToolCall building from raw text ──────────────────────────────────────

    /**
     * Converts a classified [ChatIntent] + raw [text] into a concrete [ToolCall].
     *
     * This is a best-effort extraction in Layer 1. If entities cannot be extracted,
     * returns null (caller shows a clarification message).
     *
     * Note: [IntentClassification] does NOT carry extractedParams (the field was
     * removed from the model in kmp-expert Iteration 1). Raw text is re-parsed here
     * using simple string operations — sufficient for Layer 1 MVP.
     */
    private fun buildToolCall(intent: ChatIntent, rawText: String, locale: ChatLocale): ToolCall? {
        val lower = rawText.trim().lowercase()

        return when (intent) {
            ChatIntent.CreateItem -> {
                val (itemText, hint) = extractItemAndHint(lower, locale)
                if (itemText.isNullOrBlank()) {
                    null
                } else {
                    // Multi-item add: "add milk, eggs and bread to shopping" → AddItems(3).
                    // splitItems only splits comma-lists (single items like "mac and cheese"
                    // stay intact), so a 1-element result keeps the existing single-AddItem path.
                    val items = splitItems(itemText, locale)
                    if (items.size > 1) {
                        ToolCall.AddItems(checklistHint = hint, itemTexts = items)
                    } else {
                        ToolCall.AddItem(checklistHint = hint, itemText = itemText)
                    }
                }
            }

            ChatIntent.DeleteItem -> {
                val (itemText, hint) = extractItemAndHint(lower, locale)
                if (itemText.isNullOrBlank()) null
                else ToolCall.DeleteItem(checklistHint = hint, itemText = itemText)
            }

            ChatIntent.CompleteItem -> {
                val (itemText, hint) = extractItemAndHint(lower, locale)
                if (itemText.isNullOrBlank()) null
                else ToolCall.CompleteItem(checklistHint = hint, itemText = itemText)
            }

            is ChatIntent.CreateChecklist -> {
                // Prefer name from the classifier (already extracted in Layer 1 from raw input,
                // or from Layer 2 server-side). Falls back to fuzzy extraction from raw text
                // only when the intent didn't carry a name (edge case).
                val name = intent.name ?: extractChecklistName(lower, locale)
                if (name.isNullOrBlank()) {
                    null
                } else {
                    // Create-with-items: "create a trip list with passport, tickets, charger" →
                    // CreateChecklist(name="trip list", initialItems=[passport, tickets, charger]).
                    // Split on " with " (EN) / " с "/" со " (RU); items go through splitItems.
                    val withSeparators = if (locale == ChatLocale.Ru) listOf(" с ", " со ") else listOf(" with ")
                    val separator = withSeparators.firstOrNull { name.contains(it) }
                    if (separator != null) {
                        val splitIdx = name.indexOf(separator)
                        val rawNamePart = name.substring(0, splitIdx).trim()
                        val itemsStr = name.substring(splitIdx + separator.length).trim()
                        val namePart = stripLeadingArticleEn(rawNamePart, locale)
                        val items = splitItems(itemsStr, locale)
                        if (namePart.isBlank() || items.isEmpty()) {
                            // Couldn't cleanly split → keep the whole string as the name.
                            ToolCall.CreateChecklist(name = name, initialItems = emptyList())
                        } else {
                            ToolCall.CreateChecklist(name = namePart, initialItems = items)
                        }
                    } else {
                        ToolCall.CreateChecklist(name = name, initialItems = emptyList())
                    }
                }
            }

            ChatIntent.SetReminder -> {
                val itemText = extractPayloadAfterReminderKeyword(lower, locale)
                if (itemText.isNullOrBlank()) null
                else ToolCall.SetItemReminder(
                    checklistHint = null,
                    itemText = itemText,
                    at = nowMillis() + 24 * 60 * 60 * 1000L,
                )
            }

            ChatIntent.MoveReminders -> {
                // Phase A: move from today to tomorrow as placeholder
                // Pending: docs/todos/2026-05-13-ai-chat-assistant.md (Phase B date parsing)
                val now = nowMillis()
                val oneDayMs = 24 * 60 * 60 * 1000L
                ToolCall.MoveAllReminders(
                    fromDayStartMs = now - (now % oneDayMs),
                    fromDayEndMs = now - (now % oneDayMs) + oneDayMs - 1,
                    toDayStartMs = now - (now % oneDayMs) + oneDayMs,
                )
            }

            // FindItems, FreeForm, AttachToItem and Unknown are handled separately
            // and should not reach buildToolCall.
            ChatIntent.FindItems,
            ChatIntent.FreeForm,
            is ChatIntent.AttachToItem,  // handled inline before buildToolCall is called
            is ChatIntent.Unknown -> null
        }
    }

    // ─── Text entity extraction helpers ──────────────────────────────────────

    /**
     * Splits a multi-item payload into individual item strings.
     *
     * Rule (deliberately conservative to avoid false-splits like "mac and cheese"):
     *  - If [text] contains a comma → split on commas, then split ONLY the LAST segment again
     *    on " and " (EN) / " и " (RU) — that's where the trailing conjunction lives in natural
     *    lists ("milk, eggs and bread" → [milk, eggs, bread]).
     *  - If NO comma → return the whole text as a single item; a bare "and"/"и" is NOT a split
     *    point ("mac and cheese" stays one item).
     * Each result is trimmed; blanks are dropped.
     */
    private fun splitItems(text: String, locale: ChatLocale): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (!trimmed.contains(',')) return listOf(trimmed)

        val conjunction = if (locale == ChatLocale.Ru) " и " else " and "
        val segments = trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        segments.forEachIndexed { index, segment ->
            if (index == segments.lastIndex && segment.contains(conjunction)) {
                segment.split(conjunction)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { result += it }
            } else {
                result += segment
            }
        }
        return result
    }

    /**
     * Extracts [itemText, checklistHint] from a command like "add milk to shopping".
     * Returns [null, null] when no item text can be found.
     */
    private fun extractItemAndHint(lower: String, locale: ChatLocale): Pair<String?, String?> {
        // Remove known verb keywords from the beginning
        val verbKeywordsRu = setOf("добавь", "добавить", "удали", "удалить", "отметь", "сделано", "выполнено", "убери", "убрать")
        val verbKeywordsEn = setOf("add", "delete", "remove", "complete", "done", "mark", "check off", "tick off")
        val verbs = if (locale == ChatLocale.Ru) verbKeywordsRu else verbKeywordsEn

        var remainder = lower
        for (verb in verbs.sortedByDescending { it.length }) {
            if (remainder.startsWith(verb)) {
                remainder = remainder.removePrefix(verb).trim()
                break
            }
        }
        if (remainder.isBlank()) return Pair(null, null)

        // Leading-preposition pattern: "<prep> <hint> <item...>"
        //   «в апки тест» → hint="апки", item="тест"
        //   «in shopping milk bread» → hint="shopping", item="milk bread"
        // Falls through to middle-prep search below when remainder doesn't start with a preposition.
        val leadingPrepRu = listOf("в ", "к ", "для ", "на ", "по ")
        val leadingPrepEn = listOf("into ", "in ", "to ", "for ")
        val leadingPreps = if (locale == ChatLocale.Ru) leadingPrepRu else leadingPrepEn
        for (prep in leadingPreps.sortedByDescending { it.length }) {
            if (remainder.startsWith(prep)) {
                val afterPrep = remainder.removePrefix(prep).trim()
                if (afterPrep.isEmpty()) return Pair(null, null)
                val firstSpace = afterPrep.indexOf(' ')
                return if (firstSpace > 0) {
                    val hintCandidate = afterPrep.substring(0, firstSpace).trim().ifBlank { null }
                    val itemCandidate = afterPrep.substring(firstSpace + 1).trim().ifBlank { null }
                    // Generic-target detection: "в чеклист пункт молоко" / "in a checklist milk"
                    // → the "hint" is a bare generic word ("чеклист"/"checklist"), not a real list
                    // name. Drop it (so the caller offers a "which list?" choice) and strip any
                    // leading filler item-word ("пункт"/"item") off the item text.
                    if (isGenericTarget(hintCandidate, locale)) {
                        Pair(stripLeadingFiller(itemCandidate, locale), null)
                    } else {
                        Pair(itemCandidate, hintCandidate)
                    }
                } else {
                    // single word after prep → treat as hint only; user didn't name an item.
                    // A bare generic word ("в чеклист") is no real hint → null, null.
                    val single = afterPrep.trim().ifBlank { null }
                    if (isGenericTarget(single, locale)) Pair(null, null) else Pair(null, single)
                }
            }
        }

        // Middle-preposition pattern "<item...> <prep> <hint>"
        val hintPrepositionsEn = listOf(" in ", " to ", " for ", " into ")
        val hintPrepositionsRu = listOf(" в ", " к ", " для ", " на ", " по ")
        val prepositions = if (locale == ChatLocale.Ru) hintPrepositionsRu else hintPrepositionsEn

        for (prep in prepositions.sortedByDescending { it.length }) {
            val idx = remainder.lastIndexOf(prep)
            if (idx > 0) {
                val itemText = remainder.substring(0, idx).trim()
                val hint = remainder.substring(idx + prep.length).trim().ifBlank { null }
                if (itemText.isNotBlank()) {
                    // Generic-target detection: "add milk to a checklist" → hint="a checklist"
                    // is a bare generic word, not a real list. Drop it (→ "which list?" choice)
                    // and strip any leading filler item-word off the item text.
                    return if (isGenericTarget(hint, locale)) {
                        Pair(stripLeadingFiller(itemText, locale), null)
                    } else {
                        Pair(itemText, hint)
                    }
                }
            }
        }

        return Pair(remainder.trim(), null)
    }

    /**
     * True when [hint] is a bare GENERIC target word ("checklist"/"list" / "чеклист"/"список"),
     * not a real list name. Such a hint must be dropped so the caller offers a "which list?"
     * choice instead of trying to match a non-existent list literally named "checklist".
     *
     * Matching is EXACT after trimming a leading EN article ("a "/"an "/"the "): "shopping list"
     * is a real list name and must NOT count as generic — only the bare word "list"/"checklist".
     */
    private fun isGenericTarget(hint: String?, locale: ChatLocale): Boolean {
        val candidate = hint?.trim()?.lowercase()?.let { stripLeadingArticleEn(it, locale) } ?: return false
        val generics = if (locale == ChatLocale.Ru) {
            setOf("чеклист", "чек-лист", "список")
        } else {
            setOf("checklist", "list")
        }
        return candidate in generics
    }

    /**
     * Strips a leading EN article ("a "/"an "/"the ") while preserving the case of the rest.
     * Used for generic-target matching (lowercased input) and for create-with-items name parts
     * (case-preserved input like "A Trip List" → "Trip List"). RU has no articles.
     */
    private fun stripLeadingArticleEn(text: String, locale: ChatLocale): String {
        if (locale == ChatLocale.Ru) return text
        val lower = text.lowercase()
        for (article in listOf("the ", "an ", "a ")) {
            if (lower.startsWith(article)) return text.substring(article.length).trim()
        }
        return text
    }

    /**
     * Strips a leading FILLER item-word ("пункт"/"item"/"task") off [itemText] — the noise word
     * a user adds when naming a generic target ("добавь в чеклист ПУНКТ молоко" → item "молоко").
     */
    private fun stripLeadingFiller(itemText: String?, locale: ChatLocale): String? {
        val text = itemText?.trim()?.ifBlank { null } ?: return null
        val fillers = if (locale == ChatLocale.Ru) {
            setOf("пункт", "пунктом", "задачу")
        } else {
            setOf("item", "task", "entry")
        }
        val firstSpace = text.indexOf(' ')
        val firstWord = if (firstSpace > 0) text.substring(0, firstSpace) else text
        return if (firstWord.lowercase() in fillers && firstSpace > 0) {
            text.substring(firstSpace + 1).trim().ifBlank { null }
        } else {
            text
        }
    }

    /**
     * True when an attach command's target [itemText] is a bare referential pronoun ("attach THIS")
     * or blank — i.e. it points at the attachment itself, not a named existing item. Such a command
     * means "turn this file into a checklist" → [ToolCall.CreateChecklistFromAttachment]. A real item
     * name ("attach this to milk") is NOT referential and stays an [ToolCall.AttachToItem].
     */
    private fun isReferentialAttachTarget(itemText: String, locale: ChatLocale): Boolean {
        val target = itemText.trim().lowercase()
        if (target.isBlank()) return true
        val referents = if (locale == ChatLocale.Ru) {
            setOf("это", "этого", "всё", "все", "их")
        } else {
            setOf("this", "that", "it", "these", "those")
        }
        return target in referents
    }

    private fun extractChecklistName(lower: String, locale: ChatLocale): String? {
        val prefixesRu = setOf("создай список ", "создай новый список ", "новый список ", "новый чеклист ", "создай чеклист ")
        val prefixesEn = setOf("create checklist ", "create a checklist ", "new checklist ", "create list ", "new list ")
        val prefixes = if (locale == ChatLocale.Ru) prefixesRu else prefixesEn

        for (prefix in prefixes.sortedByDescending { it.length }) {
            if (lower.startsWith(prefix)) {
                return lower.removePrefix(prefix).trim().ifBlank { null }
            }
        }
        // Fallback: return text after any keyword
        return lower.trim().ifBlank { null }
    }

    private fun extractPayloadAfterReminderKeyword(lower: String, locale: ChatLocale): String? {
        val prefixesRu = setOf("напомни мне ", "напомни ", "поставь напоминание ")
        val prefixesEn = setOf("remind me to ", "remind me ", "set a reminder for ", "set reminder for ", "set reminder ")
        val prefixes = if (locale == ChatLocale.Ru) prefixesRu else prefixesEn

        for (prefix in prefixes.sortedByDescending { it.length }) {
            if (lower.startsWith(prefix)) {
                return lower.removePrefix(prefix).trim().ifBlank { null }
            }
        }
        return lower.trim().ifBlank { null }
    }

    /**
     * Extracts the query term from a "find X" / "найди X" command.
     */
    private fun extractQuery(text: String): String {
        val lower = text.trim().lowercase()
        val prefixesRu = listOf("найди ", "найти ", "поищи ")
        val prefixesEn = listOf("find ", "search for ", "look for ", "show me ")
        val prefixes = prefixesRu + prefixesEn
        for (prefix in prefixes.sortedByDescending { it.length }) {
            if (lower.startsWith(prefix)) {
                return lower.removePrefix(prefix).trim()
            }
        }
        return text.trim()
    }

    // ─── State helpers ────────────────────────────────────────────────────────

    private fun addAssistantMessage(
        content: String,
        linkedChecklistId: Long? = null,
        askAiForText: String? = null,
    ) {
        val msg = ChatMessage(
            id = generateId(),
            role = ChatRole.Assistant,
            content = content,
            timestamp = nowMillis(),
            costCredits = 0,
            linkedChecklistId = linkedChecklistId,
            // askAiForText is transient: NOT persisted to Room (toEntry() ignores it).
            // The "Ask AI" button disappears on app restart — intentional to avoid migration.
            askAiForText = askAiForText,
        )
        updateMessages { it + msg }
        // Persist every assistant message regardless of routing layer.
        // NonCancellable so a fast back-nav between updateMessages() and append()
        // doesn't strand the message in state without a Room row.
        viewModelScope.launch {
            withContext(NonCancellable) {
                runCatching { chatHistoryRepository.append(msg) }
                    .onFailure { e -> logger.error(TAG, "addAssistantMessage: persist failed — ${e.message}", e) }
            }
        }
    }

    /**
     * Handles the "Ask AI" fallback button tap on an Unknown-intent response.
     *
     * Escalates [text] (the original user input that produced the Unknown response) to
     * Layer 3 via [runAgentTurn]. This is an explicit user opt-in — credits are only
     * spent when the user taps the button, never automatically on Unknown classification.
     *
     * The user message is NOT re-added (it already exists in history from [handleSend]).
     * We set [isProcessing] to match the UX of any other Layer 3 call.
     */
    private fun handleAskAiFallback(text: String) {
        _screenState.value = _screenState.value.copy(isProcessing = true)
        viewModelScope.launch {
            runCatching {
                val locale = localeProvider.current()
                // Text is already in chat history from handleSend.
                // Always routed through the stateless agent loop (chat_agent CF).
                runAgentTurn(text, locale)
            }.onFailure { e ->
                logger.error(TAG, "handleAskAiFallback failed", e)
                _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_generic_error"))
                _screenState.value = _screenState.value.copy(isProcessing = false)
            }
        }
    }

    private fun updateMessages(transform: (List<ChatMessage>) -> List<ChatMessage>) {
        _screenState.value = _screenState.value.copy(
            messages = transform(_screenState.value.messages)
        )
    }

    private fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        _screenState.value = _screenState.value.copy(
            messages = _screenState.value.messages.map { if (it.id == id) transform(it) else it }
        )
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    private fun generateId(): String = "${nowMillis()}_${Random.nextInt(0, 100_000)}"

    private companion object {
        const val TAG = "ChatViewModel"
        // Stable chip ids for the AiChoiceResponse block.
        const val CHOICE_EXECUTE = "execute"
        const val CHOICE_EXECUTE_ALL = "execute_all"
        const val CHOICE_EDIT = "edit"
        const val CHOICE_ESCAPE = "escape"
        const val CHOICE_CANDIDATE_PREFIX = "candidate_"
        const val CHOICE_OPTION_PREFIX = "option_"
        /**
         * Max tappable options shown in a choice block ("which list?" / ambiguous-match chips).
         * The adaptive FlowRow wraps to as many rows as needed, so 6 stays readable on a phone dock.
         */
        const val MAX_CHOICE_OPTIONS = 6
        /** Max messages to display from persisted history on screen open. */
        const val HISTORY_DISPLAY_LIMIT = 20
        /** Retries for the startup history seed when the DB driver isn't ready yet (wasmJs OPFS race). */
        const val HISTORY_LOAD_MAX_RETRIES = 5L
        /** Backoff between history-seed retries; ~5×400ms covers OPFS Web Worker warm-up. */
        const val HISTORY_LOAD_RETRY_DELAY_MS = 400L
        /** Max checklists to include in Layer 3 context summary. */
        const val CHECKLIST_SUMMARY_LIMIT = 8
        /**
         * Max recent items sent PER checklist in the Layer 3 context (the freshest tail of the list).
         * Small by design — the goal is "what did I add recently", not a full export.
         */
        const val RECENT_ITEMS_PER_CHECKLIST = 6
        /**
         * Global cap on recent items sent across ALL checklists in one request. Bounds token cost
         * (and the amount of item text leaving the device) no matter how many lists the user has.
         */
        const val RECENT_ITEMS_TOTAL_BUDGET = 30
        /** Free-tier attachment limit per chat message (mirrors item-attachments FREE_LIMIT = 3). */
        const val MAX_ATTACHMENTS_FREE = 3
        /** Premium users: generous cap to prevent accidental runaway picks. */
        const val MAX_ATTACHMENTS_PREMIUM = 20
        /**
         * Maximum agent rounds per user turn.
         *
         * After 5 ToolCalls rounds without a Final, the loop emits a fallback message
         * and returns so the user can steer the conversation. This caps credits at
         * 5 × 3 = 15 credits per turn (first round costs 3; subsequent rounds cost 0
         * because the transcript already has tool turns — the CF only charges on the
         * first round without a tool turn).
         */
        const val AGENT_MAX_ROUNDS = 5
    }
}
