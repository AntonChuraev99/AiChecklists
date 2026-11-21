package com.antonchuraev.homesearchchecklist.telegram

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.ReminderChannel
import com.antonchuraev.homesearchchecklist.core.common.api.ReminderChannelSaveResult
import com.antonchuraev.homesearchchecklist.core.common.api.TelegramFailureReason
import com.antonchuraev.homesearchchecklist.core.common.api.TelegramLinkKnowledge
import com.antonchuraev.homesearchchecklist.core.common.api.TelegramReminderLink
import com.antonchuraev.homesearchchecklist.core.datastore.api.HintsRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderDeliveryPrefsRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.TelegramLinkCacheRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.TelegramLinkSnapshot
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountError
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountResult
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramLinkStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * All device-side caches a Telegram call result feeds, written in one place:
 * - the reminder channel `ReminderReceiver` reads ([ReminderDeliveryPrefsRepository], Push default);
 * - the per-account link snapshot the feature surfaces read ([TelegramLinkCacheRepository]);
 * - the promo `retired` flag ([HintsRepository]) — set on every observed link, so a link made while
 *   Inbox is not composed (bot deep link claim) still retires the banner.
 *
 * Called only by [TelegramLinkRepositoryImpl]. [accountId] = the Firebase uid captured BEFORE the
 * request: a result is filed under the account that asked for it, even if the user switched
 * accounts while it was in flight. A null account (signed out mid-request) writes no snapshot.
 *
 * Each write is independent and never throws: the server call already succeeded, a stale cache
 * only lasts until the next `status`.
 */
internal class TelegramLinkCacheWriter(
    private val reminderPrefs: ReminderDeliveryPrefsRepository,
    private val linkCache: TelegramLinkCacheRepository,
    private val hints: HintsRepository,
    private val logger: AppLogger,
) {

    suspend fun onStatus(accountId: String?, status: TelegramLinkStatus) {
        guarded("reminder channel") { reminderPrefs.setReminderChannel(cachedChannelFor(status)) }
        when (status) {
            TelegramLinkStatus.NotLinked -> writeSnapshot(accountId, linked = false, channel = null)
            is TelegramLinkStatus.Linked -> onLinked(accountId, status.reminderChannel)
        }
    }

    /**
     * A claim reply carries no `reminder_channel` on older servers; the channel cache for
     * `ReminderReceiver` is left alone (the next `status` sets it), only the link is recorded.
     */
    suspend fun onClaimed(accountId: String?, linked: TelegramLinkStatus.Linked) {
        onLinked(accountId, linked.reminderChannel)
    }

    suspend fun onUnlinked(accountId: String?) {
        // The server resets the channel to push on unlink; mirror it so the phone notifies again.
        guarded("reminder channel") { reminderPrefs.setReminderChannel(ReminderChannel.Push) }
        writeSnapshot(accountId, linked = false, channel = null)
    }

    /**
     * Updates the snapshot only when it already says "linked" for this account: a channel write
     * on its own is no proof of a link, and must not turn `NotLinked` / `Unknown` into `Linked`.
     */
    suspend fun onChannelSaved(accountId: String?, channel: ReminderChannel) = onChannelChanged(accountId, channel)

    /**
     * The server's data-only FCM `reminder_channel` (bot `/reminders`, `/unlink`, another device).
     * [accountId] = the signed-in uid on this device; null (cold process, session not restored)
     * updates only the receiver channel, the next foreground `status` refreshes the snapshot.
     */
    suspend fun onChannelPushed(accountId: String?, channel: ReminderChannel) = onChannelChanged(accountId, channel)

    private suspend fun onChannelChanged(accountId: String?, channel: ReminderChannel) {
        guarded("reminder channel") { reminderPrefs.setReminderChannel(channel) }
        if (accountId == null) return
        guarded("link snapshot") {
            val current = linkCache.snapshot.first()
            if (current != null && current.accountId == accountId && current.linked) {
                linkCache.write(current.copy(reminderChannel = channel))
            }
        }
    }

    private suspend fun onLinked(accountId: String?, channel: ReminderChannel?) {
        writeSnapshot(accountId, linked = true, channel = channel)
        guarded("promo retired") { hints.markTelegramPromoRetired() }
    }

    private suspend fun writeSnapshot(accountId: String?, linked: Boolean, channel: ReminderChannel?) {
        if (accountId == null) return
        guarded("link snapshot") {
            linkCache.write(TelegramLinkSnapshot(accountId = accountId, linked = linked, reminderChannel = channel))
        }
    }

    private suspend fun guarded(what: String, block: suspend () -> Unit) {
        runCatching { block() }.onFailure { e ->
            if (e is CancellationException) throw e
            logger.error(TAG, "$what cache write failed: ${e.message}", e)
        }
    }

    private companion object {
        const val TAG = "Telegram"
    }
}

/**
 * Guest → SignedOut (local, no network). Signing in → Unknown. Signed in → the snapshot of THIS
 * account, or Unknown when there is none (never fetched, or every fetch failed) or it belongs to
 * another account.
 */
internal fun telegramLinkKnowledge(
    phase: TelegramAuthPhase,
    accountId: String?,
    snapshot: TelegramLinkSnapshot?,
): TelegramLinkKnowledge = when (phase) {
    TelegramAuthPhase.SignedOut -> TelegramLinkKnowledge.SignedOut
    TelegramAuthPhase.SigningIn -> TelegramLinkKnowledge.Unknown
    TelegramAuthPhase.SignedIn -> when {
        snapshot == null || accountId == null || snapshot.accountId != accountId -> TelegramLinkKnowledge.Unknown
        snapshot.linked -> TelegramLinkKnowledge.Linked(snapshot.reminderChannel)
        else -> TelegramLinkKnowledge.NotLinked
    }
}

internal fun TelegramAccountError.toFailureReason(): TelegramFailureReason = when (this) {
    is TelegramAccountError.Network -> TelegramFailureReason.Network
    TelegramAccountError.NoAccount -> TelegramFailureReason.Account
    else -> TelegramFailureReason.Server
}

/**
 * Feature-facing view of the Telegram link (core [TelegramReminderLink]). Reads the cache that
 * [TelegramLinkCacheWriter] writes; never writes it itself.
 *
 * @param appScope application-lifetime scope (SupervisorJob): channel saves run here so they
 *   outlive the sheet and its host ViewModel.
 */
internal class TelegramReminderLinkImpl(
    private val repository: TelegramLinkRepository,
    linkCache: TelegramLinkCacheRepository,
    authPhase: Flow<TelegramAuthPhase>,
    accountId: Flow<String?>,
    private val appScope: CoroutineScope,
) : TelegramReminderLink {

    private val saveLock = Mutex()

    override val telegramLink: Flow<TelegramLinkKnowledge> =
        combine(authPhase, accountId, linkCache.snapshot, ::telegramLinkKnowledge)
            .distinctUntilChanged()

    override suspend fun setReminderChannel(channel: ReminderChannel): ReminderChannelSaveResult {
        // UNDISPATCHED: the lock is requested synchronously in the caller's call order, and the
        // Mutex is fair, so the server receives the writes in that order.
        val write = appScope.async(start = CoroutineStart.UNDISPATCHED) {
            saveLock.withLock { repository.setReminderChannel(channel) }
        }
        return when (val result = write.await()) {
            is TelegramAccountResult.Success -> ReminderChannelSaveResult.Saved(result.value)
            is TelegramAccountResult.Failure -> ReminderChannelSaveResult.Failed(result.error.toFailureReason())
        }
    }
}
