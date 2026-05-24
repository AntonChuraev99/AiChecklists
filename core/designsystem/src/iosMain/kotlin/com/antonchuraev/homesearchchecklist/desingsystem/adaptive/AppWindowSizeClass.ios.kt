package com.antonchuraev.homesearchchecklist.desingsystem.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

@Composable
actual fun rememberAppWindowSizeClass(): AppWindowSizeClass {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val widthDp = with(density) { containerSize.width.toDp().value.toInt() }
    // Defaults to Compact when containerSize is IntSize.Zero (pre-layout)
    return classifyWindowWidth(if (widthDp <= 0) 0 else widthDp)
}
