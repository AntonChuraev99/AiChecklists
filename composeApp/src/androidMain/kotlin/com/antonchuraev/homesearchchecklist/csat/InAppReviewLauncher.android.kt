package com.antonchuraev.homesearchchecklist.csat

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import org.koin.compose.koinInject

private const val TAG = "Csat"

@Composable
actual fun InAppReviewLauncher(
    shouldLaunch: Boolean,
    onComplete: () -> Unit,
) {
    val activity = LocalActivity.current
    val logger: AppLogger = koinInject()
    val currentOnComplete by rememberUpdatedState(onComplete)

    LaunchedEffect(shouldLaunch) {
        if (!shouldLaunch) return@LaunchedEffect
        val currentActivity = activity as? ComponentActivity
        if (currentActivity == null || currentActivity.isFinishing) {
            // No review was ever shown — csat_review_completed still fires (the flow is over), so
            // say so in the log, otherwise this is indistinguishable from a real review.
            logger.warning(TAG, "in-app review skipped: activity unavailable or finishing")
            currentOnComplete()
            return@LaunchedEffect
        }
        runCatching {
            val manager = ReviewManagerFactory.create(currentActivity)
            val reviewInfo = manager.requestReview()
            manager.launchReview(currentActivity, reviewInfo)
        }.onFailure { e ->
            // Usually the Play review quota, but a swallowed error here made every
            // csat_review_completed unreadable — a returned flow and a never-shown card looked
            // identical. Never silent.
            logger.error(TAG, "in-app review launch failed: ${e.message}", e)
        }
        currentOnComplete()
    }
}
