package com.antonchuraev.homesearchchecklist.telegram

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderChannel
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountResult
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramLinkStatus
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramLinkToken
import kotlinx.coroutines.CompletableDeferred

internal class FakeTelegramLinkRepository : TelegramLinkRepository {
    /** Next replies of `status`, consumed in order; [defaultStatus] once empty. */
    val statusQueue = ArrayDeque<TelegramAccountResult<TelegramLinkStatus>>()
    var defaultStatus: TelegramAccountResult<TelegramLinkStatus> = TelegramAccountResult.Success(TelegramLinkStatus.NotLinked)
    var statusCalls = 0

    var tokenResult: (() -> TelegramAccountResult<TelegramLinkToken>)? = null
    var createTokenCalls = 0

    var claimResult: TelegramAccountResult<TelegramLinkStatus.Linked> =
        TelegramAccountResult.Success(TelegramLinkStatus.Linked("anton", "Anton", null))
    val claimedTokens = mutableListOf<String>()

    var unlinkGate: CompletableDeferred<TelegramAccountResult<Unit>>? = null
    var unlinkResult: TelegramAccountResult<Unit> = TelegramAccountResult.Success(Unit)
    var unlinkCalls = 0

    override suspend fun status(): TelegramAccountResult<TelegramLinkStatus> {
        statusCalls++
        return statusQueue.removeFirstOrNull() ?: defaultStatus
    }

    override suspend fun createToken(): TelegramAccountResult<TelegramLinkToken> {
        createTokenCalls++
        return tokenResult?.invoke()
            ?: TelegramAccountResult.Success(
                TelegramLinkToken(
                    token = "t$createTokenCalls",
                    deepLink = "https://t.me/gisti_app_bot?start=t$createTokenCalls",
                    expiresInSec = 600,
                ),
            )
    }

    override suspend fun claim(token: String): TelegramAccountResult<TelegramLinkStatus.Linked> {
        claimedTokens += token
        return claimResult
    }

    override suspend fun unlink(): TelegramAccountResult<Unit> {
        unlinkCalls++
        return unlinkGate?.await() ?: unlinkResult
    }

    /** Requests in the order sent. */
    val channelRequests = mutableListOf<ReminderChannel>()

    /** When set, each `setReminderChannel` call suspends on the next gate (consumed in order). */
    val channelGates = ArrayDeque<CompletableDeferred<TelegramAccountResult<ReminderChannel>>>()

    /** Reply when no gate is queued: echoes the request. */
    var channelResult: ((ReminderChannel) -> TelegramAccountResult<ReminderChannel>) =
        { TelegramAccountResult.Success(it) }

    override suspend fun setReminderChannel(channel: ReminderChannel): TelegramAccountResult<ReminderChannel> {
        channelRequests += channel
        val gate = channelGates.removeFirstOrNull()
        return gate?.await() ?: channelResult(channel)
    }
}

internal class RecordingTelegramAnalytics : AnalyticsTracker {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    override fun setUserId(userId: String) = Unit
    override fun setUserProperties(properties: Map<String, Any>) = Unit
    override fun screenView(name: String) = Unit
    override fun event(name: String, params: Map<String, Any>) {
        events += name to params
    }

    fun count(name: String) = events.count { it.first == name }
    fun params(name: String) = events.filter { it.first == name }.map { it.second }
}
