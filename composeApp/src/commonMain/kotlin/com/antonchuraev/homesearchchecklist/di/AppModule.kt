package com.antonchuraev.homesearchchecklist.di

import com.antonchuraev.homesearchchecklist.AppViewModel
import com.antonchuraev.homesearchchecklist.csat.CsatManager
import com.antonchuraev.homesearchchecklist.csat.CsatViewModel
import com.antonchuraev.homesearchchecklist.core.common.impl.di.commonCoreModule
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.datastore.api.UserAppDatastoreProvider
import com.antonchuraev.homesearchchecklist.core.datastore.impl.di.datastoreModule
import com.antonchuraev.homesearchchecklist.core.navigation.impl.di.navigationCoreModule
import com.antonchuraev.homesearchchecklist.feature.checklist.di.checklistFeatureModule
import com.antonchuraev.homesearchchecklist.feature.create.di.createFeatureModule
import com.antonchuraev.homesearchchecklist.feature.debug.di.debugFeatureModule
import com.antonchuraev.homesearchchecklist.feature.home.di.homeFeatureModule
import com.antonchuraev.homesearchchecklist.feature.onboarding.di.onboardingFeatureModule
import com.antonchuraev.homesearchchecklist.feature.splash.di.splashFeatureModule
import com.antonchuraev.homesearchchecklist.feature.user.di.userFeatureModule
import com.antonchuraev.homesearchchecklist.feature.analyze.di.analyzeFeatureModule
import com.antonchuraev.homesearchchecklist.feature.paywall.di.paywallFeatureModule
import com.antonchuraev.homesearchchecklist.feature.sharing.di.sharingFeatureModule
import com.antonchuraev.homesearchchecklist.feature.updatefeed.di.updateFeedFeatureModule
import com.antonchuraev.homesearchchecklist.settings.di.settingsModule
import com.antonchuraev.homesearchchecklist.core.remoteconfig.impl.di.remoteConfigModule
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    includes(
        commonCoreModule,
        navigationCoreModule,
        remoteConfigModule,
        checklistFeatureModule,
        createFeatureModule,
        onboardingFeatureModule,
        debugFeatureModule,
        homeFeatureModule,
        splashFeatureModule,
        analyzeFeatureModule,
        paywallFeatureModule,  // Must be before splashFeatureModule (SplashViewModel depends on PaywallRepository)
        userFeatureModule,
        sharingFeatureModule,
        updateFeedFeatureModule,
        settingsModule,
        datastoreModule,
        platformModule()
    )
    single<AppDatastore> { UserAppDatastoreProvider.instance }
    single { CsatManager(get()) }
    viewModelOf(::AppViewModel)
    // CsatViewModel as singleton — accessed from App.kt and DebugScreen
    single { CsatViewModel(get(), get()) }
}

expect fun platformModule(): Module
