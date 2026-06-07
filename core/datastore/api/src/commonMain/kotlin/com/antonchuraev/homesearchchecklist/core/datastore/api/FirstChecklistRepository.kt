package com.antonchuraev.homesearchchecklist.core.datastore.api

/**
 * Tracks whether the one-time "Your first checklist" auto-seed has already run for a
 * given user, so the A/B `auto_create` treatment never re-creates the starter checklist
 * (e.g. if the user deletes it).
 *
 * Keyed per-uid: linking a Google account can swap the active user on the same device,
 * and each user should get the starter checklist at most once.
 */
interface FirstChecklistRepository {
    /**
     * Returns `true` once the starter checklist has been seeded for [uid].
     * Defaults to `false` for new users. Never throws.
     */
    suspend fun isFirstChecklistCreated(uid: String): Boolean

    /** Marks the starter checklist as seeded for [uid]. Idempotent. */
    suspend fun markFirstChecklistCreated(uid: String)
}
