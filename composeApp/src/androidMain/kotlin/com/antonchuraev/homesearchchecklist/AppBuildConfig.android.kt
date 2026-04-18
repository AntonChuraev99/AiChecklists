package com.antonchuraev.homesearchchecklist

import com.antonchuraev.aichecklists.BuildConfig

actual object AppBuildConfig {
    actual val isDebug: Boolean = BuildConfig.DEBUG
    actual val versionName: String = BuildConfig.VERSION_NAME
}
