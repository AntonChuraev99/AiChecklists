package com.antonchuraev.homesearchchecklist.telegram

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.telegram_disconnect_failed
import aichecklists.core.designsystem.generated.resources.telegram_disconnected_snackbar
import aichecklists.core.designsystem.generated.resources.telegram_error_account
import aichecklists.core.designsystem.generated.resources.telegram_error_network
import aichecklists.core.designsystem.generated.resources.telegram_error_server
import aichecklists.core.designsystem.generated.resources.telegram_link_expired
import aichecklists.core.designsystem.generated.resources.telegram_link_failed
import aichecklists.core.designsystem.generated.resources.telegram_linked_snackbar
import aichecklists.core.designsystem.generated.resources.telegram_linked_snackbar_plain
import aichecklists.core.designsystem.generated.resources.telegram_open_failed
import aichecklists.core.designsystem.generated.resources.telegram_pending_not_yet
import aichecklists.core.designsystem.generated.resources.telegram_reminders_save_failed
import aichecklists.core.designsystem.generated.resources.telegram_retry
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.navigation.safeOpenUriResult
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val TAG = "Telegram"

/**
 * Stateful host of [TelegramScreen]: ViewModel, lifecycle, external opens, snackbars.
 *
 * External opens happen HERE, synchronously inside the tap callback, whenever a link is ready
 * ([TelegramViewModel.readyDeepLink] / [TelegramConfig.BOT_URL]): on the web `window.open` outside a
 * user gesture is blocked as a popup, and a VM side effect arrives on a later tick. The outcome is
 * reported back as [TelegramIntent.UrlOpened].
 *
 * @param source entry point for `telegram_screen_opened` (`AnalyticsEvents.Telegram.SCREEN_SOURCE_*`).
 * @param onSignInClick the app's Google sign-in (`handleSignIn` in App.kt); its progress and result
 *   reach the VM through the auth state, not through this callback.
 */
@Composable
fun TelegramRoute(
    source: String,
    onBack: () -> Unit,
    onSignInClick: () -> Unit,
) {
    val viewModel: TelegramViewModel = koinViewModel()
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val logger: AppLogger = koinInject()
    val snackbarHostState = remember { SnackbarHostState() }

    val openNow: (String, TelegramUrlPurpose) -> Unit = { url, purpose ->
        val opened = safeOpenUriResult(uriHandler, logger, url, TAG)
        viewModel.sendIntent(TelegramIntent.UrlOpened(purpose, opened))
    }
    val connect: () -> Unit = {
        val link = viewModel.readyDeepLink()
        viewModel.sendIntent(TelegramIntent.ConnectClicked(linkOpenedByHost = link != null))
        if (link != null) openNow(link, TelegramUrlPurpose.Connect)
    }

    LaunchedEffect(Unit) { viewModel.sendIntent(TelegramIntent.ScreenEnteredFrom(source)) }
    LifecycleResumeEffect(Unit) {
        viewModel.sendIntent(TelegramIntent.ScreenResumed)
        onPauseOrDispose { viewModel.sendIntent(TelegramIntent.ScreenPaused) }
    }
    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is TelegramSideEffect.OpenUrl -> openNow(effect.url, effect.purpose)
                is TelegramSideEffect.ShowSnackbar -> launch {
                    val resolved = resolveTelegramSnackbar(effect.message)
                    val result = snackbarHostState.showSnackbar(
                        message = resolved.text,
                        actionLabel = resolved.actionLabel,
                        duration = resolved.duration,
                    )
                    // "Try again" actions. On a failed Connect the action tap is a user gesture, so
                    // the synchronous open in [connect] is still allowed on the web.
                    if (result == SnackbarResult.ActionPerformed) {
                        when (val message = effect.message) {
                            is TelegramSnackbar.ConnectFailed -> connect()
                            is TelegramSnackbar.ReminderSaveFailed ->
                                viewModel.sendIntent(TelegramIntent.ReminderChannelClicked(message.attempted))
                            else -> Unit // no other snackbar carries an action
                        }
                    }
                }
            }
        }
    }

    TelegramScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAction = { action ->
            when (action) {
                TelegramAction.SignInClick -> onSignInClick()
                TelegramAction.ConnectClick -> connect()
                TelegramAction.OpenTelegramClick -> {
                    val link = viewModel.readyDeepLink()
                    viewModel.sendIntent(TelegramIntent.OpenTelegramClicked(linkOpenedByHost = link != null))
                    if (link != null) openNow(link, TelegramUrlPurpose.Reopen)
                }
                TelegramAction.OpenBotClick -> openNow(TelegramConfig.BOT_URL, TelegramUrlPurpose.Bot)
                TelegramAction.CheckAgainClick -> viewModel.sendIntent(TelegramIntent.CheckAgainClicked)
                TelegramAction.DisconnectConfirm -> viewModel.sendIntent(TelegramIntent.DisconnectConfirmed)
                TelegramAction.RetryClick -> viewModel.sendIntent(TelegramIntent.RetryClicked)
                is TelegramAction.ReminderChannelClick ->
                    viewModel.sendIntent(TelegramIntent.ReminderChannelClicked(action.target))
                is TelegramAction.WebReminderClick ->
                    viewModel.sendIntent(TelegramIntent.WebReminderChannelClicked(action.on))
            }
        },
    )
}

