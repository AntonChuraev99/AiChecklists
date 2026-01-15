package com.antonchuraev.homesearchchecklist.feature.paywall.di

import com.antonchuraev.homesearchchecklist.feature.paywall.data.repository.PaywallRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetOfferingsUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetSubscriptionStatusUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.PurchaseProductUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.RestorePurchasesUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.PaywallViewModel
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.SubscriptionStatusViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val paywallFeatureModule = module {
    // Repository
    single<PaywallRepository> {
        PaywallRepositoryImpl(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
    }

    // Use cases
    factory { GetSubscriptionStatusUseCase(get()) }
    factory { GetOfferingsUseCase(get()) }
    factory { PurchaseProductUseCase(get()) }
    factory { RestorePurchasesUseCase(get()) }
    factory { GetUserLimitsUseCase(get(), get(), get()) }

    // ViewModels
    viewModelOf(::PaywallViewModel)
    viewModelOf(::SubscriptionStatusViewModel)
}
