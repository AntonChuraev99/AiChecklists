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
import kotlinx.coroutines.flow.Flow
import org.koin.compose.koinInject

private const val TAG = "Csat"

@Composable
actual fun InAppReviewLauncher(
    requests: Flow<Unit>,
    onComplete: (ReviewLaunchOutcome) -> Unit,
) {
    val logger: AppLogger = koinInject()
    val currentActivity by rememberUpdatedState(LocalActivity.current)
    val currentOnComplete by rememberUpdatedState(onComplete)

    // Keyed on the request flow, never on a state flag: every emission is consumed here, so
    // re-entering composition cannot replay a review that was already launched. Calling
    // requestReview() twice for one tap burned a Play review opportunity and pushed
    // csat_review_completed above csat_review_tapped.
    LaunchedEffect(requests) {
        requests.collect {
            // The element is gone from the channel the moment it is received, so this request can
            // never be retried — every exit path MUST report, including cancellation. Otherwise a
            // rotation during the (network-bound) Play call swallows the request whole: no
            // snackbar, no event, and the user's tap looks like a freeze.
            var reported = false
            fun report(outcome: ReviewLaunchOutcome) {
                if (!reported) {
                    reported = true
                    currentOnComplete(outcome)
                }
            }
            try {
                val activity = currentActivity as? ComponentActivity
                if (activity == null || activity.isFinishing) {
                    logger.warning(TAG, "in-app review skipped: activity unavailable or finishing")
                    report(ReviewLaunchOutcome.NoHostActivity)
                    return@collect
                }
                val launched = runCatching {
                    val manager = ReviewManagerFactory.create(activity)
                    val reviewInfo = manager.requestReview()
                    manager.launchReview(activity, reviewInfo)
                }.onFailure { e ->
                    // A swallowed error here made every csat_review_completed unreadable — a
                    // returned flow and a never-launched card looked identical. Never silent.
                    logger.error(TAG, "in-app review launch failed: ${e.message}", e)
                }.isSuccess
                // Success means the FLOW RAN, not that a card appeared: Play renders nothing once
                // the review quota is spent and reports no error for it.
                report(
                    if (launched) ReviewLaunchOutcome.Launched else ReviewLaunchOutcome.LaunchFailed,
                )
            } finally {
                // Reached with nothing reported only on cancellation (Activity recreation while the
                // Play call was in flight). report() is not suspending, so it still runs here.
                report(ReviewLaunchOutcome.Cancelled)
            }
        }
    }
}
