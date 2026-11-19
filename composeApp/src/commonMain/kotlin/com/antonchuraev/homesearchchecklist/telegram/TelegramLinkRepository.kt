package com.antonchuraev.homesearchchecklist.telegram

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
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
}

internal class TelegramLinkRepositoryImpl(
    private val api: TelegramAccountApiService,
    private val authRepository: GoogleAuthRepository,
    private val userDataRepository: UserDataRepository,
    private val logger: AppLogger,
) : TelegramLinkRepository {

    override suspend fun status() = withToken("status") { api.status(it) }

    override suspend fun createToken() = withToken("create_token") { api.createToken(it, creditUserId()) }

    override suspend fun claim(token: String) = withToken("claim") { api.claim(it, token, creditUserId()) }

    override suspend fun unlink() = withToken("unlink") { api.unlink(it) }

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
