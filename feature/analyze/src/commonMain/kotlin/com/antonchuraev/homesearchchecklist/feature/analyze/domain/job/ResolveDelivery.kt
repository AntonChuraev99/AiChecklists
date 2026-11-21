package com.antonchuraev.homesearchchecklist.feature.analyze.domain.job

/** Who should show a finished job's outcome to the user right now. See [resolveDelivery]. */
enum class DeliveryRoute {
    /** Not finished yet, or already delivered: nobody shows anything. */
    None,

    /** The Analyze screen that watches this job is attached and visible: it claims and navigates. */
    Screen,

    /** The app is visible but no screen watches the job: the app-level snackbar offers "Open". */
    InAppHost,

    /** The app is not visible: a system notification (Android); web shows the snackbar on return. */
    Notification,
}

/**
 * The single delivery rule (FEATURE_SPEC analyze-reliability-background-2026-11-20 §2). Pure, so
 * every claimant asks the same question and gets the same answer. Navigation still happens only
 * after [AnalysisJobScheduler.claimDelivery] wins — this function picks WHO tries, the claim makes
 * sure only one of them succeeds.
 */
fun resolveDelivery(job: AnalysisJob, attachedJobId: String?, isForeground: Boolean): DeliveryRoute = when {
    job.deliveredBy != null || job.acknowledged || !job.status.isTerminal -> DeliveryRoute.None
    attachedJobId == job.id && isForeground -> DeliveryRoute.Screen
    isForeground -> DeliveryRoute.InAppHost
    else -> DeliveryRoute.Notification
}
