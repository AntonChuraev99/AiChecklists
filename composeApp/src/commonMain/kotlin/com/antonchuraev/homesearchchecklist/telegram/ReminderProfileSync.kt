package com.antonchuraev.homesearchchecklist.telegram

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderChannel
import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderDeliveryPrefsRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.UserDocMergeUnsupported
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountError
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountResult
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Who the app is signed in as, for reminder-delivery purposes. */
sealed interface ReminderIdentity {
    /** Auth is still restoring — do nothing yet (neither write nor reset anything). */
    data object Unknown : ReminderIdentity

    /** No Google account linked in the app. */
    data object SignedOut : ReminderIdentity

    /** Google-signed-in and linked; [googleUid] = Firebase uid = the `users/{google_uid}` doc id. */
    data class SignedIn(val googleUid: String) : ReminderIdentity
}

internal fun reminderIdentity(authState: GoogleAuthState, isGoogleLinked: Boolean): ReminderIdentity =
    when {
        authState is GoogleAuthState.Authenticated && isGoogleLinked && authState.user.firebaseUid.isNotBlank() ->
            ReminderIdentity.SignedIn(authState.user.firebaseUid)
        authState is GoogleAuthState.Loading -> ReminderIdentity.Unknown
        // Authenticated but not linked yet: link_google_account is running or failed — treat as
        // signed out for writes (the server would answer 409 and the doc may not be ours yet).
        else -> ReminderIdentity.SignedOut
    }

internal fun reminderIdentityFlow(
    authRepository: GoogleAuthRepository,
    userDataRepository: UserDataRepository,
): Flow<ReminderIdentity> =
    combine(authRepository.authState, userDataRepository.getUserDataFlow()) { auth, user ->
        reminderIdentity(auth, user.isGoogleLinked)
    }.distinctUntilChanged()

/**
 * App-level upkeep of reminder delivery (Telegram bot phase 4), run on every app foreground
 * ([onForeground], wired to ON_START in App.kt — which also covers the cold start) and whenever the
 * signed-in identity settles:
 *
 * 1. **Device zone → `users/{google_uid}`** (`timezone` + `timezone_source: "device"`, MERGE write)
 *    only when the zone or the account differs from the last successful write (cached in
 *    [ReminderDeliveryPrefsRepository.getLastWrittenTimezoneKey]). The server computes repeat
 *    occurrences in this zone.
 * 2. **Channel cache refresh** — `telegram_account status` at most once per [CHANNEL_REFRESH_MIN_MS]
 *    per process; the repository writes the cache Android's `ReminderReceiver` reads. A cold start
 *    always refreshes (the throttle is in memory), which is when a change made in the bot is most
 *    likely to be stale here.
 *
 * Signed out → the cache is reset to Push: the phone must never stay silent for a device that can
 * no longer learn the channel (a double reminder beats a missed one).
 */
class ReminderProfileSync(
    private val appScope: CoroutineScope,
    private val identity: Flow<ReminderIdentity>,
    private val reminderPrefs: ReminderDeliveryPrefsRepository,
    private val deviceTimeZone: DeviceTimeZoneProvider,
    private val writeUserDoc: suspend (googleUid: String, fields: Map<String, String>) -> AppResult<Unit>,
    private val refreshChannel: suspend () -> TelegramAccountResult<*>,
    private val logger: AppLogger,
    private val nowMs: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) {
    private val foregroundTicks = MutableStateFlow(0L)
    private var started = false

    private var lastRefreshUid: String? = null
    private var lastRefreshAtMs = Long.MIN_VALUE / 2
    private var mergeUnsupportedLogged = false

    /** Idempotent. Call once from the app root. */
    fun start() {
        if (started) return
        started = true
        appScope.launch {
            combine(identity, foregroundTicks) { id, _ -> id }
                .collectLatest { runOnce(it) }
        }
    }

    /** App came to the foreground (ON_START). */
    fun onForeground() {
        foregroundTicks.value = foregroundTicks.value + 1
    }

    internal suspend fun runOnce(id: ReminderIdentity) {
        when (id) {
            ReminderIdentity.Unknown -> Unit
            ReminderIdentity.SignedOut -> {
                lastRefreshUid = null
                safely("reset channel cache") { reminderPrefs.setReminderChannel(ReminderChannel.Push) }
            }
            is ReminderIdentity.SignedIn -> {
                syncTimezone(id.googleUid)
                refreshChannelIfStale(id.googleUid)
            }
        }
    }

    private suspend fun syncTimezone(googleUid: String) {
        val zone = deviceTimeZone.ianaZoneId() ?: return // platform cannot name an IANA zone (see provider)
        val key = "$googleUid|$zone"
        val written = safely("read written zone") { reminderPrefs.getLastWrittenTimezoneKey() }
        if (written == key) return
        val fields = mapOf(FIELD_TIMEZONE to zone, FIELD_TIMEZONE_SOURCE to TIMEZONE_SOURCE_DEVICE)
        when (val result = writeUserDoc(googleUid, fields)) {
            is AppResult.Success -> safely("save written zone") { reminderPrefs.setLastWrittenTimezoneKey(key) }
            is AppResult.Error -> if (result.exception is UserDocMergeUnsupported) {
                if (!mergeUnsupportedLogged) {
                    mergeUnsupportedLogged = true
                    logger.warning(TAG, "device zone not written: no merge write on this platform")
                }
            } else {
                // Retried on the next foreground (the cache key is not saved).
                logger.error(TAG, "device zone write failed: ${result.exception.message}", result.exception)
            }
            AppResult.Loading -> Unit
        }
    }

    private suspend fun refreshChannelIfStale(googleUid: String) {
        val now = nowMs()
        if (lastRefreshUid == googleUid && now - lastRefreshAtMs < CHANNEL_REFRESH_MIN_MS) return
        lastRefreshUid = googleUid
        lastRefreshAtMs = now
        // The repository logs failures and writes the cache on success; nothing else to do here.
        val result = refreshChannel()
        if (result is TelegramAccountResult.Failure && result.error == TelegramAccountError.NotSignedIn) {
            lastRefreshUid = null // no token yet — try again on the next tick instead of in 15 min
        }
    }

    private suspend fun <T> safely(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        logger.error(TAG, "$what failed: ${e.message}", e)
        null
    }

    companion object {
        private const val TAG = "ReminderProfileSync"
        const val CHANNEL_REFRESH_MIN_MS = 15 * 60_000L
        const val FIELD_TIMEZONE = "timezone"
        const val FIELD_TIMEZONE_SOURCE = "timezone_source"
        const val TIMEZONE_SOURCE_DEVICE = "device"
    }
}
