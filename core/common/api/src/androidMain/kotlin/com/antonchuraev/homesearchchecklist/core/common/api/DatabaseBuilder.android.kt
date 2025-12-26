package com.antonchuraev.homesearchchecklist.core.common.api

import androidx.room.Room
import androidx.room.RoomDatabase

actual inline fun <reified T : RoomDatabase> getDatabaseBuilder(databaseName: String): RoomDatabase.Builder<T> {
    val appContext = AppContextHolder.context
    val dbFile = appContext.getDatabasePath("${databaseName}.db")
    return Room.databaseBuilder<T>(
        context = appContext,
        name = dbFile.absolutePath
    )
}