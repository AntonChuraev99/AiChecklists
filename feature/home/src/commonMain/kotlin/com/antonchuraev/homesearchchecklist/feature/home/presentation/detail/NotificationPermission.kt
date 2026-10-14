package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.compose.runtime.Composable

/**
 * Composable that remembers a launcher for requesting notification permission.
 *
 * @param onResult Callback invoked with true if permission was granted, false otherwise.
 * @return A lambda that, when called, triggers the system permission request.
 */
@Composable
expect fun rememberNotificationPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit

/** What tapping "turn on notifications" will do right now. */
enum class NotificationPermissionAction(val wire: String) {
    /** Notifications already reach the user — nothing to ask. */
    AlreadyEnabled("already_enabled"),

    /** The OS can still show the POST_NOTIFICATIONS dialog. */
    SystemDialog("system_dialog"),

    /** The dialog can no longer appear (permanent deny, API < 33 with notifications off, app toggle off). */
    Settings("settings"),
}

/**
 * Outcome of a system-dialog request.
 *
 * @param permanentlyDenied provably permanent (see `classifyNotificationDialogResult`) — a dismiss
 *        or a first "Don't allow" is NOT permanent.
 * @param openedSettings the OS answered "denied" without showing any dialog, so the controller
 *        opened app notification settings in the same tap (visible response instead of a silent
 *        no-op). The caller should treat it like the Settings hand-off: re-read on ON_RESUME.
 */
data class NotificationPermissionResult(
    val granted: Boolean,
    val permanentlyDenied: Boolean,
    val openedSettings: Boolean = false,
)

/**
 * Platform handle for the notification ask. [nextAction] is side-effect free (decides CTA copy);
 * [request] performs it and returns what it did:
 *  - [NotificationPermissionAction.SystemDialog] → the answer arrives later via `onResult`;
 *  - [NotificationPermissionAction.Settings] → app notification settings opened; NO `onResult` —
 *    the caller re-reads the state on ON_RESUME;
 *  - [NotificationPermissionAction.AlreadyEnabled] → `onResult(granted = true)` fired synchronously.
 */
class NotificationPermissionController(
    val nextAction: () -> NotificationPermissionAction,
    val request: () -> NotificationPermissionAction,
)

/**
 * Remembers a [NotificationPermissionController]. Android: POST_NOTIFICATIONS dialog with
 * permanent-deny detection (NotificationPermissionLedger + classifyNotificationDialogResult);
 * web/iOS: always [NotificationPermissionAction.AlreadyEnabled] (no OS push permission here).
 */
@Composable
expect fun rememberNotificationPermissionController(
    onResult: (NotificationPermissionResult) -> Unit,
): NotificationPermissionController

/**
 * Pure decision behind [NotificationPermissionController.nextAction].
 *
 * The dialog is offered unless a permanent denial was PROVEN earlier ([permanentlyDeniedValid] =
 * the persisted ledger flag, still valid per `isPermanentDenialStillValid`). A user permanently
 * denied on an older build (no flag yet) is offered the dialog once; the OS then answers with no
 * UI, which the controller classifies as blocked and turns into a settings hand-off on that tap.
 */
fun resolveNotificationPermissionAction(
    notificationsEnabled: Boolean,
    runtimePermissionApi: Boolean,
    permissionGranted: Boolean,
    permanentlyDeniedValid: Boolean,
): NotificationPermissionAction = when {
    notificationsEnabled -> NotificationPermissionAction.AlreadyEnabled
    // Below API 33 there is no runtime permission; with the permission granted the block is the
    // app-level toggle in settings. Both are only fixable in settings.
    !runtimePermissionApi || permissionGranted -> NotificationPermissionAction.Settings
    permanentlyDeniedValid -> NotificationPermissionAction.Settings
    else -> NotificationPermissionAction.SystemDialog
}
