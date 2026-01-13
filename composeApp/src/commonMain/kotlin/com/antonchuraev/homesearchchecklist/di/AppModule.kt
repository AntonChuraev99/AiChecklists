package com.antonchuraev.homesearchchecklist.di

import com.antonchuraev.homesearchchecklist.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.impl.di.commonCoreModule
import com.antonchuraev.homesearchchecklist.core.navigation.impl.di.navigationCoreModule
import com.antonchuraev.homesearchchecklist.feature.checklist.di.checklistFeatureModule
import com.antonchuraev.homesearchchecklist.feature.create.di.createFeatureModule
import com.antonchuraev.homesearchchecklist.feature.debug.di.debugFeatureModule
import com.antonchuraev.homesearchchecklist.feature.home.di.homeFeatureModule
import com.antonchuraev.homesearchchecklist.feature.onboarding.di.onboardingFeatureModule
import com.antonchuraev.homesearchchecklist.feature.splash.di.splashFeatureModule
import com.antonchuraev.homesearchchecklist.feature.user.di.userFeatureModule
import com.antonchuraev.homesearchchecklist.feature.analyze.di.analyzeFeatureModule
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    includes(
        commonCoreModule,
        navigationCoreModule,
        checklistFeatureModule,
        createFeatureModule,
        onboardingFeatureModule,
        debugFeatureModule,
        homeFeatureModule,
        splashFeatureModule,
        userFeatureModule,
        analyzeFeatureModule,
        platformModule()
    )
    viewModelOf(::AppViewModel)
}

expect fun platformModule(): Module
