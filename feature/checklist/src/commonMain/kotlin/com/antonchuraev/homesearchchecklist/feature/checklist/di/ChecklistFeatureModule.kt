package com.antonchuraev.homesearchchecklist.feature.checklist.di

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentCloudStoragePort
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChatHistoryDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.FirestoreSyncDataSource
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.InitialUploadGate
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
    single<ChecklistRepository> {
        createChecklistRepository(
            attachmentStorage = get<AttachmentStoragePort>(),
            attachmentCloudStorage = get<AttachmentCloudStoragePort>(),
            logger = get<AppLogger>(),
        )
    }
    single { RecoverRecurringRemindersUseCase(get(), get(), getOrNull()) }
    single<SmartDateParser> { SmartDateParserImpl(get()) }
    single<ChatHistoryDao> { getChecklistDatabase(get<AttachmentStoragePort>()).chatHistoryDao() }
    // DAOs exposed as first-class DI definitions so non-repository consumers can
    // resolve them directly. The Android home-screen widget's WidgetRepository
    // requires get<ChecklistDao>() / get<ChecklistFillDao>(); without these the
    // widget crashed at provideGlance with NoDefinitionFoundException → the system
    // rendered "Couldn't load widget". Same single source as the repositories above.
    single<ChecklistDao> { getChecklistDatabase(get<AttachmentStoragePort>()).checklistDao() }
    single<ChecklistFillDao> { getChecklistDatabase(get<AttachmentStoragePort>()).checklistFillDao() }
    single<SyncRepository> {
        val db = getChecklistDatabase(get<AttachmentStoragePort>())
        SyncRepositoryImpl(
            checklistDao = db.checklistDao(),
            fillDao = db.checklistFillDao(),
            firestoreDataSource = get<FirestoreSyncDataSource>(),
            authRepository = get<GoogleAuthRepository>(),
            initialUploadGate = get<InitialUploadGate>(),
            attachmentCloudStorage = get<AttachmentCloudStoragePort>(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            logger = get<AppLogger>(),
        )
    }
}
