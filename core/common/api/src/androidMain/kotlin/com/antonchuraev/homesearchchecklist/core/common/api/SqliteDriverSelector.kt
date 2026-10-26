package com.antonchuraev.homesearchchecklist.core.common.api

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Chooses the SQLite driver Room opens the local database with on Android.
 *
 * The default is [BundledSQLiteDriver] — SQLite compiled into the app as `libsqliteJni.so`.
 * Some installs do not carry that library for the device ABI (re-packaged or sideloaded APKs,
 * broken split installs). The bundled driver loads it lazily, from a static initializer, on the
 * first `open()` inside Room's coroutine: the resulting [UnsatisfiedLinkError] is an `Error` on a
 * background thread, it kills the process, and every later start dies the same way
 * (Crashlytics 9775ecb7, `dlopen failed: library "libsqliteJni.so" not found`).
 * A try/catch around `setDriver(BundledSQLiteDriver())` cannot see it — nothing is loaded there.
 *
 * So the library is probed here, up front, with the same `System.loadLibrary` call the driver
 * makes. Loaded → the driver's own load later is a no-op and behaviour is unchanged. Not loadable →
 * [AndroidSQLiteDriver] (the OS SQLite, which is also Room 3's own default on Android) is used, and
 * the failure is reported as a non-fatal so the size of the fallback population stays visible.
 *
 * The probe deliberately does not touch the driver's internal holder class: a static initializer
 * that failed once leaves its class unusable (`NoClassDefFoundError`) for the rest of the process.
 *
 * ⚠️ On fallback devices the OS SQLite is as old as 3.9 (minSdk 24). Every query and migration of
 * `ChecklistDatabase` was audited against 3.9 on 2026-10-26; new SQL must stay within it — no
 * `RETURNING`, UPSERT `ON CONFLICT … DO UPDATE`, window functions, `RENAME/DROP COLUMN`, `->>`,
 * `iif()`, `STRICT`, generated columns.
 */
@PublishedApi
internal object SqliteDriverSelector {
    /** The name `BundledSQLiteDriver` passes to `System.loadLibrary` (verified in sqlite-bundled 2.7.1). */
    internal const val BUNDLED_LIBRARY_NAME = "sqliteJni"

    /**
     * [onFallback] receives the load failure when the OS driver is chosen. Reporting is the
     * caller's job (it owns the AppLogger → Crashlytics non-fatal); this api module has no DI.
     */
    @PublishedApi
    internal fun select(onFallback: (Throwable) -> Unit): SQLiteDriver = select(
        loadBundledLibrary = { System.loadLibrary(BUNDLED_LIBRARY_NAME) },
        bundledDriver = { BundledSQLiteDriver() },
        frameworkDriver = { AndroidSQLiteDriver() },
        onFallback = onFallback,
    )

    /**
     * Pure decision, injectable for host tests. Any failure to load the bundled library falls
     * back: whatever stops `System.loadLibrary` here would stop the bundled driver's own load too.
     */
    internal fun select(
        loadBundledLibrary: () -> Unit,
        bundledDriver: () -> SQLiteDriver,
        frameworkDriver: () -> SQLiteDriver,
        onFallback: (Throwable) -> Unit,
    ): SQLiteDriver {
        val loadFailure = runCatching(loadBundledLibrary).exceptionOrNull()
            ?: return bundledDriver()
        onFallback(loadFailure)
        return frameworkDriver()
    }
}
