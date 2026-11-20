package com.antonchuraev.homesearchchecklist.telegram

import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.FirestoreSyncDataSource
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountApiService
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountApiServiceImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val telegramModule = module {
    single<TelegramAccountApiService> { TelegramAccountApiServiceImpl(logger = get()) }
    single<TelegramLinkRepository> {
        TelegramLinkRepositoryImpl(
            api = get(),
            authRepository = get(),
            userDataRepository = get(),
            reminderPrefs = get(),
            deviceTimeZone = get(),
            logger = get(),
        )
    }
    // Overridable per platform (wasmJs needs a browser-side Intl provider — see the KDoc).
    single<DeviceTimeZoneProvider> { KotlinxDeviceTimeZoneProvider }
    // App-scoped: zone write + channel cache refresh on every foreground (App.kt ON_START).
    single {
        val firestore = get<FirestoreSyncDataSource>()
        val telegram = get<TelegramLinkRepository>()
        ReminderProfileSync(
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            identity = reminderIdentityFlow(get(), get()),
            reminderPrefs = get(),
            deviceTimeZone = get(),
            writeUserDoc = { uid, fields -> firestore.mergeUserDocFields(uid, fields) },
            refreshChannel = { telegram.status() },
            logger = get(),
        )
    }
    // App-scoped: platform entry points submit the `?tg_link` token before App.kt mounts.
    single { PendingTelegramLink() }
    viewModel {
        TelegramViewModel(
            repository = get(),
            authPhase = telegramAuthPhaseFlow(get(), get()),
            linkChanged = get<PendingTelegramLink>().linkChanged,
            analyticsTracker = get(),
        )
    }
}
