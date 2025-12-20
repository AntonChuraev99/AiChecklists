package com.antonchuraev.homesearchchecklist.di

import com.antonchuraev.homesearchchecklist.core.common.impl.di.commonCoreModule
import com.antonchuraev.homesearchchecklist.core.database.di.databaseModule
import com.antonchuraev.homesearchchecklist.feature.checklist.di.checklistFeatureModule
import com.antonchuraev.homesearchchecklist.viewmodels.DebugViewModel
import com.antonchuraev.homesearchchecklist.viewmodels.OnboardingViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    includes(
        commonCoreModule,
        databaseModule,
        checklistFeatureModule,
        platformModule()
    )
    
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::DebugViewModel)
}

expect fun platformModule(): Module
