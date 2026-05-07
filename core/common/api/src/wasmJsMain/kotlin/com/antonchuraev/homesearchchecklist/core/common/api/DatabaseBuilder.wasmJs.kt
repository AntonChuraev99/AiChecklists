package com.antonchuraev.homesearchchecklist.core.common.api

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import kotlinx.coroutines.Dispatchers
import org.w3c.dom.Worker

// Top-level, non-inline, public — required so that the public inline getDatabaseBuilder can call it.
// js() may only appear in a top-level function body in Kotlin/WASM.
@OptIn(ExperimentalWasmJsInterop::class)
fun createSqliteWorker(): Worker =
    js("""new Worker(new URL("sqlite-wasm-worker/worker.js", import.meta.url))""")

actual inline fun <reified T : RoomDatabase> getDatabaseBuilder(databaseName: String): RoomDatabase.Builder<T> {
    return Room.databaseBuilder<T>(
        name = "$databaseName.db"
    ).setDriver(WebWorkerSQLiteDriver(createSqliteWorker()))
        .setQueryCoroutineContext(Dispatchers.Default)
}
