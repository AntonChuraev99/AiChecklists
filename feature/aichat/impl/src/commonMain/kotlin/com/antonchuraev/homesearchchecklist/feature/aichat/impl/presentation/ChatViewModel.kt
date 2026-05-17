package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher.ToolCallDispatcher
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.locale.ChatLocaleProvider
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AiChatRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview.ToolCallPreviewRenderer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
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
 * Phase A rules:
 *   - Layer 1 only (local routing), zero credits cost
 *   - No AI calls, no network, no [userDataRepository.consumeCredit()]
 *   - creditBalance is fetched once from [userDataRepository] and used as read-only display
 */
class ChatViewModel(
    private val aiChatRepository: AiChatRepository,
    private val toolCallDispatcher: ToolCallDispatcher,
    private val previewRenderer: ToolCallPreviewRenderer,
    private val localeProvider: ChatLocaleProvider,
    private val logger: AppLogger,
) : AppViewModel<ChatScreenState, ChatScreenIntent, ChatScreenSideEffect>() {

    private val _screenState = MutableStateFlow(
        ChatScreenState(
            messages = listOf(welcomeMessage()),
        )
    )
    override val screenState: StateFlow<ChatScreenState> = _screenState

    private val _sideEffect = MutableSharedFlow<ChatScreenSideEffect>(extraBufferCapacity = 16)
    val sideEffect: Flow<ChatScreenSideEffect> = _sideEffect.asSharedFlow()

    override fun onIntent(intent: ChatScreenIntent) {
        when (intent) {
            is ChatScreenIntent.OnInputChange -> {
                _screenState.value = _screenState.value.copy(inputText = intent.text)
            }

            ChatScreenIntent.OnSendClick -> handleSend()

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

            ChatScreenIntent.OnBackClick -> {
                viewModelScope.launch { _sideEffect.emit(ChatScreenSideEffect.NavigateBack) }
            }
        }
    }

    // ─── Send flow ────────────────────────────────────────────────────────────

    private fun handleSend() {
        val text = _screenState.value.inputText.trim()

        // Blank guard — silent skip is FORBIDDEN (global CLAUDE.md rule)
        if (text.isBlank()) {
            viewModelScope.launch {
                _sideEffect.emit(ChatScreenSideEffect.ShowSnackbar("chat_unknown_intent_hint"))
            }
            return
        }

        // Append user message
        val userMsg = ChatMessage(
            id = generateId(),
            role = ChatRole.User,
            content = text,
            timestamp = nowMillis(),
            costCredits = 0,
            routedLayer = null,
        )
        updateMessages { it + userMsg }
        _screenState.value = _screenState.value.copy(inputText = "", isProcessing = true)

        viewModelScope.launch {
            runCatching {
                val locale = localeProvider.current()
                val classification = aiChatRepository.classify(text, locale)
                logger.debug(TAG, "Classified '${text.take(40)}' → ${classification.intent::class.simpleName} conf=${classification.confidence}")

                // Update user message with routing metadata
                updateMessage(userMsg.id) { it.copy(routedLayer = classification.layer) }

                when (val intent = classification.intent) {
                    is ChatIntent.Unknown -> {
                        addAssistantMessage("I didn't catch that. Try «add milk to shopping» or «remind me on Friday at 6pm».")
                        _screenState.value = _screenState.value.copy(isProcessing = false)
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
                    ChatIntent.CreateChecklist,
                    ChatIntent.SetReminder,
                    ChatIntent.MoveReminders -> {
                        val toolCall = buildToolCall(intent, text, locale)
                        if (toolCall == null) {
                            addAssistantMessage("I understood your intent but couldn't extract the details. Please try again with more specifics.")
                            _screenState.value = _screenState.value.copy(isProcessing = false)
                            return@runCatching
                        }
                        val humanReadable = previewRenderer.render(toolCall)
                        _screenState.value = _screenState.value.copy(
                            pendingPreview = PendingPreview(toolCall, humanReadable),
                            isProcessing = false,
                        )
                    }
                }
            }.onFailure { e ->
                logger.error(TAG, "handleSend failed", e)
                addAssistantMessage("Something went wrong. Please try again.")
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
                val outcome = toolCallDispatcher.dispatch(preview.toolCall)
                _screenState.value = _screenState.value.copy(pendingPreview = null)
                handleOutcomeInline(outcome)
            }.onFailure { e ->
                logger.error(TAG, "handlePreviewApply failed", e)
                _screenState.value = _screenState.value.copy(pendingPreview = null)
                addAssistantMessage("Something went wrong applying the change. Please try again.")
            }
            _screenState.value = _screenState.value.copy(isProcessing = false)
        }
    }

    // ─── Outcome handlers ─────────────────────────────────────────────────────

    private suspend fun handleOutcomeInline(outcome: DispatchOutcome) {
        when (outcome) {
            is DispatchOutcome.Success -> {
                addAssistantMessage(outcome.humanReadable)
            }
            is DispatchOutcome.AmbiguousMatch -> {
                val candidates = outcome.candidates.take(3).joinToString(", ")
                addAssistantMessage("Did you mean: $candidates? Please clarify which checklist.")
            }
            is DispatchOutcome.NotFound -> {
                addAssistantMessage(outcome.reason)
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

            ChatIntent.CreateChecklist -> {
                val name = extractChecklistName(lower, locale)
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

            // FindItems and Unknown are handled separately — should not reach here
            ChatIntent.FindItems,
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

        // Extract checklist hint after preposition (EN: in/to/for, RU: в/к/для)
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

    private fun addAssistantMessage(content: String) {
        val msg = ChatMessage(
            id = generateId(),
            role = ChatRole.Assistant,
            content = content,
            timestamp = nowMillis(),
            costCredits = 0,
        )
        updateMessages { it + msg }
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

        fun welcomeMessage() = ChatMessage(
            id = "welcome",
            role = ChatRole.Assistant,
            content = "Hi! I can help you manage your checklists. Try «add milk to shopping» or «remind me on Friday at 6pm».",
            timestamp = 0L,
        )
    }
}
