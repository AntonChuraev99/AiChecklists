package com.antonchuraev.homesearchchecklist.core.common.api

import androidx.room3.Room
import androidx.room3.RoomDatabase

actual inline fun <reified T : RoomDatabase> getDatabaseBuilder(
    databaseName: String,
    noinline onDriverFallback: (Throwable) -> Unit,
): RoomDatabase.Builder<T> {
    val appContext = AppContextHolder.context
    val dbFile = appContext.getDatabasePath("${databaseName}.db")
    // Bundled SQLite normally; OS SQLite when libsqliteJni.so cannot be loaded (see SqliteDriverSelector).
    return Room.databaseBuilder<T>(context = appContext, name = dbFile.absolutePath)
        .setDriver(SqliteDriverSelector.select(onFallback = onDriverFallback))
}
