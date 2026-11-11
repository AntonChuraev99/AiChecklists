package com.antonchuraev.homesearchchecklist.core.voice.impl

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceNetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn

/**
 * [VoiceNetworkMonitor] over `ConnectivityManager.registerDefaultNetworkCallback` (API 24 = minSdk): online = the
 * default network has `NET_CAPABILITY_INTERNET` (not `VALIDATED` — see [isOnline]). Needs `ACCESS_NETWORK_STATE`.
 *
 * One callback for all collectors (shared, Koin `single`): Android caps an app at ~100 outstanding network requests
 * (`TooManyRequestsException` on API 30+), so a cold flow per `first()` in the engine's retry loops is not an option.
 * The callback is unregistered [STOP_TIMEOUT_MS] after the last collector leaves, and the replayed value expires with
 * it, so the next call starts from the current state, not from the last call's.
 */
internal class AndroidVoiceNetworkMonitor(
    context: Context,
    private val logger: AppLogger,
) : VoiceNetworkMonitor {

    private val manager: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val isOnline: Flow<Boolean> = callbackFlow {
        val cm = manager
        if (cm == null) {
            logger.warning(TAG, "ConnectivityManager unavailable: reporting online")
            trySend(true)
            awaitClose()
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(networkCapabilities.isOnline())
            }

            override fun onLost(network: Network) {
                // The default network is gone; a new default (if any) arrives through onCapabilitiesChanged.
                trySend(false)
            }

            override fun onUnavailable() {
                trySend(false)
            }
        }
        trySend(cm.currentlyOnline())
        val registered = runCatching { cm.registerDefaultNetworkCallback(callback) }
            .onFailure { logger.error(TAG, "registerDefaultNetworkCallback failed, reporting online: ${it.message}", it) }
            .isSuccess
        // Without a callback the initial value could stay "offline" forever: the safe side is online.
        if (!registered) trySend(true)
        awaitClose {
            if (registered) {
                runCatching { cm.unregisterNetworkCallback(callback) }
                    .onFailure { logger.error(TAG, "unregisterNetworkCallback failed: ${it.message}", it) }
            }
        }
    }
        .distinctUntilChanged()
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = STOP_TIMEOUT_MS, replayExpirationMillis = 0),
            replay = 1,
        )

    private fun ConnectivityManager.currentlyOnline(): Boolean = runCatching {
        val network = activeNetwork ?: return@runCatching false
        getNetworkCapabilities(network)?.isOnline() == true
    }.onFailure { logger.error(TAG, "active network probe failed, reporting online: ${it.message}", it) }
        .getOrDefault(true)

    // INTERNET only, deliberately not VALIDATED: validation fails behind captive portals, on networks that block
    // connectivitycheck.gstatic.com and on some VPNs, and a false "offline" here refuses the call outright. A false
    // "online" is cheap — the turn fails with Unreachable and the engine's own Reconnecting/retry path takes over.
    private fun NetworkCapabilities.isOnline(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

    private companion object {
        const val TAG = "VoiceNetwork"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
