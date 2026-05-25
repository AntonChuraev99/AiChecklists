package com.antonchuraev.homesearchchecklist.di

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentOpener
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStorage
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.csat.ObservableAnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.datastore.api.UserAppDatastoreProvider
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.FirestoreSyncDataSource
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.user.data.device.DeviceIdProvider
import com.antonchuraev.homesearchchecklist.sync.WasmFirestoreSyncDataSource
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

private object StubAnalyticsTracker : AnalyticsTracker {
    override fun setUserId(userId: String) = Unit
    override fun setUserProperties(properties: Map<String, Any>) = Unit
    override fun screenView(name: String) = Unit
    override fun event(name: String, params: Map<String, Any>) = Unit
}

private object StubReminderScheduler : ChecklistReminderScheduler {
    override fun scheduleReminder(checklistId: Long, triggerAtMillis: Long) = Unit
    override fun cancelReminder(checklistId: Long) = Unit
    override suspend fun rescheduleAllActiveReminders() = Unit
    override fun scheduleRepeat(checklistId: Long, triggerAtMillis: Long) = Unit
    override fun cancelRepeat(checklistId: Long) = Unit
    override suspend fun rescheduleAllActiveRepeats() = Unit
    override fun scheduleItemReminder(checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long) = Unit
    override fun cancelItemReminder(checklistId: Long, fillId: Long, itemId: String) = Unit
    override fun scheduleItemRepeat(checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long) = Unit
    override fun cancelItemRepeat(checklistId: Long, fillId: Long, itemId: String) = Unit
}

actual fun platformModule(): Module = module {
    single { DeviceIdProvider(UserAppDatastoreProvider.instance) }
    single<AnalyticsTracker> { ObservableAnalyticsTracker(StubAnalyticsTracker) }
    single<ChecklistReminderScheduler> { StubReminderScheduler }

    // AttachmentStorage: wasmJs stub — all methods are no-op / return null.
    // Bound as AttachmentStoragePort so ViewModel stays platform-agnostic in commonMain.
    single<AttachmentStoragePort> { AttachmentStorage() }

    // AttachmentOpener: wasmJs stub — openExternally always returns false.
    single { AttachmentOpener() }

    // wasmJs builds are always production (no debug menu), so isDebugBuild = false.
    single(named("isDebugBuild")) { false }

    // Firestore sync data source backed by globalThis.__firestore* bridges in init.js.
    single<FirestoreSyncDataSource> { WasmFirestoreSyncDataSource() }
}
