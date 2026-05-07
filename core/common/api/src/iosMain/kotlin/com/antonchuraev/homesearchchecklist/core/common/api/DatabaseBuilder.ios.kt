package com.antonchuraev.homesearchchecklist.core.common.api

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}


@OptIn(ExperimentalForeignApi::class)
actual inline fun <reified T : RoomDatabase> getDatabaseBuilder(databaseName: String): RoomDatabase.Builder<T> {
    val dbFilePath = documentDirectory() + "/${databaseName}.db"
    return Room.databaseBuilder<T>(
        name = dbFilePath,
    ).setDriver(BundledSQLiteDriver())
}
