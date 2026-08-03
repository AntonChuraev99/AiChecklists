package com.antonchuraev.homesearchchecklist.csat

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

/**
 * What happened to a review request.
 *
 * Deliberately NOT a Boolean "was it shown": the store APIs do not report whether the review card
 * appeared or whether the user rated. Play's `launchReviewFlow` completes **successfully** when the
 * review quota is exhausted and simply renders nothing, and there is no error code for that case
 * (developer.android.com/guide/playcore/in-app-review). So the most we can honestly say is whether
 * the launch itself went through, plus WHY it did not when it failed.
 *
 * [analyticsReason] is an explicit string, never `name`: R8 rewrites enum names in release, and an
 * obfuscated reason silently poisons the funnel (project precedent: ChecklistViewMode).
 */
enum class ReviewLaunchOutcome(val analyticsReason: String?) {
    /** The platform accepted the launch. The card may still not have appeared — quota is silent. */
    Launched(null),

    /** No host Activity (destroyed / finishing) — the request never reached the platform. */
    NoHostActivity("no_host_activity"),

    /** The platform rejected the request (no Play Store, invalid request, internal error). */
    LaunchFailed("launch_failed"),

    /** The launcher was torn down mid-request (Activity recreation) before the flow returned. */
    Cancelled("cancelled"),

    /** This platform has no in-app review API at all (web, iOS until release). */
    Unsupported("unsupported"),
}

/**
 * Platform-specific composable that launches the native in-app review flow.
 *
 * - Android: Google Play In-App Review API (review-ktx).
 * - iOS / Web: stub — no review UI exists, so the outcome is [ReviewLaunchOutcome.Unsupported].
 *
 * [requests] is a stream of one-shot events, NOT a latched state flag. Each emission must launch
 * the review at most once: the flow is backed by a conflated [kotlinx.coroutines.channels.Channel]
 * whose element is consumed on receipt, so a re-created collector (Activity recreation: rotation,
 * locale/theme switch, multi-window resize, "don't keep activities") cannot replay an already
 * handled request. A `Boolean` parameter could not give this guarantee — it stays true across the
 * whole window between the launch and the asynchronous clear, and every composition entering that
 * window fires the Play API again.
 *
 * @param requests One-shot review requests; collect and launch the review per emission.
 * @param onComplete Called **exactly once per request**, including when the collector is cancelled
 *   mid-flight — the element is already consumed by then, so skipping the callback would leave the
 *   user's tap with no visible response at all.
 */
@Composable
expect fun InAppReviewLauncher(
    requests: Flow<Unit>,
    onComplete: (ReviewLaunchOutcome) -> Unit,
)
