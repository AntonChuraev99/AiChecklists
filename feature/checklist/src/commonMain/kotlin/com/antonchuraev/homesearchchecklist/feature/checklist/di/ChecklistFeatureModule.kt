package com.antonchuraev.homesearchchecklist.feature.checklist.di

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChatHistoryDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.FirestoreSyncDataSource
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.SyncRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.SmartDateParser
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.SmartDateParserImpl
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.SyncRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase.RecoverRecurringRemindersUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val checklistFeatureModule = module {
    single<ChecklistRepository> { createChecklistRepository(get<AttachmentStoragePort>()) }
    single { RecoverRecurringRemindersUseCase(get(), get(), getOrNull()) }
    single<SmartDateParser> { SmartDateParserImpl(get()) }
    single<ChatHistoryDao> { getChecklistDatabase(get<AttachmentStoragePort>()).chatHistoryDao() }
    single<SyncRepository> {
        val db = getChecklistDatabase(get<AttachmentStoragePort>())
        SyncRepositoryImpl(
            checklistDao = db.checklistDao(),
            fillDao = db.checklistFillDao(),
            firestoreDataSource = get<FirestoreSyncDataSource>(),
            authRepository = get<GoogleAuthRepository>(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
}
