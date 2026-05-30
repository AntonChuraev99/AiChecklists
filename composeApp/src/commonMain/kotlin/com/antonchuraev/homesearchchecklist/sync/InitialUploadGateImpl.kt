package com.antonchuraev.homesearchchecklist.sync

import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.InitialUploadGate
import kotlinx.coroutines.flow.first

/**
 * [InitialUploadGate] backed by [AppDatastore] (Preferences DataStore).
 *
 * Stores the set of uids for which the one-time initial upload has completed as a
 * single newline-separated string under [KEY_INITIAL_UPLOAD_DONE_UIDS]. Firebase
 * uids are alphanumeric and never contain a newline, so the separator is safe.
 *
 * Lives in :composeApp (not :feature:checklist) so that the feature module stays
 * free of any DataStore dependency — same boundary as [FirestoreSyncDataSource],
 * whose interface is in the feature and whose implementation is here.
 *
 * Never throws: a read failure yields an empty set (treated as "not done"); a write
 * failure is logged-free no-op via DataStore's own error handling. The worst case is
 * one extra reconciled pull, never data loss.
 */
class InitialUploadGateImpl(
    private val dataStore: AppDatastore,
) : InitialUploadGate {

    private companion object {
        const val KEY_INITIAL_UPLOAD_DONE_UIDS = "initial_upload_done_uids"
        const val SEPARATOR = "\n"
    }

    override suspend fun isInitialUploadDone(uid: String): Boolean =
        readDoneUids().contains(uid)

    override suspend fun markInitialUploadDone(uid: String) {
        val current = readDoneUids()
        if (current.contains(uid)) return
        val updated = current + uid
        dataStore.saveString(
            KEY_INITIAL_UPLOAD_DONE_UIDS,
            updated.joinToString(SEPARATOR),
        )
    }

    private suspend fun readDoneUids(): Set<String> =
        dataStore.observeString(KEY_INITIAL_UPLOAD_DONE_UIDS, defaultValue = "")
            .first()
            .split(SEPARATOR)
            .filter { it.isNotBlank() }
            .toSet()
}
