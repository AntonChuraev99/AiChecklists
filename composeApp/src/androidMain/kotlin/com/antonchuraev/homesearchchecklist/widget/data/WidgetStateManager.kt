package com.antonchuraev.homesearchchecklist.widget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

/**
 * Manages widget configuration state using DataStore.
 * Stores the selected checklist ID for each widget instance (by appWidgetId).
 */
@Singleton
class WidgetStateManager(private val context: Context) {

    private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "widget_config"
    )

    /**
     * Get the selected checklist ID for a specific widget.
     */
    suspend fun getSelectedChecklistId(appWidgetId: Int): Long? {
        val key = longPreferencesKey(keyForWidget(appWidgetId))
        return context.widgetDataStore.data.first()[key]
    }

    /**
     * Observe the selected checklist ID for a specific widget.
     * Returns Flow that emits new value when selection changes.
     */
    fun observeSelectedChecklistId(appWidgetId: Int): Flow<Long?> {
        val key = longPreferencesKey(keyForWidget(appWidgetId))
        return context.widgetDataStore.data.map { prefs -> prefs[key] }
    }

    /**
     * Save the selected checklist ID for a specific widget.
     */
    suspend fun setSelectedChecklistId(appWidgetId: Int, checklistId: Long) {
        val key = longPreferencesKey(keyForWidget(appWidgetId))
        context.widgetDataStore.edit { prefs ->
            prefs[key] = checklistId
        }
    }

    /**
     * Clear the configuration for a specific widget (e.g., when widget is removed).
     */
    suspend fun clearWidget(appWidgetId: Int) {
        val key = longPreferencesKey(keyForWidget(appWidgetId))
        context.widgetDataStore.edit { prefs ->
            prefs.remove(key)
        }
    }

    private fun keyForWidget(appWidgetId: Int): String {
        return "widget_${appWidgetId}_checklist_id"
    }
}
