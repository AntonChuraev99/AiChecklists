package com.antonchuraev.homesearchchecklist.di

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.csat.ObservableAnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.datastore.api.UserAppDatastoreProvider
import com.antonchuraev.homesearchchecklist.feature.analyze.data.config.GeminiConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.user.data.device.DeviceIdProvider
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSBundle

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
}

actual fun platformModule(): Module = module {
    single {
        val apiKey = NSBundle.mainBundle.objectForInfoDictionaryKey("GEMINI_API_KEY") as? String ?: ""
        GeminiConfig(apiKey = apiKey)
    }
    single { DeviceIdProvider(UserAppDatastoreProvider.instance) }
    single<AnalyticsTracker> { ObservableAnalyticsTracker(StubAnalyticsTracker) }
    single<ChecklistReminderScheduler> { StubReminderScheduler }
}
