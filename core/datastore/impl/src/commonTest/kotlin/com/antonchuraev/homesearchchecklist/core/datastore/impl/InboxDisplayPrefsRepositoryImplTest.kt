package com.antonchuraev.homesearchchecklist.core.datastore.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxDisplayOptions
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxLayout
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The read path's never-fails contract.
 *
 * Worth a test because the failure is invisible in review: this flow is the first fallible source
 * inside `InboxViewModel.screenState`'s combine, which has no `catch` of its own, so a throw here
 * does not surface as a crash on the Inbox — it cancels the sharing scope and leaves the tab on an
 * infinite spinner with nothing in the logs.
 */
class InboxDisplayPrefsRepositoryImplTest {

    @Test
    fun observeDisplayOptions_unreadableStore_emitsDefaultsAndLogsTheCause() = runTest {
        val logger = RecordingLogger()
        val corrupted = IllegalStateException("preferences file is not readable")
        val repository = repository(flow<Preferences> { throw corrupted }, logger)

        assertEquals(InboxDisplayOptions(), repository.observeDisplayOptions().first())

        val logged = assertNotNull(
            logger.errors.singleOrNull(),
            "the fallback must say WHY it fell back — a silent one is undiagnosable",
        )
        // Identity, walked down the cause chain — NOT assertSame on the logged throwable itself.
        // The throw crosses a coroutine boundary (combine runs its sources in a child scope), and on
        // JVM kotlinx stacktrace recovery COPIES a copyable exception there, handing the catch a
        // reconstruction with the original attached as `cause`. assertSame would then pass on
        // wasmJs/native and fail on the host run for a code path that is perfectly correct. What
        // actually matters is unchanged: the real throwable — with its real stack — is reachable
        // from what the logger got, so Crashlytics records the cause and not a bare message.
        assertTrue(
            generateSequence(logged.second) { it.cause }.any { it === corrupted },
            "the throwable itself must reach Crashlytics, not just its message",
        )
    }

    /**
     * The enum guard and the new stream guard must not shadow each other: an unknown LAYOUT falls
     * back on its own while the sort and the completed toggle keep the values the user chose.
     */
    @Test
    fun observeDisplayOptions_unknownStoredLayout_keepsTheOtherStoredValues() = runTest {
        val logger = RecordingLogger()
        // Wire keys repeated as literals on purpose: they are the persisted format, so a rename in
        // the impl must fail here rather than silently reset every user's options on upgrade.
        val stored = mutablePreferencesOf().apply {
            this[stringPreferencesKey("inbox_display_layout")] = "BOARD"
            this[stringPreferencesKey("inbox_display_sort")] = InboxSort.NAME.name
            this[booleanPreferencesKey("inbox_display_show_completed")] = false
        }
        val repository = repository(flowOf(stored), logger)

        val options = repository.observeDisplayOptions().first()

        assertEquals(InboxLayout.CARDS, options.layout, "a dropped layout name degrades to the default")
        assertEquals(InboxSort.NAME, options.sort)
        assertFalse(options.showCompleted)
        assertTrue(logger.errors.isEmpty(), "a readable store must not report a failure")
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun repository(data: Flow<Preferences>, logger: AppLogger) =
        InboxDisplayPrefsRepositoryImpl(dataStore = AppDatastore(FakeDataStore(data)), logger = logger)
}

private class FakeDataStore(private val source: Flow<Preferences>) : DataStore<Preferences> {
    override val data: Flow<Preferences> get() = source

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        error("these tests exercise the read path only")
}

private class RecordingLogger : AppLogger {
    val errors = mutableListOf<Pair<String, Throwable?>>()
    override fun debug(tag: String, message: String) {}
    override fun info(tag: String, message: String) {}
    override fun warning(tag: String, message: String) {}
    override fun error(tag: String, message: String, throwable: Throwable?) {
        errors.add(message to throwable)
    }
}
