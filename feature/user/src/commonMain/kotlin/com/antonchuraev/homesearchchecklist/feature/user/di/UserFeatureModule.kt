package com.antonchuraev.homesearchchecklist.feature.user.di

import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.feature.user.data.repository.UserDataRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
import org.koin.dsl.bind
import org.koin.dsl.module

val userFeatureModule = module {
    single { UserDataRepositoryImpl(get()) } bind UserDataRepository::class
    factory { CompleteOnboardingUseCase(get()) }
}

