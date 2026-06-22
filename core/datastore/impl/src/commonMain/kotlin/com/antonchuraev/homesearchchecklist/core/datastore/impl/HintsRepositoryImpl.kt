package com.antonchuraev.homesearchchecklist.core.datastore.impl

import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.datastore.api.HintsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

private const val KEY_HAMBURGER_HINT_SHOWN = "hamburger_hint_shown"
private const val KEY_SYNC_BANNER_DISMISS_COUNT = "sync_banner_dismiss_count"

class HintsRepositoryImpl(
    private val dataStore: AppDatastore,
) : HintsRepository {

    override val hamburgerHintShown: Flow<Boolean> =
        dataStore.observeBoolean(KEY_HAMBURGER_HINT_SHOWN, defaultValue = false)

    override suspend fun markHamburgerHintShown() {
        dataStore.saveBoolean(KEY_HAMBURGER_HINT_SHOWN, true)
    }

    override val syncBannerDismissCount: Flow<Int> =
        dataStore.observeInt(KEY_SYNC_BANNER_DISMISS_COUNT, defaultValue = 0)

    override suspend fun incrementSyncBannerDismissCount() {
        // Read-modify-write is safe here: dismissing removes the banner from the UI immediately
        // (the in-memory session flag), so a second tap on the same banner cannot race this.
        val current = dataStore.observeInt(KEY_SYNC_BANNER_DISMISS_COUNT, defaultValue = 0).first()
        dataStore.saveInt(KEY_SYNC_BANNER_DISMISS_COUNT, current + 1)
    }
}
