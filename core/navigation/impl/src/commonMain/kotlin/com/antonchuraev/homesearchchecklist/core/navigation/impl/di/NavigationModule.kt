package com.antonchuraev.homesearchchecklist.core.navigation.impl.di

import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.navigation.api.ShellLaunchRequests
import com.antonchuraev.homesearchchecklist.core.navigation.impl.AppNavigatorImpl
import com.antonchuraev.homesearchchecklist.core.navigation.impl.ShellLaunchRequestsImpl
import org.koin.dsl.module


val navigationCoreModule = module {
    single<AppNavigator> { AppNavigatorImpl() }
    single<ShellLaunchRequests> { ShellLaunchRequestsImpl() }
}
