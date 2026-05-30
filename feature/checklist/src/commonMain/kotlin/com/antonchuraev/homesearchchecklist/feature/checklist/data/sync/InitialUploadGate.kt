package com.antonchuraev.homesearchchecklist.feature.checklist.data.sync

/**
 * Guards the one-time "initial upload" step of the sync pipeline.
 *
 * Firestore is the source of truth. Local "guest" checklists (created before the
 * account was linked) must be pushed to the cloud **exactly once** — on the first
 * authentication for a given uid. On every subsequent start the pipeline only
 * pushes pending changes and pulls with reconciliation; it must NOT re-upload the
 * full local set, otherwise checklists deleted on another device would be
 * resurrected (see [SyncRepositoryImpl.onUserAuthenticated]).
 *
 * The "done" flag is tracked per-uid so that signing into a different account on the
 * same device still performs its own one-time upload.
 *
 * Implementations persist the flag (e.g. DataStore). Never throws — a read failure
 * must default to `false` (treat as "not done"); a write failure is swallowed by the
 * implementation. The worst case of a false `false` is one extra reconciled pull, not
 * data loss.
 */
interface InitialUploadGate {

    /** Returns `true` if [markInitialUploadDone] was already called for [uid]. */
    suspend fun isInitialUploadDone(uid: String): Boolean

    /** Marks the initial upload as completed for [uid]. Idempotent. */
    suspend fun markInitialUploadDone(uid: String)
}
