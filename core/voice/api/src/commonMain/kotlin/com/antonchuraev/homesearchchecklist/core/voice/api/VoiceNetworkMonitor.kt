package com.antonchuraev.homesearchchecklist.core.voice.api

import kotlinx.coroutines.flow.Flow

/**
 * Whether the device can reach the internet right now, for the voice call: `false` before the call makes it
 * `Unavailable(Offline)`, `false` during it starts `Reconnecting`. Process-wide (Koin `single`).
 *
 * [isOnline] contract:
 * - **Hot and shared.** All collectors share one platform registration (Android: one default-network callback), so
 *   collecting it many times — `first()`, `first { it }` in retry loops, a long `collect` — costs nothing extra. The
 *   registration lives while someone collects, plus a short grace period, then is released.
 * - **Starts with the current state.** Every new collector gets a value immediately (the state at the moment of
 *   collection, not a stale one from an earlier call), then the changes.
 * - **Deduplicated.** Consecutive equal values are not repeated: each emission is a real change.
 * - **Never fails.** If the platform cannot be asked, it reports `true` (online) and logs: the turn's own network
 *   errors still drive retries, a wrong "offline" would block the call outright.
 *
 * Android: default network with `NET_CAPABILITY_INTERNET` (validation is not required). Web and iOS: always `true`.
 */
interface VoiceNetworkMonitor {
    val isOnline: Flow<Boolean>
}
