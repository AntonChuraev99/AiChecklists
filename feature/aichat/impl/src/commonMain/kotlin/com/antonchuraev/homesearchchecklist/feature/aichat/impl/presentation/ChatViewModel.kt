package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import androidx.lifecycle.viewModelScope
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
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AiChatRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatHistoryRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteCompletionResult
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview.ToolCallPreviewRenderer
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
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
 *   - [ChatIntent.FreeForm] → [aiChatRepository.completeFreeForm] (Layer 3, 3 credits)
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
                _screenState.value = _screenState.value.copy(creditBalance = userData.aiCredits)
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
                // linkedChecklistId is preserved from the original ShowAssistantMessage
                // side effect so the bubble can render the "Open checklist" button.
                addAssistantMessage(intent.text, linkedChecklistId = intent.linkedChecklistId)
            }

            ChatScreenIntent.OnPreviewApply -> handlePreviewApply()

            ChatScreenIntent.OnPreviewCancel -> {
                // No SideEffect — cancel is a silent dismiss per spec
                _screenState.value = _screenState.value.copy(pendingPreview = null)
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

            is ChatScreenIntent.OnOpenChecklist -> {
                viewModelScope.launch {
                    _sideEffect.emit(ChatScreenSideEffect.NavigateToChecklist(intent.checklistId))
                }
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

            is ChatScreenIntent.OnVoiceRecordingStopped -> handleVoiceRecordingStopped(intent.recordingPath)

            is ChatScreenIntent.OnFeedbackOpen -> {
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
                    name = "ai_chat_thumb_up",
                    params = mapOf(
                        "message_id" to msg.id,
                        "routed_layer" to (msg.routedLayer?.name ?: "unknown"),
                        "deep_thinking_enabled" to _screenState.value.deepThinkingEnabled.toString(),
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
        }
    }

    // ─── Feedback flow ────────────────────────────────────────────────────────

    private fun handleFeedbackSubmit() {
        val state = _screenState.value
        val target = state.feedbackTarget ?: return
        val feedbackText = state.feedbackText.trim()

        // Silent-skip is FORBIDDEN (CLAUDE.md rule) — emit snackbar instead of returning quietly.
        if (feedbackText.isBlank()) {
            viewModelScope.launch {
                _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_feedback_blank_hint"))
            }
            return
        }

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
                name = "ai_chat_feedback",
                params = mapOf(
                    "question" to question,
                    "answer" to answer,
                    "feedback" to feedbackText,
                    "message_id" to target.id,
                    "routed_layer" to (target.routedLayer?.name ?: "unknown"),
                    "deep_thinking_enabled" to state.deepThinkingEnabled.toString(),
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

    // ─── Send flow ────────────────────────────────────────────────────────────

    private fun handleSend() {
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
                        _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_unknown_intent_hint"))
                        _screenState.value = _screenState.value.copy(isProcessing = false)
                    }

                    // Layer 3 — open-ended conversation, no tool call preview
                    ChatIntent.FreeForm -> {
                        handleFreeForm(locale)
                    }

                    // Read intent — dispatch inline, no preview
                    ChatIntent.FindItems -> {
                        val query = extractQuery(text)
                        val outcome = toolCallDispatcher.dispatch(ToolCall.FindItemsQuery(query))
                        handleOutcomeInline(outcome)
                        _screenState.value = _screenState.value.copy(isProcessing = false)
                    }

                    // Write intents — show preview card
                    ChatIntent.CreateItem,
                    ChatIntent.DeleteItem,
                    ChatIntent.CompleteItem,
                    is ChatIntent.CreateChecklist,
                    ChatIntent.SetReminder,
                    ChatIntent.MoveReminders -> {
                        // Layer 2 (Classifier) pre-builds the ToolCall with server-extracted entities.
                        // Prefer that over re-running local text extraction from raw text.
                        val toolCall = classification.preBuiltToolCall ?: buildToolCall(intent, text, locale)
                        if (toolCall == null) {
                            _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_extract_fail"))
                            _screenState.value = _screenState.value.copy(isProcessing = false)
                            return@runCatching
                        }
                        val humanReadable = previewRenderer.render(toolCall)
                        _screenState.value = _screenState.value.copy(
                            pendingPreview = PendingPreview(
                                toolCall = toolCall,
                                humanReadable = humanReadable,
                                targetChecklistHint = extractHint(toolCall),
                                editableItemText = extractItemText(toolCall),
                            ),
                            isProcessing = false,
                        )
                    }

                    // AttachToItem — show preview only if attachments are present;
                    // otherwise emit a snackbar (silent-skip is forbidden).
                    is ChatIntent.AttachToItem -> {
                        val currentAttachments = _screenState.value.pendingAttachments
                        if (currentAttachments.isEmpty()) {
                            _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_attach_no_files"))
                            _screenState.value = _screenState.value.copy(isProcessing = false)
                            return@runCatching
                        }
                        val toolCall = ToolCall.AttachToItem(
                            checklistHint = intent.checklistHint,
                            itemText = intent.itemText,
                            attachments = currentAttachments,
                        )
                        val humanReadable = previewRenderer.render(toolCall)
                        _screenState.value = _screenState.value.copy(
                            pendingPreview = PendingPreview(
                                toolCall = toolCall,
                                humanReadable = humanReadable,
                                targetChecklistHint = intent.checklistHint,
                                editableItemText = intent.itemText,
                            ),
                            isProcessing = false,
                        )
                    }
                }
            }.onFailure { e ->
                logger.error(TAG, "handleSend failed", e)
                _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_generic_error"))
                _screenState.value = _screenState.value.copy(isProcessing = false)
            }
        }
    }

    // ─── Preview apply flow ───────────────────────────────────────────────────

    private fun handlePreviewApply() {
        val preview = _screenState.value.pendingPreview ?: return
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
            }.onFailure { e ->
                logger.error(TAG, "handleSendAttachmentsOnly failed", e)
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

    private fun handleVoiceRecordingStopped(recordingPath: String?) {
        _screenState.value = _screenState.value.copy(isRecording = false)

        if (recordingPath == null) {
            // User cancelled — silent skip FORBIDDEN, emit snackbar
            viewModelScope.launch {
                _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_recording_cancelled"))
            }
            return
        }

        // Successful recording: wrap as an Audio ChatAttachment and add to pending list
        val audioAttachment = ChatAttachment(
            sourcePath = recordingPath,
            mimeType = "audio/m4a",
            fileName = "voice_message.m4a",
            sizeBytes = 0L, // actual size unknown until file is written; dispatcher can re-probe
        )
        handleAttachmentPicked(audioAttachment)
    }

    // ─── FreeForm (Layer 3) flow ───────────────────────────────────────────────

    /**
     * Invoked when classification returns [ChatIntent.FreeForm].
     *
     * Builds a compact checklist context summary (top [CHECKLIST_SUMMARY_LIMIT] checklists,
     * names + counts only — no item text), then calls [AiChatRepository.completeFreeForm].
     * The assistant response is added as an inline message (no preview card — Layer 3 is
     * read-only conversation output, not a write action).
     */
    private suspend fun handleFreeForm(locale: ChatLocale) {
        val checklistsSummary = buildChecklistsSummary()
        val currentMessages = _screenState.value.messages

        val result = aiChatRepository.completeFreeForm(
            messages = currentMessages,
            locale = locale,
            checklistsSummary = checklistsSummary,
        )

        when (result) {
            is RemoteCompletionResult.Success -> {
                logger.info(TAG, "FreeForm success: ${result.content.take(60)} credits_remaining=${result.creditsRemaining}")
                // Optimistic credit update from server response — shown immediately,
                // before the next Firestore sync updates UserDataRepository cache.
                _screenState.value = _screenState.value.copy(creditBalance = result.creditsRemaining)
                // Persist the fresh balance to local cache so other screens see it immediately.
                runCatching {
                    val currentUserData = userDataRepository.getUserData()
                    userDataRepository.update(currentUserData.copy(aiCredits = result.creditsRemaining))
                }.onFailure { e ->
                    logger.error(TAG, "FreeForm: failed to persist updated credit balance — ${e.message}", e)
                    // Non-fatal: UserDataRepository flow will reconcile on next Firestore sync.
                }
                addAndPersistAssistantMessage(
                    content = result.content,
                    routedLayer = RoutingLayer.FullChat,
                    costCredits = 3,
                )
            }
            RemoteCompletionResult.InsufficientCredits -> {
                logger.info(TAG, "FreeForm: InsufficientCredits")
                _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_insufficient_credits"))
            }
            RemoteCompletionResult.NetworkError,
            RemoteCompletionResult.ServiceError -> {
                logger.warning(TAG, "FreeForm: ${result::class.simpleName}")
                _sideEffect.emit(ChatScreenSideEffect.ShowAssistantMessage("chat_completion_error"))
            }
        }

        _screenState.value = _screenState.value.copy(isProcessing = false)
    }

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
        }
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
        is ToolCall.CreateChecklistFromAttachment -> null
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
        is ToolCall.CreateChecklistFromAttachment -> ""
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
                // For MVP: itemText = anything after reminder keyword, at = now + 1 day (placeholder)
                // SmartDateParser is in LocalIntentRouterImpl scope; we cannot reuse it here
                // without a new dependency. Phase B will inject SmartDateParser into ViewModel.
                // Pending: docs/todos/2026-05-13-ai-chat-assistant.md (Phase B date extraction)
                val itemText = extractPayloadAfterReminderKeyword(lower, locale)
                if (itemText.isNullOrBlank()) null
                else ToolCall.SetItemReminder(
                    checklistHint = null,
                    itemText = itemText,
                    at = nowMillis() + 24 * 60 * 60 * 1000L, // placeholder: tomorrow same time
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

    private fun addAssistantMessage(content: String, linkedChecklistId: Long? = null) {
        val msg = ChatMessage(
            id = generateId(),
            role = ChatRole.Assistant,
            content = content,
            timestamp = nowMillis(),
            costCredits = 0,
            linkedChecklistId = linkedChecklistId,
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
    }
}
