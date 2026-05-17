package com.antonchuraev.homesearchchecklist.feature.checklist.di

import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChatHistoryDao
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.SmartDateParser
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.SmartDateParserImpl
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase.RecoverRecurringRemindersUseCase
import org.koin.dsl.module

val checklistFeatureModule = module {
    single<ChecklistRepository> { createChecklistRepository(get<AttachmentStoragePort>()) }
    single { RecoverRecurringRemindersUseCase(get(), get(), getOrNull()) }
    single<SmartDateParser> { SmartDateParserImpl(get()) }
    // ChatHistoryDao is derived from the same ChecklistDatabase singleton used by createChecklistRepository.
    // The database lazy is package-private in ChecklistDataModule — expose the DAO via a separate factory.
    single<ChatHistoryDao> { getChecklistDatabase(get<AttachmentStoragePort>()).chatHistoryDao() }
}
