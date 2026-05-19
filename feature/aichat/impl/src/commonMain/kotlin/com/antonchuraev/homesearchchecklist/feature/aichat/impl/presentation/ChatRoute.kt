package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_ambiguous_match
import aichecklists.core.designsystem.generated.resources.chat_apply_error
import aichecklists.core.designsystem.generated.resources.chat_dispatch_added
import aichecklists.core.designsystem.generated.resources.chat_dispatch_added_to
import aichecklists.core.designsystem.generated.resources.chat_dispatch_already_done
import aichecklists.core.designsystem.generated.resources.chat_dispatch_completed
import aichecklists.core.designsystem.generated.resources.chat_dispatch_created_empty
import aichecklists.core.designsystem.generated.resources.chat_dispatch_created_with_many
import aichecklists.core.designsystem.generated.resources.chat_dispatch_created_with_one
import aichecklists.core.designsystem.generated.resources.chat_dispatch_deleted
import aichecklists.core.designsystem.generated.resources.chat_dispatch_fill_load_failed
import aichecklists.core.designsystem.generated.resources.chat_dispatch_find_blank
import aichecklists.core.designsystem.generated.resources.chat_dispatch_find_no_match
import aichecklists.core.designsystem.generated.resources.chat_dispatch_find_success
import aichecklists.core.designsystem.generated.resources.chat_dispatch_item_not_found
import aichecklists.core.designsystem.generated.resources.chat_dispatch_moved_many
import aichecklists.core.designsystem.generated.resources.chat_dispatch_moved_one
import aichecklists.core.designsystem.generated.resources.chat_dispatch_no_checklist_match
import aichecklists.core.designsystem.generated.resources.chat_dispatch_no_checklists
import aichecklists.core.designsystem.generated.resources.chat_dispatch_no_reminders_on_day
import aichecklists.core.designsystem.generated.resources.chat_dispatch_operation_failed
import aichecklists.core.designsystem.generated.resources.chat_dispatch_reminder_set
import aichecklists.core.designsystem.generated.resources.chat_extract_fail
import aichecklists.core.designsystem.generated.resources.chat_feedback_blank_hint
import aichecklists.core.designsystem.generated.resources.chat_feedback_submitted
import aichecklists.core.designsystem.generated.resources.chat_generic_error
import aichecklists.core.designsystem.generated.resources.chat_history_load_error
import aichecklists.core.designsystem.generated.resources.chat_insufficient_credits
import aichecklists.core.designsystem.generated.resources.chat_completion_error
import aichecklists.core.designsystem.generated.resources.chat_mic_permission_denied
import aichecklists.core.designsystem.generated.resources.chat_not_found
import aichecklists.core.designsystem.generated.resources.chat_recording_cancelled
import aichecklists.core.designsystem.generated.resources.chat_requires_premium
import aichecklists.core.designsystem.generated.resources.chat_thumb_up_thanks
import aichecklists.core.designsystem.generated.resources.chat_transcribe_empty
import aichecklists.core.designsystem.generated.resources.chat_transcribe_error
import aichecklists.core.designsystem.generated.resources.chat_transcribing
import aichecklists.core.designsystem.generated.resources.chat_unknown_intent_hint
import aichecklists.core.designsystem.generated.resources.chat_voice_too_short
import aichecklists.core.designsystem.generated.resources.chat_preview_cancelled_message
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.picker.FilePickerType
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.picker.rememberFilePickerLauncher
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.recorder.rememberAudioRecorderLauncher
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Route composable for the AI Chat destination.
 *
 * Owns the ViewModel lifecycle and collects [ChatScreenSideEffect] one-shots.
 * [ChatScreen] stays stateless — it only receives state and emits intents.
 *
 * SideEffect handling:
 *   - [ChatScreenSideEffect.ShowSnackbar] → resolves [messageKey] via the
 *     pre-built locale map and shows the result on [snackbarHostState].
 *   - [ChatScreenSideEffect.ShowAssistantMessage] → resolves the key
 *     (with optional `%1$s` args), then round-trips back as
 *     [ChatScreenIntent.AppendAssistantMessage] so the ViewModel adds it
 *     to chat history. This keeps all user-facing strings out of ViewModel
 *     and bound to system locale automatically.
 *   - [ChatScreenSideEffect.NavigateBack] → calls [onBack].
 *   - [ChatScreenSideEffect.RequestRecordAudioPermission] → triggers platform
 *     permission dialog via [audioRecorderLauncher.start()]. If denied, shows
 *     a snackbar with [chat_mic_permission_denied].
 *   - [ChatScreenSideEffect.OpenFilePicker] → launches the appropriate picker
 *     (image / pdf / text via FilePicker; audio via AudioRecorder).
 *
 * Why pre-resolve in Composable scope: `stringResource()` is a @Composable
 * call and cannot run inside `LaunchedEffect.collect`. We materialise every
 * chat_* key once per composition into a plain `Map<String, String>`, then
 * look up by key inside the coroutine.
 */
