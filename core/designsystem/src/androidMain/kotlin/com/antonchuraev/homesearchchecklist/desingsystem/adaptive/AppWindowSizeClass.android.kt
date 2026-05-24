package com.antonchuraev.homesearchchecklist.desingsystem.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
actual fun rememberAppWindowSizeClass(): AppWindowSizeClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return classifyWindowWidth(widthDp)
}
