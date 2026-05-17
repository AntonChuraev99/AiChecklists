package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import aichecklists.core.designsystem.generated.resources.chat_insufficient_credits
import aichecklists.core.designsystem.generated.resources.chat_completion_error
import aichecklists.core.designsystem.generated.resources.chat_history_load_error
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
import aichecklists.core.designsystem.generated.resources.chat_generic_error
import aichecklists.core.designsystem.generated.resources.chat_not_found
import aichecklists.core.designsystem.generated.resources.chat_requires_premium
import aichecklists.core.designsystem.generated.resources.chat_unknown_intent_hint
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
    onNavigateToPaywall: (() -> Unit)? = null,
    viewModel: ChatViewModel = koinViewModel(),
) {
    val state by viewModel.screenState.collectAsState()

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
        )
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
                    viewModel.sendIntent(ChatScreenIntent.AppendAssistantMessage(resolved))
                }
                ChatScreenSideEffect.NavigateBack -> onBack()
            }
        }
    }

    ChatScreen(
        state = state,
        onIntent = viewModel::sendIntent,
        drawerState = drawerState,
        onNavigateToPaywall = onNavigateToPaywall,
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
