package com.antonchuraev.homesearchchecklist.feature.checklist.di

import com.antonchuraev.homesearchchecklist.feature.checklist.data.di.checklistDao
import org.koin.dsl.module

public val checklistFeatureModule = module {
    single(){
        checklistDao
    }
}

