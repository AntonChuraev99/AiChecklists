package com.antonchuraev.homesearchchecklist.feature.checklist.di

import com.antonchuraev.homesearchchecklist.feature.checklist.data.repository.ChecklistRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import org.koin.dsl.module

val checklistFeatureModule = module {
    single { checklistDao }
    single<ChecklistRepository> { ChecklistRepositoryImpl(checklistDao) }
}

