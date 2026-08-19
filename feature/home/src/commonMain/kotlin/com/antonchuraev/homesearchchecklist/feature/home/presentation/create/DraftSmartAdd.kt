package com.antonchuraev.homesearchchecklist.feature.home.presentation.create

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.SmartDateParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone

/**
 * How long the text has to stop changing before the parser looks at it.
 *
 * Not decoration: [SmartDateParser] is a regex cascade over six priority levels in two languages,
 * and the capture dock calls it from a field the user is typing into. The same 200ms is what the
 * checklist detail screen has run since Smart-Add shipped — one number, so the two entry points
 * cannot feel like two different features.
 */
private const val SMART_ADD_DEBOUNCE_MS = 200L

/**
 * Keeps [draft]'s [TaskDraft.parsedToken] in step with its [TaskDraft.text] — the wire that connects
 * Smart-Add to the capture dock.
 *
 * ONE function for both capture hosts rather than a copy in each ViewModel, for the same reason
 * [DraftDueController] is one object: the Inbox tab and the Calendar/Today tab ship the same dock,
 * and the last time these two grew a behaviour each they drifted into two different answers. A
 * second hand-rolled debounce would also be a second chance to forget one of the two rules below.
 *
 * ## Two rules live here, and both are subtractive
 * - **A dismissed date stays dismissed.** [TaskDraft.dueDismissed] gates the write, so the `x` on
 *   the leading chip is not undone by the next keystroke re-deriving the same phrase.
 * - **Repeats are not staged.** A token carrying a [com.antonchuraev.homesearchchecklist.feature
 *   .checklist.domain.model.ReminderRepeatRule] is dropped rather than written — see
 *   [TaskDraft.parsedToken] for why (the free-tier gate, and a draft that can only carry a repeat as
 *   a `PendingRepeatConfig`).
 *
 * The token is written back into the SAME flow this collects from. That is not a loop: the upstream
 * is keyed on [TaskDraft.text] through [distinctUntilChanged], and the write only touches
 * `parsedToken`.
 *
 * @param now injected so tests own the clock; the parser resolves "tomorrow" against it.
 */
@OptIn(FlowPreview::class)
internal fun CoroutineScope.observeDraftTextForSmartAdd(
    draft: MutableStateFlow<TaskDraft>,
    parser: SmartDateParser,
    now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
): Job = launch {
    draft
        .map { it.text }
        .distinctUntilChanged()
        .debounce(SMART_ADD_DEBOUNCE_MS)
        .collect { text ->
            val token = when {
                text.isBlank() -> null
                draft.value.dueDismissed -> null
                else -> parser.parse(text, now(), timeZone())?.takeIf { it.repeatRule == null }
            }
            draft.update { current ->
                // `text` is the string the parse ran against. If the user has typed on since, this
                // result is already stale and belongs to nobody — dropping it is what stops a slow
                // parse from stamping an old phrase onto new text.
                if (current.text == text && current.parsedToken != token) {
                    current.copy(parsedToken = token)
                } else {
                    current
                }
            }
        }
}
