package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.voice_call_action_kind_checklist_created
import aichecklists.core.designsystem.generated.resources.voice_call_action_kind_items_added
import aichecklists.core.designsystem.generated.resources.voice_call_action_kind_task_added
import aichecklists.core.designsystem.generated.resources.voice_call_action_kind_updated
import aichecklists.core.designsystem.generated.resources.voice_call_action_open_a11y
import aichecklists.core.designsystem.generated.resources.voice_call_hint_listening_first
import aichecklists.core.designsystem.generated.resources.voice_call_hint_muted
import aichecklists.core.designsystem.generated.resources.voice_call_hint_paused_background
import aichecklists.core.designsystem.generated.resources.voice_call_hint_paused_interruption
import aichecklists.core.designsystem.generated.resources.voice_call_hint_speaking_interrupt
import aichecklists.core.designsystem.generated.resources.voice_call_status_connecting
import aichecklists.core.designsystem.generated.resources.voice_call_status_listening
import aichecklists.core.designsystem.generated.resources.voice_call_status_muted
import aichecklists.core.designsystem.generated.resources.voice_call_status_paused
import aichecklists.core.designsystem.generated.resources.voice_call_status_reconnecting
import aichecklists.core.designsystem.generated.resources.voice_call_status_speaking
import aichecklists.core.designsystem.generated.resources.voice_call_status_thinking
import aichecklists.core.designsystem.generated.resources.voice_call_status_thinking_slow
import aichecklists.core.designsystem.generated.resources.voice_call_status_thinking_very_slow
import aichecklists.core.designsystem.generated.resources.voice_call_tool_adding_tasks
import aichecklists.core.designsystem.generated.resources.voice_call_tool_creating_checklist
import aichecklists.core.designsystem.generated.resources.voice_call_tool_searching
import androidx.compose.runtime.Composable
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components.OrbMode
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** StatusTitle per phase (DESIGN_SPEC §3). */
@Composable
internal fun statusTitle(phase: VoiceCallPhaseUi): String = stringResource(
    when (phase) {
        VoiceCallPhaseUi.Connecting -> Res.string.voice_call_status_connecting
        is VoiceCallPhaseUi.Listening -> Res.string.voice_call_status_listening
        is VoiceCallPhaseUi.Thinking -> when (phase.delay) {
            VoiceThinkingDelayUi.Slow -> Res.string.voice_call_status_thinking_slow
            VoiceThinkingDelayUi.VerySlow -> Res.string.voice_call_status_thinking_very_slow
            VoiceThinkingDelayUi.Normal -> when (phase.tool) {
                VoiceToolStatusUi.CreatingChecklist -> Res.string.voice_call_tool_creating_checklist
                VoiceToolStatusUi.AddingTasks -> Res.string.voice_call_tool_adding_tasks
                VoiceToolStatusUi.Searching -> Res.string.voice_call_tool_searching
                null -> Res.string.voice_call_status_thinking
            }
        }
        VoiceCallPhaseUi.Speaking -> Res.string.voice_call_status_speaking
        VoiceCallPhaseUi.Muted -> Res.string.voice_call_status_muted
        is VoiceCallPhaseUi.Reconnecting -> Res.string.voice_call_status_reconnecting
        is VoiceCallPhaseUi.Paused -> Res.string.voice_call_status_paused
    },
)

/** StatusHint, or `null` when the phase has none. The layout budget may still drop it. */
@Composable
internal fun statusHint(state: VoiceCallUiState.InCall): String? = when (val phase = state.phase) {
    is VoiceCallPhaseUi.Listening ->
        if (state.firstTurn && !phase.hearing) stringResource(Res.string.voice_call_hint_listening_first) else null
    VoiceCallPhaseUi.Speaking ->
        if (state.interruptible) stringResource(Res.string.voice_call_hint_speaking_interrupt) else null
    VoiceCallPhaseUi.Muted -> stringResource(Res.string.voice_call_hint_muted)
    is VoiceCallPhaseUi.Paused -> stringResource(
        when (phase.reason) {
            VoicePauseReasonUi.Background -> Res.string.voice_call_hint_paused_background
            VoicePauseReasonUi.SystemInterruption -> Res.string.voice_call_hint_paused_interruption
        },
    )
    else -> null
}

@Composable
internal fun actionKindLabel(action: VoiceActionUi): String = when (action.kind) {
    VoiceActionKind.ChecklistCreated -> stringResource(Res.string.voice_call_action_kind_checklist_created)
    VoiceActionKind.TaskAdded -> stringResource(Res.string.voice_call_action_kind_task_added)
    VoiceActionKind.ItemsAdded -> {
        val count = action.itemsCount ?: 1
        pluralStringResource(Res.plurals.voice_call_action_kind_items_added, count, count, action.title)
    }
    VoiceActionKind.Updated -> stringResource(Res.string.voice_call_action_kind_updated)
}

/** «Создал чек-лист. Открыть «Поездка». Звонок завершится» — the card's merged TalkBack label. */
@Composable
internal fun actionA11yLabel(action: VoiceActionUi, kindLabel: String): String =
    if (action.checklistId != null) {
        kindLabel + ". " + stringResource(Res.string.voice_call_action_open_a11y, action.title)
    } else {
        "$kindLabel. ${action.title}"
    }

internal fun orbMode(phase: VoiceCallPhaseUi): OrbMode = when (phase) {
    VoiceCallPhaseUi.Connecting -> OrbMode.Breath
    is VoiceCallPhaseUi.Listening -> if (phase.hearing) OrbMode.Listen1 else OrbMode.Listen0
    is VoiceCallPhaseUi.Thinking -> OrbMode.Think
    VoiceCallPhaseUi.Speaking -> OrbMode.Speak
    VoiceCallPhaseUi.Muted -> OrbMode.Muted
    is VoiceCallPhaseUi.Reconnecting -> OrbMode.Dim
    is VoiceCallPhaseUi.Paused -> OrbMode.Paused
}
