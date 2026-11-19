package com.antonchuraev.homesearchchecklist.telegram

import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountApiService
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountApiServiceImpl
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val telegramModule = module {
    single<TelegramAccountApiService> { TelegramAccountApiServiceImpl(logger = get()) }
    single<TelegramLinkRepository> {
        TelegramLinkRepositoryImpl(
            api = get(),
            authRepository = get(),
            userDataRepository = get(),
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
