package com.antonchuraev.homesearchchecklist.core.datastore.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.ReminderChannel
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.datastore.api.TelegramLinkSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TelegramLinkCacheRepositoryImplTest {

    @Test
    fun neverWritten_isNull() = runTest {
        assertNull(repo(InMemoryStore(), testScheduler).snapshot.first())
    }

    @Test
    fun roundTrip_linkedWithChannel_notLinked_linkedWithoutChannel() = runTest {
        val repo = repo(InMemoryStore(), testScheduler)
        listOf(
            TelegramLinkSnapshot("uid-a", linked = true, reminderChannel = ReminderChannel.Both),
            TelegramLinkSnapshot("uid-a", linked = false),
            TelegramLinkSnapshot("uid-b", linked = true, reminderChannel = null),
        ).forEach { written ->
            repo.write(written)
            assertEquals(written, repo.snapshot.first())
        }
    }

    @Test
    fun separatorInsideAccountId_doesNotShiftFields() = runTest {
        val repo = repo(InMemoryStore(), testScheduler)
        val written = TelegramLinkSnapshot("we|ird|uid", linked = true, reminderChannel = ReminderChannel.Telegram)
        repo.write(written)
        assertEquals(written, repo.snapshot.first())
    }

    @Test
    fun malformedOrFutureFormat_isNull() = runTest {
        listOf("garbage", "v2|1|push|uid", "v1|x|push|uid", "v1|1|push|").forEach { raw ->
            val store = InMemoryStore()
            // Key repeated as a literal: it is the persisted format.
            store.state.value = mutablePreferencesOf().apply { this[stringPreferencesKey("telegram_link_snapshot")] = raw }
            assertNull(repo(store, testScheduler).snapshot.first(), "raw=$raw")
        }
    }

    @Test
    fun unknownChannelWire_readsAsNotReported() = runTest {
        val store = InMemoryStore()
        store.state.value = mutablePreferencesOf().apply { this[stringPreferencesKey("telegram_link_snapshot")] = "v1|1|sms|uid" }
        assertEquals(TelegramLinkSnapshot("uid", linked = true, reminderChannel = null), repo(store, testScheduler).snapshot.first())
    }

    @Test
    fun unreadableStore_isNull_andLogged() = runTest {
        val logger = Logger()
        val broken = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IllegalStateException("corrupt") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = error("read only")
        }
        assertNull(TelegramLinkCacheRepositoryImpl(AppDatastore(broken), logger).snapshot.first())
        assertEquals(1, logger.errors)
    }

    private fun repo(store: InMemoryStore, scheduler: TestCoroutineScheduler) =
        TelegramLinkCacheRepositoryImpl(AppDatastore(store, StandardTestDispatcher(scheduler)), Logger())

    private class InMemoryStore : DataStore<Preferences> {
        val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
        override val data: Flow<Preferences> get() = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private class Logger : AppLogger {
        var errors = 0
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {
            errors++
        }
    }
}
