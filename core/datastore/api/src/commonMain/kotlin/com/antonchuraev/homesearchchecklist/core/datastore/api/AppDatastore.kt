package com.antonchuraev.homesearchchecklist.core.datastore.api

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath

expect fun createDataStore(name: String): DataStore<Preferences>

fun createDataStore(producePath: () -> String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = {
            val path = producePath()
            val normalizedPath =
                if (path.endsWith(".preferences_pb")) path else "$path.preferences_pb"
            normalizedPath.toPath()
        }
    )

class AppDatastore(
    name: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val dataStore = createDataStore(name)

    suspend fun saveBoolean(key: String, value: Boolean) {
        withContext(dispatcher) {
            dataStore.edit { prefs ->
                prefs[booleanPreferencesKey(key)] = value
            }
        }
    }

    fun observeBoolean(key: String, defaultValue: Boolean): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[booleanPreferencesKey(key)] ?: defaultValue }

    suspend fun saveString(key: String, value: String) {
        withContext(dispatcher) {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey(key)] = value
            }
        }
    }

    fun observeString(key: String, defaultValue: String): Flow<String> =
        dataStore.data.map { prefs -> prefs[stringPreferencesKey(key)] ?: defaultValue }

    suspend fun clear() {
        withContext(dispatcher) {
            dataStore.edit { prefs ->
                prefs.clear()
            }
        }
    }

}
