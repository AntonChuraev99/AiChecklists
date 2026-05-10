package com.antonchuraev.homesearchchecklist

import android.content.pm.ApplicationInfo
import com.antonchuraev.homesearchchecklist.core.common.api.AppContextHolder

actual object AppBuildConfig {
    /**
     * Runtime debug detection — replaces compile-time BuildConfig.DEBUG.
     * Works in KMP library modules where BuildConfig is not generated.
     * Uses ApplicationInfo.FLAG_DEBUGGABLE set by the build system.
     */
    actual val isDebug: Boolean
        get() = try {
            val ctx = AppContextHolder.context
            (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (_: Exception) {
            false
        }

    /**
     * Runtime versionName — replaces compile-time BuildConfig.VERSION_NAME.
     * Works in KMP library modules where BuildConfig is not generated.
     */
    actual val versionName: String
        get() = try {
            val ctx = AppContextHolder.context
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
}
