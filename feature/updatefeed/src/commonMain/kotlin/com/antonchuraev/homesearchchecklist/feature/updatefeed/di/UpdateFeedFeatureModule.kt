package com.antonchuraev.homesearchchecklist.feature.updatefeed.di

import com.antonchuraev.homesearchchecklist.feature.updatefeed.data.repository.UpdateFeedRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.deeplink.UpdateFeedDeepLinkHandler
import com.antonchuraev.homesearchchecklist.feature.updatefeed.domain.repository.UpdateFeedRepository
import com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation.UpdateFeedViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val updateFeedFeatureModule = module {
    single<UpdateFeedRepository> { UpdateFeedRepositoryImpl(get(), get()) }
    single { UpdateFeedDeepLinkHandler(get()) }
    // get() order: repository, navigator, deepLinkHandler, getSubscriptionStatusUseCase, analyticsTracker, logger
    viewModel { UpdateFeedViewModel(get(), get(), get(), get(), get(), get()) }
}
