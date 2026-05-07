package com.antonchuraev.homesearchchecklist.feature.checklist.di

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase.RecoverRecurringRemindersUseCase
import org.koin.dsl.module

val checklistFeatureModule = module {
    single<ChecklistRepository> { createChecklistRepository() }
    single { RecoverRecurringRemindersUseCase(get(), get(), getOrNull()) }
}
