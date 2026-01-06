package com.antonchuraev.homesearchchecklist.core.navigation.impl.di

import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.navigation.impl.AppNavigatorImpl
import org.koin.dsl.module


val navigationCoreModule = module {
    single<AppNavigator> { AppNavigatorImpl() }
}