package com.antonchuraev.homesearchchecklist.core.datastore.impl

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxDisplayOptions
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxDisplayPrefsRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxLayout
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine

private const val TAG = "InboxDisplayPrefs"

private const val KEY_LAYOUT = "inbox_display_layout"
private const val KEY_SORT = "inbox_display_sort"
private const val KEY_SHOW_COMPLETED = "inbox_display_show_completed"

/**
 * Enum names are the wire format, resolved back with a `firstOrNull { it.name == stored }`.
 *
 * `valueOf` is avoided on purpose: it THROWS on an unknown name, and this store outlives the code
 * that wrote it — a mode renamed or dropped in a later version would then crash the Inbox on launch
 * for anyone who had selected it. An unrecognised value degrades to the default instead.
 */
class InboxDisplayPrefsRepositoryImpl(
    private val dataStore: AppDatastore,
    private val logger: AppLogger,
) : InboxDisplayPrefsRepository {

    private val defaults = InboxDisplayOptions()

    override fun observeDisplayOptions(): Flow<InboxDisplayOptions> = combine(
        dataStore.observeString(KEY_LAYOUT, defaultValue = defaults.layout.name),
        dataStore.observeString(KEY_SORT, defaultValue = defaults.sort.name),
        dataStore.observeBoolean(KEY_SHOW_COMPLETED, defaultValue = defaults.showCompleted),
    ) { layout, sort, showCompleted ->
        InboxDisplayOptions(
            layout = InboxLayout.entries.firstOrNull { it.name == layout } ?: defaults.layout,
            sort = InboxSort.entries.firstOrNull { it.name == sort } ?: defaults.sort,
            showCompleted = showCompleted,
        )
    }.catch { e ->
        // The enum guard above only covers a value we could READ. DataStore's own `data` flow
        // throws on a corrupt or unreadable preferences file, and nothing downstream catches it:
        // this is the first fallible source in InboxViewModel.screenState's combine, so the throw
        // would take the sharing scope with it and leave the Inbox spinning forever with no log.
        logger.error(TAG, "display options unreadable — falling back to defaults: ${e.message}", e)
        emit(defaults)
    }

    override suspend fun setLayout(layout: InboxLayout) {
        dataStore.saveString(KEY_LAYOUT, layout.name)
    }

    override suspend fun setSort(sort: InboxSort) {
        dataStore.saveString(KEY_SORT, sort.name)
    }

    override suspend fun setShowCompleted(show: Boolean) {
        dataStore.saveBoolean(KEY_SHOW_COMPLETED, show)
    }
}
