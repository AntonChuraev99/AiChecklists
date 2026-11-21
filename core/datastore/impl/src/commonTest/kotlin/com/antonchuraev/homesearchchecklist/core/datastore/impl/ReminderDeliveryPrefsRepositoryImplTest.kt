package com.antonchuraev.homesearchchecklist.core.datastore.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The cache `ReminderReceiver` reads: unknown, unreadable and never-fetched all mean Push. */
class ReminderDeliveryPrefsRepositoryImplTest {

    @Test
    fun neverFetched_isPush() = runTest {
        assertEquals(ReminderChannel.Push, repo(InMemoryStore(), testScheduler).getReminderChannel())
    }

    @Test
    fun roundTrip_channelAndZoneKey() = runTest {
        val repo = repo(InMemoryStore(), testScheduler)
        repo.setReminderChannel(ReminderChannel.Telegram)
        repo.setLastWrittenTimezoneKey("uid|Europe/Berlin")
        assertEquals(ReminderChannel.Telegram, repo.getReminderChannel())
        assertEquals("uid|Europe/Berlin", repo.getLastWrittenTimezoneKey())
    }

    @Test
    fun unknownStoredValue_isPush() = runTest {
        val store = InMemoryStore()
        // Wire key repeated as a literal: it is the persisted format.
        store.state.value = mutablePreferencesOf().apply { this[stringPreferencesKey("reminder_channel")] = "sms" }
        assertEquals(ReminderChannel.Push, repo(store, testScheduler).getReminderChannel())
    }

    @Test
    fun unreadableStore_isPush_andLogged() = runTest {
        val logger = Logger()
        val broken = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IllegalStateException("corrupt") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = error("read only")
        }
        val repo = ReminderDeliveryPrefsRepositoryImpl(AppDatastore(broken), logger)
        assertEquals(ReminderChannel.Push, repo.getReminderChannel())
        assertEquals(1, logger.errors)
    }

    private fun repo(store: InMemoryStore, scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        ReminderDeliveryPrefsRepositoryImpl(AppDatastore(store, StandardTestDispatcher(scheduler)), Logger())

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
