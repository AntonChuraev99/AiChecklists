package com.antonchuraev.homesearchchecklist

import platform.Foundation.NSBundle
import kotlin.experimental.ExperimentalNativeApi

actual object AppBuildConfig {
    @OptIn(ExperimentalNativeApi::class)
    actual val isDebug: Boolean = run {
        // Check if running in Xcode debug configuration
        // DEBUG preprocessor macro is typically set in debug builds
        val infoDictionary = NSBundle.mainBundle.infoDictionary
        val configuration = infoDictionary?.get("Configuration") as? String
        configuration?.contains("Debug", ignoreCase = true) == true
                || kotlin.native.Platform.isDebugBinary
    }
}
