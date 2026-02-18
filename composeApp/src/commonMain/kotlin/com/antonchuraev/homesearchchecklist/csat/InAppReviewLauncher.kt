package com.antonchuraev.homesearchchecklist.csat

import androidx.compose.runtime.Composable

/**
 * Platform-specific composable that launches the native in-app review flow.
 *
 * - Android: Uses Google Play In-App Review API (review-ktx).
 * - iOS: Stub — iOS is not released yet.
 *
 * @param shouldLaunch When true, initiates the review flow.
 * @param onComplete Called when the review flow finishes (success, error, or quota exceeded).
 */
@Composable
expect fun InAppReviewLauncher(
    shouldLaunch: Boolean,
    onComplete: () -> Unit,
)
