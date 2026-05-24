package com.antonchuraev.homesearchchecklist.core.datastore.impl

import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.datastore.api.HintsRepository
import kotlinx.coroutines.flow.Flow

private const val KEY_HAMBURGER_HINT_SHOWN = "hamburger_hint_shown"

class HintsRepositoryImpl(
    private val dataStore: AppDatastore,
) : HintsRepository {

    override val hamburgerHintShown: Flow<Boolean> =
        dataStore.observeBoolean(KEY_HAMBURGER_HINT_SHOWN, defaultValue = false)

    override suspend fun markHamburgerHintShown() {
        dataStore.saveBoolean(KEY_HAMBURGER_HINT_SHOWN, true)
    }
}
