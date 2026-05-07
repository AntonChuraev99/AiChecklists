package com.antonchuraev.homesearchchecklist

actual object AppBuildConfig {
    /** Web builds are always considered non-debug (production web). */
    actual val isDebug: Boolean = false
    actual val versionName: String = "1.14.3"
}
