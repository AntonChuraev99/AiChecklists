package com.antonchuraev.homesearchchecklist.core.datastore.api

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.antonchuraev.homesearchchecklist.core.common.api.AppContextHolder


actual fun createDataStore(name: String): DataStore<Preferences> = createDataStore(
    producePath = {
        AppContextHolder.context.filesDir.resolve(name).absolutePath
    }
)