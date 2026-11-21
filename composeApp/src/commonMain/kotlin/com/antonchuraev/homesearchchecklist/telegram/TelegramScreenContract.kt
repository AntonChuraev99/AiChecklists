package com.antonchuraev.homesearchchecklist.telegram

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.SideEffect
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderChannel

/**
 * State of the Telegram account-link screen (`docs/designs/telegram-screen-2026-11-19/spec.md` §4).
 *
 * The Disconnect confirmation dialog is UI-local (`rememberSaveable` in the screen) and is NOT here:
 * a background refresh replaces this state, and the dialog must survive that.
 */
sealed interface TelegramScreenState : State {

    /** Signed in, status not known yet (first entry, re-entry, Retry). */
    data object Loading : TelegramScreenState

    /**
     * No Google account in the app. [isSigningIn] = the host's Google sign-in is running
     * (Sign-in button shows `loading`).
     */
    data class SignedOut(val isSigningIn: Boolean = false) : TelegramScreenState

    /**
     * Signed in, no Telegram linked. [isConnecting] = Connect was tapped before a link token was
     * ready and one is being created (Connect button shows `loading`).
     */
    data class NotLinked(val isConnecting: Boolean = false) : TelegramScreenState

    /**
     * The user was sent to Telegram, the link is not confirmed yet.
     *
     * @property isOpening a fresh token is being created for "Open Telegram" (button `loading`).
     * @property isChecking a manual "Check again" is running (button disabled).
     */
    data class Pending(
        val isOpening: Boolean = false,
        val isChecking: Boolean = false,
    ) : TelegramScreenState

    /**
     * Linked. Label priority for the account card: `@`[username] → [firstName] →
     * `telegram_linked_no_username`.
     *
     * @property linkedAtMs epoch ms of the link, null when the server did not send it
     *   (hide the "Connected since" line then).
     * @property isDisconnecting `unlink` is in flight: subtitle shows `telegram_disconnecting`,
     *   Open bot and Disconnect are disabled — and the whole reminder group is disabled.
     * @property reminderChannel the reminder channel the server last CONFIRMED. Null = the server
     *   does not report one (pre-phase-4) → the Reminders section is not rendered at all
     *   (`reminders-addendum.md` §1).
     * @property channelSaving the channel being saved right now. Non-null → that row shows the
     *   spinner and NO row shows a selected radio. Null → the radio sits on [reminderChannel].
     */
    data class Linked(
        val username: String?,
        val firstName: String?,
        val linkedAtMs: Long?,
        val isDisconnecting: Boolean = false,
        val reminderChannel: ReminderChannel? = null,
        val channelSaving: ReminderChannel? = null,
    ) : TelegramScreenState {
        /** Render the Reminders section (addendum §1 visibility rule). */
        val showsReminderSection: Boolean get() = reminderChannel != null

        /**
         * The Disconnect dialog shows the extra paragraph (`telegram_reminders_disconnect_note` on
         * Android, `telegram_reminders_disconnect_note_web` on web) — addendum §4.1.
         */
        val showsDisconnectReminderNote: Boolean
            get() = reminderChannel != null && reminderChannel != ReminderChannel.Push
    }

    /** Status could not be loaded. Error card message by [reason], Retry → Loading. */
    data class Error(val reason: TelegramErrorReason) : TelegramScreenState
}

/** Which user-facing error text applies: network → `telegram_error_network`, etc. */
enum class TelegramErrorReason {
    /** `telegram_error_network` */
    Network,

    /** `telegram_error_server` */
    Server,

    /** `telegram_error_account` (409 `no_account`) */
    Account,
}

/** User actions the stateless screen reports. The Disconnect dialog itself is UI-local. */
sealed interface TelegramAction {
    data object SignInClick : TelegramAction
    data object ConnectClick : TelegramAction
    data object OpenTelegramClick : TelegramAction
    data object CheckAgainClick : TelegramAction
    data object OpenBotClick : TelegramAction

    /** Confirm in the Disconnect dialog (the screen closes the dialog itself). */
    data object DisconnectConfirm : TelegramAction
    data object RetryClick : TelegramAction

    /** Android/iOS reminder row tapped: the row's own value. */
    data class ReminderChannelClick(val target: ReminderChannel) : TelegramAction

    /**
     * Web reminder row tapped: [on] = "Send to Telegram", false = "Don't send to Telegram". The VM
     * resolves the stored value ([webReminderTarget]); the UI never picks `both` / `push` itself.
     */
    data class WebReminderClick(val on: Boolean) : TelegramAction
}

/**
 * The value a reminder row should show as selected: the in-flight target while saving, else the
 * confirmed value. Null = section hidden. While [TelegramScreenState.Linked.channelSaving] is set,
 * the UI shows the spinner on that row and no selected radio (addendum §4 "Saving").
 */
val TelegramScreenState.Linked.displayedReminderChannel: ReminderChannel?
    get() = channelSaving ?: reminderChannel

/** Web: row 1 "Send to Telegram" is selected for `telegram` and `both`, row 2 for `push`. */
fun ReminderChannel.isWebOn(): Boolean = this != ReminderChannel.Push

