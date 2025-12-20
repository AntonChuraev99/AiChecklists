package com.antonchuraev.homesearchchecklist.core.database.di

import com.antonchuraev.homesearchchecklist.core.database.ChecklistDao
import com.antonchuraev.homesearchchecklist.core.database.ChecklistDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

val databaseModule = module {
    includes(platformDatabaseModule())
    single<ChecklistDao> { get<ChecklistDatabase>().checklistDao() }
}

expect fun platformDatabaseModule(): Module

