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
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
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
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatHistoryRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscriptionOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent.AgentToolCallMapper
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent.AgentToolResultSerializer
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview.ToolCallPreviewRenderer
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * ViewModel for the AI Chat screen.
 *
 * State machine (idle ↔ processing ↔ preview):
 *   1. idle: [ChatScreenState.isProcessing]=false, pendingPreview=null
 *   2. OnSendClick → blank check → classify intent → resolve to preview (write) or inline (read)
 *   3. OnPreviewApply → dispatch ToolCall → success message
 *   4. OnPreviewCancel → back to idle, no snackbar
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
     * Pause/resume mechanism for the agent loop plan-card.
     *
     * When the agent returns mutating tool calls, [runAgentTurn] sets this to a new
     * [CompletableDeferred] and suspends on [await()]. The intent handlers for
     * [ChatScreenIntent.OnAgentPlanApply] and [ChatScreenIntent.OnAgentPlanCancel]
     * complete it with true/false respectively. The loop resumes after the user decides.
     * Cleared to null at the start of each new turn so stale completions are ignored.
     */
    @Volatile
    private var _pendingAgentDecision: CompletableDeferred<Boolean>? = null

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
        viewModelScope.launch {
            runCatching {
                chatHistoryRepository.observeRecent(HISTORY_DISPLAY_LIMIT).first().also { history ->
                    if (history.isNotEmpty()) {
                        _screenState.value = _screenState.value.copy(messages = history)
                        logger.debug(TAG, "init: loaded ${history.size} messages from history")
                    }
                }
            }.onFailure { e ->
                logger.error(TAG, "init: failed to load history — ${e.message}", e)
                _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_history_load_error"))
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

            is ChatScreenIntent.OnPreviewItemTextChange -> {
                val current = _screenState.value.pendingPreview ?: return
                _screenState.value = _screenState.value.copy(
                    pendingPreview = current.copy(editableItemText = intent.text)
                )
            }

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

            ChatScreenIntent.OnPreviewApply -> handlePreviewApply()

            ChatScreenIntent.OnPreviewCancel -> {
                _screenState.value.pendingPreview?.let { preview ->
                    analytics.event(
                        name = AnalyticsEvents.Chat.PREVIEW_REJECTED,
                        params = mapOf(AnalyticsParams.ACTION_TYPE to (preview.toolCall::class.simpleName ?: "unknown")),
                    )
                }
                _screenState.value = _screenState.value.copy(pendingPreview = null)
                // Emit assistant message to confirm cancellation — silent dismiss is FORBIDDEN
                // (CLAUDE.md rule). The message appears in chat history as an inline reply.
                viewModelScope.launch {
                    _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_preview_cancelled_message"))
                }
            }

            ChatScreenIntent.OnPreviewReject -> handlePreviewReject()

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

            // ── Agent plan-card intents (Phase 2d) ────────────────────────────
            // The agent plan-card is the Layer-3 equivalent of the preview confirm funnel,
            // so it emits the same PREVIEW_CONFIRMED / PREVIEW_REJECTED events. ACTION_TYPE
            // is "agent_plan" (the card batches N mutating calls; per-call action types were
            // already emitted as PREVIEW_SHOWN when the card appeared).
            ChatScreenIntent.OnAgentPlanApply -> {
                analytics.event(
                    name = AnalyticsEvents.Chat.PREVIEW_CONFIRMED,
                    params = mapOf(AnalyticsParams.ACTION_TYPE to "agent_plan"),
                )
                _pendingAgentDecision?.complete(true)
            }

            ChatScreenIntent.OnAgentPlanCancel -> {
                analytics.event(
                    name = AnalyticsEvents.Chat.PREVIEW_REJECTED,
                    params = mapOf(AnalyticsParams.ACTION_TYPE to "agent_plan"),
                )
                _pendingAgentDecision?.complete(false)
            }

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
            // MVP: emit an Amplitude/Firebase event with the question/answer/feedback
            // triplet so the team can mine it offline. Real per-feedback Cloud Function
            // endpoint will be swapped in later (event remains in addition / as a backup).
            // Pending: docs/todos/2026-05-17-chat-feedback-real-endpoint.md
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
                        val humanReadable = previewRenderer.render(toolCall)
                        _screenState.value = _screenState.value.copy(
                            pendingPreview = PendingPreview(
                                toolCall = toolCall,
                                humanReadable = humanReadable,
                                targetChecklistHint = extractHint(toolCall),
                                editableItemText = extractItemText(toolCall),
                                originalText = text,
                                sourceLayer = classification.layer,
                            ),
                            isProcessing = false,
                        )
                        trackPreviewShown(toolCall)
                        trackResponseReceived(classification.layer, outcome = "preview")
                    }

                    // AttachToItem — show preview only if attachments are present;
                    // otherwise emit a snackbar (silent-skip is forbidden).
                    is ChatIntent.AttachToItem -> {
                        val currentAttachments = _screenState.value.pendingAttachments
                        if (currentAttachments.isEmpty()) {
                            _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_attach_no_files"))
                            trackResponseReceived(classification.layer, outcome = "answer")
                            _screenState.value = _screenState.value.copy(isProcessing = false)
                            return@runCatching
                        }
                        val builtToolCall = ToolCall.AttachToItem(
                            checklistHint = intent.checklistHint,
                            itemText = intent.itemText,
                            attachments = currentAttachments,
                        )
                        // P5: bias to the open checklist when the user didn't name a list.
                        val toolCall = biasToolCallToContext(builtToolCall)
                        val humanReadable = previewRenderer.render(toolCall)
                        _screenState.value = _screenState.value.copy(
                            pendingPreview = PendingPreview(
                                toolCall = toolCall,
                                humanReadable = humanReadable,
                                targetChecklistHint = extractHint(toolCall),
                                editableItemText = intent.itemText,
                                originalText = text,
                                sourceLayer = classification.layer,
                            ),
                            isProcessing = false,
                        )
                        trackPreviewShown(toolCall)
                        trackResponseReceived(classification.layer, outcome = "preview")
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

    // ─── Preview apply flow ───────────────────────────────────────────────────

    private fun handlePreviewApply() {
        val preview = _screenState.value.pendingPreview ?: return
        analytics.event(
            name = AnalyticsEvents.Chat.PREVIEW_CONFIRMED,
            params = mapOf(AnalyticsParams.ACTION_TYPE to (preview.toolCall::class.simpleName ?: "unknown")),
        )
        _screenState.value = _screenState.value.copy(isProcessing = true)

        viewModelScope.launch {
            runCatching {
                // Apply user's edits (if any) before dispatching.
                val finalToolCall = applyEditedText(preview.toolCall, preview.editableItemText)
                val outcome = toolCallDispatcher.dispatch(finalToolCall)
                _screenState.value = _screenState.value.copy(pendingPreview = null)
                handleOutcomeInline(outcome)
            }.onFailure { e ->
                logger.error(TAG, "handlePreviewApply failed", e)
                _screenState.value = _screenState.value.copy(pendingPreview = null)
                _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_apply_error"))
            }
            _screenState.value = _screenState.value.copy(isProcessing = false)
        }
    }

    // ─── Preview reject flow ("I meant something else") ─────────────────────

    /**
     * User rejected the pending preview and wants the input re-classified in the next layer.
     *
     * Escalation logic:
     * - Source = [RoutingLayer.Local]  → re-classify with [skipLayer1=true] (Layer 2, 1 credit).
     *   If Layer 2 returns a command-intent → show a NEW preview with [sourceLayer=Classifier].
     *   If Layer 2 returns FreeForm / Unknown → delegate to [runAgentTurn].
     * - Source = [RoutingLayer.Classifier] → skip straight to Layer 3 via [runAgentTurn].
     * - Source = [RoutingLayer.FullChat] → safety fallback: Layer 3 never produces a preview
     *   card, so this branch should not occur in practice.
     *
     * The user message is NOT re-persisted — it already lives in Room from [handleSend].
     * Credits for Layer 2 are deducted server-side; credit balance is reconciled via
     * [userDataRepository.getUserDataFlow] collector in init.
     */
    private fun handlePreviewReject() {
        val preview = _screenState.value.pendingPreview ?: return
        analytics.event(
            name = AnalyticsEvents.Chat.PREVIEW_REJECTED,
            params = mapOf(AnalyticsParams.ACTION_TYPE to (preview.toolCall::class.simpleName ?: "unknown")),
        )

        // Guard: attachment-only previews have no original text to re-classify.
        // The UI hides the Reject button for CreateChecklistFromAttachment, but
        // defensive check here prevents a silent mis-classification.
        if (preview.originalText.isBlank()) {
            viewModelScope.launch {
                _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_extract_fail"))
            }
            _screenState.value = _screenState.value.copy(pendingPreview = null)
            return
        }

        _screenState.value = _screenState.value.copy(
            pendingPreview = null,
            isProcessing = true,
        )

        viewModelScope.launch {
            runCatching {
                val locale = localeProvider.current()
                when (preview.sourceLayer) {
                    RoutingLayer.Local -> {
                        // Layer 1 → Layer 2 escalation
                        logger.info(TAG, "handlePreviewReject: Layer1 source → escalating to Layer2 (skipLayer1=true)")
                        val classification = aiChatRepository.classify(
                            input = preview.originalText,
                            locale = locale,
                            skipLayer1 = true,
                        )
                        logger.debug(TAG, "Reject re-classify → ${classification.intent::class.simpleName} layer=${classification.layer}")

                        when (val intent = classification.intent) {
                            // FreeForm / Unknown from Layer 2 → escalate to Layer 3 (agent)
                            ChatIntent.FreeForm,
                            is ChatIntent.Unknown -> {
                                runAgentTurn(preview.originalText, locale)
                            }

                            // FindItems is a read-intent inline result, no preview card
                            ChatIntent.FindItems -> {
                                val query = extractQuery(preview.originalText)
                                val outcome = toolCallDispatcher.dispatch(ToolCall.FindItemsQuery(query))
                                handleOutcomeInline(outcome)
                                _screenState.value = _screenState.value.copy(isProcessing = false)
                            }

                            // Write-intent → build a new preview with sourceLayer=Classifier
                            ChatIntent.CreateItem,
                            ChatIntent.DeleteItem,
                            ChatIntent.CompleteItem,
                            is ChatIntent.CreateChecklist,
                            ChatIntent.SetReminder,
                            ChatIntent.MoveReminders -> {
                                val builtToolCall = classification.preBuiltToolCall
                                    ?: buildToolCall(intent, preview.originalText, locale)
                                if (builtToolCall == null) {
                                    _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_extract_fail"))
                                    _screenState.value = _screenState.value.copy(isProcessing = false)
                                    return@runCatching
                                }
                                // P5: bias list-less commands to the open checklist (same as the
                                // initial send path), so the re-classified preview stays on context.
                                val toolCall = biasToolCallToContext(builtToolCall)
                                val humanReadable = previewRenderer.render(toolCall)
                                _screenState.value = _screenState.value.copy(
                                    pendingPreview = PendingPreview(
                                        toolCall = toolCall,
                                        humanReadable = humanReadable,
                                        targetChecklistHint = extractHint(toolCall),
                                        editableItemText = extractItemText(toolCall),
                                        originalText = preview.originalText,
                                        sourceLayer = classification.layer,
                                    ),
                                    isProcessing = false,
                                )
                            }

                            // AttachToItem without active attachments → can't re-build
                            is ChatIntent.AttachToItem -> {
                                _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_attach_no_files"))
                                _screenState.value = _screenState.value.copy(isProcessing = false)
                            }
                        }
                    }

                    RoutingLayer.Classifier -> {
                        // Layer 2 → Layer 3 escalation (agent)
                        logger.info(TAG, "handlePreviewReject: Classifier source → escalating to Layer3 (agent)")
                        runAgentTurn(preview.originalText, locale)
                    }

                    RoutingLayer.FullChat -> {
                        // Safety fallback: Layer 3 never produces preview cards, so this
                        // branch should not be reachable. Show a generic hint.
                        logger.warning(TAG, "handlePreviewReject: unexpected FullChat sourceLayer — ignoring")
                        _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_unknown_intent_hint"))
                        _screenState.value = _screenState.value.copy(isProcessing = false)
                    }
                }
            }.onFailure { e ->
                logger.error(TAG, "handlePreviewReject failed", e)
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
                val humanReadable = previewRenderer.render(toolCall)
                _screenState.value = _screenState.value.copy(
                    pendingPreview = PendingPreview(
                        toolCall = toolCall,
                        humanReadable = humanReadable,
                        targetChecklistHint = null,
                        editableItemText = "",
                    ),
                    isProcessing = false,
                )
                trackPreviewShown(toolCall)
                trackResponseReceived(RoutingLayer.Local, outcome = "preview")
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
     * Builds a compact summary of user's top [CHECKLIST_SUMMARY_LIMIT] checklists.
     * Only names + item counts are included — no item text (privacy by design).
     */
    private suspend fun buildChecklistsSummary(): List<ChecklistContext> = runCatching {
        checklistRepository.checklists.first()
            .take(CHECKLIST_SUMMARY_LIMIT)
            .map { checklist ->
                ChecklistContext(
                    name = checklist.name,
                    totalItems = checklist.items.size,
                    doneItems = checklist.items.count { it.checked },
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
     *       - Mutating calls → build [AgentPlan], show plan-card, suspend until user decides
     *         ([OnAgentPlanApply] → dispatch; [OnAgentPlanCancel] → declined results).
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

                    // Dispatch mutating calls — show plan-card if any.
                    val mutatingResults = mutableListOf<AgentToolResult>()
                    if (mutatingCalls.isNotEmpty()) {
                        // Build plan items for the card.
                        val planItems = mutatingCalls.map { call ->
                            val toolCall = AgentToolCallMapper.map(call)
                            val text = if (toolCall != null) previewRenderer.render(toolCall) else call.name
                            AgentPlanItem(text = text, isDestructive = call.name == "delete_item")
                        }
                        val plan = AgentPlan(items = planItems)

                        // Suspend the loop — show plan-card, wait for user.
                        // The plan-card is the agent's preview funnel: emit PREVIEW_SHOWN per
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
                        _screenState.value = _screenState.value.copy(
                            isProcessing = false,
                            pendingAgentPlan = plan,
                        )

                        val approved = decision.await()

                        // Clear plan-card and resume processing.
                        _pendingAgentDecision = null
                        _screenState.value = _screenState.value.copy(
                            pendingAgentPlan = null,
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

    private suspend fun handleOutcomeInline(outcome: DispatchOutcome) {
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
                val candidates = outcome.candidates.take(3).joinToString(", ")
                _sideEffect.emit(
                    ChatScreenSideEffect.ShowAssistantMessage(
                        messageKey = "chat_ambiguous_match",
                        args = listOf(candidates),
                    )
                )
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
                if (itemText.isNullOrBlank()) null
                else ToolCall.AddItem(checklistHint = hint, itemText = itemText)
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
                if (name.isNullOrBlank()) null
                else ToolCall.CreateChecklist(name = name, initialItems = emptyList())
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
                    val hint = afterPrep.substring(0, firstSpace).trim().ifBlank { null }
                    val itemText = afterPrep.substring(firstSpace + 1).trim().ifBlank { null }
                    Pair(itemText, hint)
                } else {
                    // single word after prep → treat as hint only; user didn't name an item
                    Pair(null, afterPrep.trim().ifBlank { null })
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
                if (itemText.isNotBlank()) return Pair(itemText, hint)
            }
        }

        return Pair(remainder.trim(), null)
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
        /** Max messages to display from persisted history on screen open. */
        const val HISTORY_DISPLAY_LIMIT = 20
        /** Max checklists to include in Layer 3 context summary. */
        const val CHECKLIST_SUMMARY_LIMIT = 8
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
