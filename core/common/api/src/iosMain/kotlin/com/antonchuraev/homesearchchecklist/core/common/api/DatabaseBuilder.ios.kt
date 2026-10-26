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


// onDriverFallback is unused: iOS always uses the bundled driver (statically linked, nothing to load).
@OptIn(ExperimentalForeignApi::class)
@Suppress("UNUSED_PARAMETER")
actual inline fun <reified T : RoomDatabase> getDatabaseBuilder(
    databaseName: String,
    noinline onDriverFallback: (Throwable) -> Unit,
): RoomDatabase.Builder<T> {
    val dbFilePath = documentDirectory() + "/${databaseName}.db"
    return Room.databaseBuilder<T>(
        name = dbFilePath,
    ).setDriver(BundledSQLiteDriver())
}
