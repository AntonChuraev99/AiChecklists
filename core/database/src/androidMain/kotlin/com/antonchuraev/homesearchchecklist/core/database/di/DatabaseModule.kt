package com.antonchuraev.homesearchchecklist.core.database.di

import android.content.Context
import androidx.room.Room
import com.antonchuraev.homesearchchecklist.core.database.ChecklistDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformDatabaseModule(): Module = module {
    single { provideDatabase(get()) }
}

private fun provideDatabase(context: Context) = Room.databaseBuilder(
    context,
    ChecklistDatabase::class.java,
    "checklists.db"
).fallbackToDestructiveMigration(dropAllTables = true).build()

