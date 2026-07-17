package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AiModelExperimentTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.datastore.api.AiChatPreferencesRepository
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher.ToolCallDispatcher
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatChoice
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceAction
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceObjectRow
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceOption
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RowEmphasis
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RowKind
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.UndoHandle
import com.antonchuraev.homesearchchecklist.feature.aichat.api.format.ChatDateFormatter
import com.antonchuraev.homesearchchecklist.feature.aichat.api.locale.ChatLocaleProvider
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentStepResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentTranscriptRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AiChatRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistItemContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatHistoryRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscriptionOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent.AgentToolCallMapper
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent.AgentToolResultSerializer
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent.AgentTranscriptWindow
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview.ToolCallPreviewRenderer
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistNodeType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlin.concurrent.Volatile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
import aichecklists.core.designsystem.generated.resources.chat_choice_apply_actions
import aichecklists.core.designsystem.generated.resources.chat_choice_cancel
import aichecklists.core.designsystem.generated.resources.chat_choice_create_from_file
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
import aichecklists.core.designsystem.generated.resources.chat_choice_executing_undo
import aichecklists.core.designsystem.generated.resources.chat_choice_move_reminders
import aichecklists.core.designsystem.generated.resources.chat_choice_move_to_list
import aichecklists.core.designsystem.generated.resources.chat_choice_other
import aichecklists.core.designsystem.generated.resources.chat_choice_undo
import aichecklists.core.designsystem.generated.resources.chat_choice_which_list
import aichecklists.core.designsystem.generated.resources.chat_choice_which_list_truncated
import aichecklists.core.designsystem.generated.resources.chat_question_add
import aichecklists.core.designsystem.generated.resources.chat_question_attach
import aichecklists.core.designsystem.generated.resources.chat_question_complete
import aichecklists.core.designsystem.generated.resources.chat_question_create
import aichecklists.core.designsystem.generated.resources.chat_question_delete
import aichecklists.core.designsystem.generated.resources.chat_question_set_reminder
import aichecklists.core.designsystem.generated.resources.chat_choice_action_create_items
import aichecklists.core.designsystem.generated.resources.chat_object_file_a11y
import aichecklists.core.designsystem.generated.resources.chat_object_item_a11y
import aichecklists.core.designsystem.generated.resources.chat_object_name_a11y
import aichecklists.core.designsystem.generated.resources.chat_object_time_a11y
import aichecklists.core.designsystem.generated.resources.chat_preview_checklist_label
import aichecklists.core.designsystem.generated.resources.chat_preview_files_count
import aichecklists.core.designsystem.generated.resources.items_count
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
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
    // Shared with previewRenderer on purpose: the agent batch and the D2 object rows must spell
    // one moment the same way, and two formatters would drift.
    private val dateFormatter: ChatDateFormatter,
    private val localeProvider: ChatLocaleProvider,
    private val chatHistoryRepository: ChatHistoryRepository,
    // Stage 3: persists the agent's tool rounds so a past turn's ping-pong survives a restart.
    // Kept separate from chatHistoryRepository on purpose — history owns the visible prose,
    // this owns the machine rounds, and neither duplicates the other.
    private val agentTranscriptRepository: AgentTranscriptRepository,
    private val checklistRepository: ChecklistRepository,
    private val userDataRepository: UserDataRepository,
    private val aiChatPreferencesRepository: AiChatPreferencesRepository,
    private val analytics: AnalyticsTracker,
    private val aiModelExperimentTracker: AiModelExperimentTracker,
    // Reads ai_daily_limit_premium for the out-of-credits paywall CTA label. The CTA promises a
    // concrete allowance ("300 credits now and every day"), so the number must track the live RC
    // value — a literal turns into a false promise the day the limit is retuned.
    private val remoteConfigProvider: RemoteConfigProvider,
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

        // D2 memory of choice: resolve the remembered default id to a NAME for the settings sheet.
        // Combined with the live checklists so a renamed list re-labels and a deleted one clears
        // the row — a reset control pointing at a list that no longer exists is worse than none.
        viewModelScope.launch {
            combine(
                aiChatPreferencesRepository.defaultChecklistIdFlow,
                checklistRepository.checklists,
            ) { id, checklists -> checklists.firstOrNull { it.id == id }?.name }
                .catch { e -> logger.error(TAG, "Default checklist name flow failed", e) }
                .collect { name ->
                    _screenState.value = _screenState.value.copy(defaultChecklistName = name)
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

            is ChatScreenIntent.OnChoiceMemoryToggle -> {
                val current = _screenState.value.pendingChoice ?: return
                // Bookkeeping only — nothing is persisted until a candidate chip is tapped, since
                // the preference is "that list from now on" and there is no "that list" yet.
                _screenState.value = _screenState.value.copy(
                    pendingChoice = current.copy(rememberChoice = intent.enabled),
                )
            }

            ChatScreenIntent.OnResetDefaultChecklist -> {
                viewModelScope.launch {
                    runCatching { aiChatPreferencesRepository.setDefaultChecklistId(null) }
                        .onFailure { e ->
                            logger.error(TAG, "Failed to reset default checklist", e)
                            _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_generic_error"))
                        }
                }
            }

            ChatScreenIntent.OnChoiceDismissed -> handleChoiceDismissed()

            ChatScreenIntent.OnChoiceEditConfirmed -> handleChoiceEditConfirmed()

            is ChatScreenIntent.AppendAssistantMessage -> {
                // ChatRoute resolved a localised messageKey and is round-tripping
                // the final text back so it lands in chat history with correct locale.
                // linkedChecklistId is preserved for the "Open checklist" button.
                // askAiForText is preserved for the "Ask AI" fallback button on Unknown responses.
                // paywallCtaCredits is preserved for the "Become Pro" CTA on out-of-credits replies.
                addAssistantMessage(
                    intent.text,
                    linkedChecklistId = intent.linkedChecklistId,
                    askAiForText = intent.askAiForText,
                    paywallCtaCredits = intent.paywallCtaCredits,
                )
            }

            ChatScreenIntent.OnPaywallCtaClick -> {
                // The user acted on the out-of-credits reply. Nothing to undo or clean up —
                // just hand them to the paywall (both hosts observe NavigateToPaywall).
                //
                // The source tag is the point, not decoration: "paywall_shown with source=chat —
                // expect a rise in users hitting the limit" is the post-release signal the Layer 1
                // disconnect is judged on (docs/decisions/2026-07-15-remove-ai-chat-layer1.md).
                // Merged with the credits chip, that number cannot answer the question it was
                // written for.
                viewModelScope.launch {
                    _sideEffect.emit(
                        ChatScreenSideEffect.NavigateToPaywall(
                            source = ChatScreenSideEffect.NavigateToPaywall.SOURCE_INSUFFICIENT_CREDITS,
                        )
                    )
                }
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
                    // Clear the agent's tool memory too, else "Clear chat" would wipe the visible
                    // prose while the model kept privately remembering the rounds behind it.
                    agentTranscriptRepository.clear()
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
                // Mic tapped — user initiated a voice recording. Track EVERY mic action (start →
                // cancel / transcribe / fail) so we can measure how popular voice input is and where
                // it drops off. Emit RequestRecordAudioPermission so the UI can ask at the right moment.
                analytics.event(name = AnalyticsEvents.Chat.VOICE_STARTED)
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
     * @param creditsUsed Credits the server actually CHARGED for this turn. Defaults to the
     *   layer's list price ([creditsForLayer]) because a layer that answers always bills it.
     *   Pass 0 when the layer *refused* the turn (HTTP 402): the request reached Layer 2, so
     *   routed_layer is honestly "Classifier", but nothing was charged — defaulting there would
     *   inflate the credits_used sum by one per refusal, which is exactly what shipped.
     */
    private fun trackResponseReceived(
        routedLayer: RoutingLayer?,
        outcome: String,
        creditsUsed: Int? = creditsForLayer(routedLayer),
        modelVariant: String? = null,
        modelId: String? = null,
        aiFlow: String? = null,
    ) {
        val params = mutableMapOf<String, Any>(
            AnalyticsParams.ROUTED_LAYER to (routedLayer?.name ?: "unknown"),
            AnalyticsParams.OUTCOME to outcome,
        )
        creditsUsed?.let { params[AnalyticsParams.CREDITS_USED] = it }
        _turnStartMs?.let { start -> params[AnalyticsParams.LATENCY_MS] = nowMillis() - start }
        _turnStartMs = null
        // Guardrail dimensions for the AI-model A/B test — present only on Layer 3 agent responses
        // that carry them; omitted for Layer 1/2 and error paths so the event schema stays clean.
        modelVariant?.let { params[AnalyticsParams.AI_MODEL_VARIANT] = it }
        modelId?.let { params[AnalyticsParams.AI_MODEL_ID] = it }
        aiFlow?.let { params[AnalyticsParams.AI_FLOW] = it }
        analytics.event(name = AnalyticsEvents.Chat.RESPONSE_RECEIVED, params = params)
    }

    /** The AI-model A/B arm carried by a step result, if any (only success-carrying variants have it). */
    private fun AgentStepResult.modelVariantOrNull(): String? = when (this) {
        is AgentStepResult.ToolCalls -> modelVariant
        is AgentStepResult.Final -> modelVariant
        is AgentStepResult.Options -> modelVariant
        AgentStepResult.InsufficientCredits,
        AgentStepResult.NetworkError,
        AgentStepResult.ServiceError,
        -> null
    }

    /** The concrete AI model id carried by a step result, if any. */
    private fun AgentStepResult.modelIdOrNull(): String? = when (this) {
        is AgentStepResult.ToolCalls -> modelId
        is AgentStepResult.Final -> modelId
        is AgentStepResult.Options -> modelId
        AgentStepResult.InsufficientCredits,
        AgentStepResult.NetworkError,
        AgentStepResult.ServiceError,
        -> null
    }

    /** The server AI flow tag carried by a step result, if any. */
    private fun AgentStepResult.aiFlowOrNull(): String? = when (this) {
        is AgentStepResult.ToolCalls -> aiFlow
        is AgentStepResult.Final -> aiFlow
        is AgentStepResult.Options -> aiFlow
        AgentStepResult.InsufficientCredits,
        AgentStepResult.NetworkError,
        AgentStepResult.ServiceError,
        -> null
    }

    /**
     * The Premium daily AI credit allowance advertised by the out-of-credits CTA.
     *
     * Read from Remote Config on every use (cheap local lookup) rather than cached: the CTA
     * promises a concrete number to a user who is about to pay for it, so a stale/hardcoded
     * value is a false promise. Falls back to the compiled default before the first fetch.
     */
    private fun premiumDailyCredits(): Int =
        remoteConfigProvider
            .getLong(RemoteConfigKeys.AI_DAILY_LIMIT_PREMIUM, RemoteConfigDefaults.AI_DAILY_LIMIT_PREMIUM)
            .toInt()

    /**
     * The single answer to "the server refused this turn — the wallet is empty" (HTTP 402),
     * whichever layer hit the wall.
     *
     * EVERY 402 on a chat turn routes here, and new ones must too. The bug this exists to prevent
     * is not one bad branch but a family of them: each 402 site grew its own dialect of "out of
     * credits" (Layer 2 blamed the user's phrasing via Unknown; Layer 3 showed a CTA-less snackbar
     * tagged outcome="error" and billed 3 credits for a turn that cost 0), and fixing them one at
     * a time is how the survivors stayed hidden. One helper = one behaviour:
     *
     *   - The reply states the billing reason and carries the paywall CTA; no "Ask AI" button
     *     (it asks an empty wallet for 3 more credits and 402s again).
     *   - Analytics report outcome="insufficient_credits" with credits_used=0 — a refused turn is
     *     neither an "answer" nor an "error" nor a charge.
     *   - The spinner stops.
     *
     * Call sites: the send path and the "I meant something else" reject path (both Layer 2
     * classify() 402s, see [ChatIntent.InsufficientCredits]) and the Layer 3 agent loop
     * ([AgentStepResult.InsufficientCredits], reached via Deep Thinking ON / a vague L2 / a
     * Classifier-source escalation).
     *
     * @param layer The layer that refused; reported verbatim as routed_layer, so the funnel can
     *   still tell a 1-credit refusal from a 3-credit one even though both charged nothing.
     */
    private suspend fun emitInsufficientCredits(layer: RoutingLayer?) {
        logger.info(TAG, "${layer?.name ?: "Unknown layer"} refused the turn (402) — replying out-of-credits + paywall CTA")
        _sideEffect.emit(
            ChatScreenSideEffect.ShowAssistantMessage(
                messageKey = "chat_insufficient_credits",
                paywallCtaCredits = premiumDailyCredits(),
            )
        )
        trackResponseReceived(layer, outcome = OUTCOME_INSUFFICIENT_CREDITS, creditsUsed = 0)
        _screenState.value = _screenState.value.copy(isProcessing = false)
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

        // Typing past a post-action offer (Undo / move) retires it — the user moved on, and the
        // Undo handle belongs to the previous turn. Questions are NOT cleared here: their input is
        // hidden, so reaching this line with one pending is not possible.
        _screenState.value.pendingChoice?.takeIf { it.isPostAction }?.let { clearChoice() }

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
                val userCost = when {
                    // 402: Layer 2 REFUSED the turn, so it charged nothing. Priced off the layer
                    // alone (until 2026-07-16) this row claimed 1 credit and made the chat's own
                    // history disagree with the wallet. Must stay ahead of the Classifier branch.
                    classification.intent is ChatIntent.InsufficientCredits -> 0
                    // Layer 2: a successful classify_chat_intent costs 1 credit.
                    classification.layer == RoutingLayer.Classifier -> 1
                    // Layer 1 (local, parked) is free; unknown → 0.
                    else -> 0
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
                    // Out of credits — answer with the truth, not the "I didn't catch that" hint,
                    // and offer the paywall. No askAiForText here on purpose: "Ask AI" asks an
                    // empty wallet for 3 MORE credits, 402s again, and was how the user had to
                    // discover the real reason.
                    ChatIntent.InsufficientCredits -> emitInsufficientCredits(classification.layer)

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
                        // Covers both single (AddItem) and multi (AddItems) adds; withTarget() handles both.
                        val hintlessAdd = (toolCall is ToolCall.AddItem && toolCall.checklistHint == null) ||
                            (toolCall is ToolCall.AddItems && toolCall.checklistHint == null)
                        if (hintlessAdd) {
                            // D2 memory of choice: the user explicitly asked us to stop asking, so
                            // route to their default instead of re-opening the picker. Routed by ID
                            // (the preference stores one) with the name along for copy; a default
                            // that no longer exists resolves to null and we fall through to asking
                            // — the safe direction.
                            val remembered = rememberedDefault()
                            if (remembered != null) {
                                val routed = toolCall.withTarget(remembered.id, remembered.name) ?: toolCall
                                dispatchReversible(routed, sourceLayer = classification.layer)
                                return@runCatching
                            }
                            val names = runCatching {
                                checklistRepository.checklists.first().map { it.name }
                            }.getOrDefault(emptyList())
                            if (names.size >= 2) {
                                showWhichListChoice(toolCall, names, sourceLayer = classification.layer)
                                return@runCatching
                            }
                        }
                        // ── Ceremony proportional to reversibility (D1) ───────────────────
                        // Adding an item and ticking one off are one-tap reversible, so a
                        // confirmation question costs the user a round-trip and buys nothing:
                        // apply immediately and offer Undo afterwards. Everything else
                        // (delete / create / reminder / attach) still asks first.
                        if (toolCall is ToolCall.AddItem || toolCall is ToolCall.CompleteItem) {
                            dispatchReversible(toolCall, sourceLayer = classification.layer)
                            return@runCatching
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
                    label = toolCall.outcomeLabel(),
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

        // The question is argument-less; the OBJECT of the action rides in typed rows under it
        // (D2). A one-slot prompt could only ever name one of item/list — which is how
        // "Add to Shopping?" shipped never saying what.
        _choiceSourceLayer = sourceLayer
        _screenState.value = _screenState.value.copy(
            pendingChoice = PendingChoice(
                choice = ChatChoice(
                    prompt = choiceString(toolCall.questionRes()),
                    options = options,
                    escape = escape,
                    objectRows = buildObjectRows(toolCall),
                ),
            ),
            isProcessing = false,
        )
        trackPreviewShown(toolCall)
        trackResponseReceived(sourceLayer, outcome = "preview")
    }

    // ─── D2 object rows — the typed WHAT of a pending action ──────────────────

    /**
     * Builds the typed object rows for a single write-intent [toolCall] (the D2 replacement for
     * D1's one flat preview line).
     *
     * Each entity gets its own row, so a question with three of them (item + list + time) no
     * longer has to pick one to say out loud. An entity the tool call does not carry produces NO
     * row — absence, not an empty string.
     *
     * The contentDescription is resolved here because `getString` suspends and the renderer is a
     * @Composable: resolving it there would either block a frame or ship an English literal.
     */
    private suspend fun buildObjectRows(toolCall: ToolCall): List<ChoiceObjectRow> = when (toolCall) {
        is ToolCall.AddItem -> buildList {
            add(itemRow(toolCall.itemText))
            addDestination(toolCall.checklistHint)
        }

        is ToolCall.AddItems -> buildList {
            toolCall.itemTexts.forEach { add(itemRow(it)) }
            addDestination(toolCall.checklistHint)
        }

        // The object of a delete is tinted error and carries the trash icon; the destination stays
        // neutral — the list is not what is being destroyed.
        is ToolCall.DeleteItem -> buildList {
            add(itemRow(toolCall.itemText, emphasis = RowEmphasis.Danger))
            addDestination(toolCall.checklistHint)
        }

        is ToolCall.CompleteItem -> buildList {
            add(itemRow(toolCall.itemText))
            addDestination(toolCall.checklistHint)
        }

        // Time is Accent, never Detail: a silent 3 a.m. alarm is the surprise this block exists
        // to prevent, so the moment never sits at supporting emphasis.
        is ToolCall.SetItemReminder -> buildList {
            add(itemRow(toolCall.itemText))
            addDestination(toolCall.checklistHint)
            val at = dateFormatter.formatDateTime(toolCall.at)
            add(
                ChoiceObjectRow(
                    value = at,
                    kind = RowKind.Time,
                    emphasis = RowEmphasis.Accent,
                    contentDescription = choiceString(Res.string.chat_object_time_a11y, at),
                ),
            )
        }

        is ToolCall.CreateChecklist -> buildList {
            add(
                ChoiceObjectRow(
                    value = toolCall.name,
                    kind = RowKind.Name,
                    emphasis = RowEmphasis.Primary,
                    contentDescription = choiceString(Res.string.chat_object_name_a11y, toolCall.name),
                ),
            )
            // The proposed items preview. The renderer caps these per surface and adds the
            // "…and N more" tail — it is the only side that knows dock vs full screen.
            toolCall.initialItems.forEach { item ->
                add(
                    ChoiceObjectRow(
                        value = item,
                        kind = RowKind.Preview,
                        emphasis = RowEmphasis.Detail,
                        contentDescription = choiceString(Res.string.chat_object_item_a11y, item),
                    ),
                )
            }
        }

        // Data gap: MoveAllReminders carries only from/to timestamps — no count. Without one the
        // "5 reminders" row cannot be built, so it is omitted rather than guessed (and the chip
        // degrades to a bare "Move" in outcomeLabel()). Adding the count is a data-layer change.
        is ToolCall.MoveAllReminders -> listOf(
            ChoiceObjectRow(
                value = "${dateFormatter.formatDay(toolCall.fromDayStartMs)} $RANGE_DASH " +
                    dateFormatter.formatDay(toolCall.toDayStartMs),
                kind = RowKind.DateRange,
                emphasis = RowEmphasis.Primary,
                contentDescription = choiceString(
                    Res.string.chat_object_time_a11y,
                    "${dateFormatter.formatDay(toolCall.fromDayStartMs)} $RANGE_DASH " +
                        dateFormatter.formatDay(toolCall.toDayStartMs),
                ),
            ),
        )

        is ToolCall.AttachToItem -> buildList {
            add(fileRow(toolCall.attachments))
            add(itemRow(toolCall.itemText, emphasis = RowEmphasis.Detail))
            addDestination(toolCall.checklistHint)
        }

        is ToolCall.CreateChecklistFromAttachment -> listOf(fileRow(toolCall.attachments))

        // Read-only / agent-only variants never reach a write-intent choice block.
        is ToolCall.FindItemsQuery,
        is ToolCall.ReadChecklist,
        is ToolCall.RenameChecklist -> emptyList()
    }

    private suspend fun itemRow(text: String, emphasis: RowEmphasis = RowEmphasis.Primary) =
        ChoiceObjectRow(
            value = text,
            kind = RowKind.Item,
            emphasis = emphasis,
            contentDescription = choiceString(Res.string.chat_object_item_a11y, text),
        )

    /** Adds the destination row, or nothing when the command named no list (absence, not ""). */
    private suspend fun MutableList<ChoiceObjectRow>.addDestination(hint: String?) {
        if (hint.isNullOrBlank()) return
        add(
            ChoiceObjectRow(
                value = hint,
                kind = RowKind.Destination,
                emphasis = RowEmphasis.Detail,
                // "In checklist: Shopping" — the revived chat_preview_checklist_label reads
                // exactly right as the spoken form of this row.
                contentDescription = choiceString(Res.string.chat_preview_checklist_label, hint),
            ),
        )
    }

    /**
     * The file row: one file → "report.pdf", several → a localized "N files".
     *
     * Deliberately NAME-ONLY, no size. The design calls for "report.pdf • 2.4 MB", but:
     *  1. every ChatRoute picker call site builds its ChatAttachment with `sizeBytes = 0L`, so a
     *     size would render as "• 0 B" on every attach — a confident lie, and
     *  2. the byte formatter lives in feature/home (`AttachmentFullscreenViewer`); reaching it
     *     from here would be a feature→feature dependency, and its unit strings ("KB"/"MB") are
     *     user-facing, so a local copy cannot just hardcode them.
     * Add the size once the pickers carry a real one AND the formatter is extracted to core.
     */
    private suspend fun fileRow(attachments: List<ChatAttachment>): ChoiceObjectRow {
        val first = attachments.firstOrNull()
        val name = if (attachments.size == 1 && first != null) {
            first.fileName
        } else {
            choiceString(Res.string.chat_preview_files_count, attachments.size.toString())
        }
        return ChoiceObjectRow(
            value = name,
            kind = RowKind.File,
            emphasis = RowEmphasis.Primary,
            contentDescription = choiceString(Res.string.chat_object_file_a11y, name),
        )
    }

    /**
     * Shows a "Which list?" choice: one Default Execute chip per candidate list (each re-runs
     * [sourceToolCall] with that list's name as the hint), plus a Dismiss escape.
     *
     * Used both up-front (a generic "add to a checklist" with 2+ lists, no resolvable target)
     * and post-dispatch (an [DispatchOutcome.AmbiguousMatch] where a hint matched several lists).
     * Candidates whose tool call carries no per-list target are skipped (see [ToolCall.withTarget]).
     *
     * Each chip carries its candidate's ID, so two lists sharing a name stay two answers: the meta
     * tells them apart on screen and the id tells them apart on tap. While chips were name-only the
     * second half was missing — the block could show "Shopping • 12" beside "Shopping • 3" and then
     * dispatch the identical call for either, which is the "which list… Shopping, Shopping or
     * Shopping?" trap this picker exists to avoid.
     */
    private suspend fun showWhichListChoice(
        sourceToolCall: ToolCall,
        names: List<String>,
        sourceLayer: RoutingLayer?,
    ) {
        val candidates = rankCandidates(names)
        val shown = candidates.take(MAX_CHOICE_OPTIONS)
        val metas = buildCandidateMetas(shown)
        val options = shown.mapIndexedNotNull { index, candidate ->
            val tc = sourceToolCall.withTarget(candidate.id, candidate.name)
                ?: return@mapIndexedNotNull null
            ChoiceOption(
                id = "$CHOICE_CANDIDATE_PREFIX$index",
                label = candidate.name,
                meta = metas[index],
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
        // Truncation must be visible: silently dropping candidate 7 leaves the user hunting for a
        // list the chat decided not to mention.
        val prompt = if (candidates.size > MAX_CHOICE_OPTIONS) {
            choiceString(
                Res.string.chat_choice_which_list_truncated,
                MAX_CHOICE_OPTIONS.toString(),
                candidates.size.toString(),
            )
        } else {
            choiceString(Res.string.chat_choice_which_list)
        }
        // Memory of choice is offered ONLY here, and only for an add: "always add to this list" is
        // a routing default a user can sensibly hold. "Always delete from this list" is not.
        val isAdd = sourceToolCall is ToolCall.AddItem || sourceToolCall is ToolCall.AddItems
        _choiceSourceLayer = sourceLayer
        _screenState.value = _screenState.value.copy(
            pendingChoice = PendingChoice(
                choice = ChatChoice(
                    prompt = prompt,
                    options = options,
                    escape = escape,
                    // "Which list?" must still say WHAT is going into it — without these rows the
                    // user picks a destination for an unnamed thing.
                    objectRows = buildObjectRows(sourceToolCall),
                ),
                showMemoryToggle = isAdd,
                // Never pre-checked — see PendingChoice.rememberChoice.
                rememberChoice = false,
            ),
            isProcessing = false,
        )
        // PREVIEW_SHOWN keeps the shown->confirmed funnel intact for the which-list picker: a chip
        // tap fires PREVIEW_CONFIRMED via executeChoice, so without this the confirms are orphaned.
        trackPreviewShown(sourceToolCall)
        if (sourceLayer != null) trackResponseReceived(sourceLayer, outcome = "preview")
    }

    /**
     * The remembered default list as (id, name), or null when the chat should keep asking.
     *
     * Resolves the persisted id against the live checklists on every call rather than caching:
     * the list can be renamed or deleted between turns, and a stale name would route items into
     * nothing. A dangling id resolves to null → the picker comes back, which is the safe failure.
     *
     * Returns the id as well as the name so the routed call keeps the identity the preference
     * actually stores. Resolving id → name → id again would silently re-open the ambiguity the
     * user closed: "always add to THIS Shopping" would land in whichever Shopping matched first.
     */
    private suspend fun rememberedDefault(): ListTarget? {
        val id = runCatching { aiChatPreferencesRepository.defaultChecklistIdFlow.first() }
            .getOrNull() ?: return null
        val checklist = runCatching { checklistRepository.checklists.first() }
            .getOrNull()
            ?.firstOrNull { it.id == id }
            ?: return null
        return ListTarget(id = checklist.id, name = checklist.name)
    }

    /** A list the client can name AND point at — see [ToolCall.withTarget]. */
    private data class ListTarget(val id: Long, val name: String)

    // ─── Which-list candidates — ranking + disambiguating meta (D2) ───────────

    /**
     * A candidate list for the which-list picker, with the facts needed to tell it apart.
     *
     * [id] is what makes the picker's answer actionable — a chip labelled "Shopping" is only a
     * question if tapping it dispatches "the list called Shopping". Null only when the name came
     * from somewhere the repository no longer knows about; such a candidate degrades to the name
     * path rather than disappearing.
     */
    private data class ListCandidate(
        val id: Long?,
        val name: String,
        val itemCount: Int,
        val updatedAt: Long,
    )

    /**
     * Resolves candidate [names] against the repository and orders them most-recently-updated first.
     *
     * Ordering is load-bearing, not cosmetic: the picker shows at most [MAX_CHOICE_OPTIONS], so with
     * 9 lists the order decides WHICH 6 the user is even allowed to pick. In repository order
     * "Showing 6 of 9" routinely hides the list they were just working in — the one they almost
     * certainly mean. Names the repository doesn't know keep their original relative order, last.
     *
     * Duplicate names are matched up POSITIONALLY (one repository row consumed per occurrence)
     * rather than by lookup. A `associateBy { it.name }` here would collapse both "Shopping" rows
     * onto whichever won the map — handing two chips one id, one count and one date, i.e. exactly
     * the indistinguishable pair this whole path exists to separate. Callers pass one name per
     * candidate list (both `AmbiguousMatch.candidates` and the hintless-add path do), so the
     * occurrences line up 1:1.
     */
    private suspend fun rankCandidates(names: List<String>): List<ListCandidate> {
        val known = runCatching { checklistRepository.checklists.first() }
            .onFailure { logger.warning(TAG, "rankCandidates: checklists unavailable — ${it.message}") }
            .getOrDefault(emptyList())
        val unclaimed = known.groupBy { it.name }.mapValues { (_, rows) -> ArrayDeque(rows) }
        return names
            .map { name ->
                val checklist = unclaimed[name]?.removeFirstOrNull()
                ListCandidate(
                    id = checklist?.id,
                    name = name,
                    // Folders are containers, not things you tick off — counting them would make
                    // "Shopping • 12" disagree with what the list shows.
                    itemCount = checklist?.items?.count { it.type == ChecklistNodeType.ITEM } ?: 0,
                    updatedAt = checklist?.updatedAt ?: 0L,
                )
            }
            .sortedByDescending { it.updatedAt }
    }

    /**
     * The chip meta per candidate, index-aligned with [candidates] — the bit that answers
     * "which «Shopping»?".
     *
     * Normally the item count. When two candidates share BOTH name and count the count settles
     * nothing, so the last-updated day is appended to EVERY candidate in the block — mixing
     * formats inside one block ("Shopping • 12" next to "Shopping • 12 • 3 July") would read as
     * two different kinds of fact rather than one comparison.
     *
     * Returned as a LIST, not a Map keyed by name: a map cannot describe two lists that share a
     * name, which is the one case the meta is here to resolve.
     */
    private suspend fun buildCandidateMetas(candidates: List<ListCandidate>): List<String> {
        val collides = candidates
            .groupBy { it.name to it.itemCount }
            .any { (_, group) -> group.size > 1 }
        return candidates.map { candidate ->
            // Bare value by design ("• 12", not "• 12 items"): chips are tight, RU inflates copy
            // 15-30%, and the spoken form lives in the chip's contentDescription instead.
            val base = candidate.itemCount.toString()
            if (collides && candidate.updatedAt > 0L) {
                "$base $META_SEPARATOR ${dateFormatter.formatDay(candidate.updatedAt)}"
            } else {
                base
            }
        }
    }

    // ─── Reversible path — apply now, offer Undo after (D1 "C-branch") ────────

    /**
     * Applies a reversible [toolCall] (AddItem / CompleteItem) without asking, then shows the
     * post-action chips ([ChoiceAction.Undo] / [ChoiceAction.MoveToList]).
     *
     * The result itself goes out as a normal [ChatScreenSideEffect.ShowAssistantMessage] (the
     * `chat_dispatch_added_to` copy already names both the item and the list), so it is persisted
     * to Room like any other reply — the chips are a transient offer on top, not the record.
     */
    private suspend fun dispatchReversible(toolCall: ToolCall, sourceLayer: RoutingLayer) {
        val outcome = toolCallDispatcher.dispatch(toolCall)

        // The hint matched several lists → we cannot auto-apply into a guess. Fall back to the
        // ask-first path. Handled here rather than via handleOutcomeInline so the picker is
        // tracked as this turn's response (handleOutcomeInline passes sourceLayer = null, which
        // would leave the turn with no response_received at all).
        if (outcome is DispatchOutcome.AmbiguousMatch) {
            showWhichListChoice(toolCall, outcome.candidates, sourceLayer = sourceLayer)
            return
        }

        handleOutcomeInline(outcome, sourceToolCall = toolCall)

        val undo = (outcome as? DispatchOutcome.Success)?.undo
        if (undo == null) {
            // NotFound / RequiresPremium / a Success the dispatcher deemed not undoable
            // (e.g. "already done") — the user already has a visible reply; nothing to offer.
            _screenState.value = _screenState.value.copy(isProcessing = false)
        } else {
            analytics.event(
                name = AnalyticsEvents.Chat.ACTION_AUTO_APPLIED,
                params = mapOf(
                    AnalyticsParams.ACTION_TYPE to (toolCall::class.simpleName ?: "unknown"),
                    AnalyticsParams.ROUTED_LAYER to sourceLayer.name,
                ),
            )
            showReversibleChips(undo)
        }
        trackResponseReceived(sourceLayer, outcome = "action")
    }

    /**
     * Post-action chips for a just-applied reversible mutation. The prompt is intentionally BLANK:
     * [AiChoiceResponse] then renders chips only, because the outcome was already said in its own
     * assistant bubble — a second "Added «Milk» to Shopping" inside a choice bubble would double it.
     *
     * @param includeUndo false after a move: the original row no longer exists, so "Undo" would be
     *  a lie ("undo" what — the move? the add?). Moving again (including back) covers the need.
     */
    private suspend fun showReversibleChips(handle: UndoHandle, includeUndo: Boolean = true) {
        val options = buildList {
            if (includeUndo) {
                add(
                    ChoiceOption(
                        id = CHOICE_UNDO,
                        label = choiceString(Res.string.chat_choice_undo),
                        role = ChoiceRole.Escape,
                        action = ChoiceAction.Undo(handle),
                    ),
                )
            }
            if (handle is UndoHandle.AddedItem) {
                add(
                    ChoiceOption(
                        id = CHOICE_MOVE_TO_LIST,
                        label = choiceString(Res.string.chat_choice_move_to_list),
                        role = ChoiceRole.Default,
                        action = ChoiceAction.MoveToList(handle),
                    ),
                )
            }
        }
        if (options.isEmpty()) {
            _screenState.value = _screenState.value.copy(isProcessing = false)
            return
        }
        _screenState.value = _screenState.value.copy(
            pendingChoice = PendingChoice(choice = ChatChoice(prompt = "", options = options)),
            isProcessing = false,
        )
    }

    /** Rolls the mutation back by id and reports the result (or `chat_undo_item_gone`). */
    private fun executeUndo(option: ChoiceOption, handle: UndoHandle) {
        viewModelScope.launch {
            markChipExecuting(option.id, choiceString(Res.string.chat_choice_executing_undo))
            runCatching {
                val outcome = toolCallDispatcher.undo(handle)
                // Fires on the OUTCOME, not on the tap: auto_applied → undone is the regret rate,
                // and auto_applied only counts successes. Counting a failed undo (the row was
                // already gone) as regret would inflate it against a denominator that never saw it.
                if (outcome is DispatchOutcome.Success) {
                    analytics.event(
                        name = AnalyticsEvents.Chat.ACTION_UNDONE,
                        params = mapOf(AnalyticsParams.ACTION_TYPE to (handle::class.simpleName ?: "unknown")),
                    )
                }
                clearChoice()
                handleOutcomeInline(outcome)
            }.onFailure { e ->
                logger.error(TAG, "executeUndo failed", e)
                clearChoice()
                _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_apply_error"))
            }
        }
    }

    /** Replaces the post-action chips with one chip per candidate destination list. */
    private fun showMoveTargets(handle: UndoHandle.AddedItem) {
        viewModelScope.launch {
            runCatching {
                val candidates = checklistRepository.checklists.first()
                    .map { it.name }
                    .filter { !it.equals(handle.checklistName, ignoreCase = true) }
                if (candidates.isEmpty()) {
                    // Nowhere to move it — say so and keep the chips (silent skip FORBIDDEN).
                    _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_move_no_other_lists"))
                    return@runCatching
                }
                // Premium has unlimited lists, so the chip cap can hide real destinations. Say the
                // count out loud instead of silently dropping them — an invisible 7th list reads as
                // "the app lost my list", and the user has no way to know the picker is partial.
                val names = candidates.take(MAX_CHOICE_OPTIONS)
                val options = names.mapIndexed { index, name ->
                    ChoiceOption(
                        id = "$CHOICE_MOVE_PREFIX$index",
                        label = name,
                        role = ChoiceRole.Default,
                        action = ChoiceAction.MoveTo(handle, name),
                    )
                }
                val prompt = if (candidates.size > names.size) {
                    choiceString(
                        Res.string.chat_choice_which_list_truncated,
                        names.size.toString(),
                        candidates.size.toString(),
                    )
                } else {
                    choiceString(Res.string.chat_choice_which_list)
                }
                _screenState.value = _screenState.value.copy(
                    pendingChoice = PendingChoice(
                        choice = ChatChoice(
                            prompt = prompt,
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
            }.onFailure { e ->
                logger.error(TAG, "showMoveTargets failed", e)
                _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_generic_error"))
            }
        }
    }

    /** Moves the just-added item to the tapped list (add-then-remove inside the dispatcher). */
    private fun executeMove(option: ChoiceOption, handle: UndoHandle.AddedItem, targetName: String) {
        viewModelScope.launch {
            markChipExecuting(option.id, choiceString(Res.string.chat_choice_executing_move))
            runCatching {
                val outcome = toolCallDispatcher.moveAddedItem(handle, targetName)
                // On the OUTCOME, not the tap — an ambiguous/unknown target moves nothing, and
                // counting it as a move would report relocations that never happened.
                if (outcome is DispatchOutcome.Success) {
                    analytics.event(
                        name = AnalyticsEvents.Chat.ACTION_MOVED,
                        params = mapOf(AnalyticsParams.ACTION_TYPE to "move_list"),
                    )
                }
                clearChoice()
                handleOutcomeInline(outcome)
                // A fresh handle → the item can be moved onwards (or back) from its new home.
                val newHandle = (outcome as? DispatchOutcome.Success)?.undo as? UndoHandle.AddedItem
                if (newHandle != null) showReversibleChips(newHandle, includeUndo = false)
            }.onFailure { e ->
                logger.error(TAG, "executeMove failed", e)
                clearChoice()
                _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_apply_error"))
            }
        }
    }

    /** Marks one chip as loading; the whole block goes non-interactive (blocks the double tap). */
    private fun markChipExecuting(optionId: String, loadingLabel: String) {
        _screenState.value.pendingChoice?.let { current ->
            _screenState.value = _screenState.value.copy(
                pendingChoice = current.copy(executingId = optionId, executingLabel = loadingLabel),
            )
        }
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
            is ChoiceAction.Undo -> executeUndo(option, action.handle)
            is ChoiceAction.MoveToList -> showMoveTargets(action.handle)
            is ChoiceAction.MoveTo -> executeMove(option, action.handle, action.targetName)
            ChoiceAction.Dismiss -> handleChoiceDismissed()
        }
    }

    /** Dispatches a single Execute choice (the old Apply path), with a per-chip loading state. */
    private fun executeChoice(option: ChoiceOption, toolCall: ToolCall) {
        analytics.event(
            name = AnalyticsEvents.Chat.PREVIEW_CONFIRMED,
            params = mapOf(AnalyticsParams.ACTION_TYPE to (toolCall::class.simpleName ?: "unknown")),
        )
        // Captured BEFORE the dispatch clears the choice — the checkbox state dies with the block.
        val pending = _screenState.value.pendingChoice
        val remember = pending?.showMemoryToggle == true && pending.rememberChoice
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
                // Only remember a destination the action actually reached: persisting after a
                // NotFound would pin the chat to a list the item never landed in.
                if (remember && outcome is DispatchOutcome.Success) {
                    rememberDefaultChecklist(extractChecklistId(toolCall), extractHint(toolCall))
                }
            }.onFailure { e ->
                logger.error(TAG, "executeChoice failed", e)
                clearChoice()
                _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_apply_error"))
            }
        }
    }

    /**
     * Persists the user's chosen list as the chat default AND says so out loud.
     *
     * The disclosure is not optional. A sticky routing preference the user cannot see changed is a
     * dark pattern: the next "add milk" would silently skip the question and land somewhere they
     * never re-confirmed. The message names both the list and where to undo it.
     */
    private suspend fun rememberDefaultChecklist(checklistId: Long?, listName: String?) {
        if (listName.isNullOrBlank()) {
            logger.warning(TAG, "rememberDefaultChecklist: no list name on the executed tool call")
            return
        }
        // The executed call's id when it has one — it IS the list the user picked. Re-deriving the
        // id from the name would quietly disagree with the add that just happened: with two lists
        // called "Покупки" the item lands in the tapped one while `firstOrNull { name == }` pins
        // the preference to the other, so every later add silently changes destination. Falling
        // back to the name only where there is no id (a server-built call) keeps the old path.
        val id = checklistId ?: runCatching { checklistRepository.checklists.first() }
            .getOrNull()
            ?.firstOrNull { it.name.equals(listName, ignoreCase = true) }
            ?.id
        if (id == null) {
            logger.warning(TAG, "rememberDefaultChecklist: no checklist id for '$listName'")
            return
        }
        runCatching { aiChatPreferencesRepository.setDefaultChecklistId(id) }
            .onSuccess {
                _sideEffect.emit(
                    ChatScreenSideEffect.ShowAssistantMessage(
                        messageKey = "chat_result_remembered_list",
                        args = listOf(listName),
                    ),
                )
            }
            .onFailure { e -> logger.error(TAG, "Failed to persist default checklist", e) }
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

        // Agent batch = the choice that suspends runAgentTurn on _pendingAgentDecision. Detected by
        // its ExecuteAll chip, NOT by batchItems: since D1 every write choice carries batchItems
        // (the object line), so a batchItems check would misroute an ordinary dismiss into
        // "resolve the agent decision and return" — leaving pendingChoice stuck on screen.
        val isAgentBatch = pending.choice.options.any { it.action is ChoiceAction.ExecuteAll }
        // AI-options choice: chips are SendMessage (a fresh turn), not a write-intent confirm.
        val isOptions = pending.choice.options.any { it.action is ChoiceAction.SendMessage }
        // Post-action chips (Undo / Move to another list): an OFFER on top of an already-applied
        // action, not a question. Dismissing them cancels nothing, so the "Okay, cancelled." reply
        // below would be a lie about the action that already happened — the result message already
        // in the transcript IS the visible response here.
        // MoveTo belongs here too: the move-target picker is reached FROM these chips and is still
        // post-action — the item is already in a list. Without it, cancelling the picker answered
        // "Okay, cancelled." about an add that stayed, and logged a PREVIEW_REJECTED for a choice
        // that was never a question.
        val isPostAction = pending.choice.options.any {
            it.action is ChoiceAction.Undo ||
                it.action is ChoiceAction.MoveToList ||
                it.action is ChoiceAction.MoveTo
        }
        if (isPostAction) {
            clearChoice()
            return
        }
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
                            // Same 402 as the send path, and the same rule: say the wallet is
                            // empty and offer the paywall. Before 2026-07-16 this arrived as
                            // Unknown and fell into the runAgentTurn branch below — a silent
                            // 3-credit request to a wallet that had just refused 1.
                            ChatIntent.InsufficientCredits -> emitInsufficientCredits(classification.layer)

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
            analytics.event(name = AnalyticsEvents.Chat.VOICE_CANCELLED)
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
                    analytics.event(
                        name = AnalyticsEvents.Chat.VOICE_TRANSCRIBED,
                        params = mapOf(AnalyticsParams.CHAR_LEN to outcome.transcript.length),
                    )
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
                    analytics.event(name = AnalyticsEvents.Chat.VOICE_TRANSCRIBE_EMPTY)
                    _screenState.value = _screenState.value.copy(isTranscribing = false)
                    _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_transcribe_empty"))
                }
                TranscriptionOutcome.FileMissing,
                TranscriptionOutcome.NetworkError,
                TranscriptionOutcome.ServiceError -> {
                    val reason = when (outcome) {
                        TranscriptionOutcome.FileMissing -> "file_missing"
                        TranscriptionOutcome.NetworkError -> "network_error"
                        TranscriptionOutcome.ServiceError -> "service_error"
                        else -> "unknown"
                    }
                    analytics.event(
                        name = AnalyticsEvents.Chat.VOICE_TRANSCRIBE_FAILED,
                        params = mapOf(AnalyticsParams.OUTCOME to reason),
                    )
                    _screenState.value = _screenState.value.copy(isTranscribing = false)
                    _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_transcribe_error"))
                }
                TranscriptionOutcome.InsufficientCredits -> {
                    analytics.event(
                        name = AnalyticsEvents.Chat.VOICE_TRANSCRIBE_FAILED,
                        params = mapOf(AnalyticsParams.OUTCOME to OUTCOME_INSUFFICIENT_CREDITS),
                    )
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

        // The turn these NEW rounds belong to = the most recent user message. Its id keys the
        // rounds in Room so a later session can splice them back where they happened. In the
        // "Ask AI" fallback the last message is the assistant's Unknown reply, so we look past
        // it to the user message that actually opened this turn.
        val currentTurnMessageId = historyMessages.lastOrNull { it.role == ChatRole.User }?.id

        // Stage 3: pull the persisted tool rounds of PAST turns and splice each between its user
        // message and that turn's assistant answer, reproducing the [user][calls][results][model]
        // shape the agent emitted the first time. Degrades to no rounds on a storage miss.
        val userMessageIds = historyMessages.filter { it.role == ChatRole.User }.map { it.id }
        val roundsByTurn = agentTranscriptRepository.loadForTurns(userMessageIds)

        val fullSeed: MutableList<AgentTranscriptEntry> = mutableListOf()
        for (msg in historyMessages) {
            when (msg.role) {
                ChatRole.User -> {
                    fullSeed.add(AgentTranscriptEntry.UserText(msg.content))
                    // Splice past-turn rounds after their user message. Skip the CURRENT turn:
                    // its rounds are generated live below, so seeding them here would send each
                    // one twice (stale copy + fresh copy) in the same request.
                    if (msg.id != currentTurnMessageId) {
                        roundsByTurn[msg.id]?.let { fullSeed.addAll(it) }
                    }
                }
                ChatRole.Assistant -> fullSeed.add(AgentTranscriptEntry.ModelText(msg.content))
            }
        }

        // Window to the newest turns that fit the server's caps, keeping call↔results pairs
        // intact (see AgentTranscriptWindow). Persist everything, send only a bounded slice —
        // the server rejects (400), never trims, a transcript over CHAT_AGENT_MAX_TRANSCRIPT_ENTRIES.
        val seedTranscript = AgentTranscriptWindow.select(fullSeed).toMutableList()

        logger.debug(TAG, "runAgentTurn: seeded ${seedTranscript.size}/${fullSeed.size} transcript entries (windowed)")

        // One idempotency key for the WHOLE turn: reused by every round below so a transport
        // retry can never double-charge the reservation, fresh per invocation so a genuinely
        // new turn always bills. A leaked id would make the next turn free — hence per-turn.
        val requestId = newTurnRequestId()

        // Replace any rounds a prior (aborted / re-done) run left for this turn: a turn owns
        // exactly one set of rounds, appended fresh as the loop below completes them.
        if (currentTurnMessageId != null) {
            agentTranscriptRepository.deleteTurn(currentTurnMessageId)
        }
        // Bound the on-disk table. We persist far more turns than we send (the send is windowed
        // to MAX_TURNS) so the memory is deep, but not unbounded. Prunes whole turns, never rows.
        agentTranscriptRepository.pruneToRecentTurns(PERSISTED_TURNS_LIMIT)

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
                requestId = requestId,
            )

            // A/B: mirror the server-assigned model arm into the sticky user-property + persist it via
            // the shared tracker. Deterministic per user, so it normally runs once per session (the
            // tracker dedupes) — every later event (incl. the paywall funnel) then carries the arm.
            aiModelExperimentTracker.report(
                variant = stepResult.modelVariantOrNull(),
                modelId = stepResult.modelIdOrNull(),
                aiFlow = stepResult.aiFlowOrNull(),
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
                        trackResponseReceived(
                            RoutingLayer.FullChat,
                            outcome = "preview",
                            modelVariant = stepResult.modelVariant,
                            modelId = stepResult.modelId,
                            aiFlow = stepResult.aiFlow,
                        )
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
                    val modelToolCalls = AgentTranscriptEntry.ModelToolCalls(calls)
                    val toolResults = AgentTranscriptEntry.ToolResults(allResults)
                    transcript.add(modelToolCalls)
                    transcript.add(toolResults)

                    // Stage 3: persist this completed round so it survives a restart. Written as
                    // a call↔results PAIR (one insert = one transaction) — a half-persisted round
                    // would replay to Gemini as an unanswered function_call. Non-fatal on failure:
                    // the live turn already holds the round in [transcript]; only future memory of
                    // it is at stake. Skip when the turn has no user-message key (degenerate).
                    if (currentTurnMessageId != null) {
                        agentTranscriptRepository.appendRound(
                            turnMessageId = currentTurnMessageId,
                            calls = modelToolCalls,
                            results = toolResults,
                        )
                    }
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
                    trackResponseReceived(
                        RoutingLayer.FullChat,
                        outcome = "answer",
                        modelVariant = stepResult.modelVariant,
                        modelId = stepResult.modelId,
                        aiFlow = stepResult.aiFlow,
                    )
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
                    trackResponseReceived(
                        RoutingLayer.FullChat,
                        outcome = "options",
                        modelVariant = stepResult.modelVariant,
                        modelId = stepResult.modelId,
                        aiFlow = stepResult.aiFlow,
                    )
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
                    // Same wall as the Layer 2 refusal, so the same answer — one helper, no
                    // second dialect of "you're out of credits". Until 2026-07-16 this branch
                    // shipped three separate lies: a snackbar with no way to upgrade (the owner
                    // asked for the "Become Pro" button HERE too), outcome="error" (a refusal
                    // buried in the same bucket as "the AI crashed" — undiagnosable in Amplitude),
                    // and credits_used=3 for a turn the server charged 0 for.
                    //
                    // Live paths in: Deep Thinking ON (skips L2 → lands straight here), a vague
                    // L2 → FreeForm, and escalateChoice from a Classifier-source choice. The
                    // likeliest repro is a free user with 1-2 credits: L2 classify passes and
                    // takes 1, Layer 3 wants 3 → 402.
                    emitInsufficientCredits(RoutingLayer.FullChat)
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
     * Resolves [ChatScreenState.contextChecklistId] to the open checklist as (id, name).
     *
     * Returns null when there is no active context, or when the context checklist was
     * deleted between opening the dock and sending the command (logged as a warning so
     * the silent fallback to the default-resolution path is traceable).
     *
     * Used to bias list-less commands (e.g. "add milk") toward the checklist the user currently
     * has open, instead of the dispatcher's "first checklist" fallback. Carries the ID through:
     * we are looking AT the list, so "add milk" landing in a same-named other list — which is
     * what the old name-only bias did — is indefensible. The name still rides along for the
     * result copy and the id-less fallback.
     */
    private suspend fun resolveContextChecklist(): ListTarget? {
        val contextId = _screenState.value.contextChecklistId ?: return null
        val checklist = runCatching { checklistRepository.getChecklistById(contextId) }
            .getOrElse { e ->
                logger.error(TAG, "resolveContextChecklist: lookup failed for id=$contextId — ${e.message}", e)
                null
            }
        if (checklist == null) {
            logger.warning(TAG, "resolveContextChecklist: context checklist id=$contextId not found — falling back to default resolution")
            return null
        }
        return ListTarget(id = checklist.id, name = checklist.name)
    }

    /**
     * The open checklist's display NAME — the agent prompt takes a name, not an id (the model
     * reasons over list names; ids mean nothing to it).
     */
    private suspend fun resolveContextChecklistName(): String? = resolveContextChecklist()?.name

    /**
     * Applies the active [context] checklist to a list-less command [toolCall].
     *
     * For command variants that target an existing checklist (AddItem, AddItems, CompleteItem,
     * DeleteItem, SetItemReminder, AttachToItem) whose [checklistHint] is null, returns a copy
     * aimed at [context] — id AND name (see [ToolCall.withTarget]). An explicit hint is NEVER
     * overwritten: the user naming a list always wins over the open-screen context, and a named
     * list they did not disambiguate must still be allowed to reach the picker.
     *
     * CreateChecklist / RenameChecklist are intentionally excluded: there the "list" is the
     * target/output of the action, not the context to operate within. CreateChecklistFromAttachment,
     * MoveAllReminders, FindItemsQuery and ReadChecklist carry no per-list hint either.
     */
    private fun applyContextChecklist(toolCall: ToolCall, context: ListTarget): ToolCall {
        val hasExplicitHint = when (toolCall) {
            is ToolCall.AddItem -> toolCall.checklistHint != null
            is ToolCall.AddItems -> toolCall.checklistHint != null
            is ToolCall.CompleteItem -> toolCall.checklistHint != null
            is ToolCall.DeleteItem -> toolCall.checklistHint != null
            is ToolCall.SetItemReminder -> toolCall.checklistHint != null
            is ToolCall.AttachToItem -> toolCall.checklistHint != null
            // Excluded by design — list is the target, not the context, or no hint field.
            // withTarget() returns null for these, so the elvis below keeps them untouched.
            is ToolCall.CreateChecklist,
            is ToolCall.RenameChecklist,
            is ToolCall.CreateChecklistFromAttachment,
            is ToolCall.MoveAllReminders,
            is ToolCall.FindItemsQuery,
            is ToolCall.ReadChecklist -> true
        }
        if (hasExplicitHint) return toolCall
        return toolCall.withTarget(context.id, context.name) ?: toolCall
    }

    /**
     * Convenience wrapper: resolves the context checklist (if any) and applies it to [toolCall]
     * when the hint is null. No-op when there is no context or the command already carries an
     * explicit hint. Used on the Layer-1/Layer-2 preview-build path.
     */
    private suspend fun biasToolCallToContext(toolCall: ToolCall): ToolCall {
        val context = resolveContextChecklist() ?: return toolCall
        return applyContextChecklist(toolCall, context)
    }

    /**
     * The exact target list id of a write-intent ToolCall, or null when it has none (a server-built
     * call, or a variant that carries no per-list target). The counterpart of [extractHint]: where
     * the hint is what we can SHOW, this is what we can trust — see [ToolCall].
     */
    private fun extractChecklistId(toolCall: ToolCall): Long? = when (toolCall) {
        is ToolCall.AddItem -> toolCall.checklistId
        is ToolCall.DeleteItem -> toolCall.checklistId
        is ToolCall.CompleteItem -> toolCall.checklistId
        is ToolCall.SetItemReminder -> toolCall.checklistId
        is ToolCall.AttachToItem -> toolCall.checklistId
        is ToolCall.AddItems -> toolCall.checklistId
        is ToolCall.CreateChecklist,
        is ToolCall.MoveAllReminders,
        is ToolCall.FindItemsQuery,
        is ToolCall.CreateChecklistFromAttachment,
        is ToolCall.ReadChecklist,
        is ToolCall.RenameChecklist -> null
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

    /**
     * Plural sibling of [choiceString] — same test-env degrade.
     *
     * Counts always go through a plural, never "$n items": Russian needs three forms
     * (пункт / пункта / пунктов) and a concatenated count ships the wrong one 2 times in 3.
     */
    private suspend fun choicePlural(res: PluralStringResource, quantity: Int): String =
        runCatching { getPluralString(res, quantity, quantity) }
            .getOrElse { e ->
                logger.warning(TAG, "choicePlural: resource resolution failed (test env?) — ${e.message}")
                quantity.toString()
            }

    // ─── Choice copy helpers (write-intent prompt / chip / loading labels) ─────

    /**
     * The ARGUMENT-LESS question for a write-intent choice ("Delete this?").
     *
     * There are deliberately no positional args left anywhere in a chat question. The old
     * flat `promptRes()/promptArgs()` pair gave every question exactly ONE slot, so each had to
     * pick between naming the item or naming the list — AddItem picked the list and shipped
     * "Add to Shopping?" without ever saying what would be added. The object now always rides in
     * its own line (see [ToolCallPreviewRenderer] + [PendingChoice.batchItems]), which fits any
     * number of entities and stays translatable.
     */
    private fun ToolCall.questionRes(): StringResource = when (this) {
        is ToolCall.AddItem, is ToolCall.AddItems -> Res.string.chat_question_add
        is ToolCall.DeleteItem -> Res.string.chat_question_delete
        is ToolCall.CompleteItem -> Res.string.chat_question_complete
        is ToolCall.CreateChecklist -> Res.string.chat_question_create
        is ToolCall.SetItemReminder -> Res.string.chat_question_set_reminder
        is ToolCall.MoveAllReminders -> Res.string.chat_choice_move_reminders
        is ToolCall.AttachToItem -> Res.string.chat_question_attach
        is ToolCall.CreateChecklistFromAttachment -> Res.string.chat_choice_create_from_file
        // Agent-only / read variants never produce a write-intent choice; safe fallback.
        is ToolCall.FindItemsQuery,
        is ToolCall.ReadChecklist,
        is ToolCall.RenameChecklist -> Res.string.chat_choice_apply_actions
    }

    /**
     * The primary chip's label — it names the OUTCOME, never "Apply".
     *
     * "Create 8 items" tells the user what they are about to get; "Apply" makes them re-read the
     * rows to find out. Only the two actions whose size is the point get counted:
     *  - CreateChecklist → "Create 8 items" (degrades to a bare "Create" with no initial items),
     *  - MoveAllReminders → "Move 5 reminders" — NOT reachable today: the tool call carries no
     *    count, so it degrades to "Move". Wiring the count is a data-layer change.
     * Everything else keeps its plain verb, which already names its outcome.
     */
    private suspend fun ToolCall.outcomeLabel(): String = when (this) {
        is ToolCall.CreateChecklist -> if (initialItems.isEmpty()) {
            choiceString(primaryActionLabel())
        } else {
            choiceString(
                Res.string.chat_choice_action_create_items,
                choicePlural(Res.plurals.items_count, initialItems.size),
            )
        }
        else -> choiceString(primaryActionLabel())
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
     * Returns a copy of this tool call aimed at ONE specific list — the id resolves it, the name
     * rides along for display copy, analytics and the id-less fallback. Null for tool calls that
     * carry no per-list target.
     *
     * Both fields, never just one. The id alone would leave `chat_dispatch_added_to` unable to
     * name where the item went; the name alone is what made two "Shopping" chips dispatch the
     * identical call, so the picker asked a question it could not act on (see [ToolCall]).
     *
     * [id] is nullable so a candidate the repository no longer knows still produces a working
     * chip — it just resolves by name, exactly as it did before ids existed.
     */
    private fun ToolCall.withTarget(id: Long?, name: String): ToolCall? = when (this) {
        is ToolCall.AddItem -> copy(checklistId = id, checklistHint = name)
        is ToolCall.DeleteItem -> copy(checklistId = id, checklistHint = name)
        is ToolCall.CompleteItem -> copy(checklistId = id, checklistHint = name)
        is ToolCall.SetItemReminder -> copy(checklistId = id, checklistHint = name)
        is ToolCall.AttachToItem -> copy(checklistId = id, checklistHint = name)
        is ToolCall.AddItems -> copy(checklistId = id, checklistHint = name)
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
                // Chips only help when the call can actually be aimed at ONE list. A read-only call
                // (FindItemsQuery) carries no target, so every chip would dispatch the same thing —
                // those keep the text clarification. The picker itself re-targets per candidate.
                val targetable = sourceToolCall?.takeIf { tc ->
                    outcome.candidates.any { tc.withTarget(id = null, name = it) != null }
                }
                if (targetable == null) {
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
                    showWhichListChoice(targetable, outcome.candidates, sourceLayer = null)
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

            // FindItems, FreeForm, AttachToItem, Unknown and InsufficientCredits are handled
            // separately and should not reach buildToolCall. InsufficientCredits especially:
            // building a tool call for a turn the server refused would execute work the user
            // never paid for.
            ChatIntent.FindItems,
            ChatIntent.FreeForm,
            is ChatIntent.AttachToItem,  // handled inline before buildToolCall is called
            ChatIntent.InsufficientCredits,
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
        paywallCtaCredits: Int? = null,
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
            // Same deal for the paywall CTA: transient, so a stale offer can't outlive the turn.
            paywallCtaCredits = paywallCtaCredits,
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

    /**
     * A fresh idempotency key for one agent turn. Random per invocation → each real turn bills;
     * captured in a local val and reused across the turn's rounds → a transport retry cannot
     * double-charge the server's reservation. Uuid.random() matches the id idiom used elsewhere
     * (analyze's newRequestId, cloudId generation).
     */
    @OptIn(ExperimentalUuidApi::class)
    private fun newTurnRequestId(): String = Uuid.random().toString()

    private companion object {
        const val TAG = "ChatViewModel"

        /**
         * `outcome` for a turn the server REFUSED for lack of credits (HTTP 402).
         *
         * Not new vocabulary: the voice path already reports exactly this string on the same
         * condition ([TranscriptionOutcome.InsufficientCredits]). Reporting "answer" instead —
         * as the classify path did until 2026-07-16 — hides every paywall-worthy refusal inside
         * the success bucket.
         */
        const val OUTCOME_INSUFFICIENT_CREDITS = "insufficient_credits"
        // Stable chip ids for the AiChoiceResponse block.
        const val CHOICE_EXECUTE = "execute"
        const val CHOICE_EXECUTE_ALL = "execute_all"
        const val CHOICE_EDIT = "edit"
        const val CHOICE_ESCAPE = "escape"
        const val CHOICE_CANDIDATE_PREFIX = "candidate_"
        const val CHOICE_OPTION_PREFIX = "option_"
        // Post-action chips (D1 reversible path).
        const val CHOICE_UNDO = "undo"
        const val CHOICE_MOVE_TO_LIST = "move_to_list"
        const val CHOICE_MOVE_PREFIX = "move_"

        /**
         * Separator between a value and its meta ("Shopping • 12", "Fri 17 – Sat 18").
         *
         * U+2022 / U+2013 only. Skiko on wasmJs has no CSS-style font fallback: D1 shipped "→"
         * (U+2192) and it rendered as tofu on the web canvas while Android's Roboto fallback hid
         * it. Both glyphs below are proven on that canvas — do not swap in an unverified one.
         */
        const val META_SEPARATOR = "•"
        const val RANGE_DASH = "–"
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

        /**
         * Turns of agent tool-rounds kept on disk (Stage 3). Far deeper than the send window
         * ([AgentTranscriptWindow.MAX_TURNS] = 6) — persistence is a cheap local table, so the
         * memory can be deep even though only the newest few turns ride each request. Bounds
         * the table so it cannot grow without limit over the life of the install.
         */
        const val PERSISTED_TURNS_LIMIT = 40
    }
}
