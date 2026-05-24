package com.antonchuraev.homesearchchecklist.gestures

import androidx.compose.runtime.Composable

/**
 * Reserves the leftmost ~48dp of the current Activity window as the app's own
 * gesture area so Android's edge swipe-back doesn't steal swipes meant for
 * opening the ModalNavigationDrawer.
 *
 * Without this, on Android 13+ predictive-back swipes from the left edge are
 * captured by the OS and the app is sent to the back stack — the user perceives
 * the drawer as "not showing".
 *
 * Toggle via [enabled]: pass `false` while the drawer is already open or
 * while in edit mode (drag-reorder conflict).
 *
 * - Android: implemented via `View.systemGestureExclusionRects` (API 29+).
 * - iOS / wasmJs: no-op (no equivalent OS gesture conflict).
 */
@Composable
expect fun ApplyEdgeSwipeExclusion(enabled: Boolean)
