package com.antonchuraev.homesearchchecklist.core.common.api

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask


@OptIn(ExperimentalForeignApi::class)
actual inline fun <reified T : RoomDatabase> getDatabaseBuilder(databaseName: String): RoomDatabase.Builder<T> {
    val dbDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )?.path ?: NSFileManager.defaultManager.currentDirectoryPath

    return Room.databaseBuilder<T>(
        name = dbDirectory + "/${databaseName}.db",
    ){
        TODO()
    }
}