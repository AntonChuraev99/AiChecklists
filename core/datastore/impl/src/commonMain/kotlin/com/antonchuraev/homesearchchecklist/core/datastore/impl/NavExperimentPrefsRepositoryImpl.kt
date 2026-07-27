package com.antonchuraev.homesearchchecklist.core.datastore.impl

import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.datastore.api.NavExperimentPrefsRepository
import kotlinx.coroutines.flow.first

private const val KEY_NAV_ARM = "nav_exp_arm"

class NavExperimentPrefsRepositoryImpl(
    private val dataStore: AppDatastore,
) : NavExperimentPrefsRepository {

    override suspend fun getNavArm(): String? =
        // Empty string is the "absent" sentinel — DataStore has no tri-state, so an unset key and
        // a stored "" must read identically as "no arm assigned yet".
        dataStore.observeString(KEY_NAV_ARM, defaultValue = "").first().takeIf { it.isNotEmpty() }

    override suspend fun setNavArm(arm: String) {
        dataStore.saveString(KEY_NAV_ARM, arm)
    }
}
