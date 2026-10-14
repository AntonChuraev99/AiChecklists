package com.antonchuraev.homesearchchecklist.core.common.api

/**
 * Pure classification of a POST_NOTIFICATIONS system-dialog answer — shared by every ask site
 * (feature:home controller, onboarding requester) so they cannot disagree.
 *
 * `shouldShowRequestPermissionRationale` is false BEFORE the first ask, after a back/outside
 * DISMISS (Android 11+ does not count it), AND after a permanent deny — so "rationale false after a
 * denial" alone proves nothing. Permanence is claimed only when provable:
 *  - [NotificationDialogOutcome.BlockedNoDialog]: the OS answered "denied" without showing any UI
 *    (the host never paused). Only a permanent deny does that — typical for a user upgraded from a
 *    build that never recorded the earlier denial.
 *  - [NotificationDialogOutcome.PermanentlyDenied]: denied while the rationale flipped true → false
 *    (the second "Don't allow" on Android 11+).
 *  - everything else that is not granted (first deny, dismiss) → [NotificationDialogOutcome.DeniedCanAskAgain].
 */
enum class NotificationDialogOutcome { Granted, DeniedCanAskAgain, PermanentlyDenied, BlockedNoDialog }

fun classifyNotificationDialogResult(
    granted: Boolean,
    dialogShown: Boolean,
    rationaleBefore: Boolean,
    rationaleAfter: Boolean,
): NotificationDialogOutcome = when {
    granted -> NotificationDialogOutcome.Granted
    !dialogShown -> NotificationDialogOutcome.BlockedNoDialog
    rationaleBefore && !rationaleAfter -> NotificationDialogOutcome.PermanentlyDenied
    else -> NotificationDialogOutcome.DeniedCanAskAgain
}

/** True for both permanent outcomes — the value reported as `permanently_denied` and persisted. */
val NotificationDialogOutcome.isPermanent: Boolean
    get() = this == NotificationDialogOutcome.PermanentlyDenied || this == NotificationDialogOutcome.BlockedNoDialog

/**
 * Whether the stored "permanently denied" flag still holds. A rationale of `true` now means the OS
 * will show the dialog again (the user re-allowed asking, e.g. by resetting the app), so the flag
 * is stale; a granted permission makes it moot.
 */
fun isPermanentDenialStillValid(storedFlag: Boolean, permissionGranted: Boolean, rationaleNow: Boolean): Boolean =
    storedFlag && !permissionGranted && !rationaleNow
