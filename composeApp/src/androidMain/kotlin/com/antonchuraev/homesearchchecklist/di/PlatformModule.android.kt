package com.antonchuraev.homesearchchecklist.di

import androidx.room.Room
import com.antonchuraev.homesearchchecklist.data.local.room.ChecklistDao
import com.antonchuraev.homesearchchecklist.data.local.room.ChecklistDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single { provideDatabase() }
    single<ChecklistDao> { get<ChecklistDatabase>().checklistDao() }
}

private fun provideDatabase(): ChecklistDatabase {
    return Room.databaseBuilder(
        AppContextHolder.context,
        ChecklistDatabase::class.java,
        "checklists.db"
    ).fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}


