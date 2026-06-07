package com.antonchuraev.homesearchchecklist.core.datastore.impl

import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.datastore.api.FirstChecklistRepository
import kotlinx.coroutines.flow.first

private const val KEY_PREFIX = "first_checklist_created_"

class FirstChecklistRepositoryImpl(
    private val dataStore: AppDatastore,
) : FirstChecklistRepository {

    override suspend fun isFirstChecklistCreated(uid: String): Boolean =
        dataStore.observeBoolean(key(uid), defaultValue = false).first()

    override suspend fun markFirstChecklistCreated(uid: String) {
        dataStore.saveBoolean(key(uid), true)
    }

    private fun key(uid: String) = "$KEY_PREFIX$uid"
}
