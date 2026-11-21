package com.antonchuraev.homesearchchecklist.telegram

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderChannel
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountApiService
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountError
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountResult
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramLinkStatus
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramLinkToken
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.CancellationException

/**
 * Telegram-bot account link of the signed-in Google account (CF `telegram_account`).
 *
 * Every failure comes back typed ([TelegramAccountError]) and is logged here exactly once, with
 * its cause — callers only decide what the user sees.
 */
interface TelegramLinkRepository {
    suspend fun status(): TelegramAccountResult<TelegramLinkStatus>

    suspend fun createToken(): TelegramAccountResult<TelegramLinkToken>

    suspend fun claim(token: String): TelegramAccountResult<TelegramLinkStatus.Linked>

    suspend fun unlink(): TelegramAccountResult<Unit>

    /**
     * Stores the reminder channel on the server together with the device IANA zone. Success = the
     * channel the server confirmed (also written to the local cache for `ReminderReceiver`).
     */
    suspend fun setReminderChannel(channel: ReminderChannel): TelegramAccountResult<ReminderChannel>
}

/**
 * Besides the HTTP calls, this is the single writer of the device's Telegram caches
 * ([TelegramLinkCacheWriter]: the reminder channel for `ReminderReceiver`, the per-account link
 * snapshot behind `TelegramReminderLink.telegramLink`, the promo `retired` flag). Every successful
 * `status`, `claim`, `unlink` and `set_reminder_channel` updates them, whoever made the call (the
 * Telegram screen, the `?tg_link` claim handler, the app-foreground refresh, the reminder sheet).
 * A failed call writes nothing: the cache keeps the last known state (or stays empty → Unknown).
 */
internal class TelegramLinkRepositoryImpl(
    private val api: TelegramAccountApiService,
    private val authRepository: GoogleAuthRepository,
    private val userDataRepository: UserDataRepository,
    private val cacheWriter: TelegramLinkCacheWriter,
    private val deviceTimeZone: DeviceTimeZoneProvider,
    private val logger: AppLogger,
) : TelegramLinkRepository {

    override suspend fun status(): TelegramAccountResult<TelegramLinkStatus> {
        val account = currentAccountId()
        return withToken("status") { api.status(it) }
            .also { if (it is TelegramAccountResult.Success) cacheWriter.onStatus(account, it.value) }
    }

    override suspend fun createToken() = withToken("create_token") { api.createToken(it, creditUserId()) }

    override suspend fun claim(token: String): TelegramAccountResult<TelegramLinkStatus.Linked> {
        val account = currentAccountId()
        return withToken("claim") { api.claim(it, token, creditUserId()) }
            .also { if (it is TelegramAccountResult.Success) cacheWriter.onClaimed(account, it.value) }
    }

    override suspend fun unlink(): TelegramAccountResult<Unit> {
        val account = currentAccountId()
        return withToken("unlink") { api.unlink(it) }
            .also { if (it is TelegramAccountResult.Success) cacheWriter.onUnlinked(account) }
    }

    override suspend fun setReminderChannel(channel: ReminderChannel): TelegramAccountResult<ReminderChannel> {
        val account = currentAccountId()
        val zone = deviceTimeZone.ianaZoneId()
        var result = withToken("set_reminder_channel") { api.setReminderChannel(it, channel, zone) }
        val failure = (result as? TelegramAccountResult.Failure)?.error
        if (zone != null && failure is TelegramAccountError.Server &&
            failure.code == TelegramAccountApiService.ERROR_INVALID_TIMEZONE
        ) {
            // The channel is what the user asked for; the zone is our own addition. Do not fail the
            // tap over a zone the server's database does not know — store the channel without it.
            logger.warning(TAG, "set_reminder_channel: server rejected zone '$zone', retrying without it")
            result = withToken("set_reminder_channel") { api.setReminderChannel(it, channel, null) }
        }
        if (result is TelegramAccountResult.Success) cacheWriter.onChannelSaved(account, result.value)
        return result
    }

    /** Firebase uid of the account a request is made for — captured before the request. */
    private fun currentAccountId(): String? = authRepository.currentUser?.firebaseUid?.takeIf { it.isNotBlank() }

    /** The credit-doc id (USER_ID_KEY); the server prefers it among the docs carrying this google_uid. */
    private suspend fun creditUserId(): String? =
        runCatching { userDataRepository.getUserData().userId }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    private suspend fun <T> withToken(
        action: String,
        block: suspend (idToken: String) -> TelegramAccountResult<T>,
    ): TelegramAccountResult<T> {
        val idToken = try {
            authRepository.getIdToken()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error(TAG, "$action: getIdToken threw: ${e.message}", e)
            null
        }
        val result = if (idToken.isNullOrBlank()) {
            TelegramAccountResult.Failure(TelegramAccountError.NotSignedIn)
        } else {
            block(idToken)
        }
        if (result is TelegramAccountResult.Failure) log(action, result.error)
        return result
    }

    private fun log(action: String, error: TelegramAccountError) {
        when (error) {
            // Expected outcomes of a stale bot link — not defects, but still traceable.
            TelegramAccountError.TokenExpired, TelegramAccountError.TokenUnknown ->
                logger.warning(TAG, "$action: link token rejected ($error)")
            TelegramAccountError.NotSignedIn ->
                logger.warning(TAG, "$action: no Firebase ID token, request not sent")
            is TelegramAccountError.Network ->
                logger.error(TAG, "$action: network failure: ${error.cause.message}", error.cause)
            else -> logger.error(
                TAG,
                "$action: failed ($error)",
                TelegramAccountException("$action: $error"),
            )
        }
    }

    private companion object {
        const val TAG = "Telegram"
    }
}

/** Carrier so a non-network failure still reaches Crashlytics `recordException`. */
internal class TelegramAccountException(message: String) : Exception(message)
