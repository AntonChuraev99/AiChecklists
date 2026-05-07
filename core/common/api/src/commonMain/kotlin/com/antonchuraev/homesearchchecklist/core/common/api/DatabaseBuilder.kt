package com.antonchuraev.homesearchchecklist.core.common.api

import androidx.room3.RoomDatabase

expect inline fun <reified T : RoomDatabase> getDatabaseBuilder(
    databaseName: String,
): RoomDatabase.Builder<T>