@Composable
fun ChatRoute(
    drawerState: DrawerState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onNavigateToChecklist: ((Long) -> Unit)? = null,
    onNavigateToPaywall: (() -> Unit)? = null,
    viewModel: ChatViewModel = koinViewModel(),
) {
    val state by viewModel.screenState.collectAsState()

    // Tracks the source type we're waiting to pick, used by trigger-flag LaunchedEffect
    var pendingPickerSource by remember { mutableStateOf<AttachmentSource?>(null) }

    // Pre-resolve all chat_* messages once per locale change.
    val unknownText = stringResource(Res.string.chat_unknown_intent_hint)
    val genericErrorText = stringResource(Res.string.chat_generic_error)
    val applyErrorText = stringResource(Res.string.chat_apply_error)
    val extractFailText = stringResource(Res.string.chat_extract_fail)
    val ambiguousMatchFmt = stringResource(Res.string.chat_ambiguous_match)
    val notFoundFmt = stringResource(Res.string.chat_not_found)
    val requiresPremiumText = stringResource(Res.string.chat_requires_premium)
    // Dispatch outcome messages (C.1 i18n)
    val dispatchAddedFmt = stringResource(Res.string.chat_dispatch_added)
    val dispatchAddedToFmt = stringResource(Res.string.chat_dispatch_added_to)
    val dispatchDeletedFmt = stringResource(Res.string.chat_dispatch_deleted)
    val dispatchItemNotFoundFmt = stringResource(Res.string.chat_dispatch_item_not_found)
    val dispatchCompletedFmt = stringResource(Res.string.chat_dispatch_completed)
    val dispatchAlreadyDoneFmt = stringResource(Res.string.chat_dispatch_already_done)
    val dispatchCreatedEmptyFmt = stringResource(Res.string.chat_dispatch_created_empty)
    val dispatchCreatedWithOneFmt = stringResource(Res.string.chat_dispatch_created_with_one)
    val dispatchCreatedWithManyFmt = stringResource(Res.string.chat_dispatch_created_with_many)
    val dispatchReminderSetFmt = stringResource(Res.string.chat_dispatch_reminder_set)
    val dispatchNoRemindersOnDayFmt = stringResource(Res.string.chat_dispatch_no_reminders_on_day)
    val dispatchMovedOneFmt = stringResource(Res.string.chat_dispatch_moved_one)
    val dispatchMovedManyFmt = stringResource(Res.string.chat_dispatch_moved_many)
    val dispatchFindBlankText = stringResource(Res.string.chat_dispatch_find_blank)
    val dispatchFindNoMatchFmt = stringResource(Res.string.chat_dispatch_find_no_match)
    val dispatchFindSuccessFmt = stringResource(Res.string.chat_dispatch_find_success)
    val dispatchOperationFailedFmt = stringResource(Res.string.chat_dispatch_operation_failed)
    val dispatchNoChecklistsText = stringResource(Res.string.chat_dispatch_no_checklists)
    val dispatchNoChecklistMatchFmt = stringResource(Res.string.chat_dispatch_no_checklist_match)
    val dispatchFillLoadFailedFmt = stringResource(Res.string.chat_dispatch_fill_load_failed)
    val insufficientCreditsText = stringResource(Res.string.chat_insufficient_credits)
    val completionErrorText = stringResource(Res.string.chat_completion_error)
    val historyLoadErrorText = stringResource(Res.string.chat_history_load_error)
    val feedbackSubmittedText = stringResource(Res.string.chat_feedback_submitted)
    val feedbackBlankHintText = stringResource(Res.string.chat_feedback_blank_hint)
    // Phase 3 strings
    val micPermissionDeniedText = stringResource(Res.string.chat_mic_permission_denied)
    val voiceTooShortText = stringResource(Res.string.chat_voice_too_short)
    val recordingCancelledText = stringResource(Res.string.chat_recording_cancelled)
    val thumbUpThanksText = stringResource(Res.string.chat_thumb_up_thanks)
    val previewCancelledText = stringResource(Res.string.chat_preview_cancelled_message)
    // Transcription strings (STT flow)
    val transcribingText = stringResource(Res.string.chat_transcribing)
    val transcribeEmptyText = stringResource(Res.string.chat_transcribe_empty)
    val transcribeErrorText = stringResource(Res.string.chat_transcribe_error)

    val messages = remember(
        unknownText, genericErrorText, applyErrorText, extractFailText,
        ambiguousMatchFmt, notFoundFmt, requiresPremiumText,
        dispatchAddedFmt, dispatchAddedToFmt, dispatchDeletedFmt, dispatchItemNotFoundFmt,
        dispatchCompletedFmt, dispatchAlreadyDoneFmt, dispatchCreatedEmptyFmt,
        dispatchCreatedWithOneFmt, dispatchCreatedWithManyFmt, dispatchReminderSetFmt,
        dispatchNoRemindersOnDayFmt, dispatchMovedOneFmt, dispatchMovedManyFmt,
        dispatchFindBlankText, dispatchFindNoMatchFmt, dispatchFindSuccessFmt,
        dispatchOperationFailedFmt, dispatchNoChecklistsText, dispatchNoChecklistMatchFmt,
        dispatchFillLoadFailedFmt, insufficientCreditsText, completionErrorText, historyLoadErrorText,
        feedbackSubmittedText, feedbackBlankHintText,
        micPermissionDeniedText, voiceTooShortText, recordingCancelledText, thumbUpThanksText,
        previewCancelledText, transcribingText, transcribeEmptyText, transcribeErrorText,
    ) {
        mapOf(
            "chat_unknown_intent_hint" to unknownText,
            "chat_generic_error" to genericErrorText,
            "chat_apply_error" to applyErrorText,
            "chat_extract_fail" to extractFailText,
            "chat_ambiguous_match" to ambiguousMatchFmt,
            "chat_not_found" to notFoundFmt,
            "chat_requires_premium" to requiresPremiumText,
            "chat_dispatch_added" to dispatchAddedFmt,
            "chat_dispatch_added_to" to dispatchAddedToFmt,
            "chat_dispatch_deleted" to dispatchDeletedFmt,
            "chat_dispatch_item_not_found" to dispatchItemNotFoundFmt,
            "chat_dispatch_completed" to dispatchCompletedFmt,
            "chat_dispatch_already_done" to dispatchAlreadyDoneFmt,
            "chat_dispatch_created_empty" to dispatchCreatedEmptyFmt,
            "chat_dispatch_created_with_one" to dispatchCreatedWithOneFmt,
            "chat_dispatch_created_with_many" to dispatchCreatedWithManyFmt,
            "chat_dispatch_reminder_set" to dispatchReminderSetFmt,
            "chat_dispatch_no_reminders_on_day" to dispatchNoRemindersOnDayFmt,
            "chat_dispatch_moved_one" to dispatchMovedOneFmt,
            "chat_dispatch_moved_many" to dispatchMovedManyFmt,
            "chat_dispatch_find_blank" to dispatchFindBlankText,
            "chat_dispatch_find_no_match" to dispatchFindNoMatchFmt,
            "chat_dispatch_find_success" to dispatchFindSuccessFmt,
            "chat_dispatch_operation_failed" to dispatchOperationFailedFmt,
            "chat_dispatch_no_checklists" to dispatchNoChecklistsText,
            "chat_dispatch_no_checklist_match" to dispatchNoChecklistMatchFmt,
            "chat_dispatch_fill_load_failed" to dispatchFillLoadFailedFmt,
            "chat_insufficient_credits" to insufficientCreditsText,
            "chat_completion_error" to completionErrorText,
            "chat_history_load_error" to historyLoadErrorText,
            "chat_feedback_submitted" to feedbackSubmittedText,
            "chat_feedback_blank_hint" to feedbackBlankHintText,
            "chat_mic_permission_denied" to micPermissionDeniedText,
            "chat_voice_too_short" to voiceTooShortText,
            "chat_recording_cancelled" to recordingCancelledText,
            "chat_thumb_up_thanks" to thumbUpThanksText,
            "chat_preview_cancelled_message" to previewCancelledText,
            "chat_transcribing" to transcribingText,
            "chat_transcribe_empty" to transcribeEmptyText,
            "chat_transcribe_error" to transcribeErrorText,
        )
    }

    // ── File pickers (one per supported type) ───────────────────────────────
    // Registered once in Composable scope per the trigger-flag pattern: the
    // picker is launched when pendingPickerSource is set to the matching type.

    val imagePicker = rememberFilePickerLauncher(
        type = FilePickerType.IMAGE,
        onResult = { result ->
            if (result != null) {
                viewModel.sendIntent(
                    ChatScreenIntent.OnAttachmentPicked(
                        ChatAttachment(
                            sourcePath = result.filePath,
                            mimeType = result.mimeType ?: "image/*",
                            fileName = result.fileName,
                            sizeBytes = 0L,
                        )
                    )
                )
            }
            viewModel.sendIntent(ChatScreenIntent.OnAttachmentPickerTriggered)
        }
    )

    val pdfPicker = rememberFilePickerLauncher(
        type = FilePickerType.PDF,
        onResult = { result ->
            if (result != null) {
                viewModel.sendIntent(
                    ChatScreenIntent.OnAttachmentPicked(
                        ChatAttachment(
                            sourcePath = result.filePath,
                            mimeType = result.mimeType ?: "application/pdf",
                            fileName = result.fileName,
                            sizeBytes = 0L,
                        )
                    )
                )
            }
            viewModel.sendIntent(ChatScreenIntent.OnAttachmentPickerTriggered)
        }
    )

    val textPicker = rememberFilePickerLauncher(
        type = FilePickerType.TEXT,
        onResult = { result ->
            if (result != null) {
                viewModel.sendIntent(
                    ChatScreenIntent.OnAttachmentPicked(
                        ChatAttachment(
                            sourcePath = result.filePath,
                            mimeType = result.mimeType ?: "text/plain",
                            fileName = result.fileName,
                            sizeBytes = 0L,
                        )
                    )
                )
            }
            viewModel.sendIntent(ChatScreenIntent.OnAttachmentPickerTriggered)
        }
    )

    val audioFilePicker = rememberFilePickerLauncher(
        type = FilePickerType.AUDIO,
        onResult = { result ->
            if (result != null) {
                viewModel.sendIntent(
                    ChatScreenIntent.OnAttachmentPicked(
                        ChatAttachment(
                            sourcePath = result.filePath,
                            mimeType = result.mimeType ?: "audio/*",
                            fileName = result.fileName,
                            sizeBytes = 0L,
                        )
                    )
                )
            }
            viewModel.sendIntent(ChatScreenIntent.OnAttachmentPickerTriggered)
        }
    )

    // ── Audio recorder ───────────────────────────────────────────────────────
    val audioRecorder = rememberAudioRecorderLauncher(
        onResult = { result ->
            // result == null means user cancelled or recording was too short
            viewModel.sendIntent(ChatScreenIntent.OnVoiceRecordingStopped(result?.filePath))
        },
        onError = { _ ->
            // Permission denied or hardware error — snackbar shown below
            viewModel.sendIntent(ChatScreenIntent.OnVoiceRecordingStopped(null))
        }
    )

    // ── Trigger-flag: launch pickers when pendingPickerSource is set ─────────
    LaunchedEffect(state.attachmentPickerType) {
        val source = state.attachmentPickerType ?: return@LaunchedEffect
        pendingPickerSource = source
        when (source) {
            AttachmentSource.Image -> imagePicker.launch()
            AttachmentSource.Pdf -> pdfPicker.launch()
            AttachmentSource.Text -> textPicker.launch()
            // Audio in source-chooser = "pick existing audio file from device storage".
            // New voice recording is a separate flow via press-and-hold mic in ChatInputRow.
            AttachmentSource.Audio -> audioFilePicker.launch()
        }
        // Reset the trigger-flag in ViewModel so it doesn't fire again on recomposition
        viewModel.sendIntent(ChatScreenIntent.OnAttachmentPickerTriggered)
    }

    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is ChatScreenSideEffect.ShowSnackbar -> {
                    val text = messages[effect.messageKey] ?: effect.messageKey
                    snackbarHostState.showSnackbar(text)
                }
                is ChatScreenSideEffect.ShowAssistantMessage -> {
                    val template = messages[effect.messageKey] ?: effect.messageKey
                    val resolved = applyFormatArgs(template, effect.args)
                    viewModel.sendIntent(
                        ChatScreenIntent.AppendAssistantMessage(
                            text = resolved,
                            linkedChecklistId = effect.linkedChecklistId,
                        )
                    )
                }
                ChatScreenSideEffect.NavigateBack -> onBack()
                is ChatScreenSideEffect.NavigateToChecklist -> onNavigateToChecklist?.invoke(effect.checklistId)
                ChatScreenSideEffect.NavigateToPaywall -> onNavigateToPaywall?.invoke()
                ChatScreenSideEffect.RequestRecordAudioPermission -> {
                    // AudioRecorderLauncher handles permission check internally.
                    // Starting the recorder here requests permission if needed;
                    // if denied, onError fires → OnVoiceRecordingStopped(null) → snackbar
                    // via chat_recording_cancelled path. A dedicated mic_permission_denied
                    // snackbar appears only on permanent denial (PERMISSION_DENIED_PERMANENTLY).
                    audioRecorder.start()
                }
                is ChatScreenSideEffect.OpenFilePicker -> {
                    // ViewModel sets attachmentPickerType as a trigger-flag; the
                    // LaunchedEffect(state.attachmentPickerType) above handles the launch.
                    // This branch intentionally no-ops — the trigger-flag pattern avoids
                    // dual-launch on the same event.
                }
            }
        }
    }

    ChatScreen(
        state = state,
        onIntent = viewModel::sendIntent,
        drawerState = drawerState,
        onNavigateToPaywall = onNavigateToPaywall,
        onAttachmentSourcePicked = { source ->
            // ChatScreen already sent OnPickAttachment via onIntent; this callback
            // lets Route launch the picker directly without waiting for sideEffect.
            when (source) {
                AttachmentSource.Image -> imagePicker.launch()
                AttachmentSource.Pdf -> pdfPicker.launch()
                AttachmentSource.Text -> textPicker.launch()
                AttachmentSource.Audio -> audioFilePicker.launch()
            }
        },
        // Press-and-hold mic gesture: ChatInputRow invokes this on finger down.
        // Direct call to audioRecorder.start() (which itself handles permission
        // request through ActivityResult API). Previously was `null` — no-op bug.
        onVoiceRecordingStarted = { audioRecorder.start() },
        onVoiceRecordingStopped = { audioRecorder.stop() },
        onVoiceRecordingCancelled = { audioRecorder.cancel() },
    )
}

/**
 * Substitutes `%1$s`, `%2$s`, … placeholders with the given args (positional).
 * `stringResource(..., *args)` is @Composable-only, so we do simple manual
 * substitution here for use from a coroutine context.
 */
private fun applyFormatArgs(template: String, args: List<String>): String {
    if (args.isEmpty()) return template
    var result = template
    args.forEachIndexed { index, arg ->
        val placeholder = "%${index + 1}\$s"
        result = result.replace(placeholder, arg)
    }
    return result
}
