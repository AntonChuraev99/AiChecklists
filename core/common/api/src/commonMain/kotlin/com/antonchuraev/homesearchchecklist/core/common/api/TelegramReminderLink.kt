package com.antonchuraev.homesearchchecklist.core.common.api

import kotlinx.coroutines.flow.Flow

/**
 * Where the user's reminders are delivered (Telegram bot phase 4). Per USER, not per device: the
 * server stores it in `users/{google_uid}.reminder_channel`, the bot and the app change it.
 *
 * [wire] is the server / Firestore value and the persisted format.
 *
 * Lives in `core:common:api` so every feature module can name it; `core:datastore:api` keeps a
 * `typealias ReminderChannel` to this class for its existing callers.
 */
enum class ReminderChannel(val wire: String) {
    /** Phone notification only — the default, and what an absent value means. */
    Push("push"),

    /** Telegram message only: the phone posts NO notification (bookkeeping still runs). */
    Telegram("telegram"),

    /** Phone notification and Telegram message. */
    Both("both"),
    ;

    companion object {
        /** Null for an absent or unknown value — callers decide what "unknown" means for them. */
        fun fromWire(value: String?): ReminderChannel? = entries.firstOrNull { it.wire == value }
    }
}

/** Web: the "Send to Telegram" row is selected for `telegram` and `both`, the "Don't send" row for `push`. */
fun ReminderChannel.isWebOn(): Boolean = this != ReminderChannel.Push

/**
 * Web mapping (Telegram screen reminders addendum §2.1): what a tap on a web row writes, given the
 * [current] displayed value. Shared by the Telegram screen and the reminder sheet.
 * - on + `push` → `both` (never `telegram`: the channel is per user and `telegram` would silently
 *   mute the user's phone from a surface that does not mention phones);
 * - on + `telegram`/`both` → null (row already selected, no request);
 * - off + `telegram`/`both` → `push`; off + `push` → null (already selected).
 */
fun webReminderTarget(current: ReminderChannel, on: Boolean): ReminderChannel? = when {
    on && current == ReminderChannel.Push -> ReminderChannel.Both
    !on && current != ReminderChannel.Push -> ReminderChannel.Push
    else -> null
}

/** What this device knows about the Telegram-bot link of the current user. */
sealed interface TelegramLinkKnowledge {
    /**
     * Signed in, but the link state of THIS account was never fetched (or every fetch failed and
     * nothing is cached), or sign-in is still running. Never advertise Telegram in this state.
     */
    data object Unknown : TelegramLinkKnowledge

    /** No Google account in the app (guest) — known locally, no network call needed. */
    data object SignedOut : TelegramLinkKnowledge

    data object NotLinked : TelegramLinkKnowledge

    /** [reminderChannel] = the last channel the server confirmed; null = the server does not report one. */
    data class Linked(val reminderChannel: ReminderChannel?) : TelegramLinkKnowledge
}

/** Why a reminder-channel save failed — maps 1:1 to the user-facing / analytics reason. */
enum class TelegramFailureReason {
    /** No HTTP response: offline, DNS, timeout. */
    Network,

    /** 5xx, an unexpected status, a rejected token, not signed in. */
    Server,

    /** 409 `no_account`: the Google account has no account doc on the server. */
    Account,
}

sealed interface ReminderChannelSaveResult {
    /** The server stored [channel] (may differ from the request only if the server says so). */
    data class Saved(val channel: ReminderChannel) : ReminderChannelSaveResult

    data class Failed(val reason: TelegramFailureReason) : ReminderChannelSaveResult
}

/**
 * The Telegram link as seen by feature modules (reminder sheet, Inbox promo). Implemented in
 * `composeApp` on top of the Telegram account repository, which is the single writer of the cache
 * behind [telegramLink].
 */
interface TelegramReminderLink {

    /**
     * Persisted (survives a restart) and per account: a cache written for one Google account is
     * never reported for another — that account reads [TelegramLinkKnowledge.Unknown] until its own
     * `status` arrives. Distinct values only. Refreshed by the app-foreground `status` call.
     */
    val telegramLink: Flow<TelegramLinkKnowledge>

    /**
     * Stores [channel] on the server (with the device zone) and, on success, in the local caches,
     * so [telegramLink] emits `Linked(channel)`.
     *
     * The request runs in an APPLICATION scope: cancelling the caller (the sheet closed, the host
     * ViewModel cleared) does not cancel the write, it only stops waiting for the answer. Calls are
     * sent to the server one at a time in call order, so the last call's value is the one the
     * server ends up with. No retry, no dedupe: "same value → no request" and "latest tap wins"
     * belong to the caller's controller.
     */
    suspend fun setReminderChannel(channel: ReminderChannel): ReminderChannelSaveResult
}
