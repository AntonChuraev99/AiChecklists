package com.antonchuraev.homesearchchecklist.core.database.di

import androidx.room.Room
import com.antonchuraev.homesearchchecklist.core.database.ChecklistDatabase
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSSearchPathDomainMask

actual fun platformDatabaseModule(): Module = module {
    single { provideDatabase() }
}

private fun provideDatabase() = Room.databaseBuilder<ChecklistDatabase>(
    name = databasePath()
).fallbackToDestructiveMigration(dropAllTables = true).build()

private fun databasePath(): String {
    val paths = NSSearchPathForDirectoriesInDomains(
        directory = NSSearchPathDirectory.NSDocumentDirectory,
        domainMask = NSSearchPathDomainMask.NSUserDomainMask,
        expandTilde = true
    )
    return "${paths.firstOrNull() as? String ?: ""}/checklists.db"
}