/** A [TelegramSnackbar] resolved to text, with the spec's duration (§4.7) and optional action. */
data class ResolvedTelegramSnackbar(
    val text: String,
    val duration: SnackbarDuration,
    val actionLabel: String? = null,
)

/** Resolves a typed message through Compose Resources (also used by App.kt's `?tg_link` handler). */
suspend fun resolveTelegramSnackbar(message: TelegramSnackbar): ResolvedTelegramSnackbar = when (message) {
    is TelegramSnackbar.Linked -> ResolvedTelegramSnackbar(
        text = message.username?.let { getString(Res.string.telegram_linked_snackbar, it) }
            ?: getString(Res.string.telegram_linked_snackbar_plain),
        duration = SnackbarDuration.Short,
    )
    TelegramSnackbar.Disconnected ->
        ResolvedTelegramSnackbar(getString(Res.string.telegram_disconnected_snackbar), SnackbarDuration.Short)
    TelegramSnackbar.NotYet ->
        ResolvedTelegramSnackbar(getString(Res.string.telegram_pending_not_yet), SnackbarDuration.Long)
    TelegramSnackbar.DisconnectFailed ->
        ResolvedTelegramSnackbar(getString(Res.string.telegram_disconnect_failed), SnackbarDuration.Long)
    TelegramSnackbar.OpenFailed ->
        ResolvedTelegramSnackbar(getString(Res.string.telegram_open_failed), SnackbarDuration.Long)
    is TelegramSnackbar.ConnectFailed -> ResolvedTelegramSnackbar(
        text = getString(message.reason.messageRes()),
        duration = SnackbarDuration.Long,
        actionLabel = getString(Res.string.telegram_retry),
    )
    is TelegramSnackbar.RequestFailed ->
        ResolvedTelegramSnackbar(getString(message.reason.messageRes()), SnackbarDuration.Long)
    TelegramSnackbar.LinkExpired ->
        ResolvedTelegramSnackbar(getString(Res.string.telegram_link_expired), SnackbarDuration.Long)
    TelegramSnackbar.LinkFailed ->
        ResolvedTelegramSnackbar(getString(Res.string.telegram_link_failed), SnackbarDuration.Long)
    is TelegramSnackbar.ReminderSaveFailed -> ResolvedTelegramSnackbar(
        text = getString(Res.string.telegram_reminders_save_failed),
        duration = SnackbarDuration.Long,
        actionLabel = getString(Res.string.telegram_retry),
    )
}

/** Error text per reason — shared by the Error card and the error snackbars. */
fun TelegramErrorReason.messageRes(): StringResource = when (this) {
    TelegramErrorReason.Network -> Res.string.telegram_error_network
    TelegramErrorReason.Server -> Res.string.telegram_error_server
    TelegramErrorReason.Account -> Res.string.telegram_error_account
}
