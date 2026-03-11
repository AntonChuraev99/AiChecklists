package com.antonchuraev.homesearchchecklist.feature.user.di

import com.antonchuraev.homesearchchecklist.core.datastore.api.UserAppDatastoreProvider
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.UserApiService
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.UserApiServiceImpl
import com.antonchuraev.homesearchchecklist.feature.user.data.repository.UserDataRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.GetOnboardingVariantUseCase
import org.koin.dsl.bind
import org.koin.dsl.module

val userFeatureModule = module {
    // User API Service for registration
    single<UserApiService> { UserApiServiceImpl(logger = get()) }

    // Repository with DeviceIdProvider (from platform module) and UserApiService
    single {
        UserDataRepositoryImpl(
            appScope = get(),
            deviceIdProvider = get(),
            userApiService = get(),
            logger = get(),
            appDatastore = UserAppDatastoreProvider.instance,
            analyticsTracker = get()
        )
    } bind UserDataRepository::class

    factory { CompleteOnboardingUseCase(get()) }
    factory { GetOnboardingVariantUseCase(get()) }
}
