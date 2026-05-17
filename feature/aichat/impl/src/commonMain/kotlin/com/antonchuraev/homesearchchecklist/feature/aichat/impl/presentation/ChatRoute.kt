package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.viewmodel.koinViewModel

/**
 * Route composable for the AI Chat destination.
 *
 * Owns the ViewModel lifecycle and collects [ChatScreenSideEffect] one-shots.
 * [ChatScreen] stays stateless — it only receives state and emits intents.
 *
 * Navigation pattern: same as Today/Calendar — per-destination [DrawerState]
 * is passed in from the NavHost composable (App.kt) so the drawer works correctly.
 *
 * SideEffect handling:
 *   - [ChatScreenSideEffect.ShowSnackbar] → resolves [messageKey] to a display string
 *     and shows via the shared [snackbarHostState].
 *   - [ChatScreenSideEffect.NavigateBack] → calls [onBack].
 *
 * Note: snackbar message resolution uses a plain when-map rather than
 * Compose Resources because Composable invocations cannot be called inside
 * a coroutine (LaunchedEffect). Keys correspond to strings.xml keys.
 */
@Composable
fun ChatRoute(
    drawerState: DrawerState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    viewModel: ChatViewModel = koinViewModel(),
) {
    val state by viewModel.screenState.collectAsState()

    // Collect one-shot side effects
    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is ChatScreenSideEffect.ShowSnackbar -> {
                    val message = resolveSnackbarMessage(effect.messageKey)
                    snackbarHostState.showSnackbar(message)
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
 * Maps [messageKey] (a strings.xml key) to a displayable string.
 *
 * Compose Resources cannot be called from a suspend context (LaunchedEffect),
 * so we resolve strings here as a plain map. Extend when new snackbar keys are added.
 */
private fun resolveSnackbarMessage(messageKey: String): String = when (messageKey) {
    "chat_unknown_intent_hint" ->
        "I didn't catch that. Try «add milk to shopping» or «remind me on Friday at 6pm»."
    "chat_requires_premium" ->
        "This action requires a Premium subscription."
    else -> messageKey
}
