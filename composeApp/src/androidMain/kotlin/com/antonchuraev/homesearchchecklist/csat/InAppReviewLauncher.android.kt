package com.antonchuraev.homesearchchecklist.csat

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview

@Composable
actual fun InAppReviewLauncher(
    shouldLaunch: Boolean,
    onComplete: () -> Unit,
) {
    val activity = LocalActivity.current
    val currentOnComplete by rememberUpdatedState(onComplete)

    LaunchedEffect(shouldLaunch) {
        if (!shouldLaunch) return@LaunchedEffect
        val currentActivity = activity as? ComponentActivity
        if (currentActivity == null || currentActivity.isFinishing) {
            currentOnComplete()
            return@LaunchedEffect
        }
        try {
            val manager = ReviewManagerFactory.create(currentActivity)
            val reviewInfo = manager.requestReview()
            manager.launchReview(currentActivity, reviewInfo)
        } catch (_: Exception) {
            // Quota exceeded or other error — silent fail
        }
        currentOnComplete()
    }
}
