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
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val paywallFeatureModule = module {
    single<PaywallRepository> { PaywallRepositoryImpl() }

    // Use cases
    factory { GetSubscriptionStatusUseCase(get()) }
    factory { GetOfferingsUseCase(get()) }
    factory { PurchaseProductUseCase(get(), get()) }
    factory { RestorePurchasesUseCase(get(), get()) }
    factory { GetUserLimitsUseCase(get(), get(), get()) }

    // ViewModels
    viewModel { params ->
        PaywallViewModel(
            savedStateHandle = get(),
            navigator = get(),
            getOfferingsUseCase = get(),
            purchaseProductUseCase = get(),
            restorePurchasesUseCase = get(),
            analyticsTracker = get(),
            sourceOverride = params.getOrNull<String>()
        )
    }
    viewModelOf(::SubscriptionStatusViewModel)
}