/**
 * Web mapping (addendum §2.1): what a tap on a web row writes, given the [current] displayed value.
 * - on + `push` → `both` (never `telegram`: the channel is per user and `telegram` would silently
 *   mute the user's phone from a screen that does not mention phones);
 * - on + `telegram`/`both` → null (row already selected, no request);
 * - off + `telegram`/`both` → `push`; off + `push` → null (already selected).
 */
fun webReminderTarget(current: ReminderChannel, on: Boolean): ReminderChannel? = when {
    on && current == ReminderChannel.Push -> ReminderChannel.Both
    !on && current != ReminderChannel.Push -> ReminderChannel.Push
    else -> null
}

/** Why an external URL is being opened — the VM reacts to the outcome differently per purpose. */
enum class TelegramUrlPurpose {
    /** Connect from NotLinked: success → Pending, failure → stay NotLinked + snackbar. */
    Connect,

    /** "Open Telegram" from Pending: success → restart the status poll, failure → snackbar. */
    Reopen,

    /** "Open bot" from Linked (constant bot URL, no token). Failure → snackbar. */
    Bot,
}

sealed interface TelegramIntent : Intent {
    /** The screen entered composition (every push of the route). Re-entry starts with Loading. */
    data object ScreenEntered : TelegramIntent

    /** Lifecycle ON_RESUME: the screen is visible — refresh, run the poll / token refresh. */
    data object ScreenResumed : TelegramIntent

    /** Lifecycle ON_PAUSE or leaving composition: stop polling and token refresh. */
    data object ScreenPaused : TelegramIntent

    /**
     * Connect tapped. [linkOpenedByHost] = the host already opened [TelegramViewModel.readyDeepLink]
     * synchronously inside the tap (web popup blocker) and will report [UrlOpened]; false = no
     * ready link, the VM creates a token and emits [TelegramSideEffect.OpenUrl].
     */
    data class ConnectClicked(val linkOpenedByHost: Boolean) : TelegramIntent

    /** "Open Telegram" in Pending. Same [linkOpenedByHost] contract as [ConnectClicked]. */
    data class OpenTelegramClicked(val linkOpenedByHost: Boolean) : TelegramIntent

    /** Outcome of an external open, whoever performed it (host tap handler or [TelegramSideEffect.OpenUrl]). */
    data class UrlOpened(val purpose: TelegramUrlPurpose, val opened: Boolean) : TelegramIntent

    data object CheckAgainClicked : TelegramIntent
    data object DisconnectConfirmed : TelegramIntent
    data object RetryClicked : TelegramIntent

    /**
     * Save [target] as the reminder channel. Also the snackbar "Try again" of
     * [TelegramSnackbar.ReminderSaveFailed] (with its `attempted`). Latest tap wins.
     */
    data class ReminderChannelClicked(val target: ReminderChannel) : TelegramIntent

    /** Web row tapped; resolved through [webReminderTarget]. */
    data class WebReminderChannelClicked(val on: Boolean) : TelegramIntent
}

sealed interface TelegramSideEffect : SideEffect {
    /** Open [url]; report the outcome back with [TelegramIntent.UrlOpened] for [purpose]. */
    data class OpenUrl(val url: String, val purpose: TelegramUrlPurpose) : TelegramSideEffect

    data class ShowSnackbar(val message: TelegramSnackbar) : TelegramSideEffect
}

/**
 * Typed snackbar message; the UI resolves the string resource (`resolveTelegramSnackbar`).
 * Shared by the screen and the app-level `?tg_link` handler.
 */
sealed interface TelegramSnackbar {
    /** Short. `telegram_linked_snackbar` with [username], or `telegram_linked_snackbar_plain` when null. */
    data class Linked(val username: String?) : TelegramSnackbar

    /** Long. `telegram_pending_not_yet` */
    data object NotYet : TelegramSnackbar

    /** Short. `telegram_disconnected_snackbar` */
    data object Disconnected : TelegramSnackbar

    /** Long. `telegram_disconnect_failed` */
    data object DisconnectFailed : TelegramSnackbar

    /** Long. `telegram_open_failed` */
    data object OpenFailed : TelegramSnackbar

    /** Long, action `telegram_retry` (re-runs Connect). Network / server / account text by [reason]. */
    data class ConnectFailed(val reason: TelegramErrorReason) : TelegramSnackbar

    /** Long. A failed status call from a manual action (Check again). Text by [reason]. */
    data class RequestFailed(val reason: TelegramErrorReason) : TelegramSnackbar

    /** Long. `telegram_link_expired` — claim got 410 / 404. */
    data object LinkExpired : TelegramSnackbar

    /** Long. `telegram_link_failed` — claim failed on network / server. */
    data object LinkFailed : TelegramSnackbar

    /**
     * Long, action `telegram_retry`. `telegram_reminders_save_failed` (one text for network, server
     * and 409). The action re-sends [attempted] as [TelegramIntent.ReminderChannelClicked].
     */
    data class ReminderSaveFailed(val attempted: ReminderChannel) : TelegramSnackbar
}
