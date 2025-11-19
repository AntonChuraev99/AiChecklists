package com.antonchuraev.homesearchchecklist.di

import androidx.room.Room
import com.antonchuraev.homesearchchecklist.data.local.room.ChecklistDao
import com.antonchuraev.homesearchchecklist.data.local.room.ChecklistDatabase
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSSearchPathDomainMask

actual fun platformModule(): Module = module {
    single { provideDatabase() }
    single<ChecklistDao> { get<ChecklistDatabase>().checklistDao() }
}

private fun provideDatabase(): ChecklistDatabase {
    return Room.databaseBuilder<ChecklistDatabase>(
        name = databasePath()
    ).fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}

private fun databasePath(): String {
    val paths = NSSearchPathForDirectoriesInDomains(
        directory = NSSearchPathDirectory.NSDocumentDirectory,
        domainMask = NSSearchPathDomainMask.NSUserDomainMask,
        expandTilde = true
    )
    val documentsDirectory = paths.firstOrNull() as? String ?: ""
    return "$documentsDirectory/checklists.db"
}


