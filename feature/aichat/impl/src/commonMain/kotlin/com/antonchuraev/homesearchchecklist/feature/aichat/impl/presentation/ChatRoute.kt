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

    val messages = remember(
        unknownText, genericErrorText, applyErrorText, extractFailText,
        ambiguousMatchFmt, notFoundFmt, requiresPremiumText,
    ) {
        mapOf(
            "chat_unknown_intent_hint" to unknownText,
            "chat_generic_error" to genericErrorText,
            "chat_apply_error" to applyErrorText,
            "chat_extract_fail" to extractFailText,
            "chat_ambiguous_match" to ambiguousMatchFmt,
            "chat_not_found" to notFoundFmt,
            "chat_requires_premium" to requiresPremiumText,
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
