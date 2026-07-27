package com.antonchuraev.homesearchchecklist.feature.user.di

import com.antonchuraev.homesearchchecklist.core.common.api.NavExperimentResolver
import com.antonchuraev.homesearchchecklist.core.datastore.api.UserAppDatastoreProvider
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.UserApiService
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.UserApiServiceImpl
import com.antonchuraev.homesearchchecklist.feature.user.data.repository.UserDataRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.user.domain.experiment.NavExperimentResolverImpl
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.GetFirstChecklistVariantUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.GetNavVariantUseCase
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.GetOnboardingVariantUseCase
import org.koin.core.qualifier.named
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
    factory {
        GetOnboardingVariantUseCase(
            remoteConfigProvider = get(),
            logger = get(),
            isAndroid = get(named("isAndroid")),
        )
    }
    factory { GetFirstChecklistVariantUseCase(remoteConfigProvider = get(), logger = get()) }

    factory { GetNavVariantUseCase(remoteConfigProvider = get(), logger = get()) }

    // `single`, not `factory`: the resolver's stickiness lives in per-process @Volatile fields
    // (cached arm + "user property already mirrored" guard). A factory would hand out a fresh,
    // amnesiac instance to every caller and re-read Remote Config on each navigation change —
    // exactly the mid-session shell flip the design forbids.
    single<NavExperimentResolver> {
        NavExperimentResolverImpl(
            getNavVariant = get(),
            prefs = get(),
            analytics = get(),
            logger = get(),
        )
    }
}
