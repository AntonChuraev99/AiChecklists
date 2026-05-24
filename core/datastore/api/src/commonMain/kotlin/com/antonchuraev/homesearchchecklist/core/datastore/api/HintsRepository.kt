package com.antonchuraev.homesearchchecklist.core.datastore.api

import kotlinx.coroutines.flow.Flow

/**
 * Repository for persisting one-time UX hint states.
 *
 * Each hint is shown once (on first install or first upgrade that ships it)
 * and never again after the user dismisses or completes it.
 */
interface HintsRepository {
    /**
     * Emits `true` once the hamburger-pulse hint has been shown and
     * acknowledged (user completed 3 animation cycles or opened the drawer).
     * Defaults to `false` for new installs.
     * Never throws.
     */
    val hamburgerHintShown: Flow<Boolean>

    /** Mark the hamburger-pulse hint as permanently shown. Idempotent. */
    suspend fun markHamburgerHintShown()
}
