package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_ambiguous_match
import aichecklists.core.designsystem.generated.resources.chat_apply_error
import aichecklists.core.designsystem.generated.resources.chat_dispatch_added
import aichecklists.core.designsystem.generated.resources.chat_dispatch_added_to
import aichecklists.core.designsystem.generated.resources.chat_dispatch_added_many_to
import aichecklists.core.designsystem.generated.resources.chat_dispatch_add_empty
import aichecklists.core.designsystem.generated.resources.chat_dispatch_renamed
import aichecklists.core.designsystem.generated.resources.chat_dispatch_already_done
import aichecklists.core.designsystem.generated.resources.chat_dispatch_completed
import aichecklists.core.designsystem.generated.resources.chat_dispatch_completed_items_removed
import aichecklists.core.designsystem.generated.resources.chat_dispatch_created_empty
import aichecklists.core.designsystem.generated.resources.chat_dispatch_created_from_attachment
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
import aichecklists.core.designsystem.generated.resources.chat_dispatch_no_completed_items
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
import aichecklists.core.designsystem.generated.resources.chat_error_offline
import aichecklists.core.designsystem.generated.resources.chat_error_service
import aichecklists.core.designsystem.generated.resources.chat_error_timeout
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
import aichecklists.core.designsystem.generated.resources.chat_agent_round_limit
import aichecklists.core.designsystem.generated.resources.chat_move_no_other_lists
import aichecklists.core.designsystem.generated.resources.chat_attach_analyze_empty
import aichecklists.core.designsystem.generated.resources.chat_attach_analyze_failed
import aichecklists.core.designsystem.generated.resources.chat_attach_limit_reached
import aichecklists.core.designsystem.generated.resources.chat_attach_no_files
import aichecklists.core.designsystem.generated.resources.chat_attach_store_failed
import aichecklists.core.designsystem.generated.resources.chat_attach_unsupported_type
import aichecklists.core.designsystem.generated.resources.chat_choice_dismissed_message
import aichecklists.core.designsystem.generated.resources.chat_choice_edit_empty_hint
import aichecklists.core.designsystem.generated.resources.chat_dispatch_attached_many
import aichecklists.core.designsystem.generated.resources.chat_dispatch_attached_one
import aichecklists.core.designsystem.generated.resources.chat_result_moved_to
import aichecklists.core.designsystem.generated.resources.chat_result_remembered_list
import aichecklists.core.designsystem.generated.resources.chat_result_undone_add
import aichecklists.core.designsystem.generated.resources.chat_result_undone_complete
import aichecklists.core.designsystem.generated.resources.chat_undo_item_gone
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.FilePickerType
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.rememberFilePickerLauncher
import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.rememberAudioRecorderLauncher
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
    drawerState: DrawerState?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onNavigateToChecklist: ((Long) -> Unit)? = null,
    /**
     * Opens the paywall. The `source` argument is the `paywall_shown` attribution tag and comes
     * from [ChatScreenSideEffect.NavigateToPaywall.source] — forward it verbatim. Do not collapse
     * it to a constant here: hitting the credit limit and tapping the credits chip are different
     * events, and the Layer 1 disconnect is measured on telling them apart.
     */
    onNavigateToPaywall: ((String) -> Unit)? = null,
    viewModel: ChatViewModel = koinViewModel(),
) {
    val state by viewModel.screenState.collectAsState()

    // Funnel: fire ai_chat_opened + screenView(CHAT) once per full-screen open.
    // key=Unit so it fires on entry only, not on every recomposition.
    LaunchedEffect(Unit) {
        viewModel.sendIntent(ChatScreenIntent.OnChatOpened(source = "screen"))
    }

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
    val dispatchAddedManyToFmt = stringResource(Res.string.chat_dispatch_added_many_to)
    val dispatchAddEmptyText = stringResource(Res.string.chat_dispatch_add_empty)
    val dispatchRenamedFmt = stringResource(Res.string.chat_dispatch_renamed)
    val dispatchDeletedFmt = stringResource(Res.string.chat_dispatch_deleted)
    val dispatchItemNotFoundFmt = stringResource(Res.string.chat_dispatch_item_not_found)
    val dispatchCompletedFmt = stringResource(Res.string.chat_dispatch_completed)
    val dispatchAlreadyDoneFmt = stringResource(Res.string.chat_dispatch_already_done)
    val dispatchCreatedEmptyFmt = stringResource(Res.string.chat_dispatch_created_empty)
    val dispatchCreatedFromAttachmentFmt = stringResource(Res.string.chat_dispatch_created_from_attachment)
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
    val dispatchCompletedItemsRemovedFmt = stringResource(Res.string.chat_dispatch_completed_items_removed)
    val dispatchNoCompletedItemsFmt = stringResource(Res.string.chat_dispatch_no_completed_items)
    val insufficientCreditsText = stringResource(Res.string.chat_insufficient_credits)
    val completionErrorText = stringResource(Res.string.chat_completion_error)
    // F1 connectivity-aware error replies (offline / service / timeout). Keep both maps in step.
    val errorOfflineText = stringResource(Res.string.chat_error_offline)
    val errorServiceText = stringResource(Res.string.chat_error_service)
    val errorTimeoutText = stringResource(Res.string.chat_error_timeout)
    val historyLoadErrorText = stringResource(Res.string.chat_history_load_error)
    val feedbackSubmittedText = stringResource(Res.string.chat_feedback_submitted)
    val feedbackBlankHintText = stringResource(Res.string.chat_feedback_blank_hint)
    // D1 reversible-action replies (Undo / move-to-list). Must stay in sync with the same map in
    // App.kt (the dock) — a key present in one and missing in the other ships the raw key on that
    // surface only.
    val resultUndoneAddFmt = stringResource(Res.string.chat_result_undone_add)
    val resultUndoneCompleteFmt = stringResource(Res.string.chat_result_undone_complete)
    val resultMovedToFmt = stringResource(Res.string.chat_result_moved_to)
    val undoItemGoneText = stringResource(Res.string.chat_undo_item_gone)
    val moveNoOtherListsText = stringResource(Res.string.chat_move_no_other_lists)
    // D2 memory-of-choice disclosure. Same sync rule as above — also lives in App.kt's map.
    val resultRememberedListFmt = stringResource(Res.string.chat_result_remembered_list)
    // Pre-existing gap found while wiring D2: emitted as a ShowAssistantMessage since D1 but never
    // added to either map, so every choice cancel printed the raw key into the bubble.
    val choiceDismissedText = stringResource(Res.string.chat_choice_dismissed_message)
    // The whole attach contour — both success replies and the entire error surface — plus the
    // blank-edit hint. Same gap: emitted, translated, never resolved, so the user read
    // "chat_dispatch_attached_one" where the confirmation should be. Guarded now by
    // ChatMessageKeyResolutionTest; keep both maps in step when adding to either.
    val attachNoFilesText = stringResource(Res.string.chat_attach_no_files)
    val attachLimitReachedText = stringResource(Res.string.chat_attach_limit_reached)
    val attachUnsupportedTypeFmt = stringResource(Res.string.chat_attach_unsupported_type)
    val attachAnalyzeEmptyFmt = stringResource(Res.string.chat_attach_analyze_empty)
    val attachAnalyzeFailedFmt = stringResource(Res.string.chat_attach_analyze_failed)
    val attachStoreFailedFmt = stringResource(Res.string.chat_attach_store_failed)
    val dispatchAttachedOneFmt = stringResource(Res.string.chat_dispatch_attached_one)
    val dispatchAttachedManyFmt = stringResource(Res.string.chat_dispatch_attached_many)
    val choiceEditEmptyHintText = stringResource(Res.string.chat_choice_edit_empty_hint)
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
    // Agentic loop (Phase 2d)
    val agentRoundLimitText = stringResource(Res.string.chat_agent_round_limit)

    // NOT remember()-ed, and the hand-listed key set that used to sit here is gone on purpose.
    // `stringResource` resolves asynchronously in Compose Multiplatform and returns "" for the
    // first frames. A remember() keyed on a SUBSET of these strings caches the map while the rest
    // are still empty, and those entries stay empty forever — the reply renders as an empty
    // assistant bubble. Keeping the key list complete by hand is exactly the invariant that broke:
    // every new string had to be added in THREE places (declaration, key list, map), and the D1
    // keys made it into two of them. (Found 2026-07-15 on :9090.)
    // Rebuilding a ~50-entry map per recomposition is noise next to the canvas draw.
    val messages = run {
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
            "chat_dispatch_added_many_to" to dispatchAddedManyToFmt,
            "chat_dispatch_add_empty" to dispatchAddEmptyText,
            "chat_dispatch_renamed" to dispatchRenamedFmt,
            "chat_dispatch_deleted" to dispatchDeletedFmt,
            "chat_dispatch_item_not_found" to dispatchItemNotFoundFmt,
            "chat_dispatch_completed" to dispatchCompletedFmt,
            "chat_dispatch_already_done" to dispatchAlreadyDoneFmt,
            "chat_dispatch_created_empty" to dispatchCreatedEmptyFmt,
            "chat_dispatch_created_from_attachment" to dispatchCreatedFromAttachmentFmt,
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
            "chat_dispatch_completed_items_removed" to dispatchCompletedItemsRemovedFmt,
            "chat_dispatch_no_completed_items" to dispatchNoCompletedItemsFmt,
            "chat_insufficient_credits" to insufficientCreditsText,
            "chat_completion_error" to completionErrorText,
            "chat_error_offline" to errorOfflineText,
            "chat_error_service" to errorServiceText,
            "chat_error_timeout" to errorTimeoutText,
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
            "chat_agent_round_limit" to agentRoundLimitText,
            "chat_result_undone_add" to resultUndoneAddFmt,
            "chat_result_undone_complete" to resultUndoneCompleteFmt,
            "chat_result_moved_to" to resultMovedToFmt,
            "chat_undo_item_gone" to undoItemGoneText,
            "chat_move_no_other_lists" to moveNoOtherListsText,
            "chat_result_remembered_list" to resultRememberedListFmt,
            "chat_choice_dismissed_message" to choiceDismissedText,
            "chat_attach_no_files" to attachNoFilesText,
            "chat_attach_limit_reached" to attachLimitReachedText,
            "chat_attach_unsupported_type" to attachUnsupportedTypeFmt,
            "chat_attach_analyze_empty" to attachAnalyzeEmptyFmt,
            "chat_attach_analyze_failed" to attachAnalyzeFailedFmt,
            "chat_attach_store_failed" to attachStoreFailedFmt,
            "chat_dispatch_attached_one" to dispatchAttachedOneFmt,
            "chat_dispatch_attached_many" to dispatchAttachedManyFmt,
            "chat_choice_edit_empty_hint" to choiceEditEmptyHintText,
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
            viewModel.sendIntent(
                ChatScreenIntent.OnVoiceRecordingStopped(
                    recordingPath = result?.filePath,
                    mimeType = result?.mimeType ?: "audio/m4a",
                )
            )
        },
        onError = { _ ->
            // Permission denied or hardware error — snackbar shown below
            viewModel.sendIntent(ChatScreenIntent.OnVoiceRecordingStopped(recordingPath = null))
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

    // rememberUpdatedState, NOT the map directly: LaunchedEffect captures its lambda ONCE
    // (key = viewModel), and Compose Resources resolve asynchronously — on the first frame every
    // stringResource is still "". A directly-captured map freezes those empty values for the
    // collector's lifetime and every reply renders as a blank bubble. Same stale-closure trap as
    // the wasmJs FilePicker callbacks (project memory: filepicker-rememberupdatedstate-closure-trap).
    val currentMessages by rememberUpdatedState(messages)
    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is ChatScreenSideEffect.ShowSnackbar -> {
                    val text = currentMessages[effect.messageKey] ?: effect.messageKey
                    snackbarHostState.showSnackbar(text)
                }
                is ChatScreenSideEffect.ShowAssistantMessage -> {
                    val template = currentMessages[effect.messageKey] ?: effect.messageKey
                    val resolved = applyFormatArgs(template, effect.args)
                    viewModel.sendIntent(
                        ChatScreenIntent.AppendAssistantMessage(
                            text = resolved,
                            linkedChecklistId = effect.linkedChecklistId,
                            askAiForText = effect.askAiForText,
                            paywallCtaCredits = effect.paywallCtaCredits,
                            retryText = effect.retryText,
                            routedLayer = effect.routedLayer,
                        )
                    )
                }
                ChatScreenSideEffect.NavigateBack -> onBack()
                is ChatScreenSideEffect.NavigateToChecklist -> onNavigateToChecklist?.invoke(effect.checklistId)
                is ChatScreenSideEffect.NavigateToPaywall -> onNavigateToPaywall?.invoke(effect.source)
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
        // ChatScreen's own paywall entry point is the credits chip in the top bar — an unprompted
        // tap, NOT a user who ran out mid-turn (that one arrives as the NavigateToPaywall effect
        // above, carrying its own source). Tagging both the same is what merged the two.
        onNavigateToPaywall = onNavigateToPaywall?.let { navigate ->
            { navigate(ChatScreenSideEffect.NavigateToPaywall.SOURCE_CREDITS_CHIP) }
        },
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
