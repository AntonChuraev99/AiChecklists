package com.antonchuraev.homesearchchecklist.gestures

import androidx.compose.runtime.Composable

@Composable
actual fun ApplyEdgeSwipeExclusion(enabled: Boolean) {
    // iOS has no edge-swipe-back gesture that conflicts with NavigationDrawer
}
