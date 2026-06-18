package com.antonchuraev.homesearchchecklist.core.datastore.api

/**
 * Persisted, per-UID state for the new-user activation bundle (RC flag `activation_bundle_v1`).
 *
 * Two independent one-time flags:
 *  - **new-user-pending** — set once at splash for a brand-new registration. It marks the
 *    window during which the user's *first* AI checklist should trigger the activation funnel
 *    (the [com.antonchuraev.homesearchchecklist.core.common.api] FIRST_AI_CHECKLIST_CREATED event
 *    + the reminder opt-in). It is consumed (cleared) the first time that checklist is created, so
 *    later checklists never re-trigger it. We persist this because `isNewUser` is transient —
 *    it is only returned by `ensureUserRegistered()` at splash, never stored on `UserData`.
 *  - **reminder-opt-in-shown** — the show-once guard for the contextual reminder soft-ask, so it
 *    appears at most once per user even across process death / multiple AI checklists.
 *
 * Keyed per-uid: linking a Google account can swap the active user on the same device, and each
 * user should pass through activation at most once. Mirrors [FirstChecklistRepository].
 */
interface ActivationPrefsRepository {
    /** Returns `true` while this UID is still inside the first-AI-checklist activation window. */
    suspend fun isNewUserPending(uid: String): Boolean

    /** Marks [uid] as a brand-new user awaiting their first AI checklist. Idempotent. */
    suspend fun setNewUserPending(uid: String)

    /** Clears the pending flag for [uid] — called once the first AI checklist has been created. */
    suspend fun clearNewUserPending(uid: String)

    /** Returns `true` once the reminder opt-in soft-ask has already been shown to [uid]. */
    suspend fun isReminderOptInShown(uid: String): Boolean

    /** Marks the reminder opt-in soft-ask as shown for [uid]. Idempotent. */
    suspend fun markReminderOptInShown(uid: String)
}
