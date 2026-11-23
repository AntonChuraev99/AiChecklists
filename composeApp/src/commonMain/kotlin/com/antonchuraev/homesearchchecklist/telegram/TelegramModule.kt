package com.antonchuraev.homesearchchecklist.telegram

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
import com.antonchuraev.homesearchchecklist.core.common.api.TelegramReminderLink
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.FirestoreSyncDataSource
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderDeliveryChannelController
import com.antonchuraev.homesearchchecklist.feature.user.data.device.getPlatformName
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountApiService
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountApiServiceImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val telegramModule = module {
    single<TelegramAccountApiService> { TelegramAccountApiServiceImpl(logger = get()) }
    single {
        TelegramLinkCacheWriter(
            reminderPrefs = get(),
            linkCache = get(),
            hints = get(),
            logger = get(),
        )
    }
    single<TelegramLinkRepository> {
        TelegramLinkRepositoryImpl(
            api = get(),
            authRepository = get(),
            userDataRepository = get(),
            cacheWriter = get(),
            deviceTimeZone = get(),
            logger = get(),
        )
    }
    // Feature-facing link state + channel save (reminder sheet, Inbox promo). App-scoped so a save
    // outlives the sheet and its host ViewModel.
    single<TelegramReminderLink> {
        val auth = get<GoogleAuthRepository>()
        TelegramReminderLinkImpl(
            repository = get(),
            linkCache = get(),
            authPhase = telegramAuthPhaseFlow(auth, get()),
            accountId = auth.authState.map { (it as? GoogleAuthState.Authenticated)?.user?.firebaseUid },
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    // The ONE reminder-sheet delivery controller (spec §5.2), shared by every sheet. Main
    // dispatcher on purpose: the tap handlers and the save loop then never interleave, so
    // "latest tap wins" cannot lose a tap that lands while the loop is finishing. The network call
    // itself runs in TelegramReminderLink's own app scope.
    single {
        ReminderDeliveryChannelController(
            link = get(),
            analytics = get(),
            logger = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            web = getPlatformName() == "web",
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
