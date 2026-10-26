package com.antonchuraev.homesearchchecklist.core.common.api

import androidx.room3.RoomDatabase

/**
 * [onDriverFallback] is called (synchronously, while the builder is created) when the platform
 * cannot use its preferred SQLite driver and switches to a fallback one. Only Android has a
 * fallback today (bundled SQLite → OS SQLite, see `SqliteDriverSelector`); iOS and wasmJs ignore it.
 * The callback receives the cause; the caller decides how to report it.
 */
expect inline fun <reified T : RoomDatabase> getDatabaseBuilder(
    databaseName: String,
    noinline onDriverFallback: (Throwable) -> Unit = {},
): RoomDatabase.Builder<T>
