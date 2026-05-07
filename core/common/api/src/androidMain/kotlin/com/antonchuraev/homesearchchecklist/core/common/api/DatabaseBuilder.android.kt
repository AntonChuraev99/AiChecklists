package com.antonchuraev.homesearchchecklist.core.common.api

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

actual inline fun <reified T : RoomDatabase> getDatabaseBuilder(databaseName: String): RoomDatabase.Builder<T> {
    val appContext = AppContextHolder.context
    val dbFile = appContext.getDatabasePath("${databaseName}.db")
    return Room.databaseBuilder<T>(
        context = appContext,
        name = dbFile.absolutePath
    ).setDriver(BundledSQLiteDriver())
}
