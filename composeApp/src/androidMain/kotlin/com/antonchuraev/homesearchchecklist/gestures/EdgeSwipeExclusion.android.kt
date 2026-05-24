package com.antonchuraev.homesearchchecklist.gestures

import android.graphics.Rect
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

private val EDGE_WIDTH_DP = 48.dp

@Composable
actual fun ApplyEdgeSwipeExclusion(enabled: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val view = LocalView.current
    val density = LocalDensity.current
    val widthPx = with(density) { EDGE_WIDTH_DP.toPx() }.toInt()

    DisposableEffect(enabled, widthPx) {
        view.systemGestureExclusionRects = if (enabled) {
            listOf(Rect(0, 0, widthPx, view.height.takeIf { it > 0 } ?: Int.MAX_VALUE))
        } else {
            emptyList()
        }
        onDispose {
            view.systemGestureExclusionRects = emptyList()
        }
    }
}
